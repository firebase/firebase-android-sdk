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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.util.MaybeValue
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filterIsInstance
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.take
import io.kotest.property.checkAll
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.apache.commons.statistics.inference.ChiSquareTest
import org.junit.Before
import org.junit.Test

class MaybeValueArbUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  // region Tests for emptyMaybeValue()

  @Test
  fun `emptyMaybeValue() produces Empty`() = runTest {
    checkAll(propTestConfig, Arb.emptyMaybeValue()) { maybeValue ->
      maybeValue shouldBe MaybeValue.Empty
    }
  }

  // endregion

  // region Tests for nonEmptyMaybeValue(Arb<T>)

  @Test
  fun `nonEmptyMaybeValue(value) produces Value with given value`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.33)) { sample ->
      val arb = Arb.nonEmptyMaybeValue(Arb.constant(sample?.value))

      val result = arb.bind()

      result.value.shouldHaveSameValueAs(sample)
    }
  }

  // endregion

  // region Tests for nonEmptyMaybeValue()

  @Test
  fun `nonEmptyMaybeValue() produces Value objects`() = runTest {
    checkAll(propTestConfig, Arb.nonEmptyMaybeValue()) {}
  }

  // endregion

  // region Tests for maybeValue(Arb<T>, Double)

  @Test
  fun `maybeValue(Arb_T, Double) produces both Empty and Value`() =
    testArbProducesBothEmptyAndValue(Arb.maybeValue(Arb.someValue().map { it.value }))

  @Test
  fun `maybeValue(Arb_T, Double) produces values from the given Arb`() = runTest {
    val someValueArb = Arb.someValue().map { it.value }
    checkAll(propTestConfig, Arb.constant(null)) { _ ->
      val rememberArb = RememberArb(someValueArb)
      val arb = Arb.maybeValue(rememberArb).filterIsInstance<MaybeValue.Value<*>>()

      val values = List(10) { arb.bind() }

      values.map { it.value } shouldContainExactly rememberArb.generatedValues
    }
  }

  @Test
  fun `maybeValue(Arb_T, Double) respects the given emptyProbability for non edge cases`() =
    testArbRespectsEmptyProbability {
      Arb.maybeValue(Arb.someValue(), emptyProbability = it)
    }

  @Test
  fun `maybeValue(Arb_T, Double) respects emptyProbability=0`() = runTest {
    checkAll(propTestConfig, Arb.maybeValue(Arb.someValue(), emptyProbability = 0.0)) { sample ->
      sample.shouldBeInstanceOf<MaybeValue.Value<*>>()
    }
  }

  @Test
  fun `maybeValue(Arb_T, Double) respects emptyProbability=1`() = runTest {
    checkAll(propTestConfig, Arb.maybeValue(Arb.someValue(), emptyProbability = 1.0)) { sample ->
      sample.shouldBeInstanceOf<MaybeValue.Empty>()
    }
  }

  // endregion

  // region Tests for maybeValue(Double)

  @Test
  fun `maybeValue(Double) produces both Empty and Value`() =
    testArbProducesBothEmptyAndValue(Arb.maybeValue())

  @Test
  fun `maybeValue(Double) respects the given emptyProbability for non edge cases`() = runTest {
    testArbRespectsEmptyProbability { Arb.maybeValue(emptyProbability = it) }
  }

  @Test
  fun `maybeValue(Double) respects emptyProbability=0`() = runTest {
    checkAll(propTestConfig, Arb.maybeValue(emptyProbability = 0.0)) { sample ->
      sample.shouldBeInstanceOf<MaybeValue.Value<*>>()
    }
  }

  @Test
  fun `maybeValue(Double) respects emptyProbability=1`() = runTest {
    checkAll(propTestConfig, Arb.maybeValue(emptyProbability = 1.0)) { sample ->
      sample.shouldBeInstanceOf<MaybeValue.Empty>()
    }
  }

  // endregion

}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private fun testArbProducesBothEmptyAndValue(arb: Arb<MaybeValue<*>>) = runTest {
  val emptyCount = AtomicInteger(0)
  val nonEmptyCount = AtomicInteger(0)

  checkAll(propTestConfig, arb) { maybeValue ->
    when (maybeValue) {
      is MaybeValue.Empty -> emptyCount.incrementAndGet()
      is MaybeValue.Value -> nonEmptyCount.incrementAndGet()
    }
  }

  emptyCount.get() shouldBeGreaterThan 0
  nonEmptyCount.get() shouldBeGreaterThan 0
}

private fun testArbRespectsEmptyProbability(
  createArb: (emptyProbability: Double) -> Arb<MaybeValue<*>>
) = runTest {
  checkAll(propTestConfig, Arb.double(0.01..0.99)) { emptyProbability ->
    val arb = createArb(emptyProbability)

    var emptyCount: Long = 0
    var nonEmptyCount: Long = 0
    arb.take(1000, randomSource()).forEach {
      when (it) {
        MaybeValue.Empty -> emptyCount++
        is MaybeValue.Value -> nonEmptyCount++
      }
    }

    withClue("emptyCount=$emptyCount, nonEmptyCount=$nonEmptyCount") {
      val observedCounts = longArrayOf(emptyCount, nonEmptyCount)
      val expectedProportions = doubleArrayOf(emptyProbability, 1.0 - emptyProbability)
      val significanceResult =
        ChiSquareTest.withDefaults().test(expectedProportions, observedCounts)
      withClue(significanceResult.print().value) {
        significanceResult.reject(0.0001).shouldBeFalse()
      }
    }
  }
}
