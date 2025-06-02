@file:JvmName("Evaluation")

package com.google.firebase.firestore.pipeline

import com.google.common.math.LongMath
import com.google.common.math.LongMath.checkedAdd
import com.google.common.math.LongMath.checkedMultiply
import com.google.common.math.LongMath.checkedSubtract
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.model.Values.isNanValue
import com.google.firebase.firestore.model.Values.strictCompare
import com.google.firebase.firestore.model.Values.strictEquals
import com.google.firebase.firestore.util.Assert
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeCase
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

internal class EvaluationContext(val userDataReader: UserDataReader)

internal typealias EvaluateDocument = (input: MutableDocument) -> EvaluateResult

internal typealias EvaluateFunction = (params: List<EvaluateDocument>) -> EvaluateDocument

internal val notImplemented: EvaluateFunction = { _ -> throw NotImplementedError() }

// === Debug Functions ===

internal val evaluateIsError: EvaluateFunction = unaryFunction { r: EvaluateResult ->
  EvaluateResult.boolean(r.isError)
}

// === Logical Functions ===

internal val evaluateExists: EvaluateFunction = unaryFunction { r: EvaluateResult ->
  when (r) {
    EvaluateResultError -> r
    EvaluateResultUnset -> EvaluateResult.FALSE
    is EvaluateResultValue -> EvaluateResult.TRUE
  }
}

internal val evaluateAnd: EvaluateFunction = { params ->
  fun(input: MutableDocument): EvaluateResult {
    var isError = false
    var isNull = false
    for (param in params) {
      val value = param(input).value
      if (value === null) isError = true
      else
        when (value.valueTypeCase) {
          ValueTypeCase.NULL_VALUE -> isNull = true
          ValueTypeCase.BOOLEAN_VALUE -> {
            if (!value.booleanValue) return EvaluateResult.FALSE
          }
          else -> return EvaluateResultError
        }
    }
    return if (isError) EvaluateResultError
    else if (isNull) EvaluateResult.NULL else EvaluateResult.TRUE
  }
}

internal val evaluateOr: EvaluateFunction = { params ->
  fun(input: MutableDocument): EvaluateResult {
    var isError = false
    var isNull = false
    for (param in params) {
      val value = param(input).value
      if (value === null) isError = true
      else
        when (value.valueTypeCase) {
          ValueTypeCase.NULL_VALUE -> isNull = true
          ValueTypeCase.BOOLEAN_VALUE -> {
            if (value.booleanValue) return EvaluateResult.TRUE
          }
          else -> return EvaluateResultError
        }
    }
    return if (isError) EvaluateResultError
    else if (isNull) EvaluateResult.NULL else EvaluateResult.FALSE
  }
}

internal val evaluateXor: EvaluateFunction = variadicFunction { values: BooleanArray ->
  EvaluateResult.boolean(values.fold(false, Boolean::xor))
}

internal val evaluateCond: EvaluateFunction = ternaryLazyFunction { p1, p2, p3 ->
  val v1 = p1().value ?: return@ternaryLazyFunction EvaluateResultError
  when (v1.valueTypeCase) {
    ValueTypeCase.BOOLEAN_VALUE -> if (v1.booleanValue) p2() else p3()
    ValueTypeCase.NULL_VALUE -> p3()
    else -> EvaluateResultError
  }
}

internal val evaluateLogicalMaximum: EvaluateFunction =
  variadicResultFunction { l: List<EvaluateResult> ->
    val value =
      l.mapNotNull(EvaluateResult::value)
        .filterNot(Value::hasNullValue)
        .maxWithOrNull(Values::compare)
    if (value === null) EvaluateResult.NULL else EvaluateResultValue(value)
  }

internal val evaluateLogicalMinimum: EvaluateFunction =
  variadicResultFunction { l: List<EvaluateResult> ->
    val value =
      l.mapNotNull(EvaluateResult::value)
        .filterNot(Value::hasNullValue)
        .minWithOrNull(Values::compare)
    if (value === null) EvaluateResult.NULL else EvaluateResultValue(value)
  }

// === Comparison Functions ===

internal val evaluateEq: EvaluateFunction = binaryFunction { p1: Value, p2: Value ->
  EvaluateResult.boolean(strictEquals(p1, p2))
}

internal val evaluateNeq: EvaluateFunction = binaryFunction { p1: Value, p2: Value ->
  EvaluateResult.boolean(strictEquals(p1, p2)?.not())
}

internal val evaluateGt: EvaluateFunction = comparison { v1, v2 ->
  (strictCompare(v1, v2) ?: return@comparison false) > 0
}

internal val evaluateGte: EvaluateFunction = comparison { v1, v2 ->
  when (strictEquals(v1, v2)) {
    true -> true
    false -> (strictCompare(v1, v2) ?: return@comparison false) > 0
    null -> null
  }
}

