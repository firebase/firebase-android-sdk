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
import com.google.firebase.dataconnect.emptyMutableDataConnectPath
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedField
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value

/**
 * A function that is invoked by [ProtoPrune.withPrunedDescendants] to determine if a value should
 * be pruned.
 *
 * The `path` argument is the path from the root [Struct] or [ListValue] to the value whose pruning
 * is to be determined.
 *
 * The `listSize` argument is `null` if the value whose pruning is to be determined is a [Struct],
 * or the size of the list if the value whose pruning is to be determined is a [ListValue].
 *
 * A return value of `true` indicates that the value should be pruned, and `false` indicates that it
 * should _not_ be pruned.
 */
internal typealias WithPrunedDescendantsPredicate =
  (path: DataConnectPath, listSize: Int?) -> Boolean

/**
 * Holder for "global" functions for pruning values from Proto Struct objects.
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * ProtoStructEncoderKt, ProtoUtilKt, etc. Java class with public visibility, which pollutes the
 * public API. Using an "internal" object, instead, to gather together the top-level functions
 * avoids this public API pollution.
 */
internal object ProtoPrune {

  sealed interface PrunedValue

  @JvmInline
  value class PrunedStruct(val struct: Struct) : PrunedValue {
    override fun toString() = struct.toCompactString()
  }

  @JvmInline
  value class PrunedListValue(val structs: List<Struct>) : PrunedValue {
    override fun toString() = structs.map { it.toCompactString() }.toString()
  }

  data class PruneStructResult(
    val prunedStruct: Struct,
    val prunedValueByPath: Map<DataConnectPath, PrunedValue>,
  )

  data class PruneListValueResult(
    val prunedListValue: ListValue,
    val prunedValueByPath: Map<DataConnectPath, PrunedValue>,
  )

  /**
   * Returns a new [Struct] with descendant [Struct] and [ListValue] values pruned according to the
   * given [predicate].
   *
   * This function performs a depth-first traversal of the [Struct]. For each [Struct] and
   * [ListValue] value encountered that is the value of a field (i.e. not a direct element of a
   * [ListValue]), the [predicate] is invoked.
   *
   * If the [predicate] returns `true`:
   * 1. The descendant [Struct] or [ListValue] is "pruned" (removed) from its parent [Struct].
   * 2. The pruned value is added to the [PruneStructResult.prunedValueByPath] map in the result,
   * keyed by its [DataConnectPath].
   * 3. The traversal descends further into the pruned [Struct] or [ListValue] to find more
   * descendant values to prune.
   *
   * **Note:** The [predicate] is *not* invoked for:
   * * The receiver [Struct] itself.
   * * Any value that are direct elements of a [ListValue].
   *
   * @param predicate A function that determines whether a descendant value should be pruned; see
   * [WithPrunedDescendantsPredicate] for details.
   * @return The pruned receiver [Struct] and a map of pruned values, or `null` if no pruning
   * occurred. The keys of the map are the paths for which the given [predicate] returned `true`,
   * and the associated values are the objects that were pruned, they themselves being pruned if one
   * or more of their descendants were pruned.
   */
  fun Struct.withPrunedDescendants(predicate: WithPrunedDescendantsPredicate): PruneStructResult? {
    val prunedValueByPath: MutableMap<DataConnectPath, PrunedValue> = mutableMapOf()
    val prunedStruct =
      pruneDescendantsRecursive(this, emptyMutableDataConnectPath(), prunedValueByPath, predicate)

    if (prunedStruct === this) {
      return null
    }

    check(prunedValueByPath.isNotEmpty()) {
      "internal error z2yafzcvca: prunedValueByPath is empty, but expected it to be non-empty " +
        "because prunedStruct===this"
    }

    return PruneStructResult(prunedStruct, prunedValueByPath.toMap())
  }

  /**
   * Returns a new [ListValue] with descendant [Struct] and [ListValue] values pruned according to
   * the given [predicate].
   *
   * This function performs a depth-first traversal of the [ListValue]. For each [Struct] and
   * [ListValue] value encountered that is the value of a field (i.e. not a direct element of a
   * [ListValue]), the [predicate] is invoked.
   *
   * If the [predicate] returns `true`:
   * 1. The descendant [Struct] or [ListValue] is "pruned" (removed) from its parent [Struct].
   * 2. The pruned value is added to the [PruneListValueResult.prunedValueByPath] map in the result,
   * keyed by its [DataConnectPath].
   * 3. The traversal descends further into the pruned [Struct] or [ListValue] to find more
   * descendant values to prune.
   *
   * **Note:** The [predicate] is *not* invoked for any value that is a direct element of a
   * [ListValue].
   *
   * @param predicate A function that determines whether a descendant value should be pruned; see
   * [WithPrunedDescendantsPredicate] for details.
   * @return The pruned receiver [ListValue] and a map of pruned values, or `null` if no pruning
   * occurred. The keys of the map are the paths for which the given [predicate] returned `true`,
   * and the associated values are the objects that were pruned, they themselves being pruned if one
   * or more of their descendants were pruned.
   */
  fun ListValue.withPrunedDescendants(
    predicate: WithPrunedDescendantsPredicate
  ): PruneListValueResult? {
    val prunedValueByPath: MutableMap<DataConnectPath, PrunedValue> = mutableMapOf()
    val prunedListValue =
      pruneDescendantsRecursive(this, emptyMutableDataConnectPath(), prunedValueByPath, predicate)

    if (prunedListValue === this) {
      return null
    }

    check(prunedValueByPath.isNotEmpty()) {
      "internal error wgffkhtrej: prunedValueByPath is empty, but expected it to be non-empty " +
        "because prunedListValue===this"
    }
    return PruneListValueResult(prunedListValue, prunedValueByPath.toMap())
  }

  private fun pruneDescendantsRecursive(
    struct: Struct,
    path: MutableDataConnectPath,
    prunedValues: MutableMap<DataConnectPath, PrunedValue>,
    predicate: WithPrunedDescendantsPredicate,
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
                  pruneDescendantsRecursive(structBefore, path, prunedValues, predicate)
                if (predicate(immutablePath, null)) {
                  prunedValues[immutablePath] = PrunedStruct(recursedStruct)
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
                pruneDescendantsRecursive(listValue, path, prunedValues, predicate)
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

  private fun pruneDescendantsRecursive(
    listValue: ListValue,
    path: MutableDataConnectPath,
    prunedValues: MutableMap<DataConnectPath, PrunedValue>,
    predicate: WithPrunedDescendantsPredicate,
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
                pruneDescendantsRecursive(struct, path, prunedValues, predicate)
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
                pruneDescendantsRecursive(listValue, path, prunedValues, predicate)
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
