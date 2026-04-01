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

import com.google.firebase.dataconnect.CachedDataNotFoundException
import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef.FetchPolicy
import com.google.firebase.dataconnect.core.AuthUidChangedException
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
import com.google.firebase.dataconnect.testutil.property.arbitrary.requestIdGenerator
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterations
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.RequestIdGenerator
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.duration
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
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
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueryManagerUnitTest {

  @get:Rule val cleanups = CleanupsRule()
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun `execute() uses the requestName that was given to the constructor`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
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
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
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
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
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
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
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

      val result = queryManager.execute(args.copy(dataDeserializer = dataDeserializer))

      result.data shouldBe TestData(overrideValue)
    }
  }

  @Test
  fun `execute() uses the given variablesSerializer`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
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
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
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

      val result =
        queryManager.execute(
          args
            .withDataDeserializer(dataDeserializer)
            .copy(dataSerializersModule = dataSerializersModule)
        )

      result.data shouldBe ContextualTestData(overrideValue)
    }
  }

  @Test
  fun `execute() uses the given variablesSerializersModule`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
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
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
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
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
    ) { args ->
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
      checkAll(
        propTestConfig,
        executeArgumentsArb(FetchPolicy.SERVER_ONLY),
        authTokenResultArb,
      ) { args, authTokenResult ->
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
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
      authTokenResultArb,
    ) { args, authTokenResult ->
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
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
    ) { args ->
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
      checkAll(
        propTestConfig,
        executeArgumentsArb(FetchPolicy.SERVER_ONLY),
        appCheckTokenResultArb
      ) { args, appCheckTokenResult ->
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
      checkAll(
        propTestConfig,
        executeArgumentsArb(FetchPolicy.SERVER_ONLY),
        appCheckTokenResultArb,
      ) { args, appCheckTokenResult ->
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
  fun `execute() uses the given RequestIdGenerator`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
      Arb.dataConnect.requestId(),
    ) { args, requestId ->
      val requestIdGenerator: RequestIdGenerator = mockk {
        coEvery { nextQueryRequestId() } returns requestId
      }
      val grpcRPCsRequestIdSlot: CapturingSlot<String> = slot()
      val authRequestIdSlot: CapturingSlot<String> = slot()
      val appCheckRequestIdSlot: CapturingSlot<String> = slot()
      val queryManager: QueryManager = buildQueryManager {
        setRequestIdGenerator(requestIdGenerator)
        setDataConnectGrpcRPCs(mockk { stubExecuteQuery(requestIdSlot = grpcRPCsRequestIdSlot) })
        setDataConnectAuth(mockk { coEvery { getToken(capture(authRequestIdSlot)) } returns null })
        setDataConnectAppCheck(
          mockk { coEvery { getToken(capture(appCheckRequestIdSlot)) } returns null }
        )
      }

      queryManager.execute(args)

      assertSoftly {
        withClue("grpcRPCsRequestId") { grpcRPCsRequestIdSlot.captured shouldBe requestId }
        withClue("authRequestId") { authRequestIdSlot.captured shouldBe requestId }
        withClue("appCheckRequestId") { appCheckRequestIdSlot.captured shouldBe requestId }
      }
      coVerify(exactly = 1) { requestIdGenerator.nextQueryRequestId() }
    }
  }

  @Test
  fun `execute() deduplicates identical queries`() =
    verifyExecuteDeduplication(
      transformExecuteArguments = { it.args },
      verifyResults = { valuePrefix, _, _, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
      },
    )

  @Test
  fun `execute() deduplicates identical queries even with different dataDeserializer`() =
    verifyExecuteDeduplication(
      transformExecuteArguments = {
        it.args.withDataDeserializer(
          TestDataOverrideDeserializer("${it.valuePrefixOverride} ${it.jobIndex}")
        )
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
      transformExecuteArguments = {
        val dataSerializersModule = SerializersModule {
          contextual(
            String::class,
            HardcodedStringKSerializer("${it.valuePrefixOverride} ${it.jobIndex}")
          )
        }
        it.args
          .withDataDeserializer(dataDeserializer = dataDeserializer)
          .copy(dataSerializersModule = dataSerializersModule)
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
      transformExecuteArguments = {
        val variablesSerializer = DistinctVariablesSerializer(it.args.variablesSerializer)
        it.args.copy(variablesSerializer = variablesSerializer)
      },
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
      transformExecuteArguments = {
        val variablesSerializersModule = distinctSerializersModule(it.jobIndex)
        it.args.copy(variablesSerializersModule = variablesSerializersModule)
      },
      verifyResults = { valuePrefix, _, _, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
      },
    )
  }

  @Test
  fun `execute() deduplicates identical queries, even with different callerSdkType`() =
    verifyExecuteDeduplication(
      transformExecuteArguments = {
        val callerSdkType = CallerSdkType.entries.random(randomSource().random)
        it.args.copy(callerSdkType = callerSdkType)
      },
      verifyResults = { valuePrefix, _, _, results ->
        val values = results.map { it.value }
        values.distinct().shouldContainExactlyInAnyOrder("$valuePrefix 0", "$valuePrefix 1")
      },
    )

  @Test
  fun `execute() deduplicates identical queries, even with different authToken`() {
    var dataConnectAuth: DataConnectAuth? = null
    verifyExecuteDeduplication(
      transformExecuteArguments = { it.args },
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
      transformExecuteArguments = { it.args },
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

  @Test
  fun `execute() does NOT deduplicate queries with distinct authUid`() {
    val generatedAuthTokens = CopyOnWriteArrayList<String?>()

    verifyExecuteDeduplication(
      transformExecuteArguments = { it.args },
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
              val authTokenResult = authTokenResultArb.next(randomSource())
              generatedAuthTokens.add(authTokenResult.token)
              authTokenResult
            }
        }
      },
      calculateExpectedExecuteQueryInvocationCount = { executeCount -> executeCount },
      verifyExecuteQueryInvocations = { invocations ->
        val authTokens = invocations.map { it.authToken }
        authTokens shouldContainExactlyInAnyOrder generatedAuthTokens.toList()
        generatedAuthTokens.clear()
      }
    )
  }

  @Test
  fun `execute() does NOT deduplicate queries with distinct operationName`() {
    val generatedOperationNames = CopyOnWriteArrayList<String>()
    verifyExecuteDeduplication(
      transformExecuteArguments = {
        val operationName = Arb.constant("${it.args.operationName}${it.jobIndex}").bind()
        generatedOperationNames.add(operationName)
        it.args.copy(operationName = operationName)
      },
      verifyResults = { valuePrefix, _, executeCount, results ->
        val values = results.map { it.value }
        val expectedValues = List(executeCount) { "$valuePrefix $it" }
        values shouldContainExactlyInAnyOrder expectedValues
      },
      calculateExpectedExecuteQueryInvocationCount = { executeCount -> executeCount },
      verifyExecuteQueryInvocations = { invocations ->
        val operationNames = invocations.map { it.requestProto.operationName }
        operationNames shouldContainExactlyInAnyOrder generatedOperationNames.toList()
        generatedOperationNames.clear()
      }
    )
  }

  @Test
  fun `execute() does NOT deduplicate queries with distinct variables`() {
    val generatedVariables = CopyOnWriteArrayList<TestVariables>()
    verifyExecuteDeduplication(
      transformExecuteArguments = {
        val variables = it.args.variables.copy(value = "vars${it.jobIndex}")
        generatedVariables.add(variables)
        it.args.copy(variables = variables)
      },
      verifyResults = { valuePrefix, _, executeCount, results ->
        val values = results.map { it.value }
        val expectedValues = List(executeCount) { "$valuePrefix $it" }
        values shouldContainExactlyInAnyOrder expectedValues
      },
      calculateExpectedExecuteQueryInvocationCount = { executeCount -> executeCount },
      verifyExecuteQueryInvocations = { invocations ->
        val variables = invocations.map { it.requestProto.variables }
        variables shouldContainExactlyInAnyOrder generatedVariables.map { it.encodeToStruct() }
        generatedVariables.clear()
      }
    )
  }

  @Test
  fun `execute() on UNAUTHENTICATED error retries with new tokens`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
      alphanumericStringArb(),
    ) { args, responseString ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
        val invocationIndex = AtomicInteger(0)
        coEvery { executeQuery(any(), any(), any(), any(), any()) } answers
          {
            if (invocationIndex.getAndIncrement() == 0) {
              throw StatusException(Status.UNAUTHENTICATED)
            }
            responseString.encodeToExecuteQueryResponse()
          }
      }
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(dataConnectGrpcRPCs)
        setDataConnectAuth(
          mockk {
            val tokenIndex = AtomicInteger(1)
            coEvery { getToken(any()) } answers
              {
                DataConnectAuth.GetAuthTokenResult(
                  token = "authToken${tokenIndex.get()}",
                  authUid = "testAuthUid",
                )
              }
            every { forceRefresh() } answers { tokenIndex.incrementAndGet() }
          }
        )
        setDataConnectAppCheck(
          mockk {
            val tokenIndex = AtomicInteger(1)
            coEvery { getToken(any()) } answers
              {
                DataConnectAppCheck.GetAppCheckTokenResult(
                  token = "appCheckToken${tokenIndex.get()}",
                )
              }
            every { forceRefresh() } answers { tokenIndex.incrementAndGet() }
          }
        )
      }

      val result = queryManager.execute(args)

      result.data shouldBe TestData(responseString)
      val capturedRequestIds = mutableListOf<String>()
      val capturedRequestProtos = mutableListOf<ExecuteQueryRequest>()
      coVerifySequence {
        dataConnectGrpcRPCs.executeQuery(
          capture(capturedRequestIds),
          capture(capturedRequestProtos),
          eq("authToken1"),
          eq("appCheckToken1"),
          eq(args.callerSdkType)
        )
        dataConnectGrpcRPCs.executeQuery(
          capture(capturedRequestIds),
          capture(capturedRequestProtos),
          eq("authToken2"),
          eq("appCheckToken2"),
          eq(args.callerSdkType)
        )
      }
      assertSoftly {
        capturedRequestIds.distinct().shouldHaveSize(1)
        capturedRequestProtos.distinct().shouldHaveSize(1)
      }
    }
  }

  @Test
  fun `execute() on UNAUTHENTICATED error does NOT retry when tokens unchanged`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
      Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.33),
      Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.33),
    ) { args, authTokenResult, appCheckTokenResult ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
        coEvery { executeQuery(any(), any(), any(), any(), any()) } throws
          StatusException(Status.UNAUTHENTICATED)
      }
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(dataConnectGrpcRPCs)
        setDataConnectAuth(
          mockk(relaxed = true) { coEvery { getToken(any()) } returns authTokenResult }
        )
        setDataConnectAppCheck(
          mockk(relaxed = true) { coEvery { getToken(any()) } returns appCheckTokenResult }
        )
      }

      val statusException = shouldThrow<StatusException> { queryManager.execute(args) }

      statusException.status.code shouldBe Status.UNAUTHENTICATED.code
      coVerify(exactly = 1) { dataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any()) }
    }
  }

  @Test
  fun `execute() on UNAUTHENTICATED error throws if authUid changes`() = runTest {
    val authTokenWithDistinctAuthUidArb = run {
      val nextIndex = AtomicInteger(0)
      Arb.dataConnect.authTokenResult(
        accessToken = Arb.dataConnect.accessToken(),
        authUid = arbitrary { "authToken${nextIndex.incrementAndGet()}" },
      )
    }
    checkAll(
      propTestConfig,
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
      authTokenWithDistinctAuthUidArb.pair(),
      Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.33),
    ) { args, authTokenResults, appCheckTokenResult ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
        coEvery { executeQuery(any(), any(), any(), any(), any()) } throws
          StatusException(Status.UNAUTHENTICATED)
      }
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(dataConnectGrpcRPCs)
        setDataConnectAuth(
          mockk(relaxed = true) {
            coEvery { getToken(any()) } returnsMany (authTokenResults.toList())
          }
        )
        setDataConnectAppCheck(
          mockk(relaxed = true) { coEvery { getToken(any()) } returns appCheckTokenResult }
        )
      }

      val exception = shouldThrow<AuthUidChangedException> { queryManager.execute(args) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "x7md4h6atc"
        exception.message shouldContainWithNonAbuttingText authTokenResults.value1.authUid!!
        exception.message shouldContainWithNonAbuttingText authTokenResults.value2.authUid!!
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "authUid changed"
      }
      coVerify(exactly = 1) { dataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any()) }
    }
  }

  private data class VerifyExecuteDeduplicationCallbackArguments(
    val valuePrefix: String,
    val valuePrefixOverride: String,
    val jobIndex: Int,
    val args: ExecuteArguments<TestData, TestVariables>,
  )

  private fun <Data> verifyExecuteDeduplication(
    transformExecuteArguments:
      PropertyContext.(args: VerifyExecuteDeduplicationCallbackArguments) -> ExecuteArguments<
          Data, TestVariables
        >,
    verifyResults:
      (valuePrefix: String, valuePrefixOverride: String, executeCount: Int, List<Data>) -> Unit,
    newDataConnectAuth: PropertyContext.() -> DataConnectAuth? = { null },
    newDataConnectAppCheck: PropertyContext.() -> DataConnectAppCheck? = { null },
    calculateExpectedExecuteQueryInvocationCount: (executeCount: Int) -> Int = { 2 },
    verifyExecuteQueryInvocations: (List<ExecuteQueryArguments>) -> Unit = {},
  ) = runTest {
    checkAll(
      propTestConfig.withIterations(5),
      executeArgumentsArb(FetchPolicy.SERVER_ONLY),
      alphanumericStringArb().pair()
    ) { args, (valuePrefix, valuePrefixOverride) ->
      val callbackArgsTemplate =
        VerifyExecuteDeduplicationCallbackArguments(
          valuePrefix = valuePrefix,
          valuePrefixOverride = valuePrefixOverride,
          jobIndex = -1,
          args = args,
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
            val transformedArgs = transformExecuteArguments(callbackArgs)

            if (jobIndex != executeJobIndex) {
              latch.countDown().await()
            }

            queryManager.execute(transformedArgs)
          }
        }

      val results = jobs.awaitAll()
      verifyResults(valuePrefix, valuePrefixOverride, jobs.size, results.map { it.data })

      val capturedRequestIds = mutableListOf<String>()
      val capturedRequestProtos = mutableListOf<ExecuteQueryRequest>()
      val capturedAuthTokens = mutableListOf<String?>()
      val capturedAppCheckTokens = mutableListOf<String?>()
      val capturedCallerSdkTypes = mutableListOf<CallerSdkType>()
      val expectedExecuteQueryInvocationCount =
        calculateExpectedExecuteQueryInvocationCount(jobs.size)
      coVerify(exactly = expectedExecuteQueryInvocationCount) {
        dataConnectGrpcRPCs.executeQuery(
          capture(capturedRequestIds),
          capture(capturedRequestProtos),
          captureNullable(capturedAuthTokens),
          captureNullable(capturedAppCheckTokens),
          capture(capturedCallerSdkTypes),
        )
      }
      check(capturedRequestIds.size == expectedExecuteQueryInvocationCount)
      check(capturedRequestProtos.size == expectedExecuteQueryInvocationCount)
      check(capturedAuthTokens.size == expectedExecuteQueryInvocationCount)
      check(capturedAppCheckTokens.size == expectedExecuteQueryInvocationCount)
      check(capturedCallerSdkTypes.size == expectedExecuteQueryInvocationCount)
      val executeQueryInvocations =
        List(capturedRequestIds.size) {
          ExecuteQueryArguments(
            requestId = capturedRequestIds[it],
            requestProto = capturedRequestProtos[it],
            authToken = capturedAuthTokens[it],
            appCheckToken = capturedAppCheckTokens[it],
            callerSdkType = capturedCallerSdkTypes[it],
          )
        }
      verifyExecuteQueryInvocations(executeQueryInvocations)
    }
  }

  @Test
  fun `execute(cacheSettings=null, fetchPolicy=CACHE_ONLY) throws`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(fetchPolicy = FetchPolicy.CACHE_ONLY),
    ) { args ->
      val queryManager: QueryManager = buildQueryManager {}

      val exception = shouldThrow<CachedDataNotFoundException> { queryManager.execute(args) }

      assertSoftly {
        exception.message.let {
          it shouldContainWithNonAbuttingText "m35wype9dt"
          it shouldContainWithNonAbuttingText "CACHE_ONLY"
          it shouldContainWithNonAbuttingTextIgnoringCase "cache settings is null"
        }
      }
    }
  }

  @Test
  fun `execute(cacheSettings=null, fetchPolicy=SERVER_ONLY,PREFER_CACHE) fetches from server`() =
    runTest {
      checkAll(
        propTestConfig,
        executeArgumentsArb(
          fetchPolicy = Arb.of(FetchPolicy.SERVER_ONLY, FetchPolicy.PREFER_CACHE)
        ),
        testDataArb(),
      ) { args, testData ->
        val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
          stubExecuteQuery(executeQueryResponse = testData.encodeToExecuteQueryResponse())
        }
        val queryManager: QueryManager = buildQueryManager {
          setDataConnectGrpcRPCs(dataConnectGrpcRPCs)
        }

        val result = queryManager.execute(args)

        result shouldBe QueryManager.ExecuteResult(testData, DataSource.SERVER)
        coVerify(exactly = 1) {
          dataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any())
        }
      }
    }

  @Test
  fun `execute(fetchPolicy=CACHE_ONLY) with no cached results throws`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(fetchPolicy = FetchPolicy.CACHE_ONLY),
      cacheSettingsArb(),
    ) { args, cacheSettings ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk()
      val queryManager: QueryManager = buildQueryManager { setCacheSettings(cacheSettings) }

      val exception = shouldThrow<CachedDataNotFoundException> { queryManager.execute(args) }

      exception.messageShouldIndicateNoCachedResults()
      confirmVerified(dataConnectGrpcRPCs)
    }
  }

  @Test
  fun `execute(fetchPolicy=CACHE_ONLY) with stale results returns from cache`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
      cacheSettingsArb(maxAge = Arb.duration(Duration.ZERO..1.hours)),
      Arb.of(FetchPolicy.PREFER_CACHE, FetchPolicy.SERVER_ONLY),
      testDataArb(),
    ) { args, cacheSettings, fetchPolicy1, testData ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
        stubExecuteQuery(executeQueryResponse = testData.encodeToExecuteQueryResponse())
      }
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(dataConnectGrpcRPCs)
        setCacheSettings(cacheSettings)

        val timeStepRange = cacheSettings.maxAge.inWholeMilliseconds.let { it..(it + 1) }
        setCurrentTimeMillis(
          currentTimeMillisFuncArb(
              start = Long.MIN_VALUE..(Long.MAX_VALUE - (timeStepRange.last * 2)),
              step = timeStepRange,
            )
            .bind()
        )
      }
      queryManager.execute(args.copy(fetchPolicy = fetchPolicy1)) // populate cache

      val result = queryManager.execute(args.copy(fetchPolicy = FetchPolicy.CACHE_ONLY))

      result shouldBe QueryManager.ExecuteResult(testData, DataSource.CACHE)
      coVerify(exactly = 1) { dataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any()) }
    }
  }

  @Test
  fun `execute(fetchPolicy=CACHE_ONLY,PREFER_CACHE) with fresh results returns from cache`() =
    runTest {
      checkAll(
        propTestConfig,
        executeArgumentsArb(),
        cacheSettingsArb(maxAge = Arb.duration(1.seconds..1.hours)),
        Arb.of(FetchPolicy.PREFER_CACHE, FetchPolicy.SERVER_ONLY),
        Arb.of(FetchPolicy.PREFER_CACHE, FetchPolicy.CACHE_ONLY),
        testDataArb(),
      ) { args, cacheSettings, fetchPolicy1, fetchPolicy2, testData ->
        val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
          stubExecuteQuery(executeQueryResponse = testData.encodeToExecuteQueryResponse())
        }
        val queryManager: QueryManager = buildQueryManager {
          setDataConnectGrpcRPCs(dataConnectGrpcRPCs)
          setCacheSettings(cacheSettings)

          val timeStepRange = cacheSettings.maxAge.inWholeMilliseconds.let { 0L until (it / 2) }
          setCurrentTimeMillis(
            currentTimeMillisFuncArb(
                start = Long.MIN_VALUE..(Long.MAX_VALUE - (timeStepRange.last * 2)),
                step = timeStepRange,
              )
              .bind()
          )
        }
        queryManager.execute(args.copy(fetchPolicy = fetchPolicy1)) // populate cache

        val result = queryManager.execute(args.copy(fetchPolicy = fetchPolicy2))

        result shouldBe QueryManager.ExecuteResult(testData, DataSource.CACHE)
        coVerify(exactly = 1) {
          dataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any())
        }
      }
    }

  @Test
  fun `execute(fetchPolicy=PREFER_CACHE) with stale results returns from server`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(),
      cacheSettingsArb(maxAge = Arb.duration(Duration.ZERO..1.hours)),
      Arb.of(FetchPolicy.PREFER_CACHE, FetchPolicy.SERVER_ONLY),
      Arb.list(testDataArb(), 2..5),
    ) { args, cacheSettings, fetchPolicy1, testDataList ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
        coEvery { executeQuery(any(), any(), any(), any(), any()) } returnsMany
          testDataList.map { it.encodeToExecuteQueryResponse() }
      }
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(dataConnectGrpcRPCs)
        setCacheSettings(cacheSettings)

        val timeStepRange = cacheSettings.maxAge.inWholeMilliseconds.let { it..(it + 1) }
        setCurrentTimeMillis(
          currentTimeMillisFuncArb(
              start =
                Long.MIN_VALUE..(Long.MAX_VALUE - (timeStepRange.last * 2 * testDataList.size)),
              step = timeStepRange,
            )
            .bind()
        )
      }
      queryManager.execute(args.copy(fetchPolicy = fetchPolicy1)) // populate cache

      val results =
        List(testDataList.size - 1) {
          queryManager.execute(args.copy(fetchPolicy = FetchPolicy.PREFER_CACHE))
        }

      results shouldContainExactly
        testDataList.drop(1).map { QueryManager.ExecuteResult(it, DataSource.SERVER) }
      coVerify(exactly = testDataList.size) {
        dataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any())
      }
    }
  }

  @Test
  fun `execute(fetchPolicy=SERVER_ONLY) always returns from server`() = runTest {
    checkAll(
      propTestConfig,
      executeArgumentsArb(fetchPolicy = FetchPolicy.SERVER_ONLY),
      cacheSettingsArb(),
      Arb.dataConnect.requestName(),
      Arb.list(testDataArb(), 1..5),
    ) { args, cacheSettings, requestName, testDataList ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
        val responses = testDataList.map { it.encodeToExecuteQueryResponse() }
        coEvery { executeQuery(any(), any(), any(), any(), any()) } returnsMany responses
      }
      val queryManager: QueryManager = buildQueryManager {
        setDataConnectGrpcRPCs(dataConnectGrpcRPCs)
        setCacheSettings(cacheSettings)
        setRequestName(requestName)
      }

      val results = List(testDataList.size) { queryManager.execute(args) }

      val expectedResults = testDataList.map { QueryManager.ExecuteResult(it, DataSource.SERVER) }
      results shouldContainExactly expectedResults
      coVerify(exactly = testDataList.size) {
        val requestProto = eq(args.encodeToExecuteQueryRequest(requestName))
        dataConnectGrpcRPCs.executeQuery(any(), requestProto, any(), any(), any())
      }
      confirmVerified(dataConnectGrpcRPCs)
    }
  }

  private fun cacheFileArb(): Arb<File> = arbitrary {
    File(temporaryFolder.newFolder(), "db.sqlite")
  }

  private fun cacheSettingsArb(
    file: Arb<File?> = cacheFileArb().orNull(nullProbability = 0.2),
    maxAge: Arb<Duration> = cacheSettingsDurationArb(),
  ): Arb<QueryManager.CacheSettings> = Arb.bind(file, maxAge, QueryManager::CacheSettings)

  private fun cacheSettingsArb(maxAge: Duration): Arb<QueryManager.CacheSettings> =
    cacheSettingsArb(maxAge = Arb.constant(maxAge))

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
        requestIdGenerator = Arb.dataConnect.requestIdGenerator().next(randomSource()),
        cacheSettings = null,
        currentTimeMillis = System::currentTimeMillis,
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
  val fetchPolicy: FetchPolicy,
)

