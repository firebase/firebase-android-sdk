package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.util.Assert
import com.google.firestore.v1.Value

internal class EvaluationContext(val userDataReader: UserDataReader)

internal fun interface EvaluateFunction {
  fun evaluate(params: Sequence<EvaluateResult>): EvaluateResult
}

private fun evaluateValue(
  params: Sequence<EvaluateResult>,
  next: (value: Value) -> EvaluateResult?,
  complete: () -> EvaluateResult
): EvaluateResult {
  for (value in params.map(EvaluateResult::value)) {
    if (value == null) return EvaluateResultError
    val result = next(value)
    if (result != null) return result
  }
  return complete()
}

private fun evaluateValueShortCircuitNull(
  function: (values: List<Value>) -> EvaluateResult
): EvaluateFunction {
  return object : EvaluateFunction {
    override fun evaluate(params: Sequence<EvaluateResult>): EvaluateResult {
      val values = buildList {
        for (value in params.map(EvaluateResult::value)) {
          if (value == null) return EvaluateResultError
          if (value.hasNullValue()) return EvaluateResult.NULL
          add(value)
        }
      }
      return function.invoke(values)
    }
  }
}

private fun evaluateBooleanValue(
  function: (values: List<Boolean>) -> EvaluateResult
): EvaluateFunction {
  return object : EvaluateFunction {
    override fun evaluate(params: Sequence<EvaluateResult>): EvaluateResult {
      val values = buildList {
        for (value in params.map(EvaluateResult::value)) {
          if (value == null) return EvaluateResultError
          if (value.hasNullValue()) return EvaluateResult.NULL
          if (!value.hasBooleanValue()) return EvaluateResultError
          add(value.booleanValue)
        }
      }
      return function.invoke(values)
    }
  }
}

private fun evaluateBooleanValue(
  params: Sequence<EvaluateResult>,
  next: (value: Boolean) -> Boolean,
  complete: () -> EvaluateResult
): EvaluateResult {
  for (value in params.map(EvaluateResult::value)) {
    if (value == null) return EvaluateResultError
    if (value.hasNullValue()) return EvaluateResult.NULL
    if (!value.hasBooleanValue()) return EvaluateResultError
    if (!next(value.booleanValue)) break
  }
  return complete()
}

internal val evaluateNotImplemented = EvaluateFunction { _ -> throw NotImplementedError() }

internal val evaluateAnd = EvaluateFunction { params ->
  var result: EvaluateResult = EvaluateResult.TRUE
  evaluateValue(
    params,
    fun(value: Value): EvaluateResult? {
      when (value.valueTypeCase) {
        Value.ValueTypeCase.NULL_VALUE -> result = EvaluateResult.NULL
        Value.ValueTypeCase.BOOLEAN_VALUE -> {
          if (!value.booleanValue) return EvaluateResult.FALSE
        }
        else -> return EvaluateResultError
      }
      return null
    },
    { result }
  )
}
internal val evaluateOr = EvaluateFunction { params ->
  var result: EvaluateResult = EvaluateResult.FALSE
  evaluateValue(
    params,
    fun(value: Value): EvaluateResult? {
      when (value.valueTypeCase) {
        Value.ValueTypeCase.NULL_VALUE -> result = EvaluateResult.NULL
        Value.ValueTypeCase.BOOLEAN_VALUE -> {
          if (value.booleanValue) return EvaluateResult.TRUE
        }
        else -> return EvaluateResultError
      }
      return null
    },
    { result }
  )
}
internal val evaluateXor = evaluateBooleanValue { params ->
  EvaluateResult.booleanValue(params.fold(false, Boolean::xor))
}
internal val evaluateEq = evaluateValueShortCircuitNull { values ->
  Assert.hardAssert(values.size == 2, "Eq function should have exactly 2 params")
  EvaluateResult.booleanValue(Values.equals(values.get(0), values.get(1)))
}
