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

@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.StandardDeviationMode
import com.google.firebase.dataconnect.testutil.standardDeviation
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.math.pow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IntWithEvenNumDigitsDistributionUnitTest {

  @Test
  fun `intWithEvenNumDigitsDistribution produces both positive and negative values`() = runTest {
    var positiveCount = 0
    var negativeCount = 0
    var zeroCount = 0

    checkAll(propTestConfig, Arb.intWithEvenNumDigitsDistribution()) { value ->
      if (value > 0) {
        positiveCount++
      } else if (value < 0) {
        negativeCount++
      } else {
        check(value == 0)
        zeroCount++
      }
    }

    assertSoftly {
      withClue("positiveCount") { positiveCount shouldBeGreaterThan 0 }
      withClue("negativeCount") { negativeCount shouldBeGreaterThan 0 }
      withClue("zeroCount") { zeroCount shouldBeGreaterThan 0 }
    }
  }

  @Test
  fun `intWithEvenNumDigitsDistribution produces all num digits`() = runTest {
    val numDigitsGenerated = mutableSetOf<Int>()

    checkAll(propTestConfig, Arb.intWithEvenNumDigitsDistribution()) { value ->
      val numDigits = value.countBase10Digits()
      numDigitsGenerated.add(numDigits)
    }

    numDigitsGenerated shouldContainExactlyInAnyOrder (1..10).toSet()
  }

  @Test
  fun `intWithEvenNumDigitsDistribution produces a range of num digits`() = runTest {
    val countByNumDigits = mutableMapOf<Int, Int>()

    checkAll(propTestConfig, Arb.intWithEvenNumDigitsDistribution()) { value ->
      val numDigits = value.countBase10Digits()
      val oldCount = countByNumDigits.getOrDefault(numDigits, 0)
      countByNumDigits[numDigits] = oldCount + 1
    }

    val standardDeviation = countByNumDigits.values.standardDeviation(StandardDeviationMode.Sample)
    withClue("countByNumDigits=${countByNumDigits.toSortedMap().print().value}") {
      standardDeviation shouldBeLessThan 12.0
    }
  }

  @Test
  fun `intWithEvenNumDigitsDistribution respects the given range`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.int())) { extents ->
      val (first, last) = extents.sorted()
      val range = first..last
      val arb = Arb.intWithEvenNumDigitsDistribution(range)

      val sample = arb.bind()

      sample shouldBeIn range
    }
  }

  @Test
  fun `intWithEvenNumDigitsDistribution produces a range of num digits in the given range`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.intWithEvenNumDigitsDistribution().distinctPair { value1, value2 ->
          value1.countBase10Digits() == value2.countBase10Digits()
        }
      ) { extents ->
        val (first, last) = extents.toList().sorted()
        check(first.countBase10Digits() != last.countBase10Digits())
        val range = first..last
        val arb = Arb.intWithEvenNumDigitsDistribution(range)
        val countByNumDigits = mutableMapOf<Int, Int>()

        repeat(propTestConfig.iterations!!) {
          val numDigits = arb.bind().countBase10Digits()
          val oldCount = countByNumDigits.getOrDefault(numDigits, 0)
          countByNumDigits[numDigits] = oldCount + 1
        }

        val standardDeviation =
          countByNumDigits.values.standardDeviation(StandardDeviationMode.Sample)

        val growthRate = 4.0.pow(1.0 / (countByNumDigits.size))
        val maxStandardDeviation = 12.0 * growthRate.pow(19 - countByNumDigits.size - 1)
        withClue("countByNumDigits=${countByNumDigits.toSortedMap().print().value}") {
          standardDeviation shouldBeLessThan maxStandardDeviation
        }
      }
    }

  @Test
  fun `nonNegativeIntWithEvenNumDigitsDistribution produces non-negative values`() = runTest {
    checkAll(propTestConfig, Arb.nonNegativeIntWithEvenNumDigitsDistribution()) { value ->
      value shouldBeGreaterThanOrEqual 0
    }
  }
}

private val propTestConfig = PropTestConfig(iterations = 1000)

private fun Int.countBase10Digits(): Int {
  val digitCount = toString(10).length
  return if (this >= 0) digitCount else digitCount - 1
}
