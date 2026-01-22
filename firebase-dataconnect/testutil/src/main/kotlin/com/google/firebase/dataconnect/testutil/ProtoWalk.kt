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

fun Struct.walk(includeSelf: Boolean = false): Sequence<DataConnectPathValuePair> =
  toValueProto().walk(includeSelf = includeSelf)

fun ListValue.walk(includeSelf: Boolean = false): Sequence<DataConnectPathValuePair> =
  toValueProto().walk(includeSelf = includeSelf)

fun Value.walk(includeSelf: Boolean = true): Sequence<DataConnectPathValuePair> =
  valueWalk(this, includeSelf = includeSelf)

fun Struct.walkValues(includeSelf: Boolean = false): Sequence<Value> =
  walk(includeSelf = includeSelf).map { it.value }

fun ListValue.walkValues(includeSelf: Boolean = false): Sequence<Value> =
  walk(includeSelf = includeSelf).map { it.value }

fun Value.walkValues(includeSelf: Boolean = true): Sequence<Value> =
  walk(includeSelf = includeSelf).map { it.value }

private fun valueWalk(value: Value, includeSelf: Boolean) = sequence {
  val rootProtoValuePathPair = DataConnectPathValuePair(emptyList(), value)
  val queue = ArrayDeque<DataConnectPathValuePair>()
  queue.add(rootProtoValuePathPair)

  while (queue.isNotEmpty()) {
    val protoValuePathPair = queue.removeFirst()
    val (path, value) = protoValuePathPair

    if (includeSelf || protoValuePathPair !== rootProtoValuePathPair) {
      yield(protoValuePathPair)
    }

    if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
      value.structValue.fieldsMap.entries.forEach { (key, childValue) ->
        queue.add(DataConnectPathValuePair(path.withAddedField(key), childValue))
      }
    } else if (value.kindCase == Value.KindCase.LIST_VALUE) {
      value.listValue.valuesList.forEachIndexed { index, childValue ->
        queue.add(DataConnectPathValuePair(path.withAddedListIndex(index), childValue))
      }
    }
  }
}
