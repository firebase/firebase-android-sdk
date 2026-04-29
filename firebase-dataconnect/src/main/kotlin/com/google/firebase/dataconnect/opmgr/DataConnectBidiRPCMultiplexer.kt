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

package com.google.firebase.dataconnect.opmgr

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.util.CoroutineUtils.createSupervisorCoroutineScope
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.job

internal class DataConnectBidiRPCMultiplexer
private constructor(
  private val authUid: String?,
  private val outgoingRequests: Channel<StreamRequest>,
  incomingResponses: Flow<StreamResponse>,
  cpuDispatcher: CoroutineDispatcher,
  logger: Logger,
) {

  private val coroutineScope = createSupervisorCoroutineScope(cpuDispatcher, logger)
  private val sharedFlow: SharedFlow<IncomingResponse> =
    createSharedFlow(coroutineScope, incomingResponses)

  suspend fun close() {
    coroutineScope.cancel("DataConnectBidiRPCMultiplexer.close() called")
    coroutineScope.coroutineContext.job.join()
  }

  fun execute(requestId: String, request: ExecuteRequest): Flow<ExecuteResponse> =
    executeOrSubscribe(requestId, request, Channel<StreamRequest>::sendExecute)

  fun subscribe(requestId: String, request: ExecuteRequest): Flow<ExecuteResponse> =
    executeOrSubscribe(requestId, request, Channel<StreamRequest>::sendSubscribe)

  private fun executeOrSubscribe(
    requestId: String,
    request: ExecuteRequest,
    block: Channel<StreamRequest>.(requestId: String, request: ExecuteRequest) -> Unit
  ): Flow<ExecuteResponse> =
    sharedFlow.transformWhile { incomingResponse ->
      when (incomingResponse) {
        IncomingResponse.Completed -> false
        is IncomingResponse.Data -> transformDataWhile(requestId, incomingResponse)
        is IncomingResponse.Error -> throw incomingResponse.throwable
        IncomingResponse.Subscribed -> {
          block(outgoingRequests, requestId, request)
          true
        }
        IncomingResponse.Noop -> true
      }
    }

  private suspend fun FlowCollector<ExecuteResponse>.transformDataWhile(
    filterRequestId: String,
    data: IncomingResponse.Data,
  ): Boolean {
    val streamResponse: StreamResponse = data.response

    if (streamResponse.requestId != filterRequestId) {
      return true
    }

    val executeResponse: ExecuteResponse? = streamResponse.toExecuteResponse(authUid)
    if (executeResponse !== null) {
      emit(executeResponse)
    }

    return !streamResponse.cancelled
  }

  private sealed interface IncomingResponse {
    class Data(val response: StreamResponse) : IncomingResponse
    class Error(val throwable: Throwable) : IncomingResponse
    object Completed : IncomingResponse
    object Subscribed : IncomingResponse
    object Noop : IncomingResponse
  }

  companion object {

    suspend fun create(
      dataConnectGrpcRPCs: DataConnectGrpcRPCs,
      streamId: String,
      authToken: GetAuthTokenResult?,
      appCheckToken: GetAppCheckTokenResult?,
      callerSdkType: FirebaseDataConnect.CallerSdkType,
      cpuDispatcher: CoroutineDispatcher,
      logger: Logger,
    ): DataConnectBidiRPCMultiplexer {
      val (outgoingRequests, incomingResponses) =
        dataConnectGrpcRPCs.connect2(streamId, authToken, appCheckToken, callerSdkType)
      return DataConnectBidiRPCMultiplexer(
        authToken?.authUid,
        outgoingRequests,
        incomingResponses,
        cpuDispatcher,
        logger
      )
    }

    private fun createSharedFlow(
      coroutineScope: CoroutineScope,
      incomingResponses: Flow<StreamResponse>
    ): SharedFlow<IncomingResponse> =
      incomingResponses
        .transform {
          emit(IncomingResponse.Data(it))
          emit(IncomingResponse.Noop) // don't let Data sit in the replay cache, consuming memory
        }
        .onCompletion { exception ->
          if (exception === null) {
            emit(IncomingResponse.Completed)
          }
        }
        .catch { exception -> emit(IncomingResponse.Error(exception)) }
        .shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)
        .onSubscription { emit(IncomingResponse.Subscribed) }
  }
}

private fun Channel<StreamRequest>.sendExecute(requestId: String, request: ExecuteRequest) {
  sendExecuteRequest(requestId) { it.setExecute(request) }
}

private fun Channel<StreamRequest>.sendSubscribe(requestId: String, request: ExecuteRequest) {
  sendExecuteRequest(requestId) { it.setSubscribe(request) }
}

@OptIn(ExperimentalContracts::class)
private inline fun Channel<StreamRequest>.sendExecuteRequest(
  requestId: String,
  block: (StreamRequest.Builder) -> Unit
) {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
  val streamRequest =
    StreamRequest.newBuilder().let {
      it.setRequestId(requestId)
      block(it)
      it.build()
    }
  trySend(streamRequest).getOrThrow()
}
