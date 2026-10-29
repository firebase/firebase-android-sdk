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

import com.google.firebase.dataconnect.OptionalVariable
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.apache.commons.statistics.inference.ChiSquareTest
import org.junit.Test

class ArbsUnitTest {

  @Test
  fun `maxAgeArb() produces valid values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.maxAge()) { maxAge ->
      assertSoftly {
        withClue("seconds") { maxAge.seconds shouldBeGreaterThanOrEqual 0 }
        withClue("nanos") { maxAge.nanos shouldBeInRange 0..999_999_999 }
      }
    }
  }

  @Test
  fun `maxAgeArb() respects the given min`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.maxAge()) { min ->
      val arb = Arb.dataConnect.maxAge(min = min)

      val maxAge = arb.bind()

      assertSoftly {
        withClue("seconds") { maxAge.seconds shouldBeGreaterThanOrEqual min.seconds }
        withClue("nanos") {
          val minNanos = if (maxAge.seconds == min.seconds) min.nanos else 0
          maxAge.nanos shouldBeInRange minNanos..999_999_999
        }
      }
    }
  }

  @Test
  fun `optionalVariable(undefinedProbability=1)`() = runTest {
    val arb = Arb.dataConnect.optionalVariable(Arb.any(), undefinedProbability = 1.0)
    checkAll(propTestConfig, arb) { optionalVariable ->
      optionalVariable shouldBe OptionalVariable.Undefined
    }
  }

  @Test
  fun `optionalVariable(undefinedProbability=0)`() = runTest {
    val arb = Arb.dataConnect.optionalVariable(Arb.any(), undefinedProbability = 0.0)
    checkAll(propTestConfig, arb) { optionalVariable ->
      optionalVariable.shouldBeInstanceOf<OptionalVariable.Value<*>>()
    }
  }

  @Test
  fun `optionalVariable(undefinedProbability=0point5)`() = runTest {
    val arb = Arb.dataConnect.optionalVariable(Arb.any(), undefinedProbability = 0.5)
    var undefinedCount = 0
    var valueCount = 0
    checkAll(propTestConfig, arb) { optionalVariable ->
      when (optionalVariable) {
        OptionalVariable.Undefined -> undefinedCount++
        is OptionalVariable.Value<*> -> valueCount++
      }
    }

    withClue("undefinedCount=$undefinedCount, valueCount=$valueCount") {
      val iterations = undefinedCount + valueCount
      val observedCounts = longArrayOf(undefinedCount.toLong(), valueCount.toLong())
      val expectedObservedCount = iterations.toDouble() / observedCounts.size
      val expectedCounts = DoubleArray(observedCounts.size) { expectedObservedCount }
      val significanceResult = ChiSquareTest.withDefaults().test(expectedCounts, observedCounts)
      withClue("significanceResult=${significanceResult.print().value}") {
        significanceResult.reject(0.00001).shouldBeFalse()
      }
    }
  }

  @Test
  fun `optionalVariable(arb) produces values from the given Arb`() = runTest {
    val arb = Arb.dataConnect.optionalVariable(Arb.int(-1000..1000), undefinedProbability = 0.0)
    checkAll(propTestConfig, arb) { optionalVariable ->
      check(optionalVariable is OptionalVariable.Value<Int>)
      optionalVariable.value shouldBeInRange -1000..1000
    }
  }

  @Test
  fun `nullableOptionalVariable(undefinedProbability=1)`() = runTest {
    val arb =
      Arb.dataConnect.nullableOptionalVariable(
        Arb.any(),
        undefinedProbability = 1.0,
        nullableProbability = 0.0,
      )
    checkAll(propTestConfig, arb) { nullableOptionalVariable ->
      nullableOptionalVariable shouldBe OptionalVariable.Undefined
    }
  }

  @Test
  fun `nullableOptionalVariable(nullableProbability=1)`() = runTest {
    val arb =
      Arb.dataConnect.nullableOptionalVariable(
        Arb.any(),
        undefinedProbability = 0.0,
        nullableProbability = 1.0,
      )
    checkAll(propTestConfig, arb) { nullableOptionalVariable ->
      nullableOptionalVariable.shouldBeInstanceOf<OptionalVariable.Value<Any?>>()
      nullableOptionalVariable.value.shouldBeNull()
    }
  }

  @Test
  fun `nullableOptionalVariable(undefinedProbability and nullableProbability = 0)`() = runTest {
    val arb =
      Arb.dataConnect.nullableOptionalVariable(
        Arb.any(),
        undefinedProbability = 0.0,
        nullableProbability = 0.0,
      )
    checkAll(propTestConfig, arb) { nullableOptionalVariable ->
      nullableOptionalVariable.shouldBeInstanceOf<OptionalVariable.Value<Any?>>()
    }
  }

  @Test
  fun `nullableOptionalVariable(undefinedProbability and nullableProbability = 0point5)`() =
    runTest {
      val arb =
        Arb.dataConnect.nullableOptionalVariable(
          Arb.any(),
          undefinedProbability = 0.5,
          nullableProbability = 0.5,
        )
      var undefinedCount = 0
      var nullCount = 0
      checkAll(propTestConfig, arb) { nullableOptionalVariable ->
        when (nullableOptionalVariable) {
          OptionalVariable.Undefined -> undefinedCount++
          is OptionalVariable.Value<Any?> -> {
            check(nullableOptionalVariable.value == null)
            nullCount++
          }
        }
      }

      withClue("undefinedCount=$undefinedCount, nullCount=$nullCount") {
        val iterations = undefinedCount + nullCount
        val observedCounts = longArrayOf(undefinedCount.toLong(), nullCount.toLong())
        val expectedObservedCount = iterations.toDouble() / observedCounts.size
        val expectedCounts = DoubleArray(observedCounts.size) { expectedObservedCount }
        val significanceResult = ChiSquareTest.withDefaults().test(expectedCounts, observedCounts)
        withClue("significanceResult=${significanceResult.print().value}") {
          significanceResult.reject(0.00001).shouldBeFalse()
        }
      }
    }

  @Test
  fun `nullableOptionalVariable(undefinedProbability and nullableProbability = 0point33333)`() =
    runTest {
      val oneThird = 1.0 / 3.0
      val arb =
        Arb.dataConnect.nullableOptionalVariable(
          Arb.any(),
          undefinedProbability = oneThird,
          nullableProbability = oneThird,
        )
      var undefinedCount = 0
      var nullCount = 0
      var nonNullCount = 0
      checkAll(propTestConfig, arb) { nullableOptionalVariable ->
        when (nullableOptionalVariable) {
          OptionalVariable.Undefined -> undefinedCount++
          is OptionalVariable.Value<Any?> -> {
            if (nullableOptionalVariable.value == null) {
              nullCount++
            } else {
              nonNullCount++
            }
          }
        }
      }

      withClue("undefinedCount=$undefinedCount, nullCount=$nullCount nonNullCount=$nonNullCount") {
        val iterations = undefinedCount + nullCount + nonNullCount
        val observedCounts =
          longArrayOf(undefinedCount.toLong(), nullCount.toLong(), nonNullCount.toLong())
        val expectedObservedCount = iterations.toDouble() / observedCounts.size
        val expectedCounts = DoubleArray(observedCounts.size) { expectedObservedCount }
        val significanceResult = ChiSquareTest.withDefaults().test(expectedCounts, observedCounts)
        withClue("significanceResult=${significanceResult.print().value}") {
          significanceResult.reject(0.00001).shouldBeFalse()
        }
      }
    }

  @Test
  fun `nullableOptionalVariable(arb) produces values from the given Arb`() = runTest {
    val arb =
      Arb.dataConnect.nullableOptionalVariable(
        Arb.int(-1000..1000),
        undefinedProbability = 0.0,
        nullableProbability = 0.0,
      )
    checkAll(propTestConfig, arb) { nullableOptionalVariable ->
      check(nullableOptionalVariable is OptionalVariable.Value<Int?>)
      nullableOptionalVariable.value.shouldNotBeNull() shouldBeInRange -1000..1000
    }
  }
}

private val propTestConfig = PropTestConfig(iterations = 1000)
