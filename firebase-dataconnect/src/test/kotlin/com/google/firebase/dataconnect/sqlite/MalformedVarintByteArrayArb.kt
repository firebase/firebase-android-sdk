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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.byte
import io.kotest.property.asSample

/**
 * Creates and returns an [Arb] that generates byte arrays that will fail to be parsed as a varint.
 * The sizes of the generated arrays will be in the given [sizeRange] with the first
 * `sizeRange.first` bytes set to "continuation bytes".
 */
class MalformedVarintByteArrayArb(private val sizeRange: IntRange) :
  Arb<MalformedVarintByteArrayArb.Sample>() {

  class Sample(val byteArray: ByteArray, val edgeCase: EdgeCase?) {
    override fun toString() =
      "${MalformedVarintByteArrayArb::class.simpleName}.${this::class.simpleName}(" +
        "${byteArray.to0xHexString()} (${byteArray.size} bytes), edgeCase=$edgeCase)"
  }

  enum class EdgeCase {
    Size,
    Byte,
    AllContinuationBytes,
    SizeAndByte,
  }

  private val byteArb = Arb.byte()

  override fun sample(rs: RandomSource) = generateSample(rs).asSample()

  override fun edgecase(rs: RandomSource): Sample =
    when (val edgeCase = EdgeCase.entries.random(rs.random)) {
      EdgeCase.Size -> generateSample(rs, sizeEdgeCaseProbability = 1.0f, edgeCase = edgeCase)
      EdgeCase.Byte -> generateSample(rs, byteEdgeCaseProbability = 1.0f, edgeCase = edgeCase)
      EdgeCase.AllContinuationBytes ->
        generateSample(rs, continuationByteProbability = 1.0f, edgeCase = edgeCase)
      EdgeCase.SizeAndByte ->
        generateSample(
          rs,
          sizeEdgeCaseProbability = 1.0f,
          byteEdgeCaseProbability = 1.0f,
          edgeCase = edgeCase
        )
    }

  private fun generateSample(
    rs: RandomSource,
    sizeEdgeCaseProbability: Float = rs.random.nextFloat(),
    byteEdgeCaseProbability: Float = rs.random.nextFloat(),
    continuationByteProbability: Float = rs.random.nextFloat(),
    edgeCase: EdgeCase? = null,
  ): Sample {
    val size =
      if (sizeEdgeCaseProbability < 1.0f && rs.random.nextFloat() >= sizeEdgeCaseProbability) {
        sizeRange.random(rs.random)
      } else if (rs.random.nextBoolean()) {
        sizeRange.first
      } else {
        sizeRange.last
      }

    val byteArray = ByteArray(size)
    byteArray.indices.forEach { index ->
      byteArray[index] =
        byteArb.next(rs, byteEdgeCaseProbability).let {
          if (index <= sizeRange.first || rs.random.nextFloat() < continuationByteProbability) {
            it.toVarintContinuationByte()
          } else {
            it
          }
        }
    }
    return Sample(byteArray, edgeCase)
  }
}

/**
 * Converts this byte to a protobuf variant "continuation byte" by setting the high bit, and
 * returning the result.
 */
fun Byte.toVarintContinuationByte(): Byte = (toInt() or 0x80).toByte()
