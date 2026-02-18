/*
 * Copyright 2026 Google LLC
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

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs.CacheSettings
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs.ExecuteQueryResult
import com.google.firebase.dataconnect.sqlite.QueryResultArb
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.ProtoArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectGrpcMetadata
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.shouldBe
import google.firebase.dataconnect.proto.ConnectorServiceGrpc
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.grpc.InsecureServerCredentials
import io.grpc.Server
import io.grpc.okhttp.OkHttpServerBuilder
import io.grpc.stub.StreamObserver
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DataConnectGrpcRPCsClientUnitTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val cleanupsRule = CleanupsRule()
  @get:Rule val temporaryFolder = TemporaryFolder()

  private val mockLogger = newMockLogger("s3nx74epqj")
  private val requestIdArb = Arb.dataConnect.requestId()
  private val executeQueryRequestArb = executeQueryRequestArb()
  private val callerSdkTypeArb = Arb.enum<CallerSdkType>()
  private val grpcMetadataArb = Arb.dataConnect.dataConnectGrpcMetadata()

  @Test
  fun `executeQuery() returns query results from cache`() = runTest {
    checkAll(propTestConfig, QueryResultArb(entityCountRange = 0..5)) { sample ->
      val response =
        ExecuteQueryResponse.newBuilder().let { responseBuilder ->
          responseBuilder.setData(sample.hydratedStruct)
          responseBuilder.build()
        }
      startServer(response).use { server ->
        val dataConnectGrpcRPCs = newDataConnectGrpcRPCs(server)
        val request = executeQueryRequestArb.bind()

        val result1 =
          dataConnectGrpcRPCs.executeQuery(
            requestIdArb.bind(),
            request,
            callerSdkTypeArb.bind(),
          )
        val result2 =
          dataConnectGrpcRPCs.executeQuery(
            requestIdArb.bind(),
            request,
            callerSdkTypeArb.bind(),
          )

        withClue("result1") {
          val response1 = result1.shouldBeInstanceOf<ExecuteQueryResult.FromServer>().response
          response1 shouldBe response
        }
        withClue("result2") {
          val response2 = result2.shouldBeInstanceOf<ExecuteQueryResult.FromCache>().data
          response2 shouldBe sample.hydratedStruct
        }
        withClue("executeQueryInvocationCount") { server.executeQueryInvocationCount shouldBe 1 }
      }
    }
  }

  private fun newDbFile() = File(temporaryFolder.newFolder(), "db.sqlite")

  private class StartServerResult(
    private val grpcServer: Server,
    private val connectorServiceImpl: ConnectorServiceImpl
  ) : AutoCloseable {

    val port = grpcServer.port

    val executeQueryInvocationCount: Int
      get() = connectorServiceImpl.executeQueryInvocationCount.get()

    override fun close() {
      grpcServer.shutdown()
    }
  }

  private fun startServer(response: ExecuteQueryResponse): StartServerResult {
    val connectorServiceImpl = ConnectorServiceImpl(response)
    val grpcServer =
      OkHttpServerBuilder.forPort(0, InsecureServerCredentials.create())
        .addService(connectorServiceImpl)
        .build()

    grpcServer.start()

    cleanupsRule.register { grpcServer.shutdown() }

    return StartServerResult(grpcServer, connectorServiceImpl)
  }

  private class ConnectorServiceImpl(private val response: ExecuteQueryResponse) :
    ConnectorServiceGrpc.ConnectorServiceImplBase() {

    val executeQueryInvocationCount = AtomicInteger(0)

    override fun executeQuery(
      request: ExecuteQueryRequest,
      responseObserver: StreamObserver<ExecuteQueryResponse>,
    ) {
      executeQueryInvocationCount.incrementAndGet()
      responseObserver.onNext(response)
      responseObserver.onCompleted()
    }
  }

  private fun PropertyContext.newDataConnectGrpcRPCs(server: StartServerResult) =
    DataConnectGrpcRPCs(
      context = RuntimeEnvironment.getApplication(),
      host = "localhost:${server.port}",
      sslEnabled = false,
      blockingCoroutineDispatcher = Dispatchers.IO,
      grpcMetadata = grpcMetadataArb.bind(),
      cacheSettings = CacheSettings(newDbFile()),
      parentLogger = mockLogger,
    )
}

private val propTestConfig =
  PropTestConfig(iterations = 50, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private fun executeQueryRequestArb(
  operationName: Arb<String> = Arb.dataConnect.operationName(),
  variables: Arb<ProtoArb.StructInfo> = Arb.proto.struct(),
): Arb<ExecuteQueryRequest> =
  Arb.bind(operationName, variables) { operationName, variables ->
    ExecuteQueryRequest.newBuilder()
      .setOperationName(operationName)
      .setVariables(variables.struct)
      .build()
  }
