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
    maxDigits = 19,
    fullRange = Long.MIN_VALUE..Long.MAX_VALUE
  ) {
  private val POWERS_OF_TEN =
    longArrayOf(
      1L,
      10L,
      100L,
      1_000L,
      10_000L,
      100_000L,
      1_000_000L,
      10_000_000L,
      100_000_000L,
      1_000_000_000L,
      10_000_000_000L,
      100_000_000_000L,
      1_000_000_000_000L,
      10_000_000_000_000L,
      100_000_000_000_000L,
      1_000_000_000_000_000L,
      10_000_000_000_000_000L,
      100_000_000_000_000_000L,
      1_000_000_000_000_000_000L
    )

  override fun getTheoreticalBounds(digitCount: Int): LongRange {
    val positive = digitCount > 0
    val count = kotlin.math.abs(digitCount)
    if (positive) {
      if (count == 1) return 0L..9L
      val min = POWERS_OF_TEN[count - 1]
      val max = if (count == 19) Long.MAX_VALUE else POWERS_OF_TEN[count] - 1L
      return min..max
    } else {
      val max = -POWERS_OF_TEN[count - 1]
      val min = if (count == 19) Long.MIN_VALUE else -(POWERS_OF_TEN[count] - 1L)
      return min..max
    }
  }
  override fun intersect(r1: LongRange, r2: LongRange): LongRange = r1 intersect r2
  override fun isEmpty(range: LongRange): Boolean = range.isEmpty()
  override fun createArb(range: LongRange): Arb<Long> = Arb.long(range)
}
