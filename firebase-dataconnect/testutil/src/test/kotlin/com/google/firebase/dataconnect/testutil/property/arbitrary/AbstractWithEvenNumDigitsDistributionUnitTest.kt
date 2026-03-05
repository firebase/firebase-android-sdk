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
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestTestutilPrinters
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.apache.commons.statistics.inference.ChiSquareTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

abstract class AbstractWithEvenNumDigitsDistributionUnitTest<T : Comparable<T>, R : ClosedRange<T>>(
  private val maxDigitCount: Int,
  private val zero: T
) {
  @get:Rule(order = Int.MIN_VALUE) val randomSeedTestRule = RandomSeedTestRule()

  protected val rs: RandomSource by randomSeedTestRule.rs

  @Before
  fun registerPrinters() {
    registerDataConnectKotestTestutilPrinters()
  }

  abstract fun getSign(value: T): Int
  abstract fun countBase10Digits(value: T): Int
  abstract fun arbDistribution(): Arb<T>
  abstract fun arbDistribution(range: R): Arb<T>
  abstract fun arbNonNegativeDistribution(): Arb<T>
  abstract fun arbRange(): Arb<R>
  abstract fun arbMultipleDigitsRange(): Arb<R>
  abstract fun assertIn(value: T, range: R)
  abstract fun assertGreaterThanOrEqual(value: T, limit: T)

  @Test
  fun `distribution produces both positive and negative values`() = runTest {
    var positiveCount = 0
    var negativeCount = 0

    checkAll(propTestConfig, arbDistribution()) { value ->
      val cmp = value.compareTo(zero)
      if (cmp > 0) {
        positiveCount++
      } else if (cmp < 0) {
        negativeCount++
      }
    }

    check(positiveCount > 0 && negativeCount > 0)
    withClue("positiveCount=$positiveCount, negativeCount=$negativeCount") {
      val iterations = positiveCount + negativeCount
      val observedCounts = longArrayOf(positiveCount.toLong(), negativeCount.toLong())
      val expectedObservedCount = iterations.toDouble() / observedCounts.size
      val expectedCounts = DoubleArray(observedCounts.size) { expectedObservedCount }
      val significanceResult = ChiSquareTest.withDefaults().test(expectedCounts, observedCounts)
      withClue(significanceResult.print().value) { significanceResult.reject(0.01).shouldBeFalse() }
    }
  }

  @Test
  fun `distribution respects the given range`() = runTest {
    checkAll(propTestConfig, arbRange()) { range ->
      val arb = arbDistribution(range)
      val sample = arb.bind()
      assertIn(sample, range)
    }
  }

  @Test
  fun `distribution produces the full unbiased range of num digits`() {
    val iterations = 1_000_000
    val observedCounts = LongArray(maxDigitCount)

    arbDistribution().samples(rs).take(iterations).forEach {
      observedCounts[countBase10Digits(it.value) - 1]++
    }

    withClue("observedCounts=${observedCounts.print().value}") {
      val expectedObservedCount = iterations.toDouble() / observedCounts.size
      val expectedCounts = DoubleArray(observedCounts.size) { expectedObservedCount }
      val significanceResult = ChiSquareTest.withDefaults().test(expectedCounts, observedCounts)
      withClue(significanceResult.print().value) { significanceResult.reject(0.01).shouldBeFalse() }
    }
  }

  @Test
  fun `distribution produces an unbiased range of num digits within the given range`() = runTest {
    checkAll(propTestConfig.withIterations(100), arbMultipleDigitsRange()) { range ->
      val iterations = 100_000
      val observedCounts = LongArray(maxDigitCount)

      arbDistribution(range).samples(randomSource()).take(iterations).forEach {
        observedCounts[countBase10Digits(it.value) - 1]++
      }

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
          significanceResult.reject(0.0001).shouldBeFalse()
        }
      }
    }
  }

  @Test
  fun `nonNegativeDistribution produces non-negative values`() = runTest {
    checkAll(propTestConfig, arbNonNegativeDistribution()) { value ->
      assertGreaterThanOrEqual(value, zero)
    }
  }

  companion object {
    internal val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
        shrinkingMode = ShrinkingMode.Off,
      )
  }
}
