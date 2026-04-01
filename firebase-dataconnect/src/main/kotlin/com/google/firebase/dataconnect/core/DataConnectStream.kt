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

package com.google.firebase.dataconnect.core

import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.transformWhile

/**
 * An active bidirectional stream connection to the Data Connect service.
 *
 * This class encapsulates the communication channels used to multiplex multiple query subscriptions
 * over a single gRPC stream.
 *
 * @property outgoingRequests Sends requests to the server, such as subscription initiations and
 * cancellations.
 * @property incomingResponses Emits responses received from the server, which must be later
 * dispatched to individual subscribers based on their request IDs.
 */
internal class DataConnectStream(
  private val coroutineScope: CoroutineScope,
  private val outgoingRequests: SendChannel<StreamRequest>,
  private val incomingResponses: SharedFlow<IncomingResponse>,
) {

  private val atomicIsAlive = AtomicBoolean(true)

  val isAlive: Boolean
    get() = atomicIsAlive.get()

  fun subscribe(requestId: String, request: ExecuteRequest): ReceiveChannel<ExecuteQueryResponse> {
    val flow: Flow<ExecuteQueryResponse> =
      incomingResponses.transformWhile { incomingResponse: IncomingResponse ->
        when (incomingResponse) {
          IncomingResponse.Completed -> {
            atomicIsAlive.set(false)
            false
          }
          is IncomingResponse.Error -> {
            atomicIsAlive.set(false)
            throw incomingResponse.throwable
          }
          IncomingResponse.Ready -> {
            outgoingRequests.send(
              StreamRequest.newBuilder().setRequestId(requestId).setSubscribe(request).build()
            )
            true
          }
          is IncomingResponse.Data -> {
            if (incomingResponse.response.requestId != requestId) {
              true
            } else {
              val streamResponse: StreamResponse = incomingResponse.response
              val executeQueryResponse = streamResponse.toExecuteQueryResponse()
              if (executeQueryResponse !== null) {
                emit(executeQueryResponse)
              }
              !streamResponse.cancelled
            }
          }
        }
      }

    return flow.buffer(capacity = Channel.RENDEZVOUS).produceIn(coroutineScope)
  }

  sealed interface IncomingResponse {
    data class Data(val response: StreamResponse) : IncomingResponse
    data class Error(val throwable: Throwable) : IncomingResponse
    data object Completed : IncomingResponse
    data object Ready : IncomingResponse
  }
}

private fun StreamResponse.toExecuteQueryResponse(): ExecuteQueryResponse? {
  if (!hasData() && errorsCount == 0 && (!hasExtensions() || extensions.dataConnectCount == 0)) {
    return null
  }

  val builder = ExecuteQueryResponse.newBuilder()
  if (hasData()) {
    builder.setData(data)
  }
  if (errorsCount > 0) {
    builder.addAllErrors(errorsList)
  }
  if (hasExtensions()) {
    builder.setExtensions(extensions)
  }

  return builder.build()
}
