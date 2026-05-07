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

package com.google.firebase.dataconnect.testutil

import android.os.ConditionVariable
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
 *
 * @param onEvent An optional callback invoked whenever an event occurs. This method is called
 * synchronously and, therefore, if it blocks then the gRPC server blocks and if it throws an
 * exception then that exception will bubble up as if it were thrown by the caller itself. If the
 * only desire is to _observe_ the events, then consider collecting from the [events] stream
 * instead.
 */
class InProcessDataConnectGrpcStreamingServer(onEvent: ((Server, Event) -> Unit)? = null) :
  AutoCloseable {

  private val _events = MutableSharedFlow<Event>(extraBufferCapacity = UNLIMITED)

  /**
   * A flow of events that occur on this server.
   *
   * Tests can collect this flow to verify the behavior of the client.
   *
   * In order to influence the server's behavior in response to events, use the [onEvent]
   * constructor parameter instead.
   */
  val events: SharedFlow<Event> = _events.asSharedFlow()

  private class EventHandler(
    private val events: MutableSharedFlow<Event>,
    private val onEvent: ((Server, Event) -> Unit)?,
  ) {

    private val server = AtomicReference<Server?>(null)

    fun setServer(server: Server) {
      if (!this.server.compareAndSet(null, server)) {
        error("server has already been set [mkqx8t245x]")
      }
    }

    fun handleEvent(event: Event) {
      val emitSuccessful = events.tryEmit(event)
      check(emitSuccessful) {
        "internal error vrv8vnpkch: tryEmit($event) was expected to return true " +
          "(since the MutableSharedFlow was created with extraBufferCapacity=UNLIMITED) " +
          "but it returned false"
      }

      onEvent?.invoke(server.get()!!, event)
    }
  }

  private val state: AtomicReference<State> = run {
    val eventHandler = EventHandler(_events, onEvent)
    val server =
      OkHttpServerBuilder.forPort(0, InsecureServerCredentials.create())
        .addService(ConnectorStreamingServiceImpl(eventHandler))
        .intercept(ServerInterceptorImpl(eventHandler))
        .build()

    eventHandler.setServer(server)

    AtomicReference(State.Unopened(server, ConditionVariable()))
  }

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
    data class Call(val call: ServerCall<*, *>, val headers: Metadata) : Event {
      override fun toString() = "Call"
    }

    /** Represents the start of a "Connect" RPC. */
    data class ConnectRpcStarted(
      val connectionId: ConnectionId,
      val responseObserver: StreamObserver<StreamResponse>,
    ) : Event {
      override fun toString() = "ConnectRpcStarted($connectionId)"
    }

    /** Represents a request received from the client over the stream. */
    data class StreamRequestReceived(
      val connectionId: ConnectionId,
      val streamRequest: StreamRequest,
    ) : Event {
      override fun toString() =
        "StreamRequestReceived($connectionId, streamRequest={" +
          "requestId=${streamRequest.requestId}, kind=${streamRequest.requestKindCase}})"
    }

    /** Represents an error received from the client or the stream. */
    data class ErrorReceived(val connectionId: ConnectionId, val exception: Throwable) : Event {
      override fun toString() = "ErrorReceived($connectionId, ${exception::class.qualifiedName})"
    }

    /** Represents the completion of the stream by the client. */
    data class CompletedReceived(val connectionId: ConnectionId) : Event {
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
        is State.Unopened ->
          if (
            state.compareAndSet(
              currentState,
              State.Opened(currentState.server, currentState.startCondition)
            )
          ) {
            currentState.server.start()
            currentState.startCondition.open()
          }
        is State.Opened -> {
          currentState.startCondition.block()
          return currentState.server
        }
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
      val currentState = state.get()
      when (currentState) {
        State.Closed -> return
        is State.Opened -> {
          currentState.server.shutdownNow()
          currentState.server.awaitTermination()
        }
        is State.Unopened -> {}
      }

      state.compareAndSet(currentState, State.Closed)
    }
  }

  private sealed interface State {
    class Unopened(val server: Server, val startCondition: ConditionVariable) : State {
      override fun toString() = "Unopened"
    }
    class Opened(val server: Server, val startCondition: ConditionVariable) : State {
      override fun toString() = "Opened(port=${server.port})"
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