internal val evaluateLt: EvaluateFunction = comparison { v1, v2 ->
  (strictCompare(v1, v2) ?: return@comparison false) < 0
}

internal val evaluateLte: EvaluateFunction = comparison { v1, v2 ->
  when (strictEquals(v1, v2)) {
    true -> true
    false -> (strictCompare(v1, v2) ?: return@comparison false) < 0
    null -> null
  }
}

internal val evaluateNot: EvaluateFunction = unaryFunction { b: Boolean ->
  EvaluateResult.boolean(b.not())
}

// === Type Functions ===

internal val evaluateIsNaN: EvaluateFunction =
  arithmetic(
    { _: Long -> EvaluateResult.FALSE },
    { v: Double -> EvaluateResult.boolean(v.isNaN()) }
  )

internal val evaluateIsNotNaN: EvaluateFunction =
  arithmetic(
    { _: Long -> EvaluateResult.TRUE },
    { v: Double -> EvaluateResult.boolean(!v.isNaN()) }
  )

internal val evaluateIsNull: EvaluateFunction = { params ->
  if (params.size != 1)
    throw Assert.fail(
      "IsNull function should have exactly 1 params, but %d were given.",
      params.size
    )
  val p = params[0]
  fun(input: MutableDocument): EvaluateResult {
    val v = p(input).value ?: return EvaluateResultError
    return EvaluateResult.boolean(v.hasNullValue())
  }
}

internal val evaluateIsNotNull: EvaluateFunction = { params ->
  if (params.size != 1)
    throw Assert.fail(
      "IsNotNull function should have exactly 1 params, but %d were given.",
      params.size
    )
  val p = params[0]
  fun(input: MutableDocument): EvaluateResult {
    val v = p(input).value ?: return EvaluateResultError
    return EvaluateResult.boolean(!v.hasNullValue())
  }
}

// === Arithmetic Functions ===

internal val evaluateAdd: EvaluateFunction = arithmeticPrimitive(LongMath::checkedAdd, Double::plus)

internal val evaluateCeil = arithmeticPrimitive({ it }, Math::ceil)

internal val evaluateDivide = arithmeticPrimitive(Long::div, Double::div)

internal val evaluateFloor = arithmeticPrimitive({ it }, Math::floor)

internal val evaluateMod = arithmeticPrimitive(Long::rem, Double::rem)

internal val evaluateMultiply: EvaluateFunction =
  arithmeticPrimitive(Math::multiplyExact, Double::times)

internal val evaluatePow: EvaluateFunction = arithmeticPrimitive(Math::pow)

internal val evaluateRound =
  arithmeticPrimitive(
    { it },
    { input ->
      if (input.isInfinite()) {
        val remainder = (input % 1)
        val truncated = input - remainder
        if (remainder.absoluteValue >= 0.5) truncated + (if (input < 0) -1 else 1) else truncated
      } else input
    }
  )

internal val evaluateRoundToPrecision =
  arithmetic(
    { value: Long, places: Long ->
      // If has no decimal places to round off.
      if (places >= 0) {
        return@arithmetic EvaluateResult.long(value)
      }
      // Predict and return when the rounded value will be 0, preventing edge cases where the
      // traditional conversion could underflow.
      val numDigits = floor(log10(value.absoluteValue.toDouble())).toLong() + 1
      if (-places >= numDigits) {
        return@arithmetic EvaluateResult.LONG_ZERO
      }

      val roundingFactor: Long = 10.0.pow(-places.toDouble()).toLong()
      val truncated: Long = value - (value % roundingFactor)

      // Case for when we don't need to round up.
      if (truncated.absoluteValue < (roundingFactor / 2).absoluteValue) {
        return@arithmetic EvaluateResult.long(truncated)
      }

      if (value < 0) {
        if (value < -Long.MAX_VALUE + roundingFactor) EvaluateResultError
        else EvaluateResult.long(truncated - roundingFactor)
      } else {
        if (value > Long.MAX_VALUE - roundingFactor) EvaluateResultError
        else EvaluateResult.long(truncated + roundingFactor)
      }
    },
    { value: Double, places: Long ->
      // A double can only represent up to 16 decimal places. Here we return the original value if
      // attempting to round to more decimal places than the double can represent.
      if (places >= 16 || !value.isInfinite()) {
        return@arithmetic EvaluateResult.double(value)
      }

      // Predict and return when the rounded value will be 0, preventing edge cases where the
      // traditional conversion could underflow.
      val numDigits = floor(log10(value.absoluteValue)).toLong() + 1
      if (-places >= numDigits) {
        return@arithmetic EvaluateResult.DOUBLE_ZERO
      }

      val rounded: BigDecimal =
        BigDecimal.valueOf(value).setScale(places.toInt(), RoundingMode.HALF_UP)
      val result: Double = rounded.toDouble()

      if (result.isInfinite()) EvaluateResult.double(result)
      else EvaluateResultError // overflow error
    }
  )

