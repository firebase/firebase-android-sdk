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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.mock
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Test

class QueryManagerUnitTest {

  @Test
  fun `execute() uses the given variablesSerializer`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
      alphanumericStringArb(),
      executeQueryResponseArb()
    ) { args, stringValue, executeQueryResponse ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      val executeQueryRequestSlot: CapturingSlot<ExecuteQueryRequest> =
        dataConnectGrpcRPCs.stubExecuteQuery(executeQueryResponse).executeQueryRequest
      val queryManager: QueryManager = newQueryManager(dataConnectGrpcRPCs = dataConnectGrpcRPCs)
      val variablesSerializer = TestVariablesOverrideSerializer(stringValue)

      queryManager.execute(
        operationName = args.operationName,
        variables = args.variables,
        dataDeserializer = serializer<Unit>(),
        variablesSerializer = variablesSerializer,
        callerSdkType = args.callerSdkType,
        dataSerializersModule = args.dataSerializersModule,
        variablesSerializersModule = args.variablesSerializersModule,
        fetchPolicy = args.fetchPolicy,
      )

      val actualEncodedVariables: Struct = executeQueryRequestSlot.captured.variables
      val expectedEncodedVariables: Struct = TestVariables(stringValue).encodeToStruct()
      actualEncodedVariables shouldBe expectedEncodedVariables
    }
  }
}

private val propTestConfig =
  PropTestConfig(
    iterations = 100,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private fun PropertyContext.newQueryManager(
  requestName: String = "requestName" + randomSource().random.nextAlphanumericString(10),
  dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk(relaxed = true),
  cpuBoundDispatcher: CoroutineDispatcher = Dispatchers.Default,
  secureRandom: Random = randomSource().random,
  logger: Logger = newMockLogger("logger" + randomSource().random.nextAlphanumericString(10)),
): QueryManager =
  QueryManager(
    requestName = requestName,
    dataConnectGrpcRPCs = dataConnectGrpcRPCs,
    cpuBoundDispatcher = cpuBoundDispatcher,
    secureRandom = secureRandom,
    logger = logger,
  )

private data class ExecuteArguments<Data, Variables>(
  val operationName: String,
  val variables: Variables,
  val dataDeserializer: DeserializationStrategy<Data>,
  val variablesSerializer: SerializationStrategy<Variables>,
  val callerSdkType: FirebaseDataConnect.CallerSdkType,
  val dataSerializersModule: SerializersModule?,
  val variablesSerializersModule: SerializersModule?,
  val fetchPolicy: QueryRef.FetchPolicy,
)

@Serializable private data class TestVariables(val value: String)

private fun TestVariables.encodeToStruct(): Struct = buildStructProto { put("value", value) }

@Serializable private data class TestData(val value: String)

private fun TestData.encodeToStruct(): Struct = buildStructProto { put("value", value) }

private fun TestData.encodeToExecuteQueryResponse(): ExecuteQueryResponse =
  ExecuteQueryResponse.newBuilder().setData(encodeToStruct()).build()

private fun String.encodeToExecuteQueryResponse(): ExecuteQueryResponse =
  TestData(this).encodeToExecuteQueryResponse()

private fun alphanumericStringArb(): Arb<String> = Arb.string(10, Codepoint.alphanumeric())

private fun operationNameArb(stringArb: Arb<String> = alphanumericStringArb()): Arb<String> =
  stringArb.map { "operationName_$it" }

private fun testVariablesArb(stringArb: Arb<String> = alphanumericStringArb()): Arb<TestVariables> =
  stringArb.map { TestVariables("vars_$it") }

private fun testDataArb(stringArb: Arb<String> = alphanumericStringArb()): Arb<TestData> =
  stringArb.map { TestData("data_$it") }

private fun executeArgumentsArb(
  operationNameArb: Arb<String> = operationNameArb(),
  variablesArb: Arb<TestVariables> = testVariablesArb(),
  dataDeserializerArb: Arb<DeserializationStrategy<TestData>> = Arb.constant(serializer()),
  variablesSerializerArb: Arb<SerializationStrategy<TestVariables>> = Arb.constant(serializer()),
  callerSdkTypeArb: Arb<FirebaseDataConnect.CallerSdkType> = Arb.enum(),
  dataSerializersModuleArb: Arb<SerializersModule?> =
    Arb.mock<SerializersModule>().orNull(nullProbability = 0.5),
  variablesSerializersModuleArb: Arb<SerializersModule?> =
    Arb.mock<SerializersModule>().orNull(nullProbability = 0.5),
  fetchPolicyArb: Arb<QueryRef.FetchPolicy> = Arb.enum(),
): Arb<ExecuteArguments<TestData, TestVariables>> =
  Arb.bind(
    operationNameArb,
    variablesArb,
    dataDeserializerArb,
    variablesSerializerArb,
    callerSdkTypeArb,
    dataSerializersModuleArb,
    variablesSerializersModuleArb,
    fetchPolicyArb,
    ::ExecuteArguments,
  )

private fun executeQueryResponseArb(
  testDataArb: Arb<TestData> = testDataArb()
): Arb<ExecuteQueryResponse> = testDataArb.map { it.encodeToExecuteQueryResponse() }

private class TestVariablesOverrideSerializer(overrideValue: String) :
  SerializationStrategy<TestVariables> {
  private val overrideVariables = TestVariables(overrideValue)
  private val delegate = serializer<TestVariables>()

  override val descriptor by delegate::descriptor

  override fun serialize(encoder: Encoder, value: TestVariables) =
    delegate.serialize(encoder, overrideVariables)
}

private class ExecuteQueryCaptureSlots {
  val executeQueryRequest: CapturingSlot<ExecuteQueryRequest> = CapturingSlot()
}

private fun DataConnectGrpcRPCs.stubExecuteQuery(
  executeQueryResponse: ExecuteQueryResponse
): ExecuteQueryCaptureSlots {
  val slots = ExecuteQueryCaptureSlots()
  coEvery { executeQuery(any(), capture(slots.executeQueryRequest), any(), any(), any()) } returns
    executeQueryResponse
  return slots
}
