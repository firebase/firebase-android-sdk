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

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class LocalQuery<Data>(
  private val remoteQuery: RemoteQuery,
  private val cpuDispatcher: CoroutineDispatcher,
  private val dataDeserializer: DeserializationStrategy<Data>,
  private val dataSerializersModule: SerializersModule?,
) {

  suspend fun execute(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteResult<Data> {
    val remoteResult =
      remoteQuery.execute(
        requestId = requestId,
        sequenceNumber = sequenceNumber,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    val response: ExecuteQueryResponse =
      when (remoteResult) {
        RemoteQuery.ExecuteResult.Retry -> return ExecuteResult.Retry
        is RemoteQuery.ExecuteResult.Success ->
          if (remoteResult.response.sequenceNumber < sequenceNumber) {
            return ExecuteResult.Retry
          } else {
            remoteResult.response.ref
          }
      }

    val data =
      withContext(cpuDispatcher) { response.deserialize(dataDeserializer, dataSerializersModule) }

    return ExecuteResult.Success(data)
  }

  sealed interface ExecuteResult<out T> {
    data class Success<T>(val data: T) : ExecuteResult<T>
    data object Retry : ExecuteResult<Nothing>
  }
}
