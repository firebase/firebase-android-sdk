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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.connectorConfig
import com.google.firebase.dataconnect.testutil.containsMatchWithNonAdjacentText
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import com.google.firebase.dataconnect.testutil.randomHost
import com.google.firebase.dataconnect.testutil.randomOperationName
import com.google.firebase.dataconnect.testutil.randomProjectId
import com.google.firebase.dataconnect.testutil.requestId
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.grpc.Metadata
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.regex.Pattern
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectGrpcClientUnitTest {

  private val executeQueryMetadataSlot = slot<Metadata>()
  private val executeMutationMetadataSlot = slot<Metadata>()

  private val mockDataConnectGrpcRPCs =
    mockk<DataConnectGrpcRPCs>(relaxed = true, name = "mockDataConnectGrpcRPCs") {
      coEvery { executeQuery(any(), capture(executeQueryMetadataSlot)) } answers
        {
          ExecuteQueryResponse.getDefaultInstance()
        }
      coEvery { executeMutation(any(), capture(executeMutationMetadataSlot)) } answers
        {
          ExecuteMutationResponse.getDefaultInstance()
        }
    }

  private val mockDataConnectGrpcRPCsFactory =
    mockk<DataConnectGrpcRPCsFactory>(relaxed = true, name = "mockDataConnectGrpcRPCsFactory") {
      every { host } returns (randomHost("bety6t2y5z"))
      every { sslEnabled } returns (Random.nextBoolean())
      every { newInstance() } returns (mockDataConnectGrpcRPCs)
    }

  private val dataConnectGrpcClient =
    DataConnectGrpcClient(
      projectId = randomProjectId("nywm75x5xm"),
      connector = Arb.connectorConfig("w3v2443737").next(),
      dataConnectAuth = mockk<DataConnectAuth>(relaxed = true, name = "mockDataConnectAuth"),
      grpcRPCsFactory = mockDataConnectGrpcRPCsFactory,
      parentLogger = mockk<Logger>(relaxed = true, name = "mockLogger"),
    )

  @Test
  fun `executeQuery() should include x-goog-api-client grpc header`() = runTest {
    dataConnectGrpcClient.executeQuery(
      requestId = Arb.requestId("dx6ecw35r7").next(),
      operationName = randomOperationName("ranwgj8ys6"),
      variables = Struct.getDefaultInstance()
    )

    executeQueryMetadataSlot.captured.verifyGoogApiClientHeader()
  }

  @Test
  fun `executeMutation() should include x-goog-api-client grpc header`() = runTest {
    dataConnectGrpcClient.executeMutation(
      requestId = Arb.requestId("wt4dsrhqdw").next(),
      operationName = randomOperationName("bhkeqsb4d7"),
      variables = Struct.getDefaultInstance()
    )

    executeMutationMetadataSlot.captured.verifyGoogApiClientHeader()
  }

  private companion object {
    @Suppress("SpellCheckingInspection")
    private val googApiClientHeader: Metadata.Key<String> =
      Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER)

    private const val versionPattern = "[\\w\\d-_.]+"

    fun Metadata.verifyGoogApiClientHeader() {
      assertThat(keys()).contains(googApiClientHeader.name())
      val values = getAll(googApiClientHeader)
      assertThat(values).hasSize(1)

      val value = values!!.single()
      assertThat(value)
        .containsMatchWithNonAdjacentText(Pattern.quote("gl-kotlin/") + versionPattern)
      assertThat(value)
        .containsMatchWithNonAdjacentText(Pattern.quote("gl-android/") + versionPattern)
      assertThat(value).containsMatchWithNonAdjacentText(Pattern.quote("fire/") + versionPattern)
      assertThat(value).containsWithNonAdjacentText("grpc/")
    }
  }
}
