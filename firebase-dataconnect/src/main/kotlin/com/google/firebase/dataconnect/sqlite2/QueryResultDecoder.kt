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

package com.google.firebase.dataconnect.sqlite2

import com.google.firebase.dataconnect.sqlite2.QueryResultCodec.Entity
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.protobuf.Struct
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.charset.CodingErrorAction

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultDecoder(
  private val channel: ReadableByteChannel,
  private val entities: List<Entity>
) {

  private val charsetDecoder =
    Charsets.UTF_8.newDecoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT)

  private val byteBuffer: ByteBuffer =
    ByteBuffer.allocate(2048).apply {
      order(ByteOrder.BIG_ENDIAN)
      flip()
    }

  fun decode(): Struct = buildStructProto {
    val keyCount = readStructKeyCount()
    repeat(keyCount) {
      val key = readString()
      when (readKindCase()) {
        ValueKindCase.Null -> putNull(key)
        ValueKindCase.Number -> put(key, readDouble())
        ValueKindCase.BoolTrue -> put(key, true)
        ValueKindCase.BoolFalse -> put(key, false)
        ValueKindCase.StringEmpty -> put(key, readString(StringType.Empty))
        ValueKindCase.StringUtf8 -> put(key, readString(StringType.Utf8))
        ValueKindCase.StringUtf16 -> put(key, readString(StringType.Utf16))
      }
    }
  }

  private fun readSome() {
    byteBuffer.compact()
    channel.read(byteBuffer)
    byteBuffer.flip()
  }

  private fun ensureRemaining(byteCount: Int) {
    val originalRemaining = byteBuffer.remaining()
    while (byteBuffer.remaining() < byteCount) {
      val remainingBefore = byteBuffer.remaining()
      readSome()
      val remainingAfter = byteBuffer.remaining()
      if (remainingBefore == remainingAfter) {
        throw EOFException(
          "unexpected EOF: expected at least $byteCount bytes, but only got ${remainingAfter-originalRemaining}"
        )
      }
    }
  }

  private fun readByte(): Byte {
    ensureRemaining(1)
    return byteBuffer.get()
  }

  private fun readInt(): Int {
    ensureRemaining(4)
    return byteBuffer.getInt()
  }

  private fun readDouble(): Double {
    ensureRemaining(8)
    return byteBuffer.getDouble()
  }

  private fun readString(): String = readString(readStringType())

  private fun readString(stringType: StringType): String =
    when (stringType) {
      StringType.Empty -> ""
      StringType.Utf8 -> readStringUtf8()
      StringType.Utf16 -> readStringCustomUtf16()
    }

  private fun readStructKeyCount(): Int =
    readInt().also {
      if (it < 0) {
        throw NegativeStructKeyCountException(
          "read struct key count $it, but expected " +
            "a number greater than or equal to zero [y9253xj96g]"
        )
      }
    }

  private fun readStringByteCount(): Int =
    readInt().also {
      if (it < 0) {
        throw NegativeStringByteCountException(
          "read string byte count $it, but expected " +
            "a number greater than or equal to zero [a9kma55y7m]"
        )
      }
    }

  private fun readStringCharCount(): Int =
    readInt().also {
      if (it < 0) {
        throw NegativeStringCharCountException(
          "read string char count $it, but expected " +
            "a number greater than or equal to zero [gwybfam237]"
        )
      }
    }

  private enum class ValueKindCase(val serializedByte: Byte, val displayName: String) {
    Null(QueryResultCodec.VALUE_NULL, "null"),
    Number(QueryResultCodec.VALUE_NUMBER, "number"),
    BoolTrue(QueryResultCodec.VALUE_BOOL_TRUE, "true"),
    BoolFalse(QueryResultCodec.VALUE_BOOL_FALSE, "false"),
    StringEmpty(QueryResultCodec.VALUE_STRING_EMPTY, "emptystring"),
    StringUtf8(QueryResultCodec.VALUE_STRING_UTF8, "utf8"),
    StringUtf16(QueryResultCodec.VALUE_STRING_UTF16, "utf16");

    companion object {
      fun fromSerializedByte(serializedByte: Byte): ValueKindCase? =
        entries.firstOrNull { it.serializedByte == serializedByte }
    }
  }

  private fun readKindCase(): ValueKindCase =
    readByte().let { byte ->
      val kindCase = ValueKindCase.fromSerializedByte(byte)
      if (kindCase === null) {
        throw UnknownKindCaseByteException(
          "read unknown kind case byte $byte, but expected one of " +
            ValueKindCase.entries
              .sortedBy { it.serializedByte }
              .joinToString { "${it.serializedByte} (${it.displayName})" } +
            " [pmkb3sc2mn]"
        )
      }
      kindCase
    }

  private enum class StringType(val valueKindCase: ValueKindCase) {
    Empty(ValueKindCase.StringEmpty),
    Utf8(ValueKindCase.StringUtf8),
    Utf16(ValueKindCase.StringUtf16);

    companion object {
      fun fromValueKindCase(valueKindCase: ValueKindCase): StringType? =
        entries.firstOrNull { it.valueKindCase == valueKindCase }
    }
  }

  private fun readStringType(): StringType =
    readKindCase().let { valueKindCase ->
      val stringType = StringType.fromValueKindCase(valueKindCase)
      if (stringType === null) {
        throw UnknownStringTypeException(
          "read non-string kind case byte ${valueKindCase.serializedByte} " +
            "(${valueKindCase.displayName}), but expected one of " +
            StringType.entries
              .sortedBy { it.valueKindCase.serializedByte }
              .joinToString {
                "${it.valueKindCase.serializedByte} (${it.valueKindCase.displayName})"
              } +
            " [hfvxx849cv]"
        )
      }
      stringType
    }

  private fun readStringUtf8(): String {
    val byteCount = readStringByteCount()
    val charCount = readStringCharCount()

    charsetDecoder.reset()
    val charBuffer = CharBuffer.allocate(charCount)

    var bytesRemaining = byteCount
    while (true) {
      if (byteBuffer.remaining() >= bytesRemaining) {
        val oldLimit = byteBuffer.limit()
        byteBuffer.limit(byteBuffer.position() + bytesRemaining)
        val decodeResult = charsetDecoder.decode(byteBuffer, charBuffer, true)
        byteBuffer.limit(oldLimit)
        if (!decodeResult.isUnderflow) {
          decodeResult.throwException()
        }
        break
      }

      val byteBufferPositionBefore = byteBuffer.position()
      val decodeResult = charsetDecoder.decode(byteBuffer, charBuffer, false)
      val byteBufferPositionAfter = byteBuffer.position()
      val curByteCount = byteBufferPositionAfter - byteBufferPositionBefore
      bytesRemaining -= curByteCount

      if (!decodeResult.isUnderflow || curByteCount == 0) {
        decodeResult.throwException()
      }

      if (byteBuffer.remaining() < bytesRemaining) {
        readSome()
      }
    }

    val flushResult = charsetDecoder.flush(charBuffer)
    if (!flushResult.isUnderflow || charBuffer.remaining() > 0) {
      flushResult.throwException()
    }

    charBuffer.clear()
    return charBuffer.toString()
  }

  private fun readStringCustomUtf16(): String {
    val charCount = readStringCharCount()
    val charBuffer = CharBuffer.allocate(charCount)

    while (charBuffer.remaining() > 0) {
      if (byteBuffer.remaining() < 2) {
        readSome()
      }
      if (byteBuffer.remaining() == 0) {
        throw EOFException(
          "expected to read ${charCount*2} bytes, but only got ${byteBuffer.remaining() + (charBuffer.position()*2)}"
        )
      }

      val charBufferView = byteBuffer.asCharBuffer()
      if (charBufferView.remaining() > charBuffer.remaining()) {
        charBufferView.limit(charBuffer.remaining())
      }

      charBuffer.put(charBufferView)
      byteBuffer.position(byteBuffer.position() + (charBufferView.position() * 2))
    }

    charBuffer.clear()
    return charBuffer.toString()
  }

  sealed class DecodeException(message: String) : Exception(message)

  class NegativeStructKeyCountException(message: String) : DecodeException(message)

  class NegativeStringByteCountException(message: String) : DecodeException(message)

  class NegativeStringCharCountException(message: String) : DecodeException(message)

  class UnknownKindCaseByteException(message: String) : DecodeException(message)

  class UnknownStringTypeException(message: String) : DecodeException(message)

  companion object {

    fun decode(byteArray: ByteArray, entities: List<Entity>): Struct =
      ByteArrayInputStream(byteArray).use { byteArrayInputStream ->
        Channels.newChannel(byteArrayInputStream).use { channel ->
          val decoder = QueryResultDecoder(channel, entities)
          decoder.decode()
        }
      }
  }
}
