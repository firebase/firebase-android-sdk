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

import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.common.DelicateKotest
import io.kotest.property.Arb
import io.kotest.property.PropertyContext
import io.kotest.property.arbitrary.distinct
import kotlin.random.Random

fun PropertyContext.structWithValues(
  values: Collection<Value>,
  structKey: Arb<String> = @OptIn(DelicateKotest::class) Arb.proto.structKey().distinct()
): Struct =
  Struct.newBuilder().let { structBuilder ->
    values.forEach { structBuilder.putFields(structKey.bind(), it) }
    structBuilder.build()
  }

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
): ProtoValuePath = randomlyInsertStructs(listOf(struct), random, generateKey).single()

fun Struct.Builder.randomlyInsertStructs(
  structs: List<Struct>,
  random: Random,
  generateKey: () -> String
): List<ProtoValuePath> =
  randomlyInsertValues(this, structs.map { it.toValueProto() }, random, generateKey)

fun Struct.Builder.randomlyInsertValue(
  value: Value,
  random: Random,
  generateKey: () -> String
): ProtoValuePath = randomlyInsertValues(listOf(value), random, generateKey).single()

fun Struct.Builder.randomlyInsertValues(
  values: List<Value>,
  random: Random,
  generateKey: () -> String
): List<ProtoValuePath> = randomlyInsertValues(this, values, random, generateKey)

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
): List<ProtoValuePath> {
  val candidateInsertionPoints: List<ProtoValuePathPair> =
    structBuilder
      .build()
      .walk(includeSelf = true)
      .filter { it.value.isStructValue }
      .toList()
      .sortedWith(ProtoValuePathPairPathComparator)

  val insertionPoints: List<ProtoValuePathPair> =
    List(values.size) { candidateInsertionPoints.random(random) }

  val insertions: List<ProtoValuePathPair> =
    List(insertionPoints.size) {
      val insertionPoint = insertionPoints[it]
      val generatedKeysByPath: MutableMap<ProtoValuePath, MutableList<String>> = mutableMapOf()

      val key: String =
        sequence {
            while (true) {
              val key = generateKey()
              if (insertionPoint.value.structValue.containsFields(key)) {
                continue
              }
              val generatedKeys =
                generatedKeysByPath.getOrPut(insertionPoint.path, { mutableListOf() })
              if (generatedKeys.contains(key)) {
                continue
              }
              generatedKeys.add(key)
              yield(key)
            }
          }
          .first()

      ProtoValuePathPair(insertionPoint.path.withAppendedStructKey(key), values[it])
    }

  insertions.forEach { insertValue(structBuilder, it.path, it.value) }

  return insertions.map { it.path }
}

private fun insertValue(structBuilder: Struct.Builder, path: ProtoValuePath, value: Value) {
  require(path.isNotEmpty()) { "internal error rmeq3c634e: path is empty" }
  val pathComponent = path[0]
  require(pathComponent is StructKeyProtoValuePathComponent) {
    "internal error pt77babwtk: path[0] is ${pathComponent::class.qualifiedName}, " +
      "but expected StructKeyProtoValuePathComponent: $pathComponent"
  }
  val key = pathComponent.field

  if (path.size == 1) {
    require(!structBuilder.containsFields(key)) {
      "internal error x5kr8f9mqx: the only component of path $path ($pathComponent) " +
        "is already contained in the struct: ${structBuilder.build()}"
    }
    structBuilder.putFields(key, value)
    return
  }

  require(structBuilder.containsFields(key)) {
    "internal error sykypwq2h7: the first component of path $path ($pathComponent) " +
      "should be contained in the struct, but it is not: ${structBuilder.build()}"
  }
  val oldValue: Value = structBuilder.getFieldsOrThrow(key)
  val newValue = insertValue(oldValue, path.drop(1), value)
  structBuilder.putFields(key, newValue)
}

private fun insertValue(listValueBuilder: ListValue.Builder, path: ProtoValuePath, value: Value) {
  require(path.size > 1) {
    "internal error en68kvkb83: path.size is ${path.size}, " + "but expected a value greater than 1"
  }
  val pathComponent = path[0]
  require(pathComponent is ListElementProtoValuePathComponent) {
    "internal error kgxfp9j7ee: path[0] is ${pathComponent::class.qualifiedName}, " +
      "but expected ListElementProtoValuePathComponent: $pathComponent"
  }
  val index = pathComponent.index

  require(index >= 0 && index < listValueBuilder.valuesCount) {
    "internal error pcdar4t98b: the first component of path $path ($pathComponent) " +
      "is not a valid index: $index (list size is ${listValueBuilder.valuesCount})"
  }
  val oldValue: Value = listValueBuilder.getValues(index)
  val newValue = insertValue(oldValue, path.drop(1), value)
  listValueBuilder.setValues(index, newValue)
}

private fun insertValue(oldValue: Value, path: ProtoValuePath, value: Value) =
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
