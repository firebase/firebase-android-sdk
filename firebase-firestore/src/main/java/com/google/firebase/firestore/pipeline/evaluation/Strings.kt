// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:JvmName("Strings")

package com.google.firebase.firestore.pipeline.evaluation

import android.icu.lang.UCharacter.isLowerCase
import android.icu.lang.UCharacter.isUpperCase
import android.icu.lang.UCharacter.toLowerCase
import android.icu.lang.UCharacter.toUpperCase
import android.os.Build
import com.google.common.base.CharMatcher
import com.google.common.collect.ImmutableList
import com.google.common.math.IntMath
import com.google.common.primitives.Ints
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.util.RegexUtils
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeCase
import com.google.protobuf.ByteString
import com.google.re2j.Pattern
import kotlin.math.max
import kotlin.math.min

// === String Functions ===

internal val evaluateStrConcat = variadicFunction { strings: List<String> ->
  EvaluateResult.string(buildString { strings.forEach(::append) })
}

internal val evaluateStrContains = binaryFunction { value: String, substring: String ->
  EvaluateResult.boolean(value.contains(substring))
}

internal val evaluateStartsWith = binaryFunction { value: String, prefix: String ->
  EvaluateResult.boolean(value.startsWith(prefix))
}

internal val evaluateEndsWith = binaryFunction { value: String, suffix: String ->
  EvaluateResult.boolean(value.endsWith(suffix))
}

internal val evaluateByteLength =
  unaryFunction(
    { b: ByteString -> EvaluateResult.long(b.size()) },
    { s: String -> EvaluateResult.long(s.toByteArray(Charsets.UTF_8).size) }
  )

internal val evaluateCharLength = unaryFunction { s: String ->
  // For strings containing only BMP characters, #length() and #codePointCount() will return
  // the same value. Once we exceed the first plane, #length() will not provide the correct
  // result. It is safe to use #length() within #codePointCount() because beyond the BMP,
  // #length() always yields a larger number.
  EvaluateResult.long(s.codePointCount(0, s.length))
}

private fun isUpperCaseImpl(c: Int): Boolean =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    isUpperCase(c)
  } else {
    Char(c).isUpperCase()
  }

private fun toLowerCaseImpl(c: Int): Int =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    toLowerCase(c)
  } else {
    Char(c).lowercaseChar().code
  }

internal val evaluateToLowercase = unaryFunction { value: Value ->
  when (value.valueTypeCase) {
    ValueTypeCase.STRING_VALUE -> EvaluateResult.string(value.stringValue.lowercase())
    ValueTypeCase.BYTES_VALUE -> {
      val bytes: ByteArray = value.bytesValue.toByteArray()
      for (i in bytes.indices) {
        bytes[i] =
          if (isUpperCaseImpl(bytes[i].toInt())) toLowerCaseImpl(bytes[i].toInt()).toByte()
          else bytes[i]
      }
      EvaluateResult.value(encodeValue(bytes))
    }
    else -> EvaluateResultError
  }
}

private fun isLowerCaseImpl(c: Int): Boolean =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    isLowerCase(c)
  } else {
    Char(c).isUpperCase()
  }

private fun toUpperCaseImpl(c: Int): Int =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    toUpperCase(c)
  } else {
    Char(c).uppercaseChar().code
  }

internal val evaluateToUppercase = unaryFunction { value: Value ->
  when (value.valueTypeCase) {
    ValueTypeCase.STRING_VALUE -> EvaluateResult.string(value.stringValue.uppercase())
    ValueTypeCase.BYTES_VALUE -> {
      val bytes: ByteArray = value.bytesValue.toByteArray()
      for (i in bytes.indices) {
        bytes[i] =
          if (isLowerCaseImpl(bytes[i].toInt())) toUpperCaseImpl(bytes[i].toInt()).toByte()
          else bytes[i]
      }
      EvaluateResult.value(encodeValue(bytes))
    }
    else -> EvaluateResultError
  }
}

