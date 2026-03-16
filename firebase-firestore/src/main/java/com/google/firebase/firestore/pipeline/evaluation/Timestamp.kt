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

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.common.math.LongMath.checkedAdd
import com.google.common.math.LongMath.checkedMultiply
import com.google.common.math.LongMath.checkedSubtract
import com.google.firebase.firestore.model.Values
import com.google.protobuf.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

// === Date / Timestamp Functions ===

private const val L_NANOS_PER_SECOND: Long = 1000_000_000
private const val I_NANOS_PER_SECOND: Int = 1000_000_000

private const val L_MICROS_PER_SECOND: Long = 1000_000
private const val I_MICROS_PER_SECOND: Int = 1000_000

private const val L_MILLIS_PER_SECOND: Long = 1000
private const val I_MILLIS_PER_SECOND: Int = 1000

// 0001-01-01T00:00:00Z
private const val TIMESTAMP_MIN_SECONDS = -62135596800L
// 9999-12-31T23:59:59Z - but the max timestamp has 999,999,999 nanoseconds
private const val TIMESTAMP_MAX_SECONDS = 253402300799L

// 0001-01-01T00:00:00.000Z
private const val TIMESTAMP_MIN_MILLISECONDS: Long = TIMESTAMP_MIN_SECONDS * L_MILLIS_PER_SECOND
// 9999-12-31T23:59:59.999Z - but the max timestamp has 999,999,999 nanoseconds
private const val TIMESTAMP_MAX_MILLISECONDS: Long =
  (TIMESTAMP_MAX_SECONDS * L_MILLIS_PER_SECOND) + (L_MILLIS_PER_SECOND - 1)

// 0001-01-01T00:00:00.000000Z
private const val TIMESTAMP_MIN_MICROSECONDS: Long = TIMESTAMP_MIN_SECONDS * L_MICROS_PER_SECOND
// 9999-12-31T23:59:59.999999Z - but the max timestamp has 999,999,999 nanoseconds
private const val TIMESTAMP_MAX_MICROSECONDS: Long =
  (TIMESTAMP_MAX_SECONDS * L_MICROS_PER_SECOND) + (L_MICROS_PER_SECOND - 1)

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

/**
 * Converts string units to [TemporalUnit].
 *
 * @return the converted unit
 * @throws IllegalArgumentException if `unit` is not among the list of recognized units.
 */
@RequiresApi(Build.VERSION_CODES.O)
fun convertUnit(unit: String): ChronoUnit {
  return when (unit) {
    "millisecond" -> ChronoUnit.MILLIS
    "microsecond" -> ChronoUnit.MICROS
    "second" -> ChronoUnit.SECONDS
    "minute" -> ChronoUnit.MINUTES
    "hour" -> ChronoUnit.HOURS
    "day" -> ChronoUnit.DAYS
    else -> throw IllegalArgumentException("Unexpected timestamp unit: " + unit)
  }
}

fun isTimestampInBounds(seconds: Long, nanos: Int): Boolean {
  if (seconds < TIMESTAMP_MIN_SECONDS || seconds > TIMESTAMP_MAX_SECONDS) {
    return false
  }
  if (nanos < 0 || nanos >= L_NANOS_PER_SECOND) {
    return false
  }

  return true
}

internal val evaluateTimestampAdd = ternaryTimestampFunction { t: Timestamp, u: String, n: Long ->
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val result = Instant.ofEpochSecond(t.seconds, t.nanos.toLong()).plus(n, convertUnit(u))
    if (!isTimestampInBounds(result.epochSecond, result.nano)) {
      return@ternaryTimestampFunction EvaluateResultError
    }
    EvaluateResult.timestamp(result.epochSecond, result.nano)
  } else {
    val result =
      when (u) {
        "microsecond" -> plus(t, n / L_MICROS_PER_SECOND, (n % L_MICROS_PER_SECOND) * 1000)
        "millisecond" -> plus(t, n / L_MILLIS_PER_SECOND, (n % L_MILLIS_PER_SECOND) * 1000_000)
        "second" -> plus(t, n)
        "minute" -> plus(t, checkedMultiply(n, 60))
        "hour" -> plus(t, checkedMultiply(n, 3600))
        "day" -> plus(t, checkedMultiply(n, 86400))
        else -> return@ternaryTimestampFunction EvaluateResultError
      }
    if (!isTimestampInBounds(result.seconds, result.nanos)) {
      return@ternaryTimestampFunction EvaluateResultError
    }
    EvaluateResult.timestamp(result)
  }
}