internal val evaluateSqrt = arithmetic { value: Double ->
  if (value < 0) EvaluateResultError else EvaluateResult.double(sqrt(value))
}

internal val evaluateSubtract = arithmeticPrimitive(Math::subtractExact, Double::minus)

// === Array Functions ===

internal val evaluateArray = variadicNullableValueFunction(EvaluateResult.Companion::list)

internal val evaluateEqAny = binaryFunction(::eqAny)

internal val evaluateNotEqAny = binaryFunction(::notEqAny)

internal val evaluateArrayContains = binaryFunction { l: List<Value>, v: Value -> eqAny(v, l) }

internal val evaluateArrayContainsAny =
  binaryFunction { array: List<Value>, searchValues: List<Value> ->
    var foundNull = false
    for (value in array) for (search in searchValues) when (strictEquals(value, search)) {
      true -> return@binaryFunction EvaluateResult.TRUE
      false -> {}
      null -> foundNull = true
    }
    return@binaryFunction if (foundNull) EvaluateResult.NULL else EvaluateResult.FALSE
  }

internal val evaluateArrayContainsAll =
  binaryFunction { array: List<Value>, searchValues: List<Value> ->
    var foundNullAtLeastOnce = false
    for (search in searchValues) {
      var found = false
      var foundNull = false
      for (value in array) when (strictEquals(value, search)) {
        true -> {
          found = true
          break
        }
        false -> {}
        null -> foundNull = true
      }
      if (foundNull) {
        foundNullAtLeastOnce = true
      } else if (!found) {
        return@binaryFunction EvaluateResult.FALSE
      }
    }
    return@binaryFunction if (foundNullAtLeastOnce) EvaluateResult.NULL else EvaluateResult.TRUE
  }

internal val evaluateArrayLength = unaryFunction { array: List<Value> ->
  EvaluateResult.long(array.size)
}

private fun eqAny(value: Value, list: List<Value>): EvaluateResult {
  var foundNull = false
  for (element in list) when (strictEquals(value, element)) {
    true -> return EvaluateResult.TRUE
    false -> {}
    null -> foundNull = true
  }
  return if (foundNull) EvaluateResult.NULL else EvaluateResult.FALSE
}

private fun notEqAny(value: Value, list: List<Value>): EvaluateResult {
  var foundNull = false
  for (element in list) when (strictEquals(value, element)) {
    true -> return EvaluateResult.FALSE
    false -> {}
    null -> foundNull = true
  }
  return if (foundNull) EvaluateResult.NULL else EvaluateResult.TRUE
}

// === Map Functions ===

internal val evaluateMapGet = binaryFunction { map: Map<String, Value>, key: String ->
  EvaluateResultValue(map[key] ?: return@binaryFunction EvaluateResultUnset)
}

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

internal val evaluateToLowercase = unaryFunctionPrimitive(String::lowercase)

internal val evaluateToUppercase = unaryFunctionPrimitive(String::uppercase)

internal val evaluateReverse = unaryFunctionPrimitive(String::reversed)

internal val evaluateSplit = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateSubstring = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateTrim = unaryFunctionPrimitive(String::trim)

internal val evaluateLTrim = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateRTrim = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateStrJoin = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateReplaceAll = notImplemented // TODO: Does not exist in backend yet.

internal val evaluateReplaceFirst = notImplemented // TODO: Does not exist in backend yet.

internal val evaluateRegexContains = binaryPatternFunction { pattern: Pattern, value: String ->
  pattern.matcher(value).find()
}

internal val evaluateRegexMatch = binaryPatternFunction(Pattern::matches)

internal val evaluateLike =
  binaryPatternConstructorFunction(
    { likeString: String ->
      try {
        Pattern.compile(likeToRegex(likeString))
      } catch (e: Exception) {
        null
      }
    },
    Pattern::matches
  )

