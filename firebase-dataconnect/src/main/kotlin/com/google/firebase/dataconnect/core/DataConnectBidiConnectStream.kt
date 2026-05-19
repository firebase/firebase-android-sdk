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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import com.google.protobuf.Empty as EmptyProto
import google.firebase.dataconnect.proto.ExecuteRequest as ExecuteRequestProto
import google.firebase.dataconnect.proto.GraphqlError as GraphqlErrorProto
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties as DataConnectPropertiesProto
import google.firebase.dataconnect.proto.ResumeRequest as ResumeRequestProto
import google.firebase.dataconnect.proto.StreamRequest as StreamRequestProto
import google.firebase.dataconnect.proto.StreamResponse as StreamResponseProto

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
  flow: Flow<GrpcBidiFlow.Event<StreamRequestProto, StreamResponseProto, String?>>,
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
        .map<_, MessageOrSubscribe> { MessageOrSubscribe.Message(it) }
        .buffer(capacity = Channel.UNLIMITED)
        .shareIn(
          coroutineScope,
          started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
          replay = 0,
        )
        .onSubscription { emit(MessageOrSubscribe.Subscribed) }
        .transform { messageOrSubscribe ->
          when (messageOrSubscribe) {
            is MessageOrSubscribe.Message -> emit(messageOrSubscribe.message)
            MessageOrSubscribe.Subscribed -> {
              while (true) {
                when (val currentState = state.get()) {
                  is SubscriptionState.Disconnected ->
                    if (currentState.pendingSubscription) {
                      break
                    } else if (state.compareAndSet(currentState, SubscriptionState.Disconnected(pendingSubscription = true))) {
                      break
                    }
                  is SubscriptionState.Connected -> {
                    currentState.enqueueSubscribeOrResume()
                  }
                }
              }
            }
          }
        }

    TODO()
  }

  private sealed interface MessageOrSubscribe {

    class Message(val message: StreamResponseProto) : MessageOrSubscribe {
      constructor(event: SubscriptionEvent.Message) : this(event.message)
      override fun toString() = "Message(message=${message.toCompactString()}"
    }

    object Subscribed : MessageOrSubscribe

  }

  private sealed interface SubscriptionState {

    class Disconnected(val pendingSubscription: Boolean) : SubscriptionState {
      override fun toString(): String = "Disconnected(pendingSubscription=$pendingSubscription)"
    }

    class Connected(
      val outgoingRequests: SendChannel<StreamRequestProto>,
      val wasPendingSubscription: Boolean,
      private val enqueuedSubscribeOrResume: MutableStateFlow<Boolean>,
      private val subscribeOrResumeJob: Job,
    ) : SubscriptionState {

      fun enqueueSubscribeOrResume() {
        enqueuedSubscribeOrResume.value = true
        subscribeOrResumeJob.start()
      }

      fun cancelSubscribeOrResumeJob(message: String) {
        subscribeOrResumeJob.cancel(message)
      }

      override fun toString(): String =
        "Connected(" +
            "wasPendingSubscription=$wasPendingSubscription " +
            "enqueuedSubscribeOrResume=${enqueuedSubscribeOrResume.value} " +
            "subscribeOrResumeJob={isActive=${subscribeOrResumeJob.isActive}, " +
            "isCancelled=${subscribeOrResumeJob.isCancelled}, " +
            "isCompleted=${subscribeOrResumeJob.isCompleted}})"
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
      val authUid: String?,
      val outgoingRequests: SendChannel<StreamRequestProto>,
    ) : Connection {
      constructor(
        event: GrpcBidiFlow.Event.ConnectionInfo<StreamRequestProto, String?>
      ) : this(event.connectionId, event.connectionContext, event.outgoingRequests)

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
          ExecuteRequestProto.newBuilder().let { executeRequest ->
            executeRequest.setOperationName(operationName)
            executeRequest.setVariables(variables)
            executeRequest.build()
          }
        )
        streamRequest.build()
      }

    val resumeRequest =
      StreamRequestProto.newBuilder().let { streamRequest ->
        streamRequest.setRequestId(requestId)
        streamRequest.setResume(ResumeRequestProto.getDefaultInstance())
        streamRequest.build()
      }

    val cancelRequest =
      StreamRequestProto.newBuilder().let { streamRequest ->
        streamRequest.setRequestId(requestId)
        streamRequest.setCancel(EmptyProto.getDefaultInstance())
        streamRequest.build()
      }

    suspend fun SendChannel<StreamRequestProto>.subscribeOrResumeLoop(authUid: String?, enqueuedSubscribeOrResume: MutableStateFlow<Boolean>) {
      fun sendRequest(request: StreamRequestProto) {
        val sendResult = trySend(request)
        sendResult.onFailure { exception ->
          if (!sendResult.isClosed) {
            throw exception
              ?: error(
                "internal error gt2ms5wwby: outgoingRequests.trySend() failed, " +
                "but should have succeeded because outgoingRequests is created " +
                    "with capacity=UNLIMITED (request=${request.toCompactString(authUid)})"
              )
          }
        }
      }

      var subscribed = false
      try {
        enqueuedSubscribeOrResume.filter { it }.collect {
          enqueuedSubscribeOrResume.value = false
          if (! subscribed) {
            sendRequest(subscribeRequest)
            subscribed = true
          } else {
            sendRequest(resumeRequest)
          }
        }
      } finally {
        withContext(NonCancellable) {
          if (subscribed) {
            sendRequest(cancelRequest)
          }
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
            val enqueuedSubscribeOrResume = MutableStateFlow(currentState.pendingSubscription)
            val newState =
              SubscriptionState.Connected(
                event.outgoingRequests,
                wasPendingSubscription = currentState.pendingSubscription,
                enqueuedSubscribeOrResume = enqueuedSubscribeOrResume,
                subscribeOrResumeJob =
                  coroutineScope.launch(start = CoroutineStart.LAZY) {
                    event.outgoingRequests.subscribeOrResumeLoop(event.authUid, enqueuedSubscribeOrResume)
                  }
              )
            if (state.compareAndSet(currentState, newState)) {
              newState.enqueueSubscribeOrResume()
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
            currentState.cancelSubscribeOrResumeJob("got Disconnected event")
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
