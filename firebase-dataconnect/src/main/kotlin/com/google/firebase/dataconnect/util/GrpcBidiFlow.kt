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

import com.google.firebase.dataconnect.util.CoroutineUtils.asSendChannel
import io.grpc.CallOptions
import io.grpc.Channel as GrpcChannel
import io.grpc.ClientCall
import io.grpc.Metadata as GrpcMetadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
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

internal object GrpcBidiFlow {

  sealed interface Event<in RequestT, out ResponseT> {
    class Started<in RequestT>(
      val outgoingRequests: SendChannel<RequestT>,
    ) : Event<RequestT, Nothing> {
      override fun toString() = "GrpcBidiFlow.Event.Started"
    }

    class Message<out ResponseT>(val message: ResponseT) : Event<Any?, ResponseT> {
      override fun toString() = "GrpcBidiFlow.Event.Message(message=$message)"
    }
  }

  interface Listener {

    fun onCollectStarted(connectionId: String)
  }

  fun <RequestT, ResponseT> create(
    grpcChannel: GrpcChannel,
    method: MethodDescriptor<RequestT, ResponseT>,
    callOptions: CallOptions,
    headers: GrpcMetadata,
    idStringGenerator: IdStringGenerator,
    listener: Listener? = null,
  ): Flow<Event<RequestT, ResponseT>> {
    require(method.type == MethodDescriptor.MethodType.BIDI_STREAMING) {
      "method.type is ${method.type} but BIDI_STREAMING is required"
    }

    return flow {
      val connectionId = idStringGenerator.next("con")
      listener?.onCollectStarted(connectionId)

      val requestChannel = Channel<RequestT>()
      emit(Event.Started(requestChannel.asSendChannel()))

      val clientCall: ClientCall<RequestT, ResponseT> = grpcChannel.newCall(method, callOptions)

      /*
       * We maintain a buffer of size 1 so onMessage never has to block: it only gets called after
       * we request a response from the server, which only happens when responses is empty and
       * there is room in the buffer.
       */
      val responses = Channel<ResponseT>(1)
      val readiness = Readiness { clientCall.isReady }

      clientCall.start(
        object : ClientCall.Listener<ResponseT>() {
          override fun onMessage(message: ResponseT) {
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
            responses.close(cause = cause)
          }

          override fun onReady() {
            readiness.onReady()
          }
        },
        headers.copy()
      )

      coroutineScope {
        val sender =
          launch(CoroutineName("SendMessage worker for ${method.fullMethodName}")) {
            try {
              readiness.suspendUntilReady()
              for (request in requestChannel) {
                clientCall.sendMessage(request)
                readiness.suspendUntilReady()
              }
              clientCall.halfClose()
            } catch (ex: Exception) {
              clientCall.cancel("Collection of requests completed exceptionally", ex)
              throw ex // propagate failure upward
            }
          }

        try {
          clientCall.request(1)
          for (response in responses) {
            emit(Event.Message(response))
            clientCall.request(1)
          }
        } catch (e: Exception) {
          withContext(NonCancellable) {
            sender.cancel("Collection of responses completed exceptionally", e)
            sender.join()
            // we want sender to be done cancelling before we cancel clientCall, or it might try
            // sending to a dead call, which results in ugly exception messages
            clientCall.cancel("Collection of responses completed exceptionally", e)
          }
          throw e
        }

        if (!sender.isCompleted) {
          sender.cancel("Collection of responses completed before collection of requests")
        }
      }
    }
  }

  private class Readiness(private val isReallyReady: () -> Boolean) {
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
      while (!isReallyReady()) {
        channel.receive()
      }
    }
  }
}
