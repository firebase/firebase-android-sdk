/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.CoroutineUtils.asSendChannel
import io.grpc.CallOptions
import io.grpc.Channel as GrpcChannel
import io.grpc.ClientCall
import io.grpc.Metadata as GrpcMetadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A utility object that facilitates the creation of a cold [Flow] representing a bidirectional gRPC
 * streaming call.
 *
 * This utility coordinates the sending of requests and receiving of responses using coroutines and
 * channels, and supports a [Listener] for fine-grained monitoring of the stream's lifecycle events.
 *
 * ### Key Behavioral Characteristics for Consumers:
 * * **Cold Flow**: The returned [Flow] is cold. No network connection is established, and no gRPC
 * call is initiated, until the flow collection actually starts.
 * * **Multiple Collections**: The flow can be collected multiple times, either sequentially or
 * concurrently. Each collection acts as a completely independent gRPC call with its own connection
 * and unique connection ID.
 * * **Initialization & Request Channel**: As the very first action upon collection, the flow emits
 * an [Event.Started] containing a [SendChannel] (the `outgoingRequests` channel). Consumers must
 * use this channel to send request messages to the server.
 * * **Half-Closure**: Closing the `outgoingRequests` channel will half-close the gRPC call from the
 * client side, signaling to the server that no more requests will be sent. The client will continue
 * to receive responses from the server until the server closes its side of the stream.
 * * **Backpressure**: The flow employs gRPC backpressure mechanisms by requesting only one response
 * at a time from the server. If the collector is slow to process emissions, the server will be
 * throttled, preventing memory bloat from unbuffered incoming messages.
 * * **Cancellation & Teardown**:
 * * Cancelling the coroutine scope in which the flow is being collected will immediately cancel the
 * underlying gRPC call and gracefully shut down all internal worker coroutines.
 * * If the server closes the call exceptionally, the flow will throw the corresponding exception to
 * the collector. If closed normally, the flow completes normally.
 */
internal object GrpcBidiFlow {

  /** Represents events emitted by the [Flow] created by [GrpcBidiFlow.create]. */
  sealed interface Event<in RequestT, out ResponseT> {
    /**
     * Emitted once when the gRPC flow collection starts.
     *
     * It provides a [SendChannel] that the caller can use to send requests to the server. Closing
     * this channel will half-close the gRPC stream from the client side.
     *
     * @property connectionId A unique identifier for this particular flow collection; it's provided
     * here mainly for debugging purposes, especially correlating with [Listener] callbacks.
     * @property headers a copy of the request headers sent when opening the connection; this copy
     * is owned by the [Started] object and may be freely modified without affecting the gRPC
     * connection in any way.
     * @property outgoingRequests The channel to send requests to the server.
     */
    class Started<in RequestT>(
      val connectionId: String,
      val headers: GrpcMetadata,
      val outgoingRequests: SendChannel<RequestT>,
    ) : Event<RequestT, Nothing> {
      override fun toString() = "GrpcBidiFlow.Event.Started(connectionId=$connectionId)"
    }

    /**
     * Emitted when a response message is received from the server.
     *
     * @property message The response message received from the server.
     */
    @JvmInline
    value class Message<out ResponseT>(val message: ResponseT) : Event<Any?, ResponseT> {
      override fun toString() = "GrpcBidiFlow.Event.Message(message=$message)"
    }
  }

  /**
   * A listener interface for monitoring the low-level lifecycle events of a bidirectional gRPC
   * stream.
   *
   * **Threading and Blocking Behavior:** All callbacks defined in this interface are invoked
   * synchronously from the critical path of the stream's execution (either from the gRPC thread
   * pool or from the coroutine dispatchers). Implementations should return as quickly as possible
   * and avoid performing any blocking or long-running operations. Blocking inside these callbacks
   * will directly block progress of the stream (either stalling message sending or receiving).
   *
   * **Thread Safety:** The callbacks may be invoked on different threads, and even concurrently,
   * and the exact threads used for each callback is undefined. Implementations must be thread-safe.
   *
   * **Exception Handling:** Implementations must strive to never throw exceptions from these
   * callbacks. Any thrown will propagate into the stream's internal coroutine scope or gRPC
   * listener, which will negatively impact the gRPC connection in undefined ways (typically
   * resulting in abnormal termination of the call and/or coroutine cancellation).
   */
  interface Listener<RequestT, ResponseT> {
    fun collectStarted(connectionId: String)
    fun collectCompleted(connectionId: String, exception: Throwable?)

    fun connectionStarting(
      connectionId: String,
      method: MethodDescriptor<RequestT, ResponseT>,
      callOptions: CallOptions,
      headers: GrpcMetadata,
    )

    fun sendingMessage(connectionId: String, message: RequestT)
    fun sendingMessagesComplete(connectionId: String)
    fun sendingMessagesFailed(connectionId: String, exception: Throwable)

