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

import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.util.decodeFromStruct
import com.google.firebase.dataconnect.util.toMap
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.GraphqlError
import google.firebase.dataconnect.proto.SourceLocation
import google.firebase.dataconnect.proto.executeMutationRequest
import google.firebase.dataconnect.proto.executeQueryRequest
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.serialization.DeserializationStrategy

internal class DataConnectGrpcClient(
  projectId: String,
  connector: ConnectorConfig,
  private val grpcRPCs: DataConnectGrpcRPCs,
  private val dataConnectAuth: DataConnectAuth,
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
    val errors: List<DataConnectError>,
  )

  suspend fun executeQuery(
    requestId: String,
    operationName: String,
    variables: Struct,
  ): OperationResult {
    val request = executeQueryRequest {
      this.name = requestName
      this.operationName = operationName
      this.variables = variables
    }

    val response =
      grpcRPCs.retryOnGrpcUnauthenticatedError(requestId, "executeQuery") {
        executeQuery(requestId, request)
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
  ): OperationResult {
    val request = executeMutationRequest {
      this.name = requestName
      this.operationName = operationName
      this.variables = variables
    }

    val response =
      grpcRPCs.retryOnGrpcUnauthenticatedError(requestId, "executeMutation") {
        executeMutation(requestId, request)
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
      if (e.status != Status.UNAUTHENTICATED) {
        throw e
      }
      logger.warn(e) {
        "$kotlinMethodName() [rid=$requestId]" +
          " retrying with fresh auth token due to UNAUTHENTICATED error"
      }
      dataConnectAuth.forceRefresh()
      block()
    }
  }
}

internal fun ListValue.toPathSegment() =
  valuesList.map {
    when (val kind = it.kindCase) {
      Value.KindCase.STRING_VALUE -> DataConnectError.PathSegment.Field(it.stringValue)
      Value.KindCase.NUMBER_VALUE -> DataConnectError.PathSegment.ListIndex(it.numberValue.toInt())
      else -> DataConnectError.PathSegment.Field("invalid PathSegment kind: $kind")
    }
  }

internal fun List<SourceLocation>.toSourceLocations(): List<DataConnectError.SourceLocation> =
  buildList {
    this@toSourceLocations.forEach {
      add(DataConnectError.SourceLocation(line = it.line, column = it.column))
    }
  }

internal fun GraphqlError.toDataConnectError() =
  DataConnectError(
    message = message,
    path = path.toPathSegment(),
    this.locationsList.toSourceLocations()
  )

internal fun <T> DataConnectGrpcClient.OperationResult.deserialize(
  dataDeserializer: DeserializationStrategy<T>
): T =
  if (dataDeserializer === DataConnectUntypedData) {
    @Suppress("UNCHECKED_CAST")
    DataConnectUntypedData(data?.toMap(), errors) as T
  } else if (data === null) {
    if (errors.isNotEmpty()) {
      throw DataConnectException("operation failed: errors=$errors")
    } else {
      throw DataConnectException("no data included in result")
    }
  } else if (errors.isNotEmpty()) {
    throw DataConnectException("operation failed: errors=$errors (data=$data)")
  } else {
    try {
      decodeFromStruct(dataDeserializer, data)
    } catch (dataConnectException: DataConnectException) {
      throw dataConnectException
    } catch (throwable: Throwable) {
      throw DataConnectException("decoding response data failed: $throwable", throwable)
    }
  }
