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

import com.google.firebase.dataconnect.CachedDataNotFoundException
import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.core.encodeVariables
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.CoroutineUtils.createSupervisorCoroutineScope
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.RequestIdGenerator
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
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
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class OperationExecutor(
  dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  dataConnectAuth: DataConnectAuth,
  dataConnectAppCheck: DataConnectAppCheck,
  cacheDb: DataConnectCacheDatabase?,
  currentTimeMillis: () -> Long,
  private val ioDispatcher: CoroutineDispatcher,
  private val cpuDispatcher: CoroutineDispatcher,
  private val requestIdGenerator: RequestIdGenerator,
  private val logger: Logger,
) {

  private val state =
    MutableStateFlow<State>(
      State.Info(
        createSupervisorCoroutineScope(cpuDispatcher, logger),
        dataConnectGrpcRPCs,
        dataConnectAuth,
        dataConnectAppCheck,
        cacheDb,
        currentTimeMillis,
      )
    )

  suspend fun close() {
    while (true) {
      val currentState = state.value

      val newState =
        when (currentState) {
          is State.Info -> State.Closing(currentState)
          is State.Connecting -> State.Closing(currentState)
          is State.Connected -> State.Closing(currentState)
          is State.Closing ->
            currentState.run {
              queries?.localQueries?.close()
              queries?.remoteQueries?.close()
              coroutineScope.cancel("OperationExecutor.close() called")
              coroutineScope.coroutineContext.job.join()
              State.Closed
            }
          State.Closed -> return
        }

      state.compareAndSet(currentState, newState)
    }
  }

  suspend fun <Data, Variables> executeMutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: CallerSdkType,
  ): Data {
    val sequenceNumber = nextSequenceNumber()
    val requestId = requestIdGenerator.nextMutationRequestId()
    logger.debug {
      "[rid=$requestId] Executing mutation with operationName=$operationName " +
        "and variables=$variables"
    }

    val (connectedState, variablesStruct) =
      coroutineScope {
        var connectedState: State.Connected? = null
        var variablesStruct: Struct? = null

        val connectedStateJob =
          launch(CoroutineName("OperationExecutor_ConState_$requestId")) {
            connectedState = ensureConnected(requestId, sequenceNumber, callerSdkType)
          }
        val variablesJob =
          launch(CoroutineName("OperationExecutor_EncodeVars_$requestId") + cpuDispatcher) {
            variablesStruct =
              encodeVariables(variables, variablesSerializer, variablesSerializersModule)
          }

        joinAll(connectedStateJob, variablesJob)

        Pair(connectedState!!, variablesStruct!!)
      }

    return connectedState.execute(
      requestId = requestId,
      operationName = operationName,
      variables = variablesStruct,
      dataDeserializer = dataDeserializer,
      dataSerializersModule = dataSerializersModule,
    )
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
    val sequenceNumber = nextSequenceNumber()
    val requestId = requestIdGenerator.nextQueryRequestId()
    logger.debug {
      "[rid=$requestId] Executing query with operationName=$operationName, " +
        "fetchPolicy=$fetchPolicy, and variables=$variables"
    }

    val (connectedState, variablesStruct, queryId) =
      coroutineScope {
        var connectedState: State.Connected? = null
        var variablesStruct: Struct? = null
        var queryId: ImmutableByteArray? = null

        val connectedStateJob =
          launch(CoroutineName("OperationExecutor_ConState_$requestId")) {
            connectedState = ensureConnected(requestId, sequenceNumber, callerSdkType)
          }
        val variablesJob =
          launch(CoroutineName("OperationExecutor_EncodeVars_$requestId") + cpuDispatcher) {
            variablesStruct =
              encodeVariables(variables, variablesSerializer, variablesSerializersModule)
            ensureActive()
            queryId = calculateQueryId(operationName, variablesStruct)
          }

        joinAll(connectedStateJob, variablesJob)

        Triple(connectedState!!, variablesStruct!!, queryId!!)
      }

    val remoteQueryKey =
      RemoteQueries.Key(
        // authUid = connectedState.authUid,
        queryId = queryId,
      )

    val cacheDb = connectedState.info.cacheDb
    if (cacheDb === null) {
      if (fetchPolicy == QueryRef.FetchPolicy.CACHE_ONLY) {
        throw CachedDataNotFoundException(
          "CACHE_ONLY fetch policy is unsupported when cache settings is null [m35wype9dt]"
        )
      }

      val localQueryKey =
        LocalQueries.Key(
          remoteQueryKey,
          dataDeserializer,
          dataSerializersModule,
          QueryRef.FetchPolicy.SERVER_ONLY,
        )

      while (true) {
        TODO()
      }
    }

    connectedState.execute(
      requestId = requestId,
      operationName = operationName,
      variables = variablesStruct,
      dataDeserializer = dataDeserializer,
      dataSerializersModule = dataSerializersModule,
    )

    return OperationManager.ExecuteQueryResult(TODO(), DataSource.SERVER)
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

  private suspend fun <Data> State.Connected.execute(
    requestId: String,
    operationName: String,
    variables: Struct,
    dataDeserializer: DeserializationStrategy<Data>,
    dataSerializersModule: SerializersModule?,
  ): Data {
    val executeResponse =
      execute(
        requestId = requestId,
        operationName = operationName,
        variables = variables,
      )
    return withContext(cpuDispatcher) {
      executeResponse.deserialize(dataDeserializer, dataSerializersModule)
    }
  }

  private suspend fun State.Connected.execute(
    requestId: String,
    operationName: String,
    variables: Struct,
  ): ExecuteResponse {
    val streamRequest =
      StreamRequest.newBuilder()
        .setRequestId(requestId)
        .setExecute(
          ExecuteRequest.newBuilder().setOperationName(operationName).setVariables(variables)
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
              val executeResponse = streamResponse.toExecuteResponse(TODO())
              if (executeResponse !== null) {
                emit(executeResponse)
              }
              !streamResponse.cancelled
            }
          }
        }
      }

    return flow.first()
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
          is State.Info -> currentState.toConnectingState(requestId, callerSdkType)
          is State.Connecting -> {
            val result = currentState.job.runCatching { await() }
            if (result.isFailure && currentState.sequenceNumber < sequenceNumber) {
              currentState.info.toConnectingState(
                requestId,
                callerSdkType,
              )
            } else {
              result.getOrThrow()
            }
          }
          is State.Connected -> return currentState
          is State.Closing,
          State.Closed -> error("close() has been called [ty783z85qk]")
        }

      state.compareAndSet(currentState, newState)
    }
  }

  private fun State.Info.toConnectingState(
    requestId: String,
    callerSdkType: CallerSdkType
  ): State.Connecting {
    val getAuthTokenResult =
      coroutineScope.async(CoroutineName("OperationExecutor_GetAuthToken_$requestId")) {
        dataConnectAuth.getToken(requestId)
      }
    val getAppCheckTokenResult =
      coroutineScope.async(CoroutineName("OperationExecutor_GetAppCheckToken_$requestId")) {
        dataConnectAppCheck.getToken(requestId)
      }

    val remoteQueries =
      RemoteQueries(
        TODO(),
        TODO(),
        TODO(),
        ioDispatcher,
        Logger("RemoteQueries").apply { debug { "created by ${logger.nameWithId}" } },
      )

    val localQueries =
      LocalQueries(
        remoteQueries,
        cpuDispatcher,
        ioDispatcher,
        Logger("LocalQueries").apply { debug { "created by ${logger.nameWithId}" } },
      )

    val queries = State.Queries(remoteQueries, localQueries)

    val job =
      coroutineScope.async(
        CoroutineName("OperationExecutor_Connect_$requestId"),
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
          authToken?.authUid,
          collectJob,
          outgoingStreamRequests,
          incomingResponses,
          queries,
        )
      }

    job.invokeOnCompletion { exception ->
      if (exception === null) {
        logger.debug { "[rid=$requestId] connecting to Data Connect backend succeeded" }
      } else {
        logger.warn(exception) { "[rid=$requestId] connecting to Data Connect backend failed" }
      }
    }

    return State.Connecting(nextSequenceNumber(), this, job, queries)
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

    class Queries(
      val remoteQueries: RemoteQueries,
      val localQueries: LocalQueries,
    )

    class Info(
      val coroutineScope: CoroutineScope,
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
      val dataConnectAuth: DataConnectAuth,
      val dataConnectAppCheck: DataConnectAppCheck,
      val cacheDb: DataConnectCacheDatabase?,
      val currentTimeMillis: () -> Long,
    ) : State

    class Connecting(
      val sequenceNumber: Long,
      val info: Info,
      val job: Deferred<Connected>,
      val queries: Queries,
    ) : State

    class Connected(
      val info: Info,
      val authUid: String?,
      val collectJob: Job,
      val outgoingStreamRequests: Channel<StreamRequest>,
      val incomingResponses: SharedFlow<IncomingResponse>,
      val queries: Queries,
    ) : State

    class Closing(
      val coroutineScope: CoroutineScope,
      val queries: Queries?,
    ) : State {
      constructor(info: Info, queries: Queries?) : this(info.coroutineScope, queries)
      constructor(info: Info) : this(info, queries = null)
      constructor(connecting: Connecting) : this(connecting.info, connecting.queries)
      constructor(connected: Connected) : this(connected.info, connected.queries)
    }

    object Closed : State {
      override fun toString() = "Closed"
    }
  }
}

private fun calculateQueryId(
  operationName: String,
  variables: Struct,
): ImmutableByteArray = variables.calculateSha512(preamble = operationName)
