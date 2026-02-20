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

import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.Values.Enterprise.equals
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult.Companion.list
import com.google.firebase.firestore.util.Assert
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeCase
import com.google.protobuf.ByteString

// === Array Functions ===

internal val evaluateArray = variadicNullableValueFunction(::list)

internal val evaluateEqAny = binaryFunction { v: Value?, l: List<Value> ->
  if (v == null) return@binaryFunction EvaluateResult.FALSE
  return@binaryFunction equalAny(v, l)
}

internal val evaluateNotEqAny =
  binaryFunction { v: Value?, l: List<Value>,
    ->
    if (v == null) return@binaryFunction EvaluateResult.FALSE
    return@binaryFunction notEqualAny(v, l)
  }

internal val evaluateArrayContains = binaryFunction { l: List<Value>, v: Value? ->
  if (v == null) return@binaryFunction EvaluateResult.FALSE
  return@binaryFunction equalAny(v, l)
}

internal val evaluateArrayContainsAny =
  binaryFunction { array: List<Value>, searchValues: List<Value> ->
    for (value in array) for (search in searchValues) when (equals(value, search)) {
      true -> return@binaryFunction EvaluateResult.TRUE
      false -> {}
    }
    return@binaryFunction EvaluateResult.FALSE
  }

internal val evaluateArrayContainsAll =
  binaryFunction { array: List<Value>, searchValues: List<Value> ->
    for (search in searchValues) {
      var found = false
      for (value in array) when (equals(value, search)) {
        true -> {
          found = true
          break
        }
        false -> {}
      }

      if (!found) {
        return@binaryFunction EvaluateResult.FALSE
      }
    }
    return@binaryFunction EvaluateResult.TRUE
  }

internal val evaluateArrayLength = unaryFunction { array: List<Value> ->
  EvaluateResult.long(array.size)
}

internal val evaluateArrayFirst = unaryFunction { array: List<Value> ->
  if (array.isEmpty()) EvaluateResultUnset else EvaluateResult.value(array.first())
}

internal val evaluateArrayReverse = unaryFunction { array: List<Value> ->
  EvaluateResult.value(encodeValue(array.reversed()))
}

internal val evaluateJoin: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 2)
      throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)

    var hasNull = false
    val array = params[0](input)
    when (array) {
      is EvaluateResultError -> return@block EvaluateResultError
      is EvaluateResultUnset -> return@block EvaluateResultError
      EvaluateResult.NULL -> hasNull = true
      else -> {
        if (array.value?.valueTypeCase != ValueTypeCase.ARRAY_VALUE)
          return@block EvaluateResultError
      }
    }

    val delimiter = params[1](input)
    when (delimiter) {
      is EvaluateResultError -> return@block EvaluateResultError
      is EvaluateResultUnset -> return@block EvaluateResultError
      EvaluateResult.NULL -> return@block EvaluateResult.NULL
      else -> {
        when (delimiter.value?.valueTypeCase) {
          ValueTypeCase.STRING_VALUE ->
            if (!hasNull) {
              joinStrings(array.value?.arrayValue!!.valuesList, delimiter.value?.stringValue!!)
            } else EvaluateResult.NULL
          ValueTypeCase.BYTES_VALUE ->
            if (!hasNull) {
              joinBytes(array.value?.arrayValue!!.valuesList, delimiter.value?.bytesValue!!)
            } else EvaluateResult.NULL
          else -> EvaluateResultError
        }
      }
    }
  }
}

private fun joinStrings(array: List<Value>, delimiter: String): EvaluateResult {
  val builder = java.lang.StringBuilder()
  var isFirstElement = true
  for (i in 0 until array.size) {
    val element = array[i]
    when (element.valueTypeCase) {
      ValueTypeCase.STRING_VALUE -> {
        if (!isFirstElement) {
          builder.append(delimiter)
        }
        builder.append(element.stringValue)
        isFirstElement = false
      }
      ValueTypeCase.NULL_VALUE -> {} // skip null
      else -> return EvaluateResultError
    }
  }
  return EvaluateResult.string(builder.toString())
}

private fun joinBytes(array: List<Value>, delimiter: ByteString): EvaluateResult {
  val builder = mutableListOf<Byte>()
  var isFirstElement = true
  for (i in 0 until array.size) {
    when (array[i].valueTypeCase) {
      ValueTypeCase.BYTES_VALUE -> {
        if (!isFirstElement) {
          delimiter.forEach { builder.add(it) }
        }
        array[i].bytesValue.forEach { builder.add(it) }
        isFirstElement = false
      }
      ValueTypeCase.NULL_VALUE -> {} // skip null
      else -> return EvaluateResultError
    }
  }
  return EvaluateResult.value(encodeValue(builder.toByteArray()))
}

internal val evaluateArrayGet: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 2)
      throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)

    val p1 = params[0](input)
    val array =
      if (p1.value?.hasArrayValue() == true) {
        p1.value?.arrayValue?.valuesList
      } else null

    val p2 = params[1](input)
    val offset =
      if (p2.value?.hasIntegerValue() == true) {
        p2.value?.integerValue
      } else return@block EvaluateResultError

    if (array == null) return@block EvaluateResultUnset

    // If the index is out of bounds, return UNSET.
    var index = offset!!
    if (index >= array.size || index < -array.size) {
      return@block EvaluateResultUnset
    }

    // Adjust index for negative indexes.
    index =
      if (index < 0) {
        array.size + index
      } else index

    EvaluateResult.value(array[index.toInt()])
  }
}

internal val evaluateArrayConcat: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size < 2)
      throw Assert.fail("Function should have at least 2 params, but %d were given.", params.size)

    val allArraysValues = mutableListOf<List<Value>>()
    var hasNull = false

    for (param in params) {
      val result = param(input)
      when (result) {
        is EvaluateResultValue -> {
          if (result.value?.hasArrayValue() == true) {
            allArraysValues.add(result.value.arrayValue.valuesList)
          } else if (result.value?.hasNullValue() == true) {
            hasNull = true
          } else {
            return@block EvaluateResultError
          }
        }
        EvaluateResultUnset -> hasNull = true
        EvaluateResultError -> return@block EvaluateResultError
      }
    }

    if (hasNull) {
      return@block EvaluateResult.NULL
    }

    arrayConcatImpl(allArraysValues)
  }
}

internal fun arrayConcatImpl(arrays: List<List<Value>>) =
  EvaluateResult.value(encodeValue(arrays.flatten()))

private fun equalAny(value: Value, list: List<Value>): EvaluateResult {
  for (element in list) when (equals(value, element)) {
    true -> return EvaluateResult.TRUE
    false -> {}
  }
  return EvaluateResult.FALSE
}

private fun notEqualAny(value: Value, list: List<Value>): EvaluateResult {
  for (element in list) when (equals(value, element)) {
    true -> return EvaluateResult.FALSE
    false -> {}
  }
  return EvaluateResult.TRUE
}
