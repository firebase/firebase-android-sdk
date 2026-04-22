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

/**
 * An abstract base class for unit testing [Arb]s that generate numbers with a distribution that is
 * even across the number of digits.
 *
 * This class provides a common set of tests to ensure that the distribution is unbiased across
 * digit counts and respects given ranges.
 *
 * @param T The numeric type (e.g., [Int], [Long]).
 * @param R The range type (e.g., [IntRange], [LongRange]).
 * @property maxDigitCount The maximum number of base-10 digits supported by the type [T].
 * @property zero The value representing zero for the type [T].
 */
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

  /**
   * Returns the sign of the given [value] (-1, 0, or 1).
   *
   * The returned value must have the same semantics as [Int.sign] and [Long.sign].
   */
  abstract fun getSign(value: T): Int

  /**
   * Returns the number of base-10 digits in the given [value].
   *
   * If the value is negative, the returned count should be the number of digits in the absolute
   * value (e.g., -123 should return 3).
   */
  abstract fun countBase10Digits(value: T): Int

  /** Returns an [Arb] for the distribution being tested. */
  abstract fun arbDistribution(): Arb<T>

  /** Returns an [Arb] for the distribution restricted to the given [range]. */
  abstract fun arbDistribution(range: R): Arb<T>

  /** Returns an [Arb] for the distribution that only produces non-negative values. */
  abstract fun arbNonNegativeDistribution(): Arb<T>

  /** Returns an [Arb] that generates valid ranges [R] for the type [T]. */
  abstract fun arbRange(): Arb<R>

  /** Returns an [Arb] that generates ranges [R] spanning multiple digit counts. */
  abstract fun arbMultipleDigitsRange(): Arb<R>

  /** Asserts that the given [value] is within the specified [range]. */
  abstract fun assertIn(value: T, range: R)

  /** Asserts that the given [value] is greater than or equal to the [limit]. */
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
      withClue(significanceResult.print().value) {
        significanceResult.reject(0.00001).shouldBeFalse()
      }
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
      withClue(significanceResult.print().value) {
        significanceResult.reject(0.00001).shouldBeFalse()
      }
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
          significanceResult.reject(0.00001).shouldBeFalse()
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
}

private val propTestConfig =
  PropTestConfig(
    iterations = 1000,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )
