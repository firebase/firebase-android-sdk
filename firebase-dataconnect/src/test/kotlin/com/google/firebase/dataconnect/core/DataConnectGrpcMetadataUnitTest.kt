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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.dataconnect.BuildConfig
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.appCheckTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.authTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectGrpcMetadata
import io.grpc.Metadata
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import io.mockk.coEvery
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
    val dataConnectGrpcMetadata =
      Arb.dataConnect
        .dataConnectGrpcMetadata(
          kotlinVersion = Arb.constant("cdsz85awyc"),
          androidVersion = Arb.constant(490843892),
          dataConnectSdkVersion = Arb.constant("v3q46qc2ax"),
          grpcVersion = Arb.constant("fq9fhx6j5e"),
        )
        .next()
    val requestId = Arb.dataConnect.requestId().next()

    val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType = CallerSdkType.Generated)

    metadata.asClue {
      it.keys() shouldContain "x-goog-api-client"
      val metadataKey = Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)
      it.get(metadataKey) shouldBe
        "gl-kotlin/cdsz85awyc gl-android/490843892 fire/v3q46qc2ax grpc/fq9fhx6j5e kotlin/gen"
    }
  }

  @Test
  fun `should include x-goog-api-client when callerSdkType is Base`() = runTest {
    val dataConnectGrpcMetadata =
      Arb.dataConnect
        .dataConnectGrpcMetadata(
          kotlinVersion = Arb.constant("cdsz85awyc"),
          androidVersion = Arb.constant(490843892),
          dataConnectSdkVersion = Arb.constant("v3q46qc2ax"),
          grpcVersion = Arb.constant("fq9fhx6j5e"),
        )
        .next()
    val requestId = Arb.dataConnect.requestId().next()

    val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType = CallerSdkType.Base)

    metadata.asClue {
      it.keys() shouldContain "x-goog-api-client"
      val metadataKey = Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)
      it.get(metadataKey) shouldBe
        "gl-kotlin/cdsz85awyc gl-android/490843892 fire/v3q46qc2ax grpc/fq9fhx6j5e"
    }
  }

  @Test
  fun `should include x-goog-request-params`() = runTest {
    val dataConnectGrpcMetadata =
      Arb.dataConnect
        .dataConnectGrpcMetadata(
          connectorLocation = Arb.constant("q8mgtztcz2"),
        )
        .next()
    val requestId = Arb.dataConnect.requestId().next()
    val callerSdkType = Arb.enum<CallerSdkType>().next()

    val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType)

    metadata.asClue {
      it.keys() shouldContain "x-goog-request-params"
      val metadataKey = Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)
      it.get(metadataKey) shouldBe "location=q8mgtztcz2&frontend=data"
    }
  }

  @Test
  fun `should include x-firebase-gmpid`() = runTest {
    val dataConnectGrpcMetadata =
      Arb.dataConnect.dataConnectGrpcMetadata(appId = Arb.constant("tvsxjeb745.appId")).next()
    val requestId = Arb.dataConnect.requestId().next()
    val callerSdkType = Arb.enum<CallerSdkType>().next()

    val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType)

    metadata.asClue {
      it.keys() shouldContain "x-firebase-gmpid"
      val metadataKey = Metadata.Key.of("x-firebase-gmpid", Metadata.ASCII_STRING_MARSHALLER)
      it.get(metadataKey) shouldBe "tvsxjeb745.appId"
    }
  }

  @Test
  fun `should NOT include x-firebase-gmpid if appId is the empty string`() = runTest {
    val dataConnectGrpcMetadata =
      Arb.dataConnect.dataConnectGrpcMetadata(appId = Arb.constant("")).next()
    val requestId = Arb.dataConnect.requestId().next()
    val callerSdkType = Arb.enum<CallerSdkType>().next()

    val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType)

    metadata.asClue { it.keys() shouldNotContain "x-firebase-gmpid" }
  }

  @Test
  fun `should NOT include x-firebase-gmpid if appId is blank`() = runTest {
    val dataConnectGrpcMetadata =
      Arb.dataConnect.dataConnectGrpcMetadata(appId = Arb.constant(" \r\n\t ")).next()
    val requestId = Arb.dataConnect.requestId().next()
    val callerSdkType = Arb.enum<CallerSdkType>().next()

    val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType)

    metadata.asClue { it.keys() shouldNotContain "x-firebase-gmpid" }
  }

  @Test
  fun `should omit x-firebase-auth-token when the auth token is null`() = runTest {
    val getAuthTokenResults: Exhaustive<GetAuthTokenResult?> =
      Exhaustive.of(
        null,
        Arb.dataConnect.authTokenResult(accessToken = Arb.constant(null)).next(),
      )

    checkAll(getAuthTokenResults) { getAuthTokenResult ->
      val dataConnectAuth: DataConnectAuth = mockk()
      coEvery { dataConnectAuth.getToken(any()) } returns getAuthTokenResult
      val dataConnectGrpcMetadata =
        Arb.dataConnect
          .dataConnectGrpcMetadata(dataConnectAuth = Arb.constant(dataConnectAuth))
          .next()
      val requestId = Arb.dataConnect.requestId().next()
      val callerSdkType = Arb.enum<CallerSdkType>().next()

      val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType)

      metadata.asClue { it.keys() shouldNotContain "x-firebase-auth-token" }
    }
  }

  @Test
  fun `should include x-firebase-auth-token when the auth token is not null`() = runTest {
    val dataConnectAuth: DataConnectAuth = mockk()
    val authTokenResult =
      Arb.dataConnect.authTokenResult(accessToken = Arb.dataConnect.accessToken()).next()
    coEvery { dataConnectAuth.getToken(any()) } returns authTokenResult
    val dataConnectGrpcMetadata =
      Arb.dataConnect
        .dataConnectGrpcMetadata(dataConnectAuth = Arb.constant(dataConnectAuth))
        .next()
    val requestId = Arb.dataConnect.requestId().next()
    val callerSdkType = Arb.enum<CallerSdkType>().next()

    val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType)

    metadata.asClue {
      it.keys() shouldContain "x-firebase-auth-token"
      val metadataKey = Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)
      it.get(metadataKey) shouldBe authTokenResult.token
    }
  }

  @Test
  fun `should omit x-firebase-appcheck when the AppCheck token is null`() = runTest {
    val getAppCheckTokenResults: Exhaustive<GetAppCheckTokenResult?> =
      Exhaustive.of(
        null,
        Arb.dataConnect.appCheckTokenResult(accessToken = Arb.constant(null)).next(),
      )

    checkAll(getAppCheckTokenResults) { getAppCheckTokenResult ->
      val dataConnectAppCheck: DataConnectAppCheck = mockk {
        coEvery { getToken(any()) } returns getAppCheckTokenResult
      }
      val dataConnectGrpcMetadata =
        Arb.dataConnect
          .dataConnectGrpcMetadata(dataConnectAppCheck = Arb.constant(dataConnectAppCheck))
          .next()
      val requestId = Arb.dataConnect.requestId().next()
      val callerSdkType = Arb.enum<CallerSdkType>().next()

      val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType)

      metadata.asClue { it.keys() shouldNotContain "x-firebase-appcheck" }
    }
  }

  @Test
  fun `should include x-firebase-appcheck when the AppCheck token is not null`() = runTest {
    val appCheckTokenResult =
      Arb.dataConnect.appCheckTokenResult(accessToken = Arb.dataConnect.accessToken()).next()
    val dataConnectAppCheck: DataConnectAppCheck = mockk {
      coEvery { getToken(any()) } returns appCheckTokenResult
    }
    val dataConnectGrpcMetadata =
      Arb.dataConnect
        .dataConnectGrpcMetadata(dataConnectAppCheck = Arb.constant(dataConnectAppCheck))
        .next()
    val requestId = Arb.dataConnect.requestId().next()
    val callerSdkType = Arb.enum<CallerSdkType>().next()

    val metadata = dataConnectGrpcMetadata.get(requestId, callerSdkType)

    metadata.asClue {
      it.keys() shouldContain "x-firebase-appcheck"
      val metadataKey = Metadata.Key.of("x-firebase-appcheck", Metadata.ASCII_STRING_MARSHALLER)
      it.get(metadataKey) shouldBe appCheckTokenResult.token
    }
  }

  @Test
  fun `forSystemVersions() should return correct values`() = runTest {
    val dataConnectAuth: DataConnectAuth = mockk()
    val dataConnectAppCheck: DataConnectAppCheck = mockk()
    val connectorLocation = Arb.dataConnect.connectorLocation().next()

    val dataConnectGrpcMetadata =
      DataConnectGrpcMetadata.forSystemVersions(
        firebaseApp = firebaseAppFactory.newInstance(),
        dataConnectAuth = dataConnectAuth,
        dataConnectAppCheck = dataConnectAppCheck,
        connectorLocation = connectorLocation,
        parentLogger = mockk(relaxed = true),
      )

    dataConnectGrpcMetadata.asClue {
      it.dataConnectAuth shouldBeSameInstanceAs dataConnectAuth
      it.dataConnectAppCheck shouldBeSameInstanceAs dataConnectAppCheck
      it.connectorLocation shouldBeSameInstanceAs connectorLocation
      it.kotlinVersion shouldBe "${KotlinVersion.CURRENT}"
      it.androidVersion shouldBe Build.VERSION.SDK_INT
      it.dataConnectSdkVersion shouldBe BuildConfig.VERSION_NAME
      it.grpcVersion shouldBe ""
    }
  }
}
