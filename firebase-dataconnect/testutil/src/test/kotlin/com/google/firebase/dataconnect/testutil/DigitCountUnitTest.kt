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

@file:OptIn(DelicateKotest::class, ExperimentalKotest::class)

package com.google.firebase.dataconnect.testutil

import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DigitCountUnitTest {

  @Test
  fun `Int countBase10Digits returns the correct value for 0`() {
    0.countBase10Digits() shouldBe 1
  }

  @Test
  fun `Long countBase10Digits returns the correct value for 0`() {
    0L.countBase10Digits() shouldBe 1
  }

  @Test
  fun `Int countBase10Digits returns the correct value for positive numbers up to 9 digits`() =
    runTest {
      checkAll(propTestConfig, intWithDigitCountArb(1..9)) { (value, digitCount) ->
        check(value >= 0)
        value.countBase10Digits() shouldBe digitCount
      }
    }

  @Test
  fun `Long countBase10Digits returns the correct value for positive numbers up to 18 digits`() =
    runTest {
      checkAll(propTestConfig, longWithDigitCountArb(1..18)) { (value, digitCount) ->
        check(value >= 0)
        value.countBase10Digits() shouldBe digitCount
      }
    }

  @Test
  fun `Int countBase10Digits returns the correct value for positive numbers with 10 digits`() =
    runTest {
      checkAll(propTestConfig, Arb.int(1_000_000_000..Int.MAX_VALUE)) { value ->
        check(value >= 0)
        value.countBase10Digits() shouldBe 10
      }
    }

  @Test
  fun `Long countBase10Digits returns the correct value for positive numbers with 19 digits`() =
    runTest {
      checkAll(propTestConfig, Arb.long(1_000_000_000_000_000_000L..Long.MAX_VALUE)) { value ->
        check(value >= 0)
        value.countBase10Digits() shouldBe 19
      }
    }

  @Test
  fun `Int countBase10Digits returns the correct value for negative numbers up to 9 digits`() =
    runTest {
      checkAll(propTestConfig, negativeIntWithDigitCountArb(1..9)) { (value, digitCount) ->
        check(value < 0)
        value.countBase10Digits() shouldBe digitCount
      }
    }

  @Test
  fun `Long countBase10Digits returns the correct value for negative numbers up to 18 digits`() =
    runTest {
      checkAll(propTestConfig, negativeLongWithDigitCountArb(1..18)) { (value, digitCount) ->
        check(value < 0)
        value.countBase10Digits() shouldBe digitCount
      }
    }

  @Test
  fun `Int countBase10Digits returns the correct value for negative numbers with 19 digits`() =
    runTest {
      checkAll(propTestConfig, Arb.long(Long.MIN_VALUE..-1_000_000_000_000_000_000L)) { value ->
        check(value < 0)
        value.countBase10Digits() shouldBe 19
      }
    }
}

private val propTestConfig =
  PropTestConfig(
    iterations = 1000,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off
  )

private data class NumberWithDigitCount<T : Number>(val value: T, val digitCount: Int)

private fun <T : Number> numberWithDigitCountArb(
  digitCountRange: IntRange,
  toNumber: String.() -> T
): Arb<NumberWithDigitCount<T>> {
  val firstDigitArb = Arb.int(1..9)
  val subsequentDigitCountRange = digitCountRange.first until digitCountRange.last
  val subsequentDigitsArb = Arb.list(Arb.int(0..9), subsequentDigitCountRange)
  return Arb.bind(firstDigitArb, subsequentDigitsArb) { firstDigit, subsequentDigits ->
    val string = buildString {
      append(firstDigit)
      subsequentDigits.forEach(::append)
    }
    check(string.length == subsequentDigits.size + 1)
    NumberWithDigitCount(string.toNumber(), string.length)
  }
}

private fun intWithDigitCountArb(digitCountRange: IntRange): Arb<NumberWithDigitCount<Int>> =
  numberWithDigitCountArb(digitCountRange) { toInt() }

private fun longWithDigitCountArb(digitCountRange: IntRange): Arb<NumberWithDigitCount<Long>> =
  numberWithDigitCountArb(digitCountRange) { toLong() }

private fun negativeIntWithDigitCountArb(
  digitCountRange: IntRange
): Arb<NumberWithDigitCount<Int>> =
  intWithDigitCountArb(digitCountRange).map { it.copy(value = -it.value) }

private fun negativeLongWithDigitCountArb(
  digitCountRange: IntRange
): Arb<NumberWithDigitCount<Long>> =
  longWithDigitCountArb(digitCountRange).map { it.copy(value = -it.value) }