    fun receivedMessage(connectionId: String, message: ResponseT)
    fun receivingMessagesComplete(connectionId: String)
    fun receivingMessagesFailed(connectionId: String, exception: Throwable)

    fun onReady(connectionId: String)

    fun onMessage(connectionId: String, message: ResponseT)

    fun onClose(
      connectionId: String,
      status: Status,
      trailers: GrpcMetadata,
      calculatedCause: Throwable?,
    )
  }

  /**
   * Creates a cold [Flow] that executes a bidirectional streaming gRPC method.
   *
   * The returned flow, when collected, will:
   * 1. Start a new gRPC [ClientCall] for the specified [method].
   * 2. Emit an [Event.Started] containing a [SendChannel] for sending requests.
   * 3. Concurrently read responses from the server and emit them as [Event.Message].
   * 4. Coordinate sending and receiving loops, ensuring proper backpressure and cancellation.
   *
   * Collecting the flow may fail with exceptions if the underlying call fails or is cancelled.
   *
   * @param grpcChannel The gRPC [GrpcChannel] to use for the call.
   * @param method The descriptor of the bidirectional streaming method to call.
   * @param callOptions The options to customize the call.
   * @param headers The metadata headers to send with the initial request.
   * @param idStringGenerator Generator used to create unique connection identifiers.
   * @param listener Optional listener to receive lifecycle callbacks.
   * @return A cold flow of [Event]s.
   * @throws IllegalArgumentException if [method] is not a bidirectional streaming RPC.
   */
  fun <RequestT, ResponseT> create(
    grpcChannel: GrpcChannel,
    method: MethodDescriptor<RequestT, ResponseT>,
    callOptions: CallOptions,
    headers: (connectionId: String) -> GrpcMetadata,
    idStringGenerator: IdStringGenerator,
    listener: Listener<RequestT, ResponseT>? = null,
  ): Flow<Event<RequestT, ResponseT>> {
    require(method.type == MethodDescriptor.MethodType.BIDI_STREAMING) {
      "method.type is ${method.type} but BIDI_STREAMING is required"
    }

    return flow {
      val connectionId = idStringGenerator.next("con")
      listener?.collectStarted(connectionId)

      val requestHeaders = headers(connectionId)
      val requestChannel = Channel<RequestT>()
      emit(Event.Started(connectionId, requestHeaders.copy(), requestChannel.asSendChannel()))

      val clientCall: ClientCall<RequestT, ResponseT> = grpcChannel.newCall(method, callOptions)
      val readiness = Readiness(clientCall)
      suspend fun ClientCall<*, *>.suspendUntilReady() {
        check(this === clientCall)
        readiness.suspendUntilReady()
      }

      /*
       * We maintain a buffer of size 1 so onMessage never has to block: it only gets called after
       * we request a response from the server, which only happens when responses is empty and
       * there is room in the buffer.
       */
      val responses = Channel<ResponseT>(1)

      listener?.connectionStarting(connectionId, method, callOptions, requestHeaders)
      clientCall.start(
        object : ClientCall.Listener<ResponseT>() {
          override fun onMessage(message: ResponseT) {
            listener?.onMessage(connectionId, message)
            responses.trySend(message).onFailure { e ->
              throw e ?: AssertionError("onMessage should never be called until responses is ready")
            }
          }

          override fun onClose(status: Status, trailers: GrpcMetadata) {
            val cause =
              when {
                status.isOk -> null
                status.cause is CancellationException -> status.cause
                else -> status.asException(trailers)
              }
            listener?.onClose(connectionId, status, trailers, cause)
            responses.close(cause)
          }

          override fun onReady() {
            listener?.onReady(connectionId)
            readiness.onReady()
          }
        },
        requestHeaders,
      )

      coroutineScope {
        if (listener !== null) {
          coroutineContext[Job]?.invokeOnCompletion { exception ->
            listener.collectCompleted(connectionId, exception)
          }
        }

        val sendJob =
          launch(CoroutineName("SendMessage worker for ${method.fullMethodName}")) {
            val sendingResult = runCatching {
              clientCall.suspendUntilReady()
              for (request in requestChannel) {
                listener?.sendingMessage(connectionId, request)
                clientCall.sendMessage(request)
                clientCall.suspendUntilReady()
              }

              listener?.sendingMessagesComplete(connectionId)

              clientCall.halfClose()
            }

            sendingResult.onFailure { exception ->
              listener?.sendingMessagesFailed(connectionId, exception)
              clientCall.cancel("Collection of requests completed exceptionally", exception)
            }

            sendingResult.getOrThrow()
          }

        val receiveResult = runCatching {
          clientCall.request(1)
          for (response in responses) {
            listener?.receivedMessage(connectionId, response)
            emit(Event.Message(response))
            clientCall.request(1)
          }
          listener?.receivingMessagesComplete(connectionId)
        }

        receiveResult.onFailure { exception ->
          listener?.receivingMessagesFailed(connectionId, exception)

          withContext(NonCancellable) {
            sendJob.cancel("Collection of responses completed exceptionally", exception)
            sendJob.join()
            // we want sender to be done cancelling before we cancel clientCall, or it might try
            // sending to a dead call, which results in ugly exception messages
            clientCall.cancel("Collection of responses completed exceptionally", exception)
          }
        }

        receiveResult.getOrThrow()

        if (!sendJob.isCompleted) {
          sendJob.cancel("Collection of responses completed before collection of requests")
        }
      }
    }
  }

