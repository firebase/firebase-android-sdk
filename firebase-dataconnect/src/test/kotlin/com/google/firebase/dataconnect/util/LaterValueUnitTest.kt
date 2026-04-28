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
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.shouldHaveSameValueAs
import com.google.firebase.dataconnect.testutil.property.arbitrary.someValue
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

  @Test
  fun `primary constructor default value is empty`() {
    val laterValue = LaterValue<Nothing?>()

    laterValue.state.value shouldBe MaybeValue.Empty
  }

  @Test
  fun `primary constructor populates state with the given object`() = runTest {
    checkAll(propTestConfig, Arb.maybeValue()) { maybeValue ->
      val laterValue = LaterValue(maybeValue)

      laterValue.state.value shouldBeSameInstanceAs maybeValue
    }
  }

  @Test
  fun `primary constructor populates state with the given MaybeValue_Value(null)`() {
    val maybeValue = MaybeValue.Value(null)

    val laterValue = LaterValue(maybeValue)

    laterValue.state.value shouldBeSameInstanceAs maybeValue
  }

  @Test
  fun `set(non-null) on a LaterValue initialized with Empty succeeds`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val laterValue = LaterValue<Any>()

      laterValue.set(value.value)

      laterValue.state.value.shouldBeValueFromSample(value)
    }
  }

  @Test
  fun `set(null) on a LaterValue initialized with Empty succeeds`() {
    val laterValue = LaterValue<Nothing?>()

    laterValue.set(null)

    laterValue.state.value.shouldBeInstanceOf<MaybeValue.Value<*>>().value.shouldBeNull()
  }

  @Test
  fun `set(non-null) on a LaterValue initialized with Value(non-null) throws`() = runTest {
    checkAll(propTestConfig, Arb.someValue().pair()) { (value1, value2) ->
      val laterValue = LaterValue(MaybeValue.Value(value1.value))
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(value2.value) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(null) on a LaterValue initialized with Value(non-null) throws`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val laterValue = LaterValue(MaybeValue.Value<Any?>(value.value))
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(null) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(non-null) on a LaterValue initialized with Value(null) throws`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { value ->
      val laterValue = LaterValue<Any?>(MaybeValue.Value(null))
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(value.value) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(null) on a LaterValue initialized with Value(null) throws`() {
    val laterValue = LaterValue<Any?>(MaybeValue.Value(null))
    val stateBefore = laterValue.state.value

    shouldThrow<IllegalStateException> { laterValue.set(null) }
    laterValue.state.value shouldBeSameInstanceAs stateBefore
  }

  @Test
  fun `set(null) on a LaterValue initialized with Empty then set to null throws`() {
    val laterValue = LaterValue<Any?>()
    laterValue.set(null)
    val stateBefore = laterValue.state.value

    shouldThrow<IllegalStateException> { laterValue.set(null) }
    laterValue.state.value shouldBeSameInstanceAs stateBefore
  }

  @Test
  fun `set(null) on a LaterValue initialized with Empty then set to non-null throws`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val laterValue = LaterValue<Any?>()
      laterValue.set(value)
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(null) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(non-null) on a LaterValue initialized with Empty then set to null throws`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { (value) ->
      val laterValue = LaterValue<Any?>()
      laterValue.set(null)
      val stateBefore = laterValue.state.value

      shouldThrow<IllegalStateException> { laterValue.set(value) }
      laterValue.state.value shouldBeSameInstanceAs stateBefore
    }
  }

  @Test
  fun `set(non-null) on a LaterValue initialized with Empty then set to non-null throws`() =
    runTest {
      checkAll(propTestConfig, Arb.someValue().pair()) { (value1, value2) ->
        val laterValue = LaterValue<Any>()
        laterValue.set(value1.value)
        val stateBefore = laterValue.state.value

        shouldThrow<IllegalStateException> { laterValue.set(value2.value) }
        laterValue.state.value shouldBeSameInstanceAs stateBefore
      }
    }

  @Test
  fun `set(non-null) on a LaterValue initialized with Empty then set to the same value throws`() =
    runTest {
      checkAll(propTestConfig, Arb.someValue()) { (value) ->
        val laterValue = LaterValue<Any>()
        laterValue.set(value)
        val stateBefore = laterValue.state.value

        shouldThrow<IllegalStateException> { laterValue.set(value) }
        laterValue.state.value shouldBeSameInstanceAs stateBefore
      }
    }

  @Test
  fun `set() succeeds exactly once and throws for all subsequent invocations`() = runTest {
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
