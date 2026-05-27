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

@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TuplesUnitTest {

  @Test
  fun `TwoValues toList`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.int())) { values ->
      val valueList = values.run { listOf(value1, value2) }
      values.toList() shouldBe valueList
    }
  }

  @Test
  fun `ThreeValues toList`() = runTest {
    checkAll(propTestConfig, Arb.threeValues(Arb.int())) { values ->
      val valueList = values.run { listOf(value1, value2, value3) }
      values.toList() shouldBe valueList
    }
  }

  @Test
  fun `FourValues toList`() = runTest {
    checkAll(propTestConfig, Arb.fourValues(Arb.int())) { values ->
      val valueList = values.run { listOf(value1, value2, value3, value4) }
      values.toList() shouldBe valueList
    }
  }

  @Test
  fun `FiveValues toList`() = runTest {
    checkAll(propTestConfig, Arb.fiveValues(Arb.int())) { values ->
      val valueList = values.run { listOf(value1, value2, value3, value4, value5) }
      values.toList() shouldBe valueList
    }
  }

  @Test
  fun `toTwoValues should return an instance with values from the list`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 2..2)) { values ->
      values.toTwoValues() shouldBe TwoValues(values[0], values[1])
    }
  }

  @Test
  fun `toThreeValues should return an instance with values from the list`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 3..3)) { values ->
      values.toThreeValues() shouldBe ThreeValues(values[0], values[1], values[2])
    }
  }

  @Test
  fun `toFourValues should return an instance with values from the list`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 4..4)) { values ->
      values.toFourValues() shouldBe FourValues(values[0], values[1], values[2], values[3])
    }
  }

  @Test
  fun `toFiveValues should return an instance with values from the list`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 5..5)) { values ->
      values.toFiveValues() shouldBe
        FiveValues(values[0], values[1], values[2], values[3], values[4])
    }
  }

  @Test
  fun `toTwoValues should throw if list length is not 2`() = runTest {
    val listSizeArb = Arb.choice(Arb.int(0 until 2), Arb.int(3..10))
    checkAll(propTestConfig, listSizeArb) { size ->
      val values = Arb.list(Arb.int(), size..size).bind()
      val exception = shouldThrow<IllegalArgumentException> { values.toTwoValues() }
      exception.message shouldBe "size is $size, but it must be exactly 2"
    }
  }

  @Test
  fun `toThreeValues should throw if list length is not 3`() = runTest {
    val listSizeArb = Arb.choice(Arb.int(0 until 3), Arb.int(4..10))
    checkAll(propTestConfig, listSizeArb) { size ->
      val values = Arb.list(Arb.int(), size..size).bind()
      val exception = shouldThrow<IllegalArgumentException> { values.toThreeValues() }
      exception.message shouldBe "size is $size, but it must be exactly 3"
    }
  }

  @Test
  fun `toFourValues should throw if list length is not 4`() = runTest {
    val listSizeArb = Arb.choice(Arb.int(0 until 4), Arb.int(5..10))
    checkAll(propTestConfig, listSizeArb) { size ->
      val values = Arb.list(Arb.int(), size..size).bind()
      val exception = shouldThrow<IllegalArgumentException> { values.toFourValues() }
      exception.message shouldBe "size is $size, but it must be exactly 4"
    }
  }

  @Test
  fun `toFiveValues should throw if list length is not 5`() = runTest {
    val listSizeArb = Arb.choice(Arb.int(0 until 5), Arb.int(6..10))
    checkAll(propTestConfig, listSizeArb) { size ->
      val values = Arb.list(Arb.int(), size..size).bind()
      val exception = shouldThrow<IllegalArgumentException> { values.toFiveValues() }
      exception.message shouldBe "size is $size, but it must be exactly 5"
    }
  }

  @Test
  fun `TwoValues sorted`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.int())) { values ->
      val sortedValues = values.toList().sorted().toTwoValues()

      values.sorted() shouldBe sortedValues
    }
  }

  @Test
  fun `ThreeValues sorted`() = runTest {
    checkAll(propTestConfig, Arb.threeValues(Arb.int())) { values ->
      val sortedValues = values.toList().sorted().toThreeValues()

      values.sorted() shouldBe sortedValues
    }
  }

  @Test
  fun `FourValues sorted`() = runTest {
    checkAll(propTestConfig, Arb.fourValues(Arb.int())) { values ->
      val sortedValues = values.toList().sorted().toFourValues()

      values.sorted() shouldBe sortedValues
    }
  }

  @Test
  fun `FiveValues sorted`() = runTest {
    checkAll(propTestConfig, Arb.fiveValues(Arb.int())) { values ->
      val sortedValues = values.toList().sorted().toFiveValues()

      values.sorted() shouldBe sortedValues
    }
  }
}

private val propTestConfig = PropTestConfig(iterations = 1000)
