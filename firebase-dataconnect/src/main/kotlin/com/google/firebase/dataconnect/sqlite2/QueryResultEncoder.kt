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
import com.google.protobuf.Struct
import com.google.protobuf.Value
import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultEncoder(private val dataOutput: DataOutput) {

  val entities: MutableList<Entity> = mutableListOf()

  private val charsetEncoder = Charsets.UTF_8.newEncoder()

  class EncodeResult(val byteArray: ByteArray, val entities: List<Entity>)

  fun encode(queryResult: Struct) {
    val map = queryResult.fieldsMap
    dataOutput.writeInt(map.size)
    map.entries.forEach { (key, value) ->
      dataOutput.writeString(key)
      when (value.kindCase) {
        Value.KindCase.NUMBER_VALUE -> {
          dataOutput.writeByte(QueryResultCodec.VALUE_NUMBER)
          dataOutput.writeDouble(value.numberValue)
        }
        Value.KindCase.BOOL_VALUE -> {
          dataOutput.writeByte(QueryResultCodec.VALUE_BOOL)
          dataOutput.writeBoolean(value.boolValue)
        }
        Value.KindCase.NULL_VALUE -> TODO()
        Value.KindCase.STRING_VALUE -> TODO()
        Value.KindCase.STRUCT_VALUE -> TODO()
        Value.KindCase.LIST_VALUE -> TODO()
        Value.KindCase.KIND_NOT_SET -> TODO()
      }
    }
  }

  private fun DataOutput.writeString(string: String) {
    charsetEncoder.reset()
    val encodedString: ByteBuffer = charsetEncoder.encode(CharBuffer.wrap(string))

    writeInt(encodedString.remaining())
    writeByteBuffer(encodedString)
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

    private fun DataOutput.writeByteBuffer(byteBuffer: ByteBuffer) {
      val byteArray = byteBuffer.array()
      val offset = byteBuffer.arrayOffset() + byteBuffer.position()
      val length = byteBuffer.remaining()
      write(byteArray, offset, length)
    }
  }
}
