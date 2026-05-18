/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.ExperimentalRealtimeQueries
import com.google.firebase.dataconnect.util.CoroutineUtils.completedFlow
import com.google.firebase.dataconnect.util.GrpcBidiFlow
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import com.google.protobuf.Empty
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.GraphqlError as GraphqlErrorProto
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties as DataConnectPropertiesProto
import google.firebase.dataconnect.proto.ResumeRequest
import google.firebase.dataconnect.proto.StreamRequest as StreamRequestProto
import google.firebase.dataconnect.proto.StreamResponse as StreamResponseProto
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages a bidirectional gRPC stream for Data Connect operations.
 *
 * This class multiplexes multiple incoming requests and outgoing responses over a single underlying
 * bidirectional connection. It manages the lifecycle of the connection state (Open, Closing,
 * Closed), buffering, and correlation of incoming responses to their respective subscribers based
 * on `requestId`.
 *
 * @param flow The flow that, when collected, opens the bidirectional streaming "Connect" RPC with
 * the backend and sends responses received from the backend downstream.
 * @param coroutineScope The [CoroutineScope] to whose lifetime this object belongs.
 */
@ExperimentalRealtimeQueries
internal class DataConnectBidiConnectStream(
  flow: Flow<GrpcBidiFlow.Event<StreamRequestProto, StreamResponseProto>>,
  private val coroutineScope: CoroutineScope,
) {

  private val sharedFlow: SharedFlow<SubscriptionEvent>

  init {
    val scopeCompletedFlow: Flow<SubscriptionEvent.ScopeCompleted> =
      coroutineScope.completedFlow().map { SubscriptionEvent.ScopeCompleted }

    val connectionState =
      MutableStateFlow<SubscriptionEvent.Connection>(SubscriptionEvent.Disconnected)

    val messageFlow: Flow<SubscriptionEvent.Message> =
      flow
        .onEach { event ->
          if (event is GrpcBidiFlow.Event.ConnectionInfo) {
            connectionState.value = SubscriptionEvent.Connected(event)
          }
        }
        .filterIsInstance<GrpcBidiFlow.Event.Message<StreamResponseProto>>()
        .map(SubscriptionEvent::Message)
        .onCompletion { connectionState.value = SubscriptionEvent.Disconnected }
        .retryWhen { _, attempt ->
          if (attempt < 2) {
            false
          } else {
            delay(1.seconds)
            true
          }
        }

    sharedFlow =
      merge(scopeCompletedFlow, connectionState, messageFlow)
        .buffer(capacity = Channel.UNLIMITED)
        .shareIn(
          coroutineScope,
          started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
          replay = 0,
        )
  }

  /**
   * Starts a subscription for the query with the given [operationName] and [variables].
   *
   * @param requestId A unique identifier for this request, used to correlate incoming responses.
   * @param operationName The name of the operation to execute.
   * @param variables The variables for the operation.
   * @return A [Flow] of [ExecuteResponse] objects for the subscription.
   */
  fun subscribe(
    requestId: String,
    operationName: String,
    variables: Struct,
  ): Flow<ExecuteResponse> {
    // Note: `subscriptionStateManager` is shared by _all_ collectors of the returned flow;
    // however, each flow collector gets its own `Subscriber` object from the manager.
    val subscriptionStateManager =
      SubscriptionStateManager(
        requestId = requestId,
        operationName = operationName,
        variables = variables,
      )

    return flow {
      val flowCollectorStartSequenceNumber = nextSequenceNumber()
      val subscription = subscriptionStateManager.Subscriber()

      emitAll(
        merge(sharedFlow.onSubscription { emit(Event.Subscribed) }, scopeCompletedFlow)
          .transformWhile { event ->
            when (event) {
              is Event.Ready -> {
                subscription.setOutgoingRequests(event.outgoingRequests)
                true
              }
              is Event.Subscribed -> subscription.subscribe()
              is Event.Message -> {
                subscription.setOutgoingRequests(event.outgoingRequests)
                if (event.sequenceNumber < flowCollectorStartSequenceNumber) {
                  true
                } else if (event.streamResponse.requestId != requestId) {
                  true
                } else {
                  val executeResponse = event.streamResponse.toExecuteResponse()
                  if (executeResponse !== null) {
                    emit(executeResponse)
                  }
                  !event.streamResponse.cancelled
                }
              }
              is Event.Completed -> {
                event.throwable?.let {
                  if (it !is CancellationException) {
                    throw it
                  }
                }
                false
              }
            }
          }
          .onCompletion { subscription.unsubscribe() }
      )
    }
  }

  /**
   * Represents the application-level response to a GraphQL execution request.
   *
   * @property data The data payload returned by the GraphQL query or mutation.
   * @property errors The errors related to the execution of the operation.
   * @property extensions Additional metadata or properties related to the execution.
   */
  class ExecuteResponse(
    val data: Struct?,
    val errors: List<GraphqlErrorProto>,
    val extensions: List<DataConnectPropertiesProto>,
  ) {
    operator fun component1() = data
    operator fun component2() = errors
    operator fun component3() = extensions
  }

  private sealed interface SubscriptionEvent {

    class Message(val connectionId: String, val message: StreamResponseProto) : SubscriptionEvent {
      constructor(
        event: GrpcBidiFlow.Event.Message<StreamResponseProto>
      ) : this(event.connectionId, event.message)
      override fun toString() =
        "Message(connectionId=$connectionId, message=${message.toCompactString()})"
    }

    object ScopeCompleted : SubscriptionEvent {
      override fun toString() = "ScopeCompleted"
    }

    sealed interface Connection : SubscriptionEvent

    class Connected(
      val connectionId: String,
      val outgoingRequests: SendChannel<StreamRequestProto>,
    ) : Connection {
      constructor(
        event: GrpcBidiFlow.Event.ConnectionInfo<StreamRequestProto>
      ) : this(event.connectionId, event.outgoingRequests)

      override fun toString() = "Connected(connectionId=$connectionId)"
    }

    object Disconnected : Connection {
      override fun toString() = "Disconnected"
    }
  }

  /**
   * NOTE: This class is **NOT** thread safe.
   *
   * Concurrent access to a [SubscriptionStateManager] and all [Subscriber] instances created from
   * it **MUST** be serialized with **the same** [Mutex], or else the behavior is undefined. The
   * likely observable effect of unserialized concurrent access would be things like missing, extra,
   * or out-of-order "subscribe", "resume" or "cancel" requests, which defeats the entire purpose of
   * this class.
   */
  private class SubscriptionStateManager(
    requestId: String,
    operationName: String,
    variables: Struct,
  ) {

    private val mutex = Mutex()
    private var subscriberCount = 0

    inner class Subscriber {
      private var state: State = State.NotReady(pendingSubscribe = false)

      suspend fun setOutgoingRequests(outgoingRequests: SendChannel<StreamRequestProto>) {
        when (val currentState = state) {
          is State.NotReady -> {
            val readyState = State.Ready(outgoingRequests)
            state = readyState
            if (currentState.pendingSubscribe) {
              mutex.withLock { subscribe(readyState) }
            }
          }
          is State.Ready ->
            check(currentState.outgoingRequests === outgoingRequests) {
              "internal error n99tc8qe2t: setOutgoingRequests() has already been called " +
                "with a different object"
            }
        }
      }

      suspend fun subscribe(): Boolean {
        return when (val currentState = state) {
          is State.NotReady -> {
            check(!currentState.pendingSubscribe) {
              "internal error szx94f63tz: subscribe() called when already subscribed"
            }
            state = State.NotReady(pendingSubscribe = true)
            true
          }
          is State.Ready ->
            mutex.withLock {
              return subscribe(currentState)
            }
        }
      }

      private fun subscribe(readyState: State.Ready): Boolean {
        check(!readyState.subscribed) {
          "internal error hkjgvhnk27: subscribe() called when already subscribed " +
            "(is concurrent access to the SubscriptionStateManager properly serialized " +
            "with a mutex?)"
        }

        val streamRequest =
          if (subscriberCount == 0) {
            subscribeStreamRequest
          } else {
            resumeStreamRequest
          }

        val sendResult = readyState.outgoingRequests.trySend(streamRequest)

        return when {
          sendResult.isSuccess -> {
            readyState.subscribed = true
            subscriberCount++
            true
          }
          sendResult.isClosed -> false
          else ->
            error(
              "internal error xw3zdzycfq: outgoingRequests.trySend(subscribe or resume) " +
                "was unable to enqueue the streamRequest; this should never happen because " +
                "outgoingRequests is created with capacity=UNLIMITED (sendResult=$sendResult)"
            )
        }
      }

      suspend fun unsubscribe() {
        when (val currentState = state) {
          is State.NotReady ->
            if (currentState.pendingSubscribe) {
              state = State.NotReady(pendingSubscribe = false)
            }
          is State.Ready -> mutex.withLock { unsubscribe(currentState) }
        }
      }

      private fun unsubscribe(readyState: State.Ready) {
        if (!readyState.subscribed) {
          return
        }

        readyState.subscribed = false
        subscriberCount--
        check(subscriberCount >= 0) {
          "internal error hpn3qsj746: subscriberCount should never be less than zero, " +
            "but it is: $subscriberCount"
        }

        if (subscriberCount == 0) {
          val sendResult = readyState.outgoingRequests.trySend(cancelStreamRequest)
          if (sendResult.isFailure && !sendResult.isClosed) {
            error(
              "internal error mxcsq556tv: outgoingRequests.trySend(cancel) " +
                "was unable to enqueue the streamRequest; this should never happen because " +
                "outgoingRequests is created with capacity=UNLIMITED (sendResult=$sendResult)"
            )
          }
        }
      }
    }

    private val subscribeStreamRequest =
      StreamRequestProto.newBuilder().let { streamRequest ->
        streamRequest.setRequestId(requestId)
        streamRequest.setSubscribe(
          ExecuteRequest.newBuilder().let { executeRequest ->
            executeRequest.setOperationName(operationName)
            executeRequest.setVariables(variables)
            executeRequest.build()
          }
        )
        streamRequest.build()
      }

    private val resumeStreamRequest =
      StreamRequestProto.newBuilder().let { streamRequest ->
        streamRequest.setRequestId(requestId)
        streamRequest.setResume(ResumeRequest.getDefaultInstance())
        streamRequest.build()
      }

    private val cancelStreamRequest =
      StreamRequestProto.newBuilder().let { streamRequest ->
        streamRequest.setRequestId(requestId)
        streamRequest.setCancel(Empty.getDefaultInstance())
        streamRequest.build()
      }

    private sealed interface State {
      data class NotReady(val pendingSubscribe: Boolean) : State {
        override fun toString() = "NotReady(pendingSubscribe=$pendingSubscribe)"
      }

      class Ready(
        val outgoingRequests: SendChannel<StreamRequestProto>,
        // NOTE: @Volatile is applied to `subscribed` so that toString() can safely read its value.
        @Volatile var subscribed: Boolean = false,
      ) : State {
        override fun toString() = "Ready(subscribed=$subscribed)"
      }
    }
  }

  private companion object {

    fun StreamResponseProto.toExecuteResponse(): ExecuteResponse? =
      if (!hasData() && errorsCount == 0) {
        null
      } else {
        ExecuteResponse(
          data = if (hasData()) data else null,
          errors = if (errorsCount > 0) errorsList else emptyList(),
          extensions =
            if (hasExtensions() && extensions.dataConnectCount > 0) extensions.dataConnectList
            else emptyList(),
        )
      }
  }
}
