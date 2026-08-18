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

import io.kotest.assertions.print.print
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.asSample

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
    require(sum.toLong() + summandCount - 1 <= Int.MAX_VALUE) {
      "sum+summandCount-1 exceeds Int.MAX_VALUE: sum=$sum, summandCount=$summandCount"
    }
  }

  private val edgeCaseZeroesCountArb: Arb<Int> = run {
    if (sum == 0) {
      arbitrary { throw IllegalStateException("internal error h5zagzq8g4: should never get here") }
    } else {
      Arb.int(1 until summandCount)
    }
  }

  override fun edgecase(rs: RandomSource): Sample? {
    if (summandCount < 2 || sum == 0) {
      return null
    }

    val edgeCase = Sample.EdgeCase.entries.random(rs.random)
    val summands: List<Int> =
      when (edgeCase) {
        Sample.EdgeCase.Zeroes ->
          buildList(summandCount) {
            val zeroesCount = edgeCaseZeroesCountArb.next(rs, edgeCaseProbability = 0.3f)
            check(zeroesCount > 0)
            repeat(zeroesCount) { add(0) }
            addAll(generateSummands(rs, summandCount - zeroesCount))
            shuffle(rs.random)
          }
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
    count: Int,
  ): List<Int> {
    if (count == 0) {
      return emptyList()
    }
    if (count == 1) {
      return listOf(sum)
    }

    val maxPosition = sum + count - 1
    val cuts: List<Int> =
      if (count - 1 < sum) {
        buildSet {
            while (size < count - 1) {
              add(rs.random.nextInt(maxPosition))
            }
          }
          .sorted()
      } else {
        val nonCuts =
          buildSet {
              while (size < sum) {
                add(rs.random.nextInt(maxPosition))
              }
            }
            .sorted()
        buildList(count - 1) {
          var prev = -1
          for (nonCut in nonCuts) {
            for (v in (prev + 1) until nonCut) {
              add(v)
            }
            prev = nonCut
          }
          for (v in (prev + 1) until maxPosition) {
            add(v)
          }
        }
      }

    return buildList(count) {
      var prev = -1
      for (cut in cuts) {
        add(cut - prev - 1)
        prev = cut
      }
      add(maxPosition - prev - 1)
    }
  }

  class Sample(
    val summands: List<Int>,
    val edgeCase: EdgeCase?,
  ) {

    override fun equals(other: Any?) = other is Sample && other.summands == summands

    override fun hashCode() = summands.hashCode()

    override fun toString() =
      "SumPartitionArb.Sample(summands=${summands.print().value}, edgeCase=${edgeCase?.name})"

    enum class EdgeCase {
      Zeroes,
      SortedAscending,
      SortedDescending,
    }
  }
}
