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
import google.firebase.dataconnect.proto.ExecuteQueryResponse as ExecuteQueryResponseProto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
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
    val executeImplResult =
      callExecuteImpl(
        requestId = requestId,
        sequenceNumber = sequenceNumber,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    val (executeQueryResponse, dataSource) = executeImplResult ?: return ExecuteResult.Retry

    return transformExecuteImplResult(requestId, executeQueryResponse, dataSource)
  }

  private suspend fun callExecuteImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Pair<ExecuteQueryResponseProto, DataSource>? {
    val executeImplResult =
      executeImpl(
        requestId = requestId,
        sequenceNumber = sequenceNumber,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    return when (executeImplResult) {
      ExecuteImplResult.Retry -> null
      is ExecuteImplResult.Success -> {
        if (executeImplResult.sequenceNumber < sequenceNumber) {
          null
        } else {
          Pair(executeImplResult.executeQueryResponse, executeImplResult.dataSource)
        }
      }
    }
  }

  private suspend fun transformExecuteImplResult(
    requestId: String,
    executeQueryResponse: ExecuteQueryResponseProto,
    dataSource: DataSource,
  ): ExecuteResult.Success<Data> {
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
