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

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.testutil.StubOperationRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.mock
import com.google.firebase.dataconnect.testutil.property.arbitrary.mutationRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationRefConstructorArguments
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.queryRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.shouldHavePropertiesEqualTo
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class OperationRefImplUnitTest {

  private interface TestData
  private interface TestVariables

  @Test
  fun `constructor should initialize properties to the given objects`() = runTest {
    val argsArb = Arb.dataConnect.operationRefConstructorArguments<TestData, TestVariables>()
    checkAll(propTestConfig, argsArb) { args ->
      val operationRefImpl =
        StubOperationRefImpl(
          dataConnect = args.dataConnect,
          operationName = args.operationName,
          variables = args.variables,
          dataDeserializer = args.dataDeserializer,
          variablesSerializer = args.variablesSerializer,
          callerSdkType = args.callerSdkType,
          dataSerializersModule = args.dataSerializersModule,
          variablesSerializersModule = args.variablesSerializersModule,
        )

      operationRefImpl.shouldHavePropertiesEqualTo(args)
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked repeatedly`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl()) { operationRefImpl ->
      val hashCode = operationRefImpl.hashCode()
      repeat(3) { operationRefImpl.hashCode() shouldBe hashCode }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal, objects`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.operationRefImpl()) { operationRefImpl ->
        operationRefImpl.hashCode() shouldBe operationRefImpl.copy().hashCode()
      }
    }

  @Test
  fun `hashCode() should incorporate dataConnect`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.mock<FirebaseDataConnectInternal>()
    ) { operationRefImpl1, newDataConnect ->
      assume(operationRefImpl1.dataConnect.hashCode() != newDataConnect.hashCode())
      val operationRefImpl2 = operationRefImpl1.withDataConnect(newDataConnect)
      operationRefImpl1.hashCode() shouldNotBe operationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate operationName`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl(), Arb.dataConnect.string()) {
      operationRefImpl1,
      newOperationName ->
      assume(operationRefImpl1.operationName.hashCode() != newOperationName.hashCode())
      val operationRefImpl2 = operationRefImpl1.copy(operationName = newOperationName)
      operationRefImpl1.hashCode() shouldNotBe operationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate variables`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl(), Arb.mock<TestVariables>()) {
      operationRefImpl1,
      newVariables ->
      assume(operationRefImpl1.variables.hashCode() != newVariables.hashCode())
      val operationRefImpl2 = operationRefImpl1.copy(variables = newVariables)
      operationRefImpl1.hashCode() shouldNotBe operationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate dataDeserializer`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.mock<DeserializationStrategy<TestData>>()
    ) { operationRefImpl1, newDataDeserializer ->
      assume(operationRefImpl1.dataDeserializer.hashCode() != newDataDeserializer.hashCode())
      val operationRefImpl2 = operationRefImpl1.copy(dataDeserializer = newDataDeserializer)
      operationRefImpl1.hashCode() shouldNotBe operationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializer`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.mock<SerializationStrategy<TestVariables>>()
    ) { operationRefImpl1, newVariablesSerializer ->
      assume(operationRefImpl1.variablesSerializer.hashCode() != newVariablesSerializer.hashCode())
      val operationRefImpl2 = operationRefImpl1.copy(variablesSerializer = newVariablesSerializer)
      operationRefImpl1.hashCode() shouldNotBe operationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate callerSdkType`() = runTest {
    // Increase the `maxDiscardPercentage` because it's default value is 10%, but roughly 50% will
    // be discarded because there are only two distinct values for `callerSdkType`.
    checkAll(
      propTestConfig.copy(maxDiscardPercentage = 70),
      Arb.dataConnect.operationRefImpl(),
      Arb.enum<CallerSdkType>()
    ) { operationRefImpl1, newCallerSdkType ->
      assume(operationRefImpl1.callerSdkType.hashCode() != newCallerSdkType.hashCode())
      val operationRefImpl2 = operationRefImpl1.copy(callerSdkType = newCallerSdkType)
      operationRefImpl1.hashCode() shouldNotBe operationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate dataSerializersModule`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.dataConnect.serializersModule()
    ) { operationRefImpl1, newDataSerializersModule ->
      assume(
        operationRefImpl1.dataSerializersModule.hashCode() != newDataSerializersModule.hashCode()
      )
      val operationRefImpl2 =
        operationRefImpl1.copy(dataSerializersModule = newDataSerializersModule)
      operationRefImpl1.hashCode() shouldNotBe operationRefImpl2.hashCode()
    }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializersModule`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.dataConnect.serializersModule()
    ) { operationRefImpl1, newVariablesSerializersModule ->
      assume(
        operationRefImpl1.variablesSerializersModule.hashCode() !=
          newVariablesSerializersModule.hashCode()
      )
      val operationRefImpl2 =
        operationRefImpl1.copy(variablesSerializersModule = newVariablesSerializersModule)
      operationRefImpl1.hashCode() shouldNotBe operationRefImpl2.hashCode()
    }
  }

  @Test
  fun `equals(this) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl()) { operationRefImpl ->
      operationRefImpl.equals(operationRefImpl) shouldBe true
    }
  }

  @Test
  fun `equals(equal, but distinct, instance) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl()) { operationRefImpl1 ->
      val operationRefImpl2 = operationRefImpl1.copy()
      operationRefImpl1.equals(operationRefImpl2) shouldBe true
    }
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl()) { operationRefImpl ->
      operationRefImpl.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val othersArb =
      Arb.choice(
        Arb.string(),
        Arb.int(),
        Arb.dataConnect.queryRefImpl<Nothing, TestVariables>(),
        Arb.dataConnect.mutationRefImpl<Nothing, TestVariables>()
      )
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl(), othersArb) {
      operationRefImpl,
      other ->
      operationRefImpl.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataConnect differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.mock<FirebaseDataConnectInternal>()
    ) { operationRefImpl1, newDataConnect ->
      assume(operationRefImpl1.dataConnect != newDataConnect)
      val operationRefImpl2 = operationRefImpl1.withDataConnect(newDataConnect)
      operationRefImpl1.equals(operationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only operationName differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl(), Arb.dataConnect.string()) {
      operationRefImpl1,
      newOperationName ->
      assume(operationRefImpl1.operationName != newOperationName)
      val operationRefImpl2 = operationRefImpl1.copy(operationName = newOperationName)
      operationRefImpl1.equals(operationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variables differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl(), Arb.mock<TestVariables>()) {
      operationRefImpl1,
      newVariables ->
      assume(operationRefImpl1.variables != newVariables)
      val operationRefImpl2 = operationRefImpl1.copy(variables = newVariables)
      operationRefImpl1.equals(operationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataDeserializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.mock<DeserializationStrategy<TestData>>()
    ) { operationRefImpl1, newDataDeserializer ->
      assume(operationRefImpl1.dataDeserializer != newDataDeserializer)
      val operationRefImpl2 = operationRefImpl1.copy(dataDeserializer = newDataDeserializer)
      operationRefImpl1.equals(operationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.mock<SerializationStrategy<TestVariables>>()
    ) { operationRefImpl1, newVariablesSerializer ->
      assume(operationRefImpl1.variablesSerializer != newVariablesSerializer)
      val operationRefImpl2 = operationRefImpl1.copy(variablesSerializer = newVariablesSerializer)
      operationRefImpl1.equals(operationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only callerSdkType differs`() = runTest {
    // Increase the `maxDiscardPercentage` because it's default value is 10%, but roughly 50% will
    // be discarded because there are only two distinct values for `callerSdkType`.
    checkAll(
      propTestConfig.copy(maxDiscardPercentage = 70),
      Arb.dataConnect.operationRefImpl(),
      Arb.enum<CallerSdkType>()
    ) { operationRefImpl1, newCallerSdkType ->
      assume(operationRefImpl1.callerSdkType != newCallerSdkType)
      val operationRefImpl2 = operationRefImpl1.copy(callerSdkType = newCallerSdkType)
      operationRefImpl1.equals(operationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataSerializersModule differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.dataConnect.serializersModule()
    ) { operationRefImpl1, newDataSerializersModule ->
      assume(operationRefImpl1.dataSerializersModule != newDataSerializersModule)
      val operationRefImpl2 =
        operationRefImpl1.copy(dataSerializersModule = newDataSerializersModule)
      operationRefImpl1.equals(operationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializersModule differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRefImpl(),
      Arb.dataConnect.serializersModule()
    ) { operationRefImpl1, newVariablesSerializersModule ->
      assume(operationRefImpl1.variablesSerializersModule != newVariablesSerializersModule)
      val operationRefImpl2 =
        operationRefImpl1.copy(variablesSerializersModule = newVariablesSerializersModule)
      operationRefImpl1.equals(operationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `toString() should incorporate the string representations of public properties`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationRefImpl()) { operationRefImpl ->
      val toStringResult = operationRefImpl.toString()
      assertSoftly {
        withClue("shouldStartWith") { toStringResult shouldStartWith "OperationRefImpl(" }
        withClue("shouldEndWith") { toStringResult shouldEndWith ")" }
        withClue("dataConnect") {
          toStringResult shouldContainWithNonAbuttingText
            "dataConnect=${operationRefImpl.dataConnect}"
        }
        withClue("operationName") {
          toStringResult shouldContainWithNonAbuttingText
            "operationName=${operationRefImpl.operationName}"
        }
        withClue("variables") {
          toStringResult shouldContainWithNonAbuttingText "variables=${operationRefImpl.variables}"
        }
        withClue("dataDeserializer") {
          toStringResult shouldContainWithNonAbuttingText
            "dataDeserializer=${operationRefImpl.dataDeserializer}"
        }
        withClue("variablesSerializer") {
          toStringResult shouldContainWithNonAbuttingText
            "variablesSerializer=${operationRefImpl.variablesSerializer}"
        }
        withClue("callerSdkType") {
          toStringResult shouldContainWithNonAbuttingText
            "callerSdkType=${operationRefImpl.callerSdkType}"
        }
        withClue("dataSerializersModule") {
          toStringResult shouldContainWithNonAbuttingText
            "dataSerializersModule=${operationRefImpl.dataSerializersModule}"
        }
        withClue("variablesSerializersModule") {
          toStringResult shouldContainWithNonAbuttingText
            "variablesSerializersModule=${operationRefImpl.variablesSerializersModule}"
        }
      }
    }
  }

  private companion object {
    val propTestConfig =
      PropTestConfig(
        iterations = 100,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
      )

    fun DataConnectArb.operationRefImpl(): Arb<StubOperationRefImpl<TestData, TestVariables>> =
      operationRefImpl(Arb.mock<TestVariables>())
  }
}
