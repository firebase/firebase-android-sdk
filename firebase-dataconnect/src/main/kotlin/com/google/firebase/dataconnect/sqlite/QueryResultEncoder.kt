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
import java.nio.charset.CharsetEncoder
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction
import kotlin.math.absoluteValue

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultEncoder(
  channel: WritableByteChannel,
  private val entityFieldName: String? = null
) {

  val entities: MutableList<Entity> = mutableListOf()

  private val utf8CharsetEncoder =
    Charsets.UTF_8.newEncoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT)

  private val writer = QueryResultChannelWriter(channel, utf8CharsetEncoder)

  class EncodeResult(val byteArray: ByteArray, val entities: List<Entity>)

  fun encode(queryResult: Struct) {
    writer.writeInt(QueryResultCodec.QUERY_RESULT_HEADER)
    writeStruct(queryResult)
  }

  fun flush() {
    writer.flush()
  }

  private fun writeString(string: String) {
    val (encoding, encodedByteCount) = calculateMinEncodingSizeFor(string, utf8CharsetEncoder)
    when (encoding) {
      StringEncoding.Empty -> writer.writeByte(QueryResultCodec.VALUE_STRING_EMPTY)
      StringEncoding.Utf8 -> {
        writer.writeByte(QueryResultCodec.VALUE_STRING_UTF8)
        writer.writeStringUtf8(string, encodedByteCount)
      }
      StringEncoding.Utf16 -> {
        writer.writeByte(QueryResultCodec.VALUE_STRING_UTF16)
        writer.writeStringCustomUtf16(string, encodedByteCount)
      }
    }
  }

  private fun encodeString(string: String): ByteBuffer {
    val (encoding, encodedByteCount) = calculateMinEncodingSizeFor(string, utf8CharsetEncoder)
    val encodedString =
      when (encoding) {
        StringEncoding.Empty -> {
          val byteArray = ByteArray(1)
          byteArray[0] = QueryResultCodec.VALUE_STRING_EMPTY
          ByteBuffer.wrap(byteArray)
        }
        StringEncoding.Utf8 -> {
          val byteBuffer = ByteBuffer.allocate(encodedByteCount + 1 + 8)
          byteBuffer.put(QueryResultCodec.VALUE_STRING_UTF8)
          QueryResultChannelWriter.writeStringUtf8(
            string,
            encodedByteCount,
            utf8CharsetEncoder,
            byteBuffer
          ) {
            throw IllegalStateException(
              "internal error jvab6jke6t: flushOnce() should never be called from writeStringUtf8()"
            )
          }
          byteBuffer.flip()
          byteBuffer
        }
        StringEncoding.Utf16 -> {
          val byteBuffer = ByteBuffer.allocate(encodedByteCount + 1 + 4)
          byteBuffer.put(QueryResultCodec.VALUE_STRING_UTF16)
          QueryResultChannelWriter.writeStringCustomUtf16(string, encodedByteCount, byteBuffer) {
            throw IllegalStateException(
              "internal error ea3ecqg496: flushOnce() should never be called from writeStringCustomUtf16()"
            )
          }
          byteBuffer.flip()
          byteBuffer
        }
      }

    check(encodedString.arrayOffset() == 0) {
      "internal error nz44se7xrk: encodedString.arrayOffset() returned " +
        "${encodedString.arrayOffset()}, but expected 0"
    }
    check(encodedString.position() == 0) {
      "internal error kevwyy4ftg: encodedString.position() returned " +
        "${encodedString.position()}, but expected 0"
    }
    check(encodedString.limit() == encodedString.capacity()) {
      "internal error jxcdvvpjs2: encodedString.limit() returned ${encodedString.limit()}, " +
        "but expected ${encodedString.capacity()} " +
        "(the value returned from encodedString.capacity())"
    }

    return encodedString
  }

  private fun writeList(listValue: ListValue) {
    writer.writeByte(QueryResultCodec.VALUE_LIST)
    writer.writeInt(listValue.valuesCount)
    repeat(listValue.valuesCount) { writeValue(listValue.getValues(it)) }
  }

  private fun writeStruct(struct: Struct) {
    val map = struct.fieldsMap

    val entityId =
      if (entityFieldName === null) {
        null
      } else {
        val entityIdValue = map[entityFieldName]
        if (entityIdValue?.kindCase != Value.KindCase.STRING_VALUE) {
          null
        } else {
          entityIdValue.stringValue
        }
      }

    if (entityId !== null) {
      writeEntity(entityId, struct)
      return
    }

    writer.writeByte(QueryResultCodec.VALUE_STRUCT)
    writer.writeInt(map.size)
    map.entries.forEach { (key, value) ->
      writeString(key)
      writeValue(value)
    }
  }

  private fun writeValue(value: Value) {
    when (value.kindCase) {
      Value.KindCase.NULL_VALUE -> writer.writeByte(QueryResultCodec.VALUE_NULL)
      Value.KindCase.NUMBER_VALUE -> {
        writer.writeByte(QueryResultCodec.VALUE_NUMBER)
        writer.writeDouble(value.numberValue)
      }
      Value.KindCase.BOOL_VALUE ->
        writer.writeByte(
          if (value.boolValue) QueryResultCodec.VALUE_BOOL_TRUE
          else QueryResultCodec.VALUE_BOOL_FALSE
        )
      Value.KindCase.STRING_VALUE -> writeString(value.stringValue)
      Value.KindCase.STRUCT_VALUE -> writeStruct(value.structValue)
      Value.KindCase.LIST_VALUE -> writeList(value.listValue)
      Value.KindCase.KIND_NOT_SET -> writer.writeByte(QueryResultCodec.VALUE_KIND_NOT_SET)
    }
  }

  private fun writeEntityId(entityId: String): ByteArray {
    val encodedEntityId = encodeString(entityId)
    writer.write(encodedEntityId)
    return encodedEntityId.array()
  }

  private fun writeEntity(entityId: String, entity: Struct) {
    writer.writeByte(QueryResultCodec.VALUE_ENTITY)
    val encodedEntityId = writeEntityId(entityId)
    entities.add(Entity(encodedEntityId, entity))
  }

  private enum class StringEncoding {
    Empty,
    Utf8,
    Utf16,
  }

  private data class CalculateMinEncodingSizeResult(
    val encoding: StringEncoding,
    val encodedByteCount: Int
  )

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

    private fun calculateMinEncodingSizeFor(
      string: String,
      utf8CharsetEncoder: CharsetEncoder
    ): CalculateMinEncodingSizeResult {
      if (string.isEmpty()) {
        return CalculateMinEncodingSizeResult(StringEncoding.Empty, 0)
      }

      val utf8ByteCount = string.calculateUtf8ByteCount()
      val utf16ByteCount = string.length * 2
      utf8CharsetEncoder.reset() // Prepare for calling `canEncode()`.

      return if (utf8ByteCount <= utf16ByteCount && utf8CharsetEncoder.canEncode(string)) {
        CalculateMinEncodingSizeResult(StringEncoding.Utf8, utf8ByteCount)
      } else {
        CalculateMinEncodingSizeResult(StringEncoding.Utf16, utf16ByteCount)
      }
    }
  }
}

