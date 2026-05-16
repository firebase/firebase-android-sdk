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
import com.google.firebase.dataconnect.util.GrpcBidiFlow
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.protobuf.Empty
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.GraphqlError as GraphqlErrorProto
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties as DataConnectPropertiesProto
import google.firebase.dataconnect.proto.ResumeRequest
import google.firebase.dataconnect.proto.StreamRequest as StreamRequestProto
import google.firebase.dataconnect.proto.StreamResponse as StreamResponseProto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
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

  private val sharedFlow: SharedFlow<Event> =
    flow
      .map { event ->
        when (event) {
          is GrpcBidiFlow.Event.ConnectionInfo -> Event.Started(event.outgoingRequests)
          is GrpcBidiFlow.Event.Message ->
            Event.Message(event.message, event.connectionInfo.outgoingRequests)
        }
      }
      .onCompletion { throwable ->
        if (throwable === null) {
          emit(Event.Completed(null))
        }
      }
      .catch { emit(Event.Completed(throwable = it)) }
      .buffer(capacity = Channel.UNLIMITED)
      .shareIn(coroutineScope, started = SharingStarted.WhileSubscribed(), replay = 1)

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
      val subscription = subscriptionStateManager.Subscriber()

      emitAll(
        sharedFlow
          .onSubscription { emit(Event.Subscribed) }
          .transformWhile { event ->
            when (event) {
              is Event.Started -> {
                subscription.setOutgoingRequests(event.outgoingRequests)
                true
              }
              is Event.Subscribed -> subscription.subscribe()
              is Event.Message -> {
                subscription.setOutgoingRequests(event.outgoingRequests)
                if (event.streamResponse.requestId != requestId) {
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

  /**
   * Represents an internal wrapper around incoming server responses and lifecycle signals.
   *
   * This sealed interface allows the internal [SharedFlow] to multiplex actual response data
   * alongside control signals like completion, subscriber readiness, and buffer flushes.
   */
  private sealed interface Event {

    /**
     * The event emitted once per collection, that provides the channel to use to send requests over
     * the bidirectional stream.
     */
    class Started(val outgoingRequests: SendChannel<StreamRequestProto>) : Event {
      override fun toString() = "Started"
    }

    /** Represents a standard data response from the server. */
    class Message(
      val streamResponse: StreamResponseProto,
      val outgoingRequests: SendChannel<StreamRequestProto>,
    ) : Event {
      override fun toString() = "Message(${streamResponse.toCompactString()})"
    }

    /**
     * Represents the termination of the incoming stream, either naturally or due to an error.
     *
     * By placing this in the [SharedFlow], new or existing subscribers can be notified immediately
     * if the underlying stream is disconnected.
     *
     * @property throwable The exception that caused termination, or null if the stream completed
     * normally.
     */
    class Completed(val throwable: Throwable?) : Event {
      override fun toString() = "Completed(throwable=$throwable)"
    }

    /**
     * A control signal used to synchronize the start of outgoing requests with the readiness of the
     * collector.
     *
     * Emitted locally inside the [subscribe] method's `onSubscription` block. This guarantees that
     * the collector in `transformWhile` is fully registered and actively listening to the
     * [SharedFlow] *before* the [StreamRequestProto] is actually sent to the server. Without this
     * signal, there is a race condition where the server might respond to the request so fast that
     * the resulting [Message] is processed by the [SharedFlow] before the `subscribe` collector has
     * started listening, leading to silently lost responses.
     */
    object Subscribed : Event
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
      private val state = MutableStateFlow<State>(State.NotReady(pendingSubscribe = false))

      private var subscribed = false

      suspend fun setOutgoingRequests(outgoingRequests: SendChannel<StreamRequestProto>) {
        val oldState =
          state.getAndUpdate { currentState ->
            when (currentState) {
              is State.NotReady -> State.Ready(outgoingRequests)
              is State.Ready -> {
                check(currentState.outgoingRequests === outgoingRequests) {
                  "internal error n99tc8qe2t: setOutgoingRequests() has already been called " +
                    "with a different object"
                }
                currentState
              }
            }
          }

        if (oldState is State.NotReady && oldState.pendingSubscribe) {
          mutex.withLock { subscribe(outgoingRequests) }
        }
      }

      suspend fun subscribe(): Boolean {
        while (true) {
          when (val currentState = state.value) {
            is State.NotReady -> {
              check(!currentState.pendingSubscribe) {
                "internal error szx94f63tz: subscribe() called when already subscribed"
              }
              if (state.compareAndSet(currentState, State.NotReady(pendingSubscribe = true))) {
                return true
              }
            }
            is State.Ready ->
              mutex.withLock {
                return subscribe(currentState.outgoingRequests)
              }
          }
        }
      }

      private fun subscribe(outgoingRequests: SendChannel<StreamRequestProto>): Boolean {
        check(!subscribed) {
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

        val sendResult = outgoingRequests.trySend(streamRequest)

        return when {
          sendResult.isSuccess -> {
            subscribed = true
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
        when (val currentState = state.value) {
          is State.NotReady -> return
          is State.Ready -> mutex.withLock { unsubscribe(currentState.outgoingRequests) }
        }
      }

      private fun unsubscribe(outgoingRequests: SendChannel<StreamRequestProto>) {
        if (!subscribed) {
          return
        }

        subscribed = false
        subscriberCount--
        check(subscriberCount >= 0) {
          "internal error hpn3qsj746: subscriberCount should never be less than zero, " +
            "but it is: $subscriberCount"
        }

        if (subscriberCount == 0) {
          val sendResult = outgoingRequests.trySend(cancelStreamRequest)
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

      class Ready(val outgoingRequests: SendChannel<StreamRequestProto>) : State {
        override fun toString() = "Ready"
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
