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

import com.google.firebase.dataconnect.testutil.BlockReturning
import com.google.firebase.dataconnect.testutil.BlockReturningUnit
import com.google.firebase.dataconnect.testutil.BlockReturningWithParameter
import com.google.firebase.dataconnect.testutil.BlockThrowing
import com.google.firebase.dataconnect.testutil.BlockThrowingWithParameter
import com.google.firebase.dataconnect.testutil.BlockWithParameter
import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotEqual
import com.google.firebase.dataconnect.testutil.property.arbitrary.shouldHaveSameValueAs
import com.google.firebase.dataconnect.testutil.property.arbitrary.someValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.util.MaybeValue.NoValueException
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import java.util.Objects
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
  fun `Empty getOrThrow() throws NoValueException`() {
    val exception = shouldThrow<NoValueException> { MaybeValue.Empty.getOrThrow() }
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
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val maybeValueValue = MaybeValue.Value(value?.value)
      MaybeValue.Empty.equals(maybeValueValue) shouldBe false
    }
  }

  // endregion

  // region Tests for MaybeValue.Value.value

  @Test
  fun `Value(null) value is null`() {
    val maybeValue = MaybeValue.Value(null)
    maybeValue.value.shouldBeNull()
  }

  @Test
  fun `Value(non-null) value is the same value that was given to the constructor`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value, valueCopy) ->
      val maybeValue = MaybeValue.Value(value)

      maybeValue.value.shouldHaveSameValueAs(value, valueCopy)
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

      result.shouldHaveSameValueAs(value, valueCopy)
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

      result.shouldHaveSameValueAs(value, valueCopy)
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

      result.shouldHaveSameValueAs(value, valueCopy)
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

      result.shouldHaveSameValueAs(value, valueCopy)
      block.callCount shouldBe 0
    }
  }

  // endregion

  // region Tests for ifEmpty extension function

  @Test
  fun `Empty ifEmpty() calls block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val block = BlockReturningUnit()

      val result = MaybeValue.Empty.ifEmpty(block)

      result shouldBeSameInstanceAs MaybeValue.Empty
      block.callCount shouldBe 1
    }
  }

  @Test
  fun `Value(null) ifEmpty() does not call block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val maybeValue = MaybeValue.Value(null)
      val block = BlockThrowing("block should not be called [dddrp5v4jt]")

      val result = maybeValue.ifEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      block.callCount shouldBe 0
    }
  }

  @Test
  fun `Value(non-null) ifEmpty() does not call block`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValue = MaybeValue.Value(value)
      val block = BlockThrowing("block should not be called [a7axvtqker]")

      val result = maybeValue.ifEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      block.callCount shouldBe 0
    }
  }

  @Test
  fun `MaybeValue ifEmpty() with Empty receiver calls block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val maybeValue: MaybeValue<*> = MaybeValue.Empty
      val block = BlockReturningUnit()

      val result = maybeValue.ifEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      block.callCount shouldBe 1
    }
  }

  @Test
  fun `MaybeValue ifEmpty() with Value(null) receiver does not call block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val maybeValue: MaybeValue<*> = MaybeValue.Value(null)
      val block = BlockThrowing("block should not be called [y79ey8582t]")

      val result = maybeValue.ifEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      block.callCount shouldBe 0
    }
  }

  @Test
  fun `MaybeValue ifEmpty() with Value(non-null) receiver does not call block`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValue: MaybeValue<*> = MaybeValue.Value(value)
      val block = BlockThrowing("block should not be called [jdes7ymjn9]")

      val result = maybeValue.ifEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      block.callCount shouldBe 0
    }
  }

  // endregion

  // region Tests for ifNonEmpty extension function

  @Test
  fun `Empty ifNonEmpty() does not call block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val block = BlockThrowingWithParameter<Nothing>("block should not be called [tgbfxty2kr]")

      val result = MaybeValue.Empty.ifNonEmpty(block)

      result shouldBeSameInstanceAs MaybeValue.Empty
      block.calls.shouldBeEmpty()
    }
  }

  @Test
  fun `Value(null) ifNonEmpty() calls block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val maybeValue = MaybeValue.Value(null)
      val block = BlockWithParameter<Nothing?>()

      val result = maybeValue.ifNonEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      block.calls shouldHaveSingleElement null
    }
  }

  @Test
  fun `Value(non-null) ifNonEmpty() calls block`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValue = MaybeValue.Value(value)
      val block = BlockWithParameter<Any>()

      val result = maybeValue.ifNonEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      block.calls shouldHaveSingleElement value
    }
  }

  @Test
  fun `MaybeValue ifNonEmpty() with Empty receiver does not call block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val maybeValue: MaybeValue<Nothing?> = MaybeValue.Empty
      val block = BlockThrowingWithParameter<Nothing?>("block should not be called [aensstz8am]")

      val result = maybeValue.ifNonEmpty(block)

      result shouldBeSameInstanceAs MaybeValue.Empty
      block.calls.shouldBeEmpty()
    }
  }

  @Test
  fun `MaybeValue ifNonEmpty() with Value(null) receiver calls the block`() = runTest {
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val maybeValue: MaybeValue<*> = MaybeValue.Value(null)
      val block = BlockWithParameter<Any?>()

      val result = maybeValue.ifNonEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      block.calls shouldHaveSingleElement null
    }
  }

  @Test
  fun `MaybeValue ifNonEmpty() with Value(non-null) receiver calls the block`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val maybeValue: MaybeValue<*> = MaybeValue.Value(value)
      val block = BlockWithParameter<Any?>()

      val result = maybeValue.ifNonEmpty(block)

      result shouldBeSameInstanceAs maybeValue
      block.calls shouldHaveSingleElement value
    }
  }

  // endregion

  // region Tests for ifNonEmpty extension function

  @Test
  fun `Empty fold() calls onEmpty`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val onEmpty = BlockReturning(value?.value)
      val onNonEmpty =
        BlockThrowingWithParameter<Nothing>("onNonEmpty should not be called [nrxvx3qgvf]")

      val result = MaybeValue.Empty.fold(onEmpty, onNonEmpty)

      result.shouldHaveSameValueAs(value)
      onEmpty.callCount shouldBe 1
      onNonEmpty.calls.shouldBeEmpty()
    }
  }

  @Test
  fun `Value(null) fold() calls onNonEmpty`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val maybeValue = MaybeValue.Value(null)
      val onEmpty = BlockThrowing("onEmpty should not be called [f7gw69tray]")
      val onNonEmpty = BlockReturningWithParameter<Nothing?, _>(value?.value)

      val result = maybeValue.fold(onEmpty, onNonEmpty)

      result.shouldHaveSameValueAs(value)
      onEmpty.callCount shouldBe 0
      onNonEmpty.calls shouldHaveSingleElement null
    }
  }

  @Test
  fun `Value(non-null) fold() calls onNonEmpty`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.someValue().orNull(nullProbability = 0.3))) {
      (value1, value2) ->
      val maybeValue = MaybeValue.Value(value1?.value)
      val onEmpty = BlockThrowing("onEmpty should not be called [sg2pvssp4j]")
      val onNonEmpty = BlockReturningWithParameter<Any?, _>(value2?.value)

      val result = maybeValue.fold(onEmpty, onNonEmpty)

      result.shouldHaveSameValueAs(value2)
      onEmpty.callCount shouldBe 0
      onNonEmpty.calls shouldHaveSingleElement value1?.value
    }
  }

  @Test
  fun `MaybeValue fold() with Empty receiver calls onEmpty`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val maybeValue: MaybeValue<Nothing?> = MaybeValue.Empty
      val onEmpty = BlockReturning(value?.value)
      val onNonEmpty =
        BlockThrowingWithParameter<Nothing?>("onNonEmpty should not be called [p8kw29xp7v]")

      val result = maybeValue.fold(onEmpty, onNonEmpty)

      result.shouldHaveSameValueAs(value)
      onEmpty.callCount shouldBe 1
      onNonEmpty.calls.shouldBeEmpty()
    }
  }

  @Test
  fun `MaybeValue fold() with Value(null) receiver calls the onNonEmpty`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val maybeValue: MaybeValue<*> = MaybeValue.Value(null)
      val onEmpty = BlockThrowing("onEmpty should not be called [kh9bm5qpdm]")
      val onNonEmpty = BlockReturningWithParameter<Any?, _>(value?.value)

      val result = maybeValue.fold(onEmpty, onNonEmpty)

      result.shouldHaveSameValueAs(value)
      onEmpty.callCount shouldBe 0
      onNonEmpty.calls shouldHaveSingleElement null
    }
  }

  @Test
  fun `MaybeValue fold() with Value(non-null) receiver calls the block`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.someValue().orNull(nullProbability = 0.3))) {
      (value1, value2) ->
      val maybeValue: MaybeValue<*> = MaybeValue.Value(value1?.value)
      val onEmpty = BlockThrowing("onEmpty should not be called [sg2pvssp4j]")
      val onNonEmpty = BlockReturningWithParameter<Any?, _>(value2?.value)

      val result = maybeValue.fold(onEmpty, onNonEmpty)

      result.shouldHaveSameValueAs(value2)
      onEmpty.callCount shouldBe 0
      onNonEmpty.calls shouldHaveSingleElement value1?.value
    }
  }

  // endregion

}

// region Helper classes, functions, and properties

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
    UnrelatedType,
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
        MaybeValueValueNotEqualPair.Dimension.UnrelatedType -> valueArb.bind()
      }
    MaybeValueValueNotEqualPair(value1 = maybeValue1, value2 = value2, dimension)
  }
}

// endregion
