/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.dataconnect.testutil

import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import com.google.protobuf.Value.KindCase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Serializes a [Struct] into a [ByteArray] such that [deserializeStructVerbatim] can recreate the
 * original [Struct] verbatim without any artifacts of UTF-8 encoding or normal proto encoding.
 */
fun serializeStructVerbatim(struct: Struct): ByteArray {
  val byteArrayOutputStream = ByteArrayOutputStream()
  DataOutputStream(byteArrayOutputStream).use { dataOutputStream ->
    dataOutputStream.writeStruct(struct)
  }
  return byteArrayOutputStream.toByteArray()
}

/** Deserializes a [Struct] from a [ByteArray] created by [serializeStructVerbatim]. */
fun deserializeStructVerbatim(byteArray: ByteArray): Struct {
  val dataInputStream = DataInputStream(ByteArrayInputStream(byteArray))
  return dataInputStream.readStruct()
}

private fun DataOutputStream.writeStruct(struct: Struct) {
  writeInt(struct.fieldsCount)
  struct.fieldsMap.keys.forEach { key ->
    writeStructKey(key)
    writeValue(struct.getFieldsOrThrow(key))
  }
}

private fun DataInputStream.readStruct(): Struct {
  val fieldCount = readInt()
  val structBuilder = Struct.newBuilder()
  repeat(fieldCount) {
    val key = readStructKey()
    val value = readValue()
    structBuilder.putFields(key, value)
  }
  return structBuilder.build()
}

private fun DataOutputStream.writeStructKey(key: String) {
  writeInt(key.length)
  writeChars(key)
}

private fun DataInputStream.readStructKey(): String {
  val length = readInt()
  return buildString(length) { repeat(length) { append(readChar()) } }
}

private fun DataOutputStream.writeListValue(listValue: ListValue) {
  writeInt(listValue.valuesCount)
  listValue.valuesList.forEach { value -> writeValue(value) }
}

private fun DataInputStream.readListValue(): ListValue {
  val valueCount = readInt()
  val listValueBuilder = ListValue.newBuilder()
  repeat(valueCount) {
    val value = readValue()
    listValueBuilder.addValues(value)
  }
  return listValueBuilder.build()
}

private fun DataOutputStream.writeKindCase(kindCase: KindCase) {
  writeInt(
    when (kindCase) {
      KindCase.NULL_VALUE -> 0
      KindCase.NUMBER_VALUE -> 1
      KindCase.STRING_VALUE -> 2
      KindCase.BOOL_VALUE -> 3
      KindCase.STRUCT_VALUE -> 4
      KindCase.LIST_VALUE -> 5
      KindCase.KIND_NOT_SET -> 6
    }
  )
}

private fun DataInputStream.readKindCase(): KindCase =
  when (val int = readInt()) {
    0 -> KindCase.NULL_VALUE
    1 -> KindCase.NUMBER_VALUE
    2 -> KindCase.STRING_VALUE
    3 -> KindCase.BOOL_VALUE
    4 -> KindCase.STRUCT_VALUE
    5 -> KindCase.LIST_VALUE
    6 -> KindCase.KIND_NOT_SET
    else -> error("invalid KindCase int: $int [hxnjyc752z]")
  }

private fun DataOutputStream.writeValue(value: Value) {
  writeKindCase(value.kindCase)
  when (value.kindCase) {
    KindCase.KIND_NOT_SET,
    KindCase.NULL_VALUE -> {}
    KindCase.NUMBER_VALUE -> writeDouble(value.numberValue)
    KindCase.STRING_VALUE -> writeStructKey(value.stringValue)
    KindCase.BOOL_VALUE -> writeBoolean(value.boolValue)
    KindCase.STRUCT_VALUE -> writeStruct(value.structValue)
    KindCase.LIST_VALUE -> writeListValue(value.listValue)
  }
}

private fun DataInputStream.readValue(): Value {
  val valueBuilder = Value.newBuilder()
  when (readKindCase()) {
    KindCase.KIND_NOT_SET -> {}
    KindCase.NULL_VALUE -> valueBuilder.setNullValue(NullValue.NULL_VALUE)
    KindCase.NUMBER_VALUE -> valueBuilder.setNumberValue(readDouble())
    KindCase.STRING_VALUE -> valueBuilder.setStringValue(readStructKey())
    KindCase.BOOL_VALUE -> valueBuilder.setBoolValue(readBoolean())
    KindCase.STRUCT_VALUE -> valueBuilder.setStructValue(readStruct())
    KindCase.LIST_VALUE -> valueBuilder.setListValue(readListValue())
  }
  return valueBuilder.build()
}
