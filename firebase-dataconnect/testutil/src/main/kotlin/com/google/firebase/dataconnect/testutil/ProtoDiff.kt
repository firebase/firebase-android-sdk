/*
 * Copyright 2025 Google LLC
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

import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value

fun structFastEqual(struct1: Struct, struct2: Struct): Boolean {
  if (struct1 === struct2) {
    return true
  } else if (struct1.fieldsCount != struct2.fieldsCount) {
    return false
  }

  val struct2FieldsMap = struct2.fieldsMap
  struct1.fieldsMap.entries.forEach { (key, value1) ->
    val value2 = struct2FieldsMap[key] ?: return false
    if (!valueFastEqual(value1, value2)) {
      return false
    }
  }

  return true
}

fun listValueFastEqual(listValue1: ListValue, listValue2: ListValue): Boolean {
  if (listValue1 === listValue2) {
    return true
  } else if (listValue1.valuesCount != listValue2.valuesCount) {
    return false
  }

  listValue1.valuesList.zip(listValue2.valuesList).forEach { (value1, value2) ->
    if (!valueFastEqual(value1, value2)) {
      return false
    }
  }

  return true
}

fun valueFastEqual(value1: Value, value2: Value): Boolean {
  if (value1 === value2) {
    return true
  } else if (value1.kindCase != value2.kindCase) {
    return false
  }
  return when (value1.kindCase) {
    Value.KindCase.KIND_NOT_SET -> true
    Value.KindCase.NULL_VALUE -> true
    Value.KindCase.NUMBER_VALUE -> numberValuesEqual(value1.numberValue, value2.numberValue)
    Value.KindCase.STRING_VALUE -> value1.stringValue == value2.stringValue
    Value.KindCase.BOOL_VALUE -> value1.boolValue == value2.boolValue
    Value.KindCase.STRUCT_VALUE -> structFastEqual(value1.structValue, value2.structValue)
    Value.KindCase.LIST_VALUE -> listValueFastEqual(value1.listValue, value2.listValue)
  }
}

data class DifferencePathPair<T : Difference>(val path: ProtoValuePath, val difference: T)

sealed interface Difference {
  data class KindCase(val value1: Value, val value2: Value) : Difference
  data class BoolValue(val value1: Boolean, val value2: Boolean) : Difference
  data class NumberValue(val value1: Double, val value2: Double) : Difference
  data class StringValue(val value1: String, val value2: String) : Difference
  data class StructMissingKey(val key: String, val value: Value) : Difference
  data class StructUnexpectedKey(val key: String, val value: Value) : Difference
  data class ListMissingElement(val index: Int, val value: Value) : Difference
  data class ListUnexpectedElement(val index: Int, val value: Value) : Difference
}

fun structDiff(
  struct1: Struct,
  struct2: Struct,
  differences: DifferenceAccumulator = DifferenceAccumulator(),
): DifferenceAccumulator {
  val map1 = struct1.fieldsMap
  val map2 = struct2.fieldsMap

  map1.entries.forEach { (key, value) ->
    if (key !in map2) {
      differences.add(Difference.StructMissingKey(key, value))
    } else {
      differences.withPushedPathComponent({ ProtoValuePathComponent.StructKey(key) }) {
        valueDiff(value, map2[key]!!, differences)
      }
    }
  }

  map2.entries.forEach { (key, value) ->
    if (key !in map1) {
      differences.add(Difference.StructUnexpectedKey(key, value))
    }
  }

  return differences
}

fun listValueDiff(
  listValue1: ListValue,
  listValue2: ListValue,
  differences: DifferenceAccumulator = DifferenceAccumulator(),
): DifferenceAccumulator {
  repeat(listValue1.valuesCount.coerceAtMost(listValue2.valuesCount)) {
    val value1 = listValue1.getValues(it)
    val value2 = listValue2.getValues(it)
    differences.withPushedPathComponent({ ProtoValuePathComponent.ListIndex(it) }) {
      valueDiff(value1, value2, differences)
    }
  }

  if (listValue1.valuesCount > listValue2.valuesCount) {
    (listValue2.valuesCount until listValue1.valuesCount).forEach {
      differences.add(Difference.ListMissingElement(it, listValue1.getValues(it)))
    }
  } else if (listValue1.valuesCount < listValue2.valuesCount) {
    (listValue1.valuesCount until listValue2.valuesCount).forEach {
      differences.add(Difference.ListUnexpectedElement(it, listValue2.getValues(it)))
    }
  }

  return differences
}

fun valueDiff(
  value1: Value,
  value2: Value,
  differences: DifferenceAccumulator = DifferenceAccumulator(),
): DifferenceAccumulator {
  if (value1.kindCase != value2.kindCase) {
    differences.add(Difference.KindCase(value1, value2))
    return differences
  }

  when (value1.kindCase) {
    Value.KindCase.KIND_NOT_SET,
    Value.KindCase.NULL_VALUE -> {}
    Value.KindCase.STRUCT_VALUE -> structDiff(value1.structValue, value2.structValue, differences)
    Value.KindCase.LIST_VALUE -> listValueDiff(value1.listValue, value2.listValue, differences)
    Value.KindCase.BOOL_VALUE ->
      if (value1.boolValue != value2.boolValue) {
        differences.add(Difference.BoolValue(value1.boolValue, value2.boolValue))
      }
    Value.KindCase.NUMBER_VALUE ->
      if (!numberValuesEqual(value1.numberValue, value2.numberValue)) {
        differences.add(Difference.NumberValue(value1.numberValue, value2.numberValue))
      }
    Value.KindCase.STRING_VALUE ->
      if (value1.stringValue != value2.stringValue) {
        differences.add(Difference.StringValue(value1.stringValue, value2.stringValue))
      }
  }

  return differences
}

class DifferenceAccumulator {
  private val differences = mutableListOf<DifferencePathPair<*>>()
  private val path: MutableProtoValuePath = mutableListOf()

  val size: Int by differences::size

  fun toList(): List<DifferencePathPair<*>> = differences.toList()

  fun pushPathComponent(pathComponent: ProtoValuePathComponent) {
    path.add(pathComponent)
  }

  fun popPathComponent() {
    path.removeAt(path.lastIndex)
  }

  fun add(difference: Difference) {
    differences.add(DifferencePathPair(path.toList(), difference))
  }

  override fun toString() = buildString {
    if (differences.size == 1) {
      append("1 difference: ")
      append(differences.single().run { "${path.toPathString()}=$difference" })
    } else {
      append("${differences.size} differences:")
      differences.forEachIndexed { index, (path, difference) ->
        append('\n')
        append(index + 1)
        append(": ")
        appendPathString(path)
        append('=')
        append(difference)
      }
    }
  }
}

private inline fun <T> DifferenceAccumulator?.withPushedPathComponent(
  pathComponent: () -> ProtoValuePathComponent,
  block: () -> T
): T {
  this?.pushPathComponent(pathComponent())
  return try {
    block()
  } finally {
    this?.popPathComponent()
  }
}

fun numberValuesEqual(value1: Double, value2: Double): Boolean =
  if (value1.isNaN()) {
    value2.isNaN()
  } else if (value1 != value2) {
    false
  } else if (value1 == 0.0) {
    // Explicitly consider 0.0 and -0.0 to be "unequal"; the == operator considers them "equal".
    value1.toBits() == value2.toBits()
  } else {
    true
  }
