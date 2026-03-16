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

package com.google.firebase.dataconnect.testutil

import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value

fun Struct.deepCopy(): Struct =
  Struct.newBuilder()
    .also { builder ->
      fieldsMap.entries.forEach { (key, value) -> builder.putFields(key, value.deepCopy()) }
    }
    .build()

fun ListValue.deepCopy(): ListValue =
  ListValue.newBuilder()
    .also { builder -> valuesList.forEach { builder.addValues(it.deepCopy()) } }
    .build()

fun Value.deepCopy(): Value =
  Value.newBuilder().let { builder ->
    when (kindCase) {
      Value.KindCase.KIND_NOT_SET -> {}
      Value.KindCase.NULL_VALUE -> builder.setNullValue(NullValue.NULL_VALUE)
      Value.KindCase.NUMBER_VALUE -> builder.setNumberValue(numberValue)
      Value.KindCase.STRING_VALUE -> builder.setStringValue(stringValue)
      Value.KindCase.BOOL_VALUE -> builder.setBoolValue(boolValue)
      Value.KindCase.STRUCT_VALUE -> builder.setStructValue(structValue.deepCopy())
      Value.KindCase.LIST_VALUE -> builder.setListValue(listValue.deepCopy())
    }
    builder.build()
  }
