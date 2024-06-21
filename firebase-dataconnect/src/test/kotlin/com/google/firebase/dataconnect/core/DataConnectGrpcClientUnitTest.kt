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
import com.google.firebase.dataconnect.core.DataConnectGrpcClient.OperationResult
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.connectorConfig
import com.google.firebase.dataconnect.testutil.iterator
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.operationName
import com.google.firebase.dataconnect.testutil.projectId
import com.google.firebase.dataconnect.testutil.requestId
import com.google.firebase.dataconnect.testutil.shouldHaveLoggedExactlyOneMessageContaining
import com.google.firebase.dataconnect.util.buildStructProto
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DataConnectGrpcClientUnitTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  private val key = "3sw2m4vkbg"
  private val rs = RandomSource.default()
  private val projectId = Arb.projectId(key).next(rs)
  private val connectorConfig = Arb.connectorConfig(key).next(rs)
  private val requestId = Arb.requestId(key).next(rs)
  private val operationName = Arb.operationName(key).next(rs)
  private val variables = buildStructProto { put("dhxpwjtb6s", key) }

  private val mockDataConnectAuth: DataConnectAuth =
    mockk(relaxed = true, name = "mockDataConnectAuth-$key")

  private val mockDataConnectGrpcRPCs: DataConnectGrpcRPCs =
    mockk(relaxed = true, name = "mockDataConnectGrpcRPCs-$key") {
      coEvery { executeQuery(any(), any()) } returns ExecuteQueryResponse.getDefaultInstance()
      coEvery { executeMutation(any(), any()) } returns ExecuteMutationResponse.getDefaultInstance()
    }

  private val mockLogger = newMockLogger(key)

  private val dataConnectGrpcClient =
    DataConnectGrpcClient(
      projectId = projectId,
      connector = connectorConfig,
      grpcRPCs = mockDataConnectGrpcRPCs,
      dataConnectAuth = mockDataConnectAuth,
      logger = mockLogger,
    )

  @Test
  fun `executeQuery() should send the right request`() = runTest {
    dataConnectGrpcClient.executeQuery(requestId, operationName, variables)

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
    coVerify { mockDataConnectGrpcRPCs.executeQuery(requestId, expectedRequest) }
  }

  @Test
  fun `executeMutation() should send the right request`() = runTest {
    dataConnectGrpcClient.executeMutation(requestId, operationName, variables)

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
    coVerify { mockDataConnectGrpcRPCs.executeMutation(requestId, expectedRequest) }
  }

  @Test
  fun `executeQuery() should return null data and empty errors if response is empty`() = runTest {
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any()) } returns
      ExecuteQueryResponse.getDefaultInstance()

    val operationResult = dataConnectGrpcClient.executeQuery(requestId, operationName, variables)

    operationResult shouldBe OperationResult(data = null, errors = emptyList())
  }

  @Test
  fun `executeMutation() should return null data and empty errors if response is empty`() =
    runTest {
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any()) } returns
        ExecuteMutationResponse.getDefaultInstance()

      val operationResult =
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables)

      operationResult shouldBe OperationResult(data = null, errors = emptyList())
    }

  @Test
  fun `executeQuery() should return data and errors`() = runTest {
    val responseData = buildStructProto { put("foo", key) }
    val responseErrors = List(3) { GraphqlErrorInfo.random(RandomSource.default()) }
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any()) } returns
      ExecuteQueryResponse.newBuilder()
        .setData(responseData)
        .addAllErrors(responseErrors.map { it.graphqlError })
        .build()

    val operationResult = dataConnectGrpcClient.executeQuery(requestId, operationName, variables)

    operationResult shouldBe
      OperationResult(data = responseData, errors = responseErrors.map { it.dataConnectError })
  }

  @Test
  fun `executeMutation() should return data and errors`() = runTest {
    val responseData = buildStructProto { put("foo", key) }
    val responseErrors = List(3) { GraphqlErrorInfo.random(RandomSource.default()) }
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any()) } returns
      ExecuteMutationResponse.newBuilder()
        .setData(responseData)
        .addAllErrors(responseErrors.map { it.graphqlError })
        .build()

    val operationResult = dataConnectGrpcClient.executeMutation(requestId, operationName, variables)

    operationResult shouldBe
      OperationResult(data = responseData, errors = responseErrors.map { it.dataConnectError })
  }

  @Test
  fun `executeQuery() should propagate non-grpc exceptions`() = runTest {
    val exception = TestException(key)
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any()) } throws exception

    val thrownException =
      shouldThrow<TestException> {
        dataConnectGrpcClient.executeQuery(requestId, operationName, variables)
      }

    thrownException shouldBe exception
  }

  @Test
  fun `executeMutation() should propagate non-grpc exceptions`() = runTest {
    val exception = TestException(key)
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any()) } throws exception

    val thrownException =
      shouldThrow<TestException> {
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables)
      }

    thrownException shouldBe exception
  }

  @Test
  fun `executeQuery() should retry with a fresh auth token on UNAUTHENTICATED`() = runTest {
    val responseData = buildStructProto { put("foo", key) }
    val forceRefresh = AtomicBoolean(false)
    coEvery { mockDataConnectAuth.forceRefresh() } answers { forceRefresh.set(true) }
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any()) } answers
      {
        if (forceRefresh.get()) {
          ExecuteQueryResponse.newBuilder().setData(responseData).build()
        } else {
          throw StatusException(Status.UNAUTHENTICATED)
        }
      }

    val result = dataConnectGrpcClient.executeQuery(requestId, operationName, variables)

    result shouldBe OperationResult(data = responseData, errors = emptyList())
    coVerify(exactly = 2) { mockDataConnectGrpcRPCs.executeQuery(any(), any()) }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("retrying with fresh auth token")
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
  }

  @Test
  fun `executeMutation() should retry with a fresh auth token on UNAUTHENTICATED`() = runTest {
    val responseData = buildStructProto { put("foo", key) }
    val forceRefresh = AtomicBoolean(false)
    coEvery { mockDataConnectAuth.forceRefresh() } answers { forceRefresh.set(true) }
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any()) } answers
      {
        if (forceRefresh.get()) {
          ExecuteMutationResponse.newBuilder().setData(responseData).build()
        } else {
          throw StatusException(Status.UNAUTHENTICATED)
        }
      }

    val result = dataConnectGrpcClient.executeMutation(requestId, operationName, variables)

    result shouldBe OperationResult(data = responseData, errors = emptyList())
    coVerify(exactly = 2) { mockDataConnectGrpcRPCs.executeMutation(any(), any()) }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("retrying with fresh auth token")
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
  }

  @Test
  fun `executeQuery() should NOT retry on error status other than UNAUTHENTICATED`() = runTest {
    val exception = StatusException(Status.INTERNAL)
    coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any()) } throws exception

    val thrownException =
      shouldThrow<StatusException> {
        dataConnectGrpcClient.executeQuery(requestId, operationName, variables)
      }

    thrownException shouldBeSameInstanceAs exception
    coVerify(exactly = 0) { mockDataConnectAuth.forceRefresh() }
  }

  @Test
  fun `executeMutation() should NOT retry on error status other than UNAUTHENTICATED`() = runTest {
    val exception = StatusException(Status.INTERNAL)
    coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any()) } throws exception

    val thrownException =
      shouldThrow<StatusException> {
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables)
      }

    thrownException shouldBeSameInstanceAs exception
    coVerify(exactly = 0) { mockDataConnectAuth.forceRefresh() }
  }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry also fails with UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.UNAUTHENTICATED)
      coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeQuery(requestId, operationName, variables)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeMutation() shoud throw the exception from the retry if retry also fails with UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.UNAUTHENTICATED)
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry fails with a code other than UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.ABORTED)
      coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeQuery(requestId, operationName, variables)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with a code other than UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.ABORTED)
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry fails with some other exception`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = TestException(key)
      coEvery { mockDataConnectGrpcRPCs.executeQuery(any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<TestException> {
          dataConnectGrpcClient.executeQuery(requestId, operationName, variables)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with some other exception`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = TestException(key)
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any()) } throwsMany
        (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<TestException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables)
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
