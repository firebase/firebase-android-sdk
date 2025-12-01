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

import com.google.firebase.dataconnect.DataConnectPathSegment as PathComponent
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.protobuf.ListValue
import com.google.protobuf.MessageLite
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.print.print
import io.kotest.common.DelicateKotest
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import io.kotest.property.Arb
import io.kotest.property.PropertyContext
import io.kotest.property.arbitrary.distinct
import java.util.regex.Pattern
import kotlin.random.Random

/** Asserts that a proto message is equal to the given proto message. */
infix fun MessageLite?.shouldBe(other: MessageLite?): MessageLite? {
  this should beEqualTo(other)
  return this
}

/** Asserts that a proto Struct is equal to the given proto Struct. */
infix fun Struct?.shouldBe(other: Struct?): Struct? {
  this should beEqualTo(other)
  return this
}

/** Asserts that a proto Struct is equal to the given proto Struct. */
infix fun Value?.shouldBe(other: Value?): Value? {
  this should beEqualTo(other)
  return this
}

/** Asserts that a proto message is equal to the default instance of its type. */
fun MessageLite?.shouldBeDefaultInstance(): MessageLite? {
  this should beEqualToDefaultInstance()
  return this
}

/**
 * Creates and returns a [Matcher] that can be used with kotest assertions for verifying that a
 * proto message is equal to the default instance of that type.
 */
fun beEqualToDefaultInstance(): Matcher<MessageLite?> = neverNullMatcher { value ->
  val valueStr = value.toTrimmedStringForTesting()
  val defaultInstance = value.defaultInstanceForType
  val defaultStr = defaultInstance.toTrimmedStringForTesting()
  MatcherResult(
    valueStr == defaultStr,
    {
      "${value::class.qualifiedName} ${value.print().value} should be equal to " +
        "the default instance: ${defaultInstance.print().value}"
    },
    {
      "${value::class.qualifiedName} ${value.print().value} should not be equal to : " +
        "the default instance: ${defaultInstance.print().value}"
    }
  )
}

/**
 * Creates and returns a [Matcher] that can be used with kotest assertions for verifying that a
 * proto message is equal to the given proto message.
 */
fun beEqualTo(
  other: MessageLite?,
  messagePrinter: (MessageLite) -> String = { it.print().value }
): Matcher<MessageLite?> = neverNullMatcher { value ->
  if (other === null) {
    MatcherResult(
      false,
      { "${value::class.qualifiedName} ${messagePrinter(value)} should be null" },
      { TODO("should not get here (error code: r2kap3te33)") }
    )
  } else {
    val valueClass = value::class
    val otherClass = other::class
    val valueStr = value.toTrimmedStringForTesting()
    val otherStr = other.toTrimmedStringForTesting()
    MatcherResult(
      valueClass == otherClass && valueStr == otherStr,
      {
        "${valueClass.qualifiedName} ${messagePrinter(value)} should be equal to " +
          "${otherClass.qualifiedName}: ${messagePrinter(other)}"
      },
      {
        "${valueClass.qualifiedName} ${messagePrinter(value)} should not be equal to " +
          "${otherClass.qualifiedName}: ${messagePrinter(other)}"
      }
    )
  }
}

/**
 * Creates and returns a [Matcher] that can be used with kotest assertions for verifying that a
 * proto Struct is equal to the given proto Struct.
 */
fun beEqualTo(
  other: Struct?,
  structPrinter: (Struct) -> String = { it.print().value }
): Matcher<Struct?> = neverNullMatcher { value ->
  if (other === null) {
    MatcherResult(
      false,
      { "${structPrinter(value)} should be null" },
      { TODO("should not get here (error code: r2kap3te33)") }
    )
  } else {
    MatcherResult(
      structFastEqual(value, other),
      {
        "${structPrinter(value)} should be equal to ${structPrinter(other)}, " +
          "but found ${structDiff(value, other)}"
      },
      { "${structPrinter(value)} should not be equal to ${structPrinter(other)}" }
    )
  }
}

// It is wrong to compare protos using their string representations. The MessageLite runtime
// deliberately prefixes debug strings with their Object.toString() to discourage string
// comparison. However, this reads poorly in tests, and makes it harder to identify differences
// from the strings alone. So, we manually strip this prefix.
// This code was adapted from
// https://github.com/google/truth/blob/f1f4d1450d/extensions/liteproto/src/main/java/com/google/common/truth/extensions/proto/LiteProtoSubject.java#L73-L93
private fun MessageLite?.toTrimmedStringForTesting(): String {
  if (this === null) {
    return "<null>"
  }

  val subjectString =
    toString().let { subjectString ->
      subjectString.trim().let { trimmedSubjectString ->
        if (!trimmedSubjectString.startsWith("# ")) {
          subjectString
        } else {
          val objectToString = "# ${this::class.qualifiedName}@${Integer.toHexString(hashCode())}"
          if (trimmedSubjectString.startsWith(objectToString)) {
            trimmedSubjectString.replaceFirst(Pattern.quote(objectToString), "").trim()
          } else {
            subjectString
          }
        }
      }
    }

  return subjectString.ifEmpty { "[empty proto]" }
}

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

