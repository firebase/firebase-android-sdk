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
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcServer
import com.google.firebase.dataconnect.testutil.newInstance
import io.grpc.Metadata
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GrpcMetadataIntegrationTest : DataConnectIntegrationTestBase() {

  @get:Rule val inProcessDataConnectGrpcServer = InProcessDataConnectGrpcServer()

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

  private companion object {
    val googRequestParamsHeader: Metadata.Key<String> =
      Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)

    val googApiClientHeader: Metadata.Key<String> =
      Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)
  }
}
