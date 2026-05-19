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

@file:SharedWithAndroidTest

package com.google.firebase.dataconnect.testutil

import google.firebase.dataconnect.proto.ConnectorStreamServiceGrpc.ConnectorStreamServiceImplBase
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import io.grpc.InsecureServerCredentials
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.okhttp.OkHttpServerBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * An in-process gRPC server for testing Firebase Data Connect streaming features. It starts a
 * server on a random port and exposes all events (calls, requests, errors) via a [SharedFlow] for
 * verification in tests.
 */
class InProcessDataConnectGrpcStreamingServer : AutoCloseable {

  private val _events = MutableSharedFlow<Event>(extraBufferCapacity = UNLIMITED)

  /**
   * A flow of events that occur on this server.
   *
   * Tests can collect this flow to verify the behavior of the client.
   *
   * In order to influence the server's behavior in response to events, register a listener via
   * [setListener].
   */
  val events: SharedFlow<Event> = _events.asSharedFlow()

  private val _port = AtomicInteger(-1)

  /**
   * The TCP port to which the gRPC server is bound and on which it is listening.
   *
   * Will be -1 before [open] has been called.
   */
  val port: Int
    get() = _port.get()

  /**
   * The gRPC [Server] started by [open].
   *
   * @throws IllegalStateException if accessed before [open] or after [close].
   */
  val grpcServer: Server
    get() =
      when (val currentState = state.get()) {
        is State.Unopened -> error("open() has not yet been called [a4zarrmvzd]")
        is State.Opening -> currentState.server
        is State.Opened -> currentState.server
        is State.Closing,
        State.Closed -> error("close() has been called [s6gt7tmktz]")
      }

  private class EventHandler(
    private val events: MutableSharedFlow<Event>,
    private val listener: AtomicReference<(Event) -> Unit>,
  ) {

    fun handleEvent(event: Event) {
      val emitSuccessful = events.tryEmit(event)
      check(emitSuccessful) {
        "internal error vrv8vnpkch: tryEmit($event) was expected to return true " +
          "(since the MutableSharedFlow was created with extraBufferCapacity=UNLIMITED) " +
          "but it returned false"
      }

      listener.get()?.invoke(event)
    }
  }

  private val listener = AtomicReference<(Event) -> Unit>()

  private val state: AtomicReference<State> = AtomicReference(State.Unopened)

  /**
   * A unique identifier for a connection to the streaming service.
   *
   * Each time the "Connect" RPC is started it is assigned a unique identifier. This identifier is
   * included in [Event] objects to enable correlation of events to a particular RPC.
   */
  @JvmInline value class ConnectionId(val value: Long)

  /** Represents an event that occurred on the server. */
  sealed interface Event {

    /** Represents an incoming gRPC call, before the call is actually processed by the server. */
    class Call(val call: ServerCall<*, *>, val headers: Metadata) : Event {
      override fun toString() = "Call"
    }

    /** Represents the start of a "Connect" RPC. */
    class ConnectRpcStarted(
      val connectionId: ConnectionId,
      val responseObserver: StreamObserver<StreamResponse>,
    ) : Event {
      override fun toString() = "ConnectRpcStarted($connectionId)"
    }

    /** Represents a request received from the client over the stream. */
    class StreamRequestReceived(
      val connectionId: ConnectionId,
      val streamRequest: StreamRequest,
    ) : Event {
      override fun toString() =
        "StreamRequestReceived($connectionId, streamRequest={" +
          "requestId=${streamRequest.requestId}, kind=${streamRequest.requestKindCase}})"
    }

    /** Represents an error received from the client or the stream. */
    class ErrorReceived(val connectionId: ConnectionId, val exception: Throwable) : Event {
      override fun toString() = "ErrorReceived($connectionId, ${exception::class.qualifiedName})"
    }

    /** Represents the completion of the stream by the client. */
    class CompletedReceived(val connectionId: ConnectionId) : Event {
      override fun toString() = "CompletedReceived($connectionId)"
    }
  }

