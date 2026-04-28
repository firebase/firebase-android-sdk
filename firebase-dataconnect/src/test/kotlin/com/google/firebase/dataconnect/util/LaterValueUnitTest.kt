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

import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.property.arbitrary.SomeValueArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.hasSameValueAs
import com.google.firebase.dataconnect.testutil.property.arbitrary.maybeValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.nonEmptyMaybeValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.shouldHaveSameValueAs
import com.google.firebase.dataconnect.testutil.property.arbitrary.someValue
import com.google.firebase.dataconnect.util.MaybeValue.NoValueException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LaterValueUnitTest {

  // region Tests for LaterValue constructor

  @Test
  fun `constructor default value is empty`() {
    val laterValue = LaterValue<Nothing?>()

    laterValue.state.value shouldBe MaybeValue.Empty
  }

  @Test
  fun `constructor populates state with the given object`() = runTest {
    checkAll(propTestConfig, Arb.maybeValue()) { maybeValue ->
      val laterValue = LaterValue(maybeValue)

      laterValue.state.value shouldBeSameInstanceAs maybeValue
    }
  }

  @Test
  fun `constructor populates state with the given MaybeValue_Value(null)`() {
    val maybeValue = MaybeValue.Value(null)

    val laterValue = LaterValue(maybeValue)

    laterValue.state.value shouldBeSameInstanceAs maybeValue
  }

  // endregion

  // region Tests for LaterValue.set()

  @Test
  fun `set(non-null) when initialized with Empty`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val laterValue = LaterValue<Any>()

      laterValue.set(value.value)

      laterValue.state.value.shouldBeValueFromSample(value)
    }
  }

  @Test
  fun `set(null) when initialized with Empty`() {
    val laterValue = LaterValue<Nothing?>()

    laterValue.set(null)

    laterValue.state.value.shouldBeInstanceOf<MaybeValue.Value<*>>().value.shouldBeNull()
  }

  @Test
  fun `set(non-null) when initialized with Value(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.nonEmptyMaybeValue(), Arb.someValue()) { initialValue, (newValue)
      ->
      val laterValue = LaterValue(initialValue)
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(newValue) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(null) when initialized with Value(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.nonEmptyMaybeValue()) { maybeValue ->
      val laterValue = LaterValue<Any?>(maybeValue)
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(null) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(non-null) when initialized with Value(null)`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val laterValue = LaterValue<Any?>(MaybeValue.Value(null))
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(value) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(null) when initialized with Value(null)`() {
    val laterValue = LaterValue<Any?>(MaybeValue.Value(null))
    val stateBefore = laterValue.state.value

    shouldThrow<IllegalStateException> { laterValue.set(null) }
    laterValue.state.value shouldBeSameInstanceAs stateBefore
  }

  @Test
  fun `set(null) after successful set(null)`() {
    val laterValue = LaterValue<Any?>()
    laterValue.set(null)
    val stateBefore = laterValue.state.value

    shouldThrow<IllegalStateException> { laterValue.set(null) }
    laterValue.state.value shouldBeSameInstanceAs stateBefore
  }

  @Test
  fun `set(null) after successful set(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val laterValue = LaterValue<Any?>()
      laterValue.set(value)
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(null) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(non-null) after successful set(null)`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val laterValue = LaterValue<Any?>()
      laterValue.set(null)
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(value) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(non-null) after successful set(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.someValue().pair()) { (value1, value2) ->
      val laterValue = LaterValue<Any>()
      laterValue.set(value1.value)
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(value2.value) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(non-null) after successful set(same object)`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val laterValue = LaterValue<Any>()
      laterValue.set(value)
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(value) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set() concurrent calls, first succeeds, subsequent fail`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.someValue(), 2..50)) { values ->
      val laterValue = LaterValue<Any>()
      val latch = SuspendingCountDownLatch(values.size)

      // Use a SuspendingCountDownLatch to create a "thundering herd" of coroutines calling set().
      val jobs =
        values.map { valueSample ->
          backgroundScope.async(Dispatchers.Default) {
            val value = valueSample.value
            latch.countDown().await()
            laterValue.runCatching { set(value) }
          }
        }
      val jobResults = jobs.awaitAll()

      withClue("successes") { jobResults.count { it.isSuccess } shouldBe 1 }
      withClue("failures") { jobResults.count { it.isFailure } shouldBe jobs.size - 1 }
      val value = laterValue.state.value.shouldBeInstanceOf<MaybeValue.Value<*>>().value
      values.count { value.hasSameValueAs(it) } shouldBeGreaterThan 0
    }
  }

  // endregion

  // region Tests for LaterValue.toString()

  @Test
  fun `toString() when no value set`() {
    val laterValue = LaterValue<Nothing>()
    laterValue.toString() shouldBe "<unset>"
  }

  @Test
  fun `toString() when value initialized to null`() {
    val laterValue = LaterValue(MaybeValue.Value(null))
    laterValue.toString() shouldBe null.toString()
  }

  @Test
  fun `toString() when value initialized to non-null`() = runTest {
    checkAll(propTestConfig, Arb.nonEmptyMaybeValue()) { maybeValue ->
      val laterValue = LaterValue(maybeValue)
      laterValue.toString() shouldBe maybeValue.value.toString()
    }
  }

  @Test
  fun `toString() after set(null)`() {
    val laterValue = LaterValue<Nothing?>()
    laterValue.set(null)
    laterValue.toString() shouldBe null.toString()
  }

  @Test
  fun `toString() after set(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val laterValue = LaterValue<Any>()
      laterValue.set(value)
      laterValue.toString() shouldBe value.toString()
    }
  }

  // endregion

  // region Tests for LaterValue.isSet

  @Test
  fun `isSet when no value set`() {
    val laterValue = LaterValue<Nothing>()
    laterValue.isSet shouldBe false
  }

  @Test
  fun `isSet when value initialized to null`() {
    val laterValue = LaterValue(MaybeValue.Value(null))
    laterValue.isSet shouldBe true
  }

  @Test
  fun `isSet when value initialized to non-null`() = runTest {
    checkAll(propTestConfig, Arb.nonEmptyMaybeValue()) { maybeValue ->
      val laterValue = LaterValue(maybeValue)
      laterValue.isSet shouldBe true
    }
  }

  @Test
  fun `isSet after set(null)`() {
    val laterValue = LaterValue<Nothing?>()
    laterValue.set(null)
    laterValue.isSet shouldBe true
  }

  @Test
  fun `isSet after set(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val laterValue = LaterValue<Any>()
      laterValue.set(value)
      laterValue.isSet shouldBe true
    }
  }

  // endregion

  // region Tests for LaterValue.getOrNull

  @Test
  fun `getOrNull() when no value set`() {
    val laterValue = LaterValue<Nothing>()
    laterValue.getOrNull().shouldBeNull()
  }

  @Test
  fun `getOrNull() when value initialized to null`() {
    val laterValue = LaterValue(MaybeValue.Value(null))
    laterValue.getOrNull().shouldBeNull()
  }

  @Test
  fun `getOrNull() when value initialized to non-null`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val laterValue = LaterValue(MaybeValue.Value(value.value))
      laterValue.getOrNull().shouldHaveSameValueAs(value)
    }
  }

  @Test
  fun `getOrNull() after set(null)`() {
    val laterValue = LaterValue<Nothing?>()
    laterValue.set(null)
    laterValue.getOrNull().shouldBeNull()
  }

  @Test
  fun `getOrNull() after set(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val laterValue = LaterValue<Any>()
      laterValue.set(value.value)
      laterValue.getOrNull().shouldHaveSameValueAs(value)
    }
  }

  // endregion

  // region Tests for LaterValue.getOrThrow

  @Test
  fun `getOrThrow() when no value set`() {
    val laterValue = LaterValue<Nothing>()
    shouldThrow<NoValueException> { laterValue.getOrThrow() }
  }

  @Test
  fun `getOrThrow() when value initialized to null`() {
    val laterValue = LaterValue(MaybeValue.Value(null))
    laterValue.getOrThrow().shouldBeNull()
  }

  @Test
  fun `getOrThrow() when value initialized to non-null`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val laterValue = LaterValue(MaybeValue.Value(value.value))
      laterValue.getOrThrow().shouldHaveSameValueAs(value)
    }
  }

  @Test
  fun `getOrThrow() after set(null)`() {
    val laterValue = LaterValue<Nothing?>()
    laterValue.set(null)
    laterValue.getOrThrow().shouldBeNull()
  }

  @Test
  fun `getOrThrow() after set(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val laterValue = LaterValue<Any>()
      laterValue.set(value.value)
      laterValue.getOrThrow().shouldHaveSameValueAs(value)
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

private fun MaybeValue<*>.shouldBeValueFromSample(sample: SomeValueArb.Sample) {
  shouldBeInstanceOf<MaybeValue.Value<*>>().value.shouldHaveSameValueAs(sample)
}

// endregion
