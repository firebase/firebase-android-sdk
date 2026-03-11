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

import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice

/**
 * An abstract base class for generating Kotest [Arb] (Arbitraries) that produce numbers with a
 * uniform distribution across their number of digits (in base-10 string representation).
 *
 * This class ensures that a 1-digit number has the same probability of being generated as a
 * 10-digit number. It achieves this by dynamically calculating the valid ranges for each possible
 * digit count, clamping them to the requested boundaries, and then using [Arb.choice] to first
 * uniformly pick a digit count, and then pick a value within that count.
 *
 * @param T The numeric type (e.g., [Int], [Long]).
 * @param R The range type (e.g., [IntRange], [LongRange]).
 * @property maxDigitCount The maximum number of digits possible for the numeric type [T] (e.g., 10
 * for [Int], 19 for [Long]).
 * @property fullRange The absolute minimum and maximum bounds for the numeric type [T] (e.g.,
 * `Int.MIN_VALUE..Int.MAX_VALUE`).
 */
internal abstract class AbstractEvenNumDigitsDistribution<T : Comparable<T>, R : ClosedRange<T>>(
  private val maxDigitCount: Int,
  private val fullRange: R
) {
  /**
   * Returns the theoretical numeric bounds for a given [digitCount].
   *
   * The [digitCount] is signed; a positive value requests the bounds for positive numbers with that
   * many digits, while a negative value requests the bounds for negative numbers. For example, a
   * `digitCount` of `2` should return the range `10..99`, and `-2` should return `-99..-10`.
   */
  abstract fun getTheoreticalBounds(digitCount: Int): R

  /**
   * Returns the mathematical intersection of [range1] and [range2]. If the ranges do not overlap,
   * an empty range should be returned.
   */
  abstract fun intersect(range1: R, range2: R): R

  /** Returns true if the given [range] is empty (contains no elements). */
  abstract fun isEmpty(range: R): Boolean

  /**
   * Creates an [Arb] that generates values uniformly distributed within the given [range]. This
   * typically delegates to standard Kotest arbitraries like [io.kotest.property.arbitrary.int].
   */
  abstract fun createArb(range: R): Arb<T>

  private val rangesGroupedByDigitCount: List<List<R>> by lazy {
    (1..maxDigitCount).mapNotNull {
      val positiveRange = intersect(getTheoreticalBounds(it), fullRange)
      val negativeRange = intersect(getTheoreticalBounds(-it), fullRange)
      val validRanges = listOf(negativeRange, positiveRange).filterNot(::isEmpty)
      validRanges.ifEmpty { null }
    }
  }

  /**
   * Generates the final [Arb] that produces values uniformly across digit counts.
   *
   * @param userRange An optional explicit range to restrict the generated values to. If provided,
   * the internal digit-grouped ranges will be intersected with this range.
   */
  fun generate(userRange: R? = null): Arb<T> {
    val ranges =
      if (userRange == null) {
        rangesGroupedByDigitCount
      } else {
        rangesGroupedByDigitCount
          .map { ranges -> ranges.map { intersect(it, userRange) }.filterNot(::isEmpty) }
          .filterNot { it.isEmpty() }
      }
    val arbs = ranges.map { Arb.choice(it.map(::createArb)) }
    return Arb.choice(arbs)
  }
}
