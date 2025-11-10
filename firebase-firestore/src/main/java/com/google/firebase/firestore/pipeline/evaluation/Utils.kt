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

package com.google.firebase.firestore.pipeline.evaluation

import com.google.firebase.firestore.RealtimePipeline
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.Values.getVectorValue
import com.google.firebase.firestore.util.Assert
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeCase
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException

// === Helper Functions ===

internal inline fun catch(f: () -> EvaluateResult): EvaluateResult =
  try {
    f()
  } catch (e: Exception) {
    EvaluateResultError
  }

/**
 * Basic Unary Function
 * - Validates there is exactly 1 parameter.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun unaryFunction(
  crossinline function: (EvaluateResult) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 1)
    throw Assert.fail("Function should have exactly 1 params, but %d were given.", params.size)
  val p = params[0]
  { input: MutableDocument -> catch { function(p(input)) } }
}

/**
 * Unary Value Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("unaryValueFunction")
internal inline fun unaryFunction(
  crossinline function: (Value) -> EvaluateResult
): EvaluateFunction = unaryFunction { r: EvaluateResult ->
  val v = r.value
  if (v === null) EvaluateResultError
  else if (v.hasNullValue()) EvaluateResult.NULL else function(v)
}

/**
 * Unary Boolean Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - Extracts Boolean for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("unaryBooleanFunction")
internal inline fun unaryFunction(crossinline function: (Boolean) -> EvaluateResult) =
  unaryFunctionType(
    ValueTypeCase.BOOLEAN_VALUE,
    Value::getBooleanValue,
    function,
  )

/**
 * Unary String Function that wraps the String result
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - Extracts Boolean for [function] evaluation.
 * - Wraps the primitive String result as [EvaluateResult].
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("unaryStringFunctionPrimitive")
internal inline fun unaryFunctionPrimitive(crossinline function: (String) -> String) =
  unaryFunction { s: String ->
    EvaluateResult.string(function(s))
  }

/**
 * Unary String Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - Extracts String for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("unaryStringFunction")
internal inline fun unaryFunction(crossinline function: (String) -> EvaluateResult) =
  unaryFunctionType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    function,
  )

/**
 * Unary String Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - Extracts String for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("unaryLongFunction")
internal inline fun unaryFunction(crossinline function: (Long) -> EvaluateResult) =
  unaryFunctionType(
    ValueTypeCase.INTEGER_VALUE,
    Value::getIntegerValue,
    function,
  )

/**
 * Unary Timestamp Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - Extracts Timestamp for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("unaryTimestampFunction")
internal inline fun unaryFunction(crossinline function: (Timestamp) -> EvaluateResult) =
  unaryFunctionType(
    ValueTypeCase.TIMESTAMP_VALUE,
    Value::getTimestampValue,
    function,
  )

/**
 * Unary Timestamp Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value], however NULL [Value]s can appear
 * inside of array.
 * - Extracts Timestamp from [Value] for evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("unaryArrayFunction")
internal inline fun unaryFunction(crossinline function: (List<Value>) -> EvaluateResult) =
  unaryFunction { r: EvaluateResult ->
    val v = r.value
    if (v === null) EvaluateResult.NULL
    else
      when (v.valueTypeCase) {
        ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
        ValueTypeCase.ARRAY_VALUE -> function(v.arrayValue.valuesList)
        else -> EvaluateResultError
      }
  }

/**
 * Unary Bytes/String Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - Depending on [Value] type, either the Timestamp or String is extracted and evaluated by either
 * [byteOp] or [stringOp].
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun unaryFunction(
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

/**
 * For building type specific Unary Functions
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - If [Value] type is [valueTypeCase] then use [valueExtractor] for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun <T> unaryFunctionType(
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

/**
 * For building type specific Unary Functions that can have 2 possible types.
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - If [Value] type is [valueTypeCase1] then use [valueExtractor1] for [function1] evaluation.
 * - If [Value] type is [valueTypeCase2] then use [valueExtractor2] for [function2] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun <T1, T2> unaryFunctionType(
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

/**
 * Binary (Value, Value) Function
 * - Validates there is exactly 2 parameters.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value].
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("binaryValueValueFunction")
internal inline fun binaryFunction(
  crossinline function: (Value?, Value?) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 2)
    throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)
  val p1 = params[0]
  val p2 = params[1]
  block@{ input: MutableDocument ->
    val v1 = p1(input)
    if (v1.isError) return@block EvaluateResultError
    val v2 = p2(input)
    if (v2.isError) return@block EvaluateResultError

    catch { function(v1.value, v2.value) }
  }
}

/**
 * Binary (Map, String) Function
 * - Validates there is exactly 2 parameters.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value], however NULL [Value]s can
 * appear inside of Map.
 * - Extracts Map and String for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("binaryMapStringFunction")
internal inline fun binaryFunction(
  crossinline function: (Map<String, Value>, String) -> EvaluateResult
): EvaluateFunction =
  binaryFunctionType(
    ValueTypeCase.MAP_VALUE,
    { v: Value -> v.mapValue.fieldsMap },
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    function
  )

/**
 * Binary (Value, Array) Function
 * - Validates there is exactly 2 parameters.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value], however NULL [Value]s can
 * appear inside of Array.
 * - Extracts Value and Array for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("binaryValueArrayFunction")
internal inline fun binaryFunction(
  crossinline function: (Value?, List<Value>) -> EvaluateResult
): EvaluateFunction = binaryFunction { v1: Value?, v2: Value? ->
  when (v2?.valueTypeCase) {
    null,
    ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
    ValueTypeCase.ARRAY_VALUE -> function(v1, v2.arrayValue.valuesList)
    else -> EvaluateResultError
  }
}

/**
 * Binary (Array, Value) Function
 * - Validates there is exactly 2 parameters.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value], however NULL [Value]s can
 * appear inside of Array.
 * - Extracts Array and Value for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("binaryArrayValueFunction")
internal inline fun binaryFunction(
  crossinline function: (List<Value>, Value?) -> EvaluateResult
): EvaluateFunction = binaryFunction { v1: Value?, v2: Value? ->
  when (v1?.valueTypeCase) {
    null,
    ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
    ValueTypeCase.ARRAY_VALUE -> function(v1.arrayValue.valuesList, v2)
    else -> EvaluateResultError
  }
}

/**
 * Binary (Vector, Vector) Function
 * - Validates there is exactly 2 parameters.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value], however NULL [Value]s can
 * appear inside of Array.
 * - Extracts vectors for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("binaryVectorVectorFunction")
internal inline fun binaryFunction(
  crossinline function: (DoubleArray, DoubleArray) -> EvaluateResult
): EvaluateFunction = binaryFunction { left: Value?, right: Value? ->
  val leftVector = getVectorValue(left)
  if (leftVector == null) return@binaryFunction EvaluateResultError

  val rightVector = getVectorValue(right)
  if (rightVector == null) return@binaryFunction EvaluateResultError

  function(leftVector, rightVector)
}

/**
 * Binary (String, String) Function
 * - Validates there is exactly 2 parameters.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value].
 * - Extracts String and String for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("binaryStringStringFunction")
internal inline fun binaryFunction(crossinline function: (String, String) -> EvaluateResult) =
  binaryFunctionType(
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    ValueTypeCase.STRING_VALUE,
    Value::getStringValue,
    function
  )

/**
 * For building binary functions that perform Regex evaluation.
 * - Separates the Regex compilation via [patternConstructor] from the [function] evaluation.
 * - Caches previously seen Regex to avoid compilation overhead.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value].
 * - Extracts String and Regex via [patternConstructor] for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("binaryStringPatternConstructorFunction")
internal inline fun binaryPatternConstructorFunction(
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

/**
 * Binary (String, Regex from String) Function
 * - Validates there is exactly 2 parameters.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value].
 * - Extracts String and Regex for [function] evaluation.
 * - Caches previously seen Regex to avoid compilation overhead.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("binaryStringPatternFunction")
internal inline fun binaryPatternFunction(crossinline function: (Pattern, String) -> Boolean) =
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

/** Simple one entry cache. */
internal inline fun <T> cache(crossinline ifAbsent: (String) -> T): (String) -> T? {
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

/**
 * Binary (Array, Array) Function
 * - Validates there is exactly 2 parameters.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value], however NULL [Value]s can
 * appear inside of Array.
 * - Extracts Array and Array for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("binaryArrayArrayFunction")
internal inline fun binaryFunction(
  crossinline function: (List<Value>, List<Value>) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 2)
    throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)

  (block@{ input: MutableDocument ->
    val p1 = params[0](input)
    val v1 = if (p1.isError) return@block EvaluateResultError else p1.value
    val p2 = params[1](input)
    val v2 = if (p2.isError) return@block EvaluateResultError else p2.value

    // Mirroring Semantics
    val array1 =
      when (v1?.valueTypeCase) {
        null,
        ValueTypeCase.NULL_VALUE -> null
        ValueTypeCase.ARRAY_VALUE -> v1.arrayValue.valuesList
        else -> {
          return@block EvaluateResultError
        }
      }
    val array2 =
      when (v2?.valueTypeCase) {
        null,
        ValueTypeCase.NULL_VALUE -> null
        ValueTypeCase.ARRAY_VALUE -> v2.arrayValue.valuesList
        else -> {
          return@block EvaluateResultError
        }
      }

    if (array1 == null || array2 == null) EvaluateResult.NULL else function(array1, array2)
  })
}

/**
 * For building type specific Binary Functions
 * - Validates there is exactly 2 parameter.
 * - First short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value].
 * - First parameter must be [Value] of [valueTypeCase1].
 * - Second parameter must be [Value] of [valueTypeCase2].
 * - Extract parameter values via [valueExtractor1] and [valueExtractor2] for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun <T1, T2> binaryFunctionType(
  valueTypeCase1: ValueTypeCase,
  crossinline valueExtractor1: (Value) -> T1,
  valueTypeCase2: ValueTypeCase,
  crossinline valueExtractor2: (Value) -> T2,
  crossinline function: (T1, T2) -> EvaluateResult
): EvaluateFunction = { params ->
  if (params.size != 2)
    throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)
  (block@{ input: MutableDocument ->
    val p1 = params[0](input)
    val v1 = if (p1.isError) return@block EvaluateResultError else p1.value
    val p2 = params[1](input)
    val v2 = if (p2.isError) return@block EvaluateResultError else p2.value

    // Mirroring Semantics
    when (v1?.valueTypeCase) {
      null -> return@block EvaluateResultUnset
      ValueTypeCase.NULL_VALUE ->
        when (v2?.valueTypeCase) {
          null,
          ValueTypeCase.NULL_VALUE,
          valueTypeCase2 -> EvaluateResult.NULL
          else -> EvaluateResultError
        }
      valueTypeCase1 ->
        when (v2?.valueTypeCase) {
          null,
          ValueTypeCase.NULL_VALUE -> EvaluateResult.NULL
          valueTypeCase2 -> catch { function(valueExtractor1(v1), valueExtractor2(v2)) }
          else -> EvaluateResultError
        }
      else -> EvaluateResultError
    }
  })
}

/**
 * For building type specific Binary Functions
 * - Has [functionConstructor] for creating stateful evaluation function.
 * - Validates there is exactly 2 parameter.
 * - First short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value].
 * - First parameter must be [Value] of [valueTypeCase1].
 * - Second parameter must be [Value] of [valueTypeCase2].
 * - Extract parameter values via [valueExtractor1] and [valueExtractor2] for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun <T1, T2> binaryFunctionConstructorType(
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

/**
 * Ternary (Timestamp, String, Long) Function
 * - Validates there is exactly 3 parameters.
 * - Passes lazy parameters that delay evaluation of parameters.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun ternaryLazyFunction(
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

/**
 * Ternary (Timestamp, String, Long) Function
 * - Validates there is exactly 3 parameters.
 * - First, short circuits UNSET and ERROR parameters to return ERROR.
 * - If 2nd parameter is NULL, short circuit and return ERROR.
 * - If 1st or 3rd parameter is NULL, short circuit and return NULL.
 * - Extracts Timestamp, String and Long for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun ternaryTimestampFunction(
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

/**
 * Ternary Value Function
 * - Validates there is exactly 3 parameters.
 * - Short circuits UNSET and ERROR parameters to return ERROR.
 * - Allows passing of NULL [Value]s to [function] for evaluation.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun ternaryNullableValueFunction(
  crossinline function: (Value, Value, Value) -> EvaluateResult
): EvaluateFunction = ternaryLazyFunction { p1, p2, p3 ->
  val v1 = p1().value ?: return@ternaryLazyFunction EvaluateResultError
  val v2 = p2().value ?: return@ternaryLazyFunction EvaluateResultError
  val v3 = p3().value ?: return@ternaryLazyFunction EvaluateResultError
  function(v1, v2, v3)
}

/**
 * Basic Variadic Function
 * - No short circuiting of parameter evaluation.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun variadicResultFunction(
  crossinline function: (List<EvaluateResult>) -> EvaluateResult
): EvaluateFunction = { params ->
  { input: MutableDocument ->
    val results = params.map { it(input) }
    catch { function(results) }
  }
}

/**
 * Variadic Value Function with NULLS
 * - Short circuits UNSET and ERROR parameters to return ERROR.
 * - Allows passing of NULL [Value]s to [function] for evaluation.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("variadicNullableValueFunction")
internal inline fun variadicNullableValueFunction(
  crossinline function: (List<Value>) -> EvaluateResult
): EvaluateFunction = variadicResultFunction { l: List<EvaluateResult> ->
  function(l.map { it.value ?: return@variadicResultFunction EvaluateResultError })
}

/**
 * Variadic String Function
 * - First short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value].
 * - Extract String parameters into List for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("variadicStringFunction")
internal inline fun variadicFunction(
  crossinline function: (List<String>) -> EvaluateResult
): EvaluateFunction =
  variadicFunctionType(ValueTypeCase.STRING_VALUE, Value::getStringValue, function)

/**
 * For building type specific Variadic Functions
 * - First short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value].
 * - Parameter must be [Value] of [valueTypeCase].
 * - Extract parameter values via [valueExtractor] into List for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun <T> variadicFunctionType(
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

/**
 * Variadic String Function
 * - First short circuits UNSET and ERROR parameters to return ERROR.
 * - Second short circuits NULL [Value] parameters to return NULL [Value].
 * - Extract String parameters into BooleanArray for [function] evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("variadicBooleanFunction")
internal inline fun variadicFunction(
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

/**
 * Binary (Value, Value) Function for Comparisons
 * - Validates there is exactly 2 parameters.
 * - Wraps result as EvaluateResult.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun comparison(crossinline f: (Value?, Value?) -> Boolean): EvaluateFunction =
  binaryFunction { p1: Value?, p2: Value? ->
    EvaluateResult.boolean(f(p1, p2))
  }

/**
 * Unary (Number) Arithmetic Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - If parameter type is Integer then [intOp] will be used for evaluation.
 * - If parameter type is Double then [doubleOp] will be used for evaluation.
 * - All other parameter types return ERROR.
 * - Primitive result is wrapped as EvaluateResult.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun arithmeticPrimitive(
  crossinline intOp: (Long) -> Long,
  crossinline doubleOp: (Double) -> Double
): EvaluateFunction =
  arithmetic(
    { x: Long -> EvaluateResult.long(intOp(x)) },
    { x: Double -> EvaluateResult.double(doubleOp(x)) }
  )

/**
 * Binary Arithmetic Function
 * - Validates there is exactly 2 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - If both parameter types are Integer then [intOp] will be used for evaluation.
 * - Otherwise if both parameters are either Integer or Double, then the values are converted to
 * Double, and then [doubleOp] will be used for evaluation.
 * - All other parameter types return ERROR.
 * - Primitive result is wrapped as EvaluateResult.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun arithmeticPrimitive(
  crossinline intOp: (Long, Long) -> Long,
  crossinline doubleOp: (Double, Double) -> Double
): EvaluateFunction =
  arithmetic(
    { x: Long, y: Long -> EvaluateResult.long(intOp(x, y)) },
    { x: Double, y: Double -> EvaluateResult.double(doubleOp(x, y)) }
  )

/**
 * Binary Arithmetic Function
 * - Validates there is exactly 2 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - If any of parameters are Integer, they will be converted to Double.
 * - After conversion, if both parameters are Double, the [doubleOp] will be used for evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun arithmeticPrimitive(
  crossinline doubleOp: (Double, Double) -> Double
): EvaluateFunction = arithmetic { x: Double, y: Double -> EvaluateResult.double(doubleOp(x, y)) }

/**
 * Unary Arithmetic Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - If parameter is Integer, it will be converted to Double.
 * - After conversion, if parameter is Double, the [function] will be used for evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun arithmetic(crossinline function: (Double) -> EvaluateResult): EvaluateFunction =
  arithmetic({ n: Long -> function(n.toDouble()) }, function)

/**
 * Unary Arithmetic Function
 * - Validates there is exactly 1 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - If [Value] type is Integer then [intOp] will be used for evaluation.
 * - If [Value] type is Double then [doubleOp] will be used for evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun arithmetic(
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

/**
 * Binary Arithmetic Function
 * - Validates there is exactly 2 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - Second parameter is expected to be Long.
 * - If first parameter type is Integer then [intOp] will be used for evaluation.
 * - If first parameter type is Double then [doubleOp] will be used for evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
@JvmName("arithmeticNumberLong")
internal inline fun arithmetic(
  crossinline intOp: (Long, Long) -> EvaluateResult,
  crossinline doubleOp: (Double, Long) -> EvaluateResult
): EvaluateFunction = binaryFunction { p1: Value?, p2: Value? ->
  if (p2?.hasIntegerValue() == true)
    when (p1?.valueTypeCase) {
      ValueTypeCase.INTEGER_VALUE -> intOp(p1.integerValue, p2.integerValue)
      ValueTypeCase.DOUBLE_VALUE -> doubleOp(p1.doubleValue, p2.integerValue)
      else -> EvaluateResultError
    }
  else EvaluateResultError
}

/**
 * Binary Arithmetic Function
 * - Validates there is exactly 2 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - If both parameter types are Integer then [intOp] will be used for evaluation.
 * - Otherwise if both parameters are either Integer or Double, then the values are converted to
 * Double, and then [doubleOp] will be used for evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun arithmetic(
  crossinline intOp: (Long, Long) -> EvaluateResult,
  crossinline doubleOp: (Double, Double) -> EvaluateResult
): EvaluateFunction = binaryFunction { p1: Value?, p2: Value? ->
  when (p1?.valueTypeCase) {
    ValueTypeCase.INTEGER_VALUE ->
      when (p2?.valueTypeCase) {
        ValueTypeCase.INTEGER_VALUE -> intOp(p1.integerValue, p2.integerValue)
        ValueTypeCase.DOUBLE_VALUE -> doubleOp(p1.integerValue.toDouble(), p2.doubleValue)
        else -> EvaluateResultError
      }
    ValueTypeCase.DOUBLE_VALUE ->
      when (p2?.valueTypeCase) {
        ValueTypeCase.INTEGER_VALUE -> doubleOp(p1.doubleValue, p2.integerValue.toDouble())
        ValueTypeCase.DOUBLE_VALUE -> doubleOp(p1.doubleValue, p2.doubleValue)
        else -> EvaluateResultError
      }
    else -> EvaluateResultError
  }
}

/**
 * Binary Arithmetic Function
 * - Validates there is exactly 2 parameter.
 * - Short circuits UNSET and ERROR parameter to return ERROR.
 * - Short circuits NULL [Value] parameter to return NULL [Value].
 * - If any of parameters are Integer, they will be converted to Double.
 * - After conversion, if both parameters are Double, the [function] will be used for evaluation.
 * - All other parameter types return ERROR.
 * - Catches evaluation exceptions and returns them as an ERROR.
 */
internal inline fun arithmetic(
  crossinline function: (Double, Double) -> EvaluateResult
): EvaluateFunction = binaryFunction { p1: Value?, p2: Value? ->
  val v1: Double =
    when (p1?.valueTypeCase) {
      ValueTypeCase.INTEGER_VALUE -> p1.integerValue.toDouble()
      ValueTypeCase.DOUBLE_VALUE -> p1.doubleValue
      else -> return@binaryFunction EvaluateResultError
    }
  val v2: Double =
    when (p2?.valueTypeCase) {
      ValueTypeCase.INTEGER_VALUE -> p2.integerValue.toDouble()
      ValueTypeCase.DOUBLE_VALUE -> p2.doubleValue
      else -> return@binaryFunction EvaluateResultError
    }
  function(v1, v2)
}

internal class EvaluationContext(val pipeline: RealtimePipeline)

internal typealias EvaluateDocument = (input: MutableDocument) -> EvaluateResult

internal typealias EvaluateFunction = (params: List<EvaluateDocument>) -> EvaluateDocument

internal val notImplemented: EvaluateFunction = { _ -> throw NotImplementedError() }
