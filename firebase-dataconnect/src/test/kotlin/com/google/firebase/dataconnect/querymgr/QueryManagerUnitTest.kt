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

@file:OptIn(ExperimentalKotest::class, DelicateKotest::class)

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.appCheckTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.authTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.mock
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterations
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.kotest.common.DelicateKotest
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
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.concurrent.CopyOnWriteArrayList
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
      val executeQueryRequestSlot = slot<ExecuteQueryRequest>()
      val queryManager: QueryManager = buildQueryManager {
        setRequestName(requestName)
        setDataConnectGrpcRPCs(
          mockk { stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot) }
        )
      }

      queryManager.execute(args)

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
      val executeQueryRequestSlot = slot<ExecuteQueryRequest>()
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(
          mockk { stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot) }
        )
      }

      queryManager.execute(args)

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
      val executeQueryRequestSlot = slot<ExecuteQueryRequest>()
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(
          mockk { stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot) }
        )
      }

      queryManager.execute(args)

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
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(
          mockk {
            stubExecuteQuery(executeQueryResponse = overrideValue.encodeToExecuteQueryResponse())
          }
        )
      }
      val dataDeserializer = TestDataOverrideDeserializer(overrideValue)

      val result: TestData = queryManager.execute(args.copy(dataDeserializer = dataDeserializer))

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
      val executeQueryRequestSlot = slot<ExecuteQueryRequest>()
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(
          mockk { stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot) }
        )
      }
      val variablesSerializer = TestVariablesOverrideSerializer(overrideValue)

      queryManager.execute(args.copy(variablesSerializer = variablesSerializer))

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
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(
          mockk {
            stubExecuteQuery(executeQueryResponse = responseValue.encodeToExecuteQueryResponse())
          }
        )
      }
      val dataDeserializer = serializer<ContextualTestData>()
      val dataSerializersModule = SerializersModule {
        contextual(String::class, HardcodedStringKSerializer(overrideValue))
      }

      val result: ContextualTestData =
        queryManager.execute(
          args
            .withDataDeserializer(dataDeserializer)
            .copy(dataSerializersModule = dataSerializersModule)
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
      val executeQueryRequestSlot = slot<ExecuteQueryRequest>()
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(
          mockk { stubExecuteQuery(executeQueryRequestSlot = executeQueryRequestSlot) }
        )
      }
      val variables = ContextualTestVariables(requestValue)
      val variablesSerializer = serializer<ContextualTestVariables>()
      val variablesSerializersModule = SerializersModule {
        contextual(String::class, HardcodedStringKSerializer(overrideValue))
      }

      queryManager.execute(
        args
          .withVariables(variables, variablesSerializer)
          .copy(variablesSerializersModule = variablesSerializersModule)
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
      val callerSdkTypeSlot = slot<CallerSdkType>()
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(mockk { stubExecuteQuery(callerSdkTypeSlot = callerSdkTypeSlot) })
      }

      queryManager.execute(args.copy(callerSdkType = callerSdkType))

      val capturedCallerSdkType: CallerSdkType = callerSdkTypeSlot.captured
      capturedCallerSdkType shouldBe callerSdkType
    }
  }

  @Test
  fun `execute() when DataConnectAuth returns null sends null authToken`() = runTest {
    checkAll(propTestConfig, executeArgumentsArb()) { args ->
      val authTokenSlot = slot<String?>()
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(mockk { stubExecuteQuery(authTokenSlot = authTokenSlot) })
        setDataConnectAuth(mockk { coEvery { getToken(any()) } returns null })
      }

      queryManager.execute(args)

      authTokenSlot.captured shouldBe null
    }
  }

  @Test
  fun `execute() when DataConnectAuth returns non-null with null token sends null authToken`() =
    runTest {
      val authTokenResultArb = Arb.dataConnect.authTokenResult(accessToken = Arb.constant(null))
      checkAll(propTestConfig, executeArgumentsArb(), authTokenResultArb) { args, authTokenResult ->
        val authTokenSlot = slot<String?>()
        val queryManager: QueryManager = buildQueryManager {
          setDataConnectGrpcRPCs(mockk { stubExecuteQuery(authTokenSlot = authTokenSlot) })
          setDataConnectAuth(
            mockk {
              check(authTokenResult.token === null) // precondition check
              coEvery { getToken(any()) } returns authTokenResult
            }
          )
        }

        queryManager.execute(args)

        authTokenSlot.captured shouldBe null
      }
    }

  @Test
  fun `execute() when DataConnectAuth returns non-null token sends the authToken`() = runTest {
    val authTokenResultArb =
      Arb.dataConnect.authTokenResult(accessToken = Arb.dataConnect.accessToken())
    checkAll(propTestConfig, executeArgumentsArb(), authTokenResultArb) { args, authTokenResult ->
      val authTokenSlot = slot<String?>()
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(mockk { stubExecuteQuery(authTokenSlot = authTokenSlot) })
        setDataConnectAuth(
          mockk {
            checkNotNull(authTokenResult.token) // precondition check
            coEvery { getToken(any()) } returns authTokenResult
          }
        )
      }

      queryManager.execute(args)

      authTokenSlot.captured shouldBe authTokenResult.token
    }
  }

  @Test
  fun `execute() when DataConnectAppCheck returns null sends null appCheckToken`() = runTest {
    checkAll(propTestConfig, executeArgumentsArb()) { args ->
      val appCheckTokenSlot = slot<String?>()
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(mockk { stubExecuteQuery(appCheckTokenSlot = appCheckTokenSlot) })
        setDataConnectAppCheck(mockk { coEvery { getToken(any()) } returns null })
      }

      queryManager.execute(args)

      appCheckTokenSlot.captured shouldBe null
    }
  }

  @Test
  fun `execute() when DataConnectAppCheck returns non-null with null token sends null appCheckToken`() =
    runTest {
      val appCheckTokenResultArb =
        Arb.dataConnect.appCheckTokenResult(accessToken = Arb.constant(null))
      checkAll(propTestConfig, executeArgumentsArb(), appCheckTokenResultArb) {
        args,
        appCheckTokenResult ->
        val appCheckTokenSlot = slot<String?>()
        val queryManager: QueryManager = buildQueryManager {
          setDataConnectGrpcRPCs(mockk { stubExecuteQuery(appCheckTokenSlot = appCheckTokenSlot) })
          setDataConnectAppCheck(
            mockk {
              check(appCheckTokenResult.token === null) // precondition check
              coEvery { getToken(any()) } returns appCheckTokenResult
            }
          )
        }

        queryManager.execute(args)

        appCheckTokenSlot.captured shouldBe null
      }
    }

  @Test
  fun `execute() when DataConnectAppCheck returns non-null token sends the appCheckToken`() =
    runTest {
      val appCheckTokenResultArb =
        Arb.dataConnect.appCheckTokenResult(accessToken = Arb.dataConnect.accessToken())
      checkAll(propTestConfig, executeArgumentsArb(), appCheckTokenResultArb) {
        args,
        appCheckTokenResult ->
        val appCheckTokenSlot = slot<String?>()
        val queryManager: QueryManager = buildQueryManager {
          setDataConnectGrpcRPCs(mockk { stubExecuteQuery(appCheckTokenSlot = appCheckTokenSlot) })
          setDataConnectAppCheck(
            mockk {
              checkNotNull(appCheckTokenResult.token) // precondition check
              coEvery { getToken(any()) } returns appCheckTokenResult
            }
          )
        }

        queryManager.execute(args)

        appCheckTokenSlot.captured shouldBe appCheckTokenResult.token
      }
    }

  @Test
  fun `execute() accesses the SecureRandom on the IO dispatcher`() = runTest {
    checkAll(propTestConfig, executeArgumentsArb()) { args ->
      val ioDispatcher =
        Executors.newSingleThreadExecutor().let {
          val ioExecutor = Executors.newSingleThreadExecutor()
          cleanups.register { ioExecutor.shutdownNow() }
          ioExecutor.asCoroutineDispatcher()
        }
      val ioThread = withContext(ioDispatcher) { Thread.currentThread() }
      val nextIntThreads = mutableListOf<Thread>()
      val secureRandom =
        RandomWrapper(randomSource().random) {
          synchronized(nextIntThreads) { nextIntThreads.add(Thread.currentThread()) }
        }
      val queryManager: QueryManager = buildQueryManager {
        setIoDispatcher(ioDispatcher)
        setSecureRandom(secureRandom)
      }

      queryManager.execute(args)

      synchronized(nextIntThreads) {
        nextIntThreads.shouldNotBeEmpty()
        nextIntThreads.distinct().shouldHaveSize(1).single() shouldBe ioThread
      }
    }
  }

  @Test
  fun `execute() deduplicates identical queries`() =
    verifyExecuteDeduplication(
      getDataDeserializer = { it.args.dataDeserializer },
      verifyResults = { valuePrefix, _, _, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
      },
    )

  @Test
  fun `execute() deduplicates identical queries even with different dataDeserializer`() =
    verifyExecuteDeduplication(
      getDataDeserializer = {
        TestDataOverrideDeserializer("${it.valuePrefixOverride} ${it.jobIndex}")
      },
      verifyResults = { _, valuePrefixOverride, _, results ->
        val values = results.map { it.value }
        val expectedResults = List(values.size) { "$valuePrefixOverride $it" }
        values shouldContainExactlyInAnyOrder expectedResults
      },
    )

  @Test
  fun `execute() deduplicates identical queries even with different dataSerializersModule`() {
    val dataDeserializer = serializer<ContextualTestData>()
    return verifyExecuteDeduplication(
      getDataDeserializer = { dataDeserializer },
      getDataSerializersModule = {
        SerializersModule {
          contextual(
            String::class,
            HardcodedStringKSerializer("${it.valuePrefixOverride} ${it.jobIndex}")
          )
        }
      },
      verifyResults = { _, valuePrefixOverride, _, results ->
        val values = results.map { it.value }
        val expectedResults = List(values.size) { "$valuePrefixOverride $it" }
        values shouldContainExactlyInAnyOrder expectedResults
      },
    )
  }

  @Test
  fun `execute() deduplicates identical queries, even with different variablesSerializer that produces same Struct`() {
    class DistinctVariablesSerializer(delegate: SerializationStrategy<TestVariables>) :
      SerializationStrategy<TestVariables> by delegate
    run {
      val delegate = serializer<TestVariables>()
      check(DistinctVariablesSerializer(delegate) != DistinctVariablesSerializer(delegate))
    }

    verifyExecuteDeduplication(
      getDataDeserializer = { it.args.dataDeserializer },
      getVariablesSerializer = { DistinctVariablesSerializer(it.args.variablesSerializer) },
      verifyResults = { valuePrefix, _, _, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
      },
    )
  }

  @Test
  fun `execute() deduplicates identical queries, even with different variablesSerializersModule that produces same Struct`() {
    fun distinctSerializersModule(key: Int): SerializersModule = SerializersModule {
      contextual(String::class, HardcodedStringKSerializer(key.toString()))
    }
    check(distinctSerializersModule(1) != distinctSerializersModule(2))

    verifyExecuteDeduplication(
      getDataDeserializer = { it.args.dataDeserializer },
      getVariablesSerializersModule = { distinctSerializersModule(it.jobIndex) },
      verifyResults = { valuePrefix, _, _, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
      },
    )
  }

  @Test
  fun `execute() deduplicates identical queries, even with different callerSdkType`() =
    verifyExecuteDeduplication(
      getDataDeserializer = { it.args.dataDeserializer },
      getCallerSdkType = { CallerSdkType.entries.random(it.random) },
      verifyResults = { valuePrefix, _, _, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
      },
    )

  @Test
  fun `execute() deduplicates identical queries, even with different authToken`() {
    var dataConnectAuth: DataConnectAuth? = null
    verifyExecuteDeduplication(
      getDataDeserializer = { it.args.dataDeserializer },
      verifyResults = { valuePrefix, _, executeCount, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
        coVerify(exactly = executeCount) { dataConnectAuth!!.getToken(any()) }
      },
      newDataConnectAuth = {
        val authUid = Arb.dataConnect.authTokenResult().map { it.authUid }.bind()
        val authTokenResultArb = Arb.dataConnect.authTokenResult(authUid = Arb.constant(authUid))
        dataConnectAuth = mockk {
          coEvery { getToken(any()) } answers { authTokenResultArb.bind() }
        }
        dataConnectAuth
      }
    )
  }

  @Test
  fun `execute() deduplicates identical queries, even with different appCheckToken`() {
    var dataConnectAppCheck: DataConnectAppCheck? = null
    verifyExecuteDeduplication(
      getDataDeserializer = { it.args.dataDeserializer },
      verifyResults = { valuePrefix, _, executeCount, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
        coVerify(exactly = executeCount) { dataConnectAppCheck!!.getToken(any()) }
      },
      newDataConnectAppCheck = {
        val appCheckTokenResultArb = Arb.dataConnect.appCheckTokenResult()
        dataConnectAppCheck = mockk {
          coEvery { getToken(any()) } answers { appCheckTokenResultArb.bind() }
        }
        dataConnectAppCheck
      }
    )
  }

  private data class VerifyExecuteDeduplicationCallbackArguments(
    val valuePrefix: String,
    val valuePrefixOverride: String,
    val jobIndex: Int,
    val args: ExecuteArguments<TestData, TestVariables>,
    val random: Random,
  )

  private fun <Data> verifyExecuteDeduplication(
    getDataDeserializer:
      (args: VerifyExecuteDeduplicationCallbackArguments) -> DeserializationStrategy<Data>,
    getVariablesSerializer:
      (args: VerifyExecuteDeduplicationCallbackArguments) -> SerializationStrategy<TestVariables> =
      {
        it.args.variablesSerializer
      },
    getDataSerializersModule:
      (args: VerifyExecuteDeduplicationCallbackArguments) -> SerializersModule? =
      {
        it.args.dataSerializersModule
      },
    getVariablesSerializersModule:
      (args: VerifyExecuteDeduplicationCallbackArguments) -> SerializersModule? =
      {
        it.args.variablesSerializersModule
      },
    getCallerSdkType: (args: VerifyExecuteDeduplicationCallbackArguments) -> CallerSdkType = {
      it.args.callerSdkType
    },
    verifyResults:
      (valuePrefix: String, valuePrefixOverride: String, executeCount: Int, List<Data>) -> Unit,
    newDataConnectAuth: PropertyContext.() -> DataConnectAuth? = { null },
    newDataConnectAppCheck: PropertyContext.() -> DataConnectAppCheck? = { null },
    calculateExpectedExecuteQueryInvocationCount: (executeCount: Int) -> Int = { 2 },
    verifyExecuteQueryInvocations:
      (authTokens: List<String?>, appCheckTokens: List<String?>) -> Unit =
      { _, _ ->
      },
  ) = runTest {
    checkAll(
      propTestConfig.withIterations(5),
      executeArgumentsArb(),
      alphanumericStringArb().pair()
    ) { args, (valuePrefix, valuePrefixOverride) ->
      val callbackArgsTemplate =
        VerifyExecuteDeduplicationCallbackArguments(
          valuePrefix = valuePrefix,
          valuePrefixOverride = valuePrefixOverride,
          jobIndex = -1,
          args = args,
          random = randomSource().random,
        )
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
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(dataConnectGrpcRPCs)
        newDataConnectAuth()?.let { setDataConnectAuth(it) }
        newDataConnectAppCheck()?.let { setDataConnectAppCheck(it) }
      }

      val executeJobIndex = randomSource().random.nextInt(0 until latch.count)
      val jobs =
        List(latch.count) { jobIndex ->
          backgroundScope.async(Dispatchers.IO) {
            val callbackArgs = callbackArgsTemplate.copy(jobIndex = jobIndex)
            if (jobIndex != executeJobIndex) {
              latch.countDown().await()
            }
            queryManager.execute(
              operationName = args.operationName,
              variables = args.variables,
              dataDeserializer = getDataDeserializer(callbackArgs),
              variablesSerializer = getVariablesSerializer(callbackArgs),
              dataSerializersModule = getDataSerializersModule(callbackArgs),
              variablesSerializersModule = getVariablesSerializersModule(callbackArgs),
              callerSdkType = getCallerSdkType(callbackArgs),
              fetchPolicy = args.fetchPolicy,
            )
          }
        }

      val results = jobs.awaitAll()
      verifyResults(valuePrefix, valuePrefixOverride, jobs.size, results)
      val capturedAuthTokens = mutableListOf<String?>()
      val capturedAppCheckTokens = mutableListOf<String?>()
      val expectedExecuteQueryInvocationCount =
        calculateExpectedExecuteQueryInvocationCount(jobs.size)
      coVerify(exactly = expectedExecuteQueryInvocationCount) {
        dataConnectGrpcRPCs.executeQuery(
          any(),
          any(),
          captureNullable(capturedAuthTokens),
          captureNullable(capturedAppCheckTokens),
          any()
        )
      }
      verifyExecuteQueryInvocations(capturedAuthTokens.toList(), capturedAppCheckTokens.toList())
    }
  }

  @Test
  fun `execute() does NOT deduplicate queries with distinct authUid`() {
    val generatedAuthTokens = CopyOnWriteArrayList<String?>()

    verifyExecuteDeduplication(
      getDataDeserializer = { it.args.dataDeserializer },
      verifyResults = { valuePrefix, _, executeCount, results ->
        val values = results.map { it.value }
        val expectedValues = List(executeCount) { "$valuePrefix $it" }
        values shouldContainExactlyInAnyOrder expectedValues
      },
      newDataConnectAuth = {
        val distinctAuthUidArb = Arb.dataConnect.authTokenResult().map { it.authUid }.distinct()
        val authTokenResultArb = Arb.dataConnect.authTokenResult(authUid = distinctAuthUidArb)
        mockk {
          coEvery { getToken(any()) } answers
            {
              val authTokenResult = authTokenResultArb.bind()
              generatedAuthTokens.add(authTokenResult.token)
              authTokenResult
            }
        }
      },
      calculateExpectedExecuteQueryInvocationCount = { executeCount -> executeCount },
      verifyExecuteQueryInvocations = { authTokens, _ ->
        authTokens shouldContainExactlyInAnyOrder generatedAuthTokens.toList()
        generatedAuthTokens.clear()
      }
    )
  }

  private suspend fun PropertyContext.buildQueryManager(
    block: QueryManagerBuilder.() -> Unit
  ): QueryManager {
    val builder =
      QueryManagerBuilder(
        requestName = "requestName" + randomSource().random.nextAlphanumericString(10),
        dataConnectGrpcRPCs = mockk { stubExecuteQuery() },
        dataConnectAuth = mockDataConnectAuth(),
        dataConnectAppCheck = mockDataConnectAppCheck(),
        ioDispatcher = Dispatchers.IO,
        cpuDispatcher = Dispatchers.Default,
        secureRandom = randomSource().random,
        logger = newMockLogger("logger" + randomSource().random.nextAlphanumericString(10)),
      )
    builder.apply(block)
    val queryManager = builder.build()
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

private fun <NewData, Variables> ExecuteArguments<*, Variables>.withDataDeserializer(
  dataDeserializer: DeserializationStrategy<NewData>
): ExecuteArguments<NewData, Variables> =
  ExecuteArguments(
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    callerSdkType = callerSdkType,
    dataSerializersModule = dataSerializersModule,
    variablesSerializersModule = variablesSerializersModule,
    fetchPolicy = fetchPolicy,
  )

private fun <Data, NewVariables> ExecuteArguments<Data, *>.withVariables(
  variables: NewVariables,
  variablesSerializer: SerializationStrategy<NewVariables>
): ExecuteArguments<Data, NewVariables> =
  ExecuteArguments(
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    callerSdkType = callerSdkType,
    dataSerializersModule = dataSerializersModule,
    variablesSerializersModule = variablesSerializersModule,
    fetchPolicy = fetchPolicy,
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
  executeQueryRequestSlot: CapturingSlot<ExecuteQueryRequest> = slot(),
  authTokenSlot: CapturingSlot<String?> = slot(),
  appCheckTokenSlot: CapturingSlot<String?> = slot(),
  callerSdkTypeSlot: CapturingSlot<CallerSdkType> = slot(),
  executeQueryResponse: ExecuteQueryResponse? = null
) {
  coEvery {
    executeQuery(
      any(),
      capture(executeQueryRequestSlot),
      captureNullable(authTokenSlot),
      captureNullable(appCheckTokenSlot),
      capture(callerSdkTypeSlot)
    )
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

private fun mockDataConnectAuth(): DataConnectAuth =
  mockk(relaxed = true) { coEvery { getToken(any()) } returns null }

private fun mockDataConnectAppCheck(): DataConnectAppCheck =
  mockk(relaxed = true) { coEvery { getToken(any()) } returns null }

private suspend fun <Data, Variables> QueryManager.execute(
  args: ExecuteArguments<Data, Variables>
): Data =
  execute(
    operationName = args.operationName,
    variables = args.variables,
    dataDeserializer = args.dataDeserializer,
    variablesSerializer = args.variablesSerializer,
    dataSerializersModule = args.dataSerializersModule,
    variablesSerializersModule = args.variablesSerializersModule,
    callerSdkType = args.callerSdkType,
    fetchPolicy = args.fetchPolicy,
  )

private class QueryManagerBuilder(
  private var requestName: String,
  private var dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private var dataConnectAuth: DataConnectAuth,
  private var dataConnectAppCheck: DataConnectAppCheck,
  private var ioDispatcher: CoroutineDispatcher,
  private var cpuDispatcher: CoroutineDispatcher,
  private var secureRandom: Random,
  private var logger: Logger,
) {

  fun setRequestName(value: String) {
    requestName = value
  }

  fun setDataConnectGrpcRPCs(value: DataConnectGrpcRPCs) {
    dataConnectGrpcRPCs = value
  }

  fun setDataConnectAuth(value: DataConnectAuth) {
    dataConnectAuth = value
  }

  fun setDataConnectAppCheck(value: DataConnectAppCheck) {
    dataConnectAppCheck = value
  }

  fun setIoDispatcher(value: CoroutineDispatcher) {
    ioDispatcher = value
  }

  fun setSecureRandom(value: Random) {
    secureRandom = value
  }

  fun build(): QueryManager =
    QueryManager(
      requestName = requestName,
      dataConnectGrpcRPCs = dataConnectGrpcRPCs,
      dataConnectAuth = dataConnectAuth,
      dataConnectAppCheck = dataConnectAppCheck,
      ioDispatcher = ioDispatcher,
      cpuDispatcher = cpuDispatcher,
      secureRandom = secureRandom,
      logger = logger,
    )
}
