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
import com.google.firebase.dataconnect.util.StringUtil.calculateUtf8ByteCount
import com.google.protobuf.Struct
import com.google.protobuf.Value
import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetEncoder
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction
import kotlin.math.absoluteValue

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultEncoder(private val dataOutput: DataOutput) {

  val entities: MutableList<Entity> = mutableListOf()

  private val charsetEncoder =
    Charsets.UTF_8.newEncoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT)

  private val byteArray = ByteArray(2048)
  private val byteBuffer = ByteBuffer.wrap(byteArray)

  class EncodeResult(val byteArray: ByteArray, val entities: List<Entity>)

  fun encode(queryResult: Struct) {
    val map = queryResult.fieldsMap
    dataOutput.writeInt(map.size)
    map.entries.forEach { (key, value) ->
      dataOutput.writeString(key)
      when (value.kindCase) {
        Value.KindCase.NULL_VALUE -> dataOutput.writeByte(QueryResultCodec.VALUE_NULL)
        Value.KindCase.NUMBER_VALUE -> {
          dataOutput.writeByte(QueryResultCodec.VALUE_NUMBER)
          dataOutput.writeDouble(value.numberValue)
        }
        Value.KindCase.BOOL_VALUE ->
          dataOutput.writeByte(
            if (value.boolValue) QueryResultCodec.VALUE_BOOL_TRUE
            else QueryResultCodec.VALUE_BOOL_FALSE
          )
        Value.KindCase.STRING_VALUE -> {
          dataOutput.writeString(value.stringValue)
        }
        Value.KindCase.STRUCT_VALUE -> TODO()
        Value.KindCase.LIST_VALUE -> TODO()
        Value.KindCase.KIND_NOT_SET -> TODO()
      }
    }
  }

  private fun DataOutput.writeString(string: String) {
    if (string.isEmpty()) {
      writeByte(QueryResultCodec.VALUE_STRING_EMPTY)
      return
    }

    val utf8ByteCount = string.calculateUtf8ByteCount()
    val utf16ByteCount = string.length * 2
    charsetEncoder.reset() // Prepare `charsetEncoder` for calling `canEncode()`.

    if (utf8ByteCount <= utf16ByteCount && charsetEncoder.canEncode(string)) {
      writeStringUtf8(string, utf8ByteCount, charsetEncoder, byteBuffer)
    } else {
      writeStringCustomUtf16(string, utf16ByteCount, byteArray)
    }
  }

  companion object {

    fun encode(queryResult: Struct): EncodeResult =
      ByteArrayOutputStream().use { byteArrayOutputStream ->
        val entities =
          DataOutputStream(byteArrayOutputStream).use { dataOutputStream ->
            val encoder = QueryResultEncoder(dataOutputStream)
            encoder.encode(queryResult)
            encoder.entities
          }
        EncodeResult(byteArrayOutputStream.toByteArray(), entities)
      }

    private fun DataOutput.writeByte(byte: Byte) {
      writeByte(byte.toInt())
    }

    private fun DataOutput.writeByteBuffer(byteBuffer: ByteBuffer): Int {
      val byteArray = byteBuffer.array()
      val position = byteBuffer.position()
      val offset = byteBuffer.arrayOffset() + position
      val length = byteBuffer.remaining()

      write(byteArray, offset, length)

      byteBuffer.position(position + length)
      return length
    }

    private fun DataOutput.writeStringUtf8(
      string: String,
      expectedByteCount: Int,
      charsetEncoder: CharsetEncoder,
      byteBuffer: ByteBuffer
    ) {
      charsetEncoder.reset()
      byteBuffer.clear()
      val charBuffer = CharBuffer.wrap(string)

      writeByte(QueryResultCodec.VALUE_STRING_UTF8)
      writeInt(expectedByteCount)
      writeInt(string.length)

      var byteWriteCount = 0
      while (true) {
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

        if (coderResult2.isUnderflow) {
          break
        }
        if (coderResult2.isOverflow) {
          byteBuffer.flip()
          byteWriteCount += writeByteBuffer(byteBuffer)
          byteBuffer.clear()
        } else {
          coderResult2.throwException()
        }
      }

      byteBuffer.flip()
      if (byteBuffer.hasRemaining()) {
        byteWriteCount += writeByteBuffer(byteBuffer)
      }

      check(byteWriteCount == expectedByteCount) {
        "internal error rvmdh67npk: byteWriteCount=$byteWriteCount " +
          "should be equal to expectedByteCount=$expectedByteCount, but they differ by " +
          "${(expectedByteCount-byteWriteCount).absoluteValue}"
      }
    }

    private fun DataOutput.writeStringCustomUtf16(
      string: String,
      expectedByteCount: Int,
      buffer: ByteArray
    ) {
      writeByte(QueryResultCodec.VALUE_STRING_UTF16)
      writeInt(string.length)

      var i = 0
      var byteWriteCount = 0
      string.forEach { char ->
        buffer[i++] = ((char.code ushr 8) and 0xFF).toByte()
        buffer[i++] = ((char.code ushr 0) and 0xFF).toByte()

        if (i + 2 >= buffer.size) {
          write(buffer, 0, i)
          byteWriteCount += i
          i = 0
        }
      }

      if (i > 0) {
        write(buffer, 0, i)
        byteWriteCount += i
      }

      check(byteWriteCount == expectedByteCount) {
        "internal error agdf5qbwwp: byteWriteCount=$byteWriteCount " +
          "should be equal to expectedByteCount=$expectedByteCount, but they differ by " +
          "${(expectedByteCount - byteWriteCount).absoluteValue}"
      }
    }
  }
}
