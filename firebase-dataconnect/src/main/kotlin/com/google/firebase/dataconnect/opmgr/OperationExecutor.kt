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

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.core.encodeVariables
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.RequestIdGenerator
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal sealed class OperationExecutor(
  dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  dataConnectAuth: DataConnectAuth,
  dataConnectAppCheck: DataConnectAppCheck,
  ioDispatcher: CoroutineDispatcher,
  cpuDispatcher: CoroutineDispatcher,
  private val requestIdGenerator: RequestIdGenerator,
  private val logger: Logger,
) {

  private val state =
    MutableStateFlow<State>(
      State.Info(
        dataConnectGrpcRPCs,
        dataConnectAuth,
        dataConnectAppCheck,
        ioDispatcher,
        cpuDispatcher,
      )
    )

  suspend fun <Data, Variables> executeMutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: CallerSdkType,
  ): Data {
    val requestId = requestIdGenerator.nextMutationRequestId()
    logger.debug {
      "[rid=$requestId] Executing mutation with " +
        "operationName=$operationName and variables=$variables"
    }

    return withConnectedState(requestId, nextSequenceNumber(), callerSdkType) {
      val variablesStruct =
        withContext(info.cpuDispatcher) {
          encodeVariables(variables, variablesSerializer, variablesSerializersModule)
        }

      val streamRequest =
        StreamRequest.newBuilder()
          .setRequestId(requestId)
          .setExecute(
            ExecuteRequest.newBuilder()
              .setOperationName(operationName)
              .setVariables(variablesStruct)
          )
          .build()

      val flow =
        incomingResponses.transformWhile { incomingResponse: IncomingResponse ->
          when (incomingResponse) {
            IncomingResponse.Completed -> {
              logger.debug { "[rid=$requestId] Completed event received" }
              false
            }
            is IncomingResponse.Error -> {
              logger.warn(incomingResponse.throwable) { "[rid=$requestId] Error received" }
              throw incomingResponse.throwable
            }
            IncomingResponse.Ready -> {
              logger.debug { "[rid=$requestId] Ready received; sending StreamRequest" }
              val channelResult = outgoingStreamRequests.trySend(streamRequest)
              channelResult.onFailure {
                logger.warn(it) { "[rid=$requestId] sending StreamRequest failed" }
              }
              channelResult.isSuccess
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

      val executeQueryResponse = flow.first()

      withContext(info.cpuDispatcher) {
        executeQueryResponse.deserialize(dataDeserializer, dataSerializersModule)
      }
    }
  }

  suspend fun <Data, Variables> executeQuery(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: CallerSdkType,
    fetchPolicy: QueryRef.FetchPolicy,
  ): OperationManager.ExecuteQueryResult<Data> {
    val requestId = requestIdGenerator.nextQueryRequestId()
    logger.debug {
      "[rid=$requestId] Executing query with " +
        "operationName=$operationName and variables=$variables"
    }

    TODO()
  }

  suspend fun <Data, Variables> subscribeQuery(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: CallerSdkType,
  ): Flow<Result<OperationManager.ExecuteQueryResult<Data>>> {
    val requestId = requestIdGenerator.nextQuerySubscriptionId()
    logger.debug {
      "[rid=$requestId] Subscribing to query with " +
        "operationName=$operationName and variables=$variables"
    }

    TODO()
  }

  private suspend fun ensureConnected(
    requestId: String,
    sequenceNumber: Long,
    callerSdkType: CallerSdkType
  ): State.Connected {
    while (true) {
      val currentState = state.value

      val newState: State =
        when (currentState) {
          is State.Connected -> return currentState
          State.Closed,
          State.Closing -> throw IllegalStateException("close() has been called [ty783z85qk]")
          is State.Info -> {
            val coroutineScope = createCoroutineScope(currentState.cpuDispatcher)
            currentState.toConnectingState(coroutineScope, requestId, callerSdkType)
          }
          is State.Connecting -> {
            val result = currentState.job.runCatching { await() }
            if (result.isFailure && currentState.sequenceNumber < sequenceNumber) {
              currentState.info.toConnectingState(
                currentState.coroutineScope,
                requestId,
                callerSdkType,
              )
            } else {
              result.getOrThrow()
            }
          }
        }

      state.compareAndSet(currentState, newState)
    }
  }

  private fun State.Info.toConnectingState(
    coroutineScope: CoroutineScope,
    requestId: String,
    callerSdkType: CallerSdkType
  ): State.Connecting {
    val getAuthTokenResult =
      coroutineScope.async(CoroutineName("dataConnectAuth.getToken")) {
        dataConnectAuth.getToken(requestId)
      }
    val getAppCheckTokenResult =
      coroutineScope.async(CoroutineName("dataConnectAppCheck.getToken")) {
        dataConnectAppCheck.getToken(requestId)
      }

    val job =
      coroutineScope.async(
        CoroutineName("OperationExecutor connect"),
        start = CoroutineStart.LAZY,
      ) {
        logger.debug { "[rid=$requestId] connecting to Data Connect backend" }

        val streamId = requestIdGenerator.nextStreamId()
        val authToken = getAuthTokenResult.await()
        val appCheckToken = getAppCheckTokenResult.await()

        val (
          outgoingStreamRequests: Channel<StreamRequest>,
          incomingStreamResponses: Flow<StreamResponse>) =
          dataConnectGrpcRPCs.connect2(streamId, authToken, appCheckToken, callerSdkType)

        val mutableIncomingResponses =
          MutableSharedFlow<IncomingResponse>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)

        val collectJob =
          coroutineScope.launch {
            incomingStreamResponses
              .map<_, IncomingResponse>(IncomingResponse::Data)
              .onCompletion { exception ->
                if (exception === null) emit(IncomingResponse.Completed)
              }
              .catch { emit(IncomingResponse.Error(it)) }
              .collect {
                check(mutableIncomingResponses.tryEmit(it)) {
                  "internal error m77j26qak4: mutableIncomingResponses.tryEmit() returned false, " +
                    "which should never happen since extraBufferCapacity=Int.MAX_VALUE"
                }
              }
          }

        val incomingResponses =
          mutableIncomingResponses.onSubscription { emit(IncomingResponse.Ready) }

        State.Connected(
          this@toConnectingState,
          coroutineScope,
          authToken?.authUid,
          collectJob,
          outgoingStreamRequests,
          incomingResponses
        )
      }

    job.invokeOnCompletion { exception ->
      if (exception === null) {
        logger.debug { "[rid=$requestId] connecting to Data Connect backend succeeded" }
      } else {
        logger.warn(exception) { "[rid=$requestId] connecting to Data Connect backend failed" }
      }
    }

    return State.Connecting(nextSequenceNumber(), this, coroutineScope, job)
  }

  private fun createCoroutineScope(dispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(
      SupervisorJob() +
        CoroutineName(logger.nameWithId) +
        dispatcher +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${context[CoroutineName]?.name}: " +
              "$throwable [wtz7g7zadz]"
          }
        }
    )

  private suspend inline fun <T> withConnectedState(
    requestId: String,
    sequenceNumber: Long,
    callerSdkType: CallerSdkType,
    block: suspend State.Connected.() -> T
  ): T {
    val connectedState = ensureConnected(requestId, sequenceNumber, callerSdkType)
    return block(connectedState)
  }

  /**
   * The various types of information received from Data Connect's "Connect" RPC. In order to play
   * nice with [SharedFlow], exceptions are wrapped in [IncomingResponse]. Note that subscribers
   * should wait until receiving [Ready] before sending a request to the backend; otherwise,
   * subscribers risk missing the response message if it's sent before the subscription is actually
   * established.
   */
  private sealed interface IncomingResponse {
    data class Data(val response: StreamResponse) : IncomingResponse
    data class Error(val throwable: Throwable) : IncomingResponse
    data object Completed : IncomingResponse
    data object Ready : IncomingResponse
  }

  private sealed interface State {

    data class Info(
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
      val dataConnectAuth: DataConnectAuth,
      val dataConnectAppCheck: DataConnectAppCheck,
      val ioDispatcher: CoroutineDispatcher,
      val cpuDispatcher: CoroutineDispatcher,
    ) : State {
      override fun toString() = "OperationExecutor.State.New"
    }

    data class Connecting(
      val sequenceNumber: Long,
      val info: Info,
      val coroutineScope: CoroutineScope,
      val job: Deferred<Connected>,
    ) : State {
      override fun toString() = "OperationExecutor.State.Connecting"
    }

    data class Connected(
      val info: Info,
      val coroutineScope: CoroutineScope,
      val authUid: String?,
      val collectJob: Job,
      val outgoingStreamRequests: Channel<StreamRequest>,
      val incomingResponses: SharedFlow<IncomingResponse>,
    ) : State {
      override fun toString() = "OperationExecutor.State.Connected"
    }

    data object Closing : State {
      override fun toString() = "OperationExecutor.State.Closing"
    }

    data object Closed : State {
      override fun toString() = "OperationExecutor.State.Closed"
    }
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