  private class Readiness(private val clientCall: ClientCall<*, *>) {
    // A CONFLATED channel never suspends to send, and two notifications of readiness are equivalent
    // to one
    private val channel = Channel<Unit>(Channel.CONFLATED)

    fun onReady() {
      channel.trySend(Unit).onFailure { e ->
        throw e
          ?: AssertionError(
            "Should be impossible; a CONFLATED channel should never return false on offer"
          )
      }
    }

    suspend fun suspendUntilReady() {
      while (!clientCall.isReady) {
        channel.receive()
      }
    }
  }
}

/**
 * An implementation of [GrpcBidiFlow.Listener] that simply performs debug logging on each callback.
 *
 * This class is intended to be used only while debugging low-level gRPC connection and connection
 * lifecycle issues. Using this listener will spam the logs with tonnes of messages, most of which
 * are totally irrelevant when debugging issues at layers above gRPC. Notably, **DO NOT** register
 * this listener in production builds as it will cause extreme log spam when customers enabled debug
 * logging, not to mention the CPU processing overhead of all of this logging.
 */
internal class LoggingGrpcBidiFlowListener<RequestT, ResponseT>(
  private val logger: Logger,
  private val formatter: Formatter<RequestT, ResponseT>? = null,
) : GrpcBidiFlow.Listener<RequestT, ResponseT> {

  interface Formatter<RequestT, ResponseT> {
    fun connectionStartingHeaders(headers: GrpcMetadata): String
    fun onCloseTrailers(trailers: GrpcMetadata): String
    fun sendingMessageMessage(message: RequestT): String
    fun receivedMessageMessage(message: ResponseT): String
    fun onMessageMessage(message: ResponseT): String
  }

  override fun collectStarted(connectionId: String) =
    logger.debug { "collectStarted($connectionId)" }

  override fun collectCompleted(connectionId: String, exception: Throwable?) =
    logger.debug(exception) { "collectCompleted($connectionId, exception=$exception)" }

  override fun connectionStarting(
    connectionId: String,
    method: MethodDescriptor<RequestT, ResponseT>,
    callOptions: CallOptions,
    headers: GrpcMetadata,
  ) =
    logger.debug {
      val formattedHeaders = formatter?.connectionStartingHeaders(headers) ?: headers
      "connectionStarting($connectionId, method=${method.fullMethodName}, " +
        "callOptions=$callOptions, headers=$formattedHeaders)"
    }

  override fun sendingMessage(connectionId: String, message: RequestT) =
    logger.debug {
      val formattedMessage = formatter?.sendingMessageMessage(message) ?: message
      "sendingMessage($connectionId, message=$formattedMessage)"
    }

  override fun sendingMessagesComplete(connectionId: String) =
    logger.debug { "sendingMessagesComplete($connectionId)" }

  override fun sendingMessagesFailed(connectionId: String, exception: Throwable) =
    logger.debug(exception) { "sendingMessagesFailed($connectionId, exception=$exception)" }

  override fun receivedMessage(connectionId: String, message: ResponseT) =
    logger.debug {
      val formattedMessage = formatter?.receivedMessageMessage(message) ?: message
      "receivedMessage($connectionId, message=$formattedMessage)"
    }

  override fun receivingMessagesComplete(connectionId: String) =
    logger.debug { "receivingMessagesComplete($connectionId)" }

  override fun receivingMessagesFailed(connectionId: String, exception: Throwable) =
    logger.debug(exception) { "receivingMessagesFailed($connectionId, exception=$exception)" }

  override fun onMessage(connectionId: String, message: ResponseT) =
    logger.debug {
      val formattedMessage = formatter?.onMessageMessage(message) ?: message
      "onMessage($connectionId, message=$formattedMessage)"
    }

  override fun onClose(
    connectionId: String,
    status: Status,
    trailers: GrpcMetadata,
    calculatedCause: Throwable?,
  ) =
    logger.debug {
      val formattedTrailers = formatter?.onCloseTrailers(trailers) ?: trailers
      "onClose($connectionId, status=$status, trailers=$formattedTrailers, " +
        "calculatedCause=$calculatedCause)"
    }

  override fun onReady(connectionId: String) {
    logger.debug { "onReady($connectionId)" }
  }
}
