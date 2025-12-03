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

import com.google.firebase.dataconnect.DataConnectPathSegment as PathComponent
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value

private typealias ProtoValueFoldCallback<R> =
  (foldedValue: R, path: ProtoValuePath, value: Value) -> R

fun <R> Struct.fold(initial: R, foldCallback: ProtoValueFoldCallback<R>): R =
  toValueProto().fold(initial, foldCallback)

fun <R> ListValue.fold(initial: R, foldCallback: ProtoValueFoldCallback<R>): R =
  toValueProto().fold(initial, foldCallback)

fun <R> Value.fold(initial: R, foldCallback: ProtoValueFoldCallback<R>): R =
  foldValue(this, initial, foldCallback)

private data class FoldValueQueueElement(val path: ProtoValuePath, val value: Value)

private fun MutableList<FoldValueQueueElement>.add(path: ProtoValuePath, value: Value) {
  add(FoldValueQueueElement(path.toList(), value))
}

private fun <R> foldValue(
  rootValue: Value,
  initial: R,
  foldCallback: ProtoValueFoldCallback<R>
): R {
  var foldedValue = initial
  val queue: MutableList<FoldValueQueueElement> = mutableListOf()
  queue.add(emptyList(), rootValue)

  while (queue.isNotEmpty()) {
    val (path, value) = queue.removeFirst()
    foldedValue = foldCallback(foldedValue, path, value)

    if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
      val childPath = path.toMutableList()
      childPath.add(PathComponent.ListIndex(-1))
      value.structValue.fieldsMap.entries.forEach { (key, value) ->
        childPath[childPath.lastIndex] = PathComponent.Field(key)
        queue.add(childPath, value)
      }
    } else if (value.kindCase == Value.KindCase.LIST_VALUE) {
      val childPath = path.toMutableList()
      childPath.add(PathComponent.ListIndex(-1))
      value.listValue.valuesList.forEachIndexed { index, value ->
        childPath[childPath.lastIndex] = PathComponent.ListIndex(index)
        queue.add(childPath, value)
      }
    }
  }

  return foldedValue
}
