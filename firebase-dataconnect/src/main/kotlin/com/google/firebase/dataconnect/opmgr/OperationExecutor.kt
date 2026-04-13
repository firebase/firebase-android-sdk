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
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.core.encodeVariables
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.RequestIdGenerator
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import google.firebase.dataconnect.proto.ExecuteMutationResponse
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

  private val state = MutableStateFlow<State>(State.Info(
    dataConnectGrpcRPCs,
    dataConnectAuth,
    dataConnectAppCheck,
    ioDispatcher,
    cpuDispatcher,
  ))

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

    val response: ExecuteMutationResponse = coroutineScope {
      val authTokenJob = async { dataConnectAuth.getToken(requestId) }
      val appCheckTokenJob = async { dataConnectAppCheck.getToken(requestId) }

      val variablesStruct =
        withContext(cpuDispatcher) {
          encodeVariables(variables, variablesSerializer, variablesSerializersModule)
        }

      val authToken: GetAuthTokenResult? = authTokenJob.await()
      val appCheckToken: GetAppCheckTokenResult? = appCheckTokenJob.await()

      dataConnectGrpcRPCs.executeMutation(
        requestId = requestId,
        operationName = operationName,
        variables = variablesStruct,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )
    }

    return withContext(cpuDispatcher) {
      response.deserialize(dataDeserializer, dataSerializersModule)
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

    val authUid = dataConnectAuth.getToken(requestId)?.authUid

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

    val authUid = dataConnectAuth.getToken(requestId)?.authUid

    TODO()
  }

  private suspend fun ensureConnected(requestId: String, sequenceNumber: Long, callerSdkType: CallerSdkType): State.Connected {
    while (true) {
      val currentState = state.value

      val newState: State = when (currentState) {
        is State.Connected -> return currentState
        State.Closed, State.Closing -> throw IllegalStateException("close() has been called [ty783z85qk]")
        is State.Info -> {
          val coroutineScope = createCoroutineScope(currentState.cpuDispatcher)
          currentState.toConnectingState(coroutineScope, requestId, callerSdkType)
        }
        is State.Connecting -> {
          val result = currentState.job.runCatching { await() }
          if (result.isFailure && currentState.sequenceNumber < sequenceNumber) {
            currentState.info.toConnectingState(currentState.coroutineScope, requestId, callerSdkType,)
          } else {
            val authToken = result.getOrThrow()
            State.Connected(currentState.info, currentState.coroutineScope, authToken?.authUid)
          }
        }
      }

      state.compareAndSet(currentState, newState)
    }
  }

  private fun State.Info.toConnectingState(coroutineScope: CoroutineScope, requestId: String, callerSdkType: CallerSdkType): State.Connecting {
    val job = coroutineScope.async(CoroutineName("OperationExecutor connect"), start= CoroutineStart.LAZY,) {
      logger.debug { "[rid=$requestId] connecting to Data Connect backend" }
      connect(requestId, callerSdkType)
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

  private suspend fun State.Info.connect(requestId: String, callerSdkType: CallerSdkType,): GetAuthTokenResult? = coroutineScope {
    val getAuthTokenResult = async { dataConnectAuth.getToken(requestId) }
    val getAppCheckTokenResult = async { dataConnectAppCheck.getToken(requestId) }
    val streamId = requestIdGenerator.nextStreamId()
    val authToken = getAuthTokenResult.await()
    val appCheckToken = getAppCheckTokenResult.await()

    val (outgoingRequests: Channel<StreamRequest>, incomingResponses: Flow<StreamResponse>) = dataConnectGrpcRPCs.connect2(streamId, authToken, appCheckToken, callerSdkType)

    authToken
  }

  private suspend inline fun <T> withConnectedState(requestId: String, sequenceNumber: Long, callerSdkType: CallerSdkType, block: suspend State.Info.() -> T): T {
    val connectedState = ensureConnected(requestId, sequenceNumber, callerSdkType)
    return block(connectedState.info)
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
      val job: Deferred<GetAuthTokenResult?>,
    ) : State {
      override fun toString() = "OperationExecutor.State.Connecting"
    }

    data class Connected(
      val info: Info,
      val coroutineScope: CoroutineScope,
      val authUid: String?,
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
