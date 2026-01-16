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

import com.google.common.math.DoubleMath
import com.google.common.math.LongMath
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult.Companion.DOUBLE_ZERO
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult.Companion.LONG_ZERO
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

// === Arithmetic Functions ===

internal sealed interface FirestoreNumber

internal data class LongValue(val value: Long) : FirestoreNumber

internal data class DoubleValue(val value: Double) : FirestoreNumber

internal val evaluateAdd: EvaluateFunction = arithmeticPrimitive(LongMath::checkedAdd, Double::plus)

internal val evaluateCeil = arithmeticPrimitive({ it }, Math::ceil)

internal val evaluateDivide = arithmeticPrimitive(Long::div, Double::div)

internal val evaluateFloor = arithmeticPrimitive({ it }, Math::floor)

internal val evaluateMod = arithmeticPrimitive(Long::rem, Double::rem)

internal val evaluateMultiply: EvaluateFunction =
  arithmeticPrimitive(LongMath::checkedMultiply, Double::times)

internal val evaluatePow: EvaluateFunction =
  arithmetic({ base: Double, exponent: Double ->
    return@arithmetic if (exponent == 0.0 || base == 1.0) {
      EvaluateResult.double(1.0)
    } else if (base == -1.0 && exponent.isInfinite()) {
      EvaluateResult.double(1.0)
    }

    // Not referenced by GoogleSQL, but put here to be explicit.
    else if (exponent.isNaN() || base.isNaN()) {
      EvaluateResult.double(Double.NaN)
    }

    // We can't have a non-integer exponent on a negative base because it may result in taking the
    // undefined root of a negative number.
    else if (base < 0 && base.isFinite() && !DoubleMath.isMathematicalInteger(exponent)) {
      EvaluateResultError
    } else if ((base == 0.0 || base == -0.0) && exponent < 0) {
      EvaluateResultError
    } else EvaluateResult.double(base.pow(exponent))
  })

internal val evaluateRound =
  arithmeticPrimitive(
    { it },
    { input ->
      if (input.isFinite()) {
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
        return@arithmetic LONG_ZERO
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
      if (places >= 16 || !value.isFinite()) {
        return@arithmetic EvaluateResult.double(value)
      }

      // Predict and return when the rounded value will be 0, preventing edge cases where the
      // traditional conversion could underflow.
      val numDigits = floor(log10(value.absoluteValue)).toLong() + 1
      if (-places >= numDigits) {
        return@arithmetic DOUBLE_ZERO
      }

      val rounded: BigDecimal =
        BigDecimal.valueOf(value).setScale(places.toInt(), RoundingMode.HALF_UP)
      val result: Double = rounded.toDouble()

      if (result.isFinite()) EvaluateResult.double(result)
      else EvaluateResultError // overflow error
    }
  )

internal val evaluateAbs =
  arithmeticPrimitive(
    { l: Long ->
      if (l == Long.MIN_VALUE) throw ArithmeticException("long overflow")
      l.absoluteValue
    },
    { d: Double -> d.absoluteValue }
  )

internal val evaluateExp = arithmetic { value: Double ->
  val result = exp(value)
  // Returning an error on double overflow (characterized by a non-infinite exponent returning an
  // infinite result).
  if (result == Double.POSITIVE_INFINITY && value != Double.POSITIVE_INFINITY) {
    throw Exception("exp(...) exponent overflow")
  }
  EvaluateResult.double(exp(value))
}

internal val evaluateLn = arithmetic { value: Double ->
  if (value <= 0) EvaluateResultError else EvaluateResult.double(ln(value))
}

internal val evaluateLog = arithmetic { value: Double, base: Double ->
  return@arithmetic if (value == Double.NEGATIVE_INFINITY) {
    EvaluateResult.double(Double.NaN)
  } else if (base == Double.POSITIVE_INFINITY) {
    EvaluateResult.double(Double.NaN)
  } else if (base <= 0 || value <= 0 || base == 1.0) {
    EvaluateResultError
  } else EvaluateResult.double(log(value, base))
}

internal val evaluateLog10 = arithmetic { value: Double ->
  if (value <= 0) EvaluateResultError else EvaluateResult.double(log10(value))
}

internal val evaluateSqrt = arithmetic { value: Double ->
  if (value < 0) EvaluateResultError else EvaluateResult.double(sqrt(value))
}

internal val evaluateSubtract = arithmeticPrimitive(LongMath::checkedSubtract, Double::minus)
