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
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.testutil.accessToken
import com.google.firebase.dataconnect.testutil.connectorConfig
import com.google.firebase.dataconnect.testutil.requestId
import io.grpc.Metadata
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectGrpcMetadataUnitTest {

  @Test
  fun `should include x-goog-api-client`() = runTest {
    val key = "pkprzbns45"
    val testValues = DataConnectGrpcMetadataTestValues.fromKey(key)
    val dataConnectGrpcMetadata =
      testValues.newDataConnectGrpcMetadata(
        kotlinVersion = "cdsz85awyc",
        androidVersion = 490843892,
        dataConnectSdkVersion = "v3q46qc2ax",
        grpcVersion = "fq9fhx6j5e",
      )
    val requestId = Arb.requestId(key).next()

    val metadata = dataConnectGrpcMetadata.get(requestId)

    metadata.asClue {
      it.keys() shouldContain "x-goog-api-client"
      val metadataKey = Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)
      it.get(metadataKey) shouldBe
        "gl-kotlin/cdsz85awyc gl-android/490843892 fire/v3q46qc2ax grpc/fq9fhx6j5e"
    }
  }

  @Test
  fun `should include x-goog-request-params`() = runTest {
    val key = "67ns7bkvx8"
    val testValues = DataConnectGrpcMetadataTestValues.fromKey(key)
    val location = testValues.connectorConfig.location
    val dataConnectGrpcMetadata = testValues.newDataConnectGrpcMetadata()
    val requestId = Arb.requestId(key).next()

    val metadata = dataConnectGrpcMetadata.get(requestId)

    metadata.asClue {
      it.keys() shouldContain "x-goog-request-params"
      val metadataKey = Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)
      it.get(metadataKey) shouldBe "location=${location}&frontend=data"
    }
  }

  @Test
  fun `should omit x-firebase-auth-token when the access token is null`() = runTest {
    val key = "d85j28zpw9"
    val testValues = DataConnectGrpcMetadataTestValues.fromKey(key)
    coEvery { testValues.dataConnectAuth.getAccessToken(any()) } returns null
    val dataConnectGrpcMetadata = testValues.newDataConnectGrpcMetadata()
    val requestId = Arb.requestId(key).next()

    val metadata = dataConnectGrpcMetadata.get(requestId)

    metadata.asClue { it.keys() shouldNotContain "x-firebase-auth-token" }
  }

  @Test
  fun `should include x-firebase-auth-token when the access token is not null`() = runTest {
    val key = "d85j28zpw9"
    val accessToken = Arb.accessToken(key).next()
    val testValues = DataConnectGrpcMetadataTestValues.fromKey(key)
    coEvery { testValues.dataConnectAuth.getAccessToken(any()) } returns accessToken
    val dataConnectGrpcMetadata = testValues.newDataConnectGrpcMetadata()
    val requestId = Arb.requestId(key).next()

    val metadata = dataConnectGrpcMetadata.get(requestId)

    metadata.asClue {
      it.keys() shouldContain "x-firebase-auth-token"
      val metadataKey = Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)
      it.get(metadataKey) shouldBe accessToken
    }
  }

  @Test
  fun `forSystemVersions() should return correct values`() = runTest {
    val key = "4vjtde6zyv"
    val testValues = DataConnectGrpcMetadataTestValues.fromKey(key)
    val dataConnectAuth = testValues.dataConnectAuth
    val connectorLocation = testValues.connectorConfig.location

    val metadata =
      DataConnectGrpcMetadata.forSystemVersions(
        dataConnectAuth = dataConnectAuth,
        connectorLocation = connectorLocation,
      )

    metadata.asClue {
      it.dataConnectAuth shouldBeSameInstanceAs dataConnectAuth
      it.connectorLocation shouldBeSameInstanceAs connectorLocation
      it.kotlinVersion shouldBe "${KotlinVersion.CURRENT}"
      it.androidVersion shouldBe Build.VERSION.SDK_INT
      it.dataConnectSdkVersion shouldBe BuildConfig.VERSION_NAME
      it.grpcVersion shouldBe ""
    }
  }

  private data class DataConnectGrpcMetadataTestValues(
    val dataConnectAuth: DataConnectAuth,
    val requestIdSlot: CapturingSlot<String>,
    val connectorConfig: ConnectorConfig,
  ) {

    fun newDataConnectGrpcMetadata(
      kotlinVersion: String = "1.2.3",
      androidVersion: Int = 4,
      dataConnectSdkVersion: String = "5.6.7",
      grpcVersion: String = "8.9.10"
    ): DataConnectGrpcMetadata =
      DataConnectGrpcMetadata(
        dataConnectAuth = dataConnectAuth,
        connectorLocation = connectorConfig.location,
        kotlinVersion = kotlinVersion,
        androidVersion = androidVersion,
        dataConnectSdkVersion = dataConnectSdkVersion,
        grpcVersion = grpcVersion,
      )

    companion object {
      fun fromKey(
        key: String,
        rs: RandomSource = RandomSource.default()
      ): DataConnectGrpcMetadataTestValues {
        val dataConnectAuth: DataConnectAuth = mockk(relaxed = true)

        val accessTokenArb = Arb.accessToken(key)
        val requestIdSlot = slot<String>()
        coEvery { dataConnectAuth.getAccessToken(capture(requestIdSlot)) } answers
          {
            accessTokenArb.next(rs)
          }

        return DataConnectGrpcMetadataTestValues(
          dataConnectAuth = dataConnectAuth,
          requestIdSlot = requestIdSlot,
          connectorConfig = Arb.connectorConfig(key).next(rs),
        )
      }
    }
  }
}
