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

import androidx.annotation.VisibleForTesting
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.core.DataConnectAuth.AuthUid
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectGrpcMetadata.Companion.FIREBASE_AUTH_TOKEN_HEADER
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.CoroutineUtils.completedFlow
import com.google.firebase.dataconnect.util.CoroutineUtils.mergeColdAndHotFlow
import com.google.firebase.dataconnect.util.GrpcBidiFlow
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.SequencedReference
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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
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
 * @param authToken A flow of sequenced authentication tokens to use for metadata headers and to
 * monitor for changes that may require stream updates.
 * @param shouldRetry A suspending callback invoked upon connection failure to determine the
 * [RetryStrategy] (retry immediately, retry after backoff, or fail permanently by throwing).
 * @param idStringGenerator Generator used to create unique connection and request identifiers.
 * @param coroutineScope The [CoroutineScope] to whose lifetime this object belongs.
 * @param logger The [Logger] instance to use for diagnostic and debugging logs.
 */
internal class DataConnectBidiConnectStream(
  flow:
    Flow<
      GrpcBidiFlow.Event<
        StreamRequestProto, StreamResponseProto, SequencedReference<GetAuthTokenResult?>
      >
    >,
  authToken: Flow<SequencedReference<GetAuthTokenResult?>>,
  shouldRetry: suspend (Throwable) -> RetryStrategy,
  idStringGenerator: IdStringGenerator,
  private val grpcMetadata: DataConnectGrpcMetadata,
  private val coroutineScope: CoroutineScope,
  private val logger: Logger,
) {

  private val retryBackoff = RetryBackoffCalculator()

  val isPermanentlyFailed: Boolean
    get() = _permanentFailureFlow.replayCache.isNotEmpty()

  /**
   * A flow that emits `null` when [coroutineScope] is canceled, which happens when
   * [com.google.firebase.dataconnect.FirebaseDataConnect.close] is called.
   */
  private val scopeCompletedFlow = coroutineScope.completedFlow().map { null }

  /** A flow that emits an exception when the connection is permanently failed. */
  private val _permanentFailureFlow =
    MutableSharedFlow<Throwable>(
      replay = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  private val permanentFailureFlow = _permanentFailureFlow.asSharedFlow()

  private val connectionFlow: Flow<SubscriptionEvent> = run {
    val connectionStateFlow =
      MutableStateFlow<SubscriptionEvent.Connection>(
        SubscriptionEvent.Disconnected(pendingAuthToken = null)
      )
    val connectionStateUpdater = ConnectionStateUpdater(idStringGenerator)

    val sharedFlow =
      mergeGrpcAndAuth(flow, authToken)
        .mapNotNull { event ->
          with(connectionStateUpdater) { connectionStateFlow.update(event) }
          when (event) {
            is GrpcAuthMergedFlowEvent.Auth,
            GrpcAuthMergedFlowEvent.Disconnect -> null
            is GrpcAuthMergedFlowEvent.Grpc ->
              when (event.event) {
                is GrpcBidiFlow.Event.ConnectionInfo -> null
                is GrpcBidiFlow.Event.Message -> {
                  retryBackoff.reset()
                  event.event
                }
              }
          }
        }
        .map(SubscriptionEvent::Message)
        .onCompletion { throwable ->
          with(connectionStateUpdater) {
            connectionStateFlow.update(GrpcAuthMergedFlowEvent.Disconnect)
          }
          throw throwable ?: Exception("to be handled by retryWhen")
        }
        .retryWhen { cause, _ ->
          val retryStrategy =
            try {
              shouldRetry(cause)
            } catch (e: Throwable) {
              currentCoroutineContext().ensureActive()
              _permanentFailureFlow.emit(e)
              throw e
            }

          when (retryStrategy) {
            RetryStrategy.RETRY_IMMEDIATELY -> true
            RetryStrategy.RETRY_AFTER_BACKOFF -> {
              val backoffMs = retryBackoff.next()
              logger.debug {
                "waiting ${backoffMs}ms before retrying connection, " + "which failed due to $cause"
              }
              delay(backoffMs.milliseconds)
              true
            }
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
   * @param callerSdkType The type of caller that is making the request.
   * @return A [Flow] of [ExecuteResponse] objects for the subscription. The coroutine context that
   * collects the flow **MUST** have a [CallerSdkTypeElement] element. This allows each individual
   * flow collector to specify its own [CallerSdkType].
   */
  fun subscribe(
    requestId: String,
    operationName: String,
    variables: Struct,
    callerSdkType: CallerSdkType,
  ): Flow<ExecuteResponse> {
    val state = AtomicReference<SubscriptionState>(SubscriptionState.Disconnected)

    fun sendSubscribeOrResume(callerSdkType: CallerSdkType) {
      while (true) {
        when (val currentState = state.get()) {
          is SubscriptionState.Connected -> {
            currentState.enqueueSubscribeOrResume(callerSdkType)
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
        .transformToMessage(requestId, operationName, variables, callerSdkType, state)
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
            MessageOrSubscribe.Subscribed -> {
              val callerSdkTypeElement = currentCoroutineContext()[CallerSdkTypeElement]
              checkNotNull(callerSdkTypeElement) {
                "internal error c6se9nvv5w: currentCoroutineContext()[CallerSdkTypeElement]==null"
              }
              sendSubscribeOrResume(callerSdkTypeElement.callerSdkType)
            }
            is MessageOrSubscribe.Message -> emit(messageOrSubscribe)
          }
        }
        .filter { it.message.requestId == requestId }
        .mapNotNull { it.message.toExecuteResponse(it.authUid) }

    // Configure the returned flow to end gracefully when FirebaseDataConnect.close() is called, and
    // fail if the Firebase Auth user changes.
    return merge(
        subscriptionFlow,
        scopeCompletedFlow,
        permanentFailureFlow.transform { throw it },
      )
      .transformWhile {
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
      val connectionId: String,
      val outgoingRequests: SendChannel<StreamRequestProto>,
      private val hadPendingSubscription: Boolean,
      private val subscribeOrResumeSignal: ConflatedSignal<CallerSdkType>,
      private val subscribeOrResumeJob: Job,
    ) : SubscriptionState {

      fun enqueueSubscribeOrResume(callerSdkType: CallerSdkType) {
        subscribeOrResumeSignal.signal(callerSdkType)
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
          "connectionId=$connectionId, " +
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

  private fun Flow<SubscriptionEvent>.transformToMessage(
    requestId: String,
    operationName: String,
    variables: Struct,
    callerSdkType: CallerSdkType,
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
      callerSdkType = callerSdkType,
    )
  }

  private fun Flow<SubscriptionEvent>.transformToMessage(
    state: AtomicReference<SubscriptionState>,
    subscribeRequest: StreamRequestProto,
    resumeRequest: StreamRequestProto,
    cancelRequest: StreamRequestProto,
    callerSdkType: CallerSdkType,
  ): Flow<SubscriptionEvent.Message> {

    suspend fun SendChannel<StreamRequestProto>.subscribeOrResumeLoop(
      authUid: AuthUid?,
      subscribeOrResumeSignal: ConflatedSignal<CallerSdkType>,
    ) {
      var subscribed = false
      try {
        subscribeOrResumeSignal.signals.collect { callerSdkType ->
          val value = grpcMetadata.googApiClientHeaderValue(callerSdkType)
          if (!subscribed) {
            val request =
              subscribeRequest
                .toBuilder()
                .putHeaders(DataConnectGrpcMetadata.GOOG_API_CLIENT_HEADER, value)
                .build()
            trySendOrThrow(authUid, request)
            subscribed = true
          } else {
            val request =
              resumeRequest
                .toBuilder()
                .putHeaders(DataConnectGrpcMetadata.GOOG_API_CLIENT_HEADER, value)
                .build()
            trySendOrThrow(authUid, request)
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
              if (currentState.connectionId == event.connectionId) {
                break // already connected; this can occur due to an auth token update
              } else {
                error(
                  "internal error nqe9gre3ny: got event $event, " +
                    "but state=$currentState (expected state=Disconnected or " +
                    "state=Connected(connectionId=${event.connectionId}))"
                )
              }
            is SubscriptionState.Disconnected -> false
            is SubscriptionState.DisconnectedWithPendingSubscription -> true
          }

        val subscribeOrResumeSignal = ConflatedSignal<CallerSdkType>()
        if (isPendingSubscription) {
          subscribeOrResumeSignal.signal(callerSdkType)
        }
        val subscribeOrResumeJob =
          coroutineScope.launch(start = CoroutineStart.LAZY) {
            event.outgoingRequests.subscribeOrResumeLoop(
              event.authToken.ref?.authUid,
              subscribeOrResumeSignal,
            )
          }

        val newState =
          SubscriptionState.Connected(
            event.connectionId,
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
        } else {
          subscribeOrResumeJob.cancel("state update failed")
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

  enum class RetryStrategy {
    RETRY_IMMEDIATELY,
    RETRY_AFTER_BACKOFF,
  }

  companion object {

    @VisibleForTesting
    fun setReconnectPendingAuthTokenForTesting(token: SequencedReference<GetAuthTokenResult>) {
      val success = reconnectPendingAuthTokenForTesting.compareAndSet(null, token)
      check(success) {
        "setReconnectPendingAuthTokenForTesting() failed: a value is already set [b887589rta]"
      }
    }

    @VisibleForTesting
    fun unsetReconnectPendingAuthTokenForTesting(token: SequencedReference<GetAuthTokenResult>) {
      val success = reconnectPendingAuthTokenForTesting.compareAndSet(token, null)
      check(success) {
        "unsetReconnectPendingAuthTokenForTesting() failed: " +
          "the given value is NOT the one currently set [dmjgeq95a2]"
      }
    }

    private fun StreamResponseProto.toExecuteResponse(authUid: AuthUid?): ExecuteResponse? =
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

private val reconnectPendingAuthTokenForTesting =
  AtomicReference<SequencedReference<GetAuthTokenResult>>(null)

private fun mergeGrpcAndAuth(
  grpcBidiFlow:
    Flow<
      GrpcBidiFlow.Event<
        StreamRequestProto, StreamResponseProto, SequencedReference<GetAuthTokenResult?>
      >
    >,
  authToken: Flow<SequencedReference<GetAuthTokenResult?>>,
): Flow<GrpcAuthMergedFlowEvent> =
  mergeColdAndHotFlow(
    coldFlow = grpcBidiFlow.map(GrpcAuthMergedFlowEvent::Grpc),
    hotFlow = authToken.map(GrpcAuthMergedFlowEvent::Auth)
  )

private sealed interface GrpcAuthMergedFlowEvent {

  @JvmInline
  value class Grpc(
    val event:
      GrpcBidiFlow.Event<
        StreamRequestProto, StreamResponseProto, SequencedReference<GetAuthTokenResult?>
      >
  ) : GrpcAuthMergedFlowEvent {
    override fun toString() = "Grpc($event)"
  }

  @JvmInline
  value class Auth(val event: SequencedReference<GetAuthTokenResult?>) : GrpcAuthMergedFlowEvent {
    override fun toString() = "Auth($event)"
  }

  object Disconnect : GrpcAuthMergedFlowEvent {
    override fun toString() = "Disconnect"
  }
}

private sealed interface SubscriptionEvent {

  class Message(
    val connectionId: String,
    val authUid: AuthUid?,
    val message: StreamResponseProto,
  ) : SubscriptionEvent {
    constructor(
      event:
        GrpcBidiFlow.Event.Message<StreamResponseProto, SequencedReference<GetAuthTokenResult?>>
    ) : this(event.connectionId, event.connectionCookie.ref?.authUid, event.message)
    override fun toString() =
      "Message(connectionId=$connectionId, authUid=$authUid, " +
        "message=${message.toCompactString()})"
  }

  sealed interface Connection : SubscriptionEvent

  class Connected(
    val connectionId: String,
    val authToken: SequencedReference<GetAuthTokenResult?>,
    val outgoingRequests: SendChannel<StreamRequestProto>,
  ) : Connection {
    constructor(
      event:
        GrpcBidiFlow.Event.ConnectionInfo<
          StreamRequestProto, SequencedReference<GetAuthTokenResult?>
        >
    ) : this(event.connectionId, event.connectionCookie, event.outgoingRequests)

    fun withAuthToken(authToken: SequencedReference<GetAuthTokenResult?>) =
      Connected(
        connectionId = connectionId,
        authToken = authToken,
        outgoingRequests = outgoingRequests,
      )

    override fun toString() = "Connected(connectionId=$connectionId, authToken=$authToken)"
  }

  class Disconnected(
    val pendingAuthToken: SequencedReference<GetAuthTokenResult?>? = null,
  ) : Connection {
    override fun toString() = "Disconnected(pendingAuthToken=$pendingAuthToken)"
  }
}

private class ConnectionStateUpdater(private val idStringGenerator: IdStringGenerator) {

  fun MutableStateFlow<SubscriptionEvent.Connection>.update(event: GrpcAuthMergedFlowEvent) {
    update(this, event)
  }

  @JvmName("updateSubscriptionEventConnectionMutableStateFlow")
  private fun update(
    state: MutableStateFlow<SubscriptionEvent.Connection>,
    event: GrpcAuthMergedFlowEvent,
  ) {
    val currentState = state.value
    val newState = processGrpcAuthMergedFlowEvent(currentState, event) ?: return
    val updateSuccessful = state.compareAndSet(currentState, newState)

    check(updateSuccessful) {
      "internal error qen6873hdf: compareAndSet($currentState, $newState) returned false, " +
        "but expected it to return true because the state should not be updated concurrently"
    }
  }

  private fun processGrpcAuthMergedFlowEvent(
    currentState: SubscriptionEvent.Connection,
    event: GrpcAuthMergedFlowEvent,
  ): SubscriptionEvent.Connection? =
    when (event) {
      is GrpcAuthMergedFlowEvent.Auth -> processAuthEvent(currentState, event.event)
      is GrpcAuthMergedFlowEvent.Grpc -> processGrpcEvent(currentState, event.event)
      is GrpcAuthMergedFlowEvent.Disconnect ->
        when (currentState) {
          is SubscriptionEvent.Connected ->
            SubscriptionEvent.Disconnected(pendingAuthToken = currentState.authToken)
          is SubscriptionEvent.Disconnected -> currentState
        }
    }

  private fun processAuthEvent(
    currentState: SubscriptionEvent.Connection,
    sequencedAuthToken: SequencedReference<GetAuthTokenResult?>,
  ): SubscriptionEvent.Connection? =
    when (currentState) {
      is SubscriptionEvent.Connected -> processAuthEvent(currentState, sequencedAuthToken)
      is SubscriptionEvent.Disconnected -> processAuthEvent(currentState, sequencedAuthToken)
    }

  private fun processAuthEvent(
    currentState: SubscriptionEvent.Connected,
    sequencedAuthToken: SequencedReference<GetAuthTokenResult?>,
  ): SubscriptionEvent.Connected? {
    // Verify that the authUid has not changed; if so, then throw to abort the connection.
    // The caller will need to re-subscribe with the new authUid as the connection stream does
    // not support changing authUid mid-stream.
    val currentAuthUid = currentState.authToken.ref?.authUid
    val newAuthUid = sequencedAuthToken.ref?.authUid
    if (currentAuthUid != newAuthUid) {
      throw FirebaseUserChangedException("cgvra2bwg3", currentAuthUid, newAuthUid)
    }

    // Ignore outdated auth token changes.
    if (sequencedAuthToken.sequenceNumber <= currentState.authToken.sequenceNumber) {
      return null
    }

    // Do not re-send the same token, as that is wasteful.
    val oldToken = currentState.authToken.ref?.token
    val newToken = sequencedAuthToken.ref?.token
    if (oldToken == newToken) {
      return null
    }

    // Do not send an empty token, as that is wasteful too (and should never happen in practice
    // because if newToken==null and oldToken!=newToken then it must hold that
    // currentAuthUid!=newAuthUid, and should have resulted in FirebaseUserChangedException above).
    if (newToken == null) {
      return null
    }

    // Update the authToken on the stream.
    val streamRequest = authTokenUpdateStreamRequest(newToken)
    currentState.outgoingRequests.trySendOrThrow(newAuthUid, streamRequest)

    return currentState.withAuthToken(sequencedAuthToken)
  }

  private fun processAuthEvent(
    currentState: SubscriptionEvent.Disconnected,
    sequencedAuthToken: SequencedReference<GetAuthTokenResult?>,
  ): SubscriptionEvent.Disconnected? =
    if (
      currentState.pendingAuthToken != null &&
        sequencedAuthToken.sequenceNumber <= currentState.pendingAuthToken.sequenceNumber
    ) {
      null // ignore outdated auth token changes
    } else {
      // TODO: consider if we should throw here also if authUid has changed. Probably?
      SubscriptionEvent.Disconnected(pendingAuthToken = sequencedAuthToken)
    }

  private fun processGrpcEvent(
    currentState: SubscriptionEvent.Connection,
    event:
      GrpcBidiFlow.Event<
        StreamRequestProto, StreamResponseProto, SequencedReference<GetAuthTokenResult?>
      >,
  ): SubscriptionEvent.Connected? =
    when (event) {
      is GrpcBidiFlow.Event.Message -> null
      is GrpcBidiFlow.Event.ConnectionInfo -> processGrpcConnectionEvent(currentState, event)
    }

  private fun processGrpcConnectionEvent(
    currentState: SubscriptionEvent.Connection,
    event:
      GrpcBidiFlow.Event.ConnectionInfo<
        StreamRequestProto, SequencedReference<GetAuthTokenResult?>
      >,
  ): SubscriptionEvent.Connected? =
    when (currentState) {
      is SubscriptionEvent.Connected -> currentState
      is SubscriptionEvent.Disconnected -> {
        val connectedState = SubscriptionEvent.Connected(event)
        val pendingAuthToken =
          reconnectPendingAuthTokenForTesting.get() ?: currentState.pendingAuthToken
        if (pendingAuthToken == null) {
          connectedState
        } else {
          // Update the auth token on the stream in the rare case that a new auth token was provided
          // between stream connection and receiving the "connected" event.
          processAuthEvent(connectedState, pendingAuthToken) ?: connectedState
        }
      }
    }

  private fun authTokenUpdateStreamRequest(authToken: String): StreamRequestProto =
    StreamRequestProto.newBuilder()
      .setRequestId(idStringGenerator.next("auth"))
      .putHeaders(FIREBASE_AUTH_TOKEN_HEADER, authToken)
      .build()
}

private fun SendChannel<StreamRequestProto>.trySendOrThrow(
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
