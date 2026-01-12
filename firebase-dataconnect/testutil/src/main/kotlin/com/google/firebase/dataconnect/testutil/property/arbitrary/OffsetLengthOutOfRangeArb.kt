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
import io.kotest.property.asSample

class OffsetLengthOutOfRangeArb(val arraySize: Int) : Arb<OffsetLengthOutOfRangeArb.Sample>() {

  data class Sample(val offset: Int, val length: Int)

  private val offsetValidRange = 0..arraySize
  private val offsetInvalidRange = (arraySize + 1)..(2 * (arraySize + 1))

  override fun sample(rs: RandomSource) =
    sample(
        rs = rs,
        offsetEdgeCaseProbability = rs.random.nextFloat(),
        lengthEdgeCaseProbability = rs.random.nextFloat(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource) =
    when (EdgeCase.entries.random(rs.random)) {
      EdgeCase.Offset ->
        sample(rs, offsetEdgeCaseProbability = 1.0f, lengthEdgeCaseProbability = 0.0f)
      EdgeCase.Length ->
        sample(rs, offsetEdgeCaseProbability = 0.0f, lengthEdgeCaseProbability = 1.0f)
      EdgeCase.OffsetAndLength ->
        sample(rs, offsetEdgeCaseProbability = 1.0f, lengthEdgeCaseProbability = 1.0f)
    }

  private enum class EdgeCase {
    Offset,
    Length,
    OffsetAndLength,
  }

  private tailrec fun sample(
    rs: RandomSource,
    offsetEdgeCaseProbability: Float,
    lengthEdgeCaseProbability: Float,
  ): OffsetLengthOutOfRangeArb.Sample {
    val offsetOutOfRangeProbability = rs.random.nextFloat()
    val lengthOutOfRangeProbability = rs.random.nextFloat()
    val offsetOutOfRange = rs.random.nextFloat() < offsetOutOfRangeProbability
    val lengthOutOfRange = rs.random.nextFloat() < lengthOutOfRangeProbability
    if (!offsetOutOfRange && !lengthOutOfRange) {
      return sample(
        rs,
        offsetEdgeCaseProbability = offsetEdgeCaseProbability,
        lengthEdgeCaseProbability = lengthEdgeCaseProbability,
      )
    }

    val offsetEdgeCase = rs.random.nextFloat() < offsetEdgeCaseProbability
    val lengthEdgeCase = rs.random.nextFloat() < lengthEdgeCaseProbability

    val offsetRange = if (offsetOutOfRange) offsetInvalidRange else offsetValidRange
    val offset =
      if (offsetEdgeCase) {
        if (rs.random.nextBoolean()) offsetRange.first else offsetRange.last
      } else {
        offsetRange.random(rs.random)
      }

    val lengthRange =
      if (!lengthOutOfRange) {
        0..arraySize * 2
      } else if (!offsetOutOfRange) {
        (arraySize - offset + 1)..(arraySize + 1) * 2
      } else {
        (arraySize + 1)..Int.MAX_VALUE
      }
    val length =
      if (lengthEdgeCase) {
        if (rs.random.nextBoolean()) lengthRange.first else lengthRange.last
      } else {
        lengthRange.random(rs.random)
      }

    return OffsetLengthOutOfRangeArb.Sample(offset = offset, length = length)
  }
}
