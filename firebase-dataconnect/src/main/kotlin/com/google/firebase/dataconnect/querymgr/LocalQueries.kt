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
  coroutineScope: CoroutineScope,
) {

  private val localQueries = mutableMapOf<Key<*>, LocalQuery<*>>()
  private val remoteQueries = RemoteQueries(dataConnectGrpcRPCs, cpuDispatcher, coroutineScope)
  private val localQueryLogger = Logger("LocalQuery")

  fun <T> getOrPut(
    key: Key<T>,
    requestProto: ExecuteQueryRequestProto,
  ): LocalQuery<T> {
    val remoteKey = key.toRemoteKey()
    val remoteQuery = remoteQueries.getOrPut(remoteKey, requestProto)

    val localQuery: LocalQuery<*> =
      localQueries.getOrPut(key) {
        ServerOnlyLocalQuery(
          remoteQuery,
          cpuDispatcher,
          key.dataDeserializer,
          key.dataSerializersModule,
          localQueryLogger,
        )
      }

    @Suppress("UNCHECKED_CAST") return localQuery as LocalQuery<T>
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
