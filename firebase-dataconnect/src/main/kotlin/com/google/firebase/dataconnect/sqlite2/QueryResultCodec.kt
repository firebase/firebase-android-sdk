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

import com.google.firebase.dataconnect.util.StringUtil.calculateUtf8ByteCount
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetEncoder
import java.nio.charset.CodingErrorAction

internal object QueryResultCodec {

  class Entity(
    val id: ByteArray,
    val data: Struct,
  )

  class EncodeResult(val data: ByteArray, val entities: List<Entity>) {}

  fun encode(data: Struct): EncodeResult {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val entities = mutableListOf<Entity>()
    DataOutputStream(byteArrayOutputStream).use { dataOutputStream ->
      Encoder(dataOutputStream, entities).writeQueryResultData(data)
    }
    return EncodeResult(byteArrayOutputStream.toByteArray(), entities)
  }

  const val QUERY_RESULT_HEADER: Int = 0x2e4286dc
  const val VALUE_NULL: Byte = 1
  const val VALUE_NUMBER: Byte = 2
  const val VALUE_BOOL_TRUE: Byte = 3
  const val VALUE_BOOL_FALSE: Byte = 4
  const val VALUE_STRING_EMPTY: Byte = 5
  const val VALUE_STRING_UTF8: Byte = 6
  const val VALUE_STRING_UTF16: Byte = 7
  const val VALUE_STRUCT: Byte = 8
  const val VALUE_LIST: Byte = 9
  const val VALUE_KIND_NOT_SET: Byte = 10
  const val VALUE_ENTITY: Byte = 11

  private class Encoder(dataOutput: DataOutput, private val entities: MutableList<Entity>) :
    DataOutput by dataOutput {

    fun writeQueryResultData(data: Struct) {
      writeInt(QUERY_RESULT_HEADER)
      writeStruct(data)
    }

    private fun writeValue(value: Value) {
      when (value.kindCase) {
        Value.KindCase.KIND_NOT_SET -> writeByte(VALUE_KIND_NOT_SET.toInt())
        Value.KindCase.NULL_VALUE -> writeByte(VALUE_NULL.toInt())
        Value.KindCase.BOOL_VALUE ->
          if (value.boolValue) {
            writeByte(VALUE_BOOL_TRUE.toInt())
          } else {
            writeByte(VALUE_BOOL_FALSE.toInt())
          }
        Value.KindCase.NUMBER_VALUE -> {
          writeByte(VALUE_NUMBER.toInt())
          writeDouble(value.numberValue)
        }
        Value.KindCase.STRING_VALUE -> {
          writeSizeOptimizedString(value.stringValue)
        }
        Value.KindCase.LIST_VALUE -> {
          writeByte(VALUE_LIST.toInt())
          writeList(value.listValue)
        }
        Value.KindCase.STRUCT_VALUE -> {
          writeStruct(value.structValue)
        }
      }
    }

    private sealed class StringEncodingInfo(val byteCount: Int) {
      class Utf8(byteCount: Int) : StringEncodingInfo(byteCount)
      class Utf16(byteCount: Int) : StringEncodingInfo(byteCount)
    }

    /**
     * Examines this string to determine which encoding technique will result in the fewest number
     * of bytes in the encoding. Since database access performance is largely determined by I/O,
     * using the encoding technique that results in the smallest byte size will generally lead to
     * improved overall performance.
     */
    private fun writeSizeOptimizedString(string: String): ByteArray {
      val utf8Encoder = threadLocalUtf8Encoder.get()!!
      utf8Encoder.reset()

      fun String.calculateUtf16ByteCount(): Int = length * 2

      val encodingInfo: StringEncodingInfo =
        if (!utf8Encoder.canEncode(string)) {
          StringEncodingInfo.Utf16(string.calculateUtf16ByteCount())
        } else {
          val utf8EncodingByteCount = string.calculateUtf8ByteCount()
          val utf16EncodingByteCount = string.calculateUtf16ByteCount()
          if (utf8EncodingByteCount <= utf16EncodingByteCount) {
            StringEncodingInfo.Utf8(utf8EncodingByteCount)
          } else {
            StringEncodingInfo.Utf16(utf16EncodingByteCount)
          }
        }

      val byteArray = ByteArray(encodingInfo.byteCount + 1)
      when (encodingInfo) {
        is StringEncodingInfo.Utf8 -> {
          val byteBuffer = ByteBuffer.wrap(byteArray)
          byteBuffer.put(VALUE_STRING_UTF8)
          utf8Encoder.reset()
          utf8Encoder.encode(CharBuffer.wrap(string), byteBuffer, true)
        }
        is StringEncodingInfo.Utf16 -> {
          byteArray[0] = VALUE_STRING_UTF16
          var i = 1
          string.forEach { char ->
            byteArray[i++] = ((char.code ushr 8) and 0xFF).toByte()
            byteArray[i++] = ((char.code ushr 0) and 0xFF).toByte()
          }
        }
      }

      write(byteArray)
      return byteArray
    }

    private val threadLocalUtf8Encoder =
      object : ThreadLocal<CharsetEncoder>() {
        override fun initialValue(): CharsetEncoder =
          Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
      }

    private fun writeStruct(struct: Struct) {
      val map: Map<String, Value> = struct.fieldsMap

      val entityId: String? =
        map["_id"]?.let {
          if (it.kindCase == Value.KindCase.STRING_VALUE) {
            it.stringValue
          } else {
            null
          }
        }

      if (entityId !== null) {
        writeByte(VALUE_ENTITY.toInt())
        writeSizeOptimizedString(entityId)
      }

      writeInt(map.size)
      map.entries.forEach { (key, value) ->
        writeSizeOptimizedString(key)
        writeValue(value)
      }
    }

    private fun writeList(list: ListValue) {}
  }
}
