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

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.mock
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterations
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
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
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Rule
import org.junit.Test

class QueryManagerUnitTest {

  @get:Rule val cleanups = CleanupsRule()

  @Test
  fun `execute() uses the requestName that was given to the constructor`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
      alphanumericStringArb(),
    ) { args, requestName ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      val executeQueryRequestSlot = CapturingSlot<ExecuteQueryRequest>()
      dataConnectGrpcRPCs.stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot)
      val queryManager: QueryManager =
        newQueryManager(requestName = requestName, dataConnectGrpcRPCs = dataConnectGrpcRPCs)

      queryManager.execute(
        operationName = args.operationName,
        variables = args.variables,
        dataDeserializer = args.dataDeserializer,
        variablesSerializer = args.variablesSerializer,
        dataSerializersModule = args.dataSerializersModule,
        variablesSerializersModule = args.variablesSerializersModule,
        callerSdkType = args.callerSdkType,
        fetchPolicy = args.fetchPolicy,
      )

      val capturedName: String = executeQueryRequestSlot.captured.name
      capturedName shouldBe requestName
    }
  }

  @Test
  fun `execute() uses the given operationName`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
    ) { args ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      val executeQueryRequestSlot = CapturingSlot<ExecuteQueryRequest>()
      dataConnectGrpcRPCs.stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot)
      val queryManager: QueryManager = newQueryManager(dataConnectGrpcRPCs = dataConnectGrpcRPCs)

      queryManager.execute(
        operationName = args.operationName,
        variables = args.variables,
        dataDeserializer = args.dataDeserializer,
        variablesSerializer = args.variablesSerializer,
        dataSerializersModule = args.dataSerializersModule,
        variablesSerializersModule = args.variablesSerializersModule,
        callerSdkType = args.callerSdkType,
        fetchPolicy = args.fetchPolicy,
      )

      val capturedOperationName: String = executeQueryRequestSlot.captured.operationName
      capturedOperationName shouldBe args.operationName
    }
  }

  @Test
  fun `execute() uses the given variables`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
    ) { args ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      val executeQueryRequestSlot = CapturingSlot<ExecuteQueryRequest>()
      dataConnectGrpcRPCs.stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot)
      val queryManager: QueryManager = newQueryManager(dataConnectGrpcRPCs = dataConnectGrpcRPCs)

      queryManager.execute(
        operationName = args.operationName,
        variables = args.variables,
        dataDeserializer = args.dataDeserializer,
        variablesSerializer = args.variablesSerializer,
        dataSerializersModule = args.dataSerializersModule,
        variablesSerializersModule = args.variablesSerializersModule,
        callerSdkType = args.callerSdkType,
        fetchPolicy = args.fetchPolicy,
      )

      val capturedVariables: Struct = executeQueryRequestSlot.captured.variables
      val expectedVariables: Struct = args.variables.encodeToStruct()
      capturedVariables shouldBe expectedVariables
    }
  }

  @Test
  fun `execute() uses the given dataDeserializer`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
      alphanumericStringArb(),
    ) { args, overrideValue ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      dataConnectGrpcRPCs.stubExecuteQuery(
        executeQueryResponse = overrideValue.encodeToExecuteQueryResponse()
      )
      val queryManager: QueryManager = newQueryManager(dataConnectGrpcRPCs = dataConnectGrpcRPCs)
      val dataDeserializer = TestDataOverrideDeserializer(overrideValue)

      val result: TestData =
        queryManager.execute(
          operationName = args.operationName,
          variables = args.variables,
          dataDeserializer = dataDeserializer,
          variablesSerializer = args.variablesSerializer,
          dataSerializersModule = args.dataSerializersModule,
          variablesSerializersModule = args.variablesSerializersModule,
          callerSdkType = args.callerSdkType,
          fetchPolicy = args.fetchPolicy,
        )

      result shouldBe TestData(overrideValue)
    }
  }

  @Test
  fun `execute() uses the given variablesSerializer`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
      alphanumericStringArb(),
    ) { args, overrideValue ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      val executeQueryRequestSlot = CapturingSlot<ExecuteQueryRequest>()
      dataConnectGrpcRPCs.stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot)
      val queryManager: QueryManager = newQueryManager(dataConnectGrpcRPCs = dataConnectGrpcRPCs)
      val variablesSerializer = TestVariablesOverrideSerializer(overrideValue)

      queryManager.execute(
        operationName = args.operationName,
        variables = args.variables,
        dataDeserializer = args.dataDeserializer,
        variablesSerializer = variablesSerializer,
        dataSerializersModule = args.dataSerializersModule,
        variablesSerializersModule = args.variablesSerializersModule,
        callerSdkType = args.callerSdkType,
        fetchPolicy = args.fetchPolicy,
      )

      val capturedVariables: Struct = executeQueryRequestSlot.captured.variables
      val expectedVariables: Struct = TestVariables(overrideValue).encodeToStruct()
      capturedVariables shouldBe expectedVariables
    }
  }

  @Test
  fun `execute() uses the given dataSerializersModule`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
      alphanumericStringArb().pair(),
    ) { args, (responseValue, overrideValue) ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      dataConnectGrpcRPCs.stubExecuteQuery(
        executeQueryResponse = responseValue.encodeToExecuteQueryResponse()
      )
      val queryManager: QueryManager = newQueryManager(dataConnectGrpcRPCs = dataConnectGrpcRPCs)
      val dataDeserializer = serializer<ContextualTestData>()
      val dataSerializersModule = SerializersModule {
        contextual(String::class, HardcodedStringKSerializer(overrideValue))
      }

      val result: ContextualTestData =
        queryManager.execute(
          operationName = args.operationName,
          variables = args.variables,
          dataDeserializer = dataDeserializer,
          variablesSerializer = args.variablesSerializer,
          dataSerializersModule = dataSerializersModule,
          variablesSerializersModule = args.variablesSerializersModule,
          callerSdkType = args.callerSdkType,
          fetchPolicy = args.fetchPolicy,
        )

      result shouldBe ContextualTestData(overrideValue)
    }
  }

  @Test
  fun `execute() uses the given variablesSerializersModule`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
      alphanumericStringArb().pair(),
    ) { args, (requestValue, overrideValue) ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      val executeQueryRequestSlot = CapturingSlot<ExecuteQueryRequest>()
      dataConnectGrpcRPCs.stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot)
      val queryManager: QueryManager = newQueryManager(dataConnectGrpcRPCs = dataConnectGrpcRPCs)
      val variables = ContextualTestVariables(requestValue)
      val variablesSerializer = serializer<ContextualTestVariables>()
      val variablesSerializersModule = SerializersModule {
        contextual(String::class, HardcodedStringKSerializer(overrideValue))
      }

      queryManager.execute(
        operationName = args.operationName,
        variables = variables,
        dataDeserializer = args.dataDeserializer,
        variablesSerializer = variablesSerializer,
        dataSerializersModule = args.dataSerializersModule,
        variablesSerializersModule = variablesSerializersModule,
        callerSdkType = args.callerSdkType,
        fetchPolicy = args.fetchPolicy,
      )

      val capturedVariables: Struct = executeQueryRequestSlot.captured.variables
      val expectedVariables: Struct = TestVariables(overrideValue).encodeToStruct()
      capturedVariables shouldBe expectedVariables
    }
  }

  @Test
  fun `execute() uses the given callerSdkType`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
      Arb.enum<CallerSdkType>(),
    ) { args, callerSdkType ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      val callerSdkTypeSlot = CapturingSlot<CallerSdkType>()
      dataConnectGrpcRPCs.stubExecuteQuery(callerSdkTypeSlot = callerSdkTypeSlot)
      val queryManager: QueryManager = newQueryManager(dataConnectGrpcRPCs = dataConnectGrpcRPCs)

      queryManager.execute(
        operationName = args.operationName,
        variables = args.variables,
        dataDeserializer = args.dataDeserializer,
        variablesSerializer = args.variablesSerializer,
        dataSerializersModule = args.dataSerializersModule,
        variablesSerializersModule = args.variablesSerializersModule,
        callerSdkType = callerSdkType,
        fetchPolicy = args.fetchPolicy,
      )

      val capturedCallerSdkType: CallerSdkType = callerSdkTypeSlot.captured
      capturedCallerSdkType shouldBe callerSdkType
    }
  }

  @Test
  fun `execute() accesses the SecureRandom on the IO dispatcher`() = runTest {
    checkAll(propTestConfig, executeArgumentsArb()) { args ->
      val ioExecutor = Executors.newSingleThreadExecutor()
      cleanups.register { ioExecutor.shutdownNow() }
      val ioDispatcher = ioExecutor.asCoroutineDispatcher()
      val ioThread = withContext(ioDispatcher) { Thread.currentThread() }
      val nextIntThreads = mutableListOf<Thread>()
      val secureRandom =
        RandomWrapper(randomSource().random) {
          synchronized(nextIntThreads) { nextIntThreads.add(Thread.currentThread()) }
        }
      val queryManager: QueryManager =
        newQueryManager(ioDispatcher = ioDispatcher, secureRandom = secureRandom)

      queryManager.execute(
        operationName = args.operationName,
        variables = args.variables,
        dataDeserializer = args.dataDeserializer,
        variablesSerializer = args.variablesSerializer,
        dataSerializersModule = args.dataSerializersModule,
        variablesSerializersModule = args.variablesSerializersModule,
        callerSdkType = args.callerSdkType,
        fetchPolicy = args.fetchPolicy,
      )

      synchronized(nextIntThreads) {
        nextIntThreads.shouldNotBeEmpty()
        nextIntThreads.distinct().shouldHaveSize(1).single() shouldBe ioThread
      }
    }
  }

  @Test
  fun `execute() deduplicates identical queries`() =
    verifyExecuteDeduplication(
      getDataDeserializer = { _, _, dataDeserializer -> dataDeserializer },
      getSerializersModule = { _, _, serializersModule -> serializersModule },
      verifyResults = { valuePrefix, _, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
      },
    )

  @Test
  fun `execute() deduplicates identical queries except for dataDeserializer`() =
    verifyExecuteDeduplication(
      getDataDeserializer = { valuePrefixOverride, jobIndex, _ ->
        TestDataOverrideDeserializer("$valuePrefixOverride $jobIndex")
      },
      getSerializersModule = { _, _, serializersModule -> serializersModule },
      verifyResults = { _, valuePrefixOverride, results ->
        val values = results.map { it.value }
        val expectedResults = List(values.size) { "$valuePrefixOverride $it" }
        values shouldContainExactlyInAnyOrder expectedResults
      },
    )

  @Test
  fun `execute() deduplicates identical queries except for dataSerializersModule`() {
    val dataDeserializer = serializer<ContextualTestData>()
    return verifyExecuteDeduplication(
      getDataDeserializer = { _, _, _ -> dataDeserializer },
      getSerializersModule = { valuePrefixOverride, jobIndex, _ ->
        SerializersModule {
          contextual(String::class, HardcodedStringKSerializer("$valuePrefixOverride $jobIndex"))
        }
      },
      verifyResults = { _, valuePrefixOverride, results ->
        val values = results.map { it.value }
        val expectedResults = List(values.size) { "$valuePrefixOverride $it" }
        values shouldContainExactlyInAnyOrder expectedResults
      },
    )
  }

  private fun <Data> verifyExecuteDeduplication(
    getDataDeserializer:
      (
        valuePrefixOverride: String, jobIndex: Int, DeserializationStrategy<TestData>
      ) -> DeserializationStrategy<Data>,
    getSerializersModule:
      (valuePrefixOverride: String, jobIndex: Int, SerializersModule?) -> SerializersModule?,
    verifyResults: (valuePrefix: String, valuePrefixOverride: String, List<Data>) -> Unit,
  ) = runTest {
    checkAll(
      propTestConfig.withIterations(5),
      executeArgumentsArb(),
      alphanumericStringArb().pair()
    ) { args, (valuePrefix, valuePrefixOverride) ->
      val latch = SuspendingCountDownLatch(10)
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
        val executeQueryInvocationCount = AtomicInteger(0)
        coEvery { executeQuery(any(), any(), any(), any(), any()) } coAnswers
          {
            val invocationIndex = executeQueryInvocationCount.getAndIncrement()
            if (invocationIndex == 0) {
              latch.countDown().await()
              delay(10.milliseconds)
            }
            "$valuePrefix $invocationIndex".encodeToExecuteQueryResponse()
          }
      }
      val queryManager: QueryManager = newQueryManager(dataConnectGrpcRPCs = dataConnectGrpcRPCs)

      val executeJobIndex = randomSource().random.nextInt(0 until latch.count)
      val jobs =
        List(latch.count) { jobIndex ->
          backgroundScope.async(Dispatchers.IO) {
            if (jobIndex != executeJobIndex) {
              latch.countDown().await()
            }
            queryManager.execute(
              operationName = args.operationName,
              variables = args.variables,
              dataDeserializer =
                getDataDeserializer(valuePrefixOverride, jobIndex, args.dataDeserializer),
              variablesSerializer = args.variablesSerializer,
              dataSerializersModule =
                getSerializersModule(valuePrefixOverride, jobIndex, args.dataSerializersModule),
              variablesSerializersModule = args.variablesSerializersModule,
              callerSdkType = args.callerSdkType,
              fetchPolicy = args.fetchPolicy,
            )
          }
        }

      val results = jobs.awaitAll()
      verifyResults(valuePrefix, valuePrefixOverride, results)
      coVerify(exactly = 2) { dataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any()) }
    }
  }

  private suspend fun PropertyContext.newQueryManager(
    requestName: String = "requestName" + randomSource().random.nextAlphanumericString(10),
    dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk { stubExecuteQuery() },
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    secureRandom: Random = randomSource().random,
    logger: Logger = newMockLogger("logger" + randomSource().random.nextAlphanumericString(10)),
  ): QueryManager {
    val queryManager =
      QueryManager(
        requestName = requestName,
        dataConnectGrpcRPCs = dataConnectGrpcRPCs,
        ioDispatcher = ioDispatcher,
        cpuDispatcher = cpuDispatcher,
        secureRandom = secureRandom,
        logger = logger,
      )
    cleanups.registerSuspending { queryManager.close() }
    return queryManager
  }
}

