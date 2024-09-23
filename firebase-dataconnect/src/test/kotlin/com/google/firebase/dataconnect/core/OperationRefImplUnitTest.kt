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

import com.google.firebase.dataconnect.testutil.StubOperationRefImpl
import com.google.firebase.dataconnect.testutil.copy
import com.google.firebase.dataconnect.testutil.operationRefImpl
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.mockk.mockk
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class OperationRefImplUnitTest {

  private class TestData
  private class TestVariables(val bar: String)

  @Test
  fun `constructor assigns public properties to the given arguments`() {
    val values = Arb.operationRefImpl().next()
    val operationRefImpl =
      object :
        OperationRefImpl<TestData, TestVariables>(
          dataConnect = values.dataConnect,
          operationName = values.operationName,
          variables = values.variables,
          dataDeserializer = values.dataDeserializer,
          variablesSerializer = values.variablesSerializer,
        ) {
        override suspend fun execute() = TODO()
      }

    operationRefImpl.asClue {
      assertSoftly {
        it.dataConnect shouldBeSameInstanceAs values.dataConnect
        it.operationName shouldBeSameInstanceAs values.operationName
        it.variables shouldBeSameInstanceAs values.variables
        it.dataDeserializer shouldBeSameInstanceAs values.dataDeserializer
        it.variablesSerializer shouldBeSameInstanceAs values.variablesSerializer
      }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked repeatedly`() {
    val operationRefImpl: OperationRefImpl<*, *> = Arb.operationRefImpl().next()
    val hashCode = operationRefImpl.hashCode()
    repeat(10) { operationRefImpl.hashCode() shouldBe hashCode }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal, objects`() {
    val operationRefImpl1 = Arb.operationRefImpl().next()
    val operationRefImpl2 = operationRefImpl1.copy()
    operationRefImpl1 shouldNotBeSameInstanceAs operationRefImpl2 // verify test precondition
    repeat(10) { operationRefImpl1.hashCode() shouldBe operationRefImpl2.hashCode() }
  }

  @Test
  fun `hashCode() should incorporate dataConnect`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(dataConnect = mockk(name = stringArb.next())) }
  }

  @Test
  fun `hashCode() should incorporate operationName`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(operationName = stringArb.next()) }
  }

  @Test
  fun `hashCode() should incorporate variables`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(variables = TestVariables(stringArb.next())) }
  }

  @Test
  fun `hashCode() should incorporate dataDeserializer`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(dataDeserializer = mockk(name = stringArb.next())) }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializer`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(variablesSerializer = mockk(name = stringArb.next()))
    }
  }

  private suspend fun verifyHashCodeEventuallyDiffers(
    otherFactory:
      (other: StubOperationRefImpl<TestData, TestVariables>) -> StubOperationRefImpl<
          TestData, TestVariables
        >
  ) {
    val obj1: StubOperationRefImpl<TestData, TestVariables> = Arb.operationRefImpl().next()
    retry(maxRetry = 50, timeout = Duration.INFINITE) {
      val obj2 = otherFactory(obj1)
      obj1.hashCode() shouldNotBe obj2.hashCode()
    }
  }

  @Test
  fun `equals(this) should return true`() = runTest {
    val operationRefImpl = Arb.operationRefImpl().next()
    operationRefImpl.equals(operationRefImpl) shouldBe true
  }

  @Test
  fun `equals(equal, but distinct, instance) should return true`() = runTest {
    val operationRefImpl1 = Arb.operationRefImpl().next()
    val operationRefImpl2 = operationRefImpl1.copy()
    operationRefImpl1 shouldNotBeSameInstanceAs operationRefImpl2 // verify test precondition
    operationRefImpl1.equals(operationRefImpl2) shouldBe true
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    val operationRefImpl = Arb.operationRefImpl().next()
    operationRefImpl.equals(null) shouldBe false
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val operationRefImpl = Arb.operationRefImpl().next()
    operationRefImpl.equals("not an OperationRefImpl") shouldBe false
  }

  @Test
  fun `equals() should return false when only dataConnect differs`() = runTest {
    val operationRefImpl1 = Arb.operationRefImpl().next()
    val operationRefImpl2 = operationRefImpl1.copy(dataConnect = mockk(stringArb.next()))
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only operationName differs`() = runTest {
    val operationRefImpl1 = Arb.operationRefImpl().next()
    val operationRefImpl2 =
      operationRefImpl1.copy(operationName = operationRefImpl1.operationName + "2")
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variables differs`() = runTest {
    val operationRefImpl1 = Arb.operationRefImpl().next()
    val operationRefImpl2 =
      operationRefImpl1.copy(variables = TestVariables(operationRefImpl1.variables.bar + "2"))
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only dataDeserializer differs`() = runTest {
    val operationRefImpl1 = Arb.operationRefImpl().next()
    val operationRefImpl2 = operationRefImpl1.copy(dataDeserializer = mockk(stringArb.next()))
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variablesSerializer differs`() = runTest {
    val operationRefImpl1 = Arb.operationRefImpl().next()
    val operationRefImpl2 = operationRefImpl1.copy(variablesSerializer = mockk(stringArb.next()))
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `toString() should incorporate the string representations of public properties`() = runTest {
    val operationRefImpl = Arb.operationRefImpl().next()
    val toStringResult = operationRefImpl.toString()

    assertSoftly {
      toStringResult.shouldContain("dataConnect=${operationRefImpl.dataConnect}")
      toStringResult.shouldContain("operationName=${operationRefImpl.operationName}")
      toStringResult.shouldContain("variables=${operationRefImpl.variables}")
      toStringResult.shouldContain("dataDeserializer=${operationRefImpl.dataDeserializer}")
      toStringResult.shouldContain("variablesSerializer=${operationRefImpl.variablesSerializer}")
    }
  }

  private companion object {
    val stringArb = Arb.string(6, codepoints = Codepoint.alphanumeric())

    fun Arb.Companion.testVariables(): Arb<TestVariables> = arbitrary {
      val stringArb = Arb.string(6, Codepoint.alphanumeric())
      TestVariables(stringArb.bind())
    }

    fun Arb.Companion.operationRefImpl(): Arb<StubOperationRefImpl<TestData, TestVariables>> =
      operationRefImpl(Arb.testVariables())
  }
}
