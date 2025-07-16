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

@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.testutil.property.arbitrary.intWithNumBase10Digits
import com.google.firebase.dataconnect.testutil.property.arbitrary.intWithUniformNumDigitsDistribution
import com.google.firebase.dataconnect.util.IntUtil.toZeroPaddedString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.math.absoluteValue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IntUtilUnitTest {

  @Test
  fun `toZeroPaddedString should throw when invalid length specified`() = runTest {
    val valuesArb = Arb.intWithUniformNumDigitsDistribution()
    val paddingArb = Arb.choice(Arb.int(Int.MIN_VALUE..-1))

    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      shouldThrow<IllegalArgumentException> { value.toZeroPaddedString(padding) }
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=1, padding=1`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(1)
    val paddingArb = Arb.int(0..1)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=1, padding is greater than 1`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(1)
    val paddingArb = Arb.int(2..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 1)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=2, padding is less than or equal to 2`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(2)
    val paddingArb = Arb.int(0..2)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=2, padding is greater than 2`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(2)
    val paddingArb = Arb.int(3..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 2)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=3, padding is less than or equal to 3`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(3)
    val paddingArb = Arb.int(0..3)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=3, padding is greater than 3`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(3)
    val paddingArb = Arb.int(4..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 3)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=4, padding is less than or equal to 4`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(4)
    val paddingArb = Arb.int(0..4)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=4, padding is greater than 4`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(4)
    val paddingArb = Arb.int(5..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 4)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=5, padding is less than or equal to 5`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(5)
    val paddingArb = Arb.int(0..5)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=5, padding is greater than 5`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(5)
    val paddingArb = Arb.int(6..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 5)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=6, padding is less than or equal to 6`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(6)
    val paddingArb = Arb.int(0..6)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=6, padding is greater than 6`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(6)
    val paddingArb = Arb.int(7..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 6)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=7, padding is less than or equal to 7`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(7)
    val paddingArb = Arb.int(0..7)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=7, padding is greater than 7`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(7)
    val paddingArb = Arb.int(8..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 7)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=8, padding is less than or equal to 8`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(8)
    val paddingArb = Arb.int(0..8)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=8, padding is greater than 8`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(8)
    val paddingArb = Arb.int(9..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 8)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=9, padding is less than or equal to 9`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(9)
    val paddingArb = Arb.int(0..9)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=9, padding is greater than 9`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(9)
    val paddingArb = Arb.int(10..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 9)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=10, padding is less than or equal to 10`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(10)
    val paddingArb = Arb.int(0..10)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      value.toZeroPaddedString(padding) shouldBe "$value"
    }
  }

  @Test
  fun `toZeroPaddedString numDigits=10, padding is greater than 10`() = runTest {
    val valuesArb = Arb.intWithNumBase10Digits(10)
    val paddingArb = Arb.int(11..MAX_PADDING)
    checkAll(propTestConfig, valuesArb, paddingArb) { value, padding ->
      val prefix = "0".repeat(padding - 10)
      value.toZeroPaddedString(padding) shouldBe "$prefix$value"
    }
  }

  @Test
  fun `toZeroPaddedString for negative values`() = runTest {
    data class TestData(val value: Int, val padding: Int)
    val numDigitsArb = Arb.int(1..10).filterNot { it == 0 }
    val testDataArb =
      numDigitsArb.flatMap { numDigits ->
        val valueArb = Arb.intWithNumBase10Digits(-numDigits).filterNot { it == Int.MIN_VALUE }
        val paddingArb = Arb.choice(Arb.int(0..numDigits), Arb.int(numDigits + 1..MAX_PADDING))
        Arb.bind(valueArb, paddingArb) { value, padding ->
          TestData(value = value, padding = padding)
        }
      }

    checkAll(propTestConfig, testDataArb) { (value, padding) ->
      val expected = "-" + value.absoluteValue.toZeroPaddedString(padding)
      value.toZeroPaddedString(padding) shouldBe expected
    }
  }

  @Test
  fun `toZeroPaddedString for MIN_VALUE`() = runTest {
    val paddingArb = Arb.choice(Arb.int(1..10), Arb.int(11..MAX_PADDING))
    checkAll(propTestConfig, paddingArb) { padding ->
      val expected = "-" + "0".repeat((padding - 10).coerceAtLeast(0)) + "2147483648"
      Int.MIN_VALUE.toZeroPaddedString(padding) shouldBe expected
    }
  }

  private companion object {

    const val MAX_PADDING = 100

    val propTestConfig = PropTestConfig(iterations = 200)
  }
}
