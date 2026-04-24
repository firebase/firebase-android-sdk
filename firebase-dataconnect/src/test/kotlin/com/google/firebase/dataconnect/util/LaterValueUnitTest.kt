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

import com.google.firebase.dataconnect.testutil.property.arbitrary.triple
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
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

class LaterValueUnitTest {

  @Test
  fun `isEmpty returns true initially`() = runTest {
    val laterValue = LaterValue<TestValue>()

    laterValue.isEmpty shouldBe true
  }

  @Test
  fun `isEmpty returns false after set()`() = testWithNullableTestValues { value ->
    val laterValue = LaterValue<TestValue?>()
    laterValue.set(value)

    laterValue.isEmpty shouldBe false
  }

  @Test
  fun `set() throws after first invocation`() = runTest {
    checkAll(propTestConfig, testValueArb().orNull(nullProbability = 0.33).triple()) {
      (value1, value2, value3) ->
      val laterValue = LaterValue<TestValue?>()
      laterValue.set(value1)

      val exception1 =
        withClue("exception1") { shouldThrow<IllegalStateException> { laterValue.set(value2) } }
      val exception2 =
        withClue("exception2") { shouldThrow<IllegalStateException> { laterValue.set(value3) } }

      withClue("exception1") { exception1 shouldHaveMessage "set() has already been called" }
      withClue("exception2") { exception2 shouldHaveMessage "set() has already been called" }
    }
  }

  @Test
  fun `getOrNull() returns null initially`() = runTest {
    val laterValue = LaterValue<TestValue>()

    laterValue.getOrNull() shouldBe null
  }

  @Test fun `getOrNull() returns the set value`() = testGetReturnsSetValue { getOrNull() }

  @Test
  fun `getOrThrow() throws initially`() = runTest {
    val laterValue = LaterValue<TestValue>()

    val exception = shouldThrow<IllegalStateException> { laterValue.getOrThrow() }

    exception shouldHaveMessage "set() has not yet been called"
  }

  @Test fun `getOrThrow() returns the set value`() = testGetReturnsSetValue { getOrThrow() }

  @Test
  fun `toString() before set() is called`() = runTest {
    val laterValue = LaterValue<TestValue>()
    laterValue.toString() shouldBe "<unset>"
  }

  @Test
  fun `toString() after set() is called`() = testWithNullableTestValues { value ->
    val laterValue = LaterValue<TestValue?>()

    laterValue.set(value)

    laterValue.toString() shouldBe value.toString()
  }

  @Test
  fun `ifNonEmpty() before set() does not call block`() = runTest {
    val laterValue = LaterValue<TestValue>()
    val block: (TestValue) -> Unit = mockk(relaxed = true)

    laterValue.ifNonEmpty(block)

    confirmVerified(block)
  }

  @Test
  fun `ifNonEmpty() after set() calls block`() = testWithNullableTestValues { value ->
    val laterValue = LaterValue<TestValue?>()
    laterValue.set(value)
    val block: (TestValue?) -> Unit = mockk(relaxed = true)

    laterValue.ifNonEmpty(block)

    verify(exactly = 1) { block(value) }
    confirmVerified(block)
  }

  @Test
  fun `ifNonEmpty() before set() returns the receiver`() = runTest {
    val laterValue = LaterValue<TestValue>()
    laterValue.ifNonEmpty(mockk(relaxed = true)) shouldBeSameInstanceAs laterValue
  }

  @Test
  fun `ifEmpty() before set() calls block`() = runTest {
    val laterValue = LaterValue<TestValue>()
    val block: () -> Unit = mockk(relaxed = true)

    laterValue.ifEmpty(block)

    verify(exactly = 1) { block() }
    confirmVerified(block)
  }

  @Test
  fun `ifEmpty() after set() does not call block`() = testWithNullableTestValues { value ->
    val laterValue = LaterValue<TestValue?>()
    laterValue.set(value)
    val block: () -> Unit = mockk(relaxed = true)

    laterValue.ifEmpty(block)

    confirmVerified(block)
  }

  @Test
  fun `ifEmpty() before set() returns the receiver`() = runTest {
    val laterValue = LaterValue<TestValue>()
    laterValue.ifEmpty(mockk(relaxed = true)) shouldBeSameInstanceAs laterValue
  }

  @Test
  fun `ifEmpty() after set() returns the receiver`() = testWithNullableTestValues { value ->
    val laterValue = LaterValue<TestValue?>()
    laterValue.set(value)

    laterValue.ifEmpty(mockk(relaxed = true)) shouldBeSameInstanceAs laterValue
  }

  @Test
  fun `getOrElse() before set() calls the block`() = testWithNullableTestValues { value ->
    val laterValue = LaterValue<TestValue?>()
    val block: () -> TestValue? = mockk { every { this@mockk() } returns value }

    laterValue.getOrElse(block)

    verify(exactly = 1) { block() }
  }

  @Test
  fun `getOrElse() after set() does not call the block`() = testWithNullableTestValues { value ->
    val laterValue = LaterValue<TestValue?>()
    laterValue.set(value)
    val block: () -> TestValue? = mockk(relaxed = true)

    laterValue.getOrElse(block)

    confirmVerified(block)
  }

  @Test
  fun `getOrElse() before set() returns the value returned from the block`() =
    testWithNullableTestValues { value ->
      val laterValue = LaterValue<TestValue?>()
      val block: () -> TestValue? = mockk { every { this@mockk() } returns value }

      val result = laterValue.getOrElse(block)

      result shouldBeSameInstanceAs value
    }

  @Test
  fun `getOrElse() after set() returns the receiver's value`() =
    testWithNullableTestValues { value ->
      val laterValue = LaterValue<TestValue?>()
      laterValue.set(value)
      val block: () -> TestValue? = mockk(relaxed = true)

      val result = laterValue.getOrElse(block)

      result shouldBeSameInstanceAs value
    }

  /**
   * A simple data class used as a test value to verify referential integrity and basic operations.
   */
  private data class TestValue(val value: String)

  private companion object {

    /**
     * Creates and returns an [Arb] that generates [TestValue] instances.
     *
     * @param string The [Arb] used to generate the underlying string values.
     */
    fun testValueArb(string: Arb<String> = Arb.string()): Arb<TestValue> = string.map(::TestValue)

    /**
     * Runs a property-based test with nullable [TestValue] instances.
     *
     * @param property The property test logic to execute.
     */
    fun testWithNullableTestValues(property: suspend PropertyContext.(TestValue?) -> Unit) =
      runTest {
        checkAll(propTestConfig, testValueArb().orNull(nullProbability = 0.2), property)
      }

    /**
     * Verifies that the given [block] correctly returns the value that was set on the [LaterValue].
     *
     * @param T The type of the value.
     * @param block A lambda that retrieves the value from the [LaterValue].
     */
    fun <T : TestValue?> testGetReturnsSetValue(block: LaterValue<TestValue?>.() -> T) =
      testWithNullableTestValues { value ->
        val laterValue = LaterValue<TestValue?>()

        laterValue.set(value)

        block(laterValue) shouldBeSameInstanceAs value
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
