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
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.GraphqlError as GraphqlErrorProto
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties as DataConnectPropertiesProto
import google.firebase.dataconnect.proto.StreamRequest as StreamRequestProto
import google.firebase.dataconnect.proto.StreamResponse as StreamResponseProto
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.isActive

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
    val state =
      AtomicReference<SubscriptionState>(
        SubscriptionState.Disconnected(pendingSubscription = false)
      )

    val x =
      sharedFlow
        .transformToMessage(requestId, operationName, variables, state)
        .buffer(capacity = Channel.UNLIMITED)
        .shareIn(
          coroutineScope,
          started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
          replay = 0,
        )
  }

  private sealed interface SubscriptionState {

    class Disconnected(val pendingSubscription: Boolean) : SubscriptionState {
      override fun toString(): String = "Disconnected(pendingSubscription=$pendingSubscription)"
    }

    class Connected(
      val outgoingRequests: SendChannel<StreamRequestProto>,
      val wasPendingSubscription: Boolean,
      val subscribeJob: Job,
    ) : SubscriptionState {
      override fun toString(): String =
        "Connected(subscribeJob={isActive=${subscribeJob.isActive}, " +
          "isCompleted=${subscribeJob.isCompleted}})"
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

    object ScopeCompleted : SubscriptionEvent {
      override fun toString() = "ScopeCompleted"
    }

    sealed interface ScopeNotCompleted : SubscriptionEvent

    class Message(val connectionId: String, val message: StreamResponseProto) : ScopeNotCompleted {
      constructor(
        event: GrpcBidiFlow.Event.Message<StreamResponseProto>
      ) : this(event.connectionId, event.message)
      override fun toString() =
        "Message(connectionId=$connectionId, message=${message.toCompactString()})"
    }

    sealed interface Connection : ScopeNotCompleted

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

  private fun SharedFlow<SubscriptionEvent>.transformToMessage(
    requestId: String,
    operationName: String,
    variables: Struct,
    state: AtomicReference<SubscriptionState>,
  ): Flow<SubscriptionEvent.Message> {

    val subscribeRequest =
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

    fun SendChannel<StreamRequestProto>.trySendSubscribeRequest() {
      val sendResult = trySend(subscribeRequest)
      sendResult.onFailure { exception ->
        if (!sendResult.isClosed) {
          throw exception
            ?: error(
              "internal error gt2ms5wwby: outgoingRequests.trySend(subscribeRequest) " +
                "failed, but should have succeeded because outgoingRequests " +
                "is created with capacity=UNLIMITED"
            )
        }
      }
    }

    fun transitionToConnectedState(event: SubscriptionEvent.Connected) {
      while (true) {
        when (val currentState = state.get()) {
          is SubscriptionState.Connected ->
            error(
              "internal error nqe9gre3ny: got event $event, " +
                "but state=$currentState (expected state=Disconnected)"
            )
          is SubscriptionState.Disconnected -> {
            val newState =
              SubscriptionState.Connected(
                event.outgoingRequests,
                wasPendingSubscription = currentState.pendingSubscription,
                subscribeJob =
                  coroutineScope.async(start = CoroutineStart.LAZY) {
                    event.outgoingRequests.trySendSubscribeRequest()
                  }
              )
            if (state.compareAndSet(currentState, newState)) {
              newState.subscribeJob.start()
              break
            }
          }
        }
      }
    }

    fun transitionToDisconnectedState(@Suppress("unused") event: SubscriptionEvent.Disconnected) {
      while (true) {
        when (val currentState = state.get()) {
          is SubscriptionState.Disconnected ->
            error(
              "internal error vv4verqchm: got Disconnected event, " +
                "but state=$currentState (expected state=Connected)"
            )
          is SubscriptionState.Connected -> {
            currentState.subscribeJob.cancel("got Disconnected event")
            val newState =
              SubscriptionState.Disconnected(
                pendingSubscription = currentState.wasPendingSubscription
              )
            if (state.compareAndSet(currentState, newState)) {
              break
            }
          }
        }
      }
    }

    return transformWhile { event ->
      if (!coroutineScope.isActive) {
        return@transformWhile false
      }
      when (event) {
        is SubscriptionEvent.ScopeCompleted -> return@transformWhile false
        is SubscriptionEvent.Connected -> transitionToConnectedState(event)
        is SubscriptionEvent.Disconnected -> transitionToDisconnectedState(event)
        is SubscriptionEvent.Message -> emit(event)
      }
      true
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