private class QueryResultChannelWriter(
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

  fun write(value: ByteBuffer) {
    while (true) {
      if (value.remaining() <= byteBuffer.remaining()) {
        byteBuffer.put(value)
        break
      }

      if (byteBuffer.position() == 0) {
        val byteWriteCount = channel.write(byteBuffer)
        check(byteWriteCount > 0) {
          "internal error jtp329cezy: no bytes written " +
            "when ${byteBuffer.remaining()} byte were available"
        }
      } else {
        val oldLimit = value.limit()
        value.limit(value.position() + byteBuffer.remaining())
        byteBuffer.put(value)
        value.limit(oldLimit)

        flushOnce()
      }
    }
  }

  fun writeStringUtf8(string: String, expectedByteCount: Int) {
    writeStringUtf8(string, expectedByteCount, utf8CharsetEncoder, byteBuffer, ::flushOnce)
  }

  fun writeStringCustomUtf16(string: String, expectedByteCount: Int) {
    writeStringCustomUtf16(string, expectedByteCount, byteBuffer, ::flushOnce)
  }

  private fun flushOnce() {
    byteBuffer.flip()
    val byteWriteCount = channel.write(byteBuffer)
    byteBuffer.compact()
    check(byteWriteCount > 0) {
      "internal error gmsx2fhfrx: no bytes written " +
        "when ${byteBuffer.remaining()} byte were available"
    }
  }

  private fun ensureRemaining(minRemaining: Int) =
    ensureRemaining(minRemaining, byteBuffer, ::flushOnce)

  companion object {

    inline fun ensureRemaining(minRemaining: Int, byteBuffer: ByteBuffer, flushOnce: () -> Unit) {
      require(minRemaining <= byteBuffer.capacity()) {
        "minRemaining=$minRemaining must be less than or equal to " +
          "byteBuffer.capacity()=${byteBuffer.capacity()}"
      }

      while (byteBuffer.remaining() < minRemaining) {
        val remainingBefore = byteBuffer.remaining()
        flushOnce()
        val remainingAfter = byteBuffer.remaining()
        check(remainingAfter > remainingBefore) {
          "internal error jtjszvtb4z: need $minRemaining bytes of available buffer space, " +
            "but could only get $remainingAfter"
        }
      }
    }

    inline fun writeStringUtf8(
      string: String,
      expectedByteCount: Int,
      utf8CharsetEncoder: CharsetEncoder,
      byteBuffer: ByteBuffer,
      flushOnce: () -> Unit,
    ) {
      utf8CharsetEncoder.reset()
      val charBuffer = CharBuffer.wrap(string)

      ensureRemaining(8, byteBuffer, flushOnce)
      byteBuffer.putInt(expectedByteCount)
      byteBuffer.putInt(string.length)

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

    inline fun writeStringCustomUtf16(
      string: String,
      expectedByteCount: Int,
      byteBuffer: ByteBuffer,
      flushOnce: () -> Unit,
    ) {
      ensureRemaining(4, byteBuffer, flushOnce)
      byteBuffer.putInt(string.length)

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
  }
}
