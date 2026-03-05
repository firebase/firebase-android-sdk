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

internal abstract class AbstractEvenNumDigitsDistribution<T : Comparable<T>, R : ClosedRange<T>>(
  private val maxDigits: Int,
  private val fullRange: R
) {
  abstract fun getTheoreticalBounds(digitCount: Int): R
  abstract fun intersect(r1: R, r2: R): R
  abstract fun isEmpty(range: R): Boolean
  abstract fun createArb(range: R): Arb<T>

  private val rangesGroupedByDigitCount: List<List<R>> by lazy {
    (1..maxDigits).mapNotNull { digitCount ->
      val positiveRange = intersect(getTheoreticalBounds(digitCount), fullRange)
      val negativeRange = intersect(getTheoreticalBounds(-digitCount), fullRange)
      val validRanges = listOf(negativeRange, positiveRange).filterNot { isEmpty(it) }
      if (validRanges.isEmpty()) null else validRanges
    }
  }
  fun generate(userRange: R? = null): Arb<T> {
    val ranges =
      if (userRange == null) {
        rangesGroupedByDigitCount
      } else {
        rangesGroupedByDigitCount
          .map { group -> group.map { intersect(it, userRange) }.filterNot { isEmpty(it) } }
          .filterNot { it.isEmpty() }
      }
    val arbs = ranges.map { group -> Arb.choice(group.map { createArb(it) }) }
    return Arb.choice(arbs)
  }
}
