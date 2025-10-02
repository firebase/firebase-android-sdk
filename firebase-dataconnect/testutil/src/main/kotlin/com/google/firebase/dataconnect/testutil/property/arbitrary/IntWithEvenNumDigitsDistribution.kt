/*
 * Copyright 2024 Google LLC
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

import com.google.common.primitives.Ints.min
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int

/**
 * Returns an [Arb] identical to [Arb.Companion.int] except that the values it produces have an
 * equal probability of having any given number of digits in its base-10 string representation. This
 * is useful for testing int values that get zero padded when they are small. The negative sign is
 * _not_ included in the "number of digits" count.
 *
 * @param range The range of values to produce; if `null` (the default) then use the entire range of
 * integers (formally, `Int.MIN_VALUE..Int.MAX_VALUE`).
 *
 * @see intWithEvenNumDigitsDistribution
 */
@JvmName("intWithEvenNumDigitsDistributionNonNullRange")
fun Arb.Companion.intWithEvenNumDigitsDistribution(range: IntRange): Arb<Int> {
  require(!range.isEmpty()) { "range must not be empty: $range (error code tmvy8ysdjy)" }
  val intRangesByNumDigits = mutableMapOf<Int, MutableList<IntRange>>()

  var first = range.first
  while (first <= range.last) {
    val numDigits = "$first".trimStart('-').length
    val numDigitsKey = if (first >= 0) numDigits else (-numDigits)
    val numDigitsRange = rangeByNumDigits[numDigitsKey]
    checkNotNull(numDigitsRange) {
      "internal error: rangeByNumDigits[numDigitsKey] returned null " +
        "(first=$first, numDigitsKey=$numDigitsKey, rangeByNumDigits=$rangeByNumDigits, " +
        "error code 3z37g9zfy8)"
    }

    val last = min(range.last, numDigitsRange.last)
    val curIntRangesByNumDigits = intRangesByNumDigits.getOrPut(numDigits) { mutableListOf() }
    curIntRangesByNumDigits.add(first..last)
    if (last == Int.MAX_VALUE) {
      break
    }
    first = last + 1
  }

  val arbLists: List<List<Arb<Int>>> =
    intRangesByNumDigits.values.map { intRanges -> intRanges.map { intRange -> Arb.int(intRange) } }
  val arbs: List<Arb<Int>> = arbLists.map { if (it.size == 1) it.single() else Arb.choice(it) }
  return Arb.choice(arbs)
}

/**
 * Returns an [Arb] identical to [Arb.Companion.int] except that the values it produces have an
 * equal probability of having any given number of digits in its base-10 string representation. This
 * is useful for testing int values that get zero padded when they are small. The negative sign is
 * _not_ included in the "number of digits" count.
 *
 * @param range The range of values to produce; if `null` (the default) then use the entire range of
 * integers (formally, `Int.MIN_VALUE..Int.MAX_VALUE`).
 *
 * @see intWithEvenNumDigitsDistribution
 */
@JvmName("intWithEvenNumDigitsDistributionNullableRange")
fun Arb.Companion.intWithEvenNumDigitsDistribution(range: IntRange? = null): Arb<Int> =
  intWithEvenNumDigitsDistribution(range ?: Int.MIN_VALUE..Int.MAX_VALUE)

private val rangeByNumDigits: Map<Int, IntRange> = buildMap {
  put(1, 0..9)
  put(2, 10..99)
  put(3, 100..999)
  put(4, 1_000..9_999)
  put(5, 10_000..99_999)
  put(6, 100_000..999_999)
  put(7, 1_000_000..9_999_999)
  put(8, 10_000_000..99_999_999)
  put(9, 100_000_000..999_999_999)
  put(10, 1_000_000_000..Int.MAX_VALUE)
  put(-1, -9..-1)
  put(-2, -99..-10)
  put(-3, -999..-100)
  put(-4, -9_999..-1_000)
  put(-5, -99_999..-10_000)
  put(-6, -999_999..-100_000)
  put(-7, -9_999_999..-1_000_000)
  put(-8, -99_999_999..-10_000_000)
  put(-9, -999_999_999..-100_000_000)
  put(-10, Int.MIN_VALUE..-1_000_000_000)
}
