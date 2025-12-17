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

import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import kotlin.random.Random
import kotlin.random.nextInt

fun ListValue.withRandomlyInsertedValue(
  value: Value,
  random: Random,
): ListValue = withRandomlyInsertedValues(listOf(value), random)

fun ListValue.withRandomlyInsertedValues(
  values: List<Value>,
  random: Random,
): ListValue = withRandomlyInsertedValues(this, values, random)

fun ListValue.Builder.randomlyInsertValue(
  value: Value,
  random: Random,
): ProtoValuePath = randomlyInsertValues(listOf(value), random).single()

fun ListValue.Builder.randomlyInsertValues(
  values: List<Value>,
  random: Random,
): List<ProtoValuePath> = randomlyInsertValues(this, values, random)

@JvmName("withRandomlyInsertedValuesInternal")
private fun withRandomlyInsertedValues(
  listValue: ListValue,
  values: List<Value>,
  random: Random,
): ListValue {
  val listValueBuilder = listValue.toBuilder()
  randomlyInsertValues(listValueBuilder, values, random)
  return listValueBuilder.build()
}

@JvmName("randomlyInsertValuesInternal")
private fun randomlyInsertValues(
  listValueBuilder: ListValue.Builder,
  values: List<Value>,
  random: Random,
): List<ProtoValuePath> {
  val listValuePaths: List<ProtoValuePath> =
    listValueBuilder
      .build()
      .walk(includeSelf = true)
      .filter { it.value.isListValue }
      .map { it.path }
      .toList()
      .sortedWith(ProtoValuePathComparator)

  val insertionListValuePaths = MutableList(values.size) { listValuePaths.random(random) }
  val insertedPaths: MutableList<ProtoValuePath> = mutableListOf()

  values.forEachIndexed { index, valueToInsert ->
    val insertionListValuePath = insertionListValuePaths[index]
    val insertIndex =
      insertValue(listValueBuilder, valueToInsert, insertionListValuePath, 0, random)
    val insertedPath = insertionListValuePath.withAppendedListIndex(insertIndex)

    fun ProtoValuePath.bumpIndex(): ProtoValuePath {
      val componentIndex = insertionListValuePath.size
      return if (size <= componentIndex) {
        this
      } else if (subList(0, componentIndex) != insertionListValuePath) {
        this
      } else {
        val listIndexComponent = get(componentIndex)
        check(listIndexComponent is ProtoValuePathComponent.ListIndex)
        if (listIndexComponent.index < insertIndex) {
          this
        } else {
          subList(0, componentIndex) +
            listOf(ProtoValuePathComponent.ListIndex(listIndexComponent.index + 1)) +
            subList(componentIndex + 1, size)
        }
      }
    }

    fun MutableList<ProtoValuePath>.bumpIndexes() {
      indices.forEach { set(it, get(it).bumpIndex()) }
    }

    insertionListValuePaths.bumpIndexes()
    insertedPaths.bumpIndexes()
    insertedPaths.add(insertedPath)
  }

  return insertedPaths.toList()
}

private fun insertValue(
  listValueBuilder: ListValue.Builder,
  valueToInsert: Value,
  insertionListValuePath: ProtoValuePath,
  insertionListValuePathIndex: Int,
  random: Random,
): Int {
  if (insertionListValuePathIndex == insertionListValuePath.size) {
    val insertIndex = random.nextInt(0..listValueBuilder.valuesCount)
    listValueBuilder.addValues(insertIndex, valueToInsert)
    return insertIndex
  }

  val index =
    insertionListValuePath[insertionListValuePathIndex].let {
      check(it is ProtoValuePathComponent.ListIndex) {
        "internal error cj7kz86pyd: path ${insertionListValuePath.toPathString()} should have " +
          "a ListIndex as component $insertionListValuePathIndex, but got: $it"
      }
      it.index
    }

  val childValue = listValueBuilder.getValues(index)

  return when (childValue.kindCase) {
    Value.KindCase.LIST_VALUE -> {
      childValue.listValue.toBuilder().let { childListValueBuilder ->
        val insertIndex =
          insertValue(
            childListValueBuilder,
            valueToInsert,
            insertionListValuePath,
            insertionListValuePathIndex + 1,
            random
          )
        listValueBuilder.setValues(index, childListValueBuilder.build().toValueProto())
        insertIndex
      }
    }
    Value.KindCase.STRUCT_VALUE -> {
      childValue.structValue.toBuilder().let { childStructBuilder ->
        val insertIndex =
          insertValue(
            childStructBuilder,
            valueToInsert,
            insertionListValuePath,
            insertionListValuePathIndex + 1,
            random
          )
        listValueBuilder.setValues(index, childStructBuilder.build().toValueProto())
        insertIndex
      }
    }
    else -> {
      val path = insertionListValuePath.subList(0, insertionListValuePathIndex + 1).toPathString()
      throw IllegalStateException(
        "internal error eyffagymnx: value at $path is ${childValue.kindCase}, " +
          "but expected LIST_VALUE or STRUCT_VALUE"
      )
    }
  }
}

private fun insertValue(
  structBuilder: Struct.Builder,
  valueToInsert: Value,
  insertionListValuePath: ProtoValuePath,
  insertionListValuePathIndex: Int,
  random: Random,
): Int {
  require(insertionListValuePathIndex < insertionListValuePath.size) {
    "internal error wnvrtq9df9: insertionListValuePathIndex=$insertionListValuePathIndex, " +
      "but a value strictly less than " +
      "insertionListValuePath.size=${insertionListValuePath.size} is required"
  }

  val key =
    insertionListValuePath[insertionListValuePathIndex].let {
      check(it is ProtoValuePathComponent.StructKey) {
        "internal error zazshp58ax: path ${insertionListValuePath.toPathString()} should have " +
          "a StructKey as component $insertionListValuePathIndex, but got: $it"
      }
      it.key
    }

  val childValue = structBuilder.getFieldsOrThrow(key)

  return when (childValue.kindCase) {
    Value.KindCase.LIST_VALUE -> {
      childValue.listValue.toBuilder().let { childListValueBuilder ->
        val insertIndex =
          insertValue(
            childListValueBuilder,
            valueToInsert,
            insertionListValuePath,
            insertionListValuePathIndex + 1,
            random
          )
        structBuilder.putFields(key, childListValueBuilder.build().toValueProto())
        insertIndex
      }
    }
    Value.KindCase.STRUCT_VALUE -> {
      childValue.structValue.toBuilder().let { childStructBuilder ->
        val insertIndex =
          insertValue(
            childStructBuilder,
            valueToInsert,
            insertionListValuePath,
            insertionListValuePathIndex + 1,
            random
          )
        structBuilder.putFields(key, childStructBuilder.build().toValueProto())
        insertIndex
      }
    }
    else -> {
      val path = insertionListValuePath.subList(0, insertionListValuePathIndex + 1).toPathString()
      throw IllegalStateException(
        "internal error rarj6p3snc: value at $path is ${childValue.kindCase}, " +
          "but expected LIST_VALUE or STRUCT_VALUE"
      )
    }
  }
}
