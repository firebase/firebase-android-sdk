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

import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectExecuteException
import com.google.firebase.dataconnect.DataConnectOperationResponse
import com.google.firebase.dataconnect.DataConnectOperationResponse.Error.PathSegment
import com.google.firebase.dataconnect.DataConnectOperationResponseImpl
import com.google.firebase.dataconnect.DataConnectUntypedData
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.DataConnectGrpcClientGlobals.toDataConnectError
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toMap
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.GraphqlError
import google.firebase.dataconnect.proto.executeMutationRequest
import google.firebase.dataconnect.proto.executeQueryRequest
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class DataConnectGrpcClient(
  projectId: String,
  connector: ConnectorConfig,
  private val grpcRPCs: DataConnectGrpcRPCs,
  private val dataConnectAuth: DataConnectAuth,
  private val dataConnectAppCheck: DataConnectAppCheck,
  private val logger: Logger,
) {
  val instanceId: String
    get() = logger.nameWithId

  private val requestName =
    "projects/$projectId/" +
      "locations/${connector.location}" +
      "/services/${connector.serviceId}" +
      "/connectors/${connector.connector}"

  data class OperationResult(
    val data: Struct?,
    val errors: List<DataConnectOperationResponse.Error>,
  )

  suspend fun executeQuery(
    requestId: String,
    operationName: String,
    variables: Struct,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): OperationResult {
    val request = executeQueryRequest {
      this.name = requestName
      this.operationName = operationName
      this.variables = variables
    }

    val response =
      grpcRPCs.retryOnGrpcUnauthenticatedError(requestId, "executeQuery") {
        executeQuery(requestId, request, callerSdkType)
      }

    return OperationResult(
      data = if (response.hasData()) response.data else null,
      errors = response.errorsList.map { it.toDataConnectError() }
    )
  }

  suspend fun executeMutation(
    requestId: String,
    operationName: String,
    variables: Struct,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): OperationResult {
    val request = executeMutationRequest {
      this.name = requestName
      this.operationName = operationName
      this.variables = variables
    }

    val response =
      grpcRPCs.retryOnGrpcUnauthenticatedError(requestId, "executeMutation") {
        executeMutation(requestId, request, callerSdkType)
      }

    return OperationResult(
      data = if (response.hasData()) response.data else null,
      errors = response.errorsList.map { it.toDataConnectError() }
    )
  }

  private suspend inline fun <T, R> T.retryOnGrpcUnauthenticatedError(
    requestId: String,
    kotlinMethodName: String,
    block: T.() -> R
  ): R {
    return try {
      block()
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

      block()
    }
  }
}

/**
 * Holder for "global" functions related to [DataConnectGrpcClient].
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * DataConnectGrpcClientKit Java class with public visibility, which pollutes the public API. Using
 * an "internal" object, instead, to gather together the top-level functions avoids this public API
 * pollution.
 */
internal object DataConnectGrpcClientGlobals {
  private fun ListValue.toPathSegment() =
    valuesList.map {
      when (val kind = it.kindCase) {
        Value.KindCase.STRING_VALUE -> PathSegment.Field(it.stringValue)
        Value.KindCase.NUMBER_VALUE -> PathSegment.ListIndex(it.numberValue.toInt())
        else -> PathSegment.Field("invalid PathSegment kind: $kind")
      }
    }

  fun GraphqlError.toDataConnectError() =
    DataConnectOperationResponseImpl.ErrorImpl(
      message = message,
      path = path.toPathSegment(),
    )

  fun <T> DataConnectGrpcClient.OperationResult.deserialize(
    deserializer: DeserializationStrategy<T>,
    serializersModule: SerializersModule?,
  ): T {
    return if (deserializer === DataConnectUntypedData) {
      @Suppress("UNCHECKED_CAST")
      DataConnectUntypedData(data?.toMap(), errors) as T
    } else if (data === null) {
      if (errors.isNotEmpty()) {
        val response =
          DataConnectOperationResponseImpl<T>(data, errors = errors, decodedData = null)
        throw DataConnectExecuteException("operation failed: errors=$errors", response = response)
      } else {
        val response =
          DataConnectOperationResponseImpl<T>(data, errors = emptyList(), decodedData = null)
        throw DataConnectExecuteException("no data included in result", response = response)
      }
    } else {
      val decodeResult = runCatching { decodeFromStruct(data!!, deserializer, serializersModule) }
      decodeResult.fold(
        onSuccess = {
          if (errors.isNotEmpty()) {
            val response =
              DataConnectOperationResponseImpl(data.toMap(), errors = errors, decodedData = it)
            throw DataConnectExecuteException(
              "operation failed: errors=$errors (data=$data)",
              response = response,
            )
          }
          it
        },
        onFailure = {
          val response =
            DataConnectOperationResponseImpl<T>(data.toMap(), errors = errors, decodedData = null)
          throw DataConnectExecuteException(
            "decoding response data failed: $it (data=$data)",
            cause = it,
            response = response,
          )
        },
      )
    }
  }
}
