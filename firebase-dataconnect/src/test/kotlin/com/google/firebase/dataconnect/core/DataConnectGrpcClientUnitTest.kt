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

import com.google.common.truth.Truth
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.grpc.Metadata
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class DataConnectGrpcClientUnitTest {

  @Mock(name = "mockDataConnectAuth", stubOnly = true)
  private lateinit var mockDataConnectAuth: DataConnectAuth
  @Mock(name = "mockDataConnectGrpcRPCsFactory", stubOnly = true)
  private lateinit var mockDataConnectGrpcRPCsFactory: DataConnectGrpcRPCsFactory
  @Mock(name = "mockDataConnectGrpcRPCs", stubOnly = true)
  private lateinit var mockDataConnectGrpcRPCs: DataConnectGrpcRPCs
  @Mock(name = "mockLogger", stubOnly = true) private lateinit var mockLogger: Logger
  @Captor private lateinit var metadataArgumentCaptor: ArgumentCaptor<Metadata>

  private lateinit var dataConnectGrpcClient: DataConnectGrpcClient

  @Before
  fun before() {
    MockitoAnnotations.openMocks(this).close()

    Mockito.`when`(mockDataConnectGrpcRPCsFactory.host).thenReturn(randomHost())
    Mockito.`when`(mockDataConnectGrpcRPCsFactory.sslEnabled).thenReturn(randomSslEnabled())
    Mockito.`when`(mockDataConnectGrpcRPCsFactory.newInstance()).thenReturn(mockDataConnectGrpcRPCs)

    runBlocking {
      Mockito.`when`(mockDataConnectGrpcRPCs.executeQuery(Mockito.any(), Mockito.any()))
        .thenReturn(ExecuteQueryResponse.getDefaultInstance())
      Mockito.`when`(mockDataConnectGrpcRPCs.executeMutation(Mockito.any(), Mockito.any()))
        .thenReturn(ExecuteMutationResponse.getDefaultInstance())
    }

    dataConnectGrpcClient =
      DataConnectGrpcClient(
        projectId = randomProjectId(),
        connector = randomConnectorConfig(),
        dataConnectAuth = mockDataConnectAuth,
        grpcRPCsFactory = mockDataConnectGrpcRPCsFactory,
        parentLogger = mockLogger,
      )
  }

  @Test
  fun `executeQuery() should include x-goog-api-client grpc header`() = runTest {
    dataConnectGrpcClient.executeQuery(
      requestId = randomRequestId(),
      operationName = randomOperationName(),
      variables = Struct.getDefaultInstance()
    )

    Mockito.verify(mockDataConnectGrpcRPCs)
      .executeQuery(Mockito.any(), metadataArgumentCaptor.capture())
    Truth.assertThat(metadataArgumentCaptor.value.keys().toList().contains("x-goog-api-client"))
  }

  @Test
  fun `executeMutation() should include x-goog-api-client grpc header`() = runTest {
    dataConnectGrpcClient.executeMutation(
      requestId = randomRequestId(),
      operationName = randomOperationName(),
      variables = Struct.getDefaultInstance()
    )

    Mockito.verify(mockDataConnectGrpcRPCs)
      .executeQuery(Mockito.any(), metadataArgumentCaptor.capture())
    Truth.assertThat(metadataArgumentCaptor.value.keys().toList().contains("x-goog-api-client"))
  }

  private companion object {
    fun randomHost() = "host_" + Random.nextAlphanumericString(length = 6)
    fun randomSslEnabled() = Random.nextBoolean()
    fun randomProjectId() = "projectid_" + Random.nextAlphanumericString(length = 6)
    fun randomConnector() = "connector_" + Random.nextAlphanumericString(length = 6)
    fun randomLocation() = "location_" + Random.nextAlphanumericString(length = 6)
    fun randomServiceId() = "serviceid_" + Random.nextAlphanumericString(length = 6)
    fun randomRequestId() = "requestid_" + Random.nextAlphanumericString(length = 6)
    fun randomOperationName() = "operation_" + Random.nextAlphanumericString(length = 6)
    fun randomConnectorConfig() =
      ConnectorConfig(
        connector = randomConnector(),
        location = randomLocation(),
        serviceId = randomServiceId()
      )
  }
}
