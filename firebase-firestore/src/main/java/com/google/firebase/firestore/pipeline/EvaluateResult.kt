package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firestore.v1.Value
import com.google.protobuf.Timestamp

internal sealed class EvaluateResult(val value: Value?) {
  abstract val isError: Boolean

  companion object {
    val TRUE: EvaluateResultValue = EvaluateResultValue(Values.TRUE_VALUE)
    val FALSE: EvaluateResultValue = EvaluateResultValue(Values.FALSE_VALUE)
    val NULL: EvaluateResultValue = EvaluateResultValue(Values.NULL_VALUE)
    val DOUBLE_ZERO: EvaluateResultValue = double(0.0)
    val LONG_ZERO: EvaluateResultValue = long(0)
    fun boolean(boolean: Boolean) = if (boolean) TRUE else FALSE
    fun double(double: Double) = EvaluateResultValue(encodeValue(double))
    fun long(long: Long) = EvaluateResultValue(encodeValue(long))
    fun long(int: Int) = EvaluateResultValue(encodeValue(int.toLong()))
    fun string(string: String) = EvaluateResultValue(encodeValue(string))
    fun list(list: List<Value>) = EvaluateResultValue(encodeValue(list))
    fun timestamp(timestamp: Timestamp): EvaluateResult =
      EvaluateResultValue(encodeValue(timestamp))
    fun timestamp(seconds: Long, nanos: Int): EvaluateResult =
      if (seconds !in -62_135_596_800 until 253_402_300_800) EvaluateResultError
      else timestamp(Values.timestamp(seconds, nanos))
  }
}

internal object EvaluateResultError : EvaluateResult(null) {
  override val isError: Boolean = true
}

internal object EvaluateResultUnset : EvaluateResult(null) {
  override val isError: Boolean = false
}

internal class EvaluateResultValue(value: Value) : EvaluateResult(value) {
  override val isError: Boolean = false
}
