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
import com.google.protobuf.ListValue
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
   * The [structsByPath] map specifies the [Struct] objects to graft in and the location at which to
   * graft them. If [structsByPath] is empty then the receiver [Struct] object is returned.
   *
   * The receiver [Struct] will be the "root" of the returned object, except in one case: if
   * [structsByPath] has the empty path as a key then the receiver [Struct] is ignored and the
   * "root" of the returned object will, instead, be the [Struct] associated with the empty path. If
   * the size of [structsByPath] is 1 and its only key is the empty path then the [Struct]
   * associated with the empty path is returned.
   *
   * Except for the empty path, every path in [structsByPath] must have a
   * [DataConnectPathSegment.Field] as its last element, otherwise a
   * [LastPathSegmentNotFieldException] will be thrown. A single-element path must have a
   * [DataConnectPathSegment.Field] as its only element, otherwise a
   * [LastPathSegmentNotFieldException] will be thrown. The element of a single-element path
   * specifies the key at which to graft the associated [Struct] into the "root". This key must not
   * already exist in the root, otherwise a [KeyExistsException] will be thrown.
   *
   * For all paths with size greater than 1, the "root" [Struct] will be traversed to find the
   * [Struct] corresponding with the penultimate element of the path. If the [Value] at this path is
   * not a [Struct] then [InsertIntoNonStructException] is thrown. Otherwise, the final element of
   * the path specifies the key at which to insert the [Struct] into the [Struct]. If this key
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

    if (structsByPath.size == 1 && structsByPath.containsKey(emptyDataConnectPath())) {
      return structsByPath.getValue(emptyDataConnectPath())
    }

    val mutableStructsByPath = structsByPath.toMutableMap()
    val rootStruct = mutableStructsByPath.remove(emptyDataConnectPath()) ?: this
    val rootNode = toMutableNode(rootStruct, parentPathSegment = null)

    val sortedPaths = mutableStructsByPath.keys.sortedWith(DataConnectPathComparator)

    for (path in sortedPaths) {
      val structToGraft = mutableStructsByPath.getValue(path)
      val parentPath = path.dropLast(1)

      val lastSegment = path.last()
      val lastSegmentField =
        when (lastSegment) {
          is DataConnectPathSegment.Field -> lastSegment.field
          is DataConnectPathSegment.ListIndex ->
            throw LastPathSegmentNotFieldException(
              "structsByPath contains path ${path.toPathString()} whose last segment " +
                "is list index ${lastSegment.index}, but the last segment " +
                "must be a field, not a list index [qxgass8cvx]"
            )
        }

      var currentNode: MutableNode = rootNode
      parentPath.forEachIndexed { pathSegmentIndex, pathSegment ->
        currentNode =
          when (pathSegment) {
            is DataConnectPathSegment.Field -> {
              val structNode =
                (currentNode as? MutableNode.StructNode)
                  ?: throw PathFieldOfNonStructException(
                    "structsByPath contains path ${path.toPathString()} " +
                      "whose segment ${pathSegmentIndex} " +
                      "(${currentNode.parentPathSegment.toFieldOrListIndexString()}) " +
                      "has kind case ${currentNode.toValue().kindCase}, " +
                      "but it is required to be ${Value.KindCase.STRUCT_VALUE} [s3mhtfj2mm]"
                  )
              structNode.fields.getOrPut(pathSegment.field) {
                MutableNode.StructNode(pathSegment, mutableMapOf())
              }
            }
            is DataConnectPathSegment.ListIndex -> {
              val listNode =
                (currentNode as? MutableNode.ListNode)
                  ?: throw PathListIndexOfNonListException(
                    "structsByPath contains path ${path.toPathString()} " +
                      "whose segment ${pathSegmentIndex} " +
                      "(${currentNode.parentPathSegment.toFieldOrListIndexString()}) " +
                      "has kind case ${currentNode.toValue().kindCase}, " +
                      "but it is required to be ${Value.KindCase.LIST_VALUE} [gr7mqk4jnn]"
                  )
              if (pathSegment.index < 0 || pathSegment.index >= listNode.values.size) {
                throw PathListIndexOutOfBoundsException(
                  "structsByPath contains path ${path.toPathString()} whose segment " +
                    "${pathSegmentIndex+1} (list index ${pathSegment.index}) is outside " +
                    "the half-open range [0..${listNode.values.size}) [rrk4t44n42]"
                )
              }
              listNode.values[pathSegment.index]
            }
          }
      }

      val parentStructNode =
        (currentNode as? MutableNode.StructNode)
          ?: throw InsertIntoNonStructException(
            "structsByPath contains path ${path.toPathString()} whose destination struct " +
              "(${parentPath.toPathString()}) has kind case ${currentNode.toValue().kindCase}, " +
              "but it is required to be ${Value.KindCase.STRUCT_VALUE} [zcj277ka6a]"
          )

      if (parentStructNode.fields.containsKey(lastSegmentField)) {
        throw KeyExistsException(
          "structsByPath contains path ${path.toPathString()} whose destination struct " +
            "(${parentPath.toPathString()}) already has a field named $lastSegmentField, " +
            "but it is required to not already have that key defined [ecgd5r2v4a]"
        )
      }

      parentStructNode.fields[lastSegmentField] = toMutableNode(structToGraft, lastSegment)
    }

    return rootNode.toStruct()
  }

  private sealed class MutableNode(val parentPathSegment: DataConnectPathSegment?) {
    abstract fun toValue(): Value

    class StructNode(
      parentPathSegment: DataConnectPathSegment?,
      val fields: MutableMap<String, MutableNode>
    ) : MutableNode(parentPathSegment) {
      fun toStruct(): Struct =
        Struct.newBuilder().let { structBuilder ->
          for ((key, value) in fields) {
            structBuilder.putFields(key, value.toValue())
          }
          structBuilder.build()
        }

      override fun toValue(): Value = toStruct().toValueProto()
    }

    class ListNode(
      parentPathSegment: DataConnectPathSegment?,
      val values: MutableList<MutableNode>
    ) : MutableNode(parentPathSegment) {
      fun toListValue(): ListValue =
        ListValue.newBuilder().let { listBuilder ->
          for (value in values) {
            listBuilder.addValues(value.toValue())
          }
          listBuilder.build()
        }

      override fun toValue(): Value = toListValue().toValueProto()
    }

    class ValueNode(parentPathSegment: DataConnectPathSegment?, val value: Value) :
      MutableNode(parentPathSegment) {
      override fun toValue(): Value = value
    }
  }

  private fun toMutableNode(value: Value, parentPathSegment: DataConnectPathSegment?): MutableNode =
    when (value.kindCase) {
      Value.KindCase.STRUCT_VALUE -> toMutableNode(value.structValue, parentPathSegment)
      Value.KindCase.LIST_VALUE -> toMutableNode(value.listValue, parentPathSegment)
      else -> MutableNode.ValueNode(parentPathSegment, value)
    }

  private fun toMutableNode(
    struct: Struct,
    parentPathSegment: DataConnectPathSegment?,
  ): MutableNode.StructNode =
    MutableNode.StructNode(
      parentPathSegment,
      struct.fieldsMap
        .mapValues { toMutableNode(it.value, DataConnectPathSegment.Field(it.key)) }
        .toMutableMap()
    )

  private fun toMutableNode(
    listValue: ListValue,
    parentPathSegment: DataConnectPathSegment?
  ): MutableNode.ListNode =
    MutableNode.ListNode(
      parentPathSegment,
      listValue.valuesList
        .mapIndexed { index, value ->
          toMutableNode(value, DataConnectPathSegment.ListIndex(index))
        }
        .toMutableList()
    )

  private fun DataConnectPathSegment?.toFieldOrListIndexString(): String =
    when (this) {
      null -> "[root object]"
      is DataConnectPathSegment.Field -> "field $field"
      is DataConnectPathSegment.ListIndex -> "list index $index"
    }

  sealed class ProtoGraftException(message: String) : Exception(message)

  class LastPathSegmentNotFieldException(message: String) : ProtoGraftException(message)

  class KeyExistsException(message: String) : ProtoGraftException(message)

  class InsertIntoNonStructException(message: String) : ProtoGraftException(message)

  class PathListIndexOutOfBoundsException(message: String) : ProtoGraftException(message)

  class PathListIndexOfNonListException(message: String) : ProtoGraftException(message)

  class PathFieldOfNonStructException(message: String) : ProtoGraftException(message)
}
