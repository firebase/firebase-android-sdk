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
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class LocalQuerySubscription<out Data>(
  private val cacheOnlyLocalQuery: CacheOnlyLocalQuery<Data>?,
  private val remoteQuerySubscription: RemoteQuerySubscription,
  private val cpuDispatcher: CoroutineDispatcher,
  private val dataDeserializer: DeserializationStrategy<Data>,
  private val dataSerializersModule: SerializersModule?,
  private val coroutineScope: CoroutineScope,
  private val logger: Logger,
) {

  suspend fun subscribe(
    requestId: String,
    authToken: GetAuthTokenResult?,
    appCheckToken: GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Flow<Result<LocalQuery.ExecuteResult<Data>>> {
    val cachedResult: Deferred<LocalQuery.ExecuteResult<Data>>? =
      if (cacheOnlyLocalQuery === null) {
        null
      } else {
        coroutineScope.async {
          cacheOnlyLocalQuery
            .execute(
              requestId = requestId,
              sequenceNumber = nextSequenceNumber(),
              authToken = authToken,
              appCheckToken = appCheckToken,
              callerSdkType = callerSdkType,
            )
            .ref
        }
      }

    val flow =
      remoteQuerySubscription.subscribe(
        requestId = requestId,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    return flow
      .map {
        LocalQuery.runCatching {
          toExecuteResult(
            requestId,
            it,
            DataSource.SERVER,
            cpuDispatcher,
            dataDeserializer,
            dataSerializersModule,
            logger,
          )
        }
      }
      .onStart {
        val result = cachedResult?.runCatching { await() }
        result?.fold(
          onSuccess = { emit(result) },
          onFailure = {
            if (it !is CachedDataNotFoundException) {
              logger.warn(it) { "[rid=$requestId] failed to get cached result; ignoring" }
            }
          }
        )
      }
  }
}
