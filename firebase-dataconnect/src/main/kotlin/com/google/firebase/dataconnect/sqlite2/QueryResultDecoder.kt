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
        ValueKindCase.String -> put(key, dataInput.readString())
      }
    }
  }

  private fun DataInput.readString(): String =
    when (readStringType()) {
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
      String(QueryResultCodec.VALUE_STRING_UTF8, "utf8");

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

    private enum class StringType(val serializedByte: Byte, val displayName: String) {
      Empty(QueryResultCodec.VALUE_STRING_EMPTY, "empty"),
      Utf8(QueryResultCodec.VALUE_STRING_UTF8, "utf8"),
      Utf16(QueryResultCodec.VALUE_STRING_UTF16, "utf16");

      companion object {
        fun fromSerializedByte(serializedByte: Byte): StringType? =
          entries.firstOrNull { it.serializedByte == serializedByte }
      }
    }

    private fun DataInput.readStringType(): StringType =
      readByte().let { byte ->
        val stringType = StringType.fromSerializedByte(byte)
        if (stringType === null) {
          throw UnknownStringTypeException(
            "read unknown string type byte $byte, but expected one of " +
              StringType.entries
                .sortedBy { it.serializedByte }
                .joinToString { "${it.serializedByte} (${it.displayName})" } +
              " [hfvxx849cv]"
          )
        }
        stringType
      }

    private fun DataInput.readStringUtf8(
      charsetDecoder: CharsetDecoder,
      byteBuffer: ByteBuffer
    ): String {
      // Assuming an array offset of 0 just makes the logic below simpler because we don't have to
      // calculate the offset from which to access the underlying byte array.
      require(byteBuffer.arrayOffset() == 0) {
        "internal error zv3dagabjp: byteBuffer.arrayOffset() should be zero, " +
          "but got ${byteBuffer.arrayOffset()}"
      }

      val byteCount = readStringByteCount()
      val charCount = readStringCharCount()

      charsetDecoder.reset()
      val charArray = CharArray(charCount)
      val charBuffer = CharBuffer.wrap(charArray)
      val byteArray = byteBuffer.array()

      var bytesRemaining = byteCount
      while (bytesRemaining > 0) {
        val curReadCount = bytesRemaining.coerceAtMost(byteArray.size)
        if (curReadCount == 0) {
          break
        }
        byteBuffer.clear()
        byteBuffer.limit(curReadCount)
        readFully(byteArray, 0, curReadCount)
        bytesRemaining -= curReadCount

        val codingResult = charsetDecoder.decode(byteBuffer, charBuffer, false)
        if (!codingResult.isUnderflow) {
          codingResult.throwException()
        }
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
      TODO()
    }
  }
}
