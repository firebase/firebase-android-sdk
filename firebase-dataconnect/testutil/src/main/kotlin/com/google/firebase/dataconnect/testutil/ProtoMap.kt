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
import com.google.protobuf.Struct
import com.google.protobuf.Value

fun Struct.map(callback: (path: DataConnectPath, value: Value) -> Value?): Struct {
  val mappedValue = toValueProto().map(callback)
  checkNotNull(mappedValue) {
    "callback returned null for root, " +
      "but must be a non-null ${Value.KindCase.STRUCT_VALUE} [qhkdn2b8z5]"
  }
  check(mappedValue.isStructValue) {
    "callback returned ${mappedValue.kindCase} for root, " +
      "but must be a non-null ${Value.KindCase.STRUCT_VALUE} [tmhxthgwyk]"
  }
  return mappedValue.structValue
}

fun ListValue.map(callback: (path: DataConnectPath, value: Value) -> Value?): ListValue {
  val mappedValue = toValueProto().map(callback)
  checkNotNull(mappedValue) {
    "callback returned null for root, " +
      "but must be a non-null ${Value.KindCase.LIST_VALUE} [hdm7p67g54]"
  }
  check(mappedValue.isListValue) {
    "callback returned ${mappedValue.kindCase} for root, " +
      "but must be a non-null ${Value.KindCase.LIST_VALUE} [nhfe2stftq]"
  }
  return mappedValue.listValue
}

fun <V : Value?> Value.map(
  callback: (path: DataConnectPath, value: Value) -> V,
): V =
  mapRecursive(
    value = this,
    path = mutableListOf(),
    callback = callback,
  )

private fun <V : Value?> mapRecursive(
  value: Value,
  path: MutableDataConnectPath,
  callback: (path: DataConnectPath, value: Value) -> V,
): V {
  val processedValue: Value =
    if (value.isStructValue) {
      Struct.newBuilder().let { structBuilder ->
        value.structValue.fieldsMap.entries.forEach { (key, childValue) ->
          val mappedChildValue =
            path.withAddedField(key) { mapRecursive(childValue, path, callback) }
          if (mappedChildValue !== null) {
            structBuilder.putFields(key, mappedChildValue)
          }
        }
        structBuilder.build().toValueProto()
      }
    } else if (value.isListValue) {
      ListValue.newBuilder().let { listValueBuilder ->
        value.listValue.valuesList.forEachIndexed { index, childValue ->
          val mappedChildValue =
            path.withAddedListIndex(index) { mapRecursive(childValue, path, callback) }
          if (mappedChildValue !== null) {
            listValueBuilder.addValues(mappedChildValue)
          }
        }
        listValueBuilder.build().toValueProto()
      }
    } else {
      value
    }

  return callback(path.toList(), processedValue)
}
