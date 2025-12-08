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
  mode: RandomInsertMode = RandomInsertMode.StructAndListValue,
  generateKey: () -> String
): Struct = withRandomlyInsertedStructs(listOf(struct), random, mode, generateKey)

fun Struct.withRandomlyInsertedStructs(
  structs: List<Struct>,
  random: Random,
  mode: RandomInsertMode = RandomInsertMode.StructAndListValue,
  generateKey: () -> String
): Struct =
  withRandomlyInsertedValues(this, structs.map { it.toValueProto() }, random, mode, generateKey)

fun Struct.withRandomlyInsertedValues(
  values: List<Value>,
  random: Random,
  mode: RandomInsertMode = RandomInsertMode.StructAndListValue,
  generateKey: () -> String
): Struct = withRandomlyInsertedValues(this, values, random, mode, generateKey)

/**
 * The types of values into which values will be randomly inserted by [withRandomlyInsertedValues].
 */
enum class RandomInsertMode(val supportedKindCases: Set<Value.KindCase>) {
  Struct(setOf(Value.KindCase.STRUCT_VALUE)),
  ListValue(setOf(Value.KindCase.LIST_VALUE)),
  StructAndListValue(setOf(Value.KindCase.STRUCT_VALUE, Value.KindCase.LIST_VALUE)),
}

@JvmName("withRandomlyInsertedValuesInternal")
private fun withRandomlyInsertedValues(
  struct: Struct,
  values: List<Value>,
  random: Random,
  mode: RandomInsertMode,
  generateKey: () -> String
): Struct {
  val candidateInsertionPaths =
    struct
      .walk(includeSelf = true)
      .filter { mode.supportedKindCases.contains(it.value.kindCase) }
      .map { it.path }
      .toList()

  val insertions =
    values.map {
      val insertionPath = candidateInsertionPaths.random(random)
      ProtoValuePathPair(insertionPath, it)
    }

  return struct.map { path, value ->
    val curInsertions = insertions.filter { it.path == path }.map { it.value }
    if (curInsertions.isEmpty()) {
      value
    } else if (value.isStructValue) {
      value.structValue
        .toBuilder()
        .also { structBuilder ->
          curInsertions.forEach { valueToInsert ->
            structBuilder.putFieldsWithUnsetKey(valueToInsert, generateKey)
          }
        }
        .build()
        .toValueProto()
    } else if (value.isListValue) {
      val newValuesList = value.listValue.valuesList.toMutableList()
      newValuesList.addAll(curInsertions)
      newValuesList.shuffle(random)
      newValuesList.toValueProto()
    } else {
      throw IllegalStateException("should never get here value=$value [ywwmyjnpwa]")
    }
  }
}

private fun Struct.Builder.generateUnsetKey(generateKey: () -> String): String {
  while (true) {
    val key = generateKey()
    if (!containsFields(key)) {
      return key
    }
  }
}

private fun Struct.Builder.putFieldsWithUnsetKey(value: Value, generateKey: () -> String) {
  val key = generateUnsetKey(generateKey)
  putFields(key, value)
}