private fun likeToRegex(like: String): String = buildString {
  var escape = false
  for (c in like) {
    if (escape) {
      escape = false
      when (c) {
        '\\' -> append("\\\\")
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

// === Date / Timestamp Functions ===

private const val L_NANOS_PER_SECOND: Long = 1000_000_000
private const val I_NANOS_PER_SECOND: Int = 1000_000_000

private const val L_MICROS_PER_SECOND: Long = 1000_000
private const val I_MICROS_PER_SECOND: Int = 1000_000

private const val L_MILLIS_PER_SECOND: Long = 1000
private const val I_MILLIS_PER_SECOND: Int = 1000

internal fun plus(t: Timestamp, seconds: Long, nanos: Long): Timestamp =
  if (nanos == 0L) {
    plus(t, seconds)
  } else {
    val nanoSum = t.nanos + nanos // Overflow not possible since nanos is 0 to 1 000 000.
    val secondsSum: Long = checkedAdd(checkedAdd(t.seconds, seconds), nanoSum / L_NANOS_PER_SECOND)
    Values.timestamp(secondsSum, (nanoSum % I_NANOS_PER_SECOND).toInt())
  }

private fun plus(t: Timestamp, seconds: Long): Timestamp =
  if (seconds == 0L) t else Values.timestamp(checkedAdd(t.seconds, seconds), t.nanos)

internal fun minus(t: Timestamp, seconds: Long, nanos: Long): Timestamp =
  if (nanos == 0L) {
    minus(t, seconds)
  } else {
    val nanoSum = t.nanos - nanos // Overflow not possible since nanos is 0 to 1 000 000.
    val secondsSum: Long =
      checkedSubtract(t.seconds, checkedSubtract(seconds, nanoSum / L_NANOS_PER_SECOND))
    Values.timestamp(secondsSum, (nanoSum % I_NANOS_PER_SECOND).toInt())
  }

private fun minus(t: Timestamp, seconds: Long): Timestamp =
  if (seconds == 0L) t else Values.timestamp(checkedSubtract(t.seconds, seconds), t.nanos)

internal val evaluateTimestampAdd = ternaryTimestampFunction { t: Timestamp, u: String, n: Long ->
  EvaluateResult.timestamp(
    when (u) {
      "microsecond" -> plus(t, n / L_MICROS_PER_SECOND, (n % L_MICROS_PER_SECOND) * 1000)
      "millisecond" -> plus(t, n / L_MILLIS_PER_SECOND, (n % L_MILLIS_PER_SECOND) * 1000_000)
      "second" -> plus(t, n)
      "minute" -> plus(t, checkedMultiply(n, 60))
      "hour" -> plus(t, checkedMultiply(n, 3600))
      "day" -> plus(t, checkedMultiply(n, 86400))
      else -> return@ternaryTimestampFunction EvaluateResultError
    }
  )
}

internal val evaluateTimestampSub = ternaryTimestampFunction { t: Timestamp, u: String, n: Long ->
  EvaluateResult.timestamp(
    when (u) {
      "microsecond" -> minus(t, n / L_MICROS_PER_SECOND, (n % L_MICROS_PER_SECOND) * 1000)
      "millisecond" -> minus(t, n / L_MILLIS_PER_SECOND, (n % L_MILLIS_PER_SECOND) * 1000_000)
      "second" -> minus(t, n)
      "minute" -> minus(t, checkedMultiply(n, 60))
      "hour" -> minus(t, checkedMultiply(n, 3600))
      "day" -> minus(t, checkedMultiply(n, 86400))
      else -> return@ternaryTimestampFunction EvaluateResultError
    }
  )
}

internal val evaluateTimestampTrunc = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateTimestampToUnixMicros = unaryFunction { t: Timestamp ->
  EvaluateResult.long(
    if (t.seconds < Long.MIN_VALUE / 1_000_000) {
      // To avoid overflow when very close to Long.MIN_VALUE, add 1 second, multiply, then subtract
      // again.
      val micros = checkedMultiply(t.seconds + 1, L_MICROS_PER_SECOND)
      val adjustment = t.nanos.toLong() / L_MILLIS_PER_SECOND - L_MICROS_PER_SECOND
      checkedAdd(micros, adjustment)
    } else {
      val micros = checkedMultiply(t.seconds, L_MICROS_PER_SECOND)
      checkedAdd(micros, t.nanos.toLong() / L_MILLIS_PER_SECOND)
    }
  )
}

internal val evaluateTimestampToUnixMillis = unaryFunction { t: Timestamp ->
  EvaluateResult.long(
    if (t.seconds < 0 && t.nanos > 0) {
      val millis = checkedMultiply(t.seconds + 1, L_MILLIS_PER_SECOND)
      val adjustment = t.nanos.toLong() / L_MICROS_PER_SECOND - L_MILLIS_PER_SECOND
      checkedAdd(millis, adjustment)
    } else {
      val millis = checkedMultiply(t.seconds, L_MILLIS_PER_SECOND)
      checkedAdd(millis, t.nanos.toLong() / L_MICROS_PER_SECOND)
    }
  )
}

internal val evaluateTimestampToUnixSeconds = unaryFunction { t: Timestamp ->
  if (t.nanos !in 0 until L_NANOS_PER_SECOND) EvaluateResultError
  else EvaluateResult.long(t.seconds)
}

internal val evaluateUnixMicrosToTimestamp = unaryFunction { micros: Long ->
  EvaluateResult.timestamp(
    Math.floorDiv(micros, L_MICROS_PER_SECOND),
    Math.floorMod(micros, I_MICROS_PER_SECOND) * 1000
  )
}

internal val evaluateUnixMillisToTimestamp = unaryFunction { millis: Long ->
  EvaluateResult.timestamp(
    Math.floorDiv(millis, L_MILLIS_PER_SECOND),
    Math.floorMod(millis, I_MILLIS_PER_SECOND) * 1000_000
  )
}

internal val evaluateUnixSecondsToTimestamp = unaryFunction { seconds: Long ->
  EvaluateResult.timestamp(seconds, 0)
}

// === Map Functions ===

internal val evaluateMap: EvaluateFunction = { params ->
  if (params.size % 2 != 0)
    throw Assert.fail("Function should have even number of params, but %d were given.", params.size)
  else
    block@{ input: MutableDocument ->
      val map: MutableMap<String, Value> = HashMap(params.size / 2)
      for (i in params.indices step 2) {
        val k = params[i](input).value ?: return@block EvaluateResultError
        if (!k.hasStringValue()) return@block EvaluateResultError
        val v = params[i + 1](input).value ?: return@block EvaluateResultError
        // It is against the API contract to include a key more than once.
        if (map.put(k.stringValue, v) != null) return@block EvaluateResultError
      }
      EvaluateResultValue(encodeValue(map))
    }
}

// === Helper Functions ===

private inline fun catch(f: () -> EvaluateResult): EvaluateResult =
  try {
    f()
  } catch (e: Exception) {
    EvaluateResultError
  }

private inline fun unaryFunction(
  crossinline function: (EvaluateResult) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 1)
    throw Assert.fail("Function should have exactly 1 params, but %d were given.", params.size)
  val p = params[0]
  { input: MutableDocument -> catch { function(p(input)) } }
}

@JvmName("unaryValueFunction")
private inline fun unaryFunction(
  crossinline function: (Value) -> EvaluateResult
): EvaluateFunction = unaryFunction { r: EvaluateResult ->
  val v = r.value
  if (v === null) EvaluateResultError
  else if (v.hasNullValue()) EvaluateResult.NULL else function(v)
}

@JvmName("unaryBooleanFunction")
private inline fun unaryFunction(crossinline stringOp: (Boolean) -> EvaluateResult) =
  unaryFunctionType(
    ValueTypeCase.BOOLEAN_VALUE,
    Value::getBooleanValue,
    stringOp,
  )

@JvmName("unaryStringFunctionPrimitive")
private inline fun unaryFunctionPrimitive(crossinline stringOp: (String) -> String) =
  unaryFunction { s: String ->
    EvaluateResult.string(stringOp(s))
  }

@JvmName("unaryStringFunction")
private inline fun unaryFunction(crossinline stringOp: (String) -> EvaluateResult) =
  unaryFunctionType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    stringOp,
  )

@JvmName("unaryLongFunction")
private inline fun unaryFunction(crossinline longOp: (Long) -> EvaluateResult) =
  unaryFunctionType(
    ValueTypeCase.INTEGER_VALUE,
    Value::getIntegerValue,
    longOp,
  )

@JvmName("unaryTimestampFunction")
private inline fun unaryFunction(crossinline timestampOp: (Timestamp) -> EvaluateResult) =
  unaryFunctionType(
    ValueTypeCase.TIMESTAMP_VALUE,
    Value::getTimestampValue,
    timestampOp,
  )

@JvmName("unaryArrayFunction")
private inline fun unaryFunction(crossinline longOp: (List<Value>) -> EvaluateResult) =
  unaryFunctionType(
    ValueTypeCase.ARRAY_VALUE,
    { it.arrayValue.valuesList },
    longOp,
  )

private inline fun unaryFunction(
  crossinline byteOp: (ByteString) -> EvaluateResult,
  crossinline stringOp: (String) -> EvaluateResult
) =
  unaryFunctionType(
    ValueTypeCase.BYTES_VALUE,
    Value::getBytesValue,
    byteOp,
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    stringOp,
  )

private inline fun <T> unaryFunctionType(
  valueTypeCase: ValueTypeCase,
  crossinline valueExtractor: (Value) -> T,
  crossinline function: (T) -> EvaluateResult
): EvaluateFunction = unaryFunction { r: EvaluateResult ->
  val v = r.value
  if (v === null) EvaluateResultError
  else
    when (v.valueTypeCase) {
      ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
      valueTypeCase -> catch { function(valueExtractor(v)) }
      else -> EvaluateResultError
    }
}

private inline fun <T1, T2> unaryFunctionType(
  valueTypeCase1: ValueTypeCase,
  crossinline valueExtractor1: (Value) -> T1,
  crossinline function1: (T1) -> EvaluateResult,
  valueTypeCase2: ValueTypeCase,
  crossinline valueExtractor2: (Value) -> T2,
  crossinline function2: (T2) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 1)
    throw Assert.fail("Function should have exactly 1 params, but %d were given.", params.size)
  val p = params[0]
  block@{ input: MutableDocument ->
    val v = p(input).value ?: return@block EvaluateResultError
    when (v.valueTypeCase) {
      ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
      valueTypeCase1 -> catch { function1(valueExtractor1(v)) }
      valueTypeCase2 -> catch { function2(valueExtractor2(v)) }
      else -> EvaluateResultError
    }
  }
}

@JvmName("binaryValueValueFunction")
private inline fun binaryFunction(
  crossinline function: (Value, Value) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 2)
    throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)
  val p1 = params[0]
  val p2 = params[1]
  block@{ input: MutableDocument ->
    val v1 = p1(input).value ?: return@block EvaluateResultError
    val v2 = p2(input).value ?: return@block EvaluateResultError
    if (v1.hasNullValue() || v2.hasNullValue()) return@block EvaluateResult.NULL
    catch { function(v1, v2) }
  }
}

