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
class MutationRefImplUnitTest {

  @Serializable private data class TestData(val foo: String)
  private interface TestVariables
  private interface TestData2
  private interface TestVariables2

  @Test
  fun `constructor should initialize properties to the given objects`() = runTest {
    val argsArb = Arb.dataConnect.operationRefConstructorArguments<TestData, TestVariables>()
    checkAll(propTestConfig, argsArb) { args ->
      val mutationRefImpl =
        MutationRefImpl(
          dataConnect = args.dataConnect,
          operationName = args.operationName,
          variables = args.variables,
          dataDeserializer = args.dataDeserializer,
          variablesSerializer = args.variablesSerializer,
          callerSdkType = args.callerSdkType,
          dataSerializersModule = args.dataSerializersModule,
          variablesSerializersModule = args.variablesSerializersModule,
        )

      mutationRefImpl.shouldHavePropertiesEqualTo(args)
    }
  }

  @Test
  fun `execute() returns the result on success`() = runTest {
    val data = Arb.dataConnect.testData().next()
    val dataConnect = dataConnectWithMutationResult(Result.success(data))
    val mutationRefImpl = Arb.dataConnect.mutationRefImpl(dataConnect).next()

    val mutationResult = mutationRefImpl.execute()

    assertSoftly {
      mutationResult.ref shouldBeSameInstanceAs mutationRefImpl
      mutationResult.data shouldBe data
    }
  }

  @Test
  fun `execute() re-throws the exception on failure`() = runTest {
    class TestException : Exception("forced exception zphgcrkp9n")
    val testException = TestException()
    val dataConnect = dataConnectWithMutationResult(Result.failure(testException))
    val mutationRefImpl = Arb.dataConnect.mutationRefImpl(dataConnect).next()

    val exception = shouldThrow<TestException> { mutationRefImpl.execute() }

    exception shouldBe testException
  }

  @Test
  fun `execute() calls MutationManager with the correct arguments`() = runTest {
    val data = Arb.dataConnect.testData().next()
    val mutationRefSlot: CapturingSlot<MutationRefImpl<TestData, TestVariables>> = slot()
    val dataConnect =
      dataConnectWithMutationResult(
        Result.success(data),
        mutationRefSlot = mutationRefSlot,
      )
    val mutationRefImpl = Arb.dataConnect.mutationRefImpl(dataConnect).next()

    mutationRefImpl.execute()

    mutationRefSlot.captured shouldBeSameInstanceAs mutationRefImpl
  }

