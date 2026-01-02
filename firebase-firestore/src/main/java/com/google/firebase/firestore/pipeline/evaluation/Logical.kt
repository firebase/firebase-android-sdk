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
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult.Companion.FALSE
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult.Companion.NULL
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult.Companion.TRUE
import com.google.firebase.firestore.util.Assert
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeCase

// === Logical Functions ===

internal val evaluateAnd: EvaluateFunction = { params ->
  fun(input: MutableDocument): EvaluateResult {
    var isNull = false
    for (param in params) {
      val result = param(input)
      if (result.isError) return EvaluateResultError

      val value = result.value
      when (value?.valueTypeCase) {
        null,
        ValueTypeCase.NULL_VALUE -> isNull = true
        ValueTypeCase.BOOLEAN_VALUE -> {
          if (!value.booleanValue) return FALSE
        }
        else -> return EvaluateResultError
      }
    }
    return if (isNull) NULL else TRUE
  }
}

internal val evaluateOr: EvaluateFunction = { params ->
  fun(input: MutableDocument): EvaluateResult {
    var isNull = false
    for (param in params) {
      val result = param(input)
      if (result.isError) return EvaluateResultError
      val value = result.value
      when (value?.valueTypeCase) {
        null,
        ValueTypeCase.NULL_VALUE -> isNull = true
        ValueTypeCase.BOOLEAN_VALUE -> {
          if (value.booleanValue) return TRUE
        }
        else -> return EvaluateResultError
      }
    }
    return if (isNull) NULL else FALSE
  }
}

internal val evaluateXor: EvaluateFunction = variadicFunction { values: BooleanArray ->
  EvaluateResult.boolean(values.fold(false, Boolean::xor))
}

internal val evaluateCond: EvaluateFunction = ternaryLazyFunction { p1, p2, p3 ->
  val r1 = p1()
  if (r1.isError) return@ternaryLazyFunction EvaluateResultError

  val v1 = r1.value
  when (v1?.valueTypeCase) {
    ValueTypeCase.BOOLEAN_VALUE -> if (v1.booleanValue) p2() else p3()
    null,
    ValueTypeCase.NULL_VALUE -> p3()
    else -> EvaluateResultError
  }
}

internal val evaluateLogicalMaximum: EvaluateFunction =
  variadicResultFunction { params: List<EvaluateResult> ->
    if (params.size < 2) return@variadicResultFunction EvaluateResultError

    val maximum = { a: Value?, b: Value ->
      if (a === null) b
      else {
        val result = Values.Enterprise.compare(a, b)
        if (result == 0) a else if (result > 0) a else b
      }
    }

    var maxResult: Value? = null
    for (param in params) {
      if (param.isError) return@variadicResultFunction EvaluateResultError
      val value = param.value
      when (value?.valueTypeCase) {
        null,
        ValueTypeCase.NULL_VALUE -> {}
        else -> maxResult = maximum(maxResult, value)
      }
    }
    if (maxResult === null) NULL else EvaluateResult.value(maxResult)
  }

internal val evaluateLogicalMinimum: EvaluateFunction =
  variadicResultFunction { params: List<EvaluateResult> ->
    if (params.size < 2) return@variadicResultFunction EvaluateResultError

    val minimum = { a: Value?, b: Value ->
      if (a === null) b
      else {
        val result = Values.Enterprise.compare(a, b)
        if (result == 0) a else if (result > 0) b else a
      }
    }

    var minResult: Value? = null
    for (param in params) {
      if (param.isError) return@variadicResultFunction EvaluateResultError
      val value = param.value
      when (value?.valueTypeCase) {
        null,
        ValueTypeCase.NULL_VALUE -> {}
        else -> minResult = minimum(minResult, value)
      }
    }
    if (minResult === null) NULL else EvaluateResult.value(minResult)
  }

// === Type Functions ===

internal val evaluateIsNaN: EvaluateFunction =
  arithmetic({ _: Long -> FALSE }, { v: Double -> EvaluateResult.boolean(v.isNaN()) })

internal val evaluateIsNotNaN: EvaluateFunction =
  arithmetic({ _: Long -> TRUE }, { v: Double -> EvaluateResult.boolean(!v.isNaN()) })

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
