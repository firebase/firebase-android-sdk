/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.testutil.property.arbitrary.someValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MaybeValueUnitTest {

  @Test
  fun `Empty isEmpty returns true`() {
    MaybeValue.Empty.isEmpty shouldBe true
  }

  @Test
  fun `Empty getOrNull() returns null`() {
    MaybeValue.Empty.getOrNull() shouldBe null
  }

  @Test
  fun `Empty getOrThrow() throws IllegalStateException`() {
    val exception = shouldThrow<IllegalStateException> { MaybeValue.Empty.getOrThrow() }
    exception shouldHaveMessage "no value"
  }

  @Test
  fun `Empty toString returns correct string`() {
    MaybeValue.Empty.toString() shouldBe "MaybeValue.Empty"
  }

  @Test
  fun `Empty hashCode() returns the same value on every invocation`() {
    val hashCodes = List(100) { MaybeValue.Empty.hashCode() }

    hashCodes.distinct().size shouldBe 1
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `Empty equals(Empty) returns true`() {
    MaybeValue.Empty.equals(MaybeValue.Empty) shouldBe true
  }

  @Test
  fun `Empty equals(null) returns false`() {
    MaybeValue.Empty.equals(null) shouldBe false
  }

  @Test
  fun `Empty equals(any other value) returns false`() = runTest {
    val someValueArb = Arb.someValue().map { it.value }
    checkAll(propTestConfig, someValueArb) { value ->
      @Suppress("ReplaceCallWithBinaryOperator")
      MaybeValue.Empty.equals(value) shouldBe false
    }
  }

  @Test
  fun `Empty equals(MaybeValue) returns false`() = runTest {
    val maybeValueArb = Arb.someValue().map { MaybeValue.Value(it) }
    checkAll(propTestConfig, maybeValueArb) { maybeValueValue ->
      MaybeValue.Empty.equals(maybeValueValue) shouldBe false
    }
  }

  @Test
  fun `Value isEmpty returns false`() = testWithNullableTestValues { value ->
    val maybeValue = MaybeValue.Value(value)
    maybeValue.isEmpty shouldBe false
  }

  @Test
  fun `Value getOrNull returns the value`() = testWithNullableTestValues { value ->
    val maybeValue = MaybeValue.Value(value)
    maybeValue.getOrNull() shouldBeSameInstanceAs value
  }

  @Test
  fun `Value getOrThrow returns the value`() = testWithNullableTestValues { value ->
    val maybeValue = MaybeValue.Value(value)
    maybeValue.getOrThrow() shouldBeSameInstanceAs value
  }

  @Test
  fun `Value equals compares inner values`() = testWithNullableTestValues { value ->
    val value1 = MaybeValue.Value(value)
    val value2 = MaybeValue.Value(value)
    val differentValue = MaybeValue.Value(if (value == null) TestValue("non-null") else null)

    value1 shouldBe value2
    value1 shouldNotBe differentValue
    value1 shouldNotBe MaybeValue.Empty
  }

  @Test
  fun `Value hashCode returns inner value hashCode`() = testWithNullableTestValues { value ->
    val maybeValue = MaybeValue.Value(value)
    maybeValue.hashCode() shouldBe (value?.hashCode() ?: 0)
  }

  @Test
  fun `Value toString returns correct string`() = testWithNullableTestValues { value ->
    val maybeValue = MaybeValue.Value(value)
    maybeValue.toString() shouldBe "MaybeValue.Value(value=$value)"
  }

  @Test
  fun `getOrElse on Empty typed as MaybeValue calls block`() = testWithNullableTestValues { value ->
    val empty: MaybeValue<TestValue?> = MaybeValue.Empty
    val block: () -> TestValue? = mockk { every { this@mockk() } returns value }

    val result = empty.getOrElse(block)

    result shouldBeSameInstanceAs value
    verify(exactly = 1) { block() }
  }

  @Test
  fun `getOrElse on typed Empty calls block`() = testWithNullableTestValues { value ->
    val empty = MaybeValue.Empty
    val block: () -> TestValue? = mockk { every { this@mockk() } returns value }

    val result = empty.getOrElse(block)

    result shouldBeSameInstanceAs value
    verify(exactly = 1) { block() }
  }

  @Test
  fun `getOrElse on Value typed as MaybeValue returns value and does not call block`() =
    testWithNullableTestValues { value ->
      val maybeValue: MaybeValue<TestValue?> = MaybeValue.Value(value)
      val block: () -> TestValue? = mockk(relaxed = true)

      val result = maybeValue.getOrElse(block)

      result shouldBeSameInstanceAs value
      confirmVerified(block)
    }

  @Test
  fun `getOrElse on typed Value returns value and does not call block`() =
    testWithNullableTestValues { value ->
      val maybeValue = MaybeValue.Value(value)
      val block: () -> TestValue? = mockk(relaxed = true)

      val result = maybeValue.getOrElse(block)

      result shouldBeSameInstanceAs value
      confirmVerified(block)
    }

  // endregion

  // region ifEmpty

  @Test
  fun `ifEmpty on Empty typed as MaybeValue calls block`() = runTest {
    val empty: MaybeValue<TestValue?> = MaybeValue.Empty
    val block: () -> Unit = mockk(relaxed = true)

    val result = empty.ifEmpty(block)

    result shouldBeSameInstanceAs empty
    verify(exactly = 1) { block() }
  }

  @Test
  fun `ifEmpty on typed Empty calls block`() = runTest {
    val empty = MaybeValue.Empty
    val block: () -> Unit = mockk(relaxed = true)

    val result = empty.ifEmpty(block)

    result shouldBeSameInstanceAs empty
    verify(exactly = 1) { block() }
  }

  @Test
  fun `ifEmpty on Value typed as MaybeValue does not call block`() =
    testWithNullableTestValues { value ->
      val maybeValue: MaybeValue<TestValue?> = MaybeValue.Value(value)
      val block: () -> Unit = mockk(relaxed = true)

      val result = maybeValue.ifEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      confirmVerified(block)
    }

  @Test
  fun `ifEmpty on typed Value does not call block`() = testWithNullableTestValues { value ->
    val maybeValue = MaybeValue.Value(value)
    val block: () -> Unit = mockk(relaxed = true)

    val result = maybeValue.ifEmpty(block)

    result shouldBeSameInstanceAs maybeValue
    confirmVerified(block)
  }

  // endregion

  // region ifNonEmpty

  @Test
  fun `ifNonEmpty on Empty typed as MaybeValue does not call block`() = runTest {
    val empty: MaybeValue<TestValue?> = MaybeValue.Empty
    val block: (TestValue?) -> Unit = mockk(relaxed = true)

    val result = empty.ifNonEmpty(block)

    result shouldBeSameInstanceAs empty
    confirmVerified(block)
  }

  @Test
  fun `ifNonEmpty on typed Empty does not call block`() = runTest {
    val empty = MaybeValue.Empty
    val block: (Nothing) -> Unit = mockk(relaxed = true)

    val result = empty.ifNonEmpty(block)

    result shouldBeSameInstanceAs empty
    confirmVerified(block)
  }

  @Test
  fun `ifNonEmpty on Value typed as MaybeValue calls block`() =
    testWithNullableTestValues { value ->
      val maybeValue: MaybeValue<TestValue?> = MaybeValue.Value(value)
      val block: (TestValue?) -> Unit = mockk(relaxed = true)

      val result = maybeValue.ifNonEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      verify(exactly = 1) { block(value) }
    }

  @Test
  fun `ifNonEmpty on typed Value calls block`() = testWithNullableTestValues { value ->
    val maybeValue = MaybeValue.Value(value)
    val block: (TestValue?) -> Unit = mockk(relaxed = true)

    val result = maybeValue.ifNonEmpty(block)

    result shouldBeSameInstanceAs maybeValue
    verify(exactly = 1) { block(value) }
  }

  // endregion

  // region fold

  @Test
  fun `fold on Empty typed as MaybeValue calls onEmpty`() = testWithNullableTestValues { value ->
    val empty: MaybeValue<TestValue?> = MaybeValue.Empty
    val onEmpty: () -> TestValue? = mockk { every { this@mockk() } returns value }
    val onNonEmpty: (TestValue?) -> TestValue? = mockk(relaxed = true)

    val result = empty.fold(onEmpty, onNonEmpty)

    result shouldBeSameInstanceAs value
    verify(exactly = 1) { onEmpty() }
    confirmVerified(onNonEmpty)
  }

  @Test
  fun `fold on typed Empty calls onEmpty`() = testWithNullableTestValues { value ->
    val empty = MaybeValue.Empty
    val onEmpty: () -> TestValue? = mockk { every { this@mockk() } returns value }
    val onNonEmpty: (Nothing) -> TestValue? = mockk(relaxed = true)

    val result = empty.fold(onEmpty, onNonEmpty)

    result shouldBeSameInstanceAs value
    verify(exactly = 1) { onEmpty() }
    confirmVerified(onNonEmpty)
  }

  @Test
  fun `fold on Value typed as MaybeValue calls onNonEmpty`() = testWithNullableTestValues { value ->
    val maybeValue: MaybeValue<TestValue?> = MaybeValue.Value(value)
    val mappedValue = TestValue("mapped")
    val onEmpty: () -> TestValue? = mockk(relaxed = true)
    val onNonEmpty: (TestValue?) -> TestValue? = mockk {
      every { this@mockk(any()) } returns mappedValue
    }

    val result = maybeValue.fold(onEmpty, onNonEmpty)

    result shouldBeSameInstanceAs mappedValue
    verify(exactly = 1) { onNonEmpty(value) }
    confirmVerified(onEmpty)
  }

  @Test
  fun `fold on typed Value calls onNonEmpty`() = testWithNullableTestValues { value ->
    val maybeValue = MaybeValue.Value(value)
    val mappedValue = TestValue("mapped")
    val onEmpty: () -> TestValue? = mockk(relaxed = true)
    val onNonEmpty: (TestValue?) -> TestValue? = mockk {
      every { this@mockk(any()) } returns mappedValue
    }

    val result = maybeValue.fold(onEmpty, onNonEmpty)

    result shouldBeSameInstanceAs mappedValue
    verify(exactly = 1) { onNonEmpty(value) }
    confirmVerified(onEmpty)
  }

  private companion object {

    /**
     * A simple data class used as a test value to verify referential integrity and basic
     * operations.
     */
    private data class TestValue(val value: String)

    /**
     * Creates and returns an [Arb] that generates [TestValue] instances.
     *
     * @param string The [Arb] used to generate the underlying string values.
     */
    private fun testValueArb(string: Arb<String> = Arb.string()): Arb<TestValue> =
      string.map(::TestValue)

    /**
     * Runs a property-based test with nullable [TestValue] instances.
     *
     * @param property The property test logic to execute.
     */
    private fun testWithNullableTestValues(property: suspend PropertyContext.(TestValue?) -> Unit) =
      runTest {
        checkAll(propTestConfig, testValueArb().orNull(nullProbability = 0.2), property)
      }
  }
}

/** The configuration for property-based tests in this file. */
@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 100,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )
