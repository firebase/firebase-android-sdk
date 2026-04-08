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
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult
import com.google.firebase.dataconnect.util.SequencedReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class PreferCacheLocalQuery<Data>(
  private val cacheOnlyLocalQuery: CacheOnlyLocalQuery<Data>,
  private val serverOnlyLocalQuery: ServerOnlyLocalQuery<Data>,
  cpuDispatcher: CoroutineDispatcher,
  dataDeserializer: DeserializationStrategy<Data>,
  dataSerializersModule: SerializersModule?,
  private val logger: Logger,
) : LocalQuery<Data>(cpuDispatcher, dataDeserializer, dataSerializersModule, logger) {

  override suspend fun executeImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: DataConnectAuth.GetAuthTokenResult?,
    appCheckToken: DataConnectAppCheck.GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<ExecuteImplResult> {
    run {
      val getQueryResultResult =
        cacheOnlyLocalQuery.executeImpl(
          requestId,
          GetQueryResultResult.Stale::class,
        )
      logger.logGetQueryResultResult(requestId, getQueryResultResult)
      val executeImplResult = getQueryResultResult.toExecuteImplResult()
      if (executeImplResult !== null) {
        return SequencedReference(sequenceNumber, executeImplResult)
      }
    }

    return serverOnlyLocalQuery.executeImpl(
      requestId = requestId,
      sequenceNumber = sequenceNumber,
      authToken = authToken,
      appCheckToken = appCheckToken,
      callerSdkType = callerSdkType,
    )
  }

  private companion object {

    fun GetQueryResultResult.toExecuteImplResult(): ExecuteImplResult? =
      when (this) {
        GetQueryResultResult.NotFound -> null
        is GetQueryResultResult.Stale -> null
        is GetQueryResultResult.Found ->
          ExecuteImplResult(
            struct.toExecuteQueryResponseProto(),
            DataSource.CACHE,
          )
      }
  }
}
