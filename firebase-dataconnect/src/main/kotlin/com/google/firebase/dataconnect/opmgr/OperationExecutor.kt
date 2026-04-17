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
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.core.encodeVariables
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.CoroutineUtils.createSupervisorCoroutineScope
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.DeserializeUtils.toErrorInfoImpl
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.RequestIdGenerator
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import com.google.firebase.dataconnect.util.SuspendingWeakValueHashMap
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.GraphqlError
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal sealed class OperationExecutor(
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
          is State.SupportsConversionToClosingState -> currentState.toClosingState()
          is State.Closing ->
            currentState.run {
              localQueries?.close()
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

    return coroutineScope {
      val encodeVariablesJob =
        async(CoroutineName("OperationExecutor_EncodeVars_$requestId") + cpuDispatcher) {
          encodeVariables(variables, variablesSerializer, variablesSerializersModule)
        }

      val connectedState = ensureConnected(requestId, sequenceNumber, callerSdkType)

      connectedState.execute(
        requestId = requestId,
        operationName = operationName,
        variables = encodeVariablesJob.await(),
        dataDeserializer = dataDeserializer,
        dataSerializersModule = dataSerializersModule,
      )
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
    val sequenceNumber = nextSequenceNumber()
    val requestId = requestIdGenerator.nextQueryRequestId()
    logger.debug {
      "[rid=$requestId] Executing query with operationName=$operationName, " +
        "fetchPolicy=$fetchPolicy, and variables=$variables"
    }

    val data = coroutineScope {
      val prepareQueryVariablesJob =
        async(CoroutineName("OperationExecutor_EncodeVars_$requestId") + cpuDispatcher) {
          prepareQueryVariables(
            operationName,
            variables,
            variablesSerializer,
            variablesSerializersModule,
          )
        }

      val connectedState = ensureConnected(requestId, sequenceNumber, callerSdkType)

      val (variablesStruct, queryId) = prepareQueryVariablesJob.await()

      val remoteQueryKey =
        RemoteQueryKey(
          authUid = connectedState.authUid,
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
          LocalQueryKey(
            remoteQueryKey,
            dataDeserializer,
            dataSerializersModule,
            QueryRef.FetchPolicy.SERVER_ONLY,
          )

        while (true) {
          val x = connectedState.localQueries.get()
        }
      }

      connectedState.execute(
        requestId = requestId,
        operationName = operationName,
        variables = variablesStruct,
        dataDeserializer = dataDeserializer,
        dataSerializersModule = dataSerializersModule,
      )
    }

    return OperationManager.ExecuteQueryResult(data, DataSource.SERVER)
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
              val executeResponse = streamResponse.toExecuteResponse()
              if (executeResponse !== null) {
                emit(executeResponse)
              }
              !streamResponse.cancelled
            }
          }
        }
      }

    val executeResponse = flow.first()

    return withContext(cpuDispatcher) {
      executeResponse.deserialize(dataDeserializer, dataSerializersModule)
    }
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
    val localQueries = SuspendingWeakValueHashMap<LocalQueries.Key<*>, LocalQuery<*>>(ioDispatcher)

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
          localQueries,
        )
      }

    job.invokeOnCompletion { exception ->
      if (exception === null) {
        logger.debug { "[rid=$requestId] connecting to Data Connect backend succeeded" }
      } else {
        logger.warn(exception) { "[rid=$requestId] connecting to Data Connect backend failed" }
      }
    }

    return State.Connecting(nextSequenceNumber(), this, job, localQueries)
  }

  private data class PrepareQueryVariablesResult(
    val variables: Struct,
    val queryId: ImmutableByteArray,
  )

  private fun <Variables> prepareQueryVariables(
    operationName: String,
    variables: Variables,
    serializer: SerializationStrategy<Variables>,
    serializersModule: SerializersModule?,
  ): PrepareQueryVariablesResult {
    val variablesStruct = encodeVariables(variables, serializer, serializersModule)
    val queryId = variablesStruct.calculateSha512(preamble = operationName)
    return PrepareQueryVariablesResult(variablesStruct, queryId)
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

    sealed interface SupportsConversionToClosingState {
      fun toClosingState(): Closing
    }

    class Info(
      val coroutineScope: CoroutineScope,
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
      val dataConnectAuth: DataConnectAuth,
      val dataConnectAppCheck: DataConnectAppCheck,
      val cacheDb: DataConnectCacheDatabase?,
      val currentTimeMillis: () -> Long,
    ) : State, SupportsConversionToClosingState {
      override fun toString() = "Info"
      override fun toClosingState() = Closing(coroutineScope, localQueries = null)
    }

    class Connecting(
      val sequenceNumber: Long,
      val info: Info,
      val job: Deferred<Connected>,
      val localQueries:
        SuspendingWeakValueHashMap<LocalQueryKey<*>, MutableStateFlow<SequencedReference<*>>>,
      val remoteQueries:
        SuspendingWeakValueHashMap<RemoteQueryKey, MutableStateFlow<SequencedReference<Struct>>>,
    ) : State, SupportsConversionToClosingState {
      override fun toString() = "Connecting"
      override fun toClosingState() = Closing(info.coroutineScope, localQueries)
    }

    class Connected(
      val info: Info,
      val authUid: String?,
      val collectJob: Job,
      val outgoingStreamRequests: Channel<StreamRequest>,
      val incomingResponses: SharedFlow<IncomingResponse>,
      val localQueries:
        SuspendingWeakValueHashMap<LocalQueryKey<*>, MutableStateFlow<SequencedReference<*>>>,
      val remoteQueries:
        SuspendingWeakValueHashMap<RemoteQueryKey, MutableStateFlow<SequencedReference<Struct>>>,
    ) : State, SupportsConversionToClosingState {
      override fun toString() = "Connected"
      override fun toClosingState() = Closing(info.coroutineScope, localQueries)
    }

    class Closing(
      val coroutineScope: CoroutineScope,
      val localQueries: SuspendingWeakValueHashMap<*, *>?,
    ) : State {
      override fun toString() = "Closing"
    }

    object Closed : State {
      override fun toString() = "Closed"
    }
  }
}

private class ExecuteResponse(
  val data: Struct,
  val errors: List<GraphqlError>,
  val extensions: List<DataConnectProperties>,
)

private fun <T> ExecuteResponse.deserialize(
  deserializer: DeserializationStrategy<T>,
  serializersModule: SerializersModule?,
): T = deserialize(data, errors.map { it.toErrorInfoImpl() }, deserializer, serializersModule)

private fun StreamResponse.toExecuteResponse(): ExecuteResponse? =
  if (!hasData() && errorsCount == 0) {
    null
  } else {
    ExecuteResponse(
      data = if (hasData()) data else Struct.getDefaultInstance(),
      errors = if (errorsCount > 0) errorsList else emptyList(),
      extensions =
        if (hasExtensions() && extensions.dataConnectCount > 0) extensions.dataConnectList
        else emptyList(),
    )
  }

private data class LocalQueryKey<Data>(
  val remoteKey: RemoteQueryKey,
  val dataDeserializer: DeserializationStrategy<Data>,
  val dataSerializersModule: SerializersModule?,
  val fetchPolicy: QueryRef.FetchPolicy,
)

private data class RemoteQueryKey(
  val authUid: String?,
  val queryId: ImmutableByteArray,
)