data class DifferencePathPair<T : Difference>(val path: List<PathComponent>, val difference: T)

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
      differences.withPushedPathComponent({ PathComponent.Field(key) }) {
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
    differences.withPushedPathComponent({ PathComponent.ListIndex(it) }) {
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
  private val path = mutableListOf<PathComponent>()

  val size: Int by differences::size

  fun toList(): List<DifferencePathPair<*>> = differences.toList()

  fun pushPathComponent(pathComponent: PathComponent) {
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
      append(differences.single().run { "$path=$difference" })
    } else {
      append("${differences.size} differences:")
      differences.forEachIndexed { index, (path, difference) ->
        append('\n').append(index + 1).append(": ").append(path).append('=').append(difference)
      }
    }
  }
}

private inline fun <T> DifferenceAccumulator?.withPushedPathComponent(
  pathComponent: () -> PathComponent,
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

/**
 * Creates and returns a [Matcher] that can be used with kotest assertions for verifying that a
 * proto Value is equal to the given proto Value.
 */
fun beEqualTo(
  other: Value?,
  valuePrinter: (Value) -> String = { it.print().value }
): Matcher<Value?> = neverNullMatcher { value ->
  if (other === null) {
    MatcherResult(
      false,
      { "${valuePrinter(value)} should be null" },
      { TODO("should not get here (error code: r2kap3te33)") }
    )
  } else {
    MatcherResult(
      valueFastEqual(value, other),
      {
        "${valuePrinter(value)} should be equal to ${valuePrinter(other)}, " +
          "but found ${valueDiff(value, other)}"
      },
      { "${valuePrinter(value)} should not be equal to ${valuePrinter(other)}" }
    )
  }
}

fun Struct.deepCopy(): Struct =
  Struct.newBuilder()
    .also { builder ->
      fieldsMap.entries.forEach { (key, value) -> builder.putFields(key, value.deepCopy()) }
    }
    .build()

fun ListValue.deepCopy(): ListValue =
  ListValue.newBuilder()
    .also { builder -> valuesList.forEach { builder.addValues(it.deepCopy()) } }
    .build()

fun Value.deepCopy(): Value =
  Value.newBuilder().let { builder ->
    when (kindCase) {
      Value.KindCase.KIND_NOT_SET -> {}
      Value.KindCase.NULL_VALUE -> builder.setNullValue(NullValue.NULL_VALUE)
      Value.KindCase.NUMBER_VALUE -> builder.setNumberValue(numberValue)
      Value.KindCase.STRING_VALUE -> builder.setStringValue(stringValue)
      Value.KindCase.BOOL_VALUE -> builder.setBoolValue(boolValue)
      Value.KindCase.STRUCT_VALUE -> builder.setStructValue(structValue.deepCopy())
      Value.KindCase.LIST_VALUE -> builder.setListValue(listValue.deepCopy())
    }
    builder.build()
  }

fun Boolean.toValueProto(): Value = Value.newBuilder().setBoolValue(this).build()

fun String.toValueProto(): Value = Value.newBuilder().setStringValue(this).build()

fun Double.toValueProto(): Value = Value.newBuilder().setNumberValue(this).build()

fun Struct.toValueProto(): Value = Value.newBuilder().setStructValue(this).build()

fun ListValue.toValueProto(): Value = Value.newBuilder().setListValue(this).build()

fun Iterable<Value>.toValueProto(): Value = toListValue().toValueProto()

fun Iterable<Value>.toListValue(): ListValue = ListValue.newBuilder().addAllValues(this).build()

fun PropertyContext.structWithValues(
  values: Collection<Value>,
  structKey: Arb<String> = @OptIn(DelicateKotest::class) Arb.proto.structKey().distinct()
): Struct =
  Struct.newBuilder().let { structBuilder ->
    values.forEach { structBuilder.putFields(structKey.bind(), it) }
    structBuilder.build()
  }

fun Struct.walk(visit: (path: List<PathComponent>, value: Value) -> Unit): Unit =
  toValueProto().walk(visit)

fun ListValue.walk(visit: (path: List<PathComponent>, value: Value) -> Unit): Unit =
  toValueProto().walk(visit)

fun Value.walk(visit: (path: List<PathComponent>, value: Value) -> Unit): Unit =
  fold(Unit) { _, path, value -> visit(path, value) }

fun Struct.allDescendants(): List<Value> = toValueProto().allDescendants()

fun ListValue.allDescendants(): List<Value> = toValueProto().allDescendants()

fun Value.allDescendants(): List<Value> = buildList {
  walk { path, value ->
    if (path.isNotEmpty()) {
      add(value)
    }
  }
}

fun Struct.allDescendantPaths(): List<List<PathComponent>> = toValueProto().allDescendantPaths()

fun ListValue.allDescendantPaths(): List<List<PathComponent>> = toValueProto().allDescendantPaths()

fun Value.allDescendantPaths(): List<List<PathComponent>> = buildList {
  walk { path, _ ->
    if (path.isNotEmpty()) {
      add(path)
    }
  }
}

fun <R> Struct.fold(
  initial: R,
  folder: (foldedValue: R, path: List<PathComponent>, value: Value) -> R,
): R = toValueProto().fold(initial, folder)

fun <R> ListValue.fold(
  initial: R,
  folder: (foldedValue: R, path: List<PathComponent>, value: Value) -> R,
): R = toValueProto().fold(initial, folder)

fun <R> Value.fold(
  initial: R,
  folder: (foldedValue: R, path: List<PathComponent>, value: Value) -> R,
): R = foldValue(this, initial, folder)

fun <R> foldValue(
  rootValue: Value,
  initial: R,
  folder: (foldedValue: R, path: List<PathComponent>, value: Value) -> R
): R {
  var foldedValue = initial
  val queue = mutableListOf<Pair<List<PathComponent>, Value>>(Pair(emptyList(), rootValue))

  while (queue.isNotEmpty()) {
    val (path, value) = queue.removeFirst()
    foldedValue = folder(foldedValue, path, value)

    if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
      val childPath = path.toMutableList()
      childPath.add(PathComponent.ListIndex(-1))
      value.structValue.fieldsMap.entries.forEach { (key, value) ->
        childPath[childPath.lastIndex] = PathComponent.Field(key)
        queue.add(childPath.toList() to value)
      }
    } else if (value.kindCase == Value.KindCase.LIST_VALUE) {
      val childPath = path.toMutableList()
      childPath.add(PathComponent.ListIndex(-1))
      value.listValue.valuesList.forEachIndexed { index, value ->
        childPath[childPath.lastIndex] = PathComponent.ListIndex(index)
        queue.add(childPath.toList() to value)
      }
    }
  }

  return foldedValue
}

