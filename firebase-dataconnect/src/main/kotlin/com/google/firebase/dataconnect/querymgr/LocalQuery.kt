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

import com.google.firebase.dataconnect.CachedDataNotFoundException
import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.protobuf.Struct as StructProto
import google.firebase.dataconnect.proto.ExecuteQueryResponse as ExecuteQueryResponseProto
import java.lang.System.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

internal sealed class LocalQuery<out Data>(
  private val cpuDispatcher: CoroutineDispatcher,
  private val dataDeserializer: DeserializationStrategy<Data>,
  private val dataSerializersModule: SerializersModule?,
  private val logger: Logger,
) {

  suspend fun execute(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteResult<Data> {
    val (executeQueryResponse, dataSource) =
      run {
        val executeImplResult =
          executeImpl(
            requestId = requestId,
            sequenceNumber = sequenceNumber,
            authToken = authToken,
            appCheckToken = appCheckToken,
            callerSdkType = callerSdkType,
          )

        val executeQueryResponseDataSourcePair =
          when (executeImplResult) {
            ExecuteImplResult.Retry -> null
            is ExecuteImplResult.Success -> {
              if (executeImplResult.sequenceNumber < sequenceNumber) {
                null
              } else {
                Pair(executeImplResult.executeQueryResponse, executeImplResult.dataSource)
              }
            }
          }

        executeQueryResponseDataSourcePair ?: return ExecuteResult.Retry
      }

    val dataDeserializeResult =
      withContext(cpuDispatcher) {
        executeQueryResponse.runCatching { deserialize(dataDeserializer, dataSerializersModule) }
      }

    dataDeserializeResult.onFailure {
      logger.warn(it) { "[rid=$requestId] decoding response data failed" }
    }

    return ExecuteResult.Success(dataDeserializeResult.getOrThrow(), dataSource)
  }

  protected abstract suspend fun executeImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteImplResult

  sealed interface ExecuteResult<out T> {
    data class Success<T>(val data: T, val source: DataSource) : ExecuteResult<T>
    data object Retry : ExecuteResult<Nothing>
  }

  protected sealed interface ExecuteImplResult {
    data class Success(
      val sequenceNumber: Long,
      val executeQueryResponse: ExecuteQueryResponseProto,
      val dataSource: DataSource
    ) : ExecuteImplResult
    data object Retry : ExecuteImplResult
  }
}

internal class ServerOnlyLocalQuery<Data>(
  private val remoteQuery: RemoteQuery,
  cpuDispatcher: CoroutineDispatcher,
  dataDeserializer: DeserializationStrategy<Data>,
  dataSerializersModule: SerializersModule?,
  logger: Logger,
) : LocalQuery<Data>(cpuDispatcher, dataDeserializer, dataSerializersModule, logger) {

  override suspend fun executeImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteImplResult {
    val remoteResult =
      remoteQuery.execute(
        requestId = requestId,
        sequenceNumber = sequenceNumber,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    return when (remoteResult) {
      RemoteQuery.ExecuteResult.Retry -> ExecuteImplResult.Retry
      is RemoteQuery.ExecuteResult.Success ->
        ExecuteImplResult.Success(
          remoteResult.response.sequenceNumber,
          remoteResult.response.ref,
          DataSource.SERVER,
        )
    }
  }
}

private object ThrowingDeserializer : DeserializationStrategy<Nothing> {
  override val descriptor
    get() = unsupported()

  override fun deserialize(decoder: Decoder) = unsupported()

  private fun unsupported(): Nothing =
    throw UnsupportedOperationException("ThrowingDeserializer does not support any methods")
}

internal class CacheOnlyNoCacheLocalQuery(
  logger: Logger,
) : LocalQuery<Nothing>(Dispatchers.Default, ThrowingDeserializer, null, logger) {

  override suspend fun executeImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType
  ): ExecuteImplResult {
    throw CachedDataNotFoundException(
      "CACHE_ONLY fetch policy is unsupported when cache settings is null [m35wype9dt]"
    )
  }
}

internal class CacheOnlyLocalQuery<Data>(
  private val cacheDb: DataConnectCacheDatabase,
  private val cacheDbInitializeJob: Deferred<Unit>,
  private val authUid: String?,
  private val queryId: ImmutableByteArray,
  cpuDispatcher: CoroutineDispatcher,
  dataDeserializer: DeserializationStrategy<Data>,
  dataSerializersModule: SerializersModule?,
  private val logger: Logger,
) : LocalQuery<Data>(cpuDispatcher, dataDeserializer, dataSerializersModule, logger) {

  override suspend fun executeImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteImplResult {
    if (!cacheDbInitializeJob.isCompleted) {
      logger.debug { "[rid=$requestId] waiting for cache database initialization" }
      cacheDbInitializeJob.await()
      logger.debug { "[rid=$requestId] waiting for cache database initialization done" }
    }

    logger.debug { "[rid=$requestId] getting query result from cache" }
    val result =
      cacheDb.getQueryResult(
        authUid = authUid,
        queryId = queryId,
        currentTimeMillis = currentTimeMillis(),
        staleResult = DataConnectCacheDatabase.GetQueryResultResult.Found::class,
      )

    return when (result) {
      is DataConnectCacheDatabase.GetQueryResultResult.Found -> {
        logger.debug {
          "[rid=$requestId] got query result from cache " +
            "with freshnessRemaining=${result.freshnessRemaining}"
        }
        ExecuteImplResult.Success(
          sequenceNumber,
          result.struct.toExecuteQueryResponseProto(),
          DataSource.CACHE,
        )
      }
      DataConnectCacheDatabase.GetQueryResultResult.NotFound -> {
        logger.debug { "[rid=$requestId] no query result found in cache" }
        throw CachedDataNotFoundException(
          "no cached results for query and CACHE_ONLY fetch policy was specified [xz3fvh9r39]"
        )
      }
      is DataConnectCacheDatabase.GetQueryResultResult.Stale ->
        throw IllegalStateException("internal error axj5etj8v3: unexpected result: $result")
    }
  }
}

private fun StructProto.toExecuteQueryResponseProto(): ExecuteQueryResponseProto =
  ExecuteQueryResponseProto.newBuilder().setData(this).build()
