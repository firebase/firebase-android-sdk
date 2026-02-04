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

import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.protobuf.Struct
import com.google.protobuf.Value

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
   * The logic also recurses into descendant [Struct] values. Any time a value from a latter
   * [Struct] has a different [Value.KindCase] than the former value then the latter value simply
   * replaces the former value. However, if they are both [Struct] values then they are overlaid
   * recursively.
   *
   * If the given collection of [Struct] objects is empty, then an empty [Struct] is returned.
   */
  fun overlay(structs: Iterable<Struct>): Struct {
    if (!structs.iterator().hasNext()) {
      return Struct.getDefaultInstance()
    }
    return structs.reduce { baseStruct, struct -> overlayStructs(baseStruct, struct) }
  }

  private fun overlayStructs(base: Struct, overlay: Struct): Struct {
    if (overlay.fieldsCount == 0) {
      return base
    }

    var overlaidStructBuilder: Struct.Builder? = null

    overlay.fieldsMap.entries.forEach { (field, value) ->
      if (overlaidStructBuilder === null) {
        val baseValue = if (base.containsFields(field)) base.getFieldsOrThrow(field) else null
        val overlaidValue: Value =
          if (baseValue === null) {
            value
          } else {
            overlayValues(baseValue, value)
          }
        if (overlaidValue !== baseValue) {
          overlaidStructBuilder = base.toBuilder()
          overlaidStructBuilder.putFields(field, overlaidValue)
        }
      } else if (!overlaidStructBuilder.containsFields(field)) {
        overlaidStructBuilder.putFields(field, value)
      } else {
        val baseValue = overlaidStructBuilder.getFieldsOrThrow(field)
        val overlaidValue = overlayValues(baseValue, value)
        overlaidStructBuilder.putFields(field, overlaidValue)
      }
    }

    return overlaidStructBuilder?.build() ?: base
  }

  private fun overlayValues(base: Value, overlay: Value): Value {
    val bothValuesAreStructs =
      base.kindCase == Value.KindCase.STRUCT_VALUE &&
        overlay.kindCase == Value.KindCase.STRUCT_VALUE
    if (!bothValuesAreStructs) {
      return overlay
    }

    val baseStruct = base.structValue
    val overlayStruct = overlay.structValue

    val overlaidStruct = overlayStructs(baseStruct, overlayStruct)

    return if (overlaidStruct === baseStruct) {
      base
    } else if (overlaidStruct === overlayStruct) {
      overlay
    } else {
      overlaidStruct.toValueProto()
    }
  }
}