  /**
   * Opens and starts the gRPC server. If the server is already opened, it blocks until it is fully
   * started and returns the instance.
   *
   * @return The started gRPC [Server] instance.
   * @throws IllegalStateException if [close] has been called.
   */
  fun open(): Server {
    while (true) {
      when (val currentState = state.get()) {
        is State.Unopened -> {
          val future = CompletableFuture<Unit>()
          val eventHandler = EventHandler(_events, listener)
          val server =
            OkHttpServerBuilder.forPort(0, InsecureServerCredentials.create())
              .addService(ConnectorStreamingServiceImpl(eventHandler))
              .intercept(ServerInterceptorImpl(eventHandler))
              .build()

          val openingState = State.Opening(server, future)
          if (!state.compareAndSet(currentState, openingState)) {
            continue
          }
          try {
            server.start()
            _port.set(server.port)
            future.complete(Unit)
          } catch (e: Throwable) {
            future.completeExceptionally(e)
            throw e
          }
        }
        is State.Opening -> {
          try {
            currentState.started.get()
          } catch (e: ExecutionException) {
            throw e.cause ?: e
          }
          state.compareAndSet(currentState, State.Opened(currentState.server))
        }
        is State.Opened -> return currentState.server
        is State.Closing,
        State.Closed -> error("close() has been called [zv6n23ry4h]")
      }
    }
  }

  /**
   * Closes and shuts down the gRPC server.
   *
   * This method may be safely called more than once. Each call will block until the entire "close"
   * operation has completed. If the "close" operation has _already_ completed, then this method
   * returns immediately.
   */
  override fun close() {
    while (true) {
      when (val currentState = state.get()) {
        is State.Unopened -> if (state.compareAndSet(currentState, State.Closed)) return
        is State.Opening ->
          if (state.compareAndSet(currentState, State.Closing(currentState.server))) {
            currentState.server.shutdownNow()
            currentState.started.completeExceptionally(IllegalStateException("close() called"))
          }
        is State.Opened ->
          if (state.compareAndSet(currentState, State.Closing(currentState.server))) {
            currentState.server.shutdownNow()
          }
        is State.Closing -> {
          currentState.server.awaitTermination()
          state.compareAndSet(currentState, State.Closed)
        }
        State.Closed -> return
      }
    }
  }

  fun setListener(onEvent: ((Event) -> Unit)) {
    while (true) {
      val currentListener = listener.get()
      if (currentListener !== null) {
        error("a listener is already registered [fwpeptaxc2]")
      }
      if (listener.compareAndSet(currentListener, onEvent)) {
        break
      }
    }
  }

  private sealed interface State {
    object Unopened : State {
      override fun toString() = "Unopened"
    }
    class Opening(val server: Server, val started: CompletableFuture<Unit>) : State {
      override fun toString() = "Opening"
    }
    class Opened(val server: Server) : State {
      override fun toString() = "Opened(${server.port})"
    }
    class Closing(val server: Server) : State {
      override fun toString() = "Closing"
    }
    data object Closed : State {
      override fun toString() = "Closed"
    }
  }

  private class ServerInterceptorImpl(private val eventHandler: EventHandler) : ServerInterceptor {

    override fun <ReqT, RespT> interceptCall(
      call: ServerCall<ReqT, RespT>,
      headers: Metadata,
      next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
      eventHandler.handleEvent(Event.Call(call, headers))
      return next.startCall(call, headers)
    }
  }

  private class ConnectorStreamingServiceImpl(private val eventHandler: EventHandler) :
    ConnectorStreamServiceImplBase() {
    override fun connect(
      responseObserver: StreamObserver<StreamResponse>
    ): StreamObserver<StreamRequest> {
      val connectionId = ConnectionId(connectionSequenceNumber.incrementAndGet())
      eventHandler.handleEvent(Event.ConnectRpcStarted(connectionId, responseObserver))

      return object : StreamObserver<StreamRequest> {
        override fun onNext(streamRequest: StreamRequest) {
          eventHandler.handleEvent(Event.StreamRequestReceived(connectionId, streamRequest))
        }

        override fun onError(exception: Throwable) {
          eventHandler.handleEvent(Event.ErrorReceived(connectionId, exception))
        }

        override fun onCompleted() {
          eventHandler.handleEvent(Event.CompletedReceived(connectionId))
        }
      }
    }
  }
}

private val connectionSequenceNumber = AtomicLong(0)
