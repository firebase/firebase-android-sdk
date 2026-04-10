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
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class QueryManager(
  private val requestName: String,
  dataConnectGrpcRPCs: DataConnectGrpcRPCs,
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
    MutableStateFlow(
      State.Open(
        coroutineScope = coroutineScope,
        localQueriesMutex = Mutex(),
        localQueries =
          LocalQueries(
            dataConnectGrpcRPCs,
            cpuDispatcher,
            cacheInfo,
            coroutineScope,
            currentTimeMillis,
            logger,
          ),
        cacheDb = cacheInfo?.db,
      )
    )
  }

  private sealed interface State {
    data object Closed : State

    class Open(
      val coroutineScope: CoroutineScope,
      val localQueriesMutex: Mutex,
      val localQueries: LocalQueries,
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

    val requestProto: ExecuteQueryRequestProto
    val queryId: ImmutableByteArray
    withContext(cpuDispatcher) {
      val variablesStruct =
        encodeVariables(variables, variablesSerializer, variablesSerializersModule)
      queryId = variablesStruct.calculateSha512(preamble = operationName)
      requestProto =
        ExecuteQueryRequestProto.newBuilder()
          .setName(requestName)
          .setOperationName(operationName)
          .setVariables(variablesStruct)
          .build()
    }

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
        authToken = authTokenResult,
        appCheckToken = appCheckTokenResult,
        requestProto = requestProto,
        callerSdkType = callerSdkType,
      )
    }
  }

  private suspend fun <Data> execute(
    requestId: String,
    sequenceNumber: Long,
    localKey: LocalQueries.Key<Data>,
    authToken: GetAuthTokenResult?,
    appCheckToken: GetAppCheckTokenResult?,
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

  private suspend fun <Data> getLocalQuery(
    localKey: LocalQueries.Key<Data>,
    requestProto: ExecuteQueryRequestProto,
  ): LocalQuery<Data> =
    when (val currentState = state.value) {
      State.Closed -> throw IllegalStateException("close() has been called [sgha6wyqyr]")
      is State.Open -> {
        currentState.run {
          localQueriesMutex.withLock { localQueries.getOrPut(localKey, requestProto) }
        }
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
