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
import java.io.DataInput
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import kotlin.math.absoluteValue

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultDecoder(
  private val dataInput: DataInput,
  private val entities: List<Entity>
) {

  private val charsetDecoder =
    Charsets.UTF_8.newDecoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT)

  private val byteArray = ByteArray(2048)
  private val byteBuffer = ByteBuffer.wrap(byteArray)

  fun decode(): Struct = buildStructProto {
    val keyCount = dataInput.readStructKeyCount()
    repeat(keyCount) {
      val key = dataInput.readString()
      when (dataInput.readKindCase()) {
        ValueKindCase.Null -> putNull(key)
        ValueKindCase.Number -> put(key, dataInput.readDouble())
        ValueKindCase.BoolTrue -> put(key, true)
        ValueKindCase.BoolFalse -> put(key, false)
        ValueKindCase.StringEmpty -> put(key, dataInput.readString(StringType.Empty))
        ValueKindCase.StringUtf8 -> put(key, dataInput.readString(StringType.Utf8))
        ValueKindCase.StringUtf16 -> put(key, dataInput.readString(StringType.Utf16))
      }
    }
  }

  private fun DataInput.readString(): String = readString(readStringType())

  private fun DataInput.readString(stringType: StringType): String =
    when (stringType) {
      StringType.Empty -> ""
      StringType.Utf8 -> readStringUtf8(charsetDecoder, byteBuffer)
      StringType.Utf16 -> readStringCustomUtf16(byteArray)
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
        DataInputStream(byteArrayInputStream).use { dataInputStream ->
          val decoder = QueryResultDecoder(dataInputStream, entities)
          decoder.decode()
        }
      }

    private fun DataInput.readStructKeyCount(): Int =
      readInt().also {
        if (it < 0) {
          throw NegativeStructKeyCountException(
            "read struct key count $it, but expected " +
              "a number greater than or equal to zero [y9253xj96g]"
          )
        }
      }

    private fun DataInput.readStringByteCount(): Int =
      readInt().also {
        if (it < 0) {
          throw NegativeStringByteCountException(
            "read string byte count $it, but expected " +
              "a number greater than or equal to zero [a9kma55y7m]"
          )
        }
      }

    private fun DataInput.readStringCharCount(): Int =
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

    private fun DataInput.readKindCase(): ValueKindCase =
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

    private fun DataInput.readFully(byteBuffer: ByteBuffer) {
      val array = byteBuffer.array()
      readFully(array, byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining())
      byteBuffer.position(byteBuffer.limit())
    }

    private fun DataInput.readStringType(): StringType =
      readKindCase().let { valueKindCase ->
        val stringType = StringType.fromValueKindCase(valueKindCase)
        if (stringType === null) {
          throw UnknownStringTypeException(
            "read non-string value type ${valueKindCase.serializedByte} " +
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

    private fun DataInput.readStringUtf8(
      charsetDecoder: CharsetDecoder,
      byteBuffer: ByteBuffer
    ): String {
      val byteCount = readStringByteCount()
      val charCount = readStringCharCount()

      charsetDecoder.reset()
      byteBuffer.clear()
      val charArray = CharArray(charCount)
      val charBuffer = CharBuffer.wrap(charArray)

      var bytesRemaining = byteCount
      while (true) {
        val curReadCount = bytesRemaining.coerceAtMost(byteBuffer.remaining())
        byteBuffer.limit(byteBuffer.position() + curReadCount)
        readFully(byteBuffer)
        bytesRemaining -= curReadCount

        byteBuffer.flip()
        val codingResult = charsetDecoder.decode(byteBuffer, charBuffer, false)
        if (!codingResult.isUnderflow) {
          codingResult.throwException()
        }

        if (bytesRemaining == 0) {
          break
        }

        byteBuffer.compact()
      }

      val finalDecodeResult = charsetDecoder.decode(byteBuffer, charBuffer, true)
      if (!finalDecodeResult.isUnderflow || byteBuffer.remaining() > 0) {
        finalDecodeResult.throwException()
      }

      val flushResult = charsetDecoder.flush(charBuffer)
      if (!flushResult.isUnderflow || charBuffer.remaining() > 0) {
        flushResult.throwException()
      }

      return String(charArray)
    }

    private fun DataInput.readStringCustomUtf16(byteArray: ByteArray): String {
      val charCount = readStringCharCount()
      val charArray = CharArray(charCount)

      var bytesRemaining = charCount * 2
      var i = 0
      var charArrayIndex = 0
      while (bytesRemaining > 0) {
        val byteReadCount = bytesRemaining.coerceAtMost(byteArray.size - i)
        readFully(byteArray, i, byteReadCount)
        bytesRemaining -= byteReadCount

        while (i + 1 < byteReadCount) {
          val b1 = byteArray[i++]
          val b2 = byteArray[i++]
          val charCode = ((b1.toInt() and 0xFF) shl 8) or (b2.toInt() and 0xFF)
          charArray[charArrayIndex++] = charCode.toChar()
        }

        if (i == byteReadCount) {
          i = 0
        } else {
          check(i + 1 == byteReadCount) {
            "internal error h2pzy6wefr: i=$i byteReadCount=$byteReadCount; " +
              "i+1 should equal byteReadCount, but they differ by " +
              "${(byteReadCount-i-1).absoluteValue}"
          }
          byteArray[0] = byteArray[i]
          i = 1
        }
      }

      check(charArrayIndex == charArray.size) {
        "internal error pfdwdh929b: charArrayIndex=$charArrayIndex, " +
          "charArray.size=${charArray.size}; charArrayIndex should equal charArray.size, " +
          "but they differ by ${(charArray.size-charArrayIndex).absoluteValue}"
      }

      return String(charArray)
    }
  }
}
