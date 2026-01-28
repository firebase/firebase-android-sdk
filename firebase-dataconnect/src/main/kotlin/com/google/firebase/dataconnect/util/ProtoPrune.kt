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
import com.google.firebase.dataconnect.MutableDataConnectPath
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedField
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value

/**
 * Holder for "global" functions for pruning values from Proto Struct objects.
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * ProtoStructEncoderKt, ProtoUtilKt, etc. Java class with public visibility, which pollutes the
 * public API. Using an "internal" object, instead, to gather together the top-level functions
 * avoids this public API pollution.
 */
internal object ProtoPrune {

  data class PruneStructResult(
    val prunedStruct: Struct,
    val prunedStructByPath: Map<DataConnectPath, Struct>,
  )

  data class PruneListValueResult(
    val prunedListValue: ListValue,
    val prunedStructByPath: Map<DataConnectPath, Struct>,
  )

  /**
   * Returns a new [Struct] with descendant [Struct] values pruned according to the given
   * [predicate].
   *
   * This function performs a depth-first traversal of the [Struct]. For each [Struct] value
   * encountered that is the value of a field (i.e. not a direct element of a [ListValue]), the
   * [predicate] is invoked.
   *
   * If the [predicate] returns `true`:
   * 1. The descendant [Struct] is "pruned" (removed) from its parent [Struct].
   * 2. The pruned [Struct] is added to the [PruneStructResult.prunedStructByPath] map in the
   * result, keyed by its [DataConnectPath].
   * 3. The traversal descends further into the pruned [Struct] to find more descendant [Struct]s to
   * prune.
   *
   * **Note:** The [predicate] is *not* invoked for:
   * * The receiver [Struct] itself.
   * * Any [Struct] values that are direct elements of a [ListValue].
   *
   * @param predicate A function that determines whether a descendant [Struct] should be pruned. It
   * is called with the [DataConnectPath] to the [Struct] from the receiver [Struct].
   * @return The pruned receiver [Struct] and a map of pruned [Struct] objects, or `null` if no
   * pruning occurred. The keys of the map are the paths for which the given [predicate] returned
   * `true`, and the associated values are the [Struct] objects that were pruned, they themselves
   * being pruned if one or more of their descendants were pruned.
   */
  fun Struct.withDescendantStructsPruned(
    predicate: (path: DataConnectPath) -> Boolean
  ): PruneStructResult? {
    val prunedStructByPath: MutableMap<DataConnectPath, Struct> = mutableMapOf()
    val prunedStruct =
      pruneDescendantStructsRecursive(this, mutableListOf(), prunedStructByPath, predicate)

    if (prunedStruct === this) {
      return null
    }

    check(prunedStructByPath.isNotEmpty()) {
      "internal error z2yafzcvca: prunedStructByPath is empty, but expected it to be non-empty " +
        "because prunedStruct===this"
    }

    return PruneStructResult(prunedStruct, prunedStructByPath.toMap())
  }

  /**
   * Returns a new [ListValue] with descendant [Struct] objects pruned according to the given
   * [predicate].
   *
   * This function performs a depth-first traversal of the [ListValue]. For each [Struct] value
   * encountered that is the value of a field (i.e. not a direct element of a [ListValue]), the
   * [predicate] is invoked.
   *
   * If the [predicate] returns `true`:
   * 1. The descendant [Struct] is "pruned" (removed) from its parent [Struct].
   * 2. The pruned [Struct] is added to the [PruneListValueResult.prunedStructByPath] map in the
   * result, keyed by its [DataConnectPath].
   * 3. The traversal descends further into the pruned [Struct] to find more descendant [Struct]s to
   * prune.
   *
   * @param predicate A function that determines whether a descendant [Struct] should be pruned. It
   * is called with the [DataConnectPath] to the [Struct] from the receiver [ListValue].
   * @return The pruned receiver [ListValue] and a map of pruned [Struct] objects, or `null` if no
   * pruning occurred. The keys of the map are the paths for which the given [predicate] returned
   * `true`, and the associated values are the [Struct] objects that were pruned, they themselves
   * being pruned if one or more of their descendants were pruned.
   */
  fun ListValue.withDescendantStructsPruned(
    predicate: (path: DataConnectPath) -> Boolean
  ): PruneListValueResult? {
    val prunedStructByPath: MutableMap<DataConnectPath, Struct> = mutableMapOf()
    val prunedListValue =
      pruneDescendantStructsRecursive(this, mutableListOf(), prunedStructByPath, predicate)

    if (prunedListValue === this) {
      return null
    }

    check(prunedStructByPath.isNotEmpty()) {
      "internal error wgffkhtrej: prunedStructByPath is empty, but expected it to be non-empty " +
        "because prunedListValue===this"
    }
    return PruneListValueResult(prunedListValue, prunedStructByPath.toMap())
  }

