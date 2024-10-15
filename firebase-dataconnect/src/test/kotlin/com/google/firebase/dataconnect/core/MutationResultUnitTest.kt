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

import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.mutationRefImpl
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class MutationResultImplUnitTest {

  @Test
  fun `'data' should be the same object given to the constructor`() = runTest {
    checkAll(Arb.dataConnect.mutationRefImpl()) { mutation ->
      val data = TestData()
      val mutationResult = mutation.MutationResultImpl(data)
      mutationResult.data shouldBeSameInstanceAs data
    }
  }

  @Test
  fun `'ref' should be the MutationRefImpl object that was used to create it`() = runTest {
    checkAll(Arb.dataConnect.mutationRefImpl()) { mutation ->
      val mutationResult = mutation.MutationResultImpl(TestData())
      mutationResult.ref shouldBeSameInstanceAs mutation
    }
  }

  @Test
  fun `toString() should begin with the class name and contain text in parentheses`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.toString()).startsWith("MutationResultImpl(")
    assertThat(mutationResult.toString()).endsWith(")")
  }

  @Test
  fun `toString() should incorporate 'data'`() {
    val data = TestData()
    val mutationResult = sampleMutation.MutationResultImpl(data)

    assertThat(mutationResult.toString()).containsWithNonAdjacentText("data=$data")
  }

  @Test
  fun `toString() should incorporate 'ref'`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.toString()).containsWithNonAdjacentText("ref=$sampleMutation")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.equals(mutationResult)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData())
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult1.equals(mutationResult2)).isTrue()
  }

  @Test
  fun `equals() should return true if all properties are equal, and 'data' is null`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(null)
    val mutationResult2 = sampleMutation.MutationResultImpl(null)

    assertThat(mutationResult1.equals(mutationResult2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only 'data' differs`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData("foo"))
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData("bar"))

    assertThat(mutationResult1.equals(mutationResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'ref' differs`() {
    val mutationResult1 = sampleMutation1.MutationResultImpl(TestData())
    val mutationResult2 = sampleMutation2.MutationResultImpl(TestData())

    assertThat(mutationResult1.equals(mutationResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of first object is null and second is non-null`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(null)
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData("bar"))

    assertThat(mutationResult1.equals(mutationResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of second object is null and first is non-null`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData("bar"))
    val mutationResult2 = sampleMutation.MutationResultImpl(null)

    assertThat(mutationResult1.equals(mutationResult2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    val hashCode = mutationResult.hashCode()

    assertThat(mutationResult.hashCode()).isEqualTo(hashCode)
    assertThat(mutationResult.hashCode()).isEqualTo(hashCode)
    assertThat(mutationResult.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData())
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult1.hashCode()).isEqualTo(mutationResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'data' is different`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData("foo"))
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData("bar"))

    assertThat(mutationResult1.hashCode()).isNotEqualTo(mutationResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'ref' is different`() {
    val mutationResult1 = sampleMutation1.MutationResultImpl(TestData())
    val mutationResult2 = sampleMutation2.MutationResultImpl(TestData())

    assertThat(mutationResult1.hashCode()).isNotEqualTo(mutationResult2.hashCode())
  }

  data class TestVariables(val value: String = "TestVariablesDefaultValue")

  data class TestData(val value: String = "TestDataDefaultValue")

  private companion object {
    fun DataConnectArb.testVariables(string: Arb<String> = string()): Arb<TestVariables> =
      arbitrary {
        TestVariables(string.bind())
      }

    fun DataConnectArb.mutationRefImpl(): Arb<MutationRefImpl<TestData, TestVariables>> =
      Arb.dataConnect.mutationRefImpl(Arb.dataConnect.testVariables())
  }
}
