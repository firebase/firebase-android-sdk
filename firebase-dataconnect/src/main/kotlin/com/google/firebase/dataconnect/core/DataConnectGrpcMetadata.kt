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
import com.google.firebase.dataconnect.BuildConfig
import com.google.firebase.dataconnect.util.buildStructProto
import com.google.protobuf.Struct
import io.grpc.Metadata

internal class DataConnectGrpcMetadata(
  val dataConnectAuth: DataConnectAuth,
  val connectorLocation: String,
  val kotlinVersion: String,
  val androidVersion: Int,
  val dataConnectSdkVersion: String,
  val grpcVersion: String,
) {
  @Suppress("SpellCheckingInspection")
  private val googRequestParamsHeaderValue = "location=${connectorLocation}&frontend=data"

  @Suppress("SpellCheckingInspection")
  private val googApiClientHeaderValue =
    "gl-kotlin/$kotlinVersion gl-android/$androidVersion fire/$dataConnectSdkVersion grpc/$grpcVersion"

  suspend fun get(requestId: String): Metadata {
    val token = dataConnectAuth.getAccessToken(requestId)
    return Metadata().also {
      it.put(googRequestParamsHeader, googRequestParamsHeaderValue)
      it.put(googApiClientHeader, googApiClientHeaderValue)
      if (token !== null) {
        it.put(firebaseAuthTokenHeader, token)
      }
    }
  }

  companion object {
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

    private val firebaseAuthTokenHeader: Metadata.Key<String> =
      Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)

    @Suppress("SpellCheckingInspection")
    private val googRequestParamsHeader: Metadata.Key<String> =
      Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)

    @Suppress("SpellCheckingInspection")
    private val googApiClientHeader: Metadata.Key<String> =
      Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)

    fun forSystemVersions(
      dataConnectAuth: DataConnectAuth,
      connectorLocation: String,
    ): DataConnectGrpcMetadata =
      DataConnectGrpcMetadata(
        dataConnectAuth = dataConnectAuth,
        connectorLocation = connectorLocation,
        kotlinVersion = "${KotlinVersion.CURRENT}",
        androidVersion = Build.VERSION.SDK_INT,
        dataConnectSdkVersion = BuildConfig.VERSION_NAME,
        grpcVersion = "", // no way to get the grpc version at runtime
      )
  }
}
