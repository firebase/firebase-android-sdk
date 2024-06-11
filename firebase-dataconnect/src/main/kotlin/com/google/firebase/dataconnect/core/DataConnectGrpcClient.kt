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

import android.os.Build
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.firebase.dataconnect.util.buildStructProto
import com.google.firebase.dataconnect.util.decodeFromStruct
import com.google.firebase.dataconnect.util.toCompactString
import com.google.firebase.dataconnect.util.toMap
import com.google.firebase.dataconnect.util.toStructProto
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.ConnectorServiceGrpc
import google.firebase.dataconnect.proto.GraphqlError
import google.firebase.dataconnect.proto.executeMutationRequest
import google.firebase.dataconnect.proto.executeQueryRequest
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy

internal class DataConnectGrpcClient(
  projectId: String,
  connector: ConnectorConfig,
  private val dataConnectAuth: DataConnectAuth,
  grpcRPCsFactory: DataConnectGrpcRPCsFactory,
  parentLogger: Logger,
) {
  private val logger =
    Logger("DataConnectGrpcClient").apply {
      debug {
        "Created by ${parentLogger.nameWithId};" +
          " projectId=$projectId" +
          " host=${grpcRPCsFactory.host}" +
          " sslEnabled=${grpcRPCsFactory.sslEnabled}" +
          " connector=$connector"
      }
    }

  private val requestName =
    "projects/$projectId/" +
      "locations/${connector.location}" +
      "/services/${connector.serviceId}" +
      "/connectors/${connector.connector}"

  @Suppress("SpellCheckingInspection")
  private val googRequestParamsHeaderValue = "location=${connector.location}&frontend=data"

  private val closedMutex = Mutex()
  private var closed = false

  private val lazyGrpcRPCs =
    SuspendingLazy(closedMutex) {
      check(!closed) { "DataConnectGrpcClient ${logger.nameWithId} instance has been closed" }
      grpcRPCsFactory.newInstance()
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
    val metadata = createMetadata(requestId)

    logger.logGrpcSending(
      requestId = requestId,
      kotlinMethodName = "executeQuery($operationName)",
      grpcMethod = ConnectorServiceGrpc.getExecuteQueryMethod(),
      metadata = metadata,
      request = request.toStructProto(),
      requestTypeName = "ExecuteQueryRequest",
    )

    val response =
      lazyGrpcRPCs
        .get()
        .runCatching { executeQuery(request, metadata) }
        .onFailure {
          logger.warn(it) {
            "executeQuery($operationName) [rid=$requestId] grpc call FAILED with ${it::class.qualifiedName}"
          }
        }
        .getOrThrow()

    logger.logGrpcReceived(
      requestId = requestId,
      kotlinMethodName = "executeQuery($operationName)",
      response = response.toStructProto(),
      responseTypeName = "ExecuteQueryResponse",
    )

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
    val metadata = createMetadata(requestId)

    logger.logGrpcSending(
      requestId = requestId,
      kotlinMethodName = "executeMutation($operationName)",
      grpcMethod = ConnectorServiceGrpc.getExecuteMutationMethod(),
      metadata = metadata,
      request = request.toStructProto(),
      requestTypeName = "ExecuteMutationRequest",
    )

    val response =
      lazyGrpcRPCs
        .get()
        .runCatching { executeMutation(request, metadata) }
        .onFailure {
          logger.warn(it) {
            "executeMutation() [rid=$requestId] grpc call FAILED with ${it::class.qualifiedName}"
          }
        }
        .getOrThrow()

    logger.logGrpcReceived(
      requestId = requestId,
      kotlinMethodName = "executeMutation($operationName)",
      response = response.toStructProto(),
      responseTypeName = "ExecuteMutationResponse",
    )

    return OperationResult(
      data = if (response.hasData()) response.data else null,
      errors = response.errorsList.map { it.toDataConnectError() }
    )
  }

  private suspend fun createMetadata(requestId: String): Metadata {
    val token = dataConnectAuth.getAccessToken(requestId)
    return Metadata().also {
      it.put(googRequestParamsHeader, googRequestParamsHeaderValue)
      it.put(googApiClientHeader, googApiClientHeaderValue)
      if (token !== null) {
        it.put(firebaseAuthTokenHeader, token)
      }
    }
  }

  suspend fun close() {
    closedMutex.withLock { closed = true }
    lazyGrpcRPCs.initializedValueOrNull?.close()
  }

  private companion object {
    val firebaseAuthTokenHeader: Metadata.Key<String> =
      Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)

    @Suppress("SpellCheckingInspection")
    val googRequestParamsHeader: Metadata.Key<String> =
      Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)

    @Suppress("SpellCheckingInspection")
    val googApiClientHeader: Metadata.Key<String> =
      Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)

    @Suppress("SpellCheckingInspection")
    val googApiClientHeaderValue: String by
      lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildList {
            add("gl-kotlin/${KotlinVersion.CURRENT}")
            add("gl-android/${Build.VERSION.SDK_INT}")
            add("fire/${BuildConfig.VERSION_NAME}")
            add("grpc/")
          }
          .joinToString(" ")
      }

    fun Metadata.toStructProto(): Struct = buildStructProto {
      val keys: List<Metadata.Key<String>> = run {
        val keySet: MutableSet<String> = keys().toMutableSet()
        // Always explicitly include the auth header in the returned string, even if it is absent.
        keySet.add(firebaseAuthTokenHeader.name())
        keySet.sorted().map { Metadata.Key.of(it, Metadata.ASCII_STRING_MARSHALLER) }
      }

      for (key in keys) {
        val values = getAll(key)
        val scrubbedValues =
          if (values === null) listOf(null)
          else {
            values.map {
              if (key.name() == firebaseAuthTokenHeader.name()) it.toScrubbedAccessToken() else it
            }
          }

        for (scrubbedValue in scrubbedValues) {
          put(key.name(), scrubbedValue)
        }
      }
    }

    fun Logger.logGrpcSending(
      requestId: String,
      kotlinMethodName: String,
      grpcMethod: MethodDescriptor<*, *>,
      metadata: Metadata,
      request: Struct,
      requestTypeName: String
    ) = debug {
      val struct = buildStructProto {
        put("RPC", grpcMethod.fullMethodName)
        put("Metadata", metadata.toStructProto())
        put(requestTypeName, request)
      }
      // Sort the keys in the output string to be more meaningful than alphabetical.
      val keySortSelector: (String) -> String = {
        when (it) {
          "RPC" -> "AAAA"
          "Metadata" -> "AAAB"
          requestTypeName -> "AAAC"
          else -> it
        }
      }
      "$kotlinMethodName [rid=$requestId] sending: ${struct.toCompactString(keySortSelector)}"
    }

    fun Logger.logGrpcReceived(
      requestId: String,
      kotlinMethodName: String,
      response: Struct,
      responseTypeName: String
    ) = debug {
      val struct = buildStructProto { put(responseTypeName, response) }
      "$kotlinMethodName [rid=$requestId] received: ${struct.toCompactString()}"
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
