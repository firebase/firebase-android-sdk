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

val Value.isStructValue: Boolean
  get() = kindCase == Value.KindCase.STRUCT_VALUE

val Value.isListValue: Boolean
  get() = kindCase == Value.KindCase.LIST_VALUE
