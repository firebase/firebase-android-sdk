// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

import android.content.Context
import com.google.android.gms.security.ProviderInstaller
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.v1main.DataServiceGrpcKt.DataServiceCoroutineStub
import google.firebase.dataconnect.v1main.DataServiceOuterClass.GraphqlError
import google.firebase.dataconnect.v1main.executeMutationRequest
import google.firebase.dataconnect.v1main.executeQueryRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.android.AndroidChannelBuilder
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy

internal class DataConnectGrpcClient(
  context: Context,
  projectId: String,
  serviceId: String,
  location: String,
  operationSet: String,
  revision: String,
  hostName: String,
  port: Int,
  sslEnabled: Boolean,
  private val executor: Executor,
  creatorLoggerId: String,
) {
  private val logger =
    Logger("DataConnectGrpcClient").apply { debug { "Created from $creatorLoggerId" } }

  private val requestName =
    "projects/$projectId/locations/$location/services/$serviceId/" +
      "operationSets/$operationSet/revisions/$revision"

  // Protects `closed`, `grpcChannel`, and `grpcStub`.
  private val mutex = Mutex()

  // All accesses to this variable _must_ have locked `mutex`.
  private var closed = false

  // All accesses to this variable _must_ have locked `mutex`. Note, however, that once a reference
  // to the lazily-created object is obtained, then the mutex can be unlocked and the instance can
  // be used.
  private val grpcChannel =
    lazy(LazyThreadSafetyMode.NONE) {
      logger.debug { "${ManagedChannel::class.qualifiedName} initialization started" }

      if (closed) throw IllegalStateException("DataConnectGrpcClient instance has been closed")

      // Upgrade the Android security provider using Google Play Services.
      //
      // We need to upgrade the Security Provider before any network channels are initialized
      // because
      // okhttp maintains a list of supported providers that is initialized when the JVM first
      // resolves the static dependencies of ManagedChannel.
      //
      // If initialization fails for any reason, then a warning is logged and the original,
      // un-upgraded security provider is used.
      try {
        ProviderInstaller.installIfNeeded(context)
      } catch (e: Exception) {
        logger.warn(e) { "Failed to update ssl context" }
      }

      val channel =
        ManagedChannelBuilder.forAddress(hostName, port).let {
          if (!sslEnabled) {
            it.usePlaintext()
          }

          // Ensure gRPC recovers from a dead connection. This is not typically necessary, as
          // the OS will  usually notify gRPC when a connection dies. But not always. This acts as a
          // failsafe.
          it.keepAliveTime(30, TimeUnit.SECONDS)

          it.executor(executor)

          // Wrap the `ManagedChannelBuilder` in an `AndroidChannelBuilder`. This allows the channel
          // to respond more gracefully to network change events, such as switching from cellular to
          // wifi.
          AndroidChannelBuilder.usingBuilder(it).context(context).build()
        }

      logger.debug { "${ManagedChannel::class.qualifiedName} initialization completed" }

      channel
    }

  private val grpcChannelOrNull
    get() = if (grpcChannel.isInitialized()) grpcChannel.value else null

  // All accesses to this variable _must_ have locked `mutex`. Note, however, that once a reference
  // to the lazily-created object is obtained, then the mutex can be unlocked and the instance can
  // be used.
  private val grpcStub: DataServiceCoroutineStub by
    lazy(LazyThreadSafetyMode.NONE) { DataServiceCoroutineStub(grpcChannel.value) }

  data class OperationResult(
    val data: Struct?,
    val errors: List<DataConnectError>,
    val sequenceNumber: Long
  )

  data class DeserialzedOperationResult<T>(val data: T, val sequenceNumber: Long)

  suspend fun executeQuery(
    requestId: String,
    sequenceNumber: Long,
    operationName: String,
    variables: Struct,
  ): OperationResult {
    val request = executeQueryRequest {
      this.name = requestName
      this.operationName = operationName
      this.variables = variables
    }

    logger.debug {
      "executeQuery() [rid=$requestId] sending " +
        "ExecuteQueryRequest: ${request.toCompactString()}"
    }
    val response =
      mutex
        .withLock { grpcStub }
        .runCatching { executeQuery(request) }
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
      errors = response.errorsList.map { it.toDataConnectError() },
      sequenceNumber = sequenceNumber,
    )
  }

  suspend fun executeMutation(
    requestId: String,
    sequenceNumber: Long,
    operationName: String,
    variables: Struct,
  ): OperationResult {
    val request = executeMutationRequest {
      this.name = requestName
      this.operationName = operationName
      this.variables = variables
    }

    logger.debug {
      "executeMutation() [rid=$requestId] sending " +
        "ExecuteMutationRequest: ${request.toCompactString()}"
    }
    val response =
      mutex
        .withLock { grpcStub }
        .runCatching { executeMutation(request) }
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
      errors = response.errorsList.map { it.toDataConnectError() },
      sequenceNumber = sequenceNumber,
    )
  }

  private val closingMutex = Mutex()
  private var closeCompleted = false

  suspend fun close() {
    val grpcChannel =
      mutex.withLock {
        closed = true
        grpcChannelOrNull
      }

    closingMutex.withLock {
      if (!closeCompleted) {
        grpcChannel?.terminate()
      }
      closeCompleted = true
    }
  }

  private suspend fun ManagedChannel.terminate() {
    shutdown()

    val deferred = CompletableDeferred<Unit>()
    thread(isDaemon = true, name = "ManagedChannel.terminate() from ${logger.id}") {
      deferred.completeWith(
        runCatching<Unit> { awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS) }
      )
    }

    deferred.await()
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
): DataConnectGrpcClient.DeserialzedOperationResult<T> {
  val deserializedData: T =
    if (data === null) {
      // TODO: include the variables and error list in the thrown exception
      throw DataConnectException("no data included in result: errors=$errors")
    } else if (errors.isNotEmpty()) {
      throw DataConnectException("operation failed: errors=$errors")
    } else {
      decodeFromStruct(dataDeserializer, data)
    }

  return DataConnectGrpcClient.DeserialzedOperationResult(
    data = deserializedData,
    sequenceNumber = sequenceNumber,
  )
}

internal fun <V, D> DataConnectGrpcClient.DeserialzedOperationResult<D>.toDataConnectResult(
  variables: V
) = DataConnectResult(variables = variables, data = data, sequenceNumber = sequenceNumber)
