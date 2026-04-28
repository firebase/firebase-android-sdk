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

import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotEqual
import com.google.firebase.dataconnect.testutil.property.arbitrary.someValue
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Objects
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MaybeValueUnitTest {

  // region Tests for MaybeValue.Empty

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
  fun `Empty equals(Empty) returns true`() {
    @Suppress("ReplaceCallWithBinaryOperator")
    MaybeValue.Empty.equals(MaybeValue.Empty) shouldBe true
  }

  @Test
  fun `Empty equals(null) returns false`() {
    MaybeValue.Empty.equals(null) shouldBe false
  }

  @Test
  fun `Empty equals(any other value) returns false`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      @Suppress("ReplaceCallWithBinaryOperator")
      MaybeValue.Empty.equals(value) shouldBe false
    }
  }

  @Test
  fun `Empty equals(MaybeValue Value) returns false`() = runTest {
    val maybeValueArb = Arb.someValue().map { MaybeValue.Value(it) }
    checkAll(propTestConfig, maybeValueArb) { maybeValueValue ->
      MaybeValue.Empty.equals(maybeValueValue) shouldBe false
    }
  }

  // endregion

  // region Tests for MaybeValue.Value.isEmpty

  @Test
  fun `Value(null) isEmpty returns false`() {
    val maybeValue = MaybeValue.Value(null)
    maybeValue.isEmpty shouldBe false
  }

  @Test
  fun `Value(non-null) isEmpty returns false`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValue = MaybeValue.Value(value)
      maybeValue.isEmpty shouldBe false
    }
  }

  @Test
  fun `Value(Empty) isEmpty returns false`() {
    val maybeValue = MaybeValue.Value(MaybeValue.Empty)
    maybeValue.isEmpty shouldBe false
  }

  @Test
  fun `Value(Value) isEmpty returns false`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValue = MaybeValue.Value(MaybeValue.Value(value))
      maybeValue.isEmpty shouldBe false
    }
  }

  // endregion

  // region Tests for MaybeValue.Value.getOrNull()

  @Test
  fun `Value(null) getOrNull returns null`() {
    val maybeValue = MaybeValue.Value(null)
    maybeValue.getOrNull().shouldBeNull()
  }

  @Test
  fun `Value(non-null) getOrNull returns the value`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value, valueCopy) ->
      val maybeValue = MaybeValue.Value(value)

      val result = maybeValue.getOrNull()

      if (valueCopy() === value) {
        result shouldBeSameInstanceAs value
      } else {
        result shouldBe value
      }
    }
  }

  @Test
  fun `Value(Empty) getOrNull returns the encapsulated MaybeValue`() {
    val maybeValue = MaybeValue.Value(MaybeValue.Empty)
    maybeValue.getOrNull() shouldBe MaybeValue.Empty
  }

  @Test
  fun `Value(Value) getOrNull returns the encapsulated MaybeValue`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val innerMaybeValue = MaybeValue.Value(value)
      val maybeValue = MaybeValue.Value(innerMaybeValue)

      maybeValue.getOrNull() shouldBeSameInstanceAs innerMaybeValue
    }
  }

  // endregion

  // region Tests for MaybeValue.Value.getOrThrow()

  @Test
  fun `Value(null) getOrThrow returns null`() {
    val maybeValue = MaybeValue.Value(null)
    maybeValue.getOrThrow().shouldBeNull()
  }

  @Test
  fun `Value(non-null) getOrThrow returns the value`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value, valueCopy) ->
      val maybeValue = MaybeValue.Value(value)

      val result = maybeValue.getOrThrow()

      if (valueCopy() === value) {
        result shouldBeSameInstanceAs value
      } else {
        result shouldBe value
      }
    }
  }

  @Test
  fun `Value(Empty) getOrThrow returns the encapsulated MaybeValue`() {
    val maybeValue = MaybeValue.Value(MaybeValue.Empty)
    maybeValue.getOrThrow() shouldBe MaybeValue.Empty
  }

  @Test
  fun `Value(Value) getOrThrow returns the encapsulated MaybeValue`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val innerMaybeValue = MaybeValue.Value(value)
      val maybeValue = MaybeValue.Value(innerMaybeValue)

      maybeValue.getOrThrow() shouldBeSameInstanceAs innerMaybeValue
    }
  }

  // endregion

  // region Tests for MaybeValue.Value.equals()

  @Test
  fun `Value(null) equals(self) returns true`() {
    val maybeValue = MaybeValue.Value(null)
    @Suppress("ReplaceCallWithBinaryOperator")
    maybeValue.equals(maybeValue) shouldBe true
  }

  @Test
  fun `Value(null) equals(Empty) returns false`() {
    MaybeValue.Value(null).equals(MaybeValue.Empty) shouldBe false
  }

  @Test
  fun `Value(null) equals(Value(non-null)) returns false`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValueNonNullValue = MaybeValue.Value(value)
      MaybeValue.Value(null).equals(maybeValueNonNullValue) shouldBe false
    }
  }

  @Test
  fun `Value(null) equals(other types) returns false`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      @Suppress("ReplaceCallWithBinaryOperator")
      MaybeValue.Value(null).equals(value) shouldBe false
    }
  }

  @Test
  fun `Value(non-null) equals() returns true for equal arguments`() = runTest {
    checkAll(propTestConfig, maybeValueValueNonNullEqualPairArb()) {
      (maybeValueValue1, maybeValueValue2) ->
      @Suppress("ReplaceCallWithBinaryOperator")
      maybeValueValue1.equals(maybeValueValue2) shouldBe true
    }
  }

  @Test
  fun `Value(non-null) equals() returns false for non-equal arguments`() = runTest {
    checkAll(propTestConfig, maybeValueValueNonNullNotEqualPairArb()) {
      (maybeValueValue1, maybeValueValue2) ->
      @Suppress("ReplaceCallWithBinaryOperator")
      maybeValueValue1.equals(maybeValueValue2) shouldBe false
    }
  }

  // endregion

  // region Tests for MaybeValue.Value.hashCode()

  @Test
  fun `Value(null) hashCode returns a constant value`() {
    val hashCodes = List(10) { MaybeValue.Value(null).hashCode() }

    hashCodes.distinct() shouldHaveSize 1
  }

  @Test
  fun `Value(non-null) hashCode returns the hash code of the encapsulated value`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValue = MaybeValue.Value(value)
      maybeValue.hashCode() shouldBe value.hashCode()
    }
  }

  // endregion

  // region Tests for MaybeValue.Value.toString()

  @Test
  fun `Value(null) toString returns correct string`() {
    MaybeValue.Value(null).toString() shouldBe "MaybeValue.Value(null)"
  }

  @Test
  fun `Value(non-null) toString returns correct string`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValue = MaybeValue.Value(value)
      maybeValue.toString() shouldBe "MaybeValue.Value($value)"
    }
  }

  // endregion

  // region Tests for getOrElse extension function

  @Test
  fun `Empty getOrElse() calls block`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val block = BlockReturning(value)

      val result = MaybeValue.Empty.getOrElse(block)

      result shouldBeSameInstanceAs value
      block.callCount shouldBe 1
    }
  }

  @Test
  fun `Value(null) getOrElse() does not call block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val maybeValue = MaybeValue.Value(null)
      val block = BlockThrowing("block should not be called [amgy46v72f]")

      val result = maybeValue.getOrElse(block)

      result.shouldBeNull()
      block.callCount shouldBe 0
    }
  }

  @Test
  fun `Value(non-null) getOrElse() does not call block`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value, valueCopy) ->
      val maybeValue = MaybeValue.Value(value)
      val block = BlockThrowing("block should not be called [mwkdnm8f3j]")

      val result = maybeValue.getOrElse(block)

      if (valueCopy() === value) {
        result shouldBeSameInstanceAs value
      } else {
        result shouldBe value
      }
      block.callCount shouldBe 0
    }
  }

  @Test
  fun `MaybeValue getOrElse() with Empty receiver calls block`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValue: MaybeValue<*> = MaybeValue.Empty
      val block = BlockReturning(value)

      val result = maybeValue.getOrElse(block)

      result shouldBeSameInstanceAs value
      block.callCount shouldBe 1
    }
  }

  @Test
  fun `MaybeValue getOrElse() with Value(null) receiver does not call block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val maybeValue: MaybeValue<*> = MaybeValue.Value(null)
      val block = BlockThrowing("block should not be called [swb4wvq9py]")

      val result = maybeValue.getOrElse(block)

      result.shouldBeNull()
      block.callCount shouldBe 0
    }
  }

  @Test
  fun `MaybeValue getOrElse() with Value(non-null) receiver does not call block`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value, valueCopy) ->
      val maybeValue: MaybeValue<*> = MaybeValue.Value(value)
      val block = BlockThrowing("block should not be called [ek9ywex88n]")

      val result = maybeValue.getOrElse(block)

      if (valueCopy() === value) {
        result shouldBeSameInstanceAs value
      } else {
        result shouldBe value
      }
      block.callCount shouldBe 0
    }
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

