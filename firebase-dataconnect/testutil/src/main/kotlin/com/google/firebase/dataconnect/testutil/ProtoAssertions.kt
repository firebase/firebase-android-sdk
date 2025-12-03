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

import com.google.protobuf.MessageLite
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
      "${value::class.qualifiedName} ${value.print().value} should not be equal to " +
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
