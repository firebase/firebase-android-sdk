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

import com.google.common.math.LongMath.checkedAdd
import com.google.common.math.LongMath.checkedMultiply
import com.google.common.math.LongMath.checkedSubtract
import com.google.firebase.firestore.model.Values
import com.google.protobuf.Timestamp

// === Date / Timestamp Functions ===

private const val L_NANOS_PER_SECOND: Long = 1000_000_000
private const val I_NANOS_PER_SECOND: Int = 1000_000_000

private const val L_MICROS_PER_SECOND: Long = 1000_000
private const val I_MICROS_PER_SECOND: Int = 1000_000

private const val L_MILLIS_PER_SECOND: Long = 1000
private const val I_MILLIS_PER_SECOND: Int = 1000

internal fun plus(t: Timestamp, seconds: Long, nanos: Long): Timestamp =
  if (nanos == 0L) {
    plus(t, seconds)
  } else {
    val nanoSum = t.nanos + nanos // Overflow not possible since nanos is 0 to 1 000 000.
    val secondsSum: Long = checkedAdd(checkedAdd(t.seconds, seconds), nanoSum / L_NANOS_PER_SECOND)
    Values.timestamp(secondsSum, (nanoSum % I_NANOS_PER_SECOND).toInt())
  }

private fun plus(t: Timestamp, seconds: Long): Timestamp =
  if (seconds == 0L) t else Values.timestamp(checkedAdd(t.seconds, seconds), t.nanos)

internal fun minus(t: Timestamp, seconds: Long, nanos: Long): Timestamp =
  if (nanos == 0L) {
    minus(t, seconds)
  } else {
    val nanoSum = t.nanos - nanos // Overflow not possible since nanos is 0 to 1 000 000.
    val secondsSum: Long =
      checkedSubtract(t.seconds, checkedSubtract(seconds, nanoSum / L_NANOS_PER_SECOND))
    Values.timestamp(secondsSum, (nanoSum % I_NANOS_PER_SECOND).toInt())
  }

private fun minus(t: Timestamp, seconds: Long): Timestamp =
  if (seconds == 0L) t else Values.timestamp(checkedSubtract(t.seconds, seconds), t.nanos)

internal val evaluateTimestampAdd = ternaryTimestampFunction { t: Timestamp, u: String, n: Long ->
  EvaluateResult.timestamp(
    when (u) {
      "microsecond" -> plus(t, n / L_MICROS_PER_SECOND, (n % L_MICROS_PER_SECOND) * 1000)
      "millisecond" -> plus(t, n / L_MILLIS_PER_SECOND, (n % L_MILLIS_PER_SECOND) * 1000_000)
      "second" -> plus(t, n)
      "minute" -> plus(t, checkedMultiply(n, 60))
      "hour" -> plus(t, checkedMultiply(n, 3600))
      "day" -> plus(t, checkedMultiply(n, 86400))
      else -> return@ternaryTimestampFunction EvaluateResultError
    }
  )
}

internal val evaluateTimestampSub = ternaryTimestampFunction { t: Timestamp, u: String, n: Long ->
  EvaluateResult.timestamp(
    when (u) {
      "microsecond" -> minus(t, n / L_MICROS_PER_SECOND, (n % L_MICROS_PER_SECOND) * 1000)
      "millisecond" -> minus(t, n / L_MILLIS_PER_SECOND, (n % L_MILLIS_PER_SECOND) * 1000_000)
      "second" -> minus(t, n)
      "minute" -> minus(t, checkedMultiply(n, 60))
      "hour" -> minus(t, checkedMultiply(n, 3600))
      "day" -> minus(t, checkedMultiply(n, 86400))
      else -> return@ternaryTimestampFunction EvaluateResultError
    }
  )
}

internal val evaluateTimestampTrunc = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateTimestampToUnixMicros = unaryFunction { t: Timestamp ->
  EvaluateResult.long(
    if (t.seconds < Long.MIN_VALUE / 1_000_000) {
      // To avoid overflow when very close to Long.MIN_VALUE, add 1 second, multiply, then subtract
      // again.
      val micros = checkedMultiply(t.seconds + 1, L_MICROS_PER_SECOND)
      val adjustment = t.nanos.toLong() / L_MILLIS_PER_SECOND - L_MICROS_PER_SECOND
      checkedAdd(micros, adjustment)
    } else {
      val micros = checkedMultiply(t.seconds, L_MICROS_PER_SECOND)
      checkedAdd(micros, t.nanos.toLong() / L_MILLIS_PER_SECOND)
    }
  )
}

internal val evaluateTimestampToUnixMillis = unaryFunction { t: Timestamp ->
  EvaluateResult.long(
    if (t.seconds < 0 && t.nanos > 0) {
      val millis = checkedMultiply(t.seconds + 1, L_MILLIS_PER_SECOND)
      val adjustment = t.nanos.toLong() / L_MICROS_PER_SECOND - L_MILLIS_PER_SECOND
      checkedAdd(millis, adjustment)
    } else {
      val millis = checkedMultiply(t.seconds, L_MILLIS_PER_SECOND)
      checkedAdd(millis, t.nanos.toLong() / L_MICROS_PER_SECOND)
    }
  )
}

internal val evaluateTimestampToUnixSeconds = unaryFunction { t: Timestamp ->
  if (t.nanos !in 0 until L_NANOS_PER_SECOND) EvaluateResultError
  else EvaluateResult.long(t.seconds)
}

internal val evaluateUnixMicrosToTimestamp = unaryFunction { micros: Long ->
  EvaluateResult.timestamp(
    Math.floorDiv(micros, L_MICROS_PER_SECOND),
    Math.floorMod(micros, I_MICROS_PER_SECOND) * 1000
  )
}

internal val evaluateUnixMillisToTimestamp = unaryFunction { millis: Long ->
  EvaluateResult.timestamp(
    Math.floorDiv(millis, L_MILLIS_PER_SECOND),
    Math.floorMod(millis, I_MILLIS_PER_SECOND) * 1000_000
  )
}

internal val evaluateUnixSecondsToTimestamp = unaryFunction { seconds: Long ->
  EvaluateResult.timestamp(seconds, 0)
}
