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

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.asSample
import kotlin.random.nextInt

fun <T> Arb.Companion.listNoRepeat(elementArb: Arb<T>, sizeRange: IntRange): Arb<List<T>> =
  ListNoRepeatsArb(elementArb, sizeRange)

private class ListNoRepeatsArb<T>(private val elementArb: Arb<T>, private val sizeRange: IntRange) :
  Arb<List<T>>() {

  init {
    require(sizeRange.first >= 0) {
      "sizeRange.first=${sizeRange.first}, but it must be greater than or equal to zero (sizeRange=$sizeRange)"
    }
    require(!sizeRange.isEmpty()) { "sizeRange must not be empty (sizeRange=$sizeRange)" }
  }

  private val sizeArb = Arb.int(sizeRange)

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        sizeEdgeCaseProbability = rs.random.nextFloat(),
        elementEdgeCaseProbability = rs.random.nextFloat(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): List<T> {
    val edgeCaseCount = rs.random.nextInt(1..EdgeCase.entries.size)
    val edgeCases = EdgeCase.entries.shuffled(rs.random).take(edgeCaseCount)
    val sizeEdgeCaseProbability = if (EdgeCase.Size in edgeCases) 1.0f else 0.0f
    val elementEdgeCaseProbability = if (EdgeCase.Element in edgeCases) 1.0f else 0.0f
    return generate(
      rs,
      sizeEdgeCaseProbability = sizeEdgeCaseProbability,
      elementEdgeCaseProbability = elementEdgeCaseProbability,
    )
  }

  private fun generate(
    rs: RandomSource,
    sizeEdgeCaseProbability: Float,
    elementEdgeCaseProbability: Float,
  ): List<T> {
    val size = sizeArb.next(rs, sizeEdgeCaseProbability)
    return List(size) { elementArb.next(rs, elementEdgeCaseProbability) }
  }

  private enum class EdgeCase {
    Size,
    Element,
  }
}
