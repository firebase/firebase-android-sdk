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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import kotlin.random.nextInt

fun Arb<String>.withPrefix(prefix: String): Arb<String> = arbitrary { "$prefix${bind()}" }

fun Arb.Companion.positiveIntWithUniformNumDigitsProbability(range: IntRange): Arb<Int> {
  require(!range.isEmpty()) { "empty range is nonsensical" }
  require(range.first >= 0) { "range.first must be non-negative, but got: ${range.first}" }

  val numDigitsRange = IntRange(range.first.toString().length, range.last.toString().length)

  return arbitrary { rs ->
    val numDigits = rs.random.nextInt(numDigitsRange)

    val min =
      if (numDigits == numDigitsRange.first) {
        range.first
      } else {
        pow10(numDigits - 1)
      }

    val max =
      if (numDigits == numDigitsRange.last) {
        range.last
      } else {
        pow10(numDigits) - 1
      }

    val number = rs.random.nextInt(min, max)
    if (
      number == 0 || numDigits == numDigitsRange.last || numDigitsRange.first == numDigitsRange.last
    ) {
      return@arbitrary number
    }

    if (rs.random.nextFloat() > 0.333f) {
      return@arbitrary number
    }

    val numberTrailingZeroes = rs.random.nextInt(numDigitsRange.first until numDigitsRange.last)
    val s = buildString {
      append(number)
      repeat(numberTrailingZeroes) { append('0') }
    }

    s.substring(s.length - numDigits).toInt()
  }
}

private fun pow10(n: Int): Int {
  require(n >= 0) { "invalid n: $n" }
  var result = 1
  repeat(n) { result *= 10 }
  return result
}
