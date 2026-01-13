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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathComparator
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.emptyDataConnectPath
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.protobuf.Struct
import com.google.protobuf.Value

/**
 * Holder for "global" functions for grafting values into Proto Struct objects.
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * ProtoStructEncoderKt, ProtoUtilKt, etc. Java class with public visibility, which pollutes the
 * public API. Using an "internal" object, instead, to gather together the top-level functions
 * avoids this public API pollution.
 */
internal object ProtoGraft {

  /**
   * Creates and returns a [Struct] that is the receiver [Struct] with the given [Struct] objects
   * grafted in.
   *
   * The [structsByPath] map specifies the [Struct] objects to graft in and the location at which
   * to graft them. If [structsByPath] is empty then the receiver [Struct] object is returned.
   *
   * The receiver [Struct] will be the "root" of the returned object, except in one case:
   * if [structsByPath] has the empty path as a key then the receiver [Struct] is ignored and the
   * "root" of the returned object will, instead, be the [Struct] associated with the empty path.
   * If the size of [structsByPath] is 1 and its only key is the empty path then the [Struct]
   * associated with the empty path is returned.
   *
   * Except for the empty path, every path in [structsByPath] must have a
   * [DataConnectPathSegment.Field] as its first and last element. If violated, a
   * [FirstPathSegmentNotFieldException] or [LastPathSegmentNotFieldException] will be thrown,
   * respectively. A single-element path must have a [DataConnectPathSegment.Field] as its only
   * element, otherwise a [LastPathSegmentNotFieldException]. The element of a single-element path
   * specifies the key at which to graft the associated [Struct] into the "root". This key must not
   * already exist in the root, otherwise a [KeyExistsException] will be thrown.
   *
   * For all paths with size greater than 1, the "root" [Struct] will be traversed to find the
   * [Struct] corresponding with the penultimate element of the path. If the [Value] at this path
   * is not a [Struct] then [InsertIntoNonStructException] is thrown. Otherwise, the final element
   * of the path specifies the key at which to insert the [Struct] into the [Struct]. If this key
   * already exists then [KeyExistsException] is thrown.
   *
   * Any elements in the path that are missing along the path from the "root" to the penultimate
   * elements in the path will be inserted as empty [Struct] objects.
   *
   * The returned [Struct] will, therefore, consist of the "root" [Struct] (either the receiver or
   * the [Struct] associated with the empty path in [structsByPath]) with the [Struct] objects
   * specified as values in the given [structsByPath] inserted at the path corresponding to their
   * keys in [structsByPath].
   */
  fun Struct.withGraftedInStructs(structsByPath: Map<DataConnectPath, Struct>): Struct {
    if (structsByPath.isEmpty()) {
      return this
    }

    val mutableStructsByPath = structsByPath.toMutableMap()
    val rootStruct = mutableStructsByPath.remove(emptyDataConnectPath()) ?: this
    val rootNode = toMutableNode(Value.newBuilder().setStructValue(rootStruct).build())

    val sortedPaths = mutableStructsByPath.keys.sortedWith(DataConnectPathComparator)

    for (path in sortedPaths) {
      val structToGraft = mutableStructsByPath.getValue(path)
      val parentPath = path.dropLast(1)
      val finalSegment = path.last()

      if (finalSegment !is DataConnectPathSegment.Field) {
        throw LastPathSegmentNotFieldException(
          "qxgass8cvx: The last path segment is list index ${
          (finalSegment as DataConnectPathSegment.ListIndex).index
          }. Must have a field as the last path segment."
        )
      }

      var currentNode: MutableNode = rootNode
      for (segment in parentPath) {
        currentNode =
          when (segment) {
            is DataConnectPathSegment.Field -> {
              val structNode =
                (currentNode as? MutableNode.StructNode)
                  ?: throw InsertIntoNonStructException(
                    "Attempting to traverse into a non-struct with field '${segment.field}'"
                  )
              structNode.fields.getOrPut(segment.field) {
                MutableNode.StructNode(mutableMapOf())
              }
            }
            is DataConnectPathSegment.ListIndex -> {
              val listNode =
                (currentNode as? MutableNode.ListNode)
                  ?: throw GraftingIntoNonStructInListException(
                    "Attempting to traverse into a non-list with index '${segment.index}'"
                  )
              if (segment.index < 0 || segment.index >= listNode.values.size) {
                throw IndexOutOfBoundsException(
                  "Index ${segment.index} is out of bounds for list of size ${listNode.values.size}"
                )
              }
              listNode.values[segment.index]
            }
          }
      }

      val parentStructNode =
        (currentNode as? MutableNode.StructNode)
          ?: throw InsertIntoNonStructException("Final destination is not a struct.")

      if (parentStructNode.fields.containsKey(finalSegment.field)) {
        throw KeyExistsException(
          "z77ec2cznn: structsByPath contains path=${
          finalSegment.field
          } which already exists."
        )
      }
      parentStructNode.fields[finalSegment.field] =
        toMutableNode(Value.newBuilder().setStructValue(structToGraft).build())
    }

    return (rootNode.toValue().structValue
      ?: throw IllegalStateException("Final result is not a struct"))
  }

  private sealed class MutableNode {
    abstract fun toValue(): Value

    data class StructNode(val fields: MutableMap<String, MutableNode>) : MutableNode() {
      override fun toValue(): Value {
        val structBuilder = Struct.newBuilder()
        for ((key, value) in fields) {
          structBuilder.putFields(key, value.toValue())
        }
        return Value.newBuilder().setStructValue(structBuilder).build()
      }
    }

    data class ListNode(val values: MutableList<MutableNode>) : MutableNode() {
      override fun toValue(): Value {
        val listBuilder = com.google.protobuf.ListValue.newBuilder()
        for (value in values) {
          listBuilder.addValues(value.toValue())
        }
        return Value.newBuilder().setListValue(listBuilder).build()
      }
    }

    data class ValueNode(val value: Value) : MutableNode() {
      override fun toValue(): Value = value
    }
  }

  private fun toMutableNode(value: Value): MutableNode {
    return when {
      value.hasStructValue() ->
        MutableNode.StructNode(
          value.structValue.fieldsMap
            .mapValues { toMutableNode(it.value) }
            .toMutableMap()
        )
      value.hasListValue() ->
        MutableNode.ListNode(value.listValue.valuesList.map { toMutableNode(it) }.toMutableList())
      else -> MutableNode.ValueNode(value)
    }
  }


  sealed class ProtoGraftException(message: String) : Exception(message)

  class LastPathSegmentNotFieldException(message: String) : ProtoGraftException(message)

  class FirstPathSegmentNotFieldException(message: String) : ProtoGraftException(message)

  class KeyExistsException(message: String) : ProtoGraftException(message)

  class InsertIntoNonStructException(message: String) : ProtoGraftException(message)

  class GraftingIntoNonStructInListException(message: String) : ProtoGraftException(message)
}
