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
import com.google.firebase.dataconnect.querymgr.LocalQuery.ExecuteImplResult
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.protobuf.Struct as StructProto
import google.firebase.dataconnect.proto.ExecuteQueryResponse as ExecuteQueryResponseProto
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class CacheOnlyLocalQuery<Data>(
  private val cacheDb: DataConnectCacheDatabase,
  private val cacheDbInitializeJob: Deferred<Unit>,
  private val authUid: String?,
  private val queryId: ImmutableByteArray,
  cpuDispatcher: CoroutineDispatcher,
  dataDeserializer: DeserializationStrategy<Data>,
  dataSerializersModule: SerializersModule?,
  private val currentTimeMillis: () -> Long,
  private val logger: Logger,
) : LocalQuery<Data>(cpuDispatcher, dataDeserializer, dataSerializersModule, logger) {

  override suspend fun executeImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<ExecuteImplResult> {
    val getQueryResultResult = executeImpl(requestId, GetQueryResultResult.Found::class)
    logger.logGetQueryResultResult(requestId, getQueryResultResult)
    val executeImplResult = getQueryResultResult.toExecuteImplResult()
    return SequencedReference(sequenceNumber, executeImplResult)
  }

  suspend fun executeImpl(
    requestId: String,
    staleResult: KClass<out GetQueryResultResult>,
  ): GetQueryResultResult {
    if (!cacheDbInitializeJob.isCompleted) {
      logger.debug { "[rid=$requestId] waiting for cache database initialization" }
      cacheDbInitializeJob.await()
      logger.debug { "[rid=$requestId] waiting for cache database initialization done" }
    }

    logger.debug { "[rid=$requestId] getting query result from cache" }
    return cacheDb.getQueryResult(
      authUid = authUid,
      queryId = queryId,
      currentTimeMillis = currentTimeMillis(),
      staleResult = staleResult,
    )
  }

  private companion object {

    fun GetQueryResultResult.toExecuteImplResult(): ExecuteImplResult =
      when (this) {
        is GetQueryResultResult.Found ->
          ExecuteImplResult(
            struct.toExecuteQueryResponseProto(),
            DataSource.CACHE,
          )
        GetQueryResultResult.NotFound ->
          throw CachedDataNotFoundException(
            "query result was not found in the local cache [xz3fvh9r39]"
          )
        is GetQueryResultResult.Stale ->
          throw IllegalStateException("internal error axj5etj8v3: unexpected result: $this")
      }
  }
}

internal fun StructProto.toExecuteQueryResponseProto(): ExecuteQueryResponseProto =
  ExecuteQueryResponseProto.newBuilder().setData(this).build()

internal fun Logger.logGetQueryResultResult(requestId: String, result: GetQueryResultResult) =
  debug {
    val message =
      when (result) {
        is GetQueryResultResult.Found ->
          "found query result in the local cache (freshnessRemaining=${result.freshnessRemaining})"
        GetQueryResultResult.NotFound -> "query result was not found in the local cache"
        is GetQueryResultResult.Stale ->
          "stale query result found in the local cache (staleness=${result.staleness})"
      }

    "[rid=$requestId] $message"
  }
