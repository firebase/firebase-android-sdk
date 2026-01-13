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
    val rootNode = toMutableNode(rootStruct)

    val sortedPaths = mutableStructsByPath.keys.sortedWith(DataConnectPathComparator)

    for (path in sortedPaths) {
      val structToGraft = mutableStructsByPath.getValue(path)
      val parentPath = path.dropLast(1)

      val insertField =
        when (val finalSegment = path.last()) {
          is DataConnectPathSegment.Field -> finalSegment.field
          is DataConnectPathSegment.ListIndex ->
            throw LastPathSegmentNotFieldException(
              "structsByPath contains path ${path.toPathString()} whose final segment " +
                "is list index ${finalSegment.index}, but the final segment " +
                "must be a field specifying the field to insert into the leaf struct, " +
                "not a list index [qxgass8cvx]"
            )
        }

      var currentNode: MutableNode = rootNode
      parentPath.forEachIndexed { pathSegmentIndex, pathSegment ->
        currentNode =
          when (pathSegment) {
            is DataConnectPathSegment.Field -> {
              val structNode =
                (currentNode as? MutableNode.StructNode)
                  ?: throw InsertIntoNonStructException(
                    "structsByPath contains path ${path.toPathString()} whose segment " +
                      "${pathSegmentIndex+1} (field ${pathSegment.field}) is kind " +
                      "${currentNode.toValue().kindCase}, but required it to be " +
                      "${Value.KindCase.STRUCT_VALUE} [s3mhtfj2mm]"
                  )
              structNode.fields.getOrPut(pathSegment.field) {
                MutableNode.StructNode(mutableMapOf())
              }
            }
            is DataConnectPathSegment.ListIndex -> {
              val listNode =
                (currentNode as? MutableNode.ListNode)
                  ?: throw GraftingIntoNonStructInListException(
                    "structsByPath contains path ${path.toPathString()} whose segment " +
                      "${pathSegmentIndex+1} (list index ${pathSegment.index}) is kind " +
                      "${currentNode.toValue().kindCase}, but required it to be " +
                      "${Value.KindCase.LIST_VALUE} [gr7mqk4jnn]"
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
              "${parentPath.toPathString()}) has kind case ${currentNode.toValue().kindCase}, " +
              "but it is required to be ${Value.KindCase.STRUCT_VALUE} [zcj277ka6a]"
          )

      if (parentStructNode.fields.containsKey(insertField)) {
        throw KeyExistsException(
          "structsByPath contains path ${path.toPathString()} whose destination struct " +
            "${parentPath.toPathString()}) already has a field named $insertField, " +
            "but it is required to not already have that key defined [ecgd5r2v4a]"
        )
      }

      parentStructNode.fields[insertField] = toMutableNode(structToGraft)
    }

    return rootNode.toStruct()
  }

  private sealed interface MutableNode {
    fun toValue(): Value

    class StructNode(val fields: MutableMap<String, MutableNode>) : MutableNode {

      fun toStruct(): Struct =
        Struct.newBuilder().let { structBuilder ->
          for ((key, value) in fields) {
            structBuilder.putFields(key, value.toValue())
          }
          structBuilder.build()
        }

      override fun toValue(): Value = toStruct().toValueProto()
    }

    class ListNode(val values: MutableList<MutableNode>) : MutableNode {

      fun toListValue(): ListValue =
        ListValue.newBuilder().let { listBuilder ->
          for (value in values) {
            listBuilder.addValues(value.toValue())
          }
          listBuilder.build()
        }

      override fun toValue(): Value = toListValue().toValueProto()
    }

    class ValueNode(val value: Value) : MutableNode {
      override fun toValue(): Value = value
    }
  }

  private fun toMutableNode(value: Value): MutableNode =
    when (value.kindCase) {
      Value.KindCase.STRUCT_VALUE -> toMutableNode(value.structValue)
      Value.KindCase.LIST_VALUE -> toMutableNode(value.listValue)
      else -> MutableNode.ValueNode(value)
    }

  private fun toMutableNode(struct: Struct): MutableNode.StructNode =
    MutableNode.StructNode(struct.fieldsMap.mapValues { toMutableNode(it.value) }.toMutableMap())

  private fun toMutableNode(listValue: ListValue): MutableNode.ListNode =
    MutableNode.ListNode(listValue.valuesList.map { toMutableNode(it) }.toMutableList())

  sealed class ProtoGraftException(message: String) : Exception(message)

  class LastPathSegmentNotFieldException(message: String) : ProtoGraftException(message)

  class KeyExistsException(message: String) : ProtoGraftException(message)

  class InsertIntoNonStructException(message: String) : ProtoGraftException(message)

  class GraftingIntoNonStructInListException(message: String) : ProtoGraftException(message)

  class PathListIndexOutOfBoundsException(message: String) : ProtoGraftException(message)
}
