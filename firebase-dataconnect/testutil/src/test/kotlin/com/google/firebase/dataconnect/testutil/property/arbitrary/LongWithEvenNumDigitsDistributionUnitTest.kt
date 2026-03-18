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

import com.google.firebase.dataconnect.testutil.countBase10Digits
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import kotlin.math.sign

class LongWithEvenNumDigitsDistributionUnitTest :
  AbstractWithEvenNumDigitsDistributionUnitTest<Long, LongRange>(maxDigitCount = 19, zero = 0L) {

  override fun getSign(value: Long) = value.sign
  override fun countBase10Digits(value: Long) = value.countBase10Digits()
  override fun arbDistribution() = Arb.longWithEvenNumDigitsDistribution()
  override fun arbDistribution(range: LongRange) = Arb.longWithEvenNumDigitsDistribution(range)
  override fun arbNonNegativeDistribution() = Arb.nonNegativeLongWithEvenNumDigitsDistribution()

  override fun arbRange(): Arb<LongRange> =
    Arb.twoValues(Arb.long()).map {
      val (first, last) = listOf(it.value1, it.value1 + it.value2).sorted()
      first..last
    }

  override fun arbMultipleDigitsRange(): Arb<LongRange> {
    fun LongRange.containsMultipleDigits(): Boolean =
      when {
        first in -9L..9L && last in -9L..9L -> false
        first.sign != last.sign -> true
        else -> first.countBase10Digits() != last.countBase10Digits()
      }
    return arbRange().filter { it.containsMultipleDigits() }
  }

  override fun assertIn(value: Long, range: LongRange) {
    value shouldBeIn range
  }

  override fun assertGreaterThanOrEqual(value: Long, limit: Long) {
    value shouldBeGreaterThanOrEqual limit
  }
}
