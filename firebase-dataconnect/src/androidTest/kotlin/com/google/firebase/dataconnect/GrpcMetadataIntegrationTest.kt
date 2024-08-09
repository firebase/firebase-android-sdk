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

package com.google.firebase.dataconnect

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.dataconnect.testutil.DataConnectBackend
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.DataConnectTestFakeAppCheckProvider
import com.google.firebase.dataconnect.testutil.FirebaseAuthBackend
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcServer
import com.google.firebase.dataconnect.testutil.newInstance
import com.google.firebase.dataconnect.util.SuspendingLazy
import io.grpc.Metadata
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GrpcMetadataIntegrationTest : DataConnectIntegrationTestBase() {

  @get:Rule val inProcessDataConnectGrpcServer = InProcessDataConnectGrpcServer()

  private val authBackend: SuspendingLazy<FirebaseAuthBackend> = SuspendingLazy {
    DataConnectBackend.fromInstrumentationArguments().authBackend
  }

  @Test
  fun executeQueryShouldSendExpectedGrpcMetadata() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val queryRef = dataConnect.query("qrysp5xs5qxy8", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }

    queryRef.execute()

    verifyMetadata(metadatasJob, dataConnect)
  }

  @Test
  fun executeMutationShouldSendExpectedGrpcMetadata() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val mutationRef =
      dataConnect.mutation("mutxasxstejj9", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }

    mutationRef.execute()

    verifyMetadata(metadatasJob, dataConnect)
  }

  @Test
  fun executeQueryShouldNotSendAuthMetadataWhenNotLoggedIn() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val queryRef = dataConnect.query("qryfyk7yfppfe", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }

    queryRef.execute()

    verifyMetadataDoesNotContain(metadatasJob, firebaseAuthTokenHeader)
  }

  @Test
  fun executeMutationShouldNotSendAuthMetadataWhenNotLoggedIn() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val mutationRef =
      dataConnect.mutation("mutckjpte9v9j", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }

    mutationRef.execute()

    verifyMetadataDoesNotContain(metadatasJob, firebaseAuthTokenHeader)
  }

  @Test
  fun executeQueryShouldSendAuthMetadataWhenLoggedIn() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val queryRef = dataConnect.query("qryyarwrxe2fv", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }
    firebaseAuthSignIn(dataConnect)

    queryRef.execute()

    verifyMetadataContains(metadatasJob, firebaseAuthTokenHeader)
  }

  @Test
  fun executeMutationShouldSendAuthMetadataWhenLoggedIn() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val mutationRef =
      dataConnect.mutation("mutayn7as5k7d", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }
    firebaseAuthSignIn(dataConnect)

    mutationRef.execute()

    verifyMetadataContains(metadatasJob, firebaseAuthTokenHeader)
  }

  @Test
  fun executeQueryShouldNotSendAuthMetadataAfterLogout() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val queryRef = dataConnect.query("qryyarwrxe2fv", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob1 = async { grpcServer.metadatas.first() }
    val metadatasJob2 = async { grpcServer.metadatas.take(2).last() }
    firebaseAuthSignIn(dataConnect)
    queryRef.execute()
    verifyMetadataContains(metadatasJob1, firebaseAuthTokenHeader)
    firebaseAuthSignOut(dataConnect)

    queryRef.execute()

    verifyMetadataDoesNotContain(metadatasJob2, firebaseAuthTokenHeader)
  }

  @Test
  fun executeMutationShouldNotSendAuthMetadataAfterLogout() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val mutationRef =
      dataConnect.mutation("mutvw945ag3vv", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob1 = async { grpcServer.metadatas.first() }
    val metadatasJob2 = async { grpcServer.metadatas.take(2).last() }
    firebaseAuthSignIn(dataConnect)
    mutationRef.execute()
    verifyMetadataContains(metadatasJob1, firebaseAuthTokenHeader)
    firebaseAuthSignOut(dataConnect)

    mutationRef.execute()

    verifyMetadataDoesNotContain(metadatasJob2, firebaseAuthTokenHeader)
  }

  @Test
  fun executeQueryShouldNotSendAppCheckMetadataWhenAppCheckIsNotEnabled() = runTest {
    // TODO: Add an integration test where the AppCheck dependency is absent, and ensure that no
    // appcheck token is sent at all.
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val queryRef = dataConnect.query("qrybbeekpkkck", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }

    queryRef.execute()

    verifyMetadataContains(metadatasJob, firebaseAppCheckTokenHeader, PLACEHOLDER_APP_CHECK_TOKEN)
  }

  @Test
  fun executeMutationShouldNotSendAppCheckMetadataWhenAppCheckIsNotEnabled() = runTest {
    // TODO: Add an integration test where the AppCheck dependency is absent, and ensure that no
    // appcheck token is sent at all.
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val mutationRef =
      dataConnect.mutation("mutbs7hhxk39c", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }

    mutationRef.execute()

    verifyMetadataContains(metadatasJob, firebaseAppCheckTokenHeader, PLACEHOLDER_APP_CHECK_TOKEN)
  }

  @Test
  fun executeQueryShouldSendAppCheckMetadataWhenAppCheckIsEnabled() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val queryRef = dataConnect.query("qryyarwrxe2fv", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }
    val appCheck = FirebaseAppCheck.getInstance(dataConnect.app)
    appCheck.installAppCheckProviderFactory(DataConnectTestFakeAppCheckProvider.Factory())

    queryRef.execute()

    // TODO: Verify the actual _value_ of the AppCheck token, not just that it is not null.
    verifyMetadataContains(metadatasJob, firebaseAppCheckTokenHeader)
  }

  @Test
  fun executeMutationShouldSendAppCheckMetadataWhenAppCheckIsEnabled() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val mutationRef =
      dataConnect.mutation("mutz4hzqzpgb4", Unit, serializer<Unit>(), serializer<Unit>())
    val metadatasJob = async { grpcServer.metadatas.first() }
    val appCheck = FirebaseAppCheck.getInstance(dataConnect.app)
    appCheck.installAppCheckProviderFactory(DataConnectTestFakeAppCheckProvider.Factory())

    mutationRef.execute()

    // TODO: Verify the actual _value_ of the AppCheck token, not just that it is not null.
    verifyMetadataContains(metadatasJob, firebaseAppCheckTokenHeader)
  }

  private suspend fun verifyMetadataContains(
    job: Deferred<Metadata>,
    key: Metadata.Key<String>,
    expectedValue: String? = null
  ) {
    val metadata = withClue("waiting for metadata to be reported") { job.await() }
    metadata.asClue {
      val actualValue = metadata.get(key)
      if (expectedValue === null) {
        actualValue.shouldNotBeNull()
      } else {
        actualValue shouldBe expectedValue
      }
    }
  }

  private suspend fun verifyMetadataDoesNotContain(
    job: Deferred<Metadata>,
    key: Metadata.Key<String>
  ) {
    val metadata = withClue("waiting for metadata to be reported") { job.await() }
    metadata.asClue { metadata.get(key).shouldBeNull() }
  }

  private suspend fun verifyMetadata(job: Deferred<Metadata>, dataConnect: FirebaseDataConnect) {
    val metadata = withClue("waiting for metadata to be reported") { job.await() }
    metadata.asClue {
      metadata.keys().shouldContainAll(googRequestParamsHeader.name(), googApiClientHeader.name())
      assertSoftly {
        // Do not verify "x-firebase-auth-token" here since that header is effectively tested by
        // AuthIntegrationTest
        metadata.get(googRequestParamsHeader) shouldBe
          "location=${dataConnect.config.location}&frontend=data"
        metadata.get(googApiClientHeader) shouldBe
          ("gl-kotlin/${KotlinVersion.CURRENT}" +
            " gl-android/${Build.VERSION.SDK_INT}" +
            " fire/${BuildConfig.VERSION_NAME}" +
            " grpc/")
      }
    }
  }

  private suspend fun firebaseAuthSignIn(dataConnect: FirebaseDataConnect) {
    withClue("FirebaseAuth.signInAnonymously()") {
      val firebaseAuth = authBackend.get().getFirebaseAuth(dataConnect.app)
      firebaseAuth.signInAnonymously().await()
    }
  }

  private suspend fun firebaseAuthSignOut(dataConnect: FirebaseDataConnect) {
    withClue("FirebaseAuth.signOut()") {
      val firebaseAuth = authBackend.get().getFirebaseAuth(dataConnect.app)
      firebaseAuth.signOut()
    }
  }

  private companion object {
    const val PLACEHOLDER_APP_CHECK_TOKEN = "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="

    val firebaseAuthTokenHeader: Metadata.Key<String> =
      Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)

    val firebaseAppCheckTokenHeader: Metadata.Key<String> =
      Metadata.Key.of("x-firebase-appcheck", Metadata.ASCII_STRING_MARSHALLER)

    val googRequestParamsHeader: Metadata.Key<String> =
      Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)

    val googApiClientHeader: Metadata.Key<String> =
      Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)
  }
}
