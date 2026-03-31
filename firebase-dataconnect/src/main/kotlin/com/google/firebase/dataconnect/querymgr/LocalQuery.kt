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
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal sealed class LocalQuery<Data>(
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
    val response =
      executeImpl(
        requestId = requestId,
        sequenceNumber = sequenceNumber,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    when (response) {
      ExecuteImplResult.Retry -> return ExecuteResult.Retry
      is ExecuteImplResult.Success -> {}
    }

    if (response.sequenceNumber < sequenceNumber) {
      return ExecuteResult.Retry
    }

    val dataDeserializeResult =
      withContext(cpuDispatcher) {
        response.data.runCatching { deserialize(dataDeserializer, dataSerializersModule) }
      }

    dataDeserializeResult.onFailure {
      logger.warn(it) { "[rid=$requestId] decoding response data failed" }
    }

    return ExecuteResult.Success(dataDeserializeResult.getOrThrow(), response.source)
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
      val data: ExecuteQueryResponse,
      val source: DataSource
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