@JvmName("binaryMapStringFunction")
private inline fun binaryFunction(
  crossinline function: (Map<String, Value>, String) -> EvaluateResult
): EvaluateFunction =
  binaryFunctionType(
    ValueTypeCase.MAP_VALUE,
    { v: Value -> v.mapValue.fieldsMap },
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    function
  )

@JvmName("binaryValueArrayFunction")
private inline fun binaryFunction(
  crossinline function: (Value, List<Value>) -> EvaluateResult
): EvaluateFunction = binaryFunction { v1: Value, v2: Value ->
  if (v2.hasArrayValue()) function(v1, v2.arrayValue.valuesList) else EvaluateResultError
}

@JvmName("binaryArrayValueFunction")
private inline fun binaryFunction(
  crossinline function: (List<Value>, Value) -> EvaluateResult
): EvaluateFunction = binaryFunction { v1: Value, v2: Value ->
  if (v1.hasArrayValue()) function(v1.arrayValue.valuesList, v2) else EvaluateResultError
}

@JvmName("binaryStringStringFunction")
private inline fun binaryFunction(crossinline function: (String, String) -> EvaluateResult) =
  binaryFunctionType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    function
  )

@JvmName("binaryStringPatternConstructorFunction")
private inline fun binaryPatternConstructorFunction(
  crossinline patternConstructor: (String) -> Pattern?,
  crossinline function: (Pattern, String) -> Boolean
) =
  binaryFunctionConstructorType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue
  ) {
    val cache = cache(patternConstructor)
    ({ value: String, regex: String ->
      val pattern = cache(regex)
      if (pattern == null) EvaluateResultError else EvaluateResult.boolean(function(pattern, value))
    })
  }

