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

package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firestore.v1.Value
import com.google.protobuf.Timestamp

internal sealed class EvaluateResult {
  abstract val value: Value?
  abstract val isError: Boolean
  abstract val isSuccess: Boolean
  abstract val isUnset: Boolean

  companion object {
    val TRUE: EvaluateResultValue = EvaluateResultValue(Values.TRUE_VALUE)
    val FALSE: EvaluateResultValue = EvaluateResultValue(Values.FALSE_VALUE)
    val NULL: EvaluateResultValue = EvaluateResultValue(Values.NULL_VALUE)
    val DOUBLE_ZERO: EvaluateResultValue = double(0.0)
    val LONG_ZERO: EvaluateResultValue = long(0)
    fun boolean(boolean: Boolean?) = if (boolean === null) NULL else boolean(boolean)
    fun boolean(boolean: Boolean) = if (boolean) TRUE else FALSE
    fun double(double: Double) = EvaluateResultValue(encodeValue(double))
    fun long(long: Long) = EvaluateResultValue(encodeValue(long))
    fun long(int: Int) = EvaluateResultValue(encodeValue(int.toLong()))
    fun string(string: String) = EvaluateResultValue(encodeValue(string))
    fun list(list: List<Value>) = EvaluateResultValue(encodeValue(list))
    fun timestamp(timestamp: Timestamp): EvaluateResult =
      EvaluateResultValue(encodeValue(timestamp))
    fun timestamp(seconds: Long, nanos: Int): EvaluateResult =
      try {
        timestamp(Values.timestamp(seconds, nanos))
      } catch (e: IllegalArgumentException) {
        EvaluateResultError
      }
    fun value(value: Value) = EvaluateResultValue(value)
  }
}

internal data class EvaluateResultValue(override val value: Value) : EvaluateResult() {
  override val isSuccess: Boolean = true
  override val isError: Boolean = false
  override val isUnset: Boolean = false
}

internal object EvaluateResultError : EvaluateResult() {
  override val value: Value? = null
  override val isSuccess: Boolean = false
  override val isError: Boolean = true
  override val isUnset: Boolean = false
}

internal object EvaluateResultUnset : EvaluateResult() {
  override val value: Value? = null
  override val isSuccess: Boolean = false
  override val isError: Boolean = false
  override val isUnset: Boolean = true
}
