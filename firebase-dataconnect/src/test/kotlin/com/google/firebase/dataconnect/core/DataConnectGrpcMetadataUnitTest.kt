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
@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.core

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.dataconnect.BuildConfig
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectGrpcMetadata
import io.grpc.Metadata
import io.kotest.assertions.asClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbs.travel.airport
import io.kotest.property.arbs.usernames
import io.kotest.property.checkAll
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataConnectGrpcMetadataUnitTest {

  @get:Rule
  val firebaseAppFactory =
    FirebaseAppUnitTestingRule(
      appNameKey = "sj4293acqj",
      applicationIdKey = "kd8n74kn2j",
      projectIdKey = "jhtzhpbtbm"
    )

  @Test
  fun `should include x-goog-api-client when callerSdkType is Generated`() = runTest {
    val dataConnectGrpcMetadataArb =
      Arb.dataConnect.dataConnectGrpcMetadata(
        kotlinVersion = Arb.constant("cdsz85awyc"),
        androidVersion = Arb.constant(490843892),
        dataConnectSdkVersion = Arb.constant("v3q46qc2ax"),
        grpcVersion = Arb.constant("fq9fhx6j5e"),
      )

    checkAll(propTestConfig, dataConnectGrpcMetadataArb, authTokenArb(), appCheckTokenArb()) {
      dataConnectGrpcMetadata,
      authToken,
      appCheckToken ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = authToken,
          appCheckToken = appCheckToken,
          callerSdkType = CallerSdkType.Generated,
        )

      metadata.asClue {
        it.keys() shouldContain "x-goog-api-client"
        val metadataKey = Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)
        it.get(metadataKey) shouldBe
          "gl-kotlin/cdsz85awyc gl-android/490843892 fire/v3q46qc2ax grpc/fq9fhx6j5e kotlin/gen"
      }
    }
  }

  @Test
  fun `should include x-goog-api-client when callerSdkType is Base`() = runTest {
    val dataConnectGrpcMetadataArb =
      Arb.dataConnect.dataConnectGrpcMetadata(
        kotlinVersion = Arb.constant("cdsz85awyc"),
        androidVersion = Arb.constant(490843892),
        dataConnectSdkVersion = Arb.constant("v3q46qc2ax"),
        grpcVersion = Arb.constant("fq9fhx6j5e"),
      )

    checkAll(propTestConfig, dataConnectGrpcMetadataArb, authTokenArb(), appCheckTokenArb()) {
      dataConnectGrpcMetadata,
      authToken,
      appCheckToken ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = authToken,
          appCheckToken = appCheckToken,
          callerSdkType = CallerSdkType.Base,
        )

      metadata.asClue {
        it.keys() shouldContain "x-goog-api-client"
        val metadataKey = Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)
        it.get(metadataKey) shouldBe
          "gl-kotlin/cdsz85awyc gl-android/490843892 fire/v3q46qc2ax grpc/fq9fhx6j5e"
      }
    }
  }

  @Test
  fun `should include x-goog-request-params`() = runTest {
    val dataConnectGrpcMetadataArb =
      Arb.dataConnect.dataConnectGrpcMetadata(
        connectorLocation = Arb.constant("q8mgtztcz2"),
      )

    checkAll(
      propTestConfig,
      dataConnectGrpcMetadataArb,
      authTokenArb(),
      appCheckTokenArb(),
      Arb.enum<CallerSdkType>()
    ) { dataConnectGrpcMetadata, authToken, appCheckToken, callerSdkType ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = authToken,
          appCheckToken = appCheckToken,
          callerSdkType = callerSdkType,
        )

      metadata.asClue {
        it.keys() shouldContain "x-goog-request-params"
        val metadataKey = Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)
        it.get(metadataKey) shouldBe "location=q8mgtztcz2&frontend=data"
      }
    }
  }

  @Test
  fun `should include x-firebase-gmpid`() = runTest {
    val dataConnectGrpcMetadataArb =
      Arb.dataConnect.dataConnectGrpcMetadata(appId = Arb.constant("tvsxjeb745.appId"))

    checkAll(
      propTestConfig,
      dataConnectGrpcMetadataArb,
      authTokenArb(),
      appCheckTokenArb(),
      Arb.enum<CallerSdkType>()
    ) { dataConnectGrpcMetadata, authToken, appCheckToken, callerSdkType ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = authToken,
          appCheckToken = appCheckToken,
          callerSdkType = callerSdkType,
        )

      metadata.asClue {
        it.keys() shouldContain "x-firebase-gmpid"
        val metadataKey = Metadata.Key.of("x-firebase-gmpid", Metadata.ASCII_STRING_MARSHALLER)
        it.get(metadataKey) shouldBe "tvsxjeb745.appId"
      }
    }
  }

  @Test
  fun `should NOT include x-firebase-gmpid if appId is the empty string`() = runTest {
    val dataConnectGrpcMetadataArb =
      Arb.dataConnect.dataConnectGrpcMetadata(appId = Arb.constant(""))

    checkAll(
      propTestConfig,
      dataConnectGrpcMetadataArb,
      authTokenArb(),
      appCheckTokenArb(),
      Arb.enum<CallerSdkType>()
    ) { dataConnectGrpcMetadata, authToken, appCheckToken, callerSdkType ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = authToken,
          appCheckToken = appCheckToken,
          callerSdkType = callerSdkType,
        )

      metadata.asClue { it.keys() shouldNotContain "x-firebase-gmpid" }
    }
  }

  @Test
  fun `should NOT include x-firebase-gmpid if appId is blank`() = runTest {
    val dataConnectGrpcMetadataArb =
      Arb.dataConnect.dataConnectGrpcMetadata(appId = Arb.constant(" \r\n\t "))

    checkAll(
      propTestConfig,
      dataConnectGrpcMetadataArb,
      authTokenArb(),
      appCheckTokenArb(),
      Arb.enum<CallerSdkType>()
    ) { dataConnectGrpcMetadata, authToken, appCheckToken, callerSdkType ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = authToken,
          appCheckToken = appCheckToken,
          callerSdkType = callerSdkType,
        )

      metadata.asClue { it.keys() shouldNotContain "x-firebase-gmpid" }
    }
  }

  @Test
  fun `should omit x-firebase-auth-token when the auth token is null`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.dataConnectGrpcMetadata(),
      appCheckTokenArb(),
      Arb.enum<CallerSdkType>()
    ) { dataConnectGrpcMetadata, appCheckToken, callerSdkType ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = null,
          appCheckToken = appCheckToken,
          callerSdkType = callerSdkType,
        )

      metadata.asClue { it.keys() shouldNotContain "x-firebase-auth-token" }
    }
  }

  @Test
  fun `should include x-firebase-auth-token when the auth token is not null`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.dataConnectGrpcMetadata(),
      nonNullAuthTokenArb(),
      appCheckTokenArb(),
      Arb.enum<CallerSdkType>()
    ) { dataConnectGrpcMetadata, nonNullAuthToken, appCheckToken, callerSdkType ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = nonNullAuthToken,
          appCheckToken = appCheckToken,
          callerSdkType = callerSdkType,
        )

      metadata.asClue {
        it.keys() shouldContain "x-firebase-auth-token"
        val metadataKey = Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)
        it.get(metadataKey) shouldBe nonNullAuthToken
      }
    }
  }

  @Test
  fun `should omit x-firebase-appcheck when the AppCheck token is null`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.dataConnectGrpcMetadata(),
      authTokenArb(),
      Arb.enum<CallerSdkType>()
    ) { dataConnectGrpcMetadata, authToken, callerSdkType ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = authToken,
          appCheckToken = null,
          callerSdkType = callerSdkType,
        )

      metadata.asClue { it.keys() shouldNotContain "x-firebase-appcheck" }
    }
  }

  @Test
  fun `should include x-firebase-appcheck when the AppCheck token is not null`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.dataConnectGrpcMetadata(),
      authTokenArb(),
      nonNullAppCheckTokenArb(),
      Arb.enum<CallerSdkType>()
    ) { dataConnectGrpcMetadata, authToken, nonNullAppCheckToken, callerSdkType ->
      val metadata =
        dataConnectGrpcMetadata.get(
          authToken = authToken,
          appCheckToken = nonNullAppCheckToken,
          callerSdkType = callerSdkType,
        )

      metadata.asClue {
        it.keys() shouldContain "x-firebase-appcheck"
        val metadataKey = Metadata.Key.of("x-firebase-appcheck", Metadata.ASCII_STRING_MARSHALLER)
        it.get(metadataKey) shouldBe nonNullAppCheckToken
      }
    }
  }

  @Test
  fun `forSystemVersions() should return correct values`() = runTest {
    val connectorLocation = Arb.dataConnect.connectorLocation().next()

    val dataConnectGrpcMetadata =
      DataConnectGrpcMetadata.forSystemVersions(
        firebaseApp = firebaseAppFactory.newInstance(),
        connectorLocation = connectorLocation,
        parentLogger = mockk(relaxed = true),
      )

    dataConnectGrpcMetadata.asClue {
      it.connectorLocation shouldBeSameInstanceAs connectorLocation
      it.kotlinVersion shouldBe "${KotlinVersion.CURRENT}"
      it.androidVersion shouldBe Build.VERSION.SDK_INT
      it.dataConnectSdkVersion shouldBe BuildConfig.VERSION_NAME
      it.grpcVersion shouldBe ""
    }
  }
}

private val propTestConfig =
  PropTestConfig(
    iterations = 5,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private fun nonNullAuthTokenArb(): Arb<String> = Arb.usernames().map { "authToken_${it.value}" }

private fun authTokenArb(): Arb<String?> = nonNullAuthTokenArb().orNull(nullProbability = 0.2)

private fun nonNullAppCheckTokenArb(): Arb<String> =
  Arb.airport().map { "appCheckToken_${it.iataCode}" }

private fun appCheckTokenArb(): Arb<String?> =
  nonNullAppCheckTokenArb().orNull(nullProbability = 0.2)
