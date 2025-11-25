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
import com.google.protobuf.MessageLite
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.print.print
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import java.util.regex.Pattern

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
      structDiff(value, other, null),
      {
        val differences = DifferenceAccumulator().also { structDiff(value, other, it) }
        "${structPrinter(value)} should be equal to ${structPrinter(other)}, but found $differences"
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

sealed interface Difference {
  data class KindCase(val kindCase1: Value.KindCase, val kindCase2: Value.KindCase) : Difference
  data class BoolValue(val value1: Boolean, val value2: Boolean) : Difference
  data class NumberValue(val value1: Double, val value2: Double) : Difference
  data class StringValue(val value1: String, val value2: String) : Difference
  data class StructFieldCount(val count1: Int, val count2: Int) : Difference
  data class StructMissingKey(val key: String, val kindCase: Value.KindCase) : Difference
  data class StructUnexpectedKey(val key: String, val kindCase: Value.KindCase) : Difference
  data class ListSize(val size1: Int, val size2: Int) : Difference
  data class ListMissingElement(val index: Int, val kindCase: Value.KindCase) : Difference
  data class ListUnexpectedElement(val index: Int, val kindCase: Value.KindCase) : Difference
}

fun structDiff(struct1: Struct, struct2: Struct, differences: DifferenceAccumulator?): Boolean {
  if (struct1 === struct2) {
    return true
  }

  var isEqual = true

  if (struct1.fieldsCount != struct2.fieldsCount) {
    if (differences === null) {
      return false
    }
    isEqual = false
    differences.add(Difference.StructFieldCount(struct1.fieldsCount, struct2.fieldsCount))
  }

  val map1 = struct1.fieldsMap
  val map2 = struct2.fieldsMap

  map1.entries.forEach { (key, value) ->
    if (key !in map2) {
      if (differences === null) {
        return false
      }
      isEqual = false
      differences.add(Difference.StructMissingKey(key, value.kindCase))
    } else {
      val diffResult =
        differences.withPushedPathComponent({ "\"$key\"" }) {
          valueDiff(value, map2[key]!!, differences)
        }
      if (differences === null && !diffResult) {
        return false
      }
    }
  }

  map2.entries.forEach { (key, value) ->
    if (key !in map1) {
      if (differences === null) {
        return false
      }
      isEqual = false
      differences.add(Difference.StructUnexpectedKey(key, value.kindCase))
    }
  }

  return isEqual
}

fun listValueDiff(
  listValue1: ListValue,
  listValue2: ListValue,
  differences: DifferenceAccumulator?
): Boolean {
  if (listValue1 === listValue2) {
    return true
  }

  var isEqual = true

  if (listValue1.valuesCount != listValue2.valuesCount) {
    if (differences === null) {
      return false
    }
    isEqual = false
    differences.add(Difference.ListSize(listValue1.valuesCount, listValue2.valuesCount))
  }

  repeat(listValue1.valuesCount.coerceAtMost(listValue2.valuesCount)) {
    val value1 = listValue1.getValues(it)
    val value2 = listValue2.getValues(it)
    val diffResult =
      differences.withPushedPathComponent({ "[$it]" }) { valueDiff(value1, value2, differences) }
    if (differences === null && !diffResult) {
      return false
    }
  }

  if (listValue1.valuesCount > listValue2.valuesCount) {
    checkNotNull(differences)
    (listValue2.valuesCount until listValue1.valuesCount).forEach {
      isEqual = false
      differences.add(Difference.ListMissingElement(it, listValue1.getValues(it).kindCase))
    }
  } else if (listValue1.valuesCount < listValue2.valuesCount) {
    checkNotNull(differences)
    (listValue1.valuesCount until listValue2.valuesCount).forEach {
      isEqual = false
      differences.add(Difference.ListUnexpectedElement(it, listValue2.getValues(it).kindCase))
    }
  }

  return isEqual
}

class DifferenceAccumulator {
  private val differences = mutableListOf<DifferenceInfo>()
  private val path = mutableListOf<String>()

  val size: Int by differences::size

  fun pushPathComponent(pathComponent: String) {
    path.add(pathComponent)
  }

  fun popPathComponent() {
    path.removeAt(path.lastIndex)
  }

  fun add(difference: Difference) {
    val key = path.joinToString(".")
    differences.add(DifferenceInfo(key, difference))
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

  data class DifferenceInfo(val path: String, val difference: Difference)
}

private inline fun <T> DifferenceAccumulator?.withPushedPathComponent(
  pathComponent: () -> String,
  block: () -> T
): T {
  this?.pushPathComponent(pathComponent())
  return try {
    block()
  } finally {
    this?.popPathComponent()
  }
}

private inline fun DifferenceAccumulator.addIf(predicate: Boolean, block: () -> Difference) {
  if (predicate) {
    add(block())
  }
}

fun valueDiff(value1: Value, value2: Value, differences: DifferenceAccumulator?): Boolean {
  if (value1 === value2) {
    return true
  }

  if (value1.kindCase != value2.kindCase) {
    differences?.add(Difference.KindCase(value1.kindCase, value2.kindCase))
    return false
  }
  return when (value1.kindCase) {
    Value.KindCase.KIND_NOT_SET -> true
    Value.KindCase.NULL_VALUE -> true
    Value.KindCase.STRUCT_VALUE -> structDiff(value1.structValue, value2.structValue, differences)
    Value.KindCase.LIST_VALUE -> listValueDiff(value1.listValue, value2.listValue, differences)
    Value.KindCase.BOOL_VALUE ->
      (value1.boolValue == value2.boolValue).also {
        differences?.addIf(!it, { Difference.BoolValue(value1.boolValue, value2.boolValue) })
      }
    Value.KindCase.NUMBER_VALUE ->
      numberValuesEqual(value1.numberValue, value2.numberValue).also {
        differences?.addIf(!it, { Difference.NumberValue(value1.numberValue, value2.numberValue) })
      }
    Value.KindCase.STRING_VALUE ->
      (value1.stringValue == value2.stringValue).also {
        differences?.addIf(!it, { Difference.StringValue(value1.stringValue, value2.stringValue) })
      }
  }
}

private fun numberValuesEqual(value1: Double, value2: Double): Boolean =
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
      valueDiff(value, other, null),
      {
        val differences = DifferenceAccumulator().also { valueDiff(value, other, it) }
        "${valuePrinter(value)} should be equal to ${valuePrinter(other)}, but found $differences"
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

fun Struct.valuesRecursive(dest: MutableList<Value> = mutableListOf()): List<Value> =
  dest.apply { fieldsMap.values.forEach { it.valuesRecursive(dest) } }

fun ListValue.valuesRecursive(dest: MutableList<Value> = mutableListOf()): List<Value> =
  dest.apply { valuesList.forEach { it.valuesRecursive(dest) } }

fun Value.valuesRecursive(dest: MutableList<Value> = mutableListOf()): List<Value> {
  dest.add(this)
  if (kindCase == Value.KindCase.STRUCT_VALUE) {
    structValue.valuesRecursive(dest)
  } else if (kindCase == Value.KindCase.LIST_VALUE) {
    listValue.valuesRecursive(dest)
  }
  return dest
}
