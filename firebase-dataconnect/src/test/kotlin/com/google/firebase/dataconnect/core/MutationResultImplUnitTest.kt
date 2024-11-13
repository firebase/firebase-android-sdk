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

import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.mutationRefImpl
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class MutationResultImplUnitTest {

  @Test
  fun `'data' should be the same object given to the constructor`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.testData()) {
      mutation,
      data ->
      val mutationResult = mutation.MutationResultImpl(data)
      mutationResult.data shouldBeSameInstanceAs data
    }
  }

  @Test
  fun `'ref' should be the MutationRefImpl object that was used to create it`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.testData()) {
      mutation,
      data ->
      val mutationResult = mutation.MutationResultImpl(data)
      mutationResult.ref shouldBeSameInstanceAs mutation
    }
  }

  @Test
  fun `toString() should contain the expected information`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.testData()) {
      mutation,
      data ->
      val mutationResult = mutation.MutationResultImpl(data)
      val toStringResult = mutationResult.toString()
      assertSoftly {
        toStringResult shouldStartWith "MutationResultImpl("
        toStringResult shouldContainWithNonAbuttingText "data=$data"
        toStringResult shouldContainWithNonAbuttingText "ref=$mutation"
        toStringResult shouldEndWith ")"
      }
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationResultImpl()) { mutationResult ->
      mutationResult.equals(mutationResult) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.testData()) {
      mutation,
      data ->
      val mutationResult1 = mutation.MutationResultImpl(data)
      val mutationResult2 = mutation.MutationResultImpl(data)
      mutationResult1.equals(mutationResult2) shouldBe true
    }
  }

  @Test
  fun `equals() should return true if all properties are equal, and 'data' is null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutation ->
      val mutationResult1 = mutation.MutationResultImpl(null)
      val mutationResult2 = mutation.MutationResultImpl(null)
      mutationResult1.equals(mutationResult2) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationResultImpl()) { mutationResult ->
      mutationResult.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val others = Arb.choice(Arb.dataConnect.string(), Arb.int(), Arb.dataConnect.mutationRefImpl())
    checkAll(propTestConfig, Arb.dataConnect.mutationResultImpl(), others) { mutationResult, other
      ->
      mutationResult.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only 'data' differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.testData()
    ) { mutation, data1, data2 ->
      assume(data1 != data2)
      val mutationResult1 = mutation.MutationResultImpl(data1)
      val mutationResult2 = mutation.MutationResultImpl(data2)
      mutationResult1.equals(mutationResult2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only 'ref' differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.testData()
    ) { mutation1, mutation2, data,
      ->
      assume(mutation1 != mutation2)
      val mutationResult1 = mutation1.MutationResultImpl(data)
      val mutationResult2 = mutation2.MutationResultImpl(data)
      mutationResult1.equals(mutationResult2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when data of first object is null and second is non-null`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.testData()) {
        mutation,
        data,
        ->
        val mutationResult1 = mutation.MutationResultImpl(null)
        val mutationResult2 = mutation.MutationResultImpl(data)
        mutationResult1.equals(mutationResult2) shouldBe false
      }
    }

  @Test
  fun `equals() should return false when data of second object is null and first is non-null`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.testData()) {
        mutation,
        data,
        ->
        val mutationResult1 = mutation.MutationResultImpl(data)
        val mutationResult2 = mutation.MutationResultImpl(null)
        mutationResult1.equals(mutationResult2) shouldBe false
      }
    }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.mutationResultImpl()) { mutationResult ->
        val hashCode1 = mutationResult.hashCode()
        mutationResult.hashCode() shouldBe hashCode1
        mutationResult.hashCode() shouldBe hashCode1
        mutationResult.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.testData()) {
      mutation,
      data,
      ->
      val mutationResult1 = mutation.MutationResultImpl(data)
      val mutationResult2 = mutation.MutationResultImpl(data)
      mutationResult1.hashCode() shouldBe mutationResult2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if 'data' is different`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.testData()
    ) { mutation, data1, data2,
      ->
      assume(data1.hashCode() != data2.hashCode())
      val mutationResult1 = mutation.MutationResultImpl(data1)
      val mutationResult2 = mutation.MutationResultImpl(data2)
      mutationResult1.hashCode() shouldNotBe mutationResult2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if 'ref' is different`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.mutationRefImpl(),
      Arb.dataConnect.testData()
    ) { mutation1, mutation2, data,
      ->
      assume(mutation1.hashCode() != mutation2.hashCode())
      val mutationResult1 = mutation1.MutationResultImpl(data)
      val mutationResult2 = mutation2.MutationResultImpl(data)
      mutationResult1.hashCode() shouldNotBe mutationResult2.hashCode()
    }
  }

  private data class TestVariables(val value: String)

  private data class TestData(val value: String)

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)

    fun DataConnectArb.testVariables(string: Arb<String> = string()): Arb<TestVariables> =
      arbitrary {
        TestVariables(string.bind())
      }

    fun DataConnectArb.testData(string: Arb<String> = string()): Arb<TestData> = arbitrary {
      TestData(string.bind())
    }

    fun DataConnectArb.mutationResultImpl(
      mutation: Arb<MutationRefImpl<TestData?, TestVariables>> = mutationRefImpl(),
      data: Arb<TestData> = testData()
    ): Arb<MutationRefImpl<TestData?, TestVariables>.MutationResultImpl> = arbitrary {
      mutation.bind().MutationResultImpl(data.bind())
    }

    fun DataConnectArb.mutationRefImpl(): Arb<MutationRefImpl<TestData?, TestVariables>> =
      mutationRefImpl(Arb.dataConnect.testVariables())
  }
}
