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
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

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
   * Creates and returns a [Struct] that is the receiver [Struct] with the given [Value] objects
   * grafted in.
   *
   * The [valueByPath] map specifies the [Value] objects to graft in and the location at which to
   * graft them. If [valueByPath] is empty then the receiver [Struct] object is returned.
   *
   * Every path in [valueByPath] traverses a tree of [Struct]s and [ListValue]s, and must have a
   * [DataConnectPathSegment.Field] as its last element (otherwise a
   * [LastPathSegmentNotFieldException] will be thrown). Using the paths, the "root" [Struct] will
   * be traversed to find the [Struct] identified by the penultimate element of the path. If the
   * [Value] at this path is not a [Struct] then [InsertIntoNonStructException] is thrown.
   * Otherwise, the final element of the path specifies the key at which to insert the [Value] into
   * the [Struct]. If this key already exists then [KeyExistsException] is thrown.
   *
   * Any [DataConnectPathSegment.Field] elements that are missing along the path from the receiver
   * to the penultimate element in the path will be inserted as empty [Struct] objects.
   * [DataConnectPathSegment.ListIndex] elements in the path must refer to an existing element in
   * the list.
   */
  fun Struct.withGraftedInValues(valueByPath: Map<DataConnectPath, Value>): Struct {
    if (valueByPath.isEmpty()) {
      return this
    }

    require(emptyDataConnectPath() !in valueByPath) {
      "valueByPath contains the empty path, but the empty path is not allowed [af5k4an5za]"
    }

    val mutableValueByPath = valueByPath.toMutableMap()
    val rootNode = toMutableNode(this, parentPathSegment = null)

    graftInValues(rootNode, mutableValueByPath)

    return rootNode.toStruct()
  }

  /**
   * Creates and returns a [ListValue] that is the receiver [ListValue] with the given [Value]
   * objects grafted in.
   *
   * The [valueByPath] map specifies the [Value] objects to graft in and the location at which to
   * graft them. If [valueByPath] is empty then the receiver [ListValue] object is returned.
   *
   * Every path in [valueByPath] traverses a tree of [Struct]s and [ListValue]s, and must have a
   * [DataConnectPathSegment.Field] as its last element (otherwise a
   * [LastPathSegmentNotFieldException] will be thrown). Using the paths, the "root" [ListValue]
   * will be traversed to find the [Struct] identified by the penultimate element of the path. If
   * the [Value] at this path is not a [Struct] then [InsertIntoNonStructException] is thrown.
   * Otherwise, the final element of the path specifies the key at which to insert the [Value] into
   * the [Struct]. If this key already exists then [KeyExistsException] is thrown.
   *
   * Any [DataConnectPathSegment.Field] elements that are missing along the path from the receiver
   * to the penultimate element in the path will be inserted as empty [Struct] objects.
   * [DataConnectPathSegment.ListIndex] elements in the path must refer to an existing element in
   * the list.
   */
  fun ListValue.withGraftedInValues(valueByPath: Map<DataConnectPath, Value>): ListValue {
    if (valueByPath.isEmpty()) {
      return this
    }

    require(emptyDataConnectPath() !in valueByPath) {
      "valueByPath contains the empty path, but the empty path is not allowed [rm45kyhtff]"
    }

    val rootNode = toMutableNode(this, parentPathSegment = null)

    graftInValues(rootNode, valueByPath)

    return rootNode.toListValue()
  }

  private fun graftInValues(rootNode: MutableNode, valueByPath: Map<DataConnectPath, Value>) {
    val sortedPaths = valueByPath.keys.sortedWith(DataConnectPathComparator)

    for (path in sortedPaths) {
      val valueToGraft = valueByPath.getValue(path)
      val parentPath = path.dropLast(1)

      val lastSegment = path.last()
      val lastSegmentField =
        when (lastSegment) {
          is DataConnectPathSegment.Field -> lastSegment.field
          is DataConnectPathSegment.ListIndex ->
            throw LastPathSegmentNotFieldException(
              "valueByPath contains path ${path.toPathString()} " +
                "whose last segment is list index ${lastSegment.index}, " +
                "but the last segment must be a field, not a list index [qxgass8cvx]"
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
                    "valueByPath contains path ${path.toPathString()} " +
                      "whose segment $pathSegmentIndex " +
                      "(${currentNode.parentPathSegment.toFieldOrListIndexString()}) " +
                      "has kind ${currentNode.toValue().kindCase}, " +
                      "but it is expected to have kind ${Value.KindCase.STRUCT_VALUE} [s3mhtfj2mm]"
                  )
              structNode.getField(pathSegment.field)
            }
            is DataConnectPathSegment.ListIndex -> {
              val listNode =
                (currentNode as? MutableNode.ListNode)
                  ?: throw PathListIndexOfNonListException(
                    "valueByPath contains path ${path.toPathString()} " +
                      "whose segment $pathSegmentIndex " +
                      "(${currentNode.parentPathSegment.toFieldOrListIndexString()}) " +
                      "has kind ${currentNode.toValue().kindCase}, " +
                      "but it is expected to have kind ${Value.KindCase.LIST_VALUE} [gr7mqk4jnn]"
                  )
              if (pathSegment.index < 0) {
                throw NegativePathListIndexException(
                  "valueByPath contains path ${path.toPathString()} " +
                    "whose segment ${pathSegmentIndex+1} (list index ${pathSegment.index}) " +
                    "is negative, but list indices must be greater than or equal to zero " +
                    "and strictly less than the size of the referent list, " +
                    "that is, between 0 (inclusive) and ${listNode.size} (exclusive) [rrk4t44n42]"
                )
              } else if (pathSegment.index >= listNode.size) {
                throw PathListIndexGreaterThanOrEqualToListSizeException(
                  "valueByPath contains path ${path.toPathString()} " +
                    "whose segment ${pathSegmentIndex+1} (list index ${pathSegment.index}) " +
                    "is greater than or equal to the size of the list, " +
                    "but list indices must be greater than or equal to zero " +
                    "and strictly less than the size of the referent list, " +
                    "that is, between 0 (inclusive) and ${listNode.size} (exclusive) [pdfqm8kb54]"
                )
              }
              listNode.getValue(pathSegment.index)
            }
          }
      }

      val parentStructNode =
        (currentNode as? MutableNode.StructNode)
          ?: throw InsertIntoNonStructException(
            "valueByPath contains path ${path.toPathString()} " +
              "whose destination struct (${parentPath.toPathString()}) " +
              "has kind ${currentNode.toValue().kindCase}, " +
              "but it is expected to have kind ${Value.KindCase.STRUCT_VALUE} [zcj277ka6a]"
          )

      if (parentStructNode.containsKey(lastSegmentField)) {
        throw KeyExistsException(
          "valueByPath contains path ${path.toPathString()} " +
            "whose destination struct (${parentPath.toPathString()}) " +
            "already has a field named $lastSegmentField, " +
            "but it is required to not already have that field [ecgd5r2v4a]"
        )
      }

      parentStructNode.setField(lastSegmentField, toMutableNode(valueToGraft, lastSegment))
    }
  }

  private sealed class MutableNode(val parentPathSegment: DataConnectPathSegment?) {
    abstract val kind: Value.KindCase
    abstract fun toValue(): Value

    class StructNode(
      parentPathSegment: DataConnectPathSegment?,
      private val struct: Struct,
    ) : MutableNode(parentPathSegment) {

      private val lazyMutatedFields =
        lazy(LazyThreadSafetyMode.NONE) { mutableMapOf<String, MutableNode>() }

      fun getField(key: String): MutableNode {
        if (lazyMutatedFields.isInitialized()) {
          lazyMutatedFields.value[key]?.let {
            return it
          }
        }

        val childParentPathSegment = DataConnectPathSegment.Field(key)
        val newChildMutableNode =
          if (struct.containsFields(key)) {
            val value = struct.getFieldsOrThrow(key)
            toMutableNode(value, childParentPathSegment)
          } else {
            StructNode(childParentPathSegment, Struct.getDefaultInstance())
          }

        lazyMutatedFields.value[key] = newChildMutableNode
        return newChildMutableNode
      }

      fun setField(key: String, node: MutableNode) {
        val oldNode = lazyMutatedFields.value.put(key, node)
        require(oldNode === null) {
          "internal error hzyybby2th: StructNode.setField(key=$key) called, " +
            "but that key is already assigned to a value of type ${oldNode!!.kind}"
        }
      }

      fun containsKey(key: String): Boolean {
        if (lazyMutatedFields.isInitialized() && lazyMutatedFields.value.containsKey(key)) {
          return true
        }
        return struct.containsFields(key)
      }

      fun toStruct(): Struct {
        if (!lazyMutatedFields.isInitialized()) {
          return struct
        }
        val structBuilder = struct.toBuilder()
        lazyMutatedFields.value.entries.forEach { (key, node) ->
          structBuilder.putFields(key, node.toValue())
        }
        return structBuilder.build()
      }

      override val kind = Value.KindCase.STRUCT_VALUE

      override fun toValue(): Value = toStruct().toValueProto()
    }

    class ListNode(
      parentPathSegment: DataConnectPathSegment?,
      private val listValue: ListValue,
    ) : MutableNode(parentPathSegment) {

      private val lazyMutatedValues =
        lazy(LazyThreadSafetyMode.NONE) { mutableMapOf<Int, MutableNode>() }

      fun getValue(index: Int): MutableNode {
        if (lazyMutatedValues.isInitialized()) {
          lazyMutatedValues.value[index]?.let {
            return it
          }
        }

        val childParentPathSegment = DataConnectPathSegment.ListIndex(index)
        val listElement = listValue.getValues(index)
        val newChildMutableNode = toMutableNode(listElement, childParentPathSegment)
        lazyMutatedValues.value[index] = newChildMutableNode
        return newChildMutableNode
      }

      val size: Int
        get() = listValue.valuesCount

      fun toListValue(): ListValue {
        if (!lazyMutatedValues.isInitialized()) {
          return listValue
        }
        val listValueBuilder = listValue.toBuilder()
        lazyMutatedValues.value.entries.forEach { (index, node) ->
          listValueBuilder.setValues(index, node.toValue())
        }
        return listValueBuilder.build()
      }

      override val kind = Value.KindCase.LIST_VALUE

      override fun toValue(): Value = toListValue().toValueProto()
    }

    class ScalarNode(parentPathSegment: DataConnectPathSegment?, val value: Value) :
      MutableNode(parentPathSegment) {
      override val kind
        get() = value.kindCase!!
      override fun toValue(): Value = value
    }
  }

  private fun toMutableNode(value: Value, parentPathSegment: DataConnectPathSegment?): MutableNode =
    when (value.kindCase) {
      Value.KindCase.STRUCT_VALUE -> toMutableNode(value.structValue, parentPathSegment)
      Value.KindCase.LIST_VALUE -> toMutableNode(value.listValue, parentPathSegment)
      else -> MutableNode.ScalarNode(parentPathSegment, value)
    }

  private fun toMutableNode(
    struct: Struct,
    parentPathSegment: DataConnectPathSegment?,
  ): MutableNode.StructNode = MutableNode.StructNode(parentPathSegment, struct)

  private fun toMutableNode(
    listValue: ListValue,
    parentPathSegment: DataConnectPathSegment?
  ): MutableNode.ListNode = MutableNode.ListNode(parentPathSegment, listValue)

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

  sealed class PathListIndexOutOfBoundsException(message: String) : ProtoGraftException(message)

  class PathListIndexGreaterThanOrEqualToListSizeException(message: String) :
    PathListIndexOutOfBoundsException(message)

  class NegativePathListIndexException(message: String) :
    PathListIndexOutOfBoundsException(message)

  class PathListIndexOfNonListException(message: String) : ProtoGraftException(message)

  class PathFieldOfNonStructException(message: String) : ProtoGraftException(message)
}
