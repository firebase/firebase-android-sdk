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

import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectError
import com.google.firebase.dataconnect.testutil.connectorConfig
import com.google.firebase.dataconnect.testutil.iterator
import com.google.firebase.dataconnect.testutil.randomOperationName
import com.google.firebase.dataconnect.testutil.randomProjectId
import com.google.firebase.dataconnect.testutil.requestId
import com.google.firebase.dataconnect.util.buildStructProto
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.GraphqlError
import google.firebase.dataconnect.proto.SourceLocation
import google.firebase.dataconnect.proto.executeMutationResponse
import google.firebase.dataconnect.proto.executeQueryResponse
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectGrpcClientUnitTest {

  @Test
  fun `executeQuery() should send the right request`() = runTest {
    val key = "3sw2m4vkbg"
    val testValues = TestValues.fromKey(key)
    val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
    val requestId = Arb.requestId(key).next()
    val operationName = randomOperationName(key)
    val variables = buildStructProto { put("foo", key) }

    dataConnectGrpcClient.executeQuery(requestId, operationName, variables)

    testValues.executeQueryRequestIdSlot.asClue { slot ->
      slot.isCaptured shouldBe true
      slot.captured shouldBe requestId
    }
    testValues.executeQueryRequestSlot.asClue { slot ->
      slot.isCaptured shouldBe true
      assertSoftly(slot.captured) {
        name shouldBe
          ("projects/${testValues.projectId}" +
            "/locations/${testValues.connectorConfig.location}" +
            "/services/${testValues.connectorConfig.serviceId}" +
            "/connectors/${testValues.connectorConfig.connector}")
        operationName shouldBe operationName
        variables shouldBe variables
      }
    }
  }

  @Test
  fun `executeMutation() should send the right request`() = runTest {
    val key = "hbfkfxw5z8"
    val testValues = TestValues.fromKey(key)
    val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
    val requestId = Arb.requestId(key).next()
    val operationName = randomOperationName(key)
    val variables = buildStructProto { put("foo", key) }

    dataConnectGrpcClient.executeQuery(requestId, operationName, variables)

    testValues.executeQueryRequestIdSlot.asClue { slot ->
      slot.isCaptured shouldBe true
      slot.captured shouldBe requestId
    }
    testValues.executeQueryRequestSlot.asClue { slot ->
      slot.isCaptured shouldBe true
      assertSoftly(slot.captured) {
        name shouldBe
          ("projects/${testValues.projectId}" +
            "/locations/${testValues.connectorConfig.location}" +
            "/services/${testValues.connectorConfig.serviceId}" +
            "/connectors/${testValues.connectorConfig.connector}")
        operationName shouldBe operationName
        variables shouldBe variables
      }
    }
  }

  @Test
  fun `executeQuery() should return null data and empty errors if response is empty`() = runTest {
    val key = "nja9w9mpcv"
    val testValues = TestValues.fromKey(key)
    val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
    coEvery { testValues.dataConnectGrpcRPCs.executeQuery(any(), any()) } returns
      ExecuteQueryResponse.getDefaultInstance()

    val operationResult =
      dataConnectGrpcClient.executeQuery(
        Arb.requestId(key).next(),
        randomOperationName(key),
        Struct.getDefaultInstance()
      )

    operationResult.asClue {
      assertSoftly(operationResult) {
        data.shouldBeNull()
        errors.shouldBeEmpty()
      }
    }
  }

  @Test
  fun `executeMutation() should return null data and empty errors if response is empty`() =
    runTest {
      val key = "bph4vbkf2q"
      val testValues = TestValues.fromKey(key)
      val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
      coEvery { testValues.dataConnectGrpcRPCs.executeMutation(any(), any()) } returns
        ExecuteMutationResponse.getDefaultInstance()

      val operationResult =
        dataConnectGrpcClient.executeMutation(
          Arb.requestId(key).next(),
          randomOperationName(key),
          Struct.getDefaultInstance()
        )

      operationResult.asClue {
        assertSoftly(operationResult) {
          data.shouldBeNull()
          errors.shouldBeEmpty()
        }
      }
    }

  @Test
  fun `executeQuery() should return data and errors`() = runTest {
    val key = "r8pj9yy6h9"
    val testValues = TestValues.fromKey(key)
    val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
    val responseData = buildStructProto { put("foo", key) }
    val responseErrors = List(3) { GraphqlErrorInfo.random(RandomSource.default()) }
    coEvery { testValues.dataConnectGrpcRPCs.executeQuery(any(), any()) } returns
      executeQueryResponse {
        this.data = responseData
        this.errors.addAll(responseErrors.map { it.graphqlError })
      }

    val operationResult =
      dataConnectGrpcClient.executeQuery(
        Arb.requestId(key).next(),
        randomOperationName(key),
        Struct.getDefaultInstance()
      )

    operationResult.asClue {
      assertSoftly(operationResult) {
        data shouldBe responseData
        errors shouldBe responseErrors.map { it.dataConnectError }
      }
    }
  }

  @Test
  fun `executeMutation() should return data and errors`() = runTest {
    val key = "7vv52na427"
    val testValues = TestValues.fromKey(key)
    val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
    val responseData = buildStructProto { put("foo", key) }
    val responseErrors = List(3) { GraphqlErrorInfo.random(RandomSource.default()) }
    coEvery { testValues.dataConnectGrpcRPCs.executeMutation(any(), any()) } returns
      executeMutationResponse {
        this.data = responseData
        this.errors.addAll(responseErrors.map { it.graphqlError })
      }

    val operationResult =
      dataConnectGrpcClient.executeMutation(
        Arb.requestId(key).next(),
        randomOperationName(key),
        Struct.getDefaultInstance()
      )

    operationResult.asClue {
      assertSoftly(operationResult) {
        data shouldBe responseData
        errors shouldBe responseErrors.map { it.dataConnectError }
      }
    }
  }

  @Test
  fun `executeQuery() should propagate exceptions from grpc`() = runTest {
    val key = "kxba2fg2gz"
    val testValues = TestValues.fromKey(key)
    val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
    val grpcException = TestException(key)
    coEvery { testValues.dataConnectGrpcRPCs.executeQuery(any(), any()) } throws grpcException

    val exception =
      shouldThrow<TestException> {
        dataConnectGrpcClient.executeQuery(
          Arb.requestId(key).next(),
          randomOperationName(key),
          Struct.getDefaultInstance()
        )
      }

    exception shouldBe grpcException
  }

  @Test
  fun `executeMutation() should propagate exceptions from grpc`() = runTest {
    val key = "q9v42fqv2t"
    val testValues = TestValues.fromKey(key)
    val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
    val grpcException = TestException(key)
    coEvery { testValues.dataConnectGrpcRPCs.executeMutation(any(), any()) } throws grpcException

    val exception =
      shouldThrow<TestException> {
        dataConnectGrpcClient.executeMutation(
          Arb.requestId(key).next(),
          randomOperationName(key),
          Struct.getDefaultInstance()
        )
      }

    exception shouldBe grpcException
  }

  private class TestException(message: String) : Exception(message)

  private data class TestValues(
    val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
    val projectId: String,
    val connectorConfig: ConnectorConfig,
    val executeQueryRequestIdSlot: CapturingSlot<String>,
    val executeQueryRequestSlot: CapturingSlot<ExecuteQueryRequest>,
    val executeMutationRequestIdSlot: CapturingSlot<String>,
    val executeMutationRequestSlot: CapturingSlot<ExecuteMutationRequest>,
  ) {
    fun newDataConnectGrpcClient(): DataConnectGrpcClient =
      DataConnectGrpcClient(
        projectId = projectId,
        connector = connectorConfig,
        grpcRPCs = dataConnectGrpcRPCs,
        parentLogger = mockk(relaxed = true)
      )
    companion object {
      fun fromKey(key: String, rs: RandomSource = RandomSource.default()): TestValues {
        val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk(relaxed = true)

        val executeQueryRequestIdSlot = slot<String>()
        val executeQueryRequestSlot = slot<ExecuteQueryRequest>()
        coEvery {
          dataConnectGrpcRPCs.executeQuery(
            capture(executeQueryRequestIdSlot),
            capture(executeQueryRequestSlot)
          )
        } returns ExecuteQueryResponse.getDefaultInstance()

        val executeMutationRequestIdSlot = slot<String>()
        val executeMutationRequestSlot = slot<ExecuteMutationRequest>()
        coEvery {
          dataConnectGrpcRPCs.executeMutation(
            capture(executeMutationRequestIdSlot),
            capture(executeMutationRequestSlot)
          )
        } returns ExecuteMutationResponse.getDefaultInstance()

        return TestValues(
          dataConnectGrpcRPCs = dataConnectGrpcRPCs,
          projectId = randomProjectId(key),
          connectorConfig = Arb.connectorConfig(key).next(rs),
          executeQueryRequestIdSlot = executeQueryRequestIdSlot,
          executeQueryRequestSlot = executeQueryRequestSlot,
          executeMutationRequestIdSlot = executeMutationRequestIdSlot,
          executeMutationRequestSlot = executeMutationRequestSlot,
        )
      }
    }
  }

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
