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

@file:Suppress("UnusedReceiverParameter")

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.assertions.print.print
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.asSample
import java.util.Objects
import kotlin.collections.sorted

/**
 * A Kotest [Arb] that generates random partitions of a non-negative integer [sum] into a fixed
 * number of non-negative parts ([summandCount]).
 *
 * Each generated [Sample] contains a list of integers whose size is exactly [summandCount] and
 * whose elements sum up to exactly [sum].
 *
 * For example, with `sum = 10` and `summandCount = 3`, generated samples could include:
 * - `[3, 2, 5]`
 * - `[0, 10, 0]`
 * - `[1, 7, 2]`
 *
 * @param sum The target sum that all generated summands must add up to. Must be non-negative.
 * @param summandCount The number of elements in the generated list of summands. Must be
 * non-negative. If [summandCount] is `0`, then [sum] must also be `0`.
 */
class SumPartitionArb(private val sum: Int, private val summandCount: Int) :
  Arb<SumPartitionArb.Sample>() {

  init {
    require(sum >= 0) { "invalid sum: $sum" }
    require(summandCount >= 0) { "invalid summandCount: $summandCount" }
    require(summandCount > 0 || sum == 0) {
      "invalid sum/summandCount pair: sum=$sum, summandCount=$summandCount"
    }
  }

  override fun edgecase(rs: RandomSource): SumPartitionArb.Sample? {
    if (summandCount < 2 || sum == 0) {
      return null
    }

    val edgeCase = Sample.EdgeCase.entries.random(rs.random)
    val summands: List<Int> =
      when (edgeCase) {
        Sample.EdgeCase.OneZero ->
          generateSummands(rs, summandCount - 1).plus(0).shuffled(rs.random)
        Sample.EdgeCase.AllButOneZero ->
          List(summandCount) { if (it == 0) sum else 0 }.shuffled(rs.random)
        Sample.EdgeCase.SortedAscending -> generateSummands(rs, summandCount).sorted()
        Sample.EdgeCase.SortedDescending -> generateSummands(rs, summandCount).sortedDescending()
      }

    return Sample(summands, edgeCase)
  }

  override fun sample(rs: RandomSource): io.kotest.property.Sample<Sample> {
    val summands = generateSummands(rs, summandCount)
    val sample = Sample(summands, edgeCase = null)
    return sample.asSample()
  }

  private fun generateSummands(
    rs: RandomSource,
    summandCount: Int,
  ): List<Int> {
    if (summandCount == 0) {
      return emptyList()
    }
    if (summandCount == 1) {
      return listOf(sum)
    }

    // Stars and Bars: choose (m - 1) distinct dividers from (n + m - 1) positions
    val cuts = (0 until sum + summandCount - 1).shuffled(rs.random).take(summandCount - 1).sorted()
    val allCuts = listOf(-1) + cuts + listOf(sum + summandCount - 1)

    // The segment lengths between cuts give values that sum exactly to n
    return List(summandCount) { i -> allCuts[i + 1] - allCuts[i] - 1 }
  }

  class Sample(
    val summands: List<Int>,
    val edgeCase: EdgeCase?,
  ) {

    override fun equals(other: Any?) = other is Sample && other.summands == summands

    override fun hashCode() = Objects.hash("SumPartitionArb.Sample", summands)

    override fun toString() =
      "SumPartitionArb.Sample(summands=${summands.print().value}, edgeCase=${edgeCase?.name})"

    enum class EdgeCase {
      OneZero,
      AllButOneZero,
      SortedAscending,
      SortedDescending,
    }
  }
}