private val propTestConfig =
  PropTestConfig(
    iterations = 100,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private data class ExecuteArguments<Data, Variables>(
  val operationName: String,
  val variables: Variables,
  val dataDeserializer: DeserializationStrategy<Data>,
  val variablesSerializer: SerializationStrategy<Variables>,
  val callerSdkType: CallerSdkType,
  val dataSerializersModule: SerializersModule?,
  val variablesSerializersModule: SerializersModule?,
  val fetchPolicy: QueryRef.FetchPolicy,
)

@Serializable private data class TestVariables(val value: String)

private fun TestVariables.encodeToStruct(): Struct = buildStructProto { put("value", value) }

@Serializable private data class ContextualTestVariables(@Contextual val value: String)

@Serializable private data class TestData(val value: String)

private fun TestData.encodeToStruct(): Struct = buildStructProto { put("value", value) }

@Serializable private data class ContextualTestData(@Contextual val value: String)

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
  callerSdkTypeArb: Arb<CallerSdkType> = Arb.enum(),
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

private class TestDataOverrideDeserializer(overrideValue: String) :
  DeserializationStrategy<TestData> {
  private val overrideData = TestData(overrideValue)
  private val delegate = serializer<TestData>()

  override val descriptor by delegate::descriptor

  override fun deserialize(decoder: Decoder) = overrideData
}

private class HardcodedStringKSerializer(private val hardcodedValue: String) : KSerializer<String> {
  override val descriptor = PrimitiveSerialDescriptor("HardcodedString", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: String) {
    encoder.encodeString(hardcodedValue)
  }

  override fun deserialize(decoder: Decoder): String = hardcodedValue
}

private fun DataConnectGrpcRPCs.stubExecuteQuery(
  executeQueryRequestSlot: CapturingSlot<ExecuteQueryRequest> = CapturingSlot(),
  callerSdkTypeSlot: CapturingSlot<CallerSdkType> = CapturingSlot(),
  executeQueryResponse: ExecuteQueryResponse? = null
) {
  coEvery {
    executeQuery(any(), capture(executeQueryRequestSlot), any(), any(), capture(callerSdkTypeSlot))
  } answers
    {
      executeQueryResponse
        ?: TestData("data_qhpgbccsar_" + Random.nextAlphanumericString(10))
          .encodeToExecuteQueryResponse()
    }
}

private class RandomWrapper(
  private val delegate: Random,
  private val onMethodCalled: () -> Unit,
) : Random() {

  override fun nextBits(bitCount: Int): Int {
    onMethodCalled()
    return delegate.nextBits(bitCount)
  }
}
