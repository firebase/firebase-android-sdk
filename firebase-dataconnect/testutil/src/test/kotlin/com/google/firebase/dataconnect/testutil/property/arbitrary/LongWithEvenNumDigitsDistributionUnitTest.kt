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

import com.google.firebase.dataconnect.testutil.StandardDeviationMode
import com.google.firebase.dataconnect.testutil.standardDeviation
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.checkAll
import kotlin.math.pow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LongWithEvenNumDigitsDistributionUnitTest {

  @Test
  fun `nonNegativeLongWithEvenNumDigitsDistribution produces non-negative values`() = runTest {
    checkAll(propTestConfig, Arb.nonNegativeLongWithEvenNumDigitsDistribution()) { value ->
      value shouldBeGreaterThanOrEqual 0
    }
  }

  @Test
  fun `nonNegativeLongWithEvenNumDigitsDistribution respects the given min`() = runTest {
    checkAll(propTestConfig, minArb()) { min ->
      val arb = Arb.nonNegativeLongWithEvenNumDigitsDistribution(min = min)

      val generatedValue = arb.bind()

      generatedValue shouldBeGreaterThanOrEqual min
    }
  }

  @Test
  fun `nonNegativeLongWithEvenNumDigitsDistribution produces all num digits`() = runTest {
    val numDigitsGenerated = mutableSetOf<Int>()

    checkAll(propTestConfig, Arb.nonNegativeLongWithEvenNumDigitsDistribution()) { value ->
      val numDigits = value.toString().length
      numDigitsGenerated.add(numDigits)
    }

    numDigitsGenerated shouldContainExactlyInAnyOrder (1..19).toSet()
  }

  @Test
  fun `nonNegativeLongWithEvenNumDigitsDistribution produces all num digits when min is specified`() =
    runTest {
      checkAll(propTestConfig, minArb()) { min ->
        val arb = Arb.nonNegativeLongWithEvenNumDigitsDistribution(min = min)

        val numDigitsGenerated = buildSet {
          repeat(propTestConfig.iterations!!) { add(arb.bind().toString().length) }
        }

        val minNumDigitsGenerated = min.toString().length
        numDigitsGenerated shouldContainExactlyInAnyOrder (minNumDigitsGenerated..19).toSet()
      }
    }

  @Test
  fun `nonNegativeLongWithEvenNumDigitsDistribution produces a range of num digits`() = runTest {
    val countByNumDigits = mutableMapOf<Int, Int>()

    checkAll(propTestConfig, Arb.nonNegativeLongWithEvenNumDigitsDistribution()) { value ->
      val numDigits = value.toString().length
      val oldCount = countByNumDigits.getOrDefault(numDigits, 0)
      countByNumDigits[numDigits] = oldCount + 1
    }

    val standardDeviation = countByNumDigits.values.standardDeviation(StandardDeviationMode.Sample)
    withClue("countByNumDigits=${countByNumDigits.toSortedMap()}") {
      standardDeviation shouldBeLessThan 12.0
    }
  }

  @Test
  fun `nonNegativeLongWithEvenNumDigitsDistribution produces a range of num digits when min is specified`() =
    runTest {
      checkAll(propTestConfig, minArb().filterNot { it.toString().length == 19 }) { min ->
        val arb = Arb.nonNegativeLongWithEvenNumDigitsDistribution(min = min)

        val countByNumDigits: Map<Int, Int> = buildMap {
          repeat(propTestConfig.iterations!!) {
            val numDigits = arb.bind().toString().length
            val oldCount = getOrDefault(numDigits, 0)
            put(numDigits, oldCount + 1)
          }
        }

        val standardDeviation =
          countByNumDigits.values.standardDeviation(StandardDeviationMode.Sample)
        val growthRate = 4.0.pow(1.0 / (19 - min.toString().length))
        val maxStandardDeviation = 12.0 * growthRate.pow(min.toString().length - 1)
        withClue("countByNumDigits=${countByNumDigits.toSortedMap()}") {
          println(
            "zzyzx countByNumDigits.size=${countByNumDigits.size}, standardDeviation=$standardDeviation"
          )
          standardDeviation shouldBeLessThan maxStandardDeviation
        }
      }
    }
}

private val propTestConfig = PropTestConfig(iterations = 1000)

private fun minArb(): Arb<Long> = Arb.nonNegativeLongWithEvenNumDigitsDistribution()
