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
    maxDigits = 10,
    fullRange = Int.MIN_VALUE..Int.MAX_VALUE
  ) {
  private val POWERS_OF_TEN =
    intArrayOf(
      1,
      10,
      100,
      1_000,
      10_000,
      100_000,
      1_000_000,
      10_000_000,
      100_000_000,
      1_000_000_000
    )

  override fun getTheoreticalBounds(digitCount: Int): IntRange {
    val positive = digitCount > 0
    val count = kotlin.math.abs(digitCount)
    if (positive) {
      if (count == 1) return 0..9
      val min = POWERS_OF_TEN[count - 1]
      val max = if (count == 10) Int.MAX_VALUE else POWERS_OF_TEN[count] - 1
      return min..max
    } else {
      val max = -POWERS_OF_TEN[count - 1]
      val min = if (count == 10) Int.MIN_VALUE else -(POWERS_OF_TEN[count] - 1)
      return min..max
    }
  }
  override fun intersect(r1: IntRange, r2: IntRange): IntRange = r1 intersect r2
  override fun isEmpty(range: IntRange): Boolean = range.isEmpty()
  override fun createArb(range: IntRange): Arb<Int> = Arb.int(range)
}
