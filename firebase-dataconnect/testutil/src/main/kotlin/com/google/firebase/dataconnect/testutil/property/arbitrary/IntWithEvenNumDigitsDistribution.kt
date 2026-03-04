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
import io.kotest.property.arbitrary.int

/**
 * Returns an [Arb] identical to [Arb.Companion.int] except that the values it produces have an
 * equal probability of having any given number of digits in its base-10 string representation.
 *
 * Note that the negative sign does _not_ contribute to the digit count. For example both 22 and -22
 * are considered to have 2 digits.
 */
fun Arb.Companion.intWithEvenNumDigitsDistribution(range: IntRange? = null): Arb<Int> {
  val ranges = calculateDigitRangesForRange(range)
  val arbs = ranges.map { Arb.choice(it.map { range -> Arb.int(range) }) }
  return Arb.choice(arbs)
}

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

private fun calculateDigitRangesForRange(range: IntRange? = null): List<List<IntRange>> =
  if (range === null) {
    rangesGroupedByDigitCount
  } else {
    rangesGroupedByDigitCount
      .map { ranges -> ranges.map { it intersect range }.filterNot { it.isEmpty() } }
      .filterNot { it.isEmpty() }
  }

private val negativeRanges: List<IntRange> =
  listOf(
    -9..-1,
    -99..-10,
    -999..-100,
    -9_999..-1_000,
    -99_999..-10_000,
    -999_999..-100_000,
    -9_999_999..-1_000_000,
    -99_999_999..-10_000_000,
    -999_999_999..-100_000_000,
    Int.MIN_VALUE..-1_000_000_000,
  )

private val nonNegativeRanges: List<IntRange> =
  listOf(
    0..9,
    10..99,
    100..999,
    1_000..9_999,
    10_000..99_999,
    100_000..999_999,
    1_000_000..9_999_999,
    10_000_000..99_999_999,
    100_000_000..999_999_999,
    1_000_000_000..Int.MAX_VALUE,
  )

private val rangesGroupedByDigitCount: List<List<IntRange>> =
  negativeRanges.zip(nonNegativeRanges).map { (range1, range2) -> listOf(range1, range2) }
