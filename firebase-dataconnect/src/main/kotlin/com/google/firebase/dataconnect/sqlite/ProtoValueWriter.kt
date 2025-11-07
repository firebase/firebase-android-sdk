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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.channels.WritableByteChannel
import java.nio.charset.CharsetEncoder
import java.nio.charset.CoderResult
import kotlin.math.absoluteValue

internal class ProtoValueWriter(
  private val channel: WritableByteChannel,
  private val utf8CharsetEncoder: CharsetEncoder,
) {

  private val byteBuffer = ByteBuffer.allocate(2048).order(ByteOrder.BIG_ENDIAN)

  fun flush() {
    byteBuffer.flip()
    while (byteBuffer.remaining() > 0) {
      channel.write(byteBuffer)
    }
    byteBuffer.clear()
  }

  fun writeInt(value: Int) {
    ensureRemaining(4)
    byteBuffer.putInt(value)
  }

  fun writeByte(value: Byte) {
    ensureRemaining(1)
    byteBuffer.put(value)
  }

  fun writeDouble(value: Double) {
    ensureRemaining(8)
    byteBuffer.putDouble(value)
  }

  fun writeStringUtf8(string: String, expectedByteCount: Int) {
    utf8CharsetEncoder.reset()
    val charBuffer = CharBuffer.wrap(string)

    writeInt(expectedByteCount)
    writeInt(string.length)

    var byteWriteCount = 0
    while (true) {
      val byteBufferPositionBefore = byteBuffer.position()

      val coderResult1 =
        if (charBuffer.hasRemaining()) {
          utf8CharsetEncoder.encode(charBuffer, byteBuffer, true)
        } else {
          CoderResult.UNDERFLOW
        }

      val coderResult2 =
        if (coderResult1.isUnderflow) {
          utf8CharsetEncoder.flush(byteBuffer)
        } else {
          coderResult1
        }

      val byteBufferPositionAfter = byteBuffer.position()
      byteWriteCount += byteBufferPositionAfter - byteBufferPositionBefore

      if (coderResult2.isUnderflow) {
        break
      }

      if (!coderResult2.isOverflow) {
        coderResult2.throwException()
      }

      flushOnce()
    }

    check(byteWriteCount == expectedByteCount) {
      "internal error rvmdh67npk: byteWriteCount=$byteWriteCount " +
        "should be equal to expectedByteCount=$expectedByteCount, but they differ by " +
        "${(expectedByteCount-byteWriteCount).absoluteValue}"
    }
  }

  fun writeStringCustomUtf16(string: String, expectedByteCount: Int) {
    writeInt(string.length)

    var byteWriteCount = 0
    var stringOffset = 0
    while (stringOffset < string.length) {
      val charBuffer = byteBuffer.asCharBuffer()
      val putLength = charBuffer.remaining().coerceAtMost(string.length - stringOffset)
      charBuffer.put(string, stringOffset, stringOffset + putLength)

      byteBuffer.position(byteBuffer.position() + (putLength * 2))
      flushOnce()

      byteWriteCount += putLength * 2
      stringOffset += putLength
    }

    check(byteWriteCount == expectedByteCount) {
      "internal error agdf5qbwwp: byteWriteCount=$byteWriteCount " +
        "should be equal to expectedByteCount=$expectedByteCount, but they differ by " +
        "${(expectedByteCount - byteWriteCount).absoluteValue}"
    }
  }

  class NoBytesWrittenException(message: String) : Exception(message)

  private fun flushOnce(): Int {
    byteBuffer.flip()
    val byteWriteCount = channel.write(byteBuffer)
    byteBuffer.compact()
    return byteWriteCount
  }

  private fun ensureRemaining(minRemaining: Int) {
    require(minRemaining <= byteBuffer.capacity()) {
      "minRemaining=$minRemaining must be less than or equal to " +
        "byteBuffer.capacity()=${byteBuffer.capacity()}"
    }

    while (byteBuffer.remaining() < minRemaining) {
      val byteWriteCount = flushOnce()
      if (byteWriteCount < 1) {
        throw NoBytesWrittenException(
          "no bytes were written despite ${byteBuffer.position()} byte available for writing"
        )
      }
    }
  }
}
