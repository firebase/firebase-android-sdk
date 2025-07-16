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

import com.google.firebase.dataconnect.testutil.calculateNumBase10Digits
import com.google.firebase.dataconnect.testutil.shouldHaveStandardDeviationLessThan
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.common.runBlocking
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IntsTest {

  @Test
  fun `intWithUniformNumDigitsDistribution with no arguments should generate all numbers`() {
    val numDigitsOccurrenceCounter = test(Arb.intWithUniformNumDigitsDistribution())
    assertSoftly {
      numDigitsOccurrenceCounter.run {
        shouldHaveEncounteredEdgeCasesInRange(Int.MIN_VALUE..Int.MAX_VALUE)
        shouldHaveNumDigitOccurrenceCountStandardDeviationLessThan(50)
        shouldHaveEncounteredValuesWithNumDigits(-10..10)
      }
    }
  }

  @Test
  fun `intWithUniformNumDigitsDistribution with null argument should generate all numbers`() {
    val numDigitsOccurrenceCounter = test(Arb.intWithUniformNumDigitsDistribution(null))
    assertSoftly {
      numDigitsOccurrenceCounter.run {
        shouldHaveEncounteredEdgeCasesInRange(Int.MIN_VALUE..Int.MAX_VALUE)
        shouldHaveNumDigitOccurrenceCountStandardDeviationLessThan(50)
        shouldHaveEncounteredValuesWithNumDigits(-10..10)
      }
    }
  }

  @Test
  fun `intWithUniformNumDigitsDistribution should generate appropriate numbers`() = runTest {
    val cachedIntWithNumBase10DigitsArgs = mutableMapOf<Int, Arb<Int>>()

    data class TestCase(val lowerNumDigits: Int, val upperNumDigits: Int, val range: IntRange)
    val numDigitsArb = Arb.of((-10..10).filterNot { it == 0 })
    val testCaseArb =
      Arb.twoValues(numDigitsArb)
        .map { it.sorted() }
        .flatMap { (lowerNumDigits, upperNumDigits) ->
          val lowerBoundArb =
            cachedIntWithNumBase10DigitsArgs.getOrPut(lowerNumDigits) {
              Arb.intWithNumBase10Digits(lowerNumDigits)
            }
          val upperBoundArb =
            cachedIntWithNumBase10DigitsArgs.getOrPut(upperNumDigits) {
              Arb.intWithNumBase10Digits(upperNumDigits)
            }
          Arb.bind(lowerBoundArb, upperBoundArb) { lowerBound, upperBound ->
            if (lowerBound <= upperBound) {
              TestCase(
                lowerNumDigits = lowerNumDigits,
                upperNumDigits = upperNumDigits,
                range = lowerBound..upperBound
              )
            } else {
              TestCase(
                lowerNumDigits = lowerNumDigits,
                upperNumDigits = upperNumDigits,
                range = upperBound..lowerBound
              )
            }
          }
        }

    checkAll(100, testCaseArb) { testCase ->
      val numDigitsOccurrenceCounter = test(Arb.intWithUniformNumDigitsDistribution(testCase.range))
      assertSoftly {
        numDigitsOccurrenceCounter.run {
          shouldHaveEncounteredEdgeCasesInRange(testCase.range)
          if (testCase.lowerNumDigits != testCase.upperNumDigits) {
            shouldHaveNumDigitOccurrenceCountStandardDeviationLessThan(200)
          }
          shouldHaveEncounteredValuesWithNumDigits(testCase.lowerNumDigits..testCase.upperNumDigits)
        }
      }
    }
  }

  private fun test(arb: Arb<Int>) =
    NumDigitsOccurrenceCounter().apply {
      runBlocking { checkAll(propTestConfig, arb) { value -> count(value) } }
    }

  class NumDigitsOccurrenceCounter {

    private val numbers = mutableSetOf<Int>()
    private val occurrenceCountByNumDigits = mutableMapOf<Int, Int>()

    fun count(value: Int) {
      numbers.add(value)

      val numDigits = value.calculateNumBase10Digits()
      val oldCount = occurrenceCountByNumDigits.getOrDefault(numDigits, 0)
      occurrenceCountByNumDigits[numDigits] = oldCount + 1
    }

    fun shouldHaveEncounteredEdgeCasesInRange(range: IntRange) {
      edgeCases.filter { range.contains(it) }.forEach { numbers shouldContain it }
    }

    fun shouldHaveNumDigitOccurrenceCountStandardDeviationLessThan(max: Number) {
      withClue(occurrenceCountByNumDigits) {
        occurrenceCountByNumDigits.values shouldHaveStandardDeviationLessThan max
      }
    }

    fun shouldHaveEncounteredValuesWithNumDigits(range: IntRange) {
      occurrenceCountByNumDigits.keys shouldContainExactlyInAnyOrder range.filterNot { it == 0 }
    }
  }

  private companion object {

    @ExperimentalKotest
    val propTestConfig =
      PropTestConfig(
        iterations = 10_000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.5)
      )

    val edgeCases: List<Int> =
      listOf(
        0,
        -1,
        1,
        9,
        -9,
        10,
        -10,
        99,
        -99,
        100,
        -100,
        999,
        -999,
        1000,
        -1000,
        9999,
        -9999,
        10000,
        -10000,
        99999,
        -99999,
        100000,
        -100000,
        999999,
        -999999,
        1000000,
        -1000000,
        9999999,
        -9999999,
        10000000,
        -10000000,
        99999999,
        -99999999,
        100000000,
        -100000000,
        999999999,
        -999999999,
        1000000000,
        -1000000000,
        Int.MIN_VALUE,
        Int.MAX_VALUE,
      )
  }
}
