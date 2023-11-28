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
import google.internal.firebase.firemat.v0.DataServiceGrpcKt.DataServiceCoroutineStub
import google.internal.firebase.firemat.v0.DataServiceOuterClass.GraphqlError
import google.internal.firebase.firemat.v0.executeMutationRequest
import google.internal.firebase.firemat.v0.executeQueryRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.android.AndroidChannelBuilder
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
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
  executor: Executor,
  creatorLoggerId: String,
) {
  private val logger = Logger("DataConnectGrpcClient")

  private val requestName =
    "projects/$projectId/locations/$location/services/$serviceId/" +
      "operationSets/$operationSet/revisions/$revision"

  init {
    logger.debug { "Created from $creatorLoggerId" }
  }

  private val grpcChannel: ManagedChannel by lazy {
    // Upgrade the Android security provider using Google Play Services.
    //
    // We need to upgrade the Security Provider before any network channels are initialized because
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

    ManagedChannelBuilder.forAddress(hostName, port).let {
      if (!sslEnabled) {
        it.usePlaintext()
      }

      // Ensure gRPC recovers from a dead connection. This is not typically necessary, as
      // the OS will  usually notify gRPC when a connection dies. But not always. This acts as a
      // failsafe.
      it.keepAliveTime(30, TimeUnit.SECONDS)

      it.executor(executor)

      // Wrap the `ManagedChannelBuilder` in an `AndroidChannelBuilder`. This allows the channel to
      // respond more gracefully to network change events, such as switching from cellular to wifi.
      AndroidChannelBuilder.usingBuilder(it).context(context).build()
    }
  }

  private val grpcStub: DataServiceCoroutineStub by lazy { DataServiceCoroutineStub(grpcChannel) }

  data class OperationResult(val data: Struct?, val errors: List<DataConnectError>)
  data class DeserialzedOperationResult<T>(val data: T, val errors: List<DataConnectError>)

  suspend fun executeQuery(operationName: String, variables: Struct): OperationResult {
    val request = executeQueryRequest {
      this.name = requestName
      this.operationName = operationName
      this.variables = variables
    }

    logger.debug { "executeQuery() sending request: $request" }
    val response = grpcStub.executeQuery(request)
    logger.debug { "executeQuery() got response: $response" }

    return OperationResult(
      data = if (response.hasData()) response.data else null,
      errors = response.errorsList.map { it.toDataConnectError() }
    )
  }

  suspend fun executeMutation(operationName: String, variables: Struct): OperationResult {
    val request = executeMutationRequest {
      this.name = requestName
      this.operationName = operationName
      this.variables = variables
    }

    logger.debug { "executeMutation() sending request: $request" }
    val response = grpcStub.executeMutation(request)
    logger.debug { "executeMutation() got response: $response" }

    return OperationResult(
      data = if (response.hasData()) response.data else null,
      errors = response.errorsList.map { it.toDataConnectError() }
    )
  }

  fun close() {
    logger.debug { "close() starting" }
    grpcChannel.shutdownNow()
    logger.debug { "close() done" }
  }
}

internal fun <T> Struct.decode(deserializer: DeserializationStrategy<T>) =
  decodeFromStruct(deserializer, this)

internal fun ListValue.decodePath() =
  valuesList.map {
    when (val kind = it.kindCase) {
      Value.KindCase.STRING_VALUE -> DataConnectError.PathSegment.Field(it.stringValue)
      Value.KindCase.NUMBER_VALUE -> DataConnectError.PathSegment.ListIndex(it.numberValue.toInt())
      else -> throw IllegalStateException("invalid PathSegement kind: $kind")
    }
  }

internal fun GraphqlError.toDataConnectError() =
  DataConnectError(message = message, path = path.decodePath(), extensions = emptyMap())

internal fun <T> DataConnectGrpcClient.OperationResult.deserialize(
  dataDeserializer: DeserializationStrategy<T>
): DataConnectGrpcClient.DeserialzedOperationResult<T> {
  if (data === null) {
    // TODO: include the variables and error list in the thrown exception
    throw DataConnectException("no data included in result: errors=${errors}")
  }
  return DataConnectGrpcClient.DeserialzedOperationResult(
    data = decodeFromStruct(dataDeserializer, data),
    errors = errors
  )
}

internal fun <V, D> DataConnectGrpcClient.DeserialzedOperationResult<D>.toDataConnectResult(
  variables: V
) = DataConnectResult(variables = variables, data = data, errors = errors)