@JvmName("binaryStringPatternFunction")
private inline fun binaryPatternFunction(crossinline function: (Pattern, String) -> Boolean) =
  binaryPatternConstructorFunction(
    { s: String ->
      try {
        Pattern.compile(s)
      } catch (e: PatternSyntaxException) {
        null
      }
    },
    function
  )

private inline fun <T> cache(crossinline ifAbsent: (String) -> T): (String) -> T? {
  var cache: Pair<String?, T?> = Pair(null, null)
  return block@{ s: String ->
    var (regex, pattern) = cache
    if (regex != s) {
      pattern = ifAbsent(s)
      cache = Pair(s, pattern)
    }
    return@block pattern
  }
}

@JvmName("binaryArrayArrayFunction")
private inline fun binaryFunction(
  crossinline function: (List<Value>, List<Value>) -> EvaluateResult
) =
  binaryFunctionType(
    ValueTypeCase.ARRAY_VALUE,
    { it.arrayValue.valuesList },
    ValueTypeCase.ARRAY_VALUE,
    { it.arrayValue.valuesList },
    function
  )

private inline fun ternaryLazyFunction(
  crossinline function:
    (() -> EvaluateResult, () -> EvaluateResult, () -> EvaluateResult) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 3)
    throw Assert.fail("Function should have exactly 3 params, but %d were given.", params.size)
  val p1 = params[0]
  val p2 = params[1]
  val p3 = params[2]
  { input: MutableDocument -> catch { function({ p1(input) }, { p2(input) }, { p3(input) }) } }
}

private inline fun ternaryTimestampFunction(
  crossinline function: (Timestamp, String, Long) -> EvaluateResult
): EvaluateFunction = ternaryNullableValueFunction { timestamp: Value, unit: Value, number: Value ->
  val t: Timestamp =
    when (timestamp.valueTypeCase) {
      ValueTypeCase.NULL_VALUE -> return@ternaryNullableValueFunction EvaluateResult.NULL
      ValueTypeCase.TIMESTAMP_VALUE -> timestamp.timestampValue
      else -> return@ternaryNullableValueFunction EvaluateResultError
    }
  val u: String =
    if (unit.hasStringValue()) unit.stringValue
    else return@ternaryNullableValueFunction EvaluateResultError
  val n: Long =
    when (number.valueTypeCase) {
      ValueTypeCase.NULL_VALUE -> return@ternaryNullableValueFunction EvaluateResult.NULL
      ValueTypeCase.INTEGER_VALUE -> number.integerValue
      else -> return@ternaryNullableValueFunction EvaluateResultError
    }
  function(t, u, n)
}

