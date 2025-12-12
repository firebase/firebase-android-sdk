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
import com.google.firebase.firestore.model.Values.VECTOR_MAP_VECTORS_KEY
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.model.Values.isVectorValue
import com.google.firebase.firestore.util.Assert
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeCase
import com.google.protobuf.ByteString

// === General Functions ===
internal val evaluateLength = unaryFunction { value: Value ->
  when (value.valueTypeCase) {
    ValueTypeCase.STRING_VALUE ->
      EvaluateResult.long(value.stringValue.codePointCount(0, value.stringValue.length))
    ValueTypeCase.BYTES_VALUE -> EvaluateResult.long(value.bytesValue.size())
    ValueTypeCase.ARRAY_VALUE -> EvaluateResult.long(value.arrayValue.valuesCount)
    ValueTypeCase.MAP_VALUE -> {
      if (isVectorValue(value)) {
        EvaluateResult.long(vectorLengthImpl(value))
      } else {
        EvaluateResult.long(value.mapValue.fieldsMap.size)
      }
    }
    else -> EvaluateResultError
  }
}

internal val evaluateConcat: EvaluateFunction = { params ->
  block@{ input: MutableDocument ->
    if (params.size < 2)
      throw Assert.fail("Function should have at least 2 params, but %d were given.", params.size)

    var hasNull = false
    var firstTypeValue: Value? = null
    val values = mutableListOf<Value>()

    for (param in params) {
      val result = param(input)
      when (result) {
        is EvaluateResultError -> return@block EvaluateResultError
        is EvaluateResultUnset -> hasNull = true
        EvaluateResult.NULL -> hasNull = true
        else -> {
          if (firstTypeValue == null) {
            firstTypeValue =
              when (result.value?.valueTypeCase) {
                ValueTypeCase.ARRAY_VALUE -> result.value
                ValueTypeCase.STRING_VALUE -> result.value
                ValueTypeCase.BYTES_VALUE -> result.value
                else -> return@block EvaluateResultError
              }
          } else if (firstTypeValue.valueTypeCase != result.value?.valueTypeCase) {
            return@block EvaluateResultError
          }

          values.add(result.value!!)
        }
      }
    }

    if (hasNull) return@block EvaluateResult.NULL

    return@block when (firstTypeValue?.valueTypeCase) {
      ValueTypeCase.ARRAY_VALUE -> arrayConcatImpl(values.map { it.arrayValue.valuesList })
      ValueTypeCase.STRING_VALUE ->
        EvaluateResult.string(buildString { values.forEach { append(it.stringValue) } })
      ValueTypeCase.BYTES_VALUE -> bytesConcat(values.map { it.bytesValue })
      else -> throw IllegalStateException("Unreachable")
    }
  }
}

private fun bytesConcat(byteStrings: List<ByteString>) =
  EvaluateResult.value(
    encodeValue(byteStrings.map { it.toByteArray() }.reduce { acc, bytes -> acc + bytes })
  )

internal fun vectorLengthImpl(value: Value): Long =
  value.mapValue.fieldsMap[VECTOR_MAP_VECTORS_KEY]!!.arrayValue.valuesCount.toLong()