private class MaybeValueValueEqualPair<out T : Any>(
  val value1: MaybeValue.Value<T>,
  val value2: MaybeValue.Value<T>,
  val dimension: Dimension,
) {

  operator fun component1() = value1

  operator fun component2() = value2

  override fun equals(other: Any?) =
    other is MaybeValueValueEqualPair<T> && other.value1 == value1 && other.value2 == value2

  override fun hashCode() = Objects.hash(MaybeValueValueEqualPair::class, value1, value2)

  override fun toString() =
    "MaybeValueValueEqualPair(value1=${value1.print().value}, " +
      "value2=${value2.print().value}, dimension=$dimension)"

  enum class Dimension {
    SameObject,
    SameValue,
    DifferentButEqualValue,
  }
}

private fun maybeValueValueNonNullEqualPairArb(): Arb<MaybeValueValueEqualPair<*>> {
  val valueArb = Arb.someValue()
  val dimensionArb = Arb.enum<MaybeValueValueEqualPair.Dimension>()
  return arbitrary {
    val (value1, value1Copy) = valueArb.bind()
    val dimension = dimensionArb.bind()

    val maybeValue1 = MaybeValue.Value(value1)
    val maybeValue2: MaybeValue.Value<Any> =
      when (dimension) {
        MaybeValueValueEqualPair.Dimension.SameObject -> maybeValue1
        MaybeValueValueEqualPair.Dimension.SameValue -> MaybeValue.Value(value1)
        MaybeValueValueEqualPair.Dimension.DifferentButEqualValue -> MaybeValue.Value(value1Copy())
      }
    MaybeValueValueEqualPair(value1 = maybeValue1, value2 = maybeValue2, dimension)
  }
}

