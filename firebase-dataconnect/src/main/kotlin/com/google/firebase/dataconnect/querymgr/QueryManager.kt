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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.DataConnectStream
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.core.encodeVariables
import com.google.firebase.dataconnect.core.retryOnGrpcUnauthenticatedError
import com.google.firebase.dataconnect.querymgr.QueryManager.ExecuteResult
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.RequestIdGenerator
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import com.google.protobuf.Duration as DurationProto
import google.firebase.dataconnect.proto.ExecuteQueryRequest as ExecuteQueryRequestProto
import java.io.File
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class QueryManager(
  private val requestName: String,
  private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val dataConnectAuth: DataConnectAuth,
  private val dataConnectAppCheck: DataConnectAppCheck,
  private val ioDispatcher: CoroutineDispatcher,
  private val cpuDispatcher: CoroutineDispatcher,
  private val requestIdGenerator: RequestIdGenerator,
  private val cacheSettings: CacheSettings?,
  currentTimeMillis: () -> Long,
  private val logger: Logger,
) {
  private val state: MutableStateFlow<State> = run {
    val coroutineScope = createCoroutineScope()
    val cacheInfo: LocalQueries.CacheInfo? = createLocalQueriesCacheInfo(coroutineScope)
    val localQueries =
      LocalQueries(
        dataConnectGrpcRPCs,
        cpuDispatcher,
        cacheInfo,
        coroutineScope,
        currentTimeMillis,
        logger,
      )
    val streamManager = StreamManager(dataConnectGrpcRPCs, requestIdGenerator, requestName)
    val localQuerySubscriptions =
      LocalQuerySubscriptions(
        localQueries,
        streamManager,
        cpuDispatcher,
        coroutineScope,
        logger,
      )

    MutableStateFlow(
      State.Open(
        coroutineScope = coroutineScope,
        mutex = Mutex(),
        localQueries = localQueries,
        localQuerySubscriptions = localQuerySubscriptions,
        cacheDb = cacheInfo?.db,
      )
    )
  }

  private sealed interface State {
    data object Closed : State

    class Open(
      val coroutineScope: CoroutineScope,
      val mutex: Mutex,
      val localQueries: LocalQueries,
      val localQuerySubscriptions: LocalQuerySubscriptions,
      val cacheDb: DataConnectCacheDatabase?,
    ) : State
  }

  suspend fun close() {
    while (true) {
      val currentState = state.value

      when (currentState) {
        State.Closed -> return
        is State.Open -> {}
      }

      currentState.run {
        cacheDb?.close()
        coroutineScope.cancel("close() called")
        coroutineScope.coroutineContext.job.join()
      }

      if (state.compareAndSet(currentState, State.Closed)) {
        break
      }
    }
  }

  data class ExecuteResult<Data>(
    val data: Data,
    val source: DataSource,
  )

  suspend fun <Data, Variables> execute(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    fetchPolicy: QueryRef.FetchPolicy,
  ): ExecuteResult<Data> {
    val requestId = requestIdGenerator.nextQueryRequestId()
    logger.debug {
      "[rid=$requestId] Executing query with operationName=$operationName and variables=$variables"
    }

    val (requestProto, queryId) =
      prepare(
        operationName,
        variables,
        variablesSerializer,
        variablesSerializersModule,
      )

    return retryOnGrpcUnauthenticatedError(
      requestId = requestId,
      getAuthToken = { dataConnectAuth.getToken(requestId) },
      getAppCheckToken = { dataConnectAppCheck.getToken(requestId) },
      forceRefreshTokens = {
        // TODO: Deduplicate forceRefresh() calls with other parallel calls
        dataConnectAuth.forceRefresh()
        dataConnectAppCheck.forceRefresh()
      },
      logger,
    ) { authTokenResult, appCheckTokenResult ->
      val localKey =
        LocalQueries.Key(
          authUid = authTokenResult?.authUid,
          queryId = queryId,
          dataDeserializer = dataDeserializer,
          dataSerializersModule = dataSerializersModule,
          fetchPolicy = fetchPolicy,
        )

      execute(
        requestId = requestId,
        sequenceNumber = nextSequenceNumber(),
        localKey = localKey,
        authToken = authTokenResult?.token,
        appCheckToken = appCheckTokenResult?.token,
        requestProto = requestProto,
        callerSdkType = callerSdkType,
      )
    }
  }

  private suspend fun <Data> execute(
    requestId: String,
    sequenceNumber: Long,
    localKey: LocalQueries.Key<Data>,
    authToken: String?,
    appCheckToken: String?,
    requestProto: ExecuteQueryRequestProto,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteResult<Data> {
    val localQuery = getLocalQuery(localKey, requestProto)

    val executeResultSequencedReference =
      localQuery.execute(
        requestId = requestId,
        sequenceNumber = sequenceNumber,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    return executeResultSequencedReference.ref.let { ExecuteResult(it.data, it.source) }
  }

  private data class PrepareResult(
    val requestProto: ExecuteQueryRequestProto,
    val queryId: ImmutableByteArray,
  )

  private suspend fun <Variables> prepare(
    operationName: String,
    variables: Variables,
    variablesSerializer: SerializationStrategy<Variables>,
    variablesSerializersModule: SerializersModule?,
  ): PrepareResult =
    withContext(cpuDispatcher) {
      val variablesStruct =
        encodeVariables(variables, variablesSerializer, variablesSerializersModule)
      val queryId = variablesStruct.calculateSha512(preamble = operationName)
      val requestProto =
        ExecuteQueryRequestProto.newBuilder()
          .setName(requestName)
          .setOperationName(operationName)
          .setVariables(variablesStruct)
          .build()
      PrepareResult(requestProto, queryId)
    }

  suspend fun <Data, Variables> subscribe(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Flow<Result<ExecuteResult<Data>>> {
    val requestId = requestIdGenerator.nextQueryRequestId()
    logger.debug {
      "[rid=$requestId] Subscribing to query with operationName=$operationName " +
        "and variables=$variables"
    }

    val (requestProto, queryId) =
      prepare(
        operationName,
        variables,
        variablesSerializer,
        variablesSerializersModule,
      )

    val authTokenResult = dataConnectAuth.getToken(requestId)
    val appCheckTokenResult = dataConnectAppCheck.getToken(requestId)

    return subscribe(
      requestId = requestId,
      localKey =
        LocalQuerySubscriptions.Key(
          authUid = authTokenResult?.authUid,
          queryId = queryId,
          dataDeserializer = dataDeserializer,
          dataSerializersModule = dataSerializersModule,
        ),
      authToken = authTokenResult?.token,
      appCheckToken = appCheckTokenResult?.token,
      requestProto = requestProto,
      callerSdkType = callerSdkType,
    )
  }

  private suspend fun <Data> subscribe(
    requestId: String,
    localKey: LocalQuerySubscriptions.Key<Data>,
    authToken: String?,
    appCheckToken: String?,
    requestProto: ExecuteQueryRequestProto,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Flow<Result<ExecuteResult<Data>>> {
    val localSubscription = getLocalQuerySubscription(localKey, requestProto)

    val flow =
      localSubscription.subscribe(
        requestId = requestId,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    return flow.map { it.map { result -> ExecuteResult(result.data, result.source) } }
  }

  private suspend fun <Data> getLocalQuery(
    localKey: LocalQueries.Key<Data>,
    requestProto: ExecuteQueryRequestProto,
  ): LocalQuery<Data> = withLock { localQueries.getOrPut(localKey, requestProto) }

  private suspend fun <Data> getLocalQuerySubscription(
    localKey: LocalQuerySubscriptions.Key<Data>,
    requestProto: ExecuteQueryRequestProto,
  ): LocalQuerySubscription<Data> = withLock {
    localQuerySubscriptions.getOrPut(localKey, requestProto)
  }

  private suspend fun <T> withLock(block: State.Open.() -> T): T =
    when (val currentState = state.value) {
      State.Closed -> throw IllegalStateException("close() has been called [sgha6wyqyr]")
      is State.Open -> {
        currentState.run { mutex.withLock { block() } }
      }
    }

  private fun createCoroutineScope(): CoroutineScope =
    CoroutineScope(
      SupervisorJob() +
        CoroutineName(logger.nameWithId) +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${context[CoroutineName]}: " +
              "$throwable [emhpq6ag2r]"
          }
        }
    )

  private fun createLocalQueriesCacheInfo(coroutineScope: CoroutineScope): LocalQueries.CacheInfo? {
    if (cacheSettings === null) {
      logger.debug { "Not creating a DataConnectCacheDatabase because cacheSettings==null" }
      return null
    }

    val cacheLogger = Logger("DataConnectCacheDatabase")
    cacheLogger.debug { "created by ${logger.nameWithId} with cacheSettings=$cacheSettings" }

    val maxAgeProto =
      cacheSettings.maxAge.toComponents { seconds, nanos ->
        DurationProto.newBuilder().setSeconds(seconds).setNanos(nanos).build()
      }

    val cacheDb = DataConnectCacheDatabase(cacheSettings.dbFile, cacheLogger)

    val initializeJob =
      coroutineScope.async(
        ioDispatcher + CoroutineName("CacheDbInitialize"),
        start = CoroutineStart.LAZY,
      ) {
        cacheDb.initialize()
      }

    return LocalQueries.CacheInfo(cacheDb, maxAgeProto, initializeJob)
  }

  class StreamManager(
    private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
    private val requestIdGenerator: RequestIdGenerator,
    private val requestName: String,
  ) {
    private val mutex = Mutex()
    private var stream: DataConnectStream? = null

    suspend fun getOrCreate(
      authToken: String?,
      appCheckToken: String?,
      callerSdkType: FirebaseDataConnect.CallerSdkType,
    ): DataConnectStream =
      mutex.withLock {
        stream?.let {
          if (it.isAlive) {
            return it
          }
        }

        val newStream =
          dataConnectGrpcRPCs.connect(
            streamId = requestIdGenerator.nextStreamId(),
            authToken = authToken,
            appCheckToken = appCheckToken,
            callerSdkType = callerSdkType,
            name = requestName,
          )

        this.stream = newStream
        return newStream
      }
  }

  data class CacheSettings(val dbFile: File?, val maxAge: Duration)
}

internal suspend fun <Data, Variables> QueryManager.execute(
  ref: QueryRef<Data, Variables>,
  fetchPolicy: QueryRef.FetchPolicy,
): ExecuteResult<Data> =
  ref.run {
    execute(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
      callerSdkType = callerSdkType,
      fetchPolicy = fetchPolicy,
    )
  }

internal suspend fun <Data, Variables> QueryManager.subscribe(
  ref: QueryRef<Data, Variables>,
): Flow<Result<ExecuteResult<Data>>> =
  ref.run {
    subscribe(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
      callerSdkType = callerSdkType,
    )
  }
