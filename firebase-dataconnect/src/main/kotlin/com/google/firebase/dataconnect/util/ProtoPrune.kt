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
    val prunedStructsByPath: Map<DataConnectPath, Struct>,
  )

  data class PruneListValueResult(
    val prunedListValue: ListValue,
    val prunedStructsByPath: Map<DataConnectPath, Struct>,
  )

  fun Struct.withDescendantStructsPruned(
    path: DataConnectPath,
    predicate: (path: DataConnectPath, struct: Struct) -> Boolean,
  ): PruneStructResult? {
    val prunedStructsByPath: MutableMap<DataConnectPath, Struct> = mutableMapOf()
    val prunedStruct =
      pruneDescendantEntitiesRecursive(this, path.toMutableList(), prunedStructsByPath, predicate)

    if (prunedStruct === this) {
      return null
    }

    check(prunedStructsByPath.isNotEmpty()) {
      "internal error z2yafzcvca: prunedStructsByPath is empty, but expected it to be non-empty " +
        "because prunedStruct===this"
    }

    return PruneStructResult(prunedStruct, prunedStructsByPath.toMap())
  }

  fun ListValue.withDescendantStructsPruned(
    path: DataConnectPath,
    predicate: (path: DataConnectPath, struct: Struct) -> Boolean,
  ): PruneListValueResult? {
    val prunedStructsByPath: MutableMap<DataConnectPath, Struct> = mutableMapOf()
    val prunedListValue =
      pruneDescendantEntitiesRecursive(this, path.toMutableList(), prunedStructsByPath, predicate)

    if (prunedListValue === this) {
      return null
    }

    check(prunedStructsByPath.isNotEmpty()) {
      "internal error wgffkhtrej: prunedStructsByPath is empty, but expected it to be non-empty " +
        "because prunedListValue===this"
    }
    return PruneListValueResult(prunedListValue, prunedStructsByPath.toMap())
  }

  private fun pruneDescendantEntitiesRecursive(
    struct: Struct,
    path: MutableDataConnectPath,
    prunedStructs: MutableMap<DataConnectPath, Struct>,
    predicate: (path: DataConnectPath, struct: Struct) -> Boolean,
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
                if (predicate(immutablePath, structBefore)) {
                  prunedStructs[immutablePath] = structBefore
                  null
                } else {
                  pruneDescendantEntitiesRecursive(structBefore, path, prunedStructs, predicate)
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
                pruneDescendantEntitiesRecursive(listValue, path, prunedStructs, predicate)
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

  private fun pruneDescendantEntitiesRecursive(
    listValue: ListValue,
    path: MutableDataConnectPath,
    prunedStructs: MutableMap<DataConnectPath, Struct>,
    predicate: (path: DataConnectPath, struct: Struct) -> Boolean,
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
                pruneDescendantEntitiesRecursive(struct, path, prunedStructs, predicate)
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
                pruneDescendantEntitiesRecursive(listValue, path, prunedStructs, predicate)
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
