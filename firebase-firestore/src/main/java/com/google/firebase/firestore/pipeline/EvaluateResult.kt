package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.model.Values
import com.google.firestore.v1.Value

internal sealed class EvaluateResult(val value: Value?) {
  companion object {
    val TRUE: EvaluateResultValue = EvaluateResultValue(Values.TRUE_VALUE)
    val FALSE: EvaluateResultValue = EvaluateResultValue(Values.FALSE_VALUE)
    val NULL: EvaluateResultValue = EvaluateResultValue(Values.NULL_VALUE)
    fun booleanValue(boolean: Boolean) = if (boolean) TRUE else FALSE
  }
}

internal object EvaluateResultError : EvaluateResult(null)

internal object EvaluateResultUnset : EvaluateResult(null)

internal class EvaluateResultValue(value: Value) : EvaluateResult(value)
