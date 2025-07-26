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
import io.kotest.property.arbitrary.enum
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

  private val edgeCaseArb: Arb<EdgeCase> = Arb.enum()

  override fun edgecase(rs: RandomSource): List<T?> =
    when (edgeCaseArb.next(rs)) {
      EdgeCase.ALL_NULLS -> List(randomListSize(rs)) { null }
      EdgeCase.MIN_SIZE -> generateListWithAtLeastOneNullElement(rs, size.first)
      EdgeCase.MIN_SIZE_ALL_NULLS -> List(size.first) { null }
      EdgeCase.MAX_SIZE -> generateListWithAtLeastOneNullElement(rs, size.last)
      EdgeCase.MAX_SIZE_ALL_NULLS -> List(size.last) { null }
    }

  override fun sample(rs: RandomSource): Sample<List<T?>> =
    generateListWithAtLeastOneNullElement(rs).asSample()

  private fun generateListWithAtLeastOneNullElement(
    rs: RandomSource,
    listSize: Int = randomListSize(rs)
  ): List<T?> =
    buildList(listSize) {
      repeat(listSize) {
        if (rs.random.nextDouble() < nullProbability) {
          add(null)
        } else {
          add(arb.next(rs))
        }
      }
      if (!contains(null)) {
        set(rs.random.nextInt(size), null)
      }
    }

  private fun randomListSize(rs: RandomSource): Int = rs.random.nextInt(size)

  private enum class EdgeCase {
    ALL_NULLS,
    MIN_SIZE,
    MIN_SIZE_ALL_NULLS,
    MAX_SIZE,
    MAX_SIZE_ALL_NULLS,
  }
}
