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
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.Enterprise.equals
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.model.Values.isNullValue
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

internal val evaluateArrayReverse = unaryFunction { array: List<Value> ->
  EvaluateResult.value(encodeValue(array.reversed()))
}

internal val evaluateArrayFirst: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 1)
      throw Assert.fail("Function should have exactly 1 params, but %d were given.", params.size)

    val p1 = params[0](input)
    if (p1.isError) return@block EvaluateResultError
    if (p1.isUnset) return@block EvaluateResult.NULL

    val value = p1.value!!
    if (value.hasNullValue()) return@block EvaluateResult.NULL
    if (!value.hasArrayValue()) return@block EvaluateResultError

    val array = value.arrayValue.valuesList
    if (array.isEmpty()) return@block EvaluateResultUnset

    EvaluateResult.value(array.first())
  }
}

internal val evaluateArrayLast: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 1)
      throw Assert.fail("Function should have exactly 1 params, but %d were given.", params.size)

    val p1 = params[0](input)
    if (p1.isError) return@block EvaluateResultError
    if (p1.isUnset) return@block EvaluateResult.NULL

    val value = p1.value!!
    if (value.hasNullValue()) return@block EvaluateResult.NULL
    if (!value.hasArrayValue()) return@block EvaluateResultError

    val array = value.arrayValue.valuesList
    if (array.isEmpty()) return@block EvaluateResultUnset

    EvaluateResult.value(array.last())
  }
}

internal val evaluateArrayFirstN: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 2)
      throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)

    val p1 = params[0](input)
    if (p1.isError) return@block EvaluateResultError
    if (p1.isUnset) return@block EvaluateResult.NULL

    val value1 = p1.value!!
    if (value1.hasNullValue()) return@block EvaluateResult.NULL
    if (!value1.hasArrayValue()) return@block EvaluateResultError
    val array = value1.arrayValue.valuesList

    val p2 = params[1](input)
    if (p2.isError) return@block EvaluateResultError
    val value2 = p2.value
    if (value2 == null || !value2.hasIntegerValue()) return@block EvaluateResultError
    val n = value2.integerValue.toInt()

    if (n < 0) return@block EvaluateResultError

    val count = n.coerceAtMost(array.size)
    EvaluateResult.list(array.subList(0, count))
  }
}

internal val evaluateArrayLastN: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 2)
      throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)

    val p1 = params[0](input)
    if (p1.isError) return@block EvaluateResultError
    if (p1.isUnset) return@block EvaluateResult.NULL

    val value1 = p1.value!!
    if (value1.hasNullValue()) return@block EvaluateResult.NULL
    if (!value1.hasArrayValue()) return@block EvaluateResultError
    val array = value1.arrayValue.valuesList

    val p2 = params[1](input)
    if (p2.isError) return@block EvaluateResultError
    val value2 = p2.value
    if (value2 == null || !value2.hasIntegerValue()) return@block EvaluateResultError
    val n = value2.integerValue.toInt()

    if (n < 0) return@block EvaluateResultError

    val count = n.coerceAtMost(array.size)
    EvaluateResult.list(array.subList(array.size - count, array.size))
  }
}

internal val evaluateArrayMinimum: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 1)
      throw Assert.fail("Function should have exactly 1 params, but %d were given.", params.size)

    val p1 = params[0](input)
    if (p1.isError) return@block EvaluateResultError
    if (p1.isUnset) return@block EvaluateResult.NULL

    val value = p1.value!!
    if (value.hasNullValue()) return@block EvaluateResult.NULL
    if (!value.hasArrayValue()) return@block EvaluateResultError

    val array = value.arrayValue.valuesList
    if (array.isEmpty()) return@block EvaluateResult.NULL

    var min = array[0]
    for (i in 1 until array.size) {
      if (
        Values.Enterprise.strictCompare(array[i], min) == Values.Enterprise.CompareResult.LESS_THAN
      ) {
        min = array[i]
      }
    }
    EvaluateResult.value(min)
  }
}

internal val evaluateArrayMaximum: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 1)
      throw Assert.fail("Function should have exactly 1 params, but %d were given.", params.size)

    val p1 = params[0](input)
    if (p1.isError) return@block EvaluateResultError
    if (p1.isUnset) return@block EvaluateResult.NULL

    val value = p1.value!!
    if (value.hasNullValue()) return@block EvaluateResult.NULL
    if (!value.hasArrayValue()) return@block EvaluateResultError

    val array = value.arrayValue.valuesList
    if (array.isEmpty()) return@block EvaluateResult.NULL

    var max = array[0]
    for (i in 1 until array.size) {
      if (
        Values.Enterprise.strictCompare(array[i], max) ==
          Values.Enterprise.CompareResult.GREATER_THAN
      ) {
        max = array[i]
      }
    }
    EvaluateResult.value(max)
  }
}

