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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.OperationRefConstructorArguments
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.mock
import com.google.firebase.dataconnect.testutil.property.arbitrary.mutationRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationRefConstructorArguments
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.queryRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.shouldHavePropertiesEqualTo
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Test

@ExperimentalKotest
@Suppress("ReplaceCallWithBinaryOperator")
class QueryRefImplUnitTest {

  @Serializable private data class TestData(val foo: String)
  private interface TestVariables
  private interface TestData2
  private interface TestVariables2

  @Test
  fun `constructor should initialize properties to the given objects`() = runTest {
    val argsArb = Arb.dataConnect.operationRefConstructorArguments<TestData, TestVariables>()
    checkAll(propTestConfig, argsArb) { args ->
      val queryRefImpl =
        QueryRefImpl(
          dataConnect = args.dataConnect,
          operationName = args.operationName,
          variables = args.variables,
          dataDeserializer = args.dataDeserializer,
          variablesSerializer = args.variablesSerializer,
          callerSdkType = args.callerSdkType,
          dataSerializersModule = args.dataSerializersModule,
          variablesSerializersModule = args.variablesSerializersModule,
        )

      queryRefImpl.shouldHavePropertiesEqualTo(args)
    }
  }

  @Test
  fun `execute() returns the result on success`() = runTest {
    val data = Arb.dataConnect.testData().next()
    val dataConnect = dataConnectWithQueryResult(Result.success(data))
    val queryRefImpl = Arb.dataConnect.queryRefImpl(dataConnect).next()

    val queryResult = queryRefImpl.execute()

    assertSoftly {
      queryResult.ref shouldBeSameInstanceAs queryRefImpl
      queryResult.data shouldBe data
    }
  }

  @Test
  fun `execute() re-throws the exception on failure`() = runTest {
    class TestException : Exception("forced exception zphgcrkp9n")
    val testException = TestException()
    val dataConnect = dataConnectWithQueryResult(Result.failure(testException))
    val queryRefImpl = Arb.dataConnect.queryRefImpl(dataConnect).next()

    val exception = shouldThrow<TestException> { queryRefImpl.execute() }

    exception shouldBe testException
  }

  @Test
  fun `execute() calls QueryManager with the correct arguments`() = runTest {
    val data = Arb.dataConnect.testData().next()
    val queryRefSlot: CapturingSlot<QueryRefImpl<TestData, TestVariables>> = slot()
    val dataConnect =
      dataConnectWithQueryResult(
        Result.success(data),
        queryRefSlot = queryRefSlot,
      )
    val queryRefImpl = Arb.dataConnect.queryRefImpl(dataConnect).next()

    queryRefImpl.execute()

    queryRefSlot.captured shouldBeSameInstanceAs queryRefImpl
  }

