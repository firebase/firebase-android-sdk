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

import com.google.firebase.dataconnect.sqlite.QueryResultCodec.Entity
import com.google.firebase.dataconnect.util.StringUtil.calculateUtf8ByteCount
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction
import kotlin.math.absoluteValue

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultEncoder(
  private val channel: WritableByteChannel,
  private val entityFieldName: String? = null
) {

  val entities: MutableList<Entity> = mutableListOf()

  private val charsetEncoder =
    Charsets.UTF_8.newEncoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT)

  private val byteBuffer = ByteBuffer.allocate(2048).order(ByteOrder.BIG_ENDIAN)

  class EncodeResult(val byteArray: ByteArray, val entities: List<Entity>)

  fun encode(queryResult: Struct) {
    writeStruct(queryResult, includeTypeIndicator = false)
  }

  fun flush() {
    byteBuffer.flip()
    while (byteBuffer.remaining() > 0) {
      channel.write(byteBuffer)
    }
    byteBuffer.clear()
  }

  private fun flushOnce() {
    byteBuffer.flip()
    channel.write(byteBuffer)
    byteBuffer.compact()
  }

  private fun ensureRemaining(minRemainingBytes: Int) {
    while (byteBuffer.remaining() < minRemainingBytes) {
      flushOnce()
    }
  }

  private fun writeInt(value: Int) {
    ensureRemaining(4)
    byteBuffer.putInt(value)
  }

  private fun writeByte(value: Byte) {
    ensureRemaining(1)
    byteBuffer.put(value)
  }

  private fun writeDouble(value: Double) {
    ensureRemaining(8)
    byteBuffer.putDouble(value)
  }

  private fun writeString(string: String) {
    if (string.isEmpty()) {
      writeByte(QueryResultCodec.VALUE_STRING_EMPTY)
      return
    }

    val utf8ByteCount = string.calculateUtf8ByteCount()
    val utf16ByteCount = string.length * 2
    charsetEncoder.reset() // Prepare `charsetEncoder` for calling `canEncode()`.

    if (utf8ByteCount <= utf16ByteCount && charsetEncoder.canEncode(string)) {
      writeStringUtf8(string, utf8ByteCount)
    } else {
      writeStringCustomUtf16(string, utf16ByteCount)
    }
  }

  private fun writeStringUtf8(string: String, expectedByteCount: Int) {
    charsetEncoder.reset()
    val charBuffer = CharBuffer.wrap(string)

    writeByte(QueryResultCodec.VALUE_STRING_UTF8)
    writeInt(expectedByteCount)
    writeInt(string.length)

    var byteWriteCount = 0
    while (true) {
      val byteBufferPositionBefore = byteBuffer.position()

      val coderResult1 =
        if (charBuffer.hasRemaining()) {
          charsetEncoder.encode(charBuffer, byteBuffer, true)
        } else {
          CoderResult.UNDERFLOW
        }

      val coderResult2 =
        if (coderResult1.isUnderflow) {
          charsetEncoder.flush(byteBuffer)
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

  private fun writeStringCustomUtf16(string: String, expectedByteCount: Int) {
    writeByte(QueryResultCodec.VALUE_STRING_UTF16)
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

  private fun writeList(listValue: ListValue) {
    writeByte(QueryResultCodec.VALUE_LIST)
    writeInt(listValue.valuesCount)
    repeat(listValue.valuesCount) { writeValue(listValue.getValues(it)) }
  }

  private fun writeStruct(struct: Struct, includeTypeIndicator: Boolean = true) {
    if (includeTypeIndicator) {
      writeByte(QueryResultCodec.VALUE_STRUCT)
    }
    val map = struct.fieldsMap
    writeInt(map.size)
    map.entries.forEach { (key, value) ->
      writeString(key)
      writeValue(value)
    }
  }

  private fun writeValue(value: Value) {
    when (value.kindCase) {
      Value.KindCase.NULL_VALUE -> writeByte(QueryResultCodec.VALUE_NULL)
      Value.KindCase.NUMBER_VALUE -> {
        writeByte(QueryResultCodec.VALUE_NUMBER)
        writeDouble(value.numberValue)
      }
      Value.KindCase.BOOL_VALUE ->
        writeByte(
          if (value.boolValue) QueryResultCodec.VALUE_BOOL_TRUE
          else QueryResultCodec.VALUE_BOOL_FALSE
        )
      Value.KindCase.STRING_VALUE -> writeString(value.stringValue)
      Value.KindCase.STRUCT_VALUE -> writeStruct(value.structValue)
      Value.KindCase.LIST_VALUE -> writeList(value.listValue)
      Value.KindCase.KIND_NOT_SET -> writeByte(QueryResultCodec.VALUE_KIND_NOT_SET)
    }
  }

  companion object {

    fun encode(queryResult: Struct, entityFieldName: String? = null): EncodeResult =
      ByteArrayOutputStream().use { byteArrayOutputStream ->
        val entities =
          Channels.newChannel(byteArrayOutputStream).use { writableByteChannel ->
            val encoder = QueryResultEncoder(writableByteChannel, entityFieldName)
            encoder.encode(queryResult)
            encoder.flush()
            encoder.entities
          }
        EncodeResult(byteArrayOutputStream.toByteArray(), entities)
      }
  }
}
