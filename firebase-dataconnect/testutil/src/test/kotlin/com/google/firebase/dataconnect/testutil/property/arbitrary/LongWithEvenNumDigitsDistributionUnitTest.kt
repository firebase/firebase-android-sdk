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

import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.countBase10Digits
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestTestutilPrinters
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlin.math.sign
import kotlinx.coroutines.test.runTest
import org.apache.commons.statistics.inference.ChiSquareTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LongWithEvenNumDigitsDistributionUnitTest {

  @get:Rule(order = Int.MIN_VALUE) val randomSeedTestRule = RandomSeedTestRule()

  private val rs: RandomSource by randomSeedTestRule.rs

  @Before
  fun registerPrinters() {
    registerDataConnectKotestTestutilPrinters()
  }

  @Test
  fun `longWithEvenNumDigitsDistribution produces both positive and negative values`() = runTest {
    var positiveCount = 0
    var negativeCount = 0
    var zeroCount = 0

    checkAll(propTestConfig, Arb.longWithEvenNumDigitsDistribution()) { value ->
      if (value > 0) {
        positiveCount++
      } else if (value < 0) {
        negativeCount++
      } else {
        check(value == 0L)
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
  fun `longWithEvenNumDigitsDistribution respects the given range`() = runTest {
    val rangeArb =
      Arb.twoValues(Arb.long()).map {
        val (first, last) = listOf(it.value1, it.value1 + it.value2).sorted()
        first..last
      }

    checkAll(propTestConfig, rangeArb) { range ->
      val arb = Arb.longWithEvenNumDigitsDistribution(range)

      val sample = arb.bind()

      sample shouldBeIn range
    }
  }

  @Test
  fun `longWithEvenNumDigitsDistribution produces the full unbiased range of num digits`() {
    val iterations = 1_000_000
    val observedCounts = LongArray(19)

    Arb.longWithEvenNumDigitsDistribution().samples(rs).take(iterations).forEach {
      observedCounts[it.value.countBase10Digits() - 1]++
    }

    withClue("observedCounts=${observedCounts.print().value}") {
      val expectedObservedCount = iterations.toDouble() / observedCounts.size
      val expectedCounts = DoubleArray(observedCounts.size) { expectedObservedCount }
      val significanceResult = ChiSquareTest.withDefaults().test(expectedCounts, observedCounts)
      withClue(significanceResult.print().value) {
        // Note: Larger values to reject() make stronger guarantees of lack of bias.
        significanceResult.reject(0.05).shouldBeFalse()
      }
    }
  }

  @Test
  fun `longWithEvenNumDigitsDistribution produces an unbiased range of num digits within the given range`() =
    runTest {
      fun LongRange.containsMultipleDigits(): Boolean =
        when {
          first in -9..9 && last in -9..9 -> false
          first.sign != last.sign -> true
          else -> first.countBase10Digits() != last.countBase10Digits()
        }
      val rangeArb =
        Arb.twoValues(Arb.long())
          .map {
            val (first, last) = listOf(it.value1, it.value1 + it.value2).sorted()
            first..last
          }
          .filter { it.containsMultipleDigits() }

      checkAll(propTestConfig.withIterations(100), rangeArb) { range ->
        val iterations = 100_000
        val observedCounts = LongArray(19)

        Arb.longWithEvenNumDigitsDistribution(range)
          .samples(randomSource())
          .take(iterations)
          .forEach { observedCounts[it.value.countBase10Digits() - 1]++ }

        val observationCountByDigitCount =
          observedCounts
            .mapIndexed { index, observationCount -> Pair(index, observationCount) }
            .filter { it.second > 0 }
            .associate { (index, observationCount) -> Pair(index + 1, observationCount) }
            .toSortedMap()
        withClue(
          "observations.size=${observationCountByDigitCount.size}, observations=${observationCountByDigitCount.print().value}"
        ) {
          val nonZeroObservedCount = observedCounts.filter { it != 0L }.toLongArray()
          check(nonZeroObservedCount.size > 1)
          val expectedObservedCount = iterations.toDouble() / nonZeroObservedCount.size
          val expectedCounts = DoubleArray(nonZeroObservedCount.size) { expectedObservedCount }
          val significanceResult =
            ChiSquareTest.withDefaults().test(expectedCounts, nonZeroObservedCount)
          withClue(
            "expectedObservedCount=$expectedObservedCount, ${significanceResult.print().value}"
          ) {
            // Note: Larger values to reject() make stronger guarantees of lack of bias.
            significanceResult.reject(0.0001).shouldBeFalse()
          }
        }
      }
    }

  @Test
  fun `nonNegativeLongWithEvenNumDigitsDistribution produces non-negative values`() = runTest {
    checkAll(propTestConfig, Arb.nonNegativeLongWithEvenNumDigitsDistribution()) { value ->
      value shouldBeGreaterThanOrEqual 0
    }
  }
}

private val propTestConfig =
  PropTestConfig(
    iterations = 1000,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )
