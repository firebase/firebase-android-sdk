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

import com.google.firebase.dataconnect.core.DataConnectAuth.AuthUid
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.CoroutineUtils.completedFlow
import com.google.firebase.dataconnect.util.GrpcBidiFlow
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.coroutines.ConflatedSignal
import com.google.firebase.dataconnect.util.update
import com.google.protobuf.Empty as EmptyProto
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteRequest as ExecuteRequestProto
import google.firebase.dataconnect.proto.GraphqlError as GraphqlErrorProto
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties as DataConnectPropertiesProto
import google.firebase.dataconnect.proto.ResumeRequest as ResumeRequestProto
import google.firebase.dataconnect.proto.StreamRequest as StreamRequestProto
import google.firebase.dataconnect.proto.StreamResponse as StreamResponseProto
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
internal class DataConnectBidiConnectStream(
  flow: Flow<GrpcBidiFlow.Event<StreamRequestProto, StreamResponseProto, AuthUid?>>,
  private val coroutineScope: CoroutineScope,
  private val logger: Logger,
) {

  /**
   * A flow that emits `null` when [coroutineScope] is canceled, which happens when
   * [com.google.firebase.dataconnect.FirebaseDataConnect.close] is called.
   */
  private val scopeCompletedFlow = coroutineScope.completedFlow().map { null }

  private val connectionFlow: Flow<SubscriptionEvent> = run {
    val connectionStateFlow =
      MutableStateFlow<SubscriptionEvent.Connection>(SubscriptionEvent.Disconnected)

    val sharedFlow =
      flow
        .onEach { event ->
          if (event is GrpcBidiFlow.Event.ConnectionInfo) {
            connectionStateFlow.value = SubscriptionEvent.Connected(event)
          }
        }
        .filterIsInstance<GrpcBidiFlow.Event.Message<StreamResponseProto, AuthUid?>>()
        .map(SubscriptionEvent::Message)
        .onCompletion { throwable ->
          connectionStateFlow.value = SubscriptionEvent.Disconnected
          throw throwable ?: Exception("to be handled by retryWhen")
        }
        .retryWhen { _, attempt ->
          if (attempt > 2) {
            false
          } else {
            delay(1.seconds)
            logger.debug { "retrying connection" }
            true
          }
        }
        .buffer(capacity = 64) // Use a finite buffer to activate gRPC flow control, when needed
        .shareIn(
          coroutineScope,
          started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
          replay = 0,
        )

    merge(connectionStateFlow, sharedFlow)
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
    val state = AtomicReference<SubscriptionState>(SubscriptionState.Disconnected)

    fun sendSubscribeOrResume() {
      while (true) {
        when (val currentState = state.get()) {
          is SubscriptionState.Connected -> {
            currentState.enqueueSubscribeOrResume()
            break
          }
          SubscriptionState.DisconnectedWithPendingSubscription -> break
          SubscriptionState.Disconnected ->
            if (
              state.compareAndSet(
                currentState,
                SubscriptionState.DisconnectedWithPendingSubscription
              )
            ) {
              break
            }
        }
      }
    }

    val subscriptionFlow =
      connectionFlow
        .onCompletion {
          state.update { currentState ->
            if (currentState is SubscriptionState.Connected) {
              currentState.cancelSubscribeOrResumeJob("all subscribers have unsubscribed")
            }
            SubscriptionState.Disconnected
          }
        }
        .transformToMessage(requestId, operationName, variables, state)
        .map<_, MessageOrSubscribe> { MessageOrSubscribe.Message(it) }
        .buffer(capacity = Channel.CONFLATED) // use CONFLATED to drop stale data
        .shareIn(
          coroutineScope,
          started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
          replay = 0,
        )
        .onSubscription { emit(MessageOrSubscribe.Subscribed) }
        .transform { messageOrSubscribe ->
          when (messageOrSubscribe) {
            MessageOrSubscribe.Subscribed -> sendSubscribeOrResume()
            is MessageOrSubscribe.Message -> emit(messageOrSubscribe)
          }
        }
        .filter { it.message.requestId == requestId }
        .mapNotNull { it.message.toExecuteResponse(it.authUid) }

    // Configure the returned flow to end gracefully when FirebaseDataConnect.close() is called.
    return merge(subscriptionFlow, scopeCompletedFlow).transformWhile {
      if (it !== null && coroutineScope.isActive) {
        emit(it)
        true
      } else {
        false
      }
    }
  }

  private sealed interface MessageOrSubscribe {

    object Subscribed : MessageOrSubscribe {
      override fun toString() = "Subscribed"
    }

    class Message(val authUid: AuthUid?, val message: StreamResponseProto) : MessageOrSubscribe {
      constructor(event: SubscriptionEvent.Message) : this(event.authUid, event.message)
      override fun toString() = "Message(authUid=$authUid, message=${message.toCompactString()})"
    }
  }

  sealed interface SubscriptionState {

    object Disconnected : SubscriptionState {
      override fun toString() = "Disconnected"
    }

    object DisconnectedWithPendingSubscription : SubscriptionState {
      override fun toString() = "DisconnectedWithPendingSubscription"
    }

    class Connected(
      val outgoingRequests: SendChannel<StreamRequestProto>,
      private val hadPendingSubscription: Boolean,
      private val subscribeOrResumeSignal: ConflatedSignal,
      private val subscribeOrResumeJob: Job,
    ) : SubscriptionState {

      fun enqueueSubscribeOrResume() {
        subscribeOrResumeSignal.signal()
        subscribeOrResumeJob.start()
      }

      fun cancelSubscribeOrResumeJob(message: String) {
        subscribeOrResumeJob.cancel(message)
      }

      fun wasSubscribeOrResumeJobStarted(): Boolean =
        subscribeOrResumeJob.run {
          hadPendingSubscription || isActive || isCompleted || isCancelled
        }

      override fun toString(): String =
        "Connected(" +
          "hadPendingSubscription=$hadPendingSubscription, " +
          "subscribeOrResumeSignal.hasPendingSignal=${subscribeOrResumeSignal.hasPendingSignal}, " +
          "subscribeOrResumeJob={isActive=${subscribeOrResumeJob.isActive}, " +
          "isCancelled=${subscribeOrResumeJob.isCancelled}, " +
          "isCompleted=${subscribeOrResumeJob.isCompleted}})"
    }
  }

  /**
   * Represents the application-level response to a GraphQL execution request.
   *
   * @property authUid The Firebase Auth UID of the Firebase user under whose credentials the query
   * was executed, or `null` if no Firebase user was logged in.
   * @property data The data payload returned by the GraphQL query or mutation.
   * @property errors The errors related to the execution of the operation.
   * @property extensions Additional metadata or properties related to the execution.
   */
  class ExecuteResponse(
    val authUid: AuthUid?,
    val data: Struct?,
    val errors: List<GraphqlErrorProto>,
    val extensions: List<DataConnectPropertiesProto>,
  )

  private sealed interface SubscriptionEvent {

    class Message(
      val connectionId: String,
      val authUid: AuthUid?,
      val message: StreamResponseProto,
    ) : SubscriptionEvent {
      constructor(
        event: GrpcBidiFlow.Event.Message<StreamResponseProto, AuthUid?>
      ) : this(event.connectionId, event.connectionCookie, event.message)
      override fun toString() =
        "Message(connectionId=$connectionId, authUid=$authUid, " +
          "message=${message.toCompactString()})"
    }

    sealed interface Connection : SubscriptionEvent

    class Connected(
      val connectionId: String,
      val authUid: AuthUid?,
      val outgoingRequests: SendChannel<StreamRequestProto>,
    ) : Connection {
      constructor(
        event: GrpcBidiFlow.Event.ConnectionInfo<StreamRequestProto, AuthUid?>
      ) : this(event.connectionId, event.connectionCookie, event.outgoingRequests)

      override fun toString() = "Connected(connectionId=$connectionId)"
    }

    object Disconnected : Connection {
      override fun toString() = "Disconnected"
    }
  }

  private fun Flow<SubscriptionEvent>.transformToMessage(
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

    return transformToMessage(
      state,
      subscribeRequest = subscribeRequest,
      resumeRequest = resumeRequest,
      cancelRequest = cancelRequest,
    )
  }

  private fun Flow<SubscriptionEvent>.transformToMessage(
    state: AtomicReference<SubscriptionState>,
    subscribeRequest: StreamRequestProto,
    resumeRequest: StreamRequestProto,
    cancelRequest: StreamRequestProto,
  ): Flow<SubscriptionEvent.Message> {

    fun SendChannel<StreamRequestProto>.trySendOrThrow(
      authUid: AuthUid?,
      request: StreamRequestProto
    ) {
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

    suspend fun SendChannel<StreamRequestProto>.subscribeOrResumeLoop(
      authUid: AuthUid?,
      subscribeOrResumeSignal: ConflatedSignal,
    ) {
      var subscribed = false
      try {
        subscribeOrResumeSignal.signals.collect {
          if (!subscribed) {
            trySendOrThrow(authUid, subscribeRequest)
            subscribed = true
          } else {
            trySendOrThrow(authUid, resumeRequest)
          }
        }
      } finally {
        if (subscribed) {
          trySendOrThrow(authUid, cancelRequest)
        }
      }
    }

    fun transitionToConnectedState(event: SubscriptionEvent.Connected) {
      while (true) {
        val currentState = state.get()

        val isPendingSubscription =
          when (currentState) {
            is SubscriptionState.Connected ->
              error(
                "internal error nqe9gre3ny: got event $event, " +
                  "but state=$currentState (expected state=Disconnected)"
              )
            is SubscriptionState.Disconnected -> false
            is SubscriptionState.DisconnectedWithPendingSubscription -> true
          }

        val subscribeOrResumeSignal = ConflatedSignal()
        if (isPendingSubscription) {
          subscribeOrResumeSignal.signal()
        }
        val subscribeOrResumeJob =
          coroutineScope.launch(start = CoroutineStart.LAZY) {
            event.outgoingRequests.subscribeOrResumeLoop(event.authUid, subscribeOrResumeSignal)
          }

        val newState =
          SubscriptionState.Connected(
            event.outgoingRequests,
            hadPendingSubscription = isPendingSubscription,
            subscribeOrResumeSignal = subscribeOrResumeSignal,
            subscribeOrResumeJob = subscribeOrResumeJob,
          )
        if (state.compareAndSet(currentState, newState)) {
          if (isPendingSubscription) {
            subscribeOrResumeJob.start()
          }
          break
        }
      }
    }

    fun transitionToDisconnectedState(@Suppress("unused") event: SubscriptionEvent.Disconnected) {
      while (true) {
        when (val currentState = state.get()) {
          SubscriptionState.Disconnected,
          SubscriptionState.DisconnectedWithPendingSubscription -> break
          is SubscriptionState.Connected -> {
            currentState.cancelSubscribeOrResumeJob("got Disconnected event")
            val newState =
              if (currentState.wasSubscribeOrResumeJobStarted()) {
                SubscriptionState.DisconnectedWithPendingSubscription
              } else {
                SubscriptionState.Disconnected
              }
            if (state.compareAndSet(currentState, newState)) {
              break
            }
          }
        }
      }
    }

    return transform { event ->
      when (event) {
        is SubscriptionEvent.Connected -> transitionToConnectedState(event)
        is SubscriptionEvent.Disconnected -> transitionToDisconnectedState(event)
        is SubscriptionEvent.Message -> emit(event)
      }
    }
  }

  private companion object {

    fun StreamResponseProto.toExecuteResponse(authUid: AuthUid?): ExecuteResponse? =
      if (!hasData() && errorsCount == 0) {
        null
      } else {
        ExecuteResponse(
          authUid = authUid,
          data = if (hasData()) data else null,
          errors = if (errorsCount > 0) errorsList else emptyList(),
          extensions =
            if (hasExtensions() && extensions.dataConnectCount > 0) extensions.dataConnectList
            else emptyList(),
        )
      }
  }
}