private data class ExecuteQueryArguments(
  val requestId: String,
  val requestProto: ExecuteQueryRequest,
  val authToken: String?,
  val appCheckToken: String?,
  val callerSdkType: CallerSdkType,
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

private fun TestVariables.encodeToExecuteQueryRequest(
  requestName: String,
  operationName: String
): ExecuteQueryRequest =
  ExecuteQueryRequest.newBuilder()
    .setVariables(encodeToStruct())
    .setName(requestName)
    .setOperationName(operationName)
    .build()

private fun ExecuteArguments<*, TestVariables>.encodeToExecuteQueryRequest(
  requestName: String
): ExecuteQueryRequest =
  variables.encodeToExecuteQueryRequest(requestName = requestName, operationName = operationName)

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
  operationName: Arb<String> = operationNameArb(),
  variables: Arb<TestVariables> = testVariablesArb(),
  dataDeserializer: Arb<DeserializationStrategy<TestData>> = Arb.constant(serializer()),
  variablesSerializer: Arb<SerializationStrategy<TestVariables>> = Arb.constant(serializer()),
  callerSdkType: Arb<CallerSdkType> = Arb.enum(),
  dataSerializersModule: Arb<SerializersModule?> =
    Arb.mock<SerializersModule>().orNull(nullProbability = 0.5),
  variablesSerializersModule: Arb<SerializersModule?> =
    Arb.mock<SerializersModule>().orNull(nullProbability = 0.5),
  fetchPolicy: Arb<FetchPolicy> = Arb.enum(),
): Arb<ExecuteArguments<TestData, TestVariables>> =
  Arb.bind(
    operationName,
    variables,
    dataDeserializer,
    variablesSerializer,
    callerSdkType,
    dataSerializersModule,
    variablesSerializersModule,
    fetchPolicy,
    ::ExecuteArguments,
  )

