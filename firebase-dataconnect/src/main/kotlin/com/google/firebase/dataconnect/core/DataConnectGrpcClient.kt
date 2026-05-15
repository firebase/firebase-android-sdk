/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.ExperimentalRealtimeQueries
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef.FetchPolicy
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.GraphqlError
import io.grpc.Status
import io.grpc.StatusException

internal class DataConnectGrpcClient(
  private val grpcRPCs: DataConnectGrpcRPCs,
  private val dataConnectAuth: DataConnectAuth,
  private val dataConnectAppCheck: DataConnectAppCheck,
  private val logger: Logger,
) {
  val instanceId: String
    get() = logger.nameWithId

  data class OperationResult(
    val data: Struct?,
    val errors: List<GraphqlError>,
    val source: DataSource,
  )

  suspend fun executeQuery(
    requestId: String,
    operationName: String,
    variables: Struct,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    fetchPolicy: FetchPolicy,
  ): OperationResult {
    val executeQueryResult =
      grpcRPCs.retryOnGrpcUnauthenticatedError(requestId, "executeQuery") { authToken, appCheckToken
        ->
        executeQuery(
          requestId,
          operationName,
          variables,
          callerSdkType,
          fetchPolicy,
          authToken,
          appCheckToken,
        )
      }

    return executeQueryResult.toOperationResult()
  }

  suspend fun executeMutation(
    requestId: String,
    operationName: String,
    variables: Struct,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): OperationResult {
    val response =
      grpcRPCs.retryOnGrpcUnauthenticatedError(requestId, "executeMutation") {
        authToken,
        appCheckToken ->
        executeMutation(
          requestId,
          operationName,
          variables,
          callerSdkType,
          authToken,
          appCheckToken,
        )
      }

    return OperationResult(
      data = if (response.hasData()) response.data else null,
      errors = response.errorsList,
      source = DataSource.SERVER,
    )
  }

  @ExperimentalRealtimeQueries
  suspend fun connect(
    streamId: String,
    requestId: String,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): DataConnectBidiConnectStream =
    grpcRPCs.retryOnGrpcUnauthenticatedError(requestId, "connect") { authToken, appCheckToken ->
      connect(
        requestId,
        callerSdkType,
        authToken,
        appCheckToken,
      )
    }

  private suspend inline fun <T, R> T.retryOnGrpcUnauthenticatedError(
    requestId: String,
    kotlinMethodName: String,
    block: T.(GetAuthTokenResult?, GetAppCheckTokenResult?) -> R,
  ): R {
    val authToken1 = dataConnectAuth.getToken(requestId)
    val appCheckToken1 = dataConnectAppCheck.getToken(requestId)

    return try {
      block(authToken1, appCheckToken1)
    } catch (e: StatusException) {
      if (e.status.code != Status.UNAUTHENTICATED.code) {
        throw e
      }
      logger.warn(e) {
        "$kotlinMethodName() [rid=$requestId]" +
          " retrying with fresh Auth and/or AppCheck tokens due to UNAUTHENTICATED error"
      }

      // TODO(b/356877295) Only invalidate auth or appcheck tokens, but not both, to avoid
      //  spamming the appcheck attestation provider.
      dataConnectAuth.forceRefresh()
      dataConnectAppCheck.forceRefresh()

      val authToken2 = dataConnectAuth.getToken(requestId)
      val appCheckToken2 = dataConnectAppCheck.getToken(requestId)

      block(authToken2, appCheckToken2)
    }
  }
}

private fun DataConnectGrpcRPCs.ExecuteQueryResult.toOperationResult():
  DataConnectGrpcClient.OperationResult =
  when (this) {
    is DataConnectGrpcRPCs.ExecuteQueryResult.FromCache ->
      DataConnectGrpcClient.OperationResult(
        data = data,
        errors = emptyList(),
        source = DataSource.CACHE,
      )
    is DataConnectGrpcRPCs.ExecuteQueryResult.FromServer ->
      DataConnectGrpcClient.OperationResult(
        data = if (response.hasData()) response.data else null,
        errors = response.errorsList,
        source = DataSource.SERVER,
      )
  }
