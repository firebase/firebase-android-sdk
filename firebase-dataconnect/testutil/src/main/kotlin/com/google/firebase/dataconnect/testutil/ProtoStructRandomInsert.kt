/*
 * Copyright 2024 Google LLC
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

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import kotlin.random.Random

fun Struct.withRandomlyInsertedStruct(
  struct: Struct,
  random: Random,
  generateKey: () -> String
): Struct = withRandomlyInsertedStructs(listOf(struct), random, generateKey)

fun Struct.withRandomlyInsertedStructs(
  structs: List<Struct>,
  random: Random,
  generateKey: () -> String
): Struct = withRandomlyInsertedValues(this, structs.map { it.toValueProto() }, random, generateKey)

fun Struct.withRandomlyInsertedValue(
  value: Value,
  random: Random,
  generateKey: () -> String
): Struct = withRandomlyInsertedValues(listOf(value), random, generateKey)

fun Struct.withRandomlyInsertedValues(
  values: List<Value>,
  random: Random,
  generateKey: () -> String
): Struct = withRandomlyInsertedValues(this, values, random, generateKey)

fun Struct.Builder.randomlyInsertStruct(
  struct: Struct,
  random: Random,
  generateKey: () -> String
): DataConnectPath = randomlyInsertStructs(listOf(struct), random, generateKey).single()

fun Struct.Builder.randomlyInsertStructs(
  structs: List<Struct>,
  random: Random,
  generateKey: () -> String
): List<DataConnectPath> =
  randomlyInsertValues(this, structs.map { it.toValueProto() }, random, generateKey)

fun Struct.Builder.randomlyInsertValue(
  value: Value,
  random: Random,
  generateKey: () -> String
): DataConnectPath = randomlyInsertValues(listOf(value), random, generateKey).single()

fun Struct.Builder.randomlyInsertValues(
  values: List<Value>,
  random: Random,
  generateKey: () -> String
): List<DataConnectPath> = randomlyInsertValues(this, values, random, generateKey)

@JvmName("withRandomlyInsertedValuesInternal")
private fun withRandomlyInsertedValues(
  struct: Struct,
  values: List<Value>,
  random: Random,
  generateKey: () -> String
): Struct {
  val structBuilder = struct.toBuilder()
  randomlyInsertValues(structBuilder, values, random, generateKey)
  return structBuilder.build()
}

@JvmName("randomlyInsertValuesInternal")
private fun randomlyInsertValues(
  structBuilder: Struct.Builder,
  values: List<Value>,
  random: Random,
  generateKey: () -> String
): List<DataConnectPath> {
  val candidateInsertionPoints: List<DataConnectPathValuePair> =
    structBuilder
      .build()
      .walk(includeSelf = true)
      .filter { it.value.isStructValue }
      .toList()
      .sortedWith(DataConnectPathValuePairPathComparator)

  val insertionPoints: List<DataConnectPathValuePair> =
    List(values.size) { candidateInsertionPoints.random(random) }

  val insertions: List<DataConnectPathValuePair> = run {
    val generatedKeysByPath: Map<DataConnectPath, MutableSet<String>> = buildMap {
      candidateInsertionPoints.forEach { (path, value) ->
        put(path, value.structValue.fieldsMap.keys.toMutableSet())
      }
    }

    fun generateKeyForInsertionPointAt(path: DataConnectPath): String {
      val unavailableKeys = generatedKeysByPath[path]!!
      while (true) {
        val key = generateKey()
        if (!unavailableKeys.contains(key)) {
          unavailableKeys.add(key)
          return key
        }
      }
    }

    List(insertionPoints.size) {
      val insertionPoint = insertionPoints[it]
      val key = generateKeyForInsertionPointAt(insertionPoint.path)
      DataConnectPathValuePair(insertionPoint.path.withAddedField(key), values[it])
    }
  }

  insertions.forEach { insertValue(structBuilder, it.path, it.value) }

  return insertions.map { it.path }
}

private fun insertValue(structBuilder: Struct.Builder, path: DataConnectPath, value: Value) {
  require(path.isNotEmpty()) { "internal error rmeq3c634e: path is empty" }
  val pathSegment = path[0]
  require(pathSegment is DataConnectPathSegment.Field) {
    "internal error pt77babwtk: path[0] is ${pathSegment::class.qualifiedName}, " +
      "but expected DataConnectPathSegment.Field: $pathSegment"
  }
  val key = pathSegment.field

  if (path.size == 1) {
    require(!structBuilder.containsFields(key)) {
      "internal error x5kr8f9mqx: the only segment of path $path ($pathSegment) " +
        "is already contained in the struct: ${structBuilder.build()}"
    }
    structBuilder.putFields(key, value)
    return
  }

  require(structBuilder.containsFields(key)) {
    "internal error sykypwq2h7: the first segment of path $path ($pathSegment) " +
      "should be contained in the struct, but it is not: ${structBuilder.build()}"
  }
  val oldValue: Value = structBuilder.getFieldsOrThrow(key)
  val newValue = insertValue(oldValue, path.drop(1), value)
  structBuilder.putFields(key, newValue)
}

private fun insertValue(listValueBuilder: ListValue.Builder, path: DataConnectPath, value: Value) {
  require(path.size > 1) {
    "internal error en68kvkb83: path.size is ${path.size}, but expected a value greater than 1"
  }
  val pathSegment = path[0]
  require(pathSegment is DataConnectPathSegment.ListIndex) {
    "internal error kgxfp9j7ee: path[0] is ${pathSegment::class.qualifiedName}, " +
      "but expected DataConnectPathSegment.ListIndex: $pathSegment"
  }
  val index = pathSegment.index

  require(index >= 0 && index < listValueBuilder.valuesCount) {
    "internal error pcdar4t98b: the first segment of path $path ($pathSegment) " +
      "is not a valid index: $index (list size is ${listValueBuilder.valuesCount})"
  }
  val oldValue: Value = listValueBuilder.getValues(index)
  val newValue = insertValue(oldValue, path.drop(1), value)
  listValueBuilder.setValues(index, newValue)
}

private fun insertValue(oldValue: Value, path: DataConnectPath, value: Value) =
  if (oldValue.isStructValue) {
    val oldValueBuilder = oldValue.structValue.toBuilder()
    insertValue(oldValueBuilder, path, value)
    oldValueBuilder.build().toValueProto()
  } else if (oldValue.isListValue) {
    val oldValueBuilder = oldValue.listValue.toBuilder()
    insertValue(oldValueBuilder, path, value)
    oldValueBuilder.build().toValueProto()
  } else {
    throw IllegalArgumentException(
      "internal error m6zknembwx: value at path $path is a ${oldValue.kindCase}, " +
        "but expected Struct or ListValue (oldValue=$oldValue)"
    )
  }
