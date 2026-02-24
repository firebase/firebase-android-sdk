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
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.long

/**
 * Returns an [Arb] identical to [Arb.Companion.long] except that the values it produces have an
 * equal probability of having any given number of digits in its base-10 string representation.
 *
 * Note that the negative sign does _not_ contribute to the digit count. For example both 22 and -22
 * are considered to have 2 digits.
 */
fun Arb.Companion.longWithEvenNumDigitsDistribution(range: LongRange? = null): Arb<Long> {
  val ranges = calculateDigitRangesForRange(range)
  val arbs = ranges.map { Arb.choice(it.map { range -> Arb.long(range) }) }
  return Arb.choice(arbs)
}

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

private fun calculateDigitRangesForRange(range: LongRange? = null): List<List<LongRange>> =
  if (range === null) {
    rangesGroupedByDigitCount
  } else {
    rangesGroupedByDigitCount
      .map { ranges -> ranges.map { it intersect range }.filterNot { it.isEmpty() } }
      .filterNot { it.isEmpty() }
  }

private val negativeRanges: List<LongRange> =
  listOf(
    -9L..-1L,
    -99L..-10L,
    -999L..-100L,
    -9_999L..-1_000L,
    -99_999L..-10_000L,
    -999_999L..-100_000L,
    -9_999_999L..-1_000_000L,
    -99_999_999L..-10_000_000L,
    -999_999_999L..-100_000_000L,
    -9_999_999_999L..-1_000_000_000L,
    -99_999_999_999L..-10_000_000_000L,
    -999_999_999_999L..-100_000_000_000L,
    -9_999_999_999_999L..-1_000_000_000_000L,
    -99_999_999_999_999L..-10_000_000_000_000L,
    -999_999_999_999_999L..-100_000_000_000_000L,
    -9_999_999_999_999_999L..-1_000_000_000_000_000L,
    -99_999_999_999_999_999L..-10_000_000_000_000_000L,
    -999_999_999_999_999_999L..-100_000_000_000_000_000L,
    Long.MIN_VALUE..-1_000_000_000_000_000_000L,
  )

private val nonNegativeRanges: List<LongRange> =
  listOf(
    0L..9L,
    10L..99L,
    100L..999L,
    1_000L..9_999L,
    10_000L..99_999L,
    100_000L..999_999L,
    1_000_000L..9_999_999L,
    10_000_000L..99_999_999L,
    100_000_000L..999_999_999L,
    1_000_000_000L..9_999_999_999L,
    10_000_000_000L..99_999_999_999L,
    100_000_000_000L..999_999_999_999L,
    1_000_000_000_000L..9_999_999_999_999L,
    10_000_000_000_000L..99_999_999_999_999L,
    100_000_000_000_000L..999_999_999_999_999L,
    1_000_000_000_000_000L..9_999_999_999_999_999L,
    10_000_000_000_000_000L..99_999_999_999_999_999L,
    100_000_000_000_000_000L..999_999_999_999_999_999L,
    1_000_000_000_000_000_000L..Long.MAX_VALUE,
  )

private val rangesGroupedByDigitCount: List<List<LongRange>> =
  negativeRanges.zip(nonNegativeRanges).map { (range1, range2) -> listOf(range1, range2) }
