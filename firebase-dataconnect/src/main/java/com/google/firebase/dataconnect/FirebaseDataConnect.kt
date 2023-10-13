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

import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.internal.firebase.firemat.v0.DataServiceGrpc
import google.internal.firebase.firemat.v0.DataServiceGrpc.DataServiceBlockingStub
import google.internal.firebase.firemat.v0.DataServiceOuterClass.ExecuteMutationRequest
import google.internal.firebase.firemat.v0.DataServiceOuterClass.ExecuteQueryRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.android.AndroidChannelBuilder
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.CoroutineDispatcher

class FirebaseDataConnect
internal constructor(
  private val firebaseApp: FirebaseApp,
  private val backgroundDispatcher: CoroutineDispatcher,
  private val blockingDispatcher: CoroutineDispatcher,
) {
  private val logger = LoggerImpl("FirebaseDataConnect", Logger.Level.DEBUG)

  private val settingsLock = ReentrantReadWriteLock()
  private var settingsFrozen = false

  private val grpcChannel: ManagedChannel by lazy {
    // Make sure that no further changes can be made to the settings.
    settingsLock.write { settingsFrozen = true }

    // Upgrade the Android security provider using Google Play Services.
    //
    // We need to upgrade the Security Provider before any network channels are initialized because
    // okhttp maintains a list of supported providers that is initialized when the JVM first
    // resolves the static dependencies of ManagedChannel.
    //
    // If initialization fails for any reason, then a warning is logged and the original,
    // un-upgraded security provider is used.
    try {
      ProviderInstaller.installIfNeeded(firebaseApp.applicationContext)
    } catch (e: Exception) {
      logger.warn(e) { "Failed to update ssl context" }
    }

    val channelBuilder = ManagedChannelBuilder.forAddress(settings.hostName, settings.port)
    if (!settings.sslEnabled) {
      channelBuilder.usePlaintext()
    }

    // Ensure gRPC recovers from a dead connection. This is not typically necessary, as
    // the OS will  usually notify gRPC when a connection dies. But not always. This acts as a
    // failsafe.
    channelBuilder.keepAliveTime(30, TimeUnit.SECONDS)

    // Wrap the `ManagedChannelBuilder` in an `AndroidChannelBuilder`. This allows the channel to
    // respond more gracefully to network change events, such as switching from cellular to wifi.
    AndroidChannelBuilder.usingBuilder(channelBuilder)
      .context(firebaseApp.applicationContext)
      .build()
  }

  private val grpcStub: DataServiceBlockingStub by lazy {
    DataServiceGrpc.newBlockingStub(grpcChannel)
  }

  var settings: FirebaseDataConnectSettings = FirebaseDataConnectSettings.defaultInstance
    get() {
      settingsLock.read {
        return field
      }
    }
    set(value) {
      settingsLock.write {
        if (settingsFrozen) {
          throw IllegalStateException("settings cannot be modified after they are used")
        }
        field = value
      }
    }

  fun executeQuery(
    projectId: String,
    location: String,
    operationName: String,
    variables: Map<String, Any?>
  ): Struct {
    val request =
      ExecuteQueryRequest.newBuilder().let {
        it.name =
          "projects/${projectId}/locations/${location}/services/s/operationSets/crud/revisions/r"
        it.operationName = operationName
        it.variables = structFromMap(variables)
        it.build()
      }

    logger.debug { "executeQuery() sending request: $request" }

    val response = grpcStub.executeQuery(request)

    logger.debug { "executeQuery() got response: $response" }

    return response.data
  }

  fun executeMutation(
    projectId: String,
    location: String,
    operationName: String,
    variables: Map<String, Any?>
  ): Struct {
    val request =
      ExecuteMutationRequest.newBuilder().let {
        it.name =
          "projects/${projectId}/locations/${location}/services/s/operationSets/crud/revisions/r"
        it.operationName = operationName
        it.variables =
          Struct.newBuilder().run {
            putFields("data", Value.newBuilder().setStructValue(structFromMap(variables)).build())
            build()
          }
        it.build()
      }

    logger.debug { "executeMutation() sending request: $request" }

    val response = grpcStub.executeMutation(request)

    logger.debug { "executeMutation() got response: $response" }

    return response.data
  }

  companion object {
    @JvmStatic
    val instance: FirebaseDataConnect
      get() = getInstance(Firebase.app)

    @JvmStatic
    fun getInstance(app: FirebaseApp): FirebaseDataConnect =
      app.get(FirebaseDataConnect::class.java)
  }
}

private fun structFromMap(map: Map<String, Any?>): Struct =
  Struct.newBuilder().run {
    map.keys.sorted().forEach { key -> putFields(key, protoValueFromObject(map[key])) }
    build()
  }

private fun protoValueFromObject(obj: Any?): Value =
  Value.newBuilder()
    .run {
      when (obj) {
        null -> setNullValue(NullValue.NULL_VALUE)
        is String -> setStringValue(obj)
        is Boolean -> setBoolValue(obj)
        is Double -> setNumberValue(obj)
        else -> throw IllegalArgumentException("unsupported value type: ${obj::class}")
      }
    }
    .build()
