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
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.property.arbitrary.SomeValueArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.hasSameValueAs
import com.google.firebase.dataconnect.testutil.property.arbitrary.maybeValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.nonEmptyMaybeValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.shouldHaveSameValueAs
import com.google.firebase.dataconnect.testutil.property.arbitrary.someValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClearableValueUnitTest {

  // region Tests for ClearableValue constructor

  @Test
  fun `primary constructor populates state with the given object`() = runTest {
    checkAll(propTestConfig, Arb.maybeValue()) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)

      clearableValue.state.value shouldBeSameInstanceAs maybeValue
    }
  }

  @Test
  fun `secondary constructor populates state with the given non-null value`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val clearableValue = ClearableValue(value.value)

      clearableValue.state.value.shouldBeValueFromSample(value)
    }
  }

  @Test
  fun `secondary constructor populates state with the given null value`() {
    val clearableValue = ClearableValue(null)

    clearableValue.state.value.shouldBeInstanceOf<MaybeValue.Value<*>>().value.shouldBeNull()
  }

  // endregion

  // region Tests for ClearableValue.clear()

  @Test
  fun `clear() when initialized with Empty`() {
    val clearableValue = ClearableValue(MaybeValue.Empty)

    val result = clearableValue.clear()

    result shouldBe MaybeValue.Empty
    clearableValue.state.value shouldBe MaybeValue.Empty
  }

  @Test
  fun `clear() when initialized with Value(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.nonEmptyMaybeValue()) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)

      val result = clearableValue.clear()

      result shouldBeSameInstanceAs maybeValue
      clearableValue.state.value shouldBe MaybeValue.Empty
    }
  }

  @Test
  fun `clear() when initialized with Value(null)`() {
    val clearableValue = ClearableValue(null)
    val stateBefore = clearableValue.state.value

    val result = clearableValue.clear()

    result shouldBeSameInstanceAs stateBefore
    clearableValue.state.value shouldBe MaybeValue.Empty
  }

  @Test
  fun `clear() multiple times`() = runTest {
    checkAll(
      propTestConfig,
      Arb.maybeValue(Arb.someValue().map { it.value }.orNull(nullProbability = 0.3))
    ) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)

      val clearResults =
        List(10) {
          val clearResult = clearableValue.clear()
          clearableValue.state.value shouldBe MaybeValue.Empty
          clearResult
        }

      clearResults[0] shouldBeSameInstanceAs maybeValue
      clearResults.forEachIndexed { index, clearResult ->
        if (index > 0) {
          withClue("index=$index") { clearResult shouldBe MaybeValue.Empty }
        }
      }
    }
  }

  @Test
  fun `clear() concurrent calls`() = runTest {
    checkAll(
      propTestConfig,
      Arb.nonEmptyMaybeValue(Arb.someValue().map { it.value }.orNull(nullProbability = 0.3))
    ) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)
      val latch = SuspendingCountDownLatch(50)

      val jobs =
        List(latch.count) { jobIndex ->
          backgroundScope.async(Dispatchers.Default) {
            latch.countDown().await()
            val clearResult = clearableValue.clear()
            withClue("jobIndex=$jobIndex") { clearableValue.isCleared shouldBe true }
            clearResult
          }
        }
      val jobResults = jobs.awaitAll()

      withClue("successes") { jobResults.count { it === maybeValue } shouldBe 1 }
      withClue("failures") { jobResults.count { it == MaybeValue.Empty } shouldBe jobs.size - 1 }
      clearableValue.state.value shouldBe MaybeValue.Empty
    }
  }

  // endregion

  // region Tests for ClearableValue.toString()

  @Test
  fun `toString() when cleared`() {
    val clearableValue = ClearableValue(MaybeValue.Empty)
    clearableValue.toString() shouldBe "<cleared>"
  }

  @Test
  fun `toString() when value is null`() {
    val clearableValue = ClearableValue<Any?>(null)
    clearableValue.toString() shouldBe null.toString()
  }

  @Test
  fun `toString() when value is non-null`() = runTest {
    checkAll(propTestConfig, Arb.nonEmptyMaybeValue()) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)
      clearableValue.toString() shouldBe maybeValue.value.toString()
    }
  }

  @Test
  fun `toString() after clear()`() = runTest {
    checkAll(
      propTestConfig,
      Arb.maybeValue(Arb.someValue().map { it.value }.orNull(nullProbability = 0.3))
    ) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)
      clearableValue.clear()
      clearableValue.toString() shouldBe "<cleared>"
    }
  }

  // endregion

  // region Tests for ClearableValue.isCleared

  @Test
  fun `isCleared when initialized with Empty`() {
    val clearableValue = ClearableValue(MaybeValue.Empty)
    clearableValue.isCleared shouldBe true
  }

  @Test
  fun `isCleared when initialized with Value(non-null)`() = runTest {
    checkAll(propTestConfig, Arb.nonEmptyMaybeValue()) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)
      clearableValue.isCleared shouldBe false
    }
  }

  @Test
  fun `isCleared when initialized with Value(null)`() {
    val clearableValue = ClearableValue(MaybeValue.Value(null))
    clearableValue.isCleared shouldBe false
  }

  @Test
  fun `isCleared after clear()`() = runTest {
    checkAll(
      propTestConfig,
      Arb.maybeValue(Arb.someValue().map { it.value }.orNull(nullProbability = 0.3))
    ) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)
      clearableValue.clear()
      clearableValue.isCleared shouldBe true
    }
  }

  // endregion

  // region Tests for ClearableValue.getOrNull()

  @Test
  fun `getOrNull() when cleared`() {
    val clearableValue = ClearableValue(MaybeValue.Empty)
    clearableValue.getOrNull().shouldBeNull()
  }

  @Test
  fun `getOrNull() when value is null`() {
    val clearableValue = ClearableValue(null)
    clearableValue.getOrNull().shouldBeNull()
  }

  @Test
  fun `getOrNull() when value is non-null`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val clearableValue = ClearableValue(value.value)
      clearableValue.getOrNull().shouldHaveSameValueAs(value)
    }
  }

  @Test
  fun `getOrNull() after clear()`() = runTest {
    checkAll(
      propTestConfig,
      Arb.maybeValue(Arb.someValue().map { it.value }.orNull(nullProbability = 0.3))
    ) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)
      clearableValue.clear()
      clearableValue.getOrNull().shouldBeNull()
    }
  }

  // endregion

  // region Tests for ClearableValue.getOrThrow()

  @Test
  fun `getOrThrow() when cleared`() {
    val clearableValue = ClearableValue(MaybeValue.Empty)
    shouldThrow<MaybeValue.NoValueException> { clearableValue.getOrThrow() }
  }

  @Test
  fun `getOrThrow() when value is null`() {
    val clearableValue = ClearableValue<Any?>(null)
    clearableValue.getOrThrow().shouldBeNull()
  }

  @Test
  fun `getOrThrow() when value is non-null`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val clearableValue = ClearableValue(value.value)
      clearableValue.getOrThrow().shouldHaveSameValueAs(value)
    }
  }

  @Test
  fun `getOrThrow() after clear()`() = runTest {
    checkAll(
      propTestConfig,
      Arb.maybeValue(Arb.someValue().map { it.value }.orNull(nullProbability = 0.3))
    ) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)
      clearableValue.clear()
      shouldThrow<MaybeValue.NoValueException> { clearableValue.getOrThrow() }
    }
  }

  // endregion

  // region Tests for ClearableValue.getOrElse()

  @Test
  fun `getOrElse() when cleared`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val clearableValue = ClearableValue(MaybeValue.Empty)
      val block = BlockReturning(value?.value)

      val result = clearableValue.getOrElse(block)

      result.shouldHaveSameValueAs(value)
      block.callCount shouldBe 1
    }
  }

  @Test
  fun `getOrElse() when value is non-null`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val clearableValue = ClearableValue(value.value)
      val block = BlockThrowing("block should not be called [r8z2x9n3we]")

      val result = clearableValue.getOrElse(block)

      result.shouldHaveSameValueAs(value)
      block.callCount shouldBe 0
    }
  }

  @Test
  fun `getOrElse() when value is null`() {
    val clearableValue = ClearableValue(null)
    val block = BlockThrowing("block should not be called [p2v9w8z7qe]")

    val result = clearableValue.getOrElse(block)

    result.shouldBeNull()
    block.callCount shouldBe 0
  }

  // endregion

  // region Tests for ClearableValue.clearOrElse()

  @Test
  fun `clearOrElse() when cleared`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val clearableValue = ClearableValue(MaybeValue.Empty)
      val block = BlockReturning(value?.value)

      val result = clearableValue.clearOrElse(block)

      result.shouldHaveSameValueAs(value)
      block.callCount shouldBe 1
      clearableValue.isCleared shouldBe true
    }
  }

  @Test
  fun `clearOrElse() when value is non-null`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val clearableValue = ClearableValue(value.value)
      val block = BlockThrowing("block should not be called [kc2vpc2xt9]")

      val result = clearableValue.clearOrElse(block)

      result.shouldHaveSameValueAs(value)
      block.callCount shouldBe 0
      clearableValue.isCleared shouldBe true
    }
  }

  @Test
  fun `clearOrElse() when value is null`() {
    val clearableValue = ClearableValue(null)
    val block = BlockThrowing("block should not be called [vrnv85ea7m]")

    val result = clearableValue.clearOrElse(block)

    result.shouldBeNull()
    block.callCount shouldBe 0
    clearableValue.isCleared shouldBe true
  }

  @Test
  fun `clearOrElse() concurrent calls`() = runTest {
    val someNullableValuePairArb =
      Arb.someValue().orNull(nullProbability = 0.3).distinctPair { sample1, sample2 ->
        sample1?.value.hasSameValueAs(sample2)
      }
    checkAll(
      propTestConfig,
      someNullableValuePairArb,
    ) { (initialValue, blockReturnValue) ->
      val clearableValue = ClearableValue(MaybeValue.Value(initialValue?.value))
      val latch = SuspendingCountDownLatch(50)
      val block = BlockReturning(blockReturnValue?.value)

      val jobs =
        List(latch.count) { jobIndex ->
          backgroundScope.async(Dispatchers.Default) {
            latch.countDown().await()
            val clearOrElseResult = clearableValue.clearOrElse(block)
            withClue("jobIndex=$jobIndex") { clearableValue.isCleared shouldBe true }
            clearOrElseResult
          }
        }
      val jobResults = jobs.awaitAll()

      withClue("successes") { jobResults.count { it.hasSameValueAs(initialValue) } shouldBe 1 }
      withClue("failures") {
        jobResults.count { it.hasSameValueAs(blockReturnValue) } shouldBe jobs.size - 1
      }
      clearableValue.state.value shouldBe MaybeValue.Empty
    }
  }

  // endregion

  // region Tests for ClearableValue.ifCleared()

  @Test
  fun `ifCleared() when cleared`() {
    val clearableValue = ClearableValue(MaybeValue.Empty)
    val block = BlockReturningUnit()

    clearableValue.ifCleared(block)

    block.callCount shouldBe 1
  }

  @Test
  fun `ifCleared() when value is non-null`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val clearableValue = ClearableValue(value.value)
      val block = BlockThrowing("block should not be called [v9w8z7qea1]")

      clearableValue.ifCleared(block)

      block.callCount shouldBe 0
    }
  }

  @Test
  fun `ifCleared() when value is null`() = runTest {
    val clearableValue = ClearableValue(null)
    val block = BlockThrowing("block should not be called [v9w8z7qea1]")

    clearableValue.ifCleared(block)

    block.callCount shouldBe 0
  }

  @Test
  fun `ifCleared() after clear()`() = runTest {
    checkAll(
      propTestConfig,
      Arb.maybeValue(Arb.someValue().map { it.value }.orNull(nullProbability = 0.3))
    ) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)
      clearableValue.clear()
      val block = BlockReturningUnit()

      clearableValue.ifCleared(block)

      block.callCount shouldBe 1
    }
  }

  // endregion

  // region Tests for ClearableValue.ifNotCleared()

  @Test
  fun `ifNotCleared() when cleared`() {
    val clearableValue = ClearableValue(MaybeValue.Empty)
    val block = BlockThrowingWithParameter<Nothing>("block should not be called [qea1v9w8z7]")

    clearableValue.ifNotCleared(block)

    block.calls.shouldBeEmpty()
  }

  @Test
  fun `ifNotCleared() when value is non-null`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val clearableValue = ClearableValue(value.value)
      val block = BlockWithParameter<Any>()

      clearableValue.ifNotCleared(block)

      block.calls.shouldHaveSingleElement { it.hasSameValueAs(value) }
    }
  }

  @Test
  fun `ifNotCleared() when value is null`() {
    val clearableValue = ClearableValue<Any?>(null)
    val block = BlockWithParameter<Any?>()

    clearableValue.ifNotCleared(block)

    block.calls.shouldContainExactly(null)
  }

  @Test
  fun `ifNotCleared() after clear()`() = runTest {
    checkAll(
      propTestConfig,
      Arb.maybeValue(Arb.someValue().map { it.value }.orNull(nullProbability = 0.3))
    ) { maybeValue ->
      val clearableValue = ClearableValue(maybeValue)
      clearableValue.clear()
      val block = BlockThrowingWithParameter<Any?>("block should not be called [gfb2wb7qnm]")

      clearableValue.ifNotCleared(block)

      block.calls.shouldBeEmpty()
    }
  }

  // endregion

  // region Tests for ClearableValue.fold()

  @Test
  fun `fold() when cleared`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val clearableValue = ClearableValue(MaybeValue.Empty)
      val onCleared = BlockReturning(value?.value)
      val onNotCleared =
        BlockThrowingWithParameter<Nothing>("onNotCleared() should not be called [z7qea1v9w8]")

      val result = clearableValue.fold(onCleared, onNotCleared)

      result.shouldHaveSameValueAs(value)
      onCleared.callCount shouldBe 1
      onNotCleared.calls.shouldBeEmpty()
    }
  }

  @Test
  fun `fold() when value is non-null`() = runTest {
    checkAll(propTestConfig, Arb.someValue(), Arb.someValue().orNull(nullProbability = 0.3)) {
      value1,
      value2 ->
      val clearableValue = ClearableValue(value1.value)
      val onCleared = BlockThrowing("onCleared() should not be called [a1v9w8z7qe]")
      val onNotCleared = BlockReturningWithParameter<Any, _>(value2?.value)

      val result = clearableValue.fold(onCleared, onNotCleared)

      result.shouldHaveSameValueAs(value2)
      onCleared.callCount shouldBe 0
      onNotCleared.calls.shouldHaveSingleElement { it.hasSameValueAs(value1) }
    }
  }

  @Test
  fun `fold() when value is null`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val clearableValue = ClearableValue<Any?>(null)
      val onCleared = BlockThrowing("onCleared() should not be called [v9w8z7qea1]")
      val onNotCleared = BlockReturningWithParameter<Any?, _>(value?.value)

      val result = clearableValue.fold(onCleared, onNotCleared)

      result.shouldHaveSameValueAs(value)
      onCleared.callCount shouldBe 0
      onNotCleared.calls.shouldHaveSingleElement(null)
    }
  }

  // endregion
}

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
