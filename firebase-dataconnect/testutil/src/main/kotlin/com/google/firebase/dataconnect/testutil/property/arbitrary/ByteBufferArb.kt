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

@file:OptIn(ExperimentalStdlibApi::class)

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.asSample
import java.nio.ByteBuffer

class ByteBufferArb(
  private val byte: Arb<Byte> = Arb.byte(),
  private val remaining: Arb<Int> = Arb.int(0..100),
  private val headerSize: Arb<Int> = Arb.int(0..100),
  private val trailerSize: Arb<Int> = Arb.int(0..100),
  private val bufferType: Arb<BufferType> = Arb.enum<BufferType>(),
) : Arb<ByteBufferArb.Sample>() {

  enum class BufferType {
    Direct,
    Indirect,
  }

  class Sample(
    val byteBuffer: ByteBuffer,
    bytes: ByteArray,
    val remaining: Int,
    val headerSize: Int,
    val trailerSize: Int,
    val bufferType: BufferType,
  ) {
    private val _bytes = bytes.copyOf()
    val bytesSize: Int = bytes.size

    fun bytesCopy(): ByteArray = _bytes.copyOf()

    fun bytesSliceArray(indices: IntRange): ByteArray = _bytes.sliceArray(indices)

    val arrayOffset = if (byteBuffer.hasArray()) byteBuffer.arrayOffset() else -1
    val position = byteBuffer.position()
    val limit = byteBuffer.limit()
    val capacity = byteBuffer.capacity()

    init {
      check(remaining == byteBuffer.remaining())
      check(headerSize == byteBuffer.position())
      check(headerSize + remaining + trailerSize == byteBuffer.capacity())
    }

    override fun toString() =
      "${this::class.simpleName}(" +
        "bufferType=$bufferType, position=$position, limit=$limit, remaining=$remaining, " +
        "capacity=$capacity, arrayOffset=$arrayOffset " +
        "headerSize=$headerSize, trailerSize=$trailerSize, " +
        "bytes.size=${_bytes.size}, bytes=0x${_bytes.toHexString()})"
  }

  override fun sample(rs: RandomSource) =
    sample(
        rs,
        byteEdgeCaseProbability = rs.random.nextFloat(),
        remainingEdgeCaseProbability = rs.random.nextFloat(),
        headerSizeEdgeCaseProbability = rs.random.nextFloat(),
        trailerSizeEdgeCaseProbability = rs.random.nextFloat(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): Sample {
    val edgeCases =
      EdgeCase.entries.shuffled(rs.random).take(1 + rs.random.nextInt(EdgeCase.entries.size))

    return sample(
      rs,
      byteEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Byte)) 1.0f else 0.0f,
      remainingEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Remaining)) 1.0f else 0.0f,
      headerSizeEdgeCaseProbability = if (edgeCases.contains(EdgeCase.HeaderSize)) 1.0f else 0.0f,
      trailerSizeEdgeCaseProbability = if (edgeCases.contains(EdgeCase.TrailerSize)) 1.0f else 0.0f,
    )
  }

  private enum class EdgeCase {
    Byte,
    Remaining,
    HeaderSize,
    TrailerSize,
  }

  private fun sample(
    rs: RandomSource,
    byteEdgeCaseProbability: Float,
    remainingEdgeCaseProbability: Float,
    headerSizeEdgeCaseProbability: Float,
    trailerSizeEdgeCaseProbability: Float,
  ): Sample {
    val remaining = remaining.next(rs, remainingEdgeCaseProbability)
    val headerSize = headerSize.next(rs, headerSizeEdgeCaseProbability)
    val trailerSize = trailerSize.next(rs, trailerSizeEdgeCaseProbability)
    val capacity = remaining + headerSize + trailerSize

    val bufferType = bufferType.sample(rs).value
    val byteBuffer =
      when (bufferType) {
        BufferType.Direct -> ByteBuffer.allocateDirect(capacity)
        BufferType.Indirect -> ByteBuffer.allocate(capacity)
      }

    val byteArray = ByteArray(remaining)
    repeat(capacity) {
      val b = byte.next(rs, byteEdgeCaseProbability)
      byteBuffer.put(b)
      if (it in (headerSize until (headerSize + remaining))) {
        byteArray[it - headerSize] = b
      }
    }
    check(byteBuffer.remaining() == 0)

    byteBuffer.flip()
    byteBuffer.position(headerSize)
    byteBuffer.limit(headerSize + remaining)

    return Sample(
      byteBuffer = byteBuffer,
      bytes = byteArray,
      remaining = remaining,
      headerSize = headerSize,
      trailerSize = trailerSize,
      bufferType = bufferType,
    )
  }
}
