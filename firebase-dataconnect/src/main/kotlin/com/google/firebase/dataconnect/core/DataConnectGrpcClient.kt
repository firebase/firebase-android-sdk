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
import com.google.firebase.dataconnect.core.DataConnectAuth.Companion.withScrubbedAccessToken
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.firebase.dataconnect.util.decodeFromStruct
import com.google.firebase.dataconnect.util.toCompactString
import com.google.firebase.dataconnect.util.toMap
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.ConnectorServiceGrpc
import google.firebase.dataconnect.proto.GraphqlError
import google.firebase.dataconnect.proto.executeMutationRequest
import google.firebase.dataconnect.proto.executeQueryRequest
import io.grpc.Metadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy

internal class DataConnectGrpcClient(
  projectId: String,
  private val connector: ConnectorConfig,
  private val dataConnectAuth: DataConnectAuth,
  grpcRPCsFactory: DataConnectGrpcRPCsFactory,
  parentLogger: Logger,
) {
  private val logger =
    Logger("DataConnectGrpcClient").apply {
      debug { "Created by ${parentLogger.nameWithId}; projectId=$projectId connector=$connector" }
    }

  private val requestName =
    "projects/$projectId/" +
      "locations/${connector.location}" +
      "/services/${connector.serviceId}" +
      "/connectors/${connector.connector}"

  private val closedMutex = Mutex()
  private var closed = false

  private val lazyGrpcRPCs =
    SuspendingLazy(closedMutex) {
      if (closed) throw IllegalStateException("DataConnectGrpcClient instance has been closed")
      grpcRPCsFactory.run {
        logger.debug { "Creating GRPC connection with host=${host} sslEnabled=${sslEnabled}" }
        newInstance()
      }
    }

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

    logger.debug {
      val method = ConnectorServiceGrpc.getExecuteQueryMethod()
      "executeQuery() [rid=$requestId] sending RPC \"${method.fullMethodName}\" with " +
        "ExecuteQueryRequest: ${request.toCompactString()}"
    }
    val response =
      lazyGrpcRPCs
        .get()
        .runCatching { executeQuery(request, createMetadata(requestId)) }
        .onFailure {
          logger.warn(it) {
            "executeQuery() [rid=$requestId] grpc call FAILED with ${it::class.qualifiedName}"
          }
        }
        .getOrThrow()
    logger.debug {
      "executeQuery() [rid=$requestId] received: " +
        "ExecuteQueryResponse ${response.toCompactString()}"
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

    logger.debug {
      val method = ConnectorServiceGrpc.getExecuteMutationMethod()
      "executeMutation() [rid=$requestId] sending RPC \"${method.fullMethodName}\" with " +
        "ExecuteMutationRequest: ${request.toCompactString()}"
    }
    val response =
      lazyGrpcRPCs
        .get()
        .runCatching { executeMutation(request, createMetadata(requestId)) }
        .onFailure {
          logger.warn(it) {
            "executeMutation() [rid=$requestId] grpc call FAILED with ${it::class.qualifiedName}"
          }
        }
        .getOrThrow()
    logger.debug {
      "executeMutation() [rid=$requestId] received: " +
        "ExecuteMutationResponse ${response.toCompactString()}"
    }

    return OperationResult(
      data = if (response.hasData()) response.data else null,
      errors = response.errorsList.map { it.toDataConnectError() }
    )
  }

  private suspend fun createMetadata(requestId: String): Metadata {
    val token = dataConnectAuth.getAccessToken(requestId)

    return Metadata()
      .apply {
        put(googRequestParamsHeader, "location=${connector.location}&frontend=data")
        if (token !== null) {
          put(firebaseAuthTokenHeader, token)
        }
      }
      .also {
        logger.debug {
          "[rid=$requestId] Sending grpc metadata \"${googRequestParamsHeader.name()}\": " +
            it.get(googRequestParamsHeader)
        }
        logger.debug {
          it.get(firebaseAuthTokenHeader).let { authHeaderValue ->
            if (authHeaderValue === null) {
              "[rid=$requestId] Not sending grpc metadata \"${firebaseAuthTokenHeader.name()}\" " +
                "because no auth token is available"
            } else {
              "[rid=$requestId] Sending grpc metadata \"${firebaseAuthTokenHeader.name()}\": " +
                it.get(firebaseAuthTokenHeader)?.withScrubbedAccessToken(token ?: "")
            }
          }
        }
      }
  }

  suspend fun close() {
    closedMutex.withLock { closed = true }
    lazyGrpcRPCs.initializedValueOrNull?.close()
  }

  private companion object {
    private val firebaseAuthTokenHeader =
      Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)

    @Suppress("SpellCheckingInspection")
    private val googRequestParamsHeader =
      Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)
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

internal fun GraphqlError.toDataConnectError() =
  DataConnectError(message = message, path = path.toPathSegment(), extensions = emptyMap())

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
