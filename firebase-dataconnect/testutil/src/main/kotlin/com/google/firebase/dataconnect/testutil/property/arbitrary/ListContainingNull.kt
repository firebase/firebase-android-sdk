/*
 * Copyright 2025 Google LLC
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
import io.kotest.property.Exhaustive
import io.kotest.property.Gen
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.next
import io.kotest.property.asSample
import kotlin.random.nextInt

/**
 * Returns an [Arb] that generates lists containing elements produced by the given [Gen], while
 * guaranteeing that at least one of the elements of the list is `null`.
 *
 * @param gen The [Gen] for non-null elements in the generated lists. If this [Gen] _also_ produces
 * `null` then such a `null` element will satisfy the requirement that at least one element in
 * generated lists is `null` even if `null` was not chosen by the given [nullProbability].
 * @param size The range of list sizes that will be produced by the returned [Arb]. The lower bound
 * of the range must be at least 1 and must be a non-empty range.
 * @param nullProbability The probability that any given element of generated lists will be `null`
 * instead of pulling a value from [gen]. This value must be greater than `0.0` and less than or
 * equal to `1.0`.
 */
fun <T> Arb.Companion.listContainingNull(
  gen: Gen<T>,
  size: IntRange = 1..100,
  nullProbability: Double = 0.5
): Arb<List<T?>> = ListContainingNullArb(gen, size, nullProbability)

private class ListContainingNullArb<T>(
  gen: Gen<T>,
  private val size: IntRange,
  private val nullProbability: Double
) : Arb<List<T?>>() {

  init {
    require(size.first > 0) {
      "the lower bound of the size range must be at least 1, " +
        "but got: ${size.first} (size=$size)"
    }
    require(!size.isEmpty()) {
      "the given size range is empty, but a non-empty range is required (size=$size)"
    }
    require(nullProbability.isFinite() && nullProbability > 0.0 && nullProbability <= 1.0) {
      "invalid nullProbability: $nullProbability " +
        "(must be strictly greater than 0.0 and less than or equal to 1.0)"
    }
  }

  private val arb: Arb<T> =
    when (gen) {
      is Arb<T> -> gen
      is Exhaustive<T> -> gen.toArb()
    }

  private val edgeCaseArb: Arb<EdgeCase> = Arb.edgeCase()

  override fun sample(rs: RandomSource): Sample<List<T?>> =
    sample(rs, listSize = randomListSize(rs)).asSample()

  private fun sample(rs: RandomSource, listSize: Int): List<T?> {
    require(listSize > 0) { "invalid listSize: $listSize (must be greater than zero)" }
    val guaranteedNullIndex = randomGuaranteedNullIndex(rs, listSize)
    return List(listSize) { index ->
      if (index == guaranteedNullIndex || rs.random.nextDouble() < nullProbability) {
        null
      } else {
        arb.next(rs)
      }
    }
  }

  override fun edgecase(rs: RandomSource): List<T?> {
    val (listSizeCategory, nullPositions) = edgeCaseArb.next(rs)

    val listSize =
      when (listSizeCategory) {
        EdgeCase.ListSizeCategory.MIN -> size.first
        EdgeCase.ListSizeCategory.MAX -> size.last
        EdgeCase.ListSizeCategory.RANDOM -> randomListSize(rs)
      }

    val guaranteedNullIndex =
      when (nullPositions) {
        EdgeCase.NullPositions.RANDOM -> randomGuaranteedNullIndex(rs, listSize)
        EdgeCase.NullPositions.FIRST,
        EdgeCase.NullPositions.LAST,
        EdgeCase.NullPositions.FIRST_AND_LAST,
        EdgeCase.NullPositions.ALL -> -1
      }

    val firstIndex = 0
    val lastIndex = listSize - 1

    return List(listSize) {
      val isNull =
        when (nullPositions) {
          EdgeCase.NullPositions.FIRST -> it == firstIndex
          EdgeCase.NullPositions.LAST -> it == lastIndex
          EdgeCase.NullPositions.FIRST_AND_LAST -> it == firstIndex || it == lastIndex
          EdgeCase.NullPositions.ALL -> true
          EdgeCase.NullPositions.RANDOM -> it == guaranteedNullIndex
        }
      if (isNull) null else arb.next(rs)
    }
  }

  private fun randomListSize(rs: RandomSource): Int = rs.random.nextInt(size)

  private fun randomGuaranteedNullIndex(rs: RandomSource, listSize: Int): Int =
    rs.random.nextInt(listSize)

  private data class EdgeCase(
    val listSizeCategory: ListSizeCategory,
    val nullPositions: NullPositions
  ) {

    enum class ListSizeCategory {
      MIN,
      MAX,
      RANDOM,
    }

    enum class NullPositions {
      FIRST,
      LAST,
      FIRST_AND_LAST,
      ALL,
      RANDOM,
    }
  }

  private companion object {
    fun Arb.Companion.edgeCase(
      listSizeCategory: Arb<EdgeCase.ListSizeCategory> = Arb.enum(),
      nullPositions: Arb<EdgeCase.NullPositions> = Arb.enum()
    ): Arb<EdgeCase> =
      Arb.bind(listSizeCategory, nullPositions) { listSizeCategoryValue, nullPositionsValue ->
          EdgeCase(listSizeCategoryValue, nullPositionsValue)
        }
        .filterNot {
          it.listSizeCategory == EdgeCase.ListSizeCategory.RANDOM &&
            it.nullPositions == EdgeCase.NullPositions.RANDOM
        }
  }
}