fun Struct.map(
  path: MutableList<PathComponent> = mutableListOf(),
  mapper: (path: List<PathComponent>, value: Value) -> Value?
): Struct =
  Struct.newBuilder().let { builder ->
    fieldsMap.entries.forEach { (key, value) ->
      path.add(PathComponent.Field(key))

      val mappedValue =
        mapper(path.toList(), value)?.let {
          when (it.kindCase) {
            Value.KindCase.STRUCT_VALUE -> it.structValue.map(path, mapper).toValueProto()
            Value.KindCase.LIST_VALUE -> it.listValue.map(path, mapper).toValueProto()
            else -> it
          }
        }

      path.removeLast()

      if (mappedValue !== null) {
        builder.putFields(key, mappedValue)
      }
    }
    builder.build()
  }

fun ListValue.map(
  path: MutableList<PathComponent> = mutableListOf(),
  mapper: (path: List<PathComponent>, value: Value) -> Value?
): ListValue =
  ListValue.newBuilder().let { builder ->
    valuesList.forEachIndexed { index, value ->
      path.add(PathComponent.ListIndex(index))

      val mappedValue =
        mapper(path.toList(), value)?.let {
          when (it.kindCase) {
            Value.KindCase.STRUCT_VALUE -> it.structValue.map(path, mapper).toValueProto()
            Value.KindCase.LIST_VALUE -> it.listValue.map(path, mapper).toValueProto()
            else -> it
          }
        }

      path.removeLast()

      if (mappedValue !== null) {
        builder.addValues(mappedValue)
      }
    }

    builder.build()
  }

fun Struct.withRandomlyInsertedValues(
  values: List<Value>,
  random: Random,
  generateKey: () -> String
): Struct {
  val structAndListPaths = buildList {
    walk { path, value ->
      when (value.kindCase) {
        Value.KindCase.STRUCT_VALUE,
        Value.KindCase.LIST_VALUE -> add(path)
        else -> {}
      }
    }
  }

  val insertions = List(values.size) { structAndListPaths.random(random) to values[it] }

  val rootStruct =
    toBuilder().let { rootStructBuilder ->
      insertions
        .filter { it.first.isEmpty() }
        .map { it.second }
        .forEach { value ->
          while (true) {
            val key = generateKey()
            if (!rootStructBuilder.containsFields(key)) {
              rootStructBuilder.putFields(key, value)
              break
            }
          }
        }
      rootStructBuilder.build()
    }

  return rootStruct.map { path, value ->
    val curInsertions = insertions.filter { it.first == path }.map { it.second }
    if (curInsertions.isEmpty()) {
      value
    } else if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
      value.structValue
        .toBuilder()
        .also { structBuilder ->
          curInsertions.forEach { valueToInsert ->
            while (true) {
              val key = generateKey()
              if (!structBuilder.containsFields(key)) {
                structBuilder.putFields(key, valueToInsert)
                break
              }
            }
          }
        }
        .build()
        .toValueProto()
    } else if (value.kindCase == Value.KindCase.LIST_VALUE) {
      val newValuesList = value.listValue.valuesList.toMutableList()
      newValuesList.addAll(curInsertions)
      newValuesList.shuffle(random)
      newValuesList.toValueProto()
    } else {
      throw IllegalStateException("should never get here value=$value [ywwmyjnpwa]")
    }
  }
}