internal val evaluateReverse = unaryFunction { value: Value ->
  when (value.valueTypeCase) {
    ValueTypeCase.STRING_VALUE -> EvaluateResult.string(stringReverse(value.stringValue))
    ValueTypeCase.BYTES_VALUE ->
      EvaluateResult.value(encodeValue(Blob.fromBytes(bytesReverse(value.bytesValue))))
    ValueTypeCase.ARRAY_VALUE ->
      EvaluateResult.value(encodeValue(value.arrayValue.valuesList.reversed()))
    else -> EvaluateResultError
  }
}

internal val evaluateStringReverse = unaryFunction { value: Value ->
  when (value.valueTypeCase) {
    ValueTypeCase.STRING_VALUE -> EvaluateResult.string(stringReverse(value.stringValue))
    ValueTypeCase.BYTES_VALUE ->
      EvaluateResult.value(encodeValue(Blob.fromBytes(bytesReverse(value.bytesValue))))
    else -> EvaluateResultError
  }
}

private fun stringReverse(input: String): String {
  val reversed = java.lang.StringBuilder()
  var curIndex: Int = input.length
  while (curIndex > 0) {
    curIndex = input.offsetByCodePoints(curIndex, -1)
    reversed.append(Character.toChars(input.codePointAt(curIndex)))
  }
  return reversed.toString()
}

private fun bytesReverse(input: ByteString): ByteArray {
  val bytes = input.toByteArray()

  for (i in 0 until bytes.size / 2) {
    val tmp = bytes[i]
    bytes[i] = bytes[bytes.size - i - 1]
    bytes[bytes.size - i - 1] = tmp
  }
  return bytes
}

internal val evaluateSplit = notImplemented // TODO: Does not exist in expressions.kt yet.

private fun getIntegerOrElse(value: EvaluateResult): Long? {
  if (!value.isSuccess) return null
  if (value.value?.valueTypeCase != ValueTypeCase.INTEGER_VALUE) return null
  return value.value?.integerValue
}

internal val evaluateSubstring = ternaryLazyFunction { strFn, startFn, lengthFn ->
  var start = getIntegerOrElse(startFn()) ?: return@ternaryLazyFunction EvaluateResultError
  val length = getIntegerOrElse(lengthFn()) ?: return@ternaryLazyFunction EvaluateResultError

  if (length < 0) {
    return@ternaryLazyFunction EvaluateResultError
  }

  val str = strFn().value
  when (str?.valueTypeCase) {
    ValueTypeCase.STRING_VALUE -> {
      val text = str.stringValue
      // Rephrasing negative position to an equivalent positive value.
      if (start < 0) {
        start = max(0, text.codePointCount(0, text.length) + start)
      }

      val codePointCount = text.codePointCount(0, text.length)

      if (start >= codePointCount) {
        return@ternaryLazyFunction EvaluateResult.string("")
      }

      val substring = StringBuilder()
      var curIndex = text.offsetByCodePoints(0, min(start, Int.MAX_VALUE.toLong()).toInt())
      for (i in 0 until length) {
        if (curIndex >= text.length) {
          return@ternaryLazyFunction EvaluateResult.string(substring.toString())
        }

        substring.append(Character.toChars(text.codePointAt(curIndex)))
        curIndex = text.offsetByCodePoints(curIndex, 1)
      }

      return@ternaryLazyFunction EvaluateResult.string(substring.toString())
    }
    ValueTypeCase.BYTES_VALUE -> {
      val bytes = str.bytesValue
      val bytesCount = bytes.size() - 1
      if (start < 0) {
        // Adding 1 since position is inclusive.
        start = max(0, bytesCount + start + 1)
      }

      if (bytesCount < start) {
        return@ternaryLazyFunction EvaluateResult.value(encodeValue(ByteArray(0)))
      }

      val end =
        min(
          Int.MAX_VALUE,
          min(
            IntMath.saturatedAdd(Ints.saturatedCast(start), Ints.saturatedCast(length)),
            bytesCount + 1
          )
        )
      return@ternaryLazyFunction EvaluateResult.value(
        encodeValue(Blob.fromByteString(bytes.substring(start.toInt(), end.toInt())))
      )
    }
    else -> return@ternaryLazyFunction EvaluateResultError
  }
}

