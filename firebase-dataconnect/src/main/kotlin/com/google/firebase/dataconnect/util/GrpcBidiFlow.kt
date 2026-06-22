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
import com.google.firebase.dataconnect.util.coroutines.ConflatedSignal
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
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
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
 * an [Event.ConnectionInfo] containing a [SendChannel] (the `outgoingRequests` channel). Consumers
 * must use this channel to send request messages to the server.
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
  sealed class Event<in RequestT, out ResponseT, out ConnectionCookie>(
    val connectionId: String,
    val connectionCookie: ConnectionCookie,
  ) {
    /**
     * Emitted once when the gRPC flow collection starts.
     *
     * It provides a [SendChannel] that the caller can use to send requests to the server. Closing
     * this channel will half-close the gRPC stream from the client side.
     */
    class ConnectionInfo<in RequestT, out ConnectionCookie>(
      connectionId: String,
      connectionCookie: ConnectionCookie,
      val outgoingRequests: SendChannel<RequestT>,
    ) : Event<RequestT, Nothing, ConnectionCookie>(connectionId, connectionCookie) {
      override fun toString() =
        "ConnectionInfo(connectionId=$connectionId, connectionCookie=$connectionCookie)"
    }

    /** Emitted when a response message is received from the server. */
    class Message<out ResponseT, out ConnectionCookie>(
      connectionId: String,
      connectionCookie: ConnectionCookie,
      val message: ResponseT,
    ) : Event<Any?, ResponseT, ConnectionCookie>(connectionId, connectionCookie) {
      override fun toString() =
        "Message(connectionId=$connectionId, connectionCookie=$connectionCookie, message=$message)"
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

    fun collectStarted(connectionId: String): CollectorListener<RequestT, ResponseT>

    interface CollectorListener<RequestT, ResponseT> {
      fun collectCompleted(exception: Throwable?)

      fun connectionStarting(
        method: MethodDescriptor<RequestT, ResponseT>,
        callOptions: CallOptions,
        headers: GrpcMetadata?,
      )

      fun sendingMessage(message: RequestT)
      fun sendingMessagesComplete()
      fun sendingMessagesFailed(exception: Throwable)

      fun receivedMessage(message: ResponseT)
      fun receivingMessagesComplete()
      fun receivingMessagesFailed(exception: Throwable)

      fun onCallReady()
      fun onCallMessage(message: ResponseT)
      fun onCallClose(status: Status, trailers: GrpcMetadata, calculatedCause: Throwable?)
    }
  }

  data class HeadersResult<ConnectionCookie>(
    val headers: GrpcMetadata?,
    val connectionCookie: ConnectionCookie,
  )

  /**
   * Creates a cold [Flow] that executes a bidirectional streaming gRPC method.
   *
   * The returned flow, when collected, will:
   * 1. Start a new gRPC [ClientCall] for the specified [method].
   * 2. Emit an [Event.ConnectionInfo] containing a [SendChannel] for sending requests.
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
   * @param initRequests Optional requests to send on the [SendChannel] that will be given to the
   * downstream collector via the [Event.ConnectionInfo] event ahead of the downstream collector.
   * @param listener Optional listener to receive lifecycle callbacks.
   * @return A cold flow of [Event]s.
   * @throws IllegalArgumentException if [method] is not a bidirectional streaming RPC.
   */
  fun <RequestT, ResponseT, ConnectionCookie> create(
    grpcChannel: GrpcChannel,
    method: MethodDescriptor<RequestT, ResponseT>,
    callOptions: CallOptions,
    headers: suspend (connectionId: String) -> HeadersResult<ConnectionCookie>,
    idStringGenerator: IdStringGenerator,
    initRequests: Iterable<RequestT> = emptyList(),
    listener: Listener<RequestT, ResponseT>? = null,
  ): Flow<Event<RequestT, ResponseT, ConnectionCookie>> {
    require(method.type == MethodDescriptor.MethodType.BIDI_STREAMING) {
      "method.type is ${method.type} but BIDI_STREAMING is required"
    }

    // Save a local, immutable copy to prevent future changes to the list.
    val initRequestsLists = initRequests.toList()

    return flow {
      val connectionId = idStringGenerator.next("con")
      val collectionListener = listener?.collectStarted(connectionId)

      val requestChannel = Channel<RequestT>(UNLIMITED)
      initRequestsLists.forEachIndexed { index, initRequest: RequestT ->
        requestChannel.trySend(initRequest).onFailure { exception ->
          throw exception
            ?: error(
              "internal error pkynh7rc22: requestChannel.trySend(initRequest) " +
                "should not have failed because `requestChannel` was created " +
                "with capacity=UNLIMITED; connectionId=$connectionId, index=$index, " +
                "initRequestsLists.size=${initRequestsLists.size}, initRequest=$initRequest"
            )
        }
      }

      val requestHeaders: GrpcMetadata?
      val connectionCookie: ConnectionCookie
      headers(connectionId).let { result ->
        requestHeaders = result.headers?.copy()
        connectionCookie = result.connectionCookie
      }
      emit(Event.ConnectionInfo(connectionId, connectionCookie, requestChannel.asSendChannel()))

      val clientCall: ClientCall<RequestT, ResponseT> = grpcChannel.newCall(method, callOptions)

      val clientCallReadySignal = ConflatedSignal()
      suspend fun suspendUntilClientCallReady() {
        // Spurious "ready" signals are possible, so check clientCall.isReady to verify.
        // See the documentation for ClientCall.Listener.onReady() for details.
        while (!clientCall.isReady) {
          clientCallReadySignal.await()
        }
      }

      /*
       * We maintain a buffer of size 1 so onMessage never has to block: it only gets called after
       * we request a response from the server, which only happens when responses is empty and
       * there is room in the buffer.
       */
      val responses = Channel<ResponseT>(1)

      collectionListener?.connectionStarting(method, callOptions, requestHeaders)
      clientCall.start(
        object : ClientCall.Listener<ResponseT>() {
          override fun onMessage(message: ResponseT) {
            collectionListener?.onCallMessage(message)
            responses.trySend(message).onFailure { exception ->
              throw exception
                ?: error(
                  "internal error wtvhy3j987: responses.trySend(message) should not have failed " +
                    "because onMessage() should never be called until `responses` is ready; " +
                    "connectionId=$connectionId, message=$message"
                )
            }
          }

          override fun onClose(status: Status, trailers: GrpcMetadata) {
            val cause =
              when {
                status.isOk -> null
                status.cause is CancellationException -> status.cause
                else -> status.asException(trailers)
              }
            collectionListener?.onCallClose(status, trailers, cause)
            responses.close(cause)
          }

          override fun onReady() {
            collectionListener?.onCallReady()
            clientCallReadySignal.signal()
          }
        },
        requestHeaders ?: GrpcMetadata(),
      )

      coroutineScope {
        if (listener !== null) {
          coroutineContext[Job]?.invokeOnCompletion { exception ->
            collectionListener?.collectCompleted(exception)
          }
        }

        val sendJob =
          launch(CoroutineName("SendMessage worker for ${method.fullMethodName}")) {
            val sendingResult = runCatching {
              suspendUntilClientCallReady()
              for (request in requestChannel) {
                collectionListener?.sendingMessage(request)
                clientCall.sendMessage(request)
                suspendUntilClientCallReady()
              }

              collectionListener?.sendingMessagesComplete()

              clientCall.halfClose()
            }

            sendingResult.onFailure { exception ->
              collectionListener?.sendingMessagesFailed(exception)
              clientCall.cancel(
                "Collection of requests for connectionId=$connectionId completed exceptionally",
                exception
              )
            }

            sendingResult.getOrThrow()
          }

        val receiveResult = runCatching {
          clientCall.request(1)
          for (response in responses) {
            collectionListener?.receivedMessage(response)
            emit(Event.Message(connectionId, connectionCookie, response))
            clientCall.request(1)
          }
          collectionListener?.receivingMessagesComplete()
        }

        receiveResult.onFailure { exception ->
          collectionListener?.receivingMessagesFailed(exception)

          withContext(NonCancellable) {
            sendJob.cancel(
              "Collection of responses for connectionId=$connectionId completed exceptionally",
              exception
            )
            sendJob.join()
          }

          // we want sender to be done cancelling before we cancel clientCall, or it might try
          // sending to a dead call, which results in ugly exception messages
          clientCall.cancel(
            "Collection of responses for connectionId=$connectionId completed exceptionally",
            exception
          )
        }

        receiveResult.getOrThrow()

        if (!sendJob.isCompleted) {
          sendJob.cancel(
            "Collection of responses completed before collection of requests " +
              "for connectionId=$connectionId"
          )
        }
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
@Suppress("unused")
internal class LoggingGrpcBidiFlowListener<RequestT, ResponseT>(
  private val logger: Logger,
  formatter: GrpcBidiFlowListenerMessageFormatter.Formatter<RequestT, ResponseT>? = null,
) : GrpcBidiFlow.Listener<RequestT, ResponseT> {

  private val formatter = GrpcBidiFlowListenerMessageFormatter(formatter)

  override fun collectStarted(connectionId: String): CollectorListenerImpl {
    logger.debug { formatter.collectStarted(connectionId) }
    return CollectorListenerImpl(connectionId)
  }

  inner class CollectorListenerImpl(private val connectionId: String) :
    GrpcBidiFlow.Listener.CollectorListener<RequestT, ResponseT> {

    override fun collectCompleted(exception: Throwable?) {
      logger.debug(exception) { formatter.collectCompleted(connectionId, exception) }
    }

    override fun connectionStarting(
      method: MethodDescriptor<RequestT, ResponseT>,
      callOptions: CallOptions,
      headers: GrpcMetadata?,
    ) {
      logger.debug { formatter.connectionStarting(connectionId, method, callOptions, headers) }
    }

    override fun sendingMessage(message: RequestT) {
      logger.debug { formatter.sendingMessage(connectionId, message) }
    }

    override fun sendingMessagesComplete() {
      logger.debug { formatter.sendingMessagesComplete(connectionId) }
    }

    override fun sendingMessagesFailed(exception: Throwable) {
      logger.debug(exception) { formatter.sendingMessagesFailed(connectionId, exception) }
    }

    override fun receivedMessage(message: ResponseT) {
      logger.debug { formatter.receivedMessage(connectionId, message) }
    }

    override fun receivingMessagesComplete() {
      logger.debug { formatter.receivingMessagesComplete(connectionId) }
    }

    override fun receivingMessagesFailed(exception: Throwable) {
      logger.debug(exception) {
        "[cid=$connectionId] receivingMessagesFailed(exception=$exception)"
      }
    }

    override fun onCallMessage(message: ResponseT) {
      logger.debug { formatter.onCallMessage(connectionId, message) }
    }

    override fun onCallClose(
      status: Status,
      trailers: GrpcMetadata,
      calculatedCause: Throwable?,
    ) {
      logger.debug(calculatedCause) {
        formatter.onCallClose(connectionId, status, trailers, calculatedCause)
      }
    }

    override fun onCallReady() {
      logger.debug { formatter.onCallReady(connectionId) }
    }
  }
}

/**
 * An implementation of [GrpcBidiFlow.Listener] that simply calls `println` on each callback.
 *
 * This class is intended to be used only while debugging low-level gRPC connection and connection
 * lifecycle issues. Using this listener will spam stdout (or logcat on an Android device) with
 * tonnes of messages, most of which are totally irrelevant when debugging issues at layers above
 * gRPC. Notably, **DO NOT** register this listener in production builds as it will cause extreme
 * log spam, not to mention the CPU processing overhead of all of this logging.
 */
@Suppress("unused")
internal class PrintlnGrpcBidiFlowListener<RequestT, ResponseT>(
  formatter: GrpcBidiFlowListenerMessageFormatter.Formatter<RequestT, ResponseT>? = null,
) : GrpcBidiFlow.Listener<RequestT, ResponseT> {

  private val formatter = GrpcBidiFlowListenerMessageFormatter(formatter)

  override fun collectStarted(connectionId: String): CollectorListenerImpl {
    println(formatter.collectStarted(connectionId))
    return CollectorListenerImpl(connectionId)
  }

  inner class CollectorListenerImpl(private val connectionId: String) :
    GrpcBidiFlow.Listener.CollectorListener<RequestT, ResponseT> {

    override fun collectCompleted(exception: Throwable?) {
      println(formatter.collectCompleted(connectionId, exception))
    }

    override fun connectionStarting(
      method: MethodDescriptor<RequestT, ResponseT>,
      callOptions: CallOptions,
      headers: GrpcMetadata?,
    ) {
      println(formatter.connectionStarting(connectionId, method, callOptions, headers))
    }

    override fun sendingMessage(message: RequestT) {
      println(formatter.sendingMessage(connectionId, message))
    }

    override fun sendingMessagesComplete() {
      println(formatter.sendingMessagesComplete(connectionId))
    }

    override fun sendingMessagesFailed(exception: Throwable) {
      println(formatter.sendingMessagesFailed(connectionId, exception))
    }

    override fun receivedMessage(message: ResponseT) {
      println(formatter.receivedMessage(connectionId, message))
    }

    override fun receivingMessagesComplete() {
      println(formatter.receivingMessagesComplete(connectionId))
    }

    override fun receivingMessagesFailed(exception: Throwable) {
      println("[cid=$connectionId] receivingMessagesFailed(exception=$exception)")
    }

    override fun onCallMessage(message: ResponseT) {
      println(formatter.onCallMessage(connectionId, message))
    }

    override fun onCallClose(
      status: Status,
      trailers: GrpcMetadata,
      calculatedCause: Throwable?,
    ) {
      println(formatter.onCallClose(connectionId, status, trailers, calculatedCause))
    }

    override fun onCallReady() {
      println(formatter.onCallReady(connectionId))
    }
  }
}

/** Formats string messages in a standard way for [GrpcBidiFlow.Listener] logging. */
internal class GrpcBidiFlowListenerMessageFormatter<RequestT, ResponseT>(
  private val formatter: Formatter<RequestT, ResponseT>? = null,
) {

  open class Formatter<RequestT, ResponseT> {
    open fun connectionStartingHeaders(headers: GrpcMetadata?): Any? = headers
    open fun onCloseTrailers(trailers: GrpcMetadata): Any? = trailers
    open fun request(message: RequestT): Any? = message
    open fun response(message: ResponseT): Any? = message
  }

  fun collectStarted(connectionId: String): String = "collectStarted(cid=$connectionId)"

  fun collectCompleted(connectionId: String, exception: Throwable?): String =
    "[cid=$connectionId] collectCompleted(exception=$exception)"

  fun connectionStarting(
    connectionId: String,
    method: MethodDescriptor<RequestT, ResponseT>,
    callOptions: CallOptions,
    headers: GrpcMetadata?,
  ): String {
    val formattedHeaders = formatter?.connectionStartingHeaders(headers) ?: headers
    return "[cid=$connectionId] connectionStarting(method=${method.fullMethodName}, " +
      "callOptions=$callOptions, headers=$formattedHeaders)"
  }

  fun sendingMessage(connectionId: String, message: RequestT): String {
    val formattedMessage = formatter?.request(message) ?: message
    return "[cid=$connectionId] sendingMessage(message=$formattedMessage)"
  }

  fun sendingMessagesComplete(connectionId: String): String =
    "[cid=$connectionId] sendingMessagesComplete()"

  fun sendingMessagesFailed(connectionId: String, exception: Throwable): String =
    "[cid=$connectionId] sendingMessagesFailed(exception=$exception)"

  fun receivedMessage(connectionId: String, message: ResponseT): String {
    val formattedMessage = formatter?.response(message) ?: message
    return "[cid=$connectionId] receivedMessage(message=$formattedMessage)"
  }

  fun receivingMessagesComplete(connectionId: String): String =
    "[cid=$connectionId] receivingMessagesComplete()"

  fun receivingMessagesFailed(connectionId: String, exception: Throwable): String =
    "[cid=$connectionId] receivingMessagesFailed(exception=$exception)"

  fun onCallMessage(connectionId: String, message: ResponseT): String {
    val formattedMessage = formatter?.response(message) ?: message
    return "[cid=$connectionId] onCallMessage(message=$formattedMessage)"
  }

  fun onCallClose(
    connectionId: String,
    status: Status,
    trailers: GrpcMetadata,
    calculatedCause: Throwable?,
  ): String {
    val formattedTrailers = formatter?.onCloseTrailers(trailers) ?: trailers
    return "[cid=$connectionId] onCallClose(status=$status, trailers=$formattedTrailers, " +
      "calculatedCause=$calculatedCause)"
  }

  fun onCallReady(connectionId: String): String = "[cid=$connectionId] onCallReady()"
}
