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
@file:OptIn(ExperimentalKotest::class, DelicateKotest::class)

package com.google.firebase.dataconnect.core

import app.cash.turbine.test
import com.google.firebase.dataconnect.CachedDataNotFoundException
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef.FetchPolicy
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs.CacheSettings
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs.ExecuteQueryResult
import com.google.firebase.dataconnect.sqlite.QueryResultArb
import com.google.firebase.dataconnect.sqlite.QueryResultArb.EntityRepeatPolicy.INTER_SAMPLE_MUTATED
import com.google.firebase.dataconnect.sqlite.hydratedStructWithMutatedEntityValuesFrom
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.DataConnectPath
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcStreamingServer
import com.google.firebase.dataconnect.testutil.OperationNameVariablesPair
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.awaitUntilInitStreamRequest
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.ProtoArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.appCheckTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.authTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectGrpcMetadata
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.quadruple
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue as ListValueProto
import com.google.protobuf.Struct as StructProto
import google.firebase.dataconnect.proto.ConnectorServiceGrpc
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.GraphqlResponseExtensions
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties
import google.firebase.dataconnect.proto.StreamRequest
import io.grpc.InsecureServerCredentials
import io.grpc.Server
import io.grpc.okhttp.OkHttpServerBuilder
import io.grpc.stub.StreamObserver
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DataConnectGrpcRPCsUnitTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val temporaryFolder = TemporaryFolder()
  @get:Rule val randomSeedTestRule = RandomSeedTestRule()
  @get:Rule val cleanups = CleanupsRule()

  private val rs: RandomSource by randomSeedTestRule.rs

  private val mockLogger = newMockLogger("s3nx74epqj")
  private val requestIdArb = Arb.dataConnect.requestId()
  private val streamIdArb = Arb.dataConnect.streamId()
  private val connectorResourceNameArb = Arb.dataConnect.connectorResourceName()
  private val operationNameVariablesPairArb = operationNameVariablesPairArb()
  private val callerSdkTypeArb = Arb.enum<CallerSdkType>()
  private val grpcMetadataArb = Arb.dataConnect.dataConnectGrpcMetadata()

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `REMINDER - add tests for various maxAge values, expired, not expired, 0, exact`() {
    assumeTrue("REMINDER - add tests for various maxAge values", false)
  }

  @Test
  fun `executeQuery(fetchPolicy=SERVER_ONLY) unconditionally returns results from server`() =
    runTest {
      val fetchPolicy1Arb = Arb.of(FetchPolicy.PREFER_CACHE, FetchPolicy.SERVER_ONLY)
      checkAll(
        propTestConfig,
        QueryResultArb(entityCountRange = 0..5).pair(),
        fetchPolicy1Arb,
        Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.3),
        Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.3),
      ) { (sample1, sample2), fetchPolicy1, authToken, appCheckToken ->
        val response1 = sample1.hydratedStruct.toExecuteQueryResponse()
        val response2 = sample2.hydratedStruct.toExecuteQueryResponse()

        startServer().use { server ->
          val dataConnectGrpcRPCs = newDataConnectGrpcRPCs(server)
          val request = operationNameVariablesPairArb.bind()

          server.nextResponse = response1
          dataConnectGrpcRPCs.executeQuery(
            requestIdArb.bind(),
            request.operationName,
            request.variables,
            callerSdkTypeArb.bind(),
            fetchPolicy1,
            authToken,
            appCheckToken,
          )
          server.nextResponse = response2
          val result2 =
            dataConnectGrpcRPCs.executeQuery(
              requestIdArb.bind(),
              request.operationName,
              request.variables,
              callerSdkTypeArb.bind(),
              FetchPolicy.SERVER_ONLY,
              authToken,
              appCheckToken,
            )

          result2.shouldBeInstanceOf<ExecuteQueryResult.FromServer>().response shouldBe response2
          withClue("executeQueryInvocationCount") { server.executeQueryInvocationCount shouldBe 2 }
        }
      }
    }

  @Test
  fun `executeQuery(fetchPolicy=SERVER_ONLY) should update cached entities`() = runTest {
    val fetchPolicy1Arb = Arb.of(FetchPolicy.PREFER_CACHE, FetchPolicy.SERVER_ONLY)
    val fetchPolicy2Arb = Arb.of(FetchPolicy.PREFER_CACHE, FetchPolicy.CACHE_ONLY)
    val fetchPoliciesArb = Arb.pair(fetchPolicy1Arb, fetchPolicy2Arb)
    checkAll(
      propTestConfig,
      fetchPoliciesArb,
      Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.3),
      Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.3),
    ) { (fetchPolicy1, fetchPolicy2), authToken, appCheckToken ->
      val (sample1, sample2) =
        QueryResultArb(entityCountRange = 0..5, entityRepeatPolicy = INTER_SAMPLE_MUTATED)
          .pair()
          .bind()
      val (request1, request2) = operationNameVariablesPairArb.distinctPair().bind()

      startServer().use { server ->
        val dataConnectGrpcRPCs = newDataConnectGrpcRPCs(server)

        server.nextResponse = sample1.toExecuteQueryResponse()
        dataConnectGrpcRPCs.executeQuery(
          requestIdArb.bind(),
          request1.operationName,
          request1.variables,
          callerSdkTypeArb.bind(),
          fetchPolicy1,
          authToken,
          appCheckToken,
        )
        server.nextResponse = sample2.toExecuteQueryResponse()
        dataConnectGrpcRPCs.executeQuery(
          requestIdArb.bind(),
          request2.operationName,
          request2.variables,
          callerSdkTypeArb.bind(),
          FetchPolicy.SERVER_ONLY,
          authToken,
          appCheckToken,
        )
        val result =
          dataConnectGrpcRPCs.executeQuery(
            requestIdArb.bind(),
            request1.operationName,
            request1.variables,
            callerSdkTypeArb.bind(),
            fetchPolicy2,
            authToken,
            appCheckToken,
          )

        val expectedData = sample1.hydratedStructWithMutatedEntityValuesFrom(sample2)
        result.shouldBeInstanceOf<ExecuteQueryResult.FromCache>().data shouldBe expectedData
      }
    }
  }

  @Test
  fun `executeQuery(fetchPolicy=CACHE_ONLY) throws if no cached data`() = runTest {
    startServer().use { server ->
      val dataConnectGrpcRPCs = newDataConnectGrpcRPCs(server)
      val request = operationNameVariablesPairArb.next(rs)

      val exception =
        shouldThrow<CachedDataNotFoundException> {
          dataConnectGrpcRPCs.executeQuery(
            requestIdArb.next(rs),
            request.operationName,
            request.variables,
            callerSdkTypeArb.next(rs),
            FetchPolicy.CACHE_ONLY,
            Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.3).next(rs),
            Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.3).next(rs),
          )
        }

      assertSoftly {
        withClue("executeQueryInvocationCount") { server.executeQueryInvocationCount shouldBe 0 }
        exception.message shouldContainWithNonAbuttingText "cck6p3fmd5"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "not found in the local cache"
      }
    }
  }

  @Test
  fun `executeQuery(fetchPolicy!=SERVER_ONLY) returns non-normalized query results from cache`() =
    runTest {
      val fetchPolicy1Arb = Arb.of(FetchPolicy.PREFER_CACHE, FetchPolicy.SERVER_ONLY)
      val fetchPolicy2Arb = Arb.of(FetchPolicy.entries.filterNot { it == FetchPolicy.SERVER_ONLY })
      val fetchPoliciesArb = Arb.pair(fetchPolicy1Arb, fetchPolicy2Arb)
      checkAll(
        propTestConfig,
        QueryResultArb(entityCountRange = 0..5),
        fetchPoliciesArb,
        Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.3),
        Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.3),
      ) { sample, (fetchPolicy1, fetchPolicy2), authToken, appCheckToken ->
        startServer().use { server ->
          val response = sample.hydratedStruct.toExecuteQueryResponse()
          server.nextResponse = response
          val dataConnectGrpcRPCs = newDataConnectGrpcRPCs(server)
          val request = operationNameVariablesPairArb.bind()

          val result1 =
            dataConnectGrpcRPCs.executeQuery(
              requestIdArb.bind(),
              request.operationName,
              request.variables,
              callerSdkTypeArb.bind(),
              fetchPolicy1,
              authToken,
              appCheckToken,
            )
          val result2 =
            dataConnectGrpcRPCs.executeQuery(
              requestIdArb.bind(),
              request.operationName,
              request.variables,
              callerSdkTypeArb.bind(),
              fetchPolicy2,
              authToken,
              appCheckToken,
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

  @Test
  fun `executeQuery(fetchPolicy!=SERVER_ONLY) returns normalized query results from cache`() =
    runTest {
      val fetchPolicy1Arb = Arb.of(FetchPolicy.PREFER_CACHE, FetchPolicy.SERVER_ONLY)
      val fetchPolicy2Arb = Arb.of(FetchPolicy.entries.filterNot { it == FetchPolicy.SERVER_ONLY })
      val fetchPoliciesArb =
        Arb.quadruple(fetchPolicy1Arb, fetchPolicy1Arb, fetchPolicy2Arb, fetchPolicy2Arb)
      checkAll(
        propTestConfig,
        fetchPoliciesArb,
        Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.3),
        Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.3),
      ) { (fetchPolicy1, fetchPolicy2, fetchPolicy3, fetchPolicy4), authToken, appCheckToken ->
        startServer().use { server ->
          val queryResultArb =
            QueryResultArb(entityCountRange = 0..5, entityRepeatPolicy = INTER_SAMPLE_MUTATED)
          val sample1 = queryResultArb.bind()
          val sample2 = queryResultArb.bind()
          val dataConnectGrpcRPCs = newDataConnectGrpcRPCs(server)
          val distinctExecuteQueryRequestArb = operationNameVariablesPairArb.distinct()
          val request1 = distinctExecuteQueryRequestArb.bind()
          val request2 = distinctExecuteQueryRequestArb.bind()

          server.nextResponse = sample1.toExecuteQueryResponse()
          dataConnectGrpcRPCs.executeQuery(
            requestIdArb.bind(),
            request1.operationName,
            request1.variables,
            callerSdkTypeArb.bind(),
            fetchPolicy1,
            authToken,
            appCheckToken,
          )
          server.nextResponse = sample2.toExecuteQueryResponse()
          dataConnectGrpcRPCs.executeQuery(
            requestIdArb.bind(),
            request2.operationName,
            request2.variables,
            callerSdkTypeArb.bind(),
            fetchPolicy2,
            authToken,
            appCheckToken,
          )
          val result1 =
            dataConnectGrpcRPCs.executeQuery(
              requestIdArb.bind(),
              request1.operationName,
              request1.variables,
              callerSdkTypeArb.bind(),
              fetchPolicy3,
              authToken,
              appCheckToken,
            )
          val result2 =
            dataConnectGrpcRPCs.executeQuery(
              requestIdArb.bind(),
              request2.operationName,
              request2.variables,
              callerSdkTypeArb.bind(),
              fetchPolicy4,
              authToken,
              appCheckToken,
            )

          withClue("result1") {
            val response1 = result1.shouldBeInstanceOf<ExecuteQueryResult.FromCache>().data
            response1 shouldBe sample1.hydratedStructWithMutatedEntityValuesFrom(sample2)
          }
          withClue("result2") {
            val response2 = result2.shouldBeInstanceOf<ExecuteQueryResult.FromCache>().data
            response2 shouldBe sample2.hydratedStruct
          }
        }
      }
    }

  @Test
  fun `connect() lazily sends init request on subscribe`() = runTest {
    val server = InProcessDataConnectGrpcStreamingServer()
    cleanups.register(server)
    server.open()
    val dataConnectGrpcRPCs = newDataConnectGrpcRPCs(server)

    server.events.test {
      val stream = dataConnectGrpcRPCs.connect()
      expectNoEvents()

      val subscriptionFlow = stream.subscribe("req1", "opName", StructProto.getDefaultInstance())
      expectNoEvents()

      backgroundScope.launch { subscriptionFlow.collect() }
      val streamRequest: StreamRequest = awaitUntilInitStreamRequest().streamRequest

      withClue("streamRequest=${streamRequest.print().value}") {
        withClue("requestId") { streamRequest.requestId shouldBe "init" }
        withClue("name") { streamRequest.name shouldBe dataConnectGrpcRPCs.connectorResourceName }
        withClue("requestKindCase") {
          streamRequest.requestKindCase shouldBe StreamRequest.RequestKindCase.REQUESTKIND_NOT_SET
        }
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  private suspend fun DataConnectGrpcRPCs.connect() =
    connect(
      streamId = streamIdArb.next(rs),
      callerSdkType = Arb.enum<CallerSdkType>().next(rs),
      authToken = Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.2).next(rs),
      appCheckToken = Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.2).next(rs),
      idStringGenerator = IdStringGenerator(Random.Default),
    )

  private fun newDbFile() = File(temporaryFolder.newFolder(), "db.sqlite")

  private class StartServerResult(
    private val grpcServer: Server,
    private val connectorServiceImpl: ConnectorServiceImpl
  ) : AutoCloseable {

    val port = grpcServer.port

    val executeQueryInvocationCount: Int
      get() = connectorServiceImpl.executeQueryInvocationCount.get()

    var nextResponse: ExecuteQueryResponse
      get() = connectorServiceImpl.nextResponse.get()
      set(value) {
        connectorServiceImpl.nextResponse.set(value)
      }

    override fun close() {
      grpcServer.shutdown()
    }
  }

  private fun startServer(): StartServerResult {
    val connectorServiceImpl = ConnectorServiceImpl()
    val grpcServer =
      OkHttpServerBuilder.forPort(0, InsecureServerCredentials.create())
        .addService(connectorServiceImpl)
        .build()

    grpcServer.start()

    return StartServerResult(grpcServer, connectorServiceImpl)
  }

  private class ConnectorServiceImpl : ConnectorServiceGrpc.ConnectorServiceImplBase() {

    val executeQueryInvocationCount = AtomicInteger(0)
    val nextResponse = AtomicReference(ExecuteQueryResponse.getDefaultInstance())

    override fun executeQuery(
      request: ExecuteQueryRequest,
      responseObserver: StreamObserver<ExecuteQueryResponse>,
    ) {
      executeQueryInvocationCount.incrementAndGet()
      responseObserver.onNext(nextResponse.get())
      responseObserver.onCompleted()
    }
  }

  private fun newDataConnectGrpcRPCs(server: StartServerResult): DataConnectGrpcRPCs =
    newDataConnectGrpcRPCsForLocalhostServerOnPort(server.port)

  private fun newDataConnectGrpcRPCs(
    server: InProcessDataConnectGrpcStreamingServer
  ): DataConnectGrpcRPCs = newDataConnectGrpcRPCsForLocalhostServerOnPort(server.port)

  private fun newDataConnectGrpcRPCsForLocalhostServerOnPort(port: Int) =
    DataConnectGrpcRPCs(
      context = RuntimeEnvironment.getApplication(),
      host = "localhost:$port",
      sslEnabled = false,
      connectorResourceName = connectorResourceNameArb.next(rs),
      nonBlockingCoroutineDispatcher = Dispatchers.Default,
      blockingCoroutineDispatcher = Dispatchers.IO,
      grpcMetadata = grpcMetadataArb.next(rs),
      cacheSettings = CacheSettings(newDbFile(), maxAge = 1.hours),
      parentLogger = mockLogger,
    )
}

private val propTestConfig =
  PropTestConfig(iterations = 50, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private fun operationNameVariablesPairArb(
  operationName: Arb<String> = Arb.dataConnect.operationName(),
  variables: Arb<ProtoArb.StructInfo> = Arb.proto.struct(),
): Arb<OperationNameVariablesPair<StructProto>> =
  Arb.bind(operationName, variables.map { it.struct }, ::OperationNameVariablesPair)

private fun StructProto.toExecuteQueryResponse(): ExecuteQueryResponse =
  ExecuteQueryResponse.newBuilder().setData(this@toExecuteQueryResponse).build()

private fun QueryResultArb.Sample.toExecuteQueryResponse(): ExecuteQueryResponse {
  val builder = ExecuteQueryResponse.newBuilder()
  builder.setData(hydratedStruct)
  toGraphqlResponseExtensions()?.let { builder.setExtensions(it) }
  return builder.build()
}

private fun QueryResultArb.Sample.toGraphqlResponseExtensions(): GraphqlResponseExtensions? {
  val builder = GraphqlResponseExtensions.newBuilder()

  entityByPath.entries.forEach { (path, entity) ->
    val lastSegment = path.lastOrNull()
    if (lastSegment is DataConnectPathSegment.Field) {
      builder.addDataConnect(
        DataConnectProperties.newBuilder()
          .setPath(listValueFromPath(path))
          .setEntityId(entity.entityId)
          .build()
      )
    }
  }

  entityListPaths.forEach { entityListPath ->
    val propertiesBuilder = DataConnectProperties.newBuilder()
    propertiesBuilder.setPath(listValueFromPath(entityListPath))

    var index = 0
    while (true) {
      val entityListElementPath = entityListPath.withAddedListIndex(index++)
      val entity = entityByPath[entityListElementPath] ?: break
      propertiesBuilder.addEntityIds(entity.entityId)
    }

    builder.addDataConnect(propertiesBuilder.build())
  }

  return if (builder.dataConnectCount > 0) builder.build() else null
}

private fun listValueFromPath(path: DataConnectPath): ListValueProto {
  val builder = ListValueProto.newBuilder()
  path.forEach { segment ->
    builder.addValues(
      when (segment) {
        is DataConnectPathSegment.Field -> segment.field.toValueProto()
        is DataConnectPathSegment.ListIndex -> segment.index.toValueProto()
      }
    )
  }
  return builder.build()
}
