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
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import com.google.protobuf.listValue
import com.google.protobuf.struct
import com.google.protobuf.value
import google.internal.firebase.firemat.v0.DataServiceGrpcKt.DataServiceCoroutineStub
import google.internal.firebase.firemat.v0.DataServiceOuterClass.GraphqlError
import google.internal.firebase.firemat.v0.executeMutationRequest
import google.internal.firebase.firemat.v0.executeQueryRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.android.AndroidChannelBuilder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

internal class DataConnectGrpcClient(
  val context: Context,
  val projectId: String,
  val location: String,
  val service: String,
  val hostName: String,
  val port: Int,
  val sslEnabled: Boolean,
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

      // TODO: Create a dedicated executor rather than using a global one.
      //  See go/kotlin/coroutines/coroutine-contexts-scopes.md for details.
      it.executor(Dispatchers.IO.asExecutor())

      // Wrap the `ManagedChannelBuilder` in an `AndroidChannelBuilder`. This allows the channel to
      // respond more gracefully to network change events, such as switching from cellular to wifi.
      AndroidChannelBuilder.usingBuilder(it).context(context).build()
    }
  }

  private val grpcStub: DataServiceCoroutineStub by lazy { DataServiceCoroutineStub(grpcChannel) }

  data class ExecuteQueryResult(val data: Map<String, Any?>, val errors: List<GraphqlError>)

  data class ExecuteMutationResult(val data: Map<String, Any?>, val errors: List<GraphqlError>)

  suspend fun executeQuery(
    operationSet: String,
    operationName: String,
    revision: String,
    variables: Map<String, Any?>
  ): ExecuteQueryResult {
    val request = executeQueryRequest {
      this.name = name(operationSet = operationSet, revision = revision)
      this.operationName = operationName
      this.variables = structFromMap(variables)
    }

    logger.debug { "executeQuery() sending request: $request" }
    val response = grpcStub.executeQuery(request)
    logger.debug { "executeQuery() got response: $response" }
    return ExecuteQueryResult(
      data = mapFromStruct(response.data),
      errors = response.errorsList,
    )
  }

  suspend fun executeMutation(
    operationSet: String,
    operationName: String,
    revision: String,
    variables: Map<String, Any?>
  ): ExecuteMutationResult {
    val request = executeMutationRequest {
      this.name = name(operationSet = operationSet, revision = revision)
      this.operationName = operationName
      this.variables = structFromMap(variables)
    }

    logger.debug { "executeMutation() sending request: $request" }
    val response = grpcStub.executeMutation(request)
    logger.debug { "executeMutation() got response: $response" }
    return ExecuteMutationResult(
      data = mapFromStruct(response.data),
      errors = response.errorsList,
    )
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

private fun mapFromStruct(struct: Struct): Map<String, Any?> =
  struct.fieldsMap.mapValues { objectFromStructValue(it.value) }

private fun objectFromStructValue(struct: Value): Any? =
  struct.run {
    when (kindCase) {
      Value.KindCase.NULL_VALUE -> null
      Value.KindCase.BOOL_VALUE -> boolValue
      Value.KindCase.NUMBER_VALUE -> numberValue
      Value.KindCase.STRING_VALUE -> stringValue
      Value.KindCase.LIST_VALUE -> listValue.valuesList.map { objectFromStructValue(it) }
      Value.KindCase.STRUCT_VALUE -> mapFromStruct(structValue)
      else -> throw IllegalArgumentException("unsupported Struct kind: $kindCase")
    }
  }

private fun structFromMap(map: Map<String, Any?>) = struct {
  map.keys.sorted().forEach { key -> fields.put(key, valueFromObject(map[key])) }
}

private fun valueFromObject(obj: Any?): Value = value {
  when (obj) {
    null -> nullValue = NullValue.NULL_VALUE
    is String -> stringValue = obj
    is Boolean -> boolValue = obj
    is Int -> numberValue = obj.toDouble()
    is Double -> numberValue = obj
    is Map<*, *> ->
      structValue =
        obj.let {
          struct {
            it.forEach { entry ->
              val key = entry.key as? String ?: error("unsupported map key: $entry.key")
              fields.put(key, valueFromObject(entry.value))
            }
          }
        }
    is Iterable<*> ->
      listValue = obj.let { listValue { it.forEach { values.add(valueFromObject(it)) } } }
    else -> throw IllegalArgumentException("unsupported value type: ${obj::class}")
  }
}