internal val evaluateTrim = unaryFunction { value: Value ->
  when (value.valueTypeCase) {
    ValueTypeCase.STRING_VALUE ->
      EvaluateResult.string(CharMatcher.whitespace().trimFrom(value.stringValue))
    ValueTypeCase.BYTES_VALUE -> {
      val bytes = value.bytesValue
      var startIndex = 0
      while (
        startIndex < bytes.size() && Character.isWhitespace(bytes.byteAt(startIndex).toInt())
      ) {
        startIndex++
      }

      var endIndex: Int = bytes.size() - 1
      while (endIndex >= startIndex && Character.isWhitespace(bytes.byteAt(endIndex).toInt())) {
        endIndex--
      }

      if (startIndex > endIndex) {
        EvaluateResult.value(encodeValue(ByteString.EMPTY.toByteArray()))
      } else {
        EvaluateResult.value(encodeValue(bytes.substring(startIndex, endIndex + 1).toByteArray()))
      }
    }
    else -> EvaluateResultError
  }
}

internal val evaluateLTrim = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateRTrim = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateReplaceAll = notImplemented // TODO: Does not exist in backend yet.

internal val evaluateReplaceFirst = notImplemented // TODO: Does not exist in backend yet.

internal val evaluateRegexContains =
  binaryFunctionConstructorType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue
  ) {
    ({ value: String, patternString: String ->
      val pattern =
        try {
          Pattern.compile(patternString)
        } catch (_: Exception) {
          null
        }
      if (pattern == null) EvaluateResultError
      else EvaluateResult.boolean(pattern.matcher(value).find())
    })
  }

internal val evaluateRegexMatch =
  binaryFunctionConstructorType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue
  ) {
    ({ value: String, patternString: String ->
      val pattern =
        try {
          Pattern.compile(patternString)
        } catch (_: Exception) {
          null
        }
      if (pattern == null) EvaluateResultError else EvaluateResult.boolean(pattern.matches(value))
    })
  }

internal val evaluateRegexFind =
  binaryFunctionConstructorType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
  ) {
    ({ value: String, patternString: String ->
      val pattern =
        try {
          Pattern.compile(patternString)
        } catch (_: Exception) {
          null
        }

      if (pattern == null) EvaluateResultError
      else EvaluateResult.value(RegexUtils.handleMatch(pattern.matcher(value.toByteArray())))
    })
  }

internal val evaluateRegexFindAll =
  binaryFunctionConstructorType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
  ) {
    ({ value: String, patternString: String ->
      val pattern =
        try {
          Pattern.compile(patternString)
        } catch (_: Exception) {
          null
        }

      if (pattern == null) {
        EvaluateResultError
      } else {
        val builder = ImmutableList.builder<Value>()
        val matcher = pattern.matcher(value.toByteArray())
        while (matcher.find()) {
          builder.add(RegexUtils.handleMatch(matcher))
        }
        EvaluateResult.list(builder.build())
      }
    })
  }

internal val evaluateLike =
  binaryFunctionConstructorType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue
  ) {
    ({ value: String, like: String ->
      val pattern =
        try {
          Pattern.compile(likeToRegex(like))
        } catch (_: Exception) {
          null
        }
      if (pattern == null) EvaluateResultError else EvaluateResult.boolean(pattern.matches(value))
    })
  }

private fun likeToRegex(like: String): String = buildString {
  var escape = false
  for (c in like) {
    if (escape) {
      escape = false
      when (c) {
        '\\' -> append("\\")
        else -> append(c)
      }
    } else
      when (c) {
        '\\' -> escape = true
        '_' -> append('.')
        '%' -> append(".*")
        '.' -> append("\\.")
        '*' -> append("\\*")
        '?' -> append("\\?")
        '+' -> append("\\+")
        '^' -> append("\\^")
        '$' -> append("\\$")
        '|' -> append("\\|")
        '(' -> append("\\(")
        ')' -> append("\\)")
        '[' -> append("\\[")
        ']' -> append("\\]")
        '{' -> append("\\{")
        '}' -> append("\\}")
        else -> append(c)
      }
  }
  if (escape) {
    throw Exception("LIKE pattern ends in backslash")
  }
}
