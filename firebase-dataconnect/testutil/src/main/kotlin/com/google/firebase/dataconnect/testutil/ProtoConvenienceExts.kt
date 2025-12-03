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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

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

fun ProtoValuePath.withAppendedListIndex(index: Int): ProtoValuePath =
  withAppendedComponent(ListElementProtoValuePathComponent(index))

fun ProtoValuePath.withAppendedStructKey(key: String): ProtoValuePath =
  withAppendedComponent(StructKeyProtoValuePathComponent(key))

fun ProtoValuePath.withAppendedComponent(component: ProtoValuePathComponent): ProtoValuePath {
  val mutablePath = toMutableList()
  mutablePath.add(component)
  return mutablePath.toList()
}

@OptIn(ExperimentalContracts::class)
fun ProtoValuePathComponent?.isStructKey(): Boolean {
  contract { returns(true) implies (this@isStructKey is StructKeyProtoValuePathComponent) }
  return this is StructKeyProtoValuePathComponent
}

@OptIn(ExperimentalContracts::class)
fun ProtoValuePathComponent?.isListElement(): Boolean {
  contract { returns(true) implies (this@isListElement is ListElementProtoValuePathComponent) }
  return this is ListElementProtoValuePathComponent
}
