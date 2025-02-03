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

import com.google.firebase.dataconnect.DataConnectError
import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.DataConnectUntypedData
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.DataConnectGrpcClient.OperationResult
import com.google.firebase.dataconnect.core.DataConnectGrpcClientGlobals.deserialize
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectError
import com.google.firebase.dataconnect.testutil.property.arbitrary.iterator
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.shouldHaveLoggedExactlyOneMessageContaining
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toMap
import com.google.protobuf.ListValue
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.GraphqlError
import google.firebase.dataconnect.proto.SourceLocation
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.arbs.firstName
import io.kotest.property.arbs.travel.airline
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Rule
import org.junit.Test

class DataConnectGrpcClientUnitTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  private val rs = RandomSource.default()
  private val projectId = Arb.dataConnect.projectId().next(rs)
  private val connectorConfig = Arb.dataConnect.connectorConfig().next(rs)
  private val requestId = Arb.dataConnect.requestId().next(rs)
  private val operationName = Arb.dataConnect.operationName().next(rs)
  private val variables = Arb.proto.struct().next(rs)
  private val callerSdkType = Arb.enum<FirebaseDataConnect.CallerSdkType>().next(rs)

  private val mockDataConnectAuth: DataConnectAuth =
    mockk(relaxed = true, name = "mockDataConnectAuth-zfbhma6tyh")
  private val mockDataConnectAppCheck: DataConnectAppCheck =
    mockk(relaxed = true, name = "mockDataConnectAppCheck-zfbhma6tyh")

  private val mockDataConnectGrpcRPCs: DataConnectGrpcRPCs =
    mockk(relaxed = true, name = "mockDataConnectGrpcRPCs-zfbhma6tyh") {
      coEvery { executeQuery(any(), any(), any()) } returns
        ExecuteQueryResponse.getDefaultInstance()
      coEvery { executeMutation(any(), any(), any()) } returns
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

  @Test
  fun `executeQuery() should send the right request`() = runTest {
    dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)

    val expectedName =
      "projects/${projectId}" +
        "/locations/${connectorConfig.location}" +
        "/services/${connectorConfig.serviceId}" +
        "/connectors/${connectorConfig.connector}"
    val expectedRequest =
      ExecuteQueryRequest.newBuilder()
        .setName(expectedName)
        .setOperationName(operationName)
        .setVariables(variables)
        .build()
    coVerify { mockDataConnectGrpcRPCs.executeQuery(requestId, expectedRequest, callerSdkType) }
  }

  @Test
  fun `executeMutation() should send the right request`() = runTest {
    dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

    val expectedName =
      "projects/${projectId}" +
        "/locations/${connectorConfig.location}" +
        "/services/${connectorConfig.serviceId}" +
        "/connectors/${connectorConfig.connector}"
    val expectedRequest =
      ExecuteMutationRequest.newBuilder()
        .setName(expectedName)
        .setOperationName(operationName)
        .setVariables(variables)
        .build()
    coVerify { mockDataConnectGrpcRPCs.executeMutation(requestId, expectedRequest, callerSdkType) }
  }

  @Test
  fun `executeQuery() should return null data and empty errors if response is empty`() = runTest {
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) } returns
      ExecuteQueryResponse.getDefaultInstance()

    val operationResult =
      dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)

    operationResult shouldBe OperationResult(data = null, errors = emptyList())
  }

  @Test
  fun `executeMutation() should return null data and empty errors if response is empty`() =
    runTest {
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) } returns
        ExecuteMutationResponse.getDefaultInstance()

      val operationResult =
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

      operationResult shouldBe OperationResult(data = null, errors = emptyList())
    }

  @Test
  fun `executeQuery() should return data and errors`() = runTest {
    val responseData = Arb.proto.struct().next(rs)
    val responseErrors = List(3) { GraphqlErrorInfo.random(RandomSource.default()) }
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) } returns
      ExecuteQueryResponse.newBuilder()
        .setData(responseData)
        .addAllErrors(responseErrors.map { it.graphqlError })
        .build()

    val operationResult =
      dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)

    operationResult shouldBe
      OperationResult(data = responseData, errors = responseErrors.map { it.dataConnectError })
  }

  @Test
  fun `executeMutation() should return data and errors`() = runTest {
    val responseData = Arb.proto.struct().next(rs)
    val responseErrors = List(3) { GraphqlErrorInfo.random(RandomSource.default()) }
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) } returns
      ExecuteMutationResponse.newBuilder()
        .setData(responseData)
        .addAllErrors(responseErrors.map { it.graphqlError })
        .build()

    val operationResult =
      dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

    operationResult shouldBe
      OperationResult(data = responseData, errors = responseErrors.map { it.dataConnectError })
  }

  @Test
  fun `executeQuery() should propagate non-grpc exceptions`() = runTest {
    val exception = TestException("k6hzgp7hvz")
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) } throws exception

    val thrownException =
      shouldThrow<TestException> {
        dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)
      }

    thrownException shouldBe exception
  }

  @Test
  fun `executeMutation() should propagate non-grpc exceptions`() = runTest {
    val exception = TestException("g32376rnd3")
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) } throws exception

    val thrownException =
      shouldThrow<TestException> {
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
      }

    thrownException shouldBe exception
  }

  @Test
  fun `executeQuery() should retry with a fresh auth token on UNAUTHENTICATED`() = runTest {
    val responseData = Arb.proto.struct().next(rs)
    val forceRefresh = AtomicBoolean(false)
    coEvery { mockDataConnectAuth.forceRefresh() } answers { forceRefresh.set(true) }
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) } answers
      {
        if (forceRefresh.get()) {
          ExecuteQueryResponse.newBuilder().setData(responseData).build()
        } else {
          // Use a custom description to ensure that DataConnectGrpcClient is checking for just
          // the code, and not the entire equality of Status.UNAUTHENTICATED.
          throw StatusException(
            Status.UNAUTHENTICATED.withDescription(
              "this error should be ignored and result in a retry with a fresh token n2ak4cq6jr"
            )
          )
        }
      }

    val result =
      dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)

    result shouldBe OperationResult(data = responseData, errors = emptyList())
    coVerify(exactly = 2) { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
      "retrying with fresh Auth and/or AppCheck tokens"
    )
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
  }

  @Test
  fun `executeMutation() should retry with a fresh auth token on UNAUTHENTICATED`() = runTest {
    val responseData = Arb.proto.struct().next(rs)
    val forceRefresh = AtomicBoolean(false)
    coEvery { mockDataConnectAuth.forceRefresh() } answers { forceRefresh.set(true) }
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) } answers
      {
        if (forceRefresh.get()) {
          ExecuteMutationResponse.newBuilder().setData(responseData).build()
        } else {
          // Use a custom description to ensure that DataConnectGrpcClient is checking for just
          // the code, and not the entire equality of Status.UNAUTHENTICATED.
          throw StatusException(
            Status.UNAUTHENTICATED.withDescription(
              "this error should be ignored and result in a retry with a fresh token p3vmc3gs5v"
            )
          )
        }
      }

    val result =
      dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

    result shouldBe OperationResult(data = responseData, errors = emptyList())
    coVerify(exactly = 2) { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
      "retrying with fresh Auth and/or AppCheck tokens"
    )
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
  }

  @Test
  fun `executeQuery() should retry with a fresh AppCheck token on UNAUTHENTICATED`() = runTest {
    val responseData = Arb.proto.struct().next(rs)
    val forceRefresh = AtomicBoolean(false)
    coEvery { mockDataConnectAppCheck.forceRefresh() } answers { forceRefresh.set(true) }
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) } answers
      {
        if (forceRefresh.get()) {
          ExecuteQueryResponse.newBuilder().setData(responseData).build()
        } else {
          // Use a custom description to ensure that DataConnectGrpcClient is checking for just
          // the code, and not the entire equality of Status.UNAUTHENTICATED.
          throw StatusException(
            Status.UNAUTHENTICATED.withDescription(
              "this error should be ignored and result in a retry with a fresh token tepb5xq4kk"
            )
          )
        }
      }

    val result =
      dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)

    result shouldBe OperationResult(data = responseData, errors = emptyList())
    coVerify(exactly = 2) { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
      "retrying with fresh Auth and/or AppCheck tokens"
    )
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
  }

  @Test
  fun `executeMutation() should retry with a fresh AppCheck token on UNAUTHENTICATED`() = runTest {
    val responseData = Arb.proto.struct().next(rs)
    val forceRefresh = AtomicBoolean(false)
    coEvery { mockDataConnectAppCheck.forceRefresh() } answers { forceRefresh.set(true) }
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) } answers
      {
        if (forceRefresh.get()) {
          ExecuteMutationResponse.newBuilder().setData(responseData).build()
        } else {
          // Use a custom description to ensure that DataConnectGrpcClient is checking for just
          // the code, and not the entire equality of Status.UNAUTHENTICATED.
          throw StatusException(
            Status.UNAUTHENTICATED.withDescription(
              "this error should be ignored and result in a retry with a fresh token v2449h6ty8"
            )
          )
        }
      }

    val result =
      dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

    result shouldBe OperationResult(data = responseData, errors = emptyList())
    coVerify(exactly = 2) { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
      "retrying with fresh Auth and/or AppCheck tokens"
    )
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
  }

  @Test
  fun `executeQuery() should NOT retry on error status other than UNAUTHENTICATED`() = runTest {
    val exception = StatusException(Status.INTERNAL)
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) } throws exception

    val thrownException =
      shouldThrow<StatusException> {
        dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)
      }

    thrownException shouldBeSameInstanceAs exception
    coVerify(exactly = 0) { mockDataConnectAuth.forceRefresh() }
    coVerify(exactly = 0) { mockDataConnectAppCheck.forceRefresh() }
  }

  @Test
  fun `executeMutation() should NOT retry on error status other than UNAUTHENTICATED`() = runTest {
    val exception = StatusException(Status.INTERNAL)
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) } throws exception

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
      coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry also fails with UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.UNAUTHENTICATED)
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) } throwsMany
        (listOf(exception1, exception2))

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
      coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with a code other than UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.ABORTED)
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) } throwsMany
        (listOf(exception1, exception2))

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
      coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<TestException> {
          dataConnectGrpcClient.executeQuery(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with some other exception`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = TestException("qz2ykb8wa2")
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<TestException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  private class TestException(message: String) : Exception(message)

  private data class GraphqlErrorInfo(
    val graphqlError: GraphqlError,
    val dataConnectError: DataConnectError,
  ) {
    companion object {
      private val randomPathComponents =
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

        val dataConnectErrorPath = mutableListOf<DataConnectError.PathSegment>()
        val graphqlErrorPath = ListValue.newBuilder()
        repeat(6) {
          if (rs.random.nextFloat() < 0.33f) {
            val pathComponent = randomInts.next(rs)
            dataConnectErrorPath.add(DataConnectError.PathSegment.ListIndex(pathComponent))
            graphqlErrorPath.addValues(Value.newBuilder().setNumberValue(pathComponent.toDouble()))
          } else {
            val pathComponent = randomPathComponents.next(rs)
            dataConnectErrorPath.add(DataConnectError.PathSegment.Field(pathComponent))
            graphqlErrorPath.addValues(Value.newBuilder().setStringValue(pathComponent))
          }
        }

        val dataConnectErrorLocations = mutableListOf<DataConnectError.SourceLocation>()
        val graphqlErrorLocations = mutableListOf<SourceLocation>()
        repeat(3) {
          val line = randomInts.next(rs)
          val column = randomInts.next(rs)
          dataConnectErrorLocations.add(
            DataConnectError.SourceLocation(line = line, column = column)
          )
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

        val dataConnectError =
          DataConnectError(
            message = message,
            path = dataConnectErrorPath.toList(),
            locations = dataConnectErrorLocations.toList()
          )

        return GraphqlErrorInfo(graphqlError, dataConnectError)
      }
    }
  }
}

@Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_AGAINST_NOT_NOTHING_EXPECTED_TYPE")
class DataConnectGrpcClientOperationResultUnitTest {

  private val rs = RandomSource.default()

  @Test
  fun `deserialize() should ignore the module given with DataConnectUntypedData`() {
    val errors = listOf(Arb.dataConnect.dataConnectError().next())
    val operationResult = OperationResult(buildStructProto { put("foo", 42.0) }, errors)
    val result = operationResult.deserialize(DataConnectUntypedData, mockk<SerializersModule>())
    result shouldBe DataConnectUntypedData(mapOf("foo" to 42.0), errors)
  }

  @Test
  fun `deserialize() should treat DataConnectUntypedData specially`() = runTest {
    checkAll(iterations = 20, Arb.dataConnect.operationResult()) { operationResult ->
      val result = operationResult.deserialize(DataConnectUntypedData, serializersModule = null)

      result.asClue {
        if (operationResult.data === null) {
          it.data.shouldBeNull()
        } else {
          it.data shouldBe operationResult.data.toMap()
        }
        it.errors shouldContainExactly operationResult.errors
      }
    }
  }

  @Test
  fun `deserialize() should throw if one or more errors and data is null`() = runTest {
    val arb =
      Arb.dataConnect
        .operationResult()
        .filter { it.errors.isNotEmpty() }
        .map { it.copy(data = null) }
    checkAll(iterations = 5, arb) { operationResult ->
      val exception =
        shouldThrow<DataConnectException> {
          operationResult.deserialize<Nothing>(mockk(), serializersModule = null)
        }
      exception.message shouldContain "${operationResult.errors}"
    }
  }

  @Test
  fun `deserialize() should throw if one or more errors and data is _not_ null`() = runTest {
    val arb =
      Arb.dataConnect.operationResult().filter { it.data !== null && it.errors.isNotEmpty() }
    checkAll(iterations = 5, arb) { operationResult ->
      val exception =
        shouldThrow<DataConnectException> {
          operationResult.deserialize<Nothing>(mockk(), serializersModule = null)
        }
      exception.message shouldContain "${operationResult.errors}"
    }
  }

  @Test
  fun `deserialize() should throw if data is null and errors is empty`() {
    val operationResult = OperationResult(data = null, errors = emptyList())
    val exception =
      shouldThrow<DataConnectException> {
        operationResult.deserialize<Nothing>(mockk(), serializersModule = null)
      }
    exception.message shouldContain "no data"
  }

  @Test
  fun `deserialize() should pass through the SerializersModule`() {
    val data = encodeToStruct(TestData("4jv7vkrs7a"))
    val serializersModule: SerializersModule = mockk()
    val operationResult = OperationResult(data = data, errors = emptyList())
    val deserializer: DeserializationStrategy<TestData> = spyk(serializer())

    operationResult.deserialize(deserializer, serializersModule)

    val slot = slot<Decoder>()
    verify { deserializer.deserialize(capture(slot)) }
    slot.captured.serializersModule shouldBeSameInstanceAs serializersModule
  }

  @Test
  fun `deserialize() successfully deserializes`() = runTest {
    val testData = TestData(Arb.firstName().next().name)
    val operationResult = OperationResult(encodeToStruct(testData), errors = emptyList())

    val deserializedData = operationResult.deserialize(serializer<TestData>(), null)

    deserializedData shouldBe testData
  }

  @Test
  fun `deserialize() throws if decoding fails`() = runTest {
    val data = Arb.proto.struct().next(rs)
    val operationResult = OperationResult(data, errors = emptyList())
    shouldThrow<DataConnectException> { operationResult.deserialize(serializer<TestData>(), null) }
  }

  @Test
  fun `deserialize() re-throws DataConnectException`() = runTest {
    val data = encodeToStruct(TestData("fe45zhyd3m"))
    val operationResult = OperationResult(data = data, errors = emptyList())
    val deserializer: DeserializationStrategy<TestData> = spyk(serializer())
    val exception = DataConnectException(message = Arb.airline().next().name)
    every { deserializer.deserialize(any()) } throws (exception)

    val thrownException =
      shouldThrow<DataConnectException> { operationResult.deserialize(deserializer, null) }

    thrownException shouldBeSameInstanceAs exception
  }

  @Test
  fun `deserialize() wraps non-DataConnectException in DataConnectException`() = runTest {
    val data = encodeToStruct(TestData("rbmkny6b4r"))
    val operationResult = OperationResult(data = data, errors = emptyList())
    val deserializer: DeserializationStrategy<TestData> = spyk(serializer())
    class MyException : Exception("y3cx44q43q")
    val exception = MyException()
    every { deserializer.deserialize(any()) } throws (exception)

    val thrownException =
      shouldThrow<DataConnectException> { operationResult.deserialize(deserializer, null) }

    thrownException.cause shouldBeSameInstanceAs exception
  }

  @Serializable data class TestData(val foo: String)
}
