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
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SequencedReference.Companion.mapSuspending
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
    authToken: DataConnectAuth.GetAuthTokenResult?,
    appCheckToken: DataConnectAppCheck.GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<ExecuteResult<Data>> {
    val executeImplResultSequencedReference =
      executeImpl(
        requestId = requestId,
        sequenceNumber = sequenceNumber,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    return executeImplResultSequencedReference.mapSuspending { it.toExecuteResult(requestId) }
  }

  abstract suspend fun executeImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: DataConnectAuth.GetAuthTokenResult?,
    appCheckToken: DataConnectAppCheck.GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<ExecuteImplResult>

  suspend fun ExecuteImplResult.toExecuteResult(requestId: String): ExecuteResult<Data> =
    toExecuteResult(
      requestId,
      executeQueryResponse,
      dataSource,
      cpuDispatcher,
      dataDeserializer,
      dataSerializersModule,
      logger,
    )

  data class ExecuteResult<out T>(val data: T, val source: DataSource)

  data class ExecuteImplResult(
    val executeQueryResponse: ExecuteQueryResponseProto,
    val dataSource: DataSource
  )

  companion object {

    suspend fun <Data> toExecuteResult(
      requestId: String,
      executeQueryResponse: ExecuteQueryResponseProto,
      dataSource: DataSource,
      cpuDispatcher: CoroutineDispatcher,
      dataDeserializer: DeserializationStrategy<Data>,
      dataSerializersModule: SerializersModule?,
      logger: Logger,
    ): ExecuteResult<Data> {
      val dataDeserializeResult =
        withContext(cpuDispatcher) {
          executeQueryResponse.runCatching { deserialize(dataDeserializer, dataSerializersModule) }
        }

      dataDeserializeResult.onFailure {
        logger.warn(it) { "[rid=$requestId] decoding response data failed" }
      }

      return ExecuteResult(dataDeserializeResult.getOrThrow(), dataSource)
    }
  }
}
