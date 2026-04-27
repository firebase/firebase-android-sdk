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

import com.google.firebase.dataconnect.DataConnectOperationException
import com.google.firebase.dataconnect.DataConnectOperationFailureResponse.ErrorInfo
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.DataConnectUntypedData
import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef.FetchPolicy
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectGrpcClient.OperationResult
import com.google.firebase.dataconnect.core.DataConnectGrpcClientGlobals.deserialize
import com.google.firebase.dataconnect.core.DataConnectOperationFailureResponseImpl.ErrorInfoImpl
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.TwoValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.appCheckTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.authTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.iterator
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationErrors
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldHaveLoggedExactlyOneMessageContaining
import com.google.firebase.dataconnect.testutil.shouldSatisfy
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toMap
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.GraphqlError
import google.firebase.dataconnect.proto.SourceLocation
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val propTestConfig =
  PropTestConfig(iterations = 20, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25))

class DataConnectGrpcClientUnitTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val randomSeedTestRule = RandomSeedTestRule()

  private val rs: RandomSource by randomSeedTestRule.rs
  private val projectId = Arb.dataConnect.projectId().next(rs)
  private val connectorConfig = Arb.dataConnect.connectorConfig().next(rs)
  private val requestId = Arb.dataConnect.requestId().next(rs)
  private val operationName = Arb.dataConnect.operationName().next(rs)
  private val variables = Arb.proto.struct().next(rs).struct
  private val callerSdkType = Arb.enum<CallerSdkType>().next(rs)
  private val fetchPolicy = Arb.enum<FetchPolicy>().next(rs)

  private val mockDataConnectAuth: DataConnectAuth =
    mockk(relaxed = true, name = "mockDataConnectAuth-zfbhma6tyh") {
      coEvery { getToken(any()) } returns null
    }
  private val mockDataConnectAppCheck: DataConnectAppCheck =
    mockk(relaxed = true, name = "mockDataConnectAppCheck-zfbhma6tyh") {
      coEvery { getToken(any()) } returns null
    }

  private val mockDataConnectGrpcRPCs: DataConnectGrpcRPCs =
    mockk(relaxed = true, name = "mockDataConnectGrpcRPCs-zfbhma6tyh") {
      coEvery { executeQuery(any(), any(), any(), any(), any(), any()) } returns
        DataConnectGrpcRPCs.ExecuteQueryResult.FromServer(ExecuteQueryResponse.getDefaultInstance())
      coEvery { executeMutation(any(), any(), any(), any(), any()) } returns
        ExecuteMutationResponse.getDefaultInstance()
    }

  private val mockLogger = newMockLogger("tmrrzrtqke")

  private val dataConnectGrpcClient =
    DataConnectGrpcClient(
      projectId = projectId,
      connector = connectorConfig,
      grpcRPCs = mockDataConnectGrpcRPCs,
      dataConnectAuth = mockDataConnectAuth,
      dataConnectAppCheck = mockDataConnectAppCheck,
      logger = mockLogger,
    )

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `executeQuery() should forward requestId, callerSdkType, and fetchPolicy`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.string(),
      Arb.enum<CallerSdkType>(),
      Arb.enum<FetchPolicy>()
    ) { requestId, callerSdkType, fetchPolicy ->
      dataConnectGrpcClient.executeQuery(
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy
      )
      coVerify {
        mockDataConnectGrpcRPCs.executeQuery(
          requestId,
          any(),
          callerSdkType,
          fetchPolicy,
          any(),
          any()
        )
      }
    }
  }

  @Test
  fun `executeQuery() should send the right ExecuteQueryRequest`() = runTest {
    checkAll(propTestConfig, Exhaustive.enum<FetchPolicy>()) {
      dataConnectGrpcClient.executeQuery(
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy
      )

      val expectedConnectorResourceName =
        "projects/${projectId}" +
          "/locations/${connectorConfig.location}" +
          "/services/${connectorConfig.serviceId}" +
          "/connectors/${connectorConfig.connector}"
      val expectedRequest =
        ExecuteQueryRequest.newBuilder()
          .setName(expectedConnectorResourceName)
          .setOperationName(operationName)
          .setVariables(variables)
          .build()
      coVerify {
        mockDataConnectGrpcRPCs.executeQuery(any(), expectedRequest, any(), any(), any(), any())
      }
    }
  }

  @Test
  fun `executeMutation() should forward requestId and callerSdkType`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.enum<CallerSdkType>()) {
      requestId,
      callerSdkType ->
      dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

      coVerify {
        mockDataConnectGrpcRPCs.executeMutation(requestId, any(), callerSdkType, any(), any())
      }
    }
  }

  @Test
  fun `executeMutation() should send the right ExecuteMutationRequest`() = runTest {
    dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

    val expectedConnectorResourceName =
      "projects/${projectId}" +
        "/locations/${connectorConfig.location}" +
        "/services/${connectorConfig.serviceId}" +
        "/connectors/${connectorConfig.connector}"
    val expectedRequest =
      ExecuteMutationRequest.newBuilder()
        .setName(expectedConnectorResourceName)
        .setOperationName(operationName)
        .setVariables(variables)
        .build()
    coVerify {
      mockDataConnectGrpcRPCs.executeMutation(any(), expectedRequest, any(), any(), any())
    }
  }

  @Test
  fun `executeQuery() should return data and empty errors if response is from cache`() = runTest {
    val responseData = Arb.proto.struct().next(rs).struct
    coEvery {
      mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any())
    } returns DataConnectGrpcRPCs.ExecuteQueryResult.FromCache(responseData)

    val operationResult =
      dataConnectGrpcClient.executeQuery(
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy
      )

    operationResult shouldBe
      OperationResult(data = responseData, errors = emptyList(), DataSource.CACHE)
  }

  @Test
  fun `executeQuery() should return null data and empty errors if response is empty`() = runTest {
    coEvery {
      mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any())
    } returns
      DataConnectGrpcRPCs.ExecuteQueryResult.FromServer(ExecuteQueryResponse.getDefaultInstance())

    val operationResult =
      dataConnectGrpcClient.executeQuery(
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy
      )

    operationResult shouldBe OperationResult(data = null, errors = emptyList(), DataSource.SERVER)
  }

  @Test
  fun `executeMutation() should return null data and empty errors if response is empty`() =
    runTest {
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any()) } returns
        ExecuteMutationResponse.getDefaultInstance()

      val operationResult =
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

      operationResult shouldBe OperationResult(data = null, errors = emptyList(), DataSource.SERVER)
    }

  @Test
  fun `executeQuery() should return data and errors`() = runTest {
    val responseData = Arb.proto.struct().next(rs).struct
    val responseErrors = List(3) { GraphqlErrorInfo.random(RandomSource.default()) }
    coEvery {
      mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any())
    } returns
      DataConnectGrpcRPCs.ExecuteQueryResult.FromServer(
        ExecuteQueryResponse.newBuilder()
          .setData(responseData)
          .addAllErrors(responseErrors.map { it.graphqlError })
          .build()
      )

    val operationResult =
      dataConnectGrpcClient.executeQuery(
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy
      )

    operationResult shouldBe
      OperationResult(
        data = responseData,
        errors = responseErrors.map { it.errorInfo },
        DataSource.SERVER
      )
  }

  @Test
  fun `executeMutation() should return data and errors`() = runTest {
    val responseData = Arb.proto.struct().next(rs).struct
    val responseErrors = List(3) { GraphqlErrorInfo.random(RandomSource.default()) }
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any()) } returns
      ExecuteMutationResponse.newBuilder()
        .setData(responseData)
        .addAllErrors(responseErrors.map { it.graphqlError })
        .build()

    val operationResult =
      dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

    operationResult shouldBe
      OperationResult(
        data = responseData,
        errors = responseErrors.map { it.errorInfo },
        DataSource.SERVER
      )
  }

  @Test
  fun `executeQuery() should propagate non-grpc exceptions`() = runTest {
    val exception = TestException("k6hzgp7hvz")
    coEvery {
      mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any())
    } throws exception

    val thrownException =
      shouldThrow<TestException> {
        dataConnectGrpcClient.executeQuery(
          requestId,
          operationName,
          variables,
          callerSdkType,
          fetchPolicy
        )
      }

    thrownException shouldBe exception
  }

  @Test
  fun `executeMutation() should propagate non-grpc exceptions`() = runTest {
    val exception = TestException("g32376rnd3")
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any()) } throws
      exception

    val thrownException =
      shouldThrow<TestException> {
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
      }

    thrownException shouldBe exception
  }

  @Test
  fun `executeQuery() should retry with fresh auth and app check tokens on UNAUTHENTICATED`() =
    runTest {
      val authTokens = authTokenPairArb().next(rs)
      mockDataConnectAuth.stubGetTokensSimulatingForceRefresh(authTokens)
      val appCheckTokens = appCheckTokenPairArb().next(rs)
      mockDataConnectAppCheck.stubGetTokensSimulatingForceRefresh(appCheckTokens)
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(
          any(),
          any(),
          any(),
          any(),
          matchNullable { it == authTokens.value1 },
          matchNullable { it == appCheckTokens.value1 },
        )
      } throws
        StatusException(
          Status.UNAUTHENTICATED.withDescription("status exception ${Random.nextInt()} bjh9zc6h5n")
        )
      val responseData = Arb.proto.struct().next(rs).struct
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(
          any(),
          any(),
          any(),
          any(),
          matchNullable { it == authTokens.value2 },
          matchNullable { it == appCheckTokens.value2 },
        )
      } returns
        DataConnectGrpcRPCs.ExecuteQueryResult.FromServer(
          ExecuteQueryResponse.newBuilder().setData(responseData).build()
        )

      val result =
        dataConnectGrpcClient.executeQuery(
          requestId,
          operationName,
          variables,
          callerSdkType,
          fetchPolicy
        )

      result shouldBe OperationResult(data = responseData, errors = emptyList(), DataSource.SERVER)
      coVerify(exactly = 2) {
        mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any())
      }
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
        "retrying with fresh Auth and/or AppCheck tokens"
      )
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
    }

  @Test
  fun `executeMutation() should retry with fresh auth and app check tokens on UNAUTHENTICATED`() =
    runTest {
      val authTokens = authTokenPairArb().next(rs)
      mockDataConnectAuth.stubGetTokensSimulatingForceRefresh(authTokens)
      val appCheckTokens = appCheckTokenPairArb().next(rs)
      mockDataConnectAppCheck.stubGetTokensSimulatingForceRefresh(appCheckTokens)
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(
          any(),
          any(),
          any(),
          matchNullable { it == authTokens.value1 },
          matchNullable { it == appCheckTokens.value1 },
        )
      } throws
        StatusException(
          Status.UNAUTHENTICATED.withDescription("status exception ${Random.nextInt()} m8gzej7pmy")
        )
      val responseData = Arb.proto.struct().next(rs).struct
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(
          any(),
          any(),
          any(),
          matchNullable { it == authTokens.value2 },
          matchNullable { it == appCheckTokens.value2 },
        )
      } returns ExecuteMutationResponse.newBuilder().setData(responseData).build()

      val result =
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

      result shouldBe OperationResult(data = responseData, errors = emptyList(), DataSource.SERVER)
      coVerify(exactly = 2) {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any())
      }
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
        "retrying with fresh Auth and/or AppCheck tokens"
      )
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
    }

  @Test
  fun `executeQuery() should NOT retry on error status other than UNAUTHENTICATED`() = runTest {
    val exception = StatusException(Status.INTERNAL)
    coEvery {
      mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any())
    } throws exception

    val thrownException =
      shouldThrow<StatusException> {
        dataConnectGrpcClient.executeQuery(
          requestId,
          operationName,
          variables,
          callerSdkType,
          fetchPolicy
        )
      }

    thrownException shouldBeSameInstanceAs exception
    coVerify(exactly = 0) { mockDataConnectAuth.forceRefresh() }
    coVerify(exactly = 0) { mockDataConnectAppCheck.forceRefresh() }
  }

  @Test
  fun `executeMutation() should NOT retry on error status other than UNAUTHENTICATED`() = runTest {
    val exception = StatusException(Status.INTERNAL)
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any()) } throws
      exception

    val thrownException =
      shouldThrow<StatusException> {
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
      }

    thrownException shouldBeSameInstanceAs exception
    coVerify(exactly = 0) { mockDataConnectAuth.forceRefresh() }
    coVerify(exactly = 0) { mockDataConnectAppCheck.forceRefresh() }
  }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry also fails with UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.UNAUTHENTICATED)
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any())
      } throwsMany (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeQuery(
            requestId,
            operationName,
            variables,
            callerSdkType,
            fetchPolicy
          )
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry also fails with UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.UNAUTHENTICATED)
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any())
      } throwsMany (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry fails with a code other than UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.ABORTED)
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any())
      } throwsMany (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeQuery(
            requestId,
            operationName,
            variables,
            callerSdkType,
            fetchPolicy
          )
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with a code other than UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.ABORTED)
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any())
      } throwsMany (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry fails with some other exception`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = TestException("eysrmxmxk7")
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any())
      } throwsMany (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<TestException> {
          dataConnectGrpcClient.executeQuery(
            requestId,
            operationName,
            variables,
            callerSdkType,
            fetchPolicy
          )
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with some other exception`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = TestException("qz2ykb8wa2")
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any())
      } throwsMany (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<TestException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  private class TestException(message: String) : Exception(message)

  private data class GraphqlErrorInfo(
    val graphqlError: GraphqlError,
    val errorInfo: ErrorInfoImpl,
  ) {
    companion object {
      private val randomPathSegments =
        Arb.string(
            minSize = 1,
            maxSize = 8,
            codepoints = Codepoint.alphanumeric().merge(Codepoint.egyptianHieroglyphs()),
          )
          .iterator(edgeCaseProbability = 0.33f)

      private val randomMessages =
        Arb.string(minSize = 1, maxSize = 100).iterator(edgeCaseProbability = 0.33f)

      private val randomInts = Arb.int().iterator(edgeCaseProbability = 0.2f)

      fun random(rs: RandomSource): GraphqlErrorInfo {

        val dataConnectErrorPath = mutableListOf<DataConnectPathSegment>()
        val graphqlErrorPath = ListValue.newBuilder()
        repeat(6) {
          if (rs.random.nextFloat() < 0.33f) {
            val pathSegment = randomInts.next(rs)
            dataConnectErrorPath.add(DataConnectPathSegment.ListIndex(pathSegment))
            graphqlErrorPath.addValues(Value.newBuilder().setNumberValue(pathSegment.toDouble()))
          } else {
            val pathSegment = randomPathSegments.next(rs)
            dataConnectErrorPath.add(DataConnectPathSegment.Field(pathSegment))
            graphqlErrorPath.addValues(Value.newBuilder().setStringValue(pathSegment))
          }
        }

        val graphqlErrorLocations = mutableListOf<SourceLocation>()
        repeat(3) {
          val line = randomInts.next(rs)
          val column = randomInts.next(rs)
          graphqlErrorLocations.add(
            SourceLocation.newBuilder().setLine(line).setColumn(column).build()
          )
        }

        val message = randomMessages.next(rs)
        val graphqlError =
          GraphqlError.newBuilder()
            .apply {
              setMessage(message)
              setPath(graphqlErrorPath)
              addAllLocations(graphqlErrorLocations)
            }
            .build()

        val errorInfo =
          ErrorInfoImpl(
            message = message,
            path = dataConnectErrorPath.toList(),
          )

        return GraphqlErrorInfo(graphqlError, errorInfo)
      }
    }
  }
}

@Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_AGAINST_NOT_NOTHING_EXPECTED_TYPE")
class DataConnectGrpcClientOperationResultUnitTest {

  @Test
  fun `deserialize() should ignore the module given with DataConnectUntypedData`() {
    val data = buildStructProto { put("foo", 42.0) }
    val errors = Arb.dataConnect.operationErrors().next()
    val operationResult = OperationResult(data, errors, DataSource.SERVER)
    val result = operationResult.deserialize(DataConnectUntypedData, mockk<SerializersModule>())
    result.shouldHaveDataAndErrors(data, errors)
  }

  @Test
  fun `deserialize() with null data should treat DataConnectUntypedData specially`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrors()) { errors ->
      val operationResult = OperationResult(null, errors, DataSource.SERVER)
      val result = operationResult.deserialize(DataConnectUntypedData, serializersModule = null)
      result.shouldHaveDataAndErrors(null, errors)
    }
  }

  @Test
  fun `deserialize() with non-null data should treat DataConnectUntypedData specially`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.dataConnect.operationErrors()) { data, errors
      ->
      val operationResult = OperationResult(data.struct, errors, DataSource.SERVER)
      val result = operationResult.deserialize(DataConnectUntypedData, serializersModule = null)
      result.shouldHaveDataAndErrors(data.struct, errors)
    }
  }

  @Test
  fun `deserialize() successfully deserializes`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { fooValue ->
      val dataStruct = buildStructProto { put("foo", fooValue) }
      val operationResult = OperationResult(dataStruct, emptyList(), DataSource.SERVER)

      val deserializedData = operationResult.deserialize(serializer<TestData>(), null)

      deserializedData shouldBe TestData(fooValue)
    }
  }

  @Test
  fun `deserialize() should throw if one or more errors and data is null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrors(range = 1..10)) { errors ->
      val operationResult = OperationResult(null, errors, DataSource.SERVER)
      val exception: DataConnectOperationException =
        shouldThrow<DataConnectOperationException> {
          operationResult.deserialize<Nothing>(mockk(), serializersModule = null)
        }
      exception.shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = "operation encountered errors",
        expectedMessageSubstringCaseSensitive = errors.toString(),
        expectedCause = null,
        expectedRawData = null,
        expectedData = null,
        expectedErrors = errors,
      )
    }
  }

  @Test
  fun `deserialize() should throw if one or more errors, data is NOT null, and decoding fails`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.proto.struct().map { it.struct },
        Arb.dataConnect.operationErrors(range = 1..10)
      ) { dataStruct, errors ->
        val operationResult = OperationResult(dataStruct, errors, DataSource.SERVER)
        val exception: DataConnectOperationException =
          shouldThrow<DataConnectOperationException> {
            operationResult.deserialize<Nothing>(mockk(), serializersModule = null)
          }
        exception.shouldSatisfy(
          expectedMessageSubstringCaseInsensitive = "operation encountered errors",
          expectedMessageSubstringCaseSensitive = errors.toString(),
          expectedCause = null,
          expectedRawData = dataStruct,
          expectedData = null,
          expectedErrors = errors,
        )
      }
    }

  @Test
  fun `deserialize() should throw if one or more errors, data is NOT null, and decoding succeeds`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.string(),
        Arb.dataConnect.operationErrors(range = 1..10)
      ) { fooValue, errors ->
        val dataStruct = buildStructProto { put("foo", fooValue) }
        val operationResult = OperationResult(dataStruct, errors, DataSource.SERVER)
        val exception: DataConnectOperationException =
          shouldThrow<DataConnectOperationException> {
            operationResult.deserialize(serializer<TestData>(), serializersModule = null)
          }
        exception.shouldSatisfy(
          expectedMessageSubstringCaseInsensitive = "operation encountered errors",
          expectedMessageSubstringCaseSensitive = errors.toString(),
          expectedCause = null,
          expectedRawData = dataStruct,
          expectedData = TestData(fooValue),
          expectedErrors = errors,
        )
      }
    }

  @Test
  fun `deserialize() should throw if data is null and errors is empty`() {
    val operationResult = OperationResult(null, emptyList(), DataSource.SERVER)
    val exception: DataConnectOperationException =
      shouldThrow<DataConnectOperationException> {
        operationResult.deserialize(serializer<TestData>(), serializersModule = null)
      }
    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "no data was included",
      expectedCause = null,
      expectedRawData = null,
      expectedData = null,
      expectedErrors = emptyList(),
    )
  }

  @Test
  fun `deserialize() should throw if decoding fails and error list is empty`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }) { dataStruct ->
      assume(!dataStruct.containsFields("foo"))
      val operationResult = OperationResult(dataStruct, emptyList(), DataSource.SERVER)
      val exception: DataConnectOperationException =
        shouldThrow<DataConnectOperationException> {
          operationResult.deserialize(serializer<TestData>(), serializersModule = null)
        }
      exception.shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = "decoding data from the server's response failed",
        expectedCause = SerializationException::class,
        expectedRawData = dataStruct,
        expectedData = null,
        expectedErrors = emptyList(),
      )
    }
  }

  @Test
  fun `deserialize() should pass through the SerializersModule`() {
    val data = encodeToStruct(TestData("4jv7vkrs7a"))
    val serializersModule: SerializersModule = mockk()
    val operationResult = OperationResult(data = data, errors = emptyList(), DataSource.SERVER)
    val deserializer: DeserializationStrategy<TestData> = spyk(serializer())

    operationResult.deserialize(deserializer, serializersModule)

    val slot = slot<Decoder>()
    verify { deserializer.deserialize(capture(slot)) }
    slot.captured.serializersModule shouldBeSameInstanceAs serializersModule
  }

  @Serializable data class TestData(val foo: String)

  private companion object {

    fun <T> DataConnectOperationException.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive: String,
      expectedMessageSubstringCaseSensitive: String? = null,
      expectedCause: KClass<*>?,
      expectedRawData: Struct?,
      expectedData: T?,
      expectedErrors: List<ErrorInfo>,
    ) =
      shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = expectedMessageSubstringCaseInsensitive,
        expectedMessageSubstringCaseSensitive = expectedMessageSubstringCaseSensitive,
        expectedCause = expectedCause,
        expectedRawData = expectedRawData?.toMap(),
        expectedData = expectedData,
        expectedErrors = expectedErrors,
      )

    fun DataConnectUntypedData.shouldHaveDataAndErrors(
      expectedData: Map<String, Any?>,
      expectedErrors: List<ErrorInfo>,
    ) {
      assertSoftly {
        withClue("data") { data.shouldNotBeNull().shouldContainExactly(expectedData) }
        withClue("errors") { errors shouldContainExactly expectedErrors }
      }
    }

    fun DataConnectUntypedData.shouldHaveDataAndErrors(
      expectedData: Struct,
      expectedErrors: List<ErrorInfo>,
    ) = shouldHaveDataAndErrors(expectedData.toMap(), expectedErrors)

    fun DataConnectUntypedData.shouldHaveDataAndErrors(
      @Suppress("UNUSED_PARAMETER") expectedData: Nothing?,
      expectedErrors: List<ErrorInfo>,
    ) {
      assertSoftly {
        withClue("data") { data.shouldBeNull() }
        withClue("errors") { errors shouldContainExactly expectedErrors }
      }
    }
  }
}

private fun DataConnectAuth.stubGetTokensSimulatingForceRefresh(
  tokens: Iterable<GetAuthTokenResult?>
) {
  val tokenIterator = tokens.iterator()
  val currentToken = AtomicReference(tokenIterator.next())
  coEvery { getToken(any()) } answers { currentToken.get() }
  coEvery { forceRefresh() } answers { currentToken.set(tokenIterator.next()) }
}

private fun DataConnectAppCheck.stubGetTokensSimulatingForceRefresh(
  tokens: Iterable<GetAppCheckTokenResult?>
) {
  val tokenIterator = tokens.iterator()
  val currentToken = AtomicReference(tokenIterator.next())
  coEvery { getToken(any()) } answers { currentToken.get() }
  coEvery { forceRefresh() } answers { currentToken.set(tokenIterator.next()) }
}

private fun authTokenPairArb(): Arb<TwoValues<GetAuthTokenResult?>> =
  Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.33).pair()

private fun appCheckTokenPairArb(): Arb<TwoValues<GetAppCheckTokenResult?>> =
  Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.33).pair()
