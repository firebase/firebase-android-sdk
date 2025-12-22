/*
 * Copyright 2024 Google LLC
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
import com.google.protobuf.Struct
import com.google.protobuf.Value

fun Boolean.toValueProto(): Value = Value.newBuilder().setBoolValue(this).build()

fun String.toValueProto(): Value = Value.newBuilder().setStringValue(this).build()

fun Double.toValueProto(): Value = Value.newBuilder().setNumberValue(this).build()

fun Struct.toValueProto(): Value = Value.newBuilder().setStructValue(this).build()

fun ListValue.toValueProto(): Value = Value.newBuilder().setListValue(this).build()

fun Iterable<Value>.toValueProto(): Value = toListValue().toValueProto()

fun Iterable<Value>.toListValue(): ListValue = ListValue.newBuilder().addAllValues(this).build()

val Value.isNullValue: Boolean
  get() = kindCase == Value.KindCase.NULL_VALUE

val Value.isNumberValue: Boolean
  get() = kindCase == Value.KindCase.NUMBER_VALUE

val Value.isStringValue: Boolean
  get() = kindCase == Value.KindCase.STRING_VALUE

val Value.isBoolValue: Boolean
  get() = kindCase == Value.KindCase.BOOL_VALUE

val Value.isStructValue: Boolean
  get() = kindCase == Value.KindCase.STRUCT_VALUE

val Value.isListValue: Boolean
  get() = kindCase == Value.KindCase.LIST_VALUE

val Value.isKindNotSet: Boolean
  get() = kindCase == Value.KindCase.KIND_NOT_SET

val Value.numberValueOrNull: Double?
  get() = if (isNumberValue) numberValue else null

val Value.boolValueOrNull: Boolean?
  get() = if (isBoolValue) boolValue else null

val Value.stringValueOrNull: String?
  get() = if (isStringValue) stringValue else null

val Value.structValueOrNull: Struct?
  get() = if (isStructValue) structValue else null

val Value.listValueOrNull: ListValue?
  get() = if (isListValue) listValue else null

fun ListValue.isRecursivelyEmpty(): Boolean {
  val queue = ArrayDeque<ListValue>()
  queue.add(this)
  while (queue.isNotEmpty()) {
    val listValue = queue.removeFirst()
    repeat(listValue.valuesCount) { index ->
      val value = listValue.getValues(index)
      if (!value.isListValue) {
        return false
      }
      queue.add(value.listValue)
    }
  }
  return true
}

fun Value.isRecursivelyEmptyListValue(): Boolean = isListValue && listValue.isRecursivelyEmpty()
