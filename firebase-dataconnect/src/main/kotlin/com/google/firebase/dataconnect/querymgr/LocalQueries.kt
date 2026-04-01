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

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.protobuf.Duration as DurationProto
import google.firebase.dataconnect.proto.ExecuteQueryRequest as ExecuteQueryRequestProto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class LocalQueries(
  dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val cpuDispatcher: CoroutineDispatcher,
  private val cacheInfo: CacheInfo?,
  private val coroutineScope: CoroutineScope,
  private val currentTimeMillis: () -> Long,
) {

  private val localQueries = mutableMapOf<Key<*>, LocalQuery<*>>()
  private val remoteQueries = RemoteQueries(dataConnectGrpcRPCs, cpuDispatcher, coroutineScope)
  private val localQueryLogger = Logger("LocalQuery")

  @Volatile private var cacheOnlyNoCacheLocalQuery: CacheOnlyNoCacheLocalQuery? = null

  fun <T> getOrPut(
    key: Key<T>,
    requestProto: ExecuteQueryRequestProto,
  ): LocalQuery<T> =
    when (key.fetchPolicy) {
      QueryRef.FetchPolicy.PREFER_CACHE -> getOrPutPreferCache(key, requestProto)
      QueryRef.FetchPolicy.CACHE_ONLY -> getOrPutCacheOnly(key)
      QueryRef.FetchPolicy.SERVER_ONLY -> getOrPutServerOnly(key, requestProto)
    }

  fun <T> getOrPutCacheOnly(key: Key<T>): LocalQuery<T> {
    check(key.fetchPolicy == QueryRef.FetchPolicy.CACHE_ONLY)

    if (cacheInfo === null) {
      val localQuery = cacheOnlyNoCacheLocalQuery ?: CacheOnlyNoCacheLocalQuery(localQueryLogger)
      cacheOnlyNoCacheLocalQuery = localQuery
      return localQuery
    }

    val untypedLocalQuery: LocalQuery<*> =
      localQueries.getOrPut(key) {
        CacheOnlyLocalQuery(
          cacheInfo.db,
          cacheInfo.initializeJob,
          key.authUid,
          key.queryId,
          cpuDispatcher,
          key.dataDeserializer,
          key.dataSerializersModule,
          currentTimeMillis,
          localQueryLogger,
        )
      }

    @Suppress("UNCHECKED_CAST") return untypedLocalQuery as LocalQuery<T>
  }

  fun <T> getOrPutServerOnly(
    key: Key<T>,
    requestProto: ExecuteQueryRequestProto,
  ): LocalQuery<T> {
    check(key.fetchPolicy == QueryRef.FetchPolicy.SERVER_ONLY)

    val remoteKey = key.toRemoteKey()
    val remoteQuery = remoteQueries.getOrPut(remoteKey, requestProto)

    val untypedLocalQuery: LocalQuery<*> =
      localQueries.getOrPut(key) {
        val queryCacheUpdater =
          cacheInfo?.let {
            QueryCacheUpdater(
              cacheInfo = it,
              authUid = remoteKey.authUid,
              queryId = remoteKey.queryId,
              cpuDispatcher = cpuDispatcher,
              coroutineScope = coroutineScope,
              currentTimeMillis = currentTimeMillis,
              logger = localQueryLogger,
            )
          }

        ServerOnlyLocalQuery(
          remoteQuery,
          queryCacheUpdater,
          cpuDispatcher,
          key.dataDeserializer,
          key.dataSerializersModule,
          localQueryLogger,
        )
      }

    @Suppress("UNCHECKED_CAST") return untypedLocalQuery as LocalQuery<T>
  }

  fun <T> getOrPutPreferCache(
    key: Key<T>,
    requestProto: ExecuteQueryRequestProto,
  ): LocalQuery<T> {
    check(key.fetchPolicy == QueryRef.FetchPolicy.PREFER_CACHE)

    if (cacheInfo === null) {
      return getOrPutServerOnly(
        key.copy(fetchPolicy = QueryRef.FetchPolicy.SERVER_ONLY),
        requestProto
      )
    }

    // TODO: implement this
    return getOrPutServerOnly(
      key.copy(fetchPolicy = QueryRef.FetchPolicy.SERVER_ONLY),
      requestProto
    )
  }

  data class Key<Data>(
    val authUid: String?,
    val queryId: ImmutableByteArray,
    val dataDeserializer: DeserializationStrategy<Data>,
    val dataSerializersModule: SerializersModule?,
    val fetchPolicy: QueryRef.FetchPolicy,
  )

  class CacheInfo(
    val db: DataConnectCacheDatabase,
    val maxAge: DurationProto,
    val initializeJob: Deferred<Unit>,
  )
}

private fun LocalQueries.Key<*>.toRemoteKey(): RemoteQueries.Key =
  RemoteQueries.Key(
    authUid = authUid,
    queryId = queryId,
  )