private fun executeArgumentsArb(
  fetchPolicy: FetchPolicy
): Arb<ExecuteArguments<TestData, TestVariables>> =
  executeArgumentsArb(fetchPolicy = Arb.constant(fetchPolicy))

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
  requestIdSlot: CapturingSlot<String> = slot(),
  executeQueryRequestSlot: CapturingSlot<ExecuteQueryRequest> = slot(),
  authTokenSlot: CapturingSlot<String?> = slot(),
  appCheckTokenSlot: CapturingSlot<String?> = slot(),
  callerSdkTypeSlot: CapturingSlot<CallerSdkType> = slot(),
  executeQueryResponse: ExecuteQueryResponse? = null
) {
  coEvery {
    executeQuery(
      capture(requestIdSlot),
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

private fun mockDataConnectAuth(): DataConnectAuth =
  mockk(relaxed = true) { coEvery { getToken(any()) } returns null }

private fun mockDataConnectAppCheck(): DataConnectAppCheck =
  mockk(relaxed = true) { coEvery { getToken(any()) } returns null }

private suspend fun <Data, Variables> QueryManager.execute(
  args: ExecuteArguments<Data, Variables>
): QueryManager.ExecuteResult<Data> =
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
  private var requestIdGenerator: RequestIdGenerator,
  private var cacheSettings: QueryManager.CacheSettings?,
  private var currentTimeMillis: () -> Long,
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

  fun setRequestIdGenerator(value: RequestIdGenerator) {
    requestIdGenerator = value
  }

  fun setCacheSettings(value: QueryManager.CacheSettings?) {
    cacheSettings = value
  }

  fun setCurrentTimeMillis(value: () -> Long) {
    currentTimeMillis = value
  }

  fun build(): QueryManager =
    QueryManager(
      requestName = requestName,
      dataConnectGrpcRPCs = dataConnectGrpcRPCs,
      dataConnectAuth = dataConnectAuth,
      dataConnectAppCheck = dataConnectAppCheck,
      ioDispatcher = ioDispatcher,
      cpuDispatcher = cpuDispatcher,
      requestIdGenerator = requestIdGenerator,
      cacheSettings = cacheSettings,
      currentTimeMillis = currentTimeMillis,
      logger = logger,
    )
}

private fun cacheSettingsDurationArb(): Arb<Duration> = Arb.duration(0.seconds..Duration.INFINITE)

private fun Exception.messageShouldIndicateNoCachedResults() {
  assertSoftly {
    message.let {
      it shouldContainWithNonAbuttingText "xz3fvh9r39"
      it shouldContainWithNonAbuttingText "CACHE_ONLY"
      it shouldContainWithNonAbuttingTextIgnoringCase "no cached results for query"
    }
  }
}

private fun currentTimeMillisFunc(start: Long, step: Long): () -> Long {
  require(step >= 0) { "invalid step: $step" }
  val nextTime = AtomicLong(start)
  return { nextTime.getAndAdd(step) }
}

private fun currentTimeMillisFuncArb(start: LongRange, step: LongRange): Arb<(() -> Long)> =
  currentTimeMillisFuncArb(start = Arb.long(start), step = Arb.long(step))

private fun currentTimeMillisFuncArb(
  start: Arb<Long> = Arb.long(Long.MIN_VALUE..(Long.MAX_VALUE - 1000)),
  step: Arb<Long> = Arb.long(0L..10L),
): Arb<CurrentTimeMillisFunction> = Arb.bind(start, step, ::CurrentTimeMillisFunction)

private data class CurrentTimeMillisFunction(
  val start: Long,
  val step: Long,
) : (() -> Long) {
  private val func = currentTimeMillisFunc(start, step)
  override operator fun invoke(): Long = func()
}