  @Test
  fun `hashCode() should return the same value when invoked repeatedly`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl ->
      val hashCode = queryRefImpl.hashCode()
      repeat(3) { queryRefImpl.hashCode() shouldBe hashCode }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal, objects`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl ->
        queryRefImpl.hashCode() shouldBe queryRefImpl.copy().hashCode()
      }
    }

  @Test
  fun `hashCode() should incorporate dataConnect`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.mock<FirebaseDataConnectInternal>()
    ) { queryRefImpl1, newDataConnect ->
      assume(queryRefImpl1.dataConnect.hashCode() != newDataConnect.hashCode())
      val queryRefImpl2 = queryRefImpl1.withDataConnect(newDataConnect)
      queryRefImpl1.hashCode() shouldNotBe queryRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate operationName`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.string()) {
      queryRefImpl1,
      newOperationName ->
      assume(queryRefImpl1.operationName.hashCode() != newOperationName.hashCode())
      val queryRefImpl2 = queryRefImpl1.copy(operationName = newOperationName)
      queryRefImpl1.hashCode() shouldNotBe queryRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate variables`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.mock<TestVariables>()) {
      queryRefImpl1,
      newVariables ->
      assume(queryRefImpl1.variables.hashCode() != newVariables.hashCode())
      val queryRefImpl2 = queryRefImpl1.copy(variables = newVariables)
      queryRefImpl1.hashCode() shouldNotBe queryRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate dataDeserializer`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.mock<DeserializationStrategy<TestData>>()
    ) { queryRefImpl1, newDataDeserializer ->
      assume(queryRefImpl1.dataDeserializer.hashCode() != newDataDeserializer.hashCode())
      val queryRefImpl2 = queryRefImpl1.copy(dataDeserializer = newDataDeserializer)
      queryRefImpl1.hashCode() shouldNotBe queryRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializer`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.mock<SerializationStrategy<TestVariables>>()
    ) { queryRefImpl1, newVariablesSerializer ->
      assume(queryRefImpl1.variablesSerializer.hashCode() != newVariablesSerializer.hashCode())
      val queryRefImpl2 = queryRefImpl1.copy(variablesSerializer = newVariablesSerializer)
      queryRefImpl1.hashCode() shouldNotBe queryRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate callerSdkType`() = runTest {
    // Increase the `maxDiscardPercentage` because it's default value is 10%, but roughly 50% will
    // be discarded because there are only two distinct values for `callerSdkType`.
    checkAll(
      propTestConfig.copy(maxDiscardPercentage = 70),
      Arb.dataConnect.queryRefImpl(),
      Arb.enum<CallerSdkType>()
    ) { queryRefImpl1, newCallerSdkType ->
      assume(queryRefImpl1.callerSdkType.hashCode() != newCallerSdkType.hashCode())
      val queryRefImpl2 = queryRefImpl1.copy(callerSdkType = newCallerSdkType)
      queryRefImpl1.hashCode() shouldNotBe queryRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate dataSerializersModule`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.serializersModule()) {
      queryRefImpl1,
      newDataSerializersModule ->
      assume(queryRefImpl1.dataSerializersModule.hashCode() != newDataSerializersModule.hashCode())
      val queryRefImpl2 = queryRefImpl1.copy(dataSerializersModule = newDataSerializersModule)
      queryRefImpl1.hashCode() shouldNotBe queryRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializersModule`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.serializersModule()) {
      queryRefImpl1,
      newVariablesSerializersModule ->
      assume(
        queryRefImpl1.variablesSerializersModule.hashCode() !=
          newVariablesSerializersModule.hashCode()
      )
      val queryRefImpl2 =
        queryRefImpl1.copy(variablesSerializersModule = newVariablesSerializersModule)
      queryRefImpl1.hashCode() shouldNotBe queryRefImpl2.hashCode()
    }
  }

  @Test
  fun `equals(this) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl ->
      queryRefImpl.equals(queryRefImpl) shouldBe true
    }
  }

  @Test
  fun `equals(equal, but distinct, instance) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl1 ->
      val queryRefImpl2 = queryRefImpl1.copy()
      queryRefImpl1.equals(queryRefImpl2) shouldBe true
    }
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl ->
      queryRefImpl.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val othersArb =
      Arb.choice(
        Arb.string(),
        Arb.int(),
        Arb.dataConnect.operationRefImpl<Nothing, TestVariables>(),
        Arb.dataConnect.mutationRefImpl<Nothing, TestVariables>(),
      )
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), othersArb) { queryRefImpl, other ->
      queryRefImpl.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataConnect differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.mock<FirebaseDataConnectInternal>()
    ) { queryRefImpl1, newDataConnect ->
      assume(queryRefImpl1.dataConnect != newDataConnect)
      val queryRefImpl2 = queryRefImpl1.withDataConnect(newDataConnect)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only operationName differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.string()) {
      queryRefImpl1,
      newOperationName ->
      assume(queryRefImpl1.operationName != newOperationName)
      val queryRefImpl2 = queryRefImpl1.copy(operationName = newOperationName)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variables differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.mock<TestVariables>()) {
      queryRefImpl1,
      newVariables ->
      assume(queryRefImpl1.variables != newVariables)
      val queryRefImpl2 = queryRefImpl1.copy(variables = newVariables)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataDeserializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.mock<DeserializationStrategy<TestData>>()
    ) { queryRefImpl1, newDataDeserializer ->
      assume(queryRefImpl1.dataDeserializer != newDataDeserializer)
      val queryRefImpl2 = queryRefImpl1.copy(dataDeserializer = newDataDeserializer)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.mock<SerializationStrategy<TestVariables>>()
    ) { queryRefImpl1, newVariablesSerializer ->
      assume(queryRefImpl1.variablesSerializer != newVariablesSerializer)
      val queryRefImpl2 = queryRefImpl1.copy(variablesSerializer = newVariablesSerializer)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only callerSdkType differs`() = runTest {
    // Increase the `maxDiscardPercentage` because it's default value is 10%, but roughly 50% will
    // be discarded because there are only two distinct values for `callerSdkType`.
    checkAll(
      propTestConfig.copy(maxDiscardPercentage = 70),
      Arb.dataConnect.queryRefImpl(),
      Arb.enum<CallerSdkType>()
    ) { queryRefImpl1, newCallerSdkType ->
      assume(queryRefImpl1.callerSdkType != newCallerSdkType)
      val queryRefImpl2 = queryRefImpl1.copy(callerSdkType = newCallerSdkType)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataSerializersModule differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.serializersModule()) {
      queryRefImpl1,
      newDataSerializersModule ->
      assume(queryRefImpl1.dataSerializersModule != newDataSerializersModule)
      val queryRefImpl2 = queryRefImpl1.copy(dataSerializersModule = newDataSerializersModule)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializersModule differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.serializersModule()) {
      queryRefImpl1,
      newVariablesSerializersModule ->
      assume(queryRefImpl1.variablesSerializersModule != newVariablesSerializersModule)
      val queryRefImpl2 =
        queryRefImpl1.copy(variablesSerializersModule = newVariablesSerializersModule)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `toString() should incorporate the string representations of public properties`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl ->
      val toStringResult = queryRefImpl.toString()
      assertSoftly {
        withClue("shouldStartWith") { toStringResult shouldStartWith "QueryRefImpl(" }
        withClue("shouldEndWith") { toStringResult shouldEndWith ")" }
        withClue("dataConnect") {
          toStringResult shouldContainWithNonAbuttingText "dataConnect=${queryRefImpl.dataConnect}"
        }
        withClue("operationName") {
          toStringResult shouldContainWithNonAbuttingText
            "operationName=${queryRefImpl.operationName}"
        }
        withClue("variables") {
          toStringResult shouldContainWithNonAbuttingText "variables=${queryRefImpl.variables}"
        }
        withClue("dataDeserializer") {
          toStringResult shouldContainWithNonAbuttingText
            "dataDeserializer=${queryRefImpl.dataDeserializer}"
        }
        withClue("variablesSerializer") {
          toStringResult shouldContainWithNonAbuttingText
            "variablesSerializer=${queryRefImpl.variablesSerializer}"
        }
        withClue("callerSdkType") {
          toStringResult shouldContainWithNonAbuttingText
            "callerSdkType=${queryRefImpl.callerSdkType}"
        }
        withClue("dataSerializersModule") {
          toStringResult shouldContainWithNonAbuttingText
            "dataSerializersModule=${queryRefImpl.dataSerializersModule}"
        }
        withClue("variablesSerializersModule") {
          toStringResult shouldContainWithNonAbuttingText
            "variablesSerializersModule=${queryRefImpl.variablesSerializersModule}"
        }
      }
    }
  }

  @Test
  fun `copy() with no arguments should return an equal, but distinct, object`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl1 ->
      val queryRefImpl2 = queryRefImpl1.copy()
      queryRefImpl2 shouldNotBeSameInstanceAs queryRefImpl1
      queryRefImpl2.shouldHavePropertiesEqualTo(queryRefImpl1)
    }
  }

  @Test
  fun `copy() with all arguments should return an object with its properties set to the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.queryRefImpl(),
        Arb.dataConnect.operationRefConstructorArguments<TestData?, TestVariables>()
      ) { queryRefImpl1, newValues ->
        val queryRefImpl2 =
          queryRefImpl1.copy(
            operationName = newValues.operationName,
            variables = newValues.variables,
            dataDeserializer = newValues.dataDeserializer,
            variablesSerializer = newValues.variablesSerializer,
            callerSdkType = newValues.callerSdkType,
            dataSerializersModule = newValues.dataSerializersModule,
            variablesSerializersModule = newValues.variablesSerializersModule,
          )

        queryRefImpl2 shouldNotBeSameInstanceAs queryRefImpl1
        queryRefImpl2.shouldHavePropertiesEqualTo(
          newValues.copy(dataConnect = queryRefImpl1.dataConnect)
        )
      }
    }

  @Test
  fun `withVariablesSerializer() with only required arguments should return an equal object, except for the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.queryRefImpl(),
        Arb.mock<TestVariables2>(),
        Arb.mock<SerializationStrategy<TestVariables2>>()
      ) { queryRefImpl1, newVariables, newVariablesSerializer ->
        val queryRefImpl2 =
          queryRefImpl1.withVariablesSerializer(
            variables = newVariables,
            variablesSerializer = newVariablesSerializer,
          )

        val expected =
          OperationRefConstructorArguments(queryRefImpl1)
            .withVariablesSerializer(newVariables, newVariablesSerializer)
        queryRefImpl2.shouldHavePropertiesEqualTo(expected)
      }
    }

  @Test
  fun `withVariablesSerializer() with all arguments should return an equal object, except for the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.queryRefImpl(),
        Arb.mock<TestVariables2>(),
        Arb.mock<SerializationStrategy<TestVariables2>>(),
        Arb.mock<SerializersModule>()
      ) { queryRefImpl1, newVariables, newVariablesSerializer, newVariablesSerializersModule ->
        val queryRefImpl2 =
          queryRefImpl1.withVariablesSerializer(
            variables = newVariables,
            variablesSerializer = newVariablesSerializer,
            variablesSerializersModule = newVariablesSerializersModule,
          )

        val expected =
          OperationRefConstructorArguments(queryRefImpl1)
            .withVariablesSerializer(newVariables, newVariablesSerializer)
            .copy(variablesSerializersModule = newVariablesSerializersModule)
        queryRefImpl2.shouldHavePropertiesEqualTo(expected)
      }
    }

  @Test
  fun `withDataDeserializer() with only required arguments should return an equal object, except for the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.queryRefImpl(),
        Arb.mock<DeserializationStrategy<TestData2>>()
      ) { queryRefImpl1, newDataDeserializer ->
        val queryRefImpl2 =
          queryRefImpl1.withDataDeserializer(dataDeserializer = newDataDeserializer)

        val expected =
          OperationRefConstructorArguments(queryRefImpl1).withDataDeserializer(newDataDeserializer)
        queryRefImpl2.shouldHavePropertiesEqualTo(expected)
      }
    }

  @Test
  fun `withDataDeserializer() with all arguments should return an equal object, except for the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.queryRefImpl(),
        Arb.mock<DeserializationStrategy<TestData2>>(),
        Arb.mock<SerializersModule>()
      ) { queryRefImpl1, newDataDeserializer, newDataSerializersModule ->
        val queryRefImpl2 =
          queryRefImpl1.withDataDeserializer(
            dataDeserializer = newDataDeserializer,
            dataSerializersModule = newDataSerializersModule
          )

        val expected =
          OperationRefConstructorArguments(queryRefImpl1)
            .withDataDeserializer(newDataDeserializer)
            .copy(dataSerializersModule = newDataSerializersModule)
        queryRefImpl2.shouldHavePropertiesEqualTo(expected)
      }
    }

  private companion object {
    val propTestConfig =
      PropTestConfig(
        iterations = 100,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
      )

    fun DataConnectArb.testData(string: Arb<String> = string()): Arb<TestData> = arbitrary {
      TestData(string.bind())
    }

    fun DataConnectArb.queryRefImpl(): Arb<QueryRefImpl<TestData?, TestVariables>> =
      queryRefImpl(
        variables = Arb.mock(),
        dataDeserializer = arbitrary { serializer() },
        variablesSerializer = Arb.mock(),
      )

    fun DataConnectArb.queryRefImpl(
      dataConnect: FirebaseDataConnectInternal
    ): Arb<QueryRefImpl<TestData?, TestVariables>> =
      queryRefImpl().map { it.withDataConnect(dataConnect) }

    fun dataConnectWithQueryResult(
      result: Result<TestData>,
      queryRefSlot: CapturingSlot<QueryRefImpl<TestData, TestVariables>> = slot(),
    ): FirebaseDataConnectInternal =
      mockk<FirebaseDataConnectInternal>(relaxed = true) {
        every { getQueryManager() } returns
          mockk<QueryManager> {
            coEvery { execute(capture(queryRefSlot)) } answers { result.getOrThrow() }
          }
      }
  }
}