private inline fun ternaryNullableValueFunction(
  crossinline function: (Value, Value, Value) -> EvaluateResult
): EvaluateFunction = ternaryLazyFunction { p1, p2, p3 ->
  val v1 = p1().value ?: return@ternaryLazyFunction EvaluateResultError
  val v2 = p2().value ?: return@ternaryLazyFunction EvaluateResultError
  val v3 = p3().value ?: return@ternaryLazyFunction EvaluateResultError
  function(v1, v2, v3)
}

private inline fun <T1, T2> binaryFunctionType(
  valueTypeCase1: ValueTypeCase,
  crossinline valueExtractor1: (Value) -> T1,
  valueTypeCase2: ValueTypeCase,
  crossinline valueExtractor2: (Value) -> T2,
  crossinline function: (T1, T2) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 2)
    throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)
  (block@{ input: MutableDocument ->
    val v1 = params[0](input).value ?: return@block EvaluateResultError
    val v2 = params[1](input).value ?: return@block EvaluateResultError
    when (v1.valueTypeCase) {
      ValueTypeCase.NULL_VALUE ->
        when (v2.valueTypeCase) {
          ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
          valueTypeCase2 -> EvaluateResult.NULL
          else -> EvaluateResultError
        }
      valueTypeCase1 ->
        when (v2.valueTypeCase) {
          ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
          valueTypeCase2 -> catch { function(valueExtractor1(v1), valueExtractor2(v2)) }
          else -> EvaluateResultError
        }
      else -> EvaluateResultError
    }
  })
}

private inline fun <T1, T2> binaryFunctionConstructorType(
  valueTypeCase1: ValueTypeCase,
  crossinline valueExtractor1: (Value) -> T1,
  valueTypeCase2: ValueTypeCase,
  crossinline valueExtractor2: (Value) -> T2,
  crossinline functionConstructor: () -> (T1, T2) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 2)
    throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)
  val p1 = params[0]
  val p2 = params[1]
  val f = functionConstructor()
  (block@{ input: MutableDocument ->
    val v1 = p1(input).value ?: return@block EvaluateResultError
    val v2 = p2(input).value ?: return@block EvaluateResultError
    when (v1.valueTypeCase) {
      ValueTypeCase.NULL_VALUE ->
        when (v2.valueTypeCase) {
          ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
          valueTypeCase2 -> EvaluateResult.NULL
          else -> EvaluateResultError
        }
      valueTypeCase1 ->
        when (v2.valueTypeCase) {
          ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
          valueTypeCase2 -> catch { f(valueExtractor1(v1), valueExtractor2(v2)) }
          else -> EvaluateResultError
        }
      else -> EvaluateResultError
    }
  })
}

private inline fun variadicResultFunction(
  crossinline function: (List<EvaluateResult>) -> EvaluateResult
): EvaluateFunction = { params ->
  { input: MutableDocument ->
    val results = params.map { it(input) }
    catch { function(results) }
  }
}

@JvmName("variadicNullableValueFunction")
private inline fun variadicNullableValueFunction(
  crossinline function: (List<Value>) -> EvaluateResult
): EvaluateFunction = variadicResultFunction { l: List<EvaluateResult> ->
  function(l.map { it.value ?: return@variadicResultFunction EvaluateResultError })
}

@JvmName("variadicStringFunction")
private inline fun variadicFunction(
  crossinline function: (List<String>) -> EvaluateResult
): EvaluateFunction =
  variadicFunctionType(ValueTypeCase.STRING_VALUE, Value::getStringValue, function)

private inline fun <T> variadicFunctionType(
  valueTypeCase: ValueTypeCase,
  crossinline valueExtractor: (Value) -> T,
  crossinline function: (List<T>) -> EvaluateResult,
): EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    val values = ArrayList<T>(params.size)
    var nullFound = false
    for (param in params) {
      val v = param(input).value ?: return@block EvaluateResultError
      when (v.valueTypeCase) {
        ValueTypeCase.NULL_VALUE -> nullFound = true
        valueTypeCase -> values.add(valueExtractor(v))
        else -> return@block EvaluateResultError
      }
    }
    if (nullFound) EvaluateResult.NULL else catch { function(values) }
  }
}

