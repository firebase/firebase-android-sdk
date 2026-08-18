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

import com.google.firebase.dataconnect.testutil.property.arbitrary.SumPartitionArb.Sample.EdgeCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.apache.commons.statistics.inference.ChiSquareTest
import org.junit.Test

class SumPartitionArbUnitTest {

  @Test
  fun `produces valid partitions`() = runTest {
    checkAll(propTestConfig, Arb.int(0..100), Arb.int(1..20)) { sum, summandCount ->
      val arb = SumPartitionArb(sum, summandCount)
      val sample = arb.bind()
      assertSoftly {
        withClue("summandCount") { sample.summands.size shouldBe summandCount }
        withClue("sum") { sample.summands.sum() shouldBe sum }
        withClue("all elements non-negative") {
          sample.summands.forEach { it shouldBeGreaterThanOrEqual 0 }
        }
      }
    }
  }

  @Test
  fun `zero sum and summandCount produces empty list`() = runTest {
    checkAll(SumPartitionArb(sum = 0, summandCount = 0)) { sample ->
      assertSoftly { sample.summands shouldBe emptyList() }
    }
  }

  @Test
  fun `summandCount of one produces single element list containing sum`() = runTest {
    checkAll(propTestConfig, Arb.int(0..100)) { sum ->
      val arb = SumPartitionArb(sum, summandCount = 1)
      val sample = arb.bind()
      assertSoftly { sample.summands shouldBe listOf(sum) }
    }
  }

  @Test
  fun `invalid parameters throw IllegalArgumentException`() {
    shouldThrow<IllegalArgumentException> { SumPartitionArb(sum = -1, summandCount = 5) }
    shouldThrow<IllegalArgumentException> { SumPartitionArb(sum = 10, summandCount = -1) }
    shouldThrow<IllegalArgumentException> { SumPartitionArb(sum = 10, summandCount = 0) }
  }

  @Test
  fun `edgecase(summandCount=0) returns null`() = runTest {
    val arb = SumPartitionArb(sum = 0, summandCount = 0)
    val rs = RandomSource.default()

    arb.edgecase(rs) shouldBe null
  }

  @Test
  fun `edgecase(summandCount=1) returns null`() = runTest {
    checkAll(propTestConfig, Arb.nonNegativeInt()) { sum ->
      val arb = SumPartitionArb(sum = sum, summandCount = 1)

      arb.edgecase(randomSource()) shouldBe null
    }
  }

  @Test
  fun `edgecase(sum=0) returns null`() = runTest {
    checkAll(propTestConfig, Arb.nonNegativeInt()) { summandCount ->
      val arb = SumPartitionArb(sum = 0, summandCount = summandCount)

      arb.edgecase(randomSource()) shouldBe null
    }
  }

  @Test
  fun `edgecase returns valid edge case samples`() = runTest {
    checkAll(propTestConfig.withEdgeConfigEdgeCasesOnly(), nonNullEdgeCaseParametersArb()) {
      (sum, summandCount) ->
      val arb = SumPartitionArb(sum = sum, summandCount = summandCount)

      val sample = arb.edgecase(randomSource())

      withClue("sample=$sample") {
        sample.shouldNotBeNull()
        sample.edgeCase.shouldNotBeNull()
        when (sample.edgeCase) {
          EdgeCase.Zeroes -> sample.summands.count { it == 0 } shouldBeGreaterThan 0
          EdgeCase.SortedAscending -> sample.summands shouldContainExactly sample.summands.sorted()
          EdgeCase.SortedDescending ->
            sample.summands shouldContainExactly sample.summands.sortedDescending()
        }
      }
    }
  }

  @Test
  fun `edgecase returns even distribution of edge case types`() = runTest {
    val edgeCases = EdgeCase.entries.associateWith { 0 }.toMutableMap()

    checkAll(propTestConfig.withEdgeConfigEdgeCasesOnly(), nonNullEdgeCaseParametersArb()) {
      (sum, summandCount) ->
      val arb = SumPartitionArb(sum = sum, summandCount = summandCount)

      val sample = arb.edgecase(randomSource())

      checkNotNull(sample?.edgeCase)
      edgeCases[sample.edgeCase] = edgeCases[sample.edgeCase]!! + 1
    }

    withClue("edgeCases=${edgeCases.print().value}") {
      val iterations = edgeCases.values.sum()
      val observedCounts = edgeCases.values.map { it.toLong() }.toLongArray()
      val expectedObservedCount = iterations.toDouble() / observedCounts.size
      val expectedCounts = DoubleArray(observedCounts.size) { expectedObservedCount }
      val significanceResult = ChiSquareTest.withDefaults().test(expectedCounts, observedCounts)
      withClue("significanceResult=${significanceResult.print().value}") {
        significanceResult.reject(0.00001).shouldBeFalse()
      }
    }
  }

  @Test
  fun `generateSummands branch count minus one is greater than or equal to sum`() = runTest {
    checkAll(propTestConfig, Arb.int(0..10), Arb.int(12..100)) { sum, summandCount ->
      val arb = SumPartitionArb(sum, summandCount)

      val sample = arb.sample(randomSource()).value

      withClue("sample=$sample") {
        withClue("summands.size") { sample.summands.size shouldBe summandCount }
        withClue("summands.sum()") { sample.summands.sum() shouldBe sum }
        withClue("all elements non-negative") {
          sample.summands.forEach { it shouldBeGreaterThanOrEqual 0 }
        }
      }
    }
  }
}

private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private data class NonNullEdgeCaseParameters(val sum: Int, val summandCount: Int)

private fun nonNullEdgeCaseParametersArb(): Arb<NonNullEdgeCaseParameters> {
  val sumArb = Arb.intWithEvenNumDigitsDistribution(1..999_999_999)
  val summandCountArb = Arb.int(2..100)
  return Arb.bind(sumArb, summandCountArb, ::NonNullEdgeCaseParameters)
}