internal val evaluateTimestampSub = ternaryTimestampFunction { t: Timestamp, u: String, n: Long ->
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val result = Instant.ofEpochSecond(t.seconds, t.nanos.toLong()).minus(n, convertUnit(u))
    if (!isTimestampInBounds(result.epochSecond, result.nano)) {
      return@ternaryTimestampFunction EvaluateResultError
    }
    EvaluateResult.timestamp(result.epochSecond, result.nano)
  } else {
    val result =
      when (u) {
        "microsecond" -> minus(t, n / L_MICROS_PER_SECOND, (n % L_MICROS_PER_SECOND) * 1000)
        "millisecond" -> minus(t, n / L_MILLIS_PER_SECOND, (n % L_MILLIS_PER_SECOND) * 1000_000)
        "second" -> minus(t, n)
        "minute" -> minus(t, checkedMultiply(n, 60))
        "hour" -> minus(t, checkedMultiply(n, 3600))
        "day" -> minus(t, checkedMultiply(n, 86400))
        else -> return@ternaryTimestampFunction EvaluateResultError
      }
    if (!isTimestampInBounds(result.seconds, result.nanos)) {
      return@ternaryTimestampFunction EvaluateResultError
    }
    EvaluateResult.timestamp(result)
  }
}

internal val evaluateTimestampTrunc = notImplemented // TODO: Does not exist in expressions.kt yet.

internal val evaluateTimestampToUnixMicros = unaryFunction { t: Timestamp ->
  if (!isTimestampInBounds(t.seconds, t.nanos)) return@unaryFunction EvaluateResultError

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
  if (!isTimestampInBounds(t.seconds, t.nanos)) return@unaryFunction EvaluateResultError
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
  if (!isTimestampInBounds(t.seconds, t.nanos)) return@unaryFunction EvaluateResultError

  if (t.nanos !in 0 until L_NANOS_PER_SECOND) EvaluateResultError
  else EvaluateResult.long(t.seconds)
}

fun isMicrosecondsInTimestampBounds(microseconds: Long): Boolean {
  return (microseconds >= TIMESTAMP_MIN_MICROSECONDS) &&
    (microseconds <= TIMESTAMP_MAX_MICROSECONDS)
}

fun isMillisecondsInTimestampBounds(milliseconds: Long): Boolean {
  return (milliseconds >= TIMESTAMP_MIN_MILLISECONDS) &&
    (milliseconds <= TIMESTAMP_MAX_MILLISECONDS)
}

fun isSecondsInTimestampBounds(seconds: Long): Boolean {
  return (seconds >= TIMESTAMP_MIN_SECONDS) && (seconds <= TIMESTAMP_MAX_SECONDS)
}

internal val evaluateUnixMicrosToTimestamp = unaryFunction { micros: Long ->
  if (!isMicrosecondsInTimestampBounds(micros)) return@unaryFunction EvaluateResultError
  EvaluateResult.timestamp(
    Math.floorDiv(micros, L_MICROS_PER_SECOND),
    Math.floorMod(micros, I_MICROS_PER_SECOND) * 1000
  )
}

internal val evaluateUnixMillisToTimestamp = unaryFunction { millis: Long ->
  if (!isMillisecondsInTimestampBounds(millis)) return@unaryFunction EvaluateResultError
  EvaluateResult.timestamp(
    Math.floorDiv(millis, L_MILLIS_PER_SECOND),
    Math.floorMod(millis, I_MILLIS_PER_SECOND) * 1000_000
  )
}

internal val evaluateUnixSecondsToTimestamp = unaryFunction { seconds: Long ->
  if (!isSecondsInTimestampBounds(seconds)) return@unaryFunction EvaluateResultError
  EvaluateResult.timestamp(seconds, 0)
}
