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

@file:Suppress("UnusedReceiverParameter")

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.intersect
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int

/**
 * Returns an [Arb] identical to [Arb.Companion.int] except that the values it produces have an
 * equal probability of having any given number of digits in its base-10 string representation.
 *
 * Note that the negative sign does _not_ contribute to the digit count. For example both 22 and -22
 * are considered to have 2 digits.
 */
fun Arb.Companion.intWithEvenNumDigitsDistribution(range: IntRange? = null): Arb<Int> =
  IntEvenNumDigitsDistribution.generate(range)

/**
 * Returns an [Arb] identical to [Arb.Companion.nonNegativeInt] except that the values it produces
 * have an equal probability of having any given number of digits in its base-10 string
 * representation.
 *
 * Note that the negative sign does _not_ contribute to the digit count. For example both 22 and -22
 * are considered to have 2 digits.
 */
fun Arb.Companion.nonNegativeIntWithEvenNumDigitsDistribution(min: Int = 0): Arb<Int> {
  require(min >= 0) { "invalid min: $min (must be greater than or equal to zero)" }
  return intWithEvenNumDigitsDistribution(min..Int.MAX_VALUE)
}

private object IntEvenNumDigitsDistribution :
  AbstractEvenNumDigitsDistribution<Int, IntRange>(
    maxDigitCount = 10,
    fullRange = Int.MIN_VALUE..Int.MAX_VALUE
  ) {
  private val RANGES_BY_DIGIT_COUNT =
    mapOf(
      -1 to -9..-1,
      -2 to -99..-10,
      -3 to -999..-100,
      -4 to -9_999..-1_000,
      -5 to -99_999..-10_000,
      -6 to -999_999..-100_000,
      -7 to -9_999_999..-1_000_000,
      -8 to -99_999_999..-10_000_000,
      -9 to -999_999_999..-100_000_000,
      -10 to Int.MIN_VALUE..-1_000_000_000,
      1 to 0..9,
      2 to 10..99,
      3 to 100..999,
      4 to 1_000..9_999,
      5 to 10_000..99_999,
      6 to 100_000..999_999,
      7 to 1_000_000..9_999_999,
      8 to 10_000_000..99_999_999,
      9 to 100_000_000..999_999_999,
      10 to 1_000_000_000..Int.MAX_VALUE
    )

  override fun getTheoreticalBounds(digitCount: Int): IntRange =
    RANGES_BY_DIGIT_COUNT.getValue(digitCount)
  override fun intersect(range1: IntRange, range2: IntRange): IntRange = range1 intersect range2
  override fun isEmpty(range: IntRange): Boolean = range.isEmpty()
  override fun createArb(range: IntRange): Arb<Int> = Arb.int(range)
}
