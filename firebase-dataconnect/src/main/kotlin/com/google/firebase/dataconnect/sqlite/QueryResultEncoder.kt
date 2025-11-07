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
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import java.nio.charset.CodingErrorAction

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

  private val writer = ProtoValueWriter(channel, utf8CharsetEncoder)

  class EncodeResult(val byteArray: ByteArray, val entities: List<Entity>)

  fun encode(queryResult: Struct) {
    writer.writeInt(QueryResultCodec.QUERY_RESULT_HEADER)
    writeStruct(queryResult)
  }

  fun flush() {
    writer.flush()
  }

  private fun writeString(string: String) {
    if (string.isEmpty()) {
      writer.writeByte(QueryResultCodec.VALUE_STRING_EMPTY)
      return
    }

    val utf8ByteCount = string.calculateUtf8ByteCount()
    val utf16ByteCount = string.length * 2
    utf8CharsetEncoder.reset() // Prepare for calling `canEncode()`.

    if (utf8ByteCount <= utf16ByteCount && utf8CharsetEncoder.canEncode(string)) {
      writer.writeByte(QueryResultCodec.VALUE_STRING_UTF8)
      writer.writeStringUtf8(string, utf8ByteCount)
    } else {
      writer.writeByte(QueryResultCodec.VALUE_STRING_UTF16)
      writer.writeStringCustomUtf16(string, utf16ByteCount)
    }
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

  private fun writeEntity(entityId: String, entity: Struct) {
    writer.writeByte(QueryResultCodec.VALUE_ENTITY)

    val encodedEntityId: ByteArray =
      ByteArrayOutputStream().use { byteArrayOutputStream ->
        Channels.newChannel(byteArrayOutputStream).use { channel ->
          writeString(entityId)
          byteArrayOf() // TODO!
        }
        byteArrayOutputStream.toByteArray()
      }

    writer.writeInt(entity.fieldsCount)
    entity.fieldsMap.keys.forEach { writeString(it) }
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
