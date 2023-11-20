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
import google.internal.firebase.firemat.v0.DataServiceGrpcKt.DataServiceCoroutineStub
import google.internal.firebase.firemat.v0.executeMutationRequest
import google.internal.firebase.firemat.v0.executeQueryRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.android.AndroidChannelBuilder
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

internal class DataConnectGrpcClient(
  val context: Context,
  val projectId: String,
  val location: String,
  val service: String,
  val hostName: String,
  val port: Int,
  val sslEnabled: Boolean,
  executor: Executor,
  creatorLoggerId: String,
) {
  private val logger = Logger("DataConnectGrpcClient")

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

  suspend fun <VariablesType, DataType> executeQuery(
    operationSet: String,
    operationName: String,
    revision: String,
    variables: VariablesType,
    variablesSerializer: SerializationStrategy<VariablesType>,
    dataDeserializer: DeserializationStrategy<DataType>
  ): DataType {
    val request = executeQueryRequest {
      this.name = name(operationSet = operationSet, revision = revision)
      this.operationName = operationName
      this.variables = encodeToStruct(variablesSerializer, variables)
    }

    logger.debug { "executeQuery() sending request: $request" }
    val response =
      try {
        grpcStub.executeQuery(request)
      } catch (e: Throwable) {
        logger.warn { "executeQuery() network transport error: $e" }
        throw NetworkTransportException("query network transport error: ${e.message}", e)
      }
    logger.debug { "executeQuery() got response: $response" }
    if (response.errorsList.isNotEmpty()) {
      throw GraphQLException(
        "query failed: ${response.errorsList}",
        response.errorsList.map { it.toString() }
      )
    }

    return decodeFromStruct(dataDeserializer, response.data)
  }

  suspend fun <VariablesType, DataType> executeMutation(
    operationSet: String,
    operationName: String,
    revision: String,
    variables: VariablesType,
    variablesSerializer: SerializationStrategy<VariablesType>,
    dataDeserializer: DeserializationStrategy<DataType>
  ): DataType {
    val request = executeMutationRequest {
      this.name = name(operationSet = operationSet, revision = revision)
      this.operationName = operationName
      this.variables = encodeToStruct(variablesSerializer, variables)
    }

    logger.debug { "executeMutation() sending request: $request" }
    val response =
      try {
        grpcStub.executeMutation(request)
      } catch (e: Throwable) {
        logger.warn { "executeMutation() network transport error: $e" }
        throw NetworkTransportException("mutation network transport error: ${e.message}", e)
      }
    logger.debug { "executeMutation() got response: $response" }
    if (response.errorsList.isNotEmpty()) {
      throw GraphQLException(
        "mutation failed: ${response.errorsList}",
        response.errorsList.map { it.toString() }
      )
    }

    return decodeFromStruct(dataDeserializer, response.data)
  }

  override fun toString(): String {
    return "FirebaseDataConnectClient{" +
      "projectId=$projectId, location=$location, service=$service, " +
      "hostName=$hostName, port=$port, sslEnabled=$sslEnabled}"
  }

  fun close() {
    logger.debug { "close() starting" }
    grpcChannel.shutdownNow()
    logger.debug { "close() done" }
  }

  private fun name(operationSet: String, revision: String): String =
    "projects/$projectId/locations/$location/services/$service/" +
      "operationSets/$operationSet/revisions/$revision"
}
