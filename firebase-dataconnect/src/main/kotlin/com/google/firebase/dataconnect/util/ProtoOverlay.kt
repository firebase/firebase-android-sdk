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

import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import kotlin.math.min

/**
 * Holder for "global" functions for overlaying Struct values.
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * ProtoStructEncoderKt, ProtoUtilKt, etc. Java class with public visibility, which pollutes the
 * public API. Using an "internal" object, instead, to gather together the top-level functions
 * avoids this public API pollution.
 */
internal object ProtoOverlay {

  /**
   * Returns a [Struct] that is composed by overlaying the values from subsequent [Struct] values in
   * the given [Struct] collection over top of earlier [Struct] values.
   *
   * For example, suppose the first [Struct] is `{a: "foo", b: 42.0}` and the second [Struct] is
   * `{b: true, c: []}`. Then the [Struct] returned from this method would be: `{a: "foo", b: true,
   * c: []}`.
   *
   * The logic also recurses into descendant [Struct] and [ListValue] values. Any time a value from
   * a latter [Struct] has a different [Value.KindCase] than the former value then the latter value
   * simply replaces the former value. However, if they are both [Struct] or both [ListValue] then
   * they are overlaid recursively, as defined below.
   *
   * When recursing into [ListValue] objects, the first n values, where n is the size of the shorter
   * list, are recursed. All extraneous values are appended.
   */
  fun overlay(structs: Iterable<Struct>): Struct {
    if (!structs.iterator().hasNext()) {
      return Struct.getDefaultInstance()
    }
    return structs.reduce { acc, struct -> overlayStructs(acc, struct) }
  }

  private fun overlayStructs(base: Struct, overlay: Struct): Struct {
    val resultBuilder = Struct.newBuilder()
    val allKeys = base.fieldsMap.keys + overlay.fieldsMap.keys

    for (key in allKeys) {
      val baseValue = base.fieldsMap[key]
      val overlayValue = overlay.fieldsMap[key]

      when {
        baseValue != null && overlayValue != null ->
          resultBuilder.putFields(key, overlayValues(baseValue, overlayValue))
        baseValue != null -> resultBuilder.putFields(key, baseValue)
        overlayValue != null -> resultBuilder.putFields(key, overlayValue)
      }
    }
    return resultBuilder.build()
  }

  private fun overlayValues(base: Value, overlay: Value): Value {
    if (base.kindCase != overlay.kindCase) {
      return overlay
    }

    return when (overlay.kindCase) {
      Value.KindCase.STRUCT_VALUE ->
        Value.newBuilder()
          .setStructValue(overlayStructs(base.structValue, overlay.structValue))
          .build()
      Value.KindCase.LIST_VALUE ->
        Value.newBuilder().setListValue(overlayLists(base.listValue, overlay.listValue)).build()
      else ->
        // For scalar types, the overlay value replaces the base value.
        overlay
    }
  }

  private fun overlayLists(base: ListValue, overlay: ListValue): ListValue {
    val resultListBuilder = ListValue.newBuilder()
    val baseSize = base.valuesCount
    val overlaySize = overlay.valuesCount
    val commonSize = min(baseSize, overlaySize)

    // Overlay common elements
    for (i in 0 until commonSize) {
      val baseElement = base.getValues(i)
      val overlayElement = overlay.getValues(i)
      resultListBuilder.addValues(overlayValues(baseElement, overlayElement))
    }

    // Add remaining elements from the longer list
    if (baseSize > overlaySize) {
      for (i in commonSize until baseSize) {
        resultListBuilder.addValues(base.getValues(i))
      }
    } else if (overlaySize > baseSize) {
      for (i in commonSize until overlaySize) {
        resultListBuilder.addValues(overlay.getValues(i))
      }
    }

    return resultListBuilder.build()
  }
}
