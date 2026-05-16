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
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.CoroutineUtils.createChildSupervisorScope
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
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
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
 * @param coroutineScope The [CoroutineScope] used to launch background collection and manage stream
 * lifecycle.
 * @param logger The [Logger] used for debug and error logging.
 */
@ExperimentalRealtimeQueries
internal class DataConnectBidiConnectStream(
  flow: Flow<GrpcBidiFlow.Event<StreamRequestProto, StreamResponseProto>>,
  coroutineScope: CoroutineScope,
  private val logger: Logger,
) {

  private val outgoingRequestsDeferred = CompletableDeferred<SendChannel<StreamRequestProto>>()

  private val state =
    MutableStateFlow<State>(
      run {
        val collectCoroutineScope = coroutineScope.createChildSupervisorScope(logger)

        val incomingResponsesSharedFlow =
          flow
            .mapNotNull<_, IncomingResponse> { event ->
              when (event) {
                is GrpcBidiFlow.Event.Message -> IncomingResponse.Message(event.message)
                is GrpcBidiFlow.Event.Started -> {
                  outgoingRequestsDeferred.complete(event.outgoingRequests)
                  null
                }
              }
            }
            .onCompletion { throwable ->
              if (throwable === null) {
                emit(IncomingResponse.Completed(null))
              }
            }
            .catch { emit(IncomingResponse.Completed(throwable = it)) }
            .buffer(capacity = Channel.UNLIMITED)
            .shareIn(collectCoroutineScope, started = SharingStarted.WhileSubscribed(), replay = 1)

        State.Open(
          outgoingRequests = outgoingRequestsDeferred,
          incomingResponses = incomingResponsesSharedFlow,
          coroutineScope = collectCoroutineScope
        )
      }
    )

  /**
   * Closes the bidirectional stream gracefully.
   *
   * This method initiates the closure of the internal coroutine scope used for collecting incoming
   * responses and suspends until the closure has completed. Once closed, the stream cannot be
   * reopened and subsequent calls to [subscribe] will throw an exception.
   *
   * This method is safe to call many times. All calls will suspend until the closure has completed,
   * just like the first call will. If the closure has already completed then this method will
   * return immediately as if successful.
   */
  suspend fun close() {
    logger.debug { "close()" }

    while (true) {
      val currentState = state.value

      val newState =
        when (currentState) {
          is State.Open -> {
            currentState.coroutineScope.cancel(
              "DataConnectBidiConnectStream.close() called [fvj7hnfksd]"
            )
            State.Closing(currentState.coroutineScope)
          }
          is State.Closing -> {
            currentState.coroutineScope.coroutineContext.job.join()
            State.Closed
          }
          State.Closed -> return
        }

      state.compareAndSet(currentState, newState)
    }
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
    val streams =
      when (val currentState = this.state.value) {
        is State.Open -> currentState
        is State.Closing,
        State.Closed -> throw CancellationException("DataConnectBidiConnectStream is closed")
      }

    val subscriptionStateManager =
      SubscriptionStateManager(
        requestId = requestId,
        operationName = operationName,
        variables = variables,
      )

    return flow {
      val subscriptionMutex = Mutex()
      val subscription = subscriptionStateManager.Subscriber()

      emitAll(
        streams.incomingResponses
          .onSubscription { emit(IncomingResponse.Subscribed) }
          .transformWhile { incomingResponse ->
            when (incomingResponse) {
              is IncomingResponse.Subscribed -> {
                val outgoingRequests = streams.outgoingRequests.await()
                subscriptionMutex.withLock { subscription.subscribe(outgoingRequests) }
              }
              is IncomingResponse.Message -> {
                if (incomingResponse.streamResponse.requestId != requestId) {
                  true
                } else {
                  val executeResponse = incomingResponse.streamResponse.toExecuteResponse()
                  if (executeResponse !== null) {
                    emit(executeResponse)
                  }
                  !incomingResponse.streamResponse.cancelled
                }
              }
              is IncomingResponse.Completed -> {
                incomingResponse.throwable?.let {
                  if (it !is CancellationException) {
                    throw it
                  }
                }
                false
              }
            }
          }
          .onCompletion {
            // If streams.outgoingRequests is _not_ completed then the upstream Flow didn't even
            // receive the "Started" event yet, and never will; therefore, it's guaranteed that
            // subscription.subscribe() was never called upstream, and we, therefore, do not need to
            // call subscription.unsubscribe(). In fact, streams.outgoingRequests.await() would
            // deadlock since the "Started" event will never be received.
            if (streams.outgoingRequests.isCompleted) {
              withContext(NonCancellable) {
                val outgoingRequests = streams.outgoingRequests.await()
                subscriptionMutex.withLock { subscription.unsubscribe(outgoingRequests) }
              }
            }
          }
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
   * Represents the current operational state of the [DataConnectBidiConnectStream].
   *
   * State transitions flow from [Open] -> [Closing] -> [Closed].
   */
  private sealed interface State {
    /**
     * The stream is fully operational and accepting new subscriptions. This is the initial state of
     * a newly-created [DataConnectBidiConnectStream] object.
     *
     * @property outgoingRequests The channel to which to send requests.
     * @property incomingResponses The shared flow containing processed [IncomingResponse] signals.
     * message _before_ the message is emitted from [incomingResponses].
     * @property coroutineScope The scope actively managing the collection of incoming responses;
     * this scope must be canceled by [DataConnectBidiConnectStream.close].
     */
    class Open(
      val outgoingRequests: Deferred<SendChannel<StreamRequestProto>>,
      val incomingResponses: SharedFlow<IncomingResponse>,
      val coroutineScope: CoroutineScope,
    ) : State {
      override fun toString() = "Open"
    }

    /**
     * The stream is in the process of shutting down and waiting for active jobs to complete.
     *
     * @property coroutineScope The scope that is undergoing cancellation.
     */
    class Closing(val coroutineScope: CoroutineScope) : State {
      override fun toString() = "Closing"
    }

    /** The stream is completely shut down and inactive. */
    object Closed : State {
      override fun toString() = "Closed"
    }
  }

  /**
   * Represents an internal wrapper around incoming server responses and lifecycle signals.
   *
   * This sealed interface allows the internal [SharedFlow] to multiplex actual response data
   * alongside control signals like completion, subscriber readiness, and buffer flushes.
   */
  private sealed interface IncomingResponse {

    /** Represents a standard data response from the server. */
    class Message(val streamResponse: StreamResponseProto) : IncomingResponse {
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
    class Completed(val throwable: Throwable?) : IncomingResponse {
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
    object Subscribed : IncomingResponse
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

    private var subscriberCount = 0

    inner class Subscriber {
      private var subscribed = false

      fun subscribe(outgoingRequests: SendChannel<StreamRequestProto>): Boolean {
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

      fun unsubscribe(outgoingRequests: SendChannel<StreamRequestProto>) {
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