@JvmName("variadicBooleanFunction")
private inline fun variadicFunction(
  crossinline function: (BooleanArray) -> EvaluateResult
): EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    val values = BooleanArray(params.size)
    var nullFound = false
    params.forEachIndexed { i, param ->
      val v = param(input).value ?: return@block EvaluateResultError
      when (v.valueTypeCase) {
        ValueTypeCase.NULL_VALUE -> nullFound = true
        ValueTypeCase.BOOLEAN_VALUE -> values[i] = v.booleanValue
        else -> return@block EvaluateResultError
      }
    }
    if (nullFound) EvaluateResult.NULL else catch { function(values) }
  }
}

private inline fun comparison(crossinline f: (Value, Value) -> Boolean?): EvaluateFunction =
  binaryFunction { p1: Value, p2: Value ->
    if (isNanValue(p1) or isNanValue(p2)) EvaluateResult.FALSE
    else EvaluateResult.boolean(f(p1, p2))
  }

private inline fun arithmeticPrimitive(
  crossinline intOp: (Long) -> Long,
  crossinline doubleOp: (Double) -> Double
): EvaluateFunction =
  arithmetic(
    { x: Long -> EvaluateResult.long(intOp(x)) },
    { x: Double -> EvaluateResult.double(doubleOp(x)) }
  )

private inline fun arithmeticPrimitive(
  crossinline intOp: (Long, Long) -> Long,
  crossinline doubleOp: (Double, Double) -> Double
): EvaluateFunction =
  arithmetic(
    { x: Long, y: Long -> EvaluateResult.long(intOp(x, y)) },
    { x: Double, y: Double -> EvaluateResult.double(doubleOp(x, y)) }
  )

private inline fun arithmeticPrimitive(
  crossinline doubleOp: (Double, Double) -> Double
): EvaluateFunction = arithmetic { x: Double, y: Double -> EvaluateResult.double(doubleOp(x, y)) }

private inline fun arithmetic(crossinline doubleOp: (Double) -> EvaluateResult): EvaluateFunction =
  arithmetic({ n: Long -> doubleOp(n.toDouble()) }, doubleOp)

private inline fun arithmetic(
  crossinline intOp: (Long) -> EvaluateResult,
  crossinline doubleOp: (Double) -> EvaluateResult
): EvaluateFunction =
  unaryFunctionType(
    ValueTypeCase.INTEGER_VALUE,
    Value::getIntegerValue,
    intOp,
    ValueTypeCase.DOUBLE_VALUE,
    Value::getDoubleValue,
    doubleOp,
  )

@JvmName("arithmeticNumberLong")
private inline fun arithmetic(
  crossinline intOp: (Long, Long) -> EvaluateResult,
  crossinline doubleOp: (Double, Long) -> EvaluateResult
): EvaluateFunction = binaryFunction { p1: Value, p2: Value ->
  if (p2.hasIntegerValue())
    when (p1.valueTypeCase) {
      ValueTypeCase.INTEGER_VALUE -> intOp(p1.integerValue, p2.integerValue)
      ValueTypeCase.DOUBLE_VALUE -> doubleOp(p1.doubleValue, p2.integerValue)
      else -> EvaluateResultError
    }
  else EvaluateResultError
}

private inline fun arithmetic(
  crossinline intOp: (Long, Long) -> EvaluateResult,
  crossinline doubleOp: (Double, Double) -> EvaluateResult
): EvaluateFunction = binaryFunction { p1: Value, p2: Value ->
  when (p1.valueTypeCase) {
    ValueTypeCase.INTEGER_VALUE ->
      when (p2.valueTypeCase) {
        ValueTypeCase.INTEGER_VALUE -> intOp(p1.integerValue, p2.integerValue)
        ValueTypeCase.DOUBLE_VALUE -> doubleOp(p1.integerValue.toDouble(), p2.doubleValue)
        else -> EvaluateResultError
      }
    ValueTypeCase.DOUBLE_VALUE ->
      when (p2.valueTypeCase) {
        ValueTypeCase.INTEGER_VALUE -> doubleOp(p1.doubleValue, p2.integerValue.toDouble())
        ValueTypeCase.DOUBLE_VALUE -> doubleOp(p1.doubleValue, p2.doubleValue)
        else -> EvaluateResultError
      }
    else -> EvaluateResultError
  }
}

private inline fun arithmetic(
  crossinline op: (Double, Double) -> EvaluateResult
): EvaluateFunction = binaryFunction { p1: Value, p2: Value ->
  val v1: Double =
    when (p1.valueTypeCase) {
      ValueTypeCase.INTEGER_VALUE -> p1.integerValue.toDouble()
      ValueTypeCase.DOUBLE_VALUE -> p1.doubleValue
      else -> return@binaryFunction EvaluateResultError
    }
  val v2: Double =
    when (p2.valueTypeCase) {
      ValueTypeCase.INTEGER_VALUE -> p2.integerValue.toDouble()
      ValueTypeCase.DOUBLE_VALUE -> p2.doubleValue
      else -> return@binaryFunction EvaluateResultError
    }
  op(v1, v2)
}
