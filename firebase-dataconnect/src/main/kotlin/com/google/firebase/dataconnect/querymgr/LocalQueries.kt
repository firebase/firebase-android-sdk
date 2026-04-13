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
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.protobuf.Duration as DurationProto
import com.google.protobuf.Struct
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
  private val logger: Logger,
) {
  private val localQueries = mutableMapOf<Key<*>, LocalQuery<*>>()
  private val remoteQueries =
    RemoteQueries(
      dataConnectGrpcRPCs,
      cpuDispatcher,
      coroutineScope,
      logger,
    )

  fun <T> getOrPut(
    key: Key<T>,
    operationName: String,
    variables: Struct,
  ): LocalQuery<T> =
    when (key.fetchPolicy) {
      QueryRef.FetchPolicy.PREFER_CACHE -> getOrPutPreferCache(key, operationName, variables)
      QueryRef.FetchPolicy.CACHE_ONLY -> getOrPutCacheOnly(key)
      QueryRef.FetchPolicy.SERVER_ONLY -> getOrPutServerOnly(key, operationName, variables)
    }

  private fun <T> getOrPutPreferCache(
    key: Key<T>,
    operationName: String,
    variables: Struct,
  ): LocalQuery<T> {
    check(key.fetchPolicy == QueryRef.FetchPolicy.PREFER_CACHE)

    val serverOnlyLocalQuery: ServerOnlyLocalQuery<T> =
      getOrPutServerOnly(
        key.copy(fetchPolicy = QueryRef.FetchPolicy.SERVER_ONLY),
        operationName,
        variables,
      )

    if (cacheInfo === null) {
      return serverOnlyLocalQuery
    }

    val cacheOnlyLocalQuery: CacheOnlyLocalQuery<T> =
      getOrPutCacheOnly(key.copy(fetchPolicy = QueryRef.FetchPolicy.CACHE_ONLY))
        as CacheOnlyLocalQuery<T>

    val untypedLocalQuery: LocalQuery<*> =
      localQueries.getOrPut(key) {
        PreferCacheLocalQuery(
          cacheOnlyLocalQuery,
          serverOnlyLocalQuery,
          cpuDispatcher,
          key.dataDeserializer,
          key.dataSerializersModule,
          logger,
        )
      }

    @Suppress("UNCHECKED_CAST") return untypedLocalQuery as PreferCacheLocalQuery<T>
  }

  private fun <T> getOrPutCacheOnly(key: Key<T>): LocalQuery<T> {
    check(key.fetchPolicy == QueryRef.FetchPolicy.CACHE_ONLY)

    val untypedLocalQuery: LocalQuery<*> =
      localQueries.getOrPut(key) {
        if (cacheInfo === null) {
          CacheOnlyNoCacheLocalQuery(logger)
        } else {
          CacheOnlyLocalQuery(
            cacheInfo.db,
            cacheInfo.initializeJob,
            key.authUid,
            key.queryId,
            cpuDispatcher,
            key.dataDeserializer,
            key.dataSerializersModule,
            currentTimeMillis,
            logger,
          )
        }
      }

    @Suppress("UNCHECKED_CAST") return untypedLocalQuery as LocalQuery<T>
  }

  fun <T> getOrPutServerOnly(
    key: Key<T>,
    operationName: String,
    variables: Struct,
  ): ServerOnlyLocalQuery<T> {
    check(key.fetchPolicy == QueryRef.FetchPolicy.SERVER_ONLY)

    val remoteKey = key.toRemoteKey()

    val remoteQuery =
      remoteQueries.getOrPut(remoteKey, operationName, variables) {
        if (cacheInfo === null) {
          null
        } else {
          QueryCacheUpdater(
            cacheInfo = cacheInfo,
            authUid = key.authUid,
            queryId = key.queryId,
            cpuDispatcher = cpuDispatcher,
            coroutineScope = coroutineScope,
            currentTimeMillis = currentTimeMillis,
            logger = logger,
          )
        }
      }

    val untypedLocalQuery: LocalQuery<*> =
      localQueries.getOrPut(key) {
        ServerOnlyLocalQuery(
          remoteQuery,
          cpuDispatcher,
          key.dataDeserializer,
          key.dataSerializersModule,
          logger,
        )
      }

    @Suppress("UNCHECKED_CAST") return untypedLocalQuery as ServerOnlyLocalQuery<T>
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
