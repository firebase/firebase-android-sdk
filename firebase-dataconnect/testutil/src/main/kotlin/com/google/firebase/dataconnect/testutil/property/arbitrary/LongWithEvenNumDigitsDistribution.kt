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

import com.google.firebase.dataconnect.testutil.intersect
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long

/**
 * Returns an [Arb] identical to [Arb.Companion.long] except that the values it produces have an
 * equal probability of having any given number of digits in its base-10 string representation.
 *
 * Note that the negative sign does _not_ contribute to the digit count. For example both 22 and -22
 * are considered to have 2 digits.
 */
fun Arb.Companion.longWithEvenNumDigitsDistribution(range: LongRange? = null): Arb<Long> =
  LongEvenNumDigitsDistribution.generate(range)

/**
 * Returns an [Arb] identical to [Arb.Companion.nonNegativeLong] except that the values it produces
 * have an equal probability of having any given number of digits in its base-10 string
 * representation.
 *
 * Note that the negative sign does _not_ contribute to the digit count. For example both 22 and -22
 * are considered to have 2 digits.
 */
fun Arb.Companion.nonNegativeLongWithEvenNumDigitsDistribution(min: Long = 0): Arb<Long> {
  require(min >= 0) { "invalid min: $min (must be greater than or equal to zero)" }
  return longWithEvenNumDigitsDistribution(min..Long.MAX_VALUE)
}

private object LongEvenNumDigitsDistribution :
  AbstractEvenNumDigitsDistribution<Long, LongRange>(
    maxDigitCount = 19,
    fullRange = Long.MIN_VALUE..Long.MAX_VALUE
  ) {
  private val RANGES_BY_DIGIT_COUNT =
    mapOf(
      -1 to -9L..-1L,
      -2 to -99L..-10L,
      -3 to -999L..-100L,
      -4 to -9_999L..-1_000L,
      -5 to -99_999L..-10_000L,
      -6 to -999_999L..-100_000L,
      -7 to -9_999_999L..-1_000_000L,
      -8 to -99_999_999L..-10_000_000L,
      -9 to -999_999_999L..-100_000_000L,
      -10 to -9_999_999_999L..-1_000_000_000L,
      -11 to -99_999_999_999L..-10_000_000_000L,
      -12 to -999_999_999_999L..-100_000_000_000L,
      -13 to -9_999_999_999_999L..-1_000_000_000_000L,
      -14 to -99_999_999_999_999L..-10_000_000_000_000L,
      -15 to -999_999_999_999_999L..-100_000_000_000_000L,
      -16 to -9_999_999_999_999_999L..-1_000_000_000_000_000L,
      -17 to -99_999_999_999_999_999L..-10_000_000_000_000_000L,
      -18 to -999_999_999_999_999_999L..-100_000_000_000_000_000L,
      -19 to Long.MIN_VALUE..-1_000_000_000_000_000_000L,
      1 to 0L..9L,
      2 to 10L..99L,
      3 to 100L..999L,
      4 to 1_000L..9_999L,
      5 to 10_000L..99_999L,
      6 to 100_000L..999_999L,
      7 to 1_000_000L..9_999_999L,
      8 to 10_000_000L..99_999_999L,
      9 to 100_000_000L..999_999_999L,
      10 to 1_000_000_000L..9_999_999_999L,
      11 to 10_000_000_000L..99_999_999_999L,
      12 to 100_000_000_000L..999_999_999_999L,
      13 to 1_000_000_000_000L..9_999_999_999_999L,
      14 to 10_000_000_000_000L..99_999_999_999_999L,
      15 to 100_000_000_000_000L..999_999_999_999_999L,
      16 to 1_000_000_000_000_000L..9_999_999_999_999_999L,
      17 to 10_000_000_000_000_000L..99_999_999_999_999_999L,
      18 to 100_000_000_000_000_000L..999_999_999_999_999_999L,
      19 to 1_000_000_000_000_000_000L..Long.MAX_VALUE
    )

  override fun getTheoreticalBounds(digitCount: Int) = RANGES_BY_DIGIT_COUNT.getValue(digitCount)
  override fun intersect(range1: LongRange, range2: LongRange) = range1 intersect range2
  override fun isEmpty(range: LongRange) = range.isEmpty()
  override fun createArb(range: LongRange) = Arb.long(range)
}