internal val evaluateArrayMinimumN: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 2)
      throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)

    val p1 = params[0](input)
    val array =
      if (p1.value?.hasArrayValue() == true) {
        p1.value?.arrayValue?.valuesList
      } else null

    val p2 = params[1](input)
    val n =
      if (p2.value?.hasIntegerValue() == true) {
        p2.value?.integerValue?.toInt()
      } else return@block EvaluateResultError

    if (array == null) return@block EvaluateResultUnset
    if (n!! < 0) return@block EvaluateResultError

    val sorted =
      array.sortedWith { o1, o2 ->
        when (Values.Enterprise.strictCompare(o1, o2)) {
          Values.Enterprise.CompareResult.LESS_THAN -> -1
          Values.Enterprise.CompareResult.GREATER_THAN -> 1
          else -> 0
        }
      }
    val count = n.coerceAtMost(sorted.size)
    EvaluateResult.list(sorted.subList(0, count))
  }
}

internal val evaluateArrayMaximumN: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 2)
      throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)

    val p1 = params[0](input)
    val array =
      if (p1.value?.hasArrayValue() == true) {
        p1.value?.arrayValue?.valuesList
      } else null

    val p2 = params[1](input)
    val n =
      if (p2.value?.hasIntegerValue() == true) {
        p2.value?.integerValue?.toInt()
      } else return@block EvaluateResultError

    if (array == null) return@block EvaluateResultUnset
    if (n!! < 0) return@block EvaluateResultError

    val sorted =
      array.sortedWith { o1, o2 ->
        when (Values.Enterprise.strictCompare(o1, o2)) {
          Values.Enterprise.CompareResult.LESS_THAN -> 1
          Values.Enterprise.CompareResult.GREATER_THAN -> -1
          else -> 0
        }
      }
    val count = n.coerceAtMost(sorted.size)
    EvaluateResult.list(sorted.subList(0, count))
  }
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

internal val evaluateArrayIndexOf: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 3)
      throw Assert.fail("Function should have exactly 3 params, but %d were given.", params.size)

    val p1 = params[0](input)
    val p2 = params[1](input)
    val p3 = params[2](input)

    if (p1.isError || p2.isError || p3.isError) return@block EvaluateResultError

    val direction =
      if (p3.value?.hasStringValue() == true) {
        p3.value?.stringValue
      } else return@block EvaluateResultError

    val arrayValue = p1.value
    if (arrayValue == null || isNullValue(arrayValue))
      return@block EvaluateResult.value(Values.NULL_VALUE)

    if (!arrayValue.hasArrayValue()) return@block EvaluateResultError

    // For the second parameter (value), if it is UNSET (null), we return NULL.
    // If it is actual NULL_VALUE, it is a valid search target.
    val value = p2.value ?: return@block EvaluateResult.value(Values.NULL_VALUE)

    val array = arrayValue.arrayValue.valuesList
    var index = -1

    if (direction == "last") {
      for (i in array.indices.reversed()) {
        if (Values.Enterprise.equals(array[i], value)) {
          index = i
          break
        }
      }
    } else if (direction == "first") {
      for (i in array.indices) {
        if (Values.Enterprise.equals(array[i], value)) {
          index = i
          break
        }
      }
    } else {
      return@block EvaluateResultError
    }
    EvaluateResult.value(encodeValue(index))
  }
}

internal val evaluateArrayIndexOfAll: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size != 2)
      throw Assert.fail("Function should have exactly 2 params, but %d were given.", params.size)

    val p1 = params[0](input)
    val p2 = params[1](input)

    if (p1.isError || p2.isError) return@block EvaluateResultError

    val arrayValue = p1.value
    if (arrayValue == null || isNullValue(arrayValue))
      return@block EvaluateResult.value(Values.NULL_VALUE)

    if (!arrayValue.hasArrayValue()) return@block EvaluateResultError

    val value = p2.value ?: return@block EvaluateResult.value(Values.NULL_VALUE)

    val array = arrayValue.arrayValue.valuesList
    val indices = mutableListOf<Value>()
    for (i in array.indices) {
      if (Values.Enterprise.equals(array[i], value)) {
        indices.add(encodeValue(i))
      }
    }
    EvaluateResult.list(indices)
  }
}