private class MaybeValueValueNotEqualPair<out T : Any>(
  val value1: MaybeValue.Value<T>,
  val value2: Any?,
  val dimension: Dimension,
) {

  operator fun component1() = value1

  operator fun component2() = value2

  override fun equals(other: Any?) =
    other is MaybeValueValueNotEqualPair<*> && other.value1 == value1 && other.value2 == value2

  override fun hashCode() = Objects.hash(MaybeValueValueNotEqualPair::class, value1, value2)

  override fun toString() =
    "MaybeValueValueNotEqualPair(value1=${value1.print().value}, " +
      "value2=${value2.print().value}, dimension=$dimension)"

  enum class Dimension {
    Null,
    Empty,
    NullValue,
    UnequalValue,
  }
}

private fun maybeValueValueNonNullNotEqualPairArb(): Arb<MaybeValueValueNotEqualPair<*>> {
  val valueArb = Arb.someValue().map { it.value }
  val dimensionArb = Arb.enum<MaybeValueValueNotEqualPair.Dimension>()
  return arbitrary {
    val value1 = valueArb.bind()
    val dimension = dimensionArb.bind()

    val maybeValue1 = MaybeValue.Value(value1)
    val value2 =
      when (dimension) {
        MaybeValueValueNotEqualPair.Dimension.Null -> null
        MaybeValueValueNotEqualPair.Dimension.Empty -> MaybeValue.Empty
        MaybeValueValueNotEqualPair.Dimension.NullValue -> MaybeValue.Value(null)
        MaybeValueValueNotEqualPair.Dimension.UnequalValue ->
          MaybeValue.Value(valueArb.filterNotEqual(value1).bind())
      }
    MaybeValueValueNotEqualPair(value1 = maybeValue1, value2 = value2, dimension)
  }
}

private class BlockReturning<out T>(val returnValue: T) : (() -> T) {

  private val _callCount = AtomicInteger(0)

  val callCount: Int
    get() = _callCount.get()

  override operator fun invoke(): T {
    _callCount.incrementAndGet()
    return returnValue
  }
}

private class BlockThrowing(val message: String) : (() -> Nothing) {

  private val _callCount = AtomicInteger(0)

  val callCount: Int
    get() = _callCount.get()

  override operator fun invoke(): Nothing {
    _callCount.incrementAndGet()
    throw UnexpectedInvocationException(message)
  }

  class UnexpectedInvocationException(message: String) : Exception(message)
}