  @Test
  fun `hashCode() should return the same value when invoked repeatedly`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl ->
      val hashCode = mutationRefImpl.hashCode()
      repeat(3) { mutationRefImpl.hashCode() shouldBe hashCode }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal, objects`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl ->
        mutationRefImpl.hashCode() shouldBe mutationRefImpl.copy().hashCode()
      }
    }

  @Test
  fun `hashCode() should incorporate dataConnect`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.mock<FirebaseDataConnectInternal>()
    ) { mutationRefImpl1, newDataConnect ->
      assume(mutationRefImpl1.dataConnect.hashCode() != newDataConnect.hashCode())
      val mutationRefImpl2 = mutationRefImpl1.withDataConnect(newDataConnect)
      mutationRefImpl1.hashCode() shouldNotBe mutationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate operationName`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.string()) {
      mutationRefImpl1,
      newOperationName ->
      assume(mutationRefImpl1.operationName.hashCode() != newOperationName.hashCode())
      val mutationRefImpl2 = mutationRefImpl1.copy(operationName = newOperationName)
      mutationRefImpl1.hashCode() shouldNotBe mutationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate variables`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.mock<TestVariables>()) {
      mutationRefImpl1,
      newVariables ->
      assume(mutationRefImpl1.variables.hashCode() != newVariables.hashCode())
      val mutationRefImpl2 = mutationRefImpl1.copy(variables = newVariables)
      mutationRefImpl1.hashCode() shouldNotBe mutationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate dataDeserializer`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.mock<DeserializationStrategy<TestData>>()
    ) { mutationRefImpl1, newDataDeserializer ->
      assume(mutationRefImpl1.dataDeserializer.hashCode() != newDataDeserializer.hashCode())
      val mutationRefImpl2 = mutationRefImpl1.copy(dataDeserializer = newDataDeserializer)
      mutationRefImpl1.hashCode() shouldNotBe mutationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializer`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.mock<SerializationStrategy<TestVariables>>()
    ) { mutationRefImpl1, newVariablesSerializer ->
      assume(mutationRefImpl1.variablesSerializer.hashCode() != newVariablesSerializer.hashCode())
      val mutationRefImpl2 = mutationRefImpl1.copy(variablesSerializer = newVariablesSerializer)
      mutationRefImpl1.hashCode() shouldNotBe mutationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate callerSdkType`() = runTest {
    // Increase the `maxDiscardPercentage` because it's default value is 10%, but roughly 50% will
    // be discarded because there are only two distinct values for `callerSdkType`.
    checkAll(
      propTestConfig.copy(maxDiscardPercentage = 70),
      Arb.dataConnect.mutationRefImpl(),
      Arb.enum<CallerSdkType>()
    ) { mutationRefImpl1, newCallerSdkType ->
      assume(mutationRefImpl1.callerSdkType.hashCode() != newCallerSdkType.hashCode())
      val mutationRefImpl2 = mutationRefImpl1.copy(callerSdkType = newCallerSdkType)
      mutationRefImpl1.hashCode() shouldNotBe mutationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate dataSerializersModule`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.serializersModule()
    ) { mutationRefImpl1, newDataSerializersModule ->
      assume(
        mutationRefImpl1.dataSerializersModule.hashCode() != newDataSerializersModule.hashCode()
      )
      val mutationRefImpl2 = mutationRefImpl1.copy(dataSerializersModule = newDataSerializersModule)
      mutationRefImpl1.hashCode() shouldNotBe mutationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializersModule`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.serializersModule()
    ) { mutationRefImpl1, newVariablesSerializersModule ->
      assume(
        mutationRefImpl1.variablesSerializersModule.hashCode() !=
          newVariablesSerializersModule.hashCode()
      )
      val mutationRefImpl2 =
        mutationRefImpl1.copy(variablesSerializersModule = newVariablesSerializersModule)
      mutationRefImpl1.hashCode() shouldNotBe mutationRefImpl2.hashCode()
    }
  }

  @Test
  fun `equals(this) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl ->
      mutationRefImpl.equals(mutationRefImpl) shouldBe true
    }
  }

  @Test
  fun `equals(equal, but distinct, instance) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl1 ->
      val mutationRefImpl2 = mutationRefImpl1.copy()
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe true
    }
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl ->
      mutationRefImpl.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val othersArb =
      Arb.choice(
        Arb.string(),
        Arb.int(),
        Arb.dataConnect.operationRefImpl<Nothing, TestVariables>(),
        Arb.dataConnect.queryRefImpl<Nothing, TestVariables>(),
      )
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), othersArb) { mutationRefImpl, other
      ->
      mutationRefImpl.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataConnect differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.mock<FirebaseDataConnectInternal>()
    ) { mutationRefImpl1, newDataConnect ->
      assume(mutationRefImpl1.dataConnect != newDataConnect)
      val mutationRefImpl2 = mutationRefImpl1.withDataConnect(newDataConnect)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only operationName differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.string()) {
      mutationRefImpl1,
      newOperationName ->
      assume(mutationRefImpl1.operationName != newOperationName)
      val mutationRefImpl2 = mutationRefImpl1.copy(operationName = newOperationName)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variables differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.mock<TestVariables>()) {
      mutationRefImpl1,
      newVariables ->
      assume(mutationRefImpl1.variables != newVariables)
      val mutationRefImpl2 = mutationRefImpl1.copy(variables = newVariables)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataDeserializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.mock<DeserializationStrategy<TestData>>()
    ) { mutationRefImpl1, newDataDeserializer ->
      assume(mutationRefImpl1.dataDeserializer != newDataDeserializer)
      val mutationRefImpl2 = mutationRefImpl1.copy(dataDeserializer = newDataDeserializer)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.mock<SerializationStrategy<TestVariables>>()
    ) { mutationRefImpl1, newVariablesSerializer ->
      assume(mutationRefImpl1.variablesSerializer != newVariablesSerializer)
      val mutationRefImpl2 = mutationRefImpl1.copy(variablesSerializer = newVariablesSerializer)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only callerSdkType differs`() = runTest {
    // Increase the `maxDiscardPercentage` because it's default value is 10%, but roughly 50% will
    // be discarded because there are only two distinct values for `callerSdkType`.
    checkAll(
      propTestConfig.copy(maxDiscardPercentage = 70),
      Arb.dataConnect.mutationRefImpl(),
      Arb.enum<CallerSdkType>()
    ) { mutationRefImpl1, newCallerSdkType ->
      assume(mutationRefImpl1.callerSdkType != newCallerSdkType)
      val mutationRefImpl2 = mutationRefImpl1.copy(callerSdkType = newCallerSdkType)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataSerializersModule differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.serializersModule()
    ) { mutationRefImpl1, newDataSerializersModule ->
      assume(mutationRefImpl1.dataSerializersModule != newDataSerializersModule)
      val mutationRefImpl2 = mutationRefImpl1.copy(dataSerializersModule = newDataSerializersModule)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializersModule differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.serializersModule()
    ) { mutationRefImpl1, newVariablesSerializersModule ->
      assume(mutationRefImpl1.variablesSerializersModule != newVariablesSerializersModule)
      val mutationRefImpl2 =
        mutationRefImpl1.copy(variablesSerializersModule = newVariablesSerializersModule)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `toString() should incorporate the string representations of public properties`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl ->
      val toStringResult = mutationRefImpl.toString()
      assertSoftly {
        withClue("shouldStartWith") { toStringResult shouldStartWith "MutationRefImpl(" }
        withClue("shouldEndWith") { toStringResult shouldEndWith ")" }
        withClue("dataConnect") {
          toStringResult shouldContainWithNonAbuttingText
            "dataConnect=${mutationRefImpl.dataConnect}"
        }
        withClue("operationName") {
          toStringResult shouldContainWithNonAbuttingText
            "operationName=${mutationRefImpl.operationName}"
        }
        withClue("variables") {
          toStringResult shouldContainWithNonAbuttingText "variables=${mutationRefImpl.variables}"
        }
        withClue("dataDeserializer") {
          toStringResult shouldContainWithNonAbuttingText
            "dataDeserializer=${mutationRefImpl.dataDeserializer}"
        }
        withClue("variablesSerializer") {
          toStringResult shouldContainWithNonAbuttingText
            "variablesSerializer=${mutationRefImpl.variablesSerializer}"
        }
        withClue("callerSdkType") {
          toStringResult shouldContainWithNonAbuttingText
            "callerSdkType=${mutationRefImpl.callerSdkType}"
        }
        withClue("dataSerializersModule") {
          toStringResult shouldContainWithNonAbuttingText
            "dataSerializersModule=${mutationRefImpl.dataSerializersModule}"
        }
        withClue("variablesSerializersModule") {
          toStringResult shouldContainWithNonAbuttingText
            "variablesSerializersModule=${mutationRefImpl.variablesSerializersModule}"
        }
      }
    }
  }

  @Test
  fun `copy() with no arguments should return an equal, but distinct, object`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl1 ->
      val mutationRefImpl2 = mutationRefImpl1.copy()
      mutationRefImpl2 shouldNotBeSameInstanceAs mutationRefImpl1
      mutationRefImpl2.shouldHavePropertiesEqualTo(mutationRefImpl1)
    }
  }

  @Test
  fun `copy() with all arguments should return an object with its properties set to the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.mutationRefImpl(),
        Arb.dataConnect.operationRefConstructorArguments<TestData?, TestVariables>()
      ) { mutationRefImpl1, newValues ->
        val mutationRefImpl2 =
          mutationRefImpl1.copy(
            operationName = newValues.operationName,
            variables = newValues.variables,
            dataDeserializer = newValues.dataDeserializer,
            variablesSerializer = newValues.variablesSerializer,
            callerSdkType = newValues.callerSdkType,
            dataSerializersModule = newValues.dataSerializersModule,
            variablesSerializersModule = newValues.variablesSerializersModule,
          )

        mutationRefImpl2 shouldNotBeSameInstanceAs mutationRefImpl1
        mutationRefImpl2.shouldHavePropertiesEqualTo(
          newValues.copy(dataConnect = mutationRefImpl1.dataConnect)
        )
      }
    }

  @Test
  fun `withVariablesSerializer() with only required arguments should return an equal object, except for the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.mutationRefImpl(),
        Arb.mock<TestVariables2>(),
        Arb.mock<SerializationStrategy<TestVariables2>>()
      ) { mutationRefImpl1, newVariables, newVariablesSerializer ->
        val mutationRefImpl2 =
          mutationRefImpl1.withVariablesSerializer(
            variables = newVariables,
            variablesSerializer = newVariablesSerializer,
          )

        val expected =
          OperationRefConstructorArguments(mutationRefImpl1)
            .withVariablesSerializer(newVariables, newVariablesSerializer)
        mutationRefImpl2.shouldHavePropertiesEqualTo(expected)
      }
    }

  @Test
  fun `withVariablesSerializer() with all arguments should return an equal object, except for the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.mutationRefImpl(),
        Arb.mock<TestVariables2>(),
        Arb.mock<SerializationStrategy<TestVariables2>>(),
        Arb.mock<SerializersModule>()
      ) { mutationRefImpl1, newVariables, newVariablesSerializer, newVariablesSerializersModule ->
        val mutationRefImpl2 =
          mutationRefImpl1.withVariablesSerializer(
            variables = newVariables,
            variablesSerializer = newVariablesSerializer,
            variablesSerializersModule = newVariablesSerializersModule,
          )

        val expected =
          OperationRefConstructorArguments(mutationRefImpl1)
            .withVariablesSerializer(newVariables, newVariablesSerializer)
            .copy(variablesSerializersModule = newVariablesSerializersModule)
        mutationRefImpl2.shouldHavePropertiesEqualTo(expected)
      }
    }

  @Test
  fun `withDataDeserializer() with only required arguments should return an equal object, except for the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.mutationRefImpl(),
        Arb.mock<DeserializationStrategy<TestData2>>()
      ) { mutationRefImpl1, newDataDeserializer ->
        val mutationRefImpl2 =
          mutationRefImpl1.withDataDeserializer(dataDeserializer = newDataDeserializer)

        val expected =
          OperationRefConstructorArguments(mutationRefImpl1)
            .withDataDeserializer(newDataDeserializer)
        mutationRefImpl2.shouldHavePropertiesEqualTo(expected)
      }
    }

  @Test
  fun `withDataDeserializer() with all arguments should return an equal object, except for the given arguments`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.mutationRefImpl(),
        Arb.mock<DeserializationStrategy<TestData2>>(),
        Arb.mock<SerializersModule>()
      ) { mutationRefImpl1, newDataDeserializer, newDataSerializersModule ->
        val mutationRefImpl2 =
          mutationRefImpl1.withDataDeserializer(
            dataDeserializer = newDataDeserializer,
            dataSerializersModule = newDataSerializersModule
          )

        val expected =
          OperationRefConstructorArguments(mutationRefImpl1)
            .withDataDeserializer(newDataDeserializer)
            .copy(dataSerializersModule = newDataSerializersModule)
        mutationRefImpl2.shouldHavePropertiesEqualTo(expected)
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

    fun DataConnectArb.mutationRefImpl(): Arb<MutationRefImpl<TestData?, TestVariables>> =
      mutationRefImpl(
        variables = Arb.mock(),
        dataDeserializer = arbitrary { serializer() },
        variablesSerializer = Arb.mock(),
      )

    fun DataConnectArb.mutationRefImpl(
      dataConnect: FirebaseDataConnectInternal
    ): Arb<MutationRefImpl<TestData?, TestVariables>> =
      mutationRefImpl().map { it.withDataConnect(dataConnect) }

    fun dataConnectWithMutationResult(
      result: Result<TestData>,
      mutationRefSlot: CapturingSlot<MutationRefImpl<TestData, TestVariables>> = slot(),
    ): FirebaseDataConnectInternal =
      mockk<FirebaseDataConnectInternal>(relaxed = true) {
        every { getMutationManager() } returns
          mockk<MutationManager> {
            coEvery { execute(capture(mutationRefSlot)) } answers { result.getOrThrow() }
          }
      }
  }
}
