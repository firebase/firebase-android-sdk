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
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.protobuf.Struct
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class LocalQuerySubscriptions(
  private val localQueries: LocalQueries,
  streamManager: QueryManager.StreamManager,
  private val cpuDispatcher: CoroutineDispatcher,
  private val coroutineScope: CoroutineScope,
  private val logger: Logger,
) {
  private val localSubscriptions = mutableMapOf<Key<*>, LocalQuerySubscription<*>>()
  private val remoteSubscriptions =
    RemoteQuerySubscriptions(
      streamManager,
      cpuDispatcher,
      coroutineScope,
      logger,
    )

  fun <T> getOrPut(
    key: Key<T>,
    operationName: String,
    variables: Struct,
  ): LocalQuerySubscription<T> {

    val cacheOnlyLocalQuery: CacheOnlyLocalQuery<T>? = run {
      val localQueryKey =
        LocalQueries.Key(
          authUid = key.authUid,
          queryId = key.queryId,
          dataDeserializer = key.dataDeserializer,
          dataSerializersModule = key.dataSerializersModule,
          fetchPolicy = QueryRef.FetchPolicy.CACHE_ONLY,
        )
      val localQuery = localQueries.getOrPut(localQueryKey, operationName, variables)
      localQuery as? CacheOnlyLocalQuery<T>
    }

    val cacheUpdater: QueryCacheUpdater? = run {
      val localQueryKey =
        LocalQueries.Key(
          authUid = key.authUid,
          queryId = key.queryId,
          dataDeserializer = key.dataDeserializer,
          dataSerializersModule = key.dataSerializersModule,
          fetchPolicy = QueryRef.FetchPolicy.SERVER_ONLY,
        )
      val localQuery = localQueries.getOrPutServerOnly(localQueryKey, operationName, variables)
      localQuery.remoteQuery.cacheUpdater
    }

    val remoteQuerySubscription: RemoteQuerySubscription = run {
      val remoteSubscriptionKey = RemoteQuerySubscriptions.Key(key.authUid, key.queryId)
      remoteSubscriptions.getOrPut(
        remoteSubscriptionKey,
        operationName,
        variables,
        { cacheUpdater }
      )
    }

    val untypedLocalSubscription: LocalQuerySubscription<*> =
      localSubscriptions.getOrPut(key) {
        LocalQuerySubscription(
          cacheOnlyLocalQuery,
          remoteQuerySubscription,
          cpuDispatcher,
          key.dataDeserializer,
          key.dataSerializersModule,
          coroutineScope,
          logger,
        )
      }

    @Suppress("UNCHECKED_CAST") return untypedLocalSubscription as LocalQuerySubscription<T>
  }

  data class Key<Data>(
    val authUid: String?,
    val queryId: ImmutableByteArray,
    val dataDeserializer: DeserializationStrategy<Data>,
    val dataSerializersModule: SerializersModule?,
  )
}