  private fun pruneDescendantStructsRecursive(
    struct: Struct,
    path: MutableDataConnectPath,
    prunedStructs: MutableMap<DataConnectPath, Struct>,
    predicate: (path: DataConnectPath) -> Boolean,
  ): Struct {
    var structBuilder: Struct.Builder? = null

    struct.fieldsMap.entries.forEach { (key, value) ->
      val prunedValue: Value? =
        when (value.kindCase) {
          Value.KindCase.STRUCT_VALUE -> {
            val structBefore = value.structValue
            val structAfter =
              path.withAddedField(key) {
                val immutablePath = path.toList()
                val recursedStruct =
                  pruneDescendantStructsRecursive(structBefore, path, prunedStructs, predicate)
                if (predicate(immutablePath)) {
                  prunedStructs[immutablePath] = recursedStruct
                  null
                } else {
                  recursedStruct
                }
              }
            if (structAfter === structBefore) {
              value
            } else {
              structAfter?.toValueProto()
            }
          }
          Value.KindCase.LIST_VALUE -> {
            val listValue = value.listValue
            val prunedListValue =
              path.withAddedField(key) {
                pruneDescendantStructsRecursive(listValue, path, prunedStructs, predicate)
              }
            if (listValue === prunedListValue) {
              value
            } else {
              prunedListValue.toValueProto()
            }
          }
          else -> value
        }

      if (value !== prunedValue) {
        structBuilder = structBuilder ?: struct.toBuilder()
        if (prunedValue === null) {
          structBuilder.removeFields(key)
        } else {
          structBuilder.putFields(key, prunedValue)
        }
      }
    }

    return structBuilder?.build() ?: struct
  }

  private fun pruneDescendantStructsRecursive(
    listValue: ListValue,
    path: MutableDataConnectPath,
    prunedStructs: MutableMap<DataConnectPath, Struct>,
    predicate: (path: DataConnectPath) -> Boolean,
  ): ListValue {
    var listValueBuilder: ListValue.Builder? = null

    repeat(listValue.valuesCount) { listIndex ->
      val value = listValue.getValues(listIndex)
      val prunedValue: Value =
        when (value.kindCase) {
          Value.KindCase.STRUCT_VALUE -> {
            val struct = value.structValue
            val prunedStruct =
              path.withAddedListIndex(listIndex) {
                pruneDescendantStructsRecursive(struct, path, prunedStructs, predicate)
              }
            if (struct === prunedStruct) {
              value
            } else {
              prunedStruct.toValueProto()
            }
          }
          Value.KindCase.LIST_VALUE -> {
            val listValue = value.listValue
            val prunedListValue =
              path.withAddedListIndex(listIndex) {
                pruneDescendantStructsRecursive(listValue, path, prunedStructs, predicate)
              }
            if (listValue === prunedListValue) {
              value
            } else {
              prunedListValue.toValueProto()
            }
          }
          else -> value
        }

      if (value !== prunedValue) {
        listValueBuilder = listValueBuilder ?: listValue.toBuilder()
        listValueBuilder.setValues(listIndex, prunedValue)
      }
    }

    return listValueBuilder?.build() ?: listValue
  }
}
