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

import com.google.firebase.dataconnect.sqlite2.DataConnectCacheDatabase.QueryResult.Entity
import com.google.protobuf.Struct
import com.google.protobuf.Value
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

internal object QueryResultCodec {

  fun encode(
    data: Map<String, Any>,
    visitedEntities: MutableCollection<Entity>? = null
  ): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    DataOutputStream(byteArrayOutputStream).use { dataOutputStream ->
      dataOutputStream.writeInt(ENCODED_QUERY_DATA_INDICATOR)
      dataOutputStream.writeQueryResultData(data, visitedEntities)
    }
    return byteArrayOutputStream.toByteArray()
  }

  fun encode(struct: Struct): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    DataOutputStream(byteArrayOutputStream).use { dataOutputStream ->
      dataOutputStream.writeStruct(struct)
    }
    return byteArrayOutputStream.toByteArray()
  }

  private const val ENCODED_QUERY_DATA_INDICATOR: Int = 0x00060a4f
  private const val QUERY_RESULT_DATA_INDICATOR: Int = 0x1000daf7
  private const val QUERY_RESULT_DATA_END_INDICATOR: Int = 0x1004457d
  private const val VALUE_INDICATOR: Int = 0x20078e70
  private const val VALUE_END_INDICATOR: Int = 0x2009f646
  private const val ENTITY_INDICATOR: Int = 0x3007393a
  private const val NULL_INDICATOR: Int = 0x400ea79e
  private const val NUMBER_INDICATOR: Int = 0x41066ea2
  private const val STRING_INDICATOR: Int = 0x420ad2d2
  private const val BOOL_INDICATOR: Int = 0x43003fa6
  private const val STRUCT_INDICATOR: Int = 0x4403e48e
  private const val STRUCT_END_INDICATOR: Int = 0x4509c87c
  private const val LIST_INDICATOR: Int = 0x460012c7
  private const val LIST_END_INDICATOR: Int = 0x47002db8

  private fun DataOutputStream.writeQueryResultData(
    data: Map<*, *>,
    visitedEntities: MutableCollection<Entity>?
  ) {
    data.forEach { (key, value) ->
      checkNotNull(key) { "got null key, but expected String" }
      check(key is String) {
        "invalid key: $key (must be String, but got ${key::class.qualifiedName})"
      }
      writeUtf16String(key)
      when (value) {
        is Entity -> {
          writeInt(ENTITY_INDICATOR)
          visitedEntities?.add(value)
          writeEntity(value)
        }
        is Value -> {
          writeInt(VALUE_INDICATOR)
          writeValue(value)
          writeInt(VALUE_END_INDICATOR)
        }
        is Map<*, *> -> {
          writeInt(QUERY_RESULT_DATA_INDICATOR)
          writeQueryResultData(value, visitedEntities)
          writeInt(QUERY_RESULT_DATA_END_INDICATOR)
        }
        null -> throw IllegalArgumentException("unsupported value for key $key: null")
        else ->
          throw IllegalArgumentException(
            "unsupported value for key $key: $value" +
              " (got ${value::class.qualifiedName}, but expected ${Entity::class.qualifiedName}" +
              ", ${Value::class.qualifiedName}, or ${Map::class.qualifiedName})"
          )
      }
    }
  }

  private fun DataOutputStream.writeEntity(entity: Entity) {
    writeInt(entity.id.size)
    write(entity.id)
  }

  private fun DataOutputStream.writeStruct(struct: Struct) {
    writeInt(STRUCT_INDICATOR)
    struct.fieldsMap.forEach { structEntry ->
      writeUtf16String(structEntry.key)
      writeValue(structEntry.value)
    }
    writeInt(STRUCT_END_INDICATOR)
  }

  private fun DataOutputStream.writeValue(value: Value) {
    when (value.kindCase) {
      Value.KindCase.NULL_VALUE -> writeInt(NULL_INDICATOR)
      Value.KindCase.NUMBER_VALUE -> {
        writeInt(NUMBER_INDICATOR)
        writeDouble(value.numberValue)
      }
      Value.KindCase.STRING_VALUE -> {
        writeInt(STRING_INDICATOR)
        writeUtf16String(value.stringValue)
      }
      Value.KindCase.BOOL_VALUE -> {
        writeInt(BOOL_INDICATOR)
        writeBoolean(value.boolValue)
      }
      Value.KindCase.STRUCT_VALUE -> {
        writeStruct(value.structValue)
      }
      Value.KindCase.LIST_VALUE -> {
        writeInt(LIST_INDICATOR)
        value.listValue.valuesList.forEach { listEntry -> writeValue(listEntry) }
        writeInt(LIST_END_INDICATOR)
      }
      Value.KindCase.KIND_NOT_SET ->
        throw IllegalArgumentException("Value.KindCase.KIND_NOT_SET is not supported")
    }
  }

  private fun DataOutputStream.writeUtf16String(string: String) {
    writeInt(string.length)
    writeChars(string)
  }
}
