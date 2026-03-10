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
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import kotlin.math.sign

class IntWithEvenNumDigitsDistributionUnitTest :
  AbstractWithEvenNumDigitsDistributionUnitTest<Int, IntRange>(maxDigitCount = 10, zero = 0) {

  override fun getSign(value: Int) = value.sign
  override fun countBase10Digits(value: Int) = value.countBase10Digits()
  override fun arbDistribution() = Arb.intWithEvenNumDigitsDistribution()
  override fun arbDistribution(range: IntRange) = Arb.intWithEvenNumDigitsDistribution(range)
  override fun arbNonNegativeDistribution() = Arb.nonNegativeIntWithEvenNumDigitsDistribution()

  override fun arbRange(): Arb<IntRange> =
    Arb.twoValues(Arb.int()).map {
      val (first, last) = listOf(it.value1, it.value1 + it.value2).sorted()
      first..last
    }

  override fun arbMultipleDigitsRange(): Arb<IntRange> {
    fun IntRange.containsMultipleDigits(): Boolean =
      when {
        first in -9..9 && last in -9..9 -> false
        first.sign != last.sign -> true
        else -> first.countBase10Digits() != last.countBase10Digits()
      }
    return arbRange().filter { it.containsMultipleDigits() }
  }

  override fun assertIn(value: Int, range: IntRange) {
    value shouldBeIn range
  }

  override fun assertGreaterThanOrEqual(value: Int, limit: Int) {
    value shouldBeGreaterThanOrEqual limit
  }
}
