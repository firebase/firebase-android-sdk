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

import com.google.firebase.Timestamp
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.nullValue // For null constant
import com.google.firebase.firestore.pipeline.Expr.Companion.timestampAdd
import com.google.firebase.firestore.pipeline.Expr.Companion.timestampToUnixMicros
import com.google.firebase.firestore.pipeline.Expr.Companion.timestampToUnixMillis
import com.google.firebase.firestore.pipeline.Expr.Companion.timestampToUnixSeconds
import com.google.firebase.firestore.pipeline.Expr.Companion.unixMicrosToTimestamp
import com.google.firebase.firestore.pipeline.Expr.Companion.unixMillisToTimestamp
import com.google.firebase.firestore.pipeline.Expr.Companion.unixSecondsToTimestamp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TimestampTests {

  // --- UnixMicrosToTimestamp Tests ---

  @Test
  fun unixMicrosToTimestamp_stringType_returnsError() {
    val expr = unixMicrosToTimestamp(constant("abc"))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "unixMicrosToTimestamp(\"abc\")")
  }

  @Test
  fun unixMicrosToTimestamp_zeroValue_returnsTimestampEpoch() {
    val expr = unixMicrosToTimestamp(constant(0L))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(Timestamp(0, 0)), "unixMicrosToTimestamp(0L)")
  }

  @Test
  fun unixMicrosToTimestamp_intType_returnsTimestamp() {
    // C++ test uses 1000000LL, which is 1 second
    val expr = unixMicrosToTimestamp(constant(1000000L))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(Timestamp(1, 0)), "unixMicrosToTimestamp(1000000L)")
  }

  @Test
  fun unixMicrosToTimestamp_longType_returnsTimestamp() {
    // C++ test uses 9876543210LL micros
    // 9876543210 / 1,000,000 = 9876 seconds
    // 9876543210 % 1,000,000 = 543210 micros = 543210000 nanos
    val expr = unixMicrosToTimestamp(constant(9876543210L))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(Timestamp(9876, 543210000)),
      "unixMicrosToTimestamp(9876543210L)"
    )
  }

  @Test
  fun unixMicrosToTimestamp_longTypeNegative_returnsTimestamp() {
    // -10000 micros = -0.01 seconds
    // seconds = -1 (floor of -0.01)
    // remaining_micros = -10000 - (-1 * 1,000,000) = -10000 + 1,000,000 = 990,000 micros
    // nanos = 990,000 * 1000 = 990,000,000 nanos
    val expr = unixMicrosToTimestamp(constant(-10000L))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(Timestamp(-1, 990000000)),
      "unixMicrosToTimestamp(-10000L)"
    )
  }

  @Test
  fun unixMicrosToTimestamp_longTypeNegativeOverflow_returnsError() {
    // Min representable timestamp: seconds=-62135596800, nanos=0
    // Corresponds to micros: -62135596800 * 1,000,000 = -62135596800000000
    val minMicros = -62135596800000000L

    // Test the boundary value
    val boundaryExpr = unixMicrosToTimestamp(constant(minMicros))
    val boundaryResult = evaluate(boundaryExpr)
    assertEvaluatesTo(
      boundaryResult,
      encodeValue(Timestamp(-62135596800L, 0)),
      "unixMicrosToTimestamp(minMicros)"
    )

    // Test value just below the boundary (minMicros - 1)
    // The C++ test uses SubtractExpr for this, we can do it directly.
    val belowMinExpr = unixMicrosToTimestamp(constant(minMicros - 1))
    val belowMinResult = evaluate(belowMinExpr)
    assertEvaluatesToError(belowMinResult, "unixMicrosToTimestamp(minMicros - 1)")
  }

  @Test
  fun unixMicrosToTimestamp_longTypePositiveOverflow_returnsError() {
    // Max representable timestamp: seconds=253402300799, nanos=999999999
    // Corresponds to micros: 253402300799 * 1,000,000 + 999999 (since nanos are truncated to
    // micros)
    // = 253402300799000000 + 999999 = 253402300799999999
    val maxMicros = 253402300799999999L

    // Test the boundary value
    // Nanos are 999999000 because 999999 micros * 1000 = 999999000 nanos
    val boundaryExpr = unixMicrosToTimestamp(constant(maxMicros))
    val boundaryResult = evaluate(boundaryExpr)
    assertEvaluatesTo(
      boundaryResult,
      encodeValue(Timestamp(253402300799L, 999999000)), // Nanos from 999999 micros
      "unixMicrosToTimestamp(maxMicros)"
    )

    // Test value just above the boundary (maxMicros + 1)
    val aboveMaxExpr = unixMicrosToTimestamp(constant(maxMicros + 1))
    val aboveMaxResult = evaluate(aboveMaxExpr)
    assertEvaluatesToError(aboveMaxResult, "unixMicrosToTimestamp(maxMicros + 1)")
  }

  // --- UnixMillisToTimestamp Tests ---

  @Test
  fun unixMillisToTimestamp_stringType_returnsError() {
    val expr = unixMillisToTimestamp(constant("abc"))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "unixMillisToTimestamp(\"abc\")")
  }

  @Test
  fun unixMillisToTimestamp_zeroValue_returnsTimestampEpoch() {
    val expr = unixMillisToTimestamp(constant(0L))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(Timestamp(0, 0)), "unixMillisToTimestamp(0L)")
  }

  @Test
  fun unixMillisToTimestamp_intType_returnsTimestamp() {
    // C++ test uses 1000LL, which is 1 second
    val expr = unixMillisToTimestamp(constant(1000L))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(Timestamp(1, 0)), "unixMillisToTimestamp(1000L)")
  }

  @Test
  fun unixMillisToTimestamp_longType_returnsTimestamp() {
    // C++ test uses 9876543210LL millis
    // 9876543210 / 1000 = 9876543 seconds
    // 9876543210 % 1000 = 210 millis = 210000000 nanos
    val expr = unixMillisToTimestamp(constant(9876543210L))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(Timestamp(9876543, 210000000)),
      "unixMillisToTimestamp(9876543210L)"
    )
  }

  @Test
  fun unixMillisToTimestamp_longTypeNegative_returnsTimestamp() {
    // -10000 millis = -10 seconds
    val expr = unixMillisToTimestamp(constant(-10000L))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(Timestamp(-10, 0)), "unixMillisToTimestamp(-10000L)")
  }

  @Test
  fun unixMillisToTimestamp_longTypeNegativeOverflow_returnsError() {
    // Min representable timestamp: seconds=-62135596800, nanos=0
    // Corresponds to millis: -62135596800 * 1000 = -62135596800000
    val minMillis = -62135596800000L

    // Test the boundary value
    val boundaryExpr = unixMillisToTimestamp(constant(minMillis))
    val boundaryResult = evaluate(boundaryExpr)
    assertEvaluatesTo(
      boundaryResult,
      encodeValue(Timestamp(-62135596800L, 0)),
      "unixMillisToTimestamp(minMillis)"
    )

    // Test value just below the boundary (minMillis - 1)
    val belowMinExpr = unixMillisToTimestamp(constant(minMillis - 1))
    val belowMinResult = evaluate(belowMinExpr)
    assertEvaluatesToError(belowMinResult, "unixMillisToTimestamp(minMillis - 1)")
  }

  @Test
  fun unixMillisToTimestamp_longTypePositiveOverflow_returnsError() {
    // Max representable timestamp: seconds=253402300799, nanos=999999999
    // Corresponds to millis: 253402300799 * 1000 + 999 (since nanos are truncated to millis)
    // = 253402300799000 + 999 = 253402300799999
    val maxMillis = 253402300799999L

    // Test the boundary value
    // Nanos are 999000000 because 999 millis * 1,000,000 = 999,000,000 nanos
    val boundaryExpr = unixMillisToTimestamp(constant(maxMillis))
    val boundaryResult = evaluate(boundaryExpr)
    assertEvaluatesTo(
      boundaryResult,
      encodeValue(Timestamp(253402300799L, 999000000)), // Nanos from 999 millis
      "unixMillisToTimestamp(maxMillis)"
    )

    // Test value just above the boundary (maxMillis + 1)
    val aboveMaxExpr = unixMillisToTimestamp(constant(maxMillis + 1))
    val aboveMaxResult = evaluate(aboveMaxExpr)
    assertEvaluatesToError(aboveMaxResult, "unixMillisToTimestamp(maxMillis + 1)")
  }

  // --- UnixSecondsToTimestamp Tests ---

  @Test
  fun unixSecondsToTimestamp_stringType_returnsError() {
    val expr = unixSecondsToTimestamp(constant("abc"))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "unixSecondsToTimestamp(\"abc\")")
  }

  @Test
  fun unixSecondsToTimestamp_zeroValue_returnsTimestampEpoch() {
    val expr = unixSecondsToTimestamp(constant(0L))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(Timestamp(0, 0)), "unixSecondsToTimestamp(0L)")
  }

  @Test
  fun unixSecondsToTimestamp_intType_returnsTimestamp() {
    val expr = unixSecondsToTimestamp(constant(1L))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(Timestamp(1, 0)), "unixSecondsToTimestamp(1L)")
  }

  @Test
  fun unixSecondsToTimestamp_longType_returnsTimestamp() {
    val expr = unixSecondsToTimestamp(constant(9876543210L))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(Timestamp(9876543210L, 0)),
      "unixSecondsToTimestamp(9876543210L)"
    )
  }

  @Test
  fun unixSecondsToTimestamp_longTypeNegative_returnsTimestamp() {
    val expr = unixSecondsToTimestamp(constant(-10000L))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(Timestamp(-10000L, 0)), "unixSecondsToTimestamp(-10000L)")
  }

  @Test
  fun unixSecondsToTimestamp_longTypeNegativeOverflow_returnsError() {
    // Min representable timestamp: seconds=-62135596800, nanos=0
    val minSeconds = -62135596800L

    // Test the boundary value
    val boundaryExpr = unixSecondsToTimestamp(constant(minSeconds))
    val boundaryResult = evaluate(boundaryExpr)
    assertEvaluatesTo(
      boundaryResult,
      encodeValue(Timestamp(minSeconds, 0)),
      "unixSecondsToTimestamp(minSeconds)"
    )

    // Test value just below the boundary (minSeconds - 1)
    val belowMinExpr = unixSecondsToTimestamp(constant(minSeconds - 1))
    val belowMinResult = evaluate(belowMinExpr)
    assertEvaluatesToError(belowMinResult, "unixSecondsToTimestamp(minSeconds - 1)")
  }

  @Test
  fun unixSecondsToTimestamp_longTypePositiveOverflow_returnsError() {
    // Max representable timestamp: seconds=253402300799, nanos=999999999
    // For UnixSecondsToTimestamp, we only care about the seconds part for overflow.
    val maxSeconds = 253402300799L

    // Test the boundary value
    val boundaryExpr = unixSecondsToTimestamp(constant(maxSeconds))
    val boundaryResult = evaluate(boundaryExpr)
    assertEvaluatesTo(
      boundaryResult,
      encodeValue(Timestamp(maxSeconds, 0)),
      "unixSecondsToTimestamp(maxSeconds)"
    )

    // Test value just above the boundary (maxSeconds + 1)
    val aboveMaxExpr = unixSecondsToTimestamp(constant(maxSeconds + 1))
    val aboveMaxResult = evaluate(aboveMaxExpr)
    assertEvaluatesToError(aboveMaxResult, "unixSecondsToTimestamp(maxSeconds + 1)")
  }

  // --- TimestampToUnixMicros Tests ---

  @Test
  fun timestampToUnixMicros_nonTimestampType_returnsError() {
    val expr = timestampToUnixMicros(constant(123L))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "timestampToUnixMicros(123L)")
  }

  @Test
  fun timestampToUnixMicros_timestamp_returnsMicros() {
    val ts = Timestamp(347068800, 0) // March 1, 1981 00:00:00 UTC
    val expr = timestampToUnixMicros(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(347068800000000L),
      "timestampToUnixMicros(Timestamp(347068800, 0))"
    )
  }

  @Test
  fun timestampToUnixMicros_epochTimestamp_returnsMicros() {
    val ts = Timestamp(0, 0)
    val expr = timestampToUnixMicros(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(0L), "timestampToUnixMicros(Timestamp(0, 0))")
  }

  @Test
  fun timestampToUnixMicros_currentTimestamp_returnsMicros() {
    // Example: March 15, 2023 12:00:00.123456 UTC
    val ts = Timestamp(1678886400, 123456000)
    val expectedMicros = 1678886400L * 1000000L + 123456L
    val expr = timestampToUnixMicros(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(expectedMicros),
      "timestampToUnixMicros(Timestamp(1678886400, 123456000))"
    )
  }

  @Test
  fun timestampToUnixMicros_maxTimestamp_returnsMicros() {
    // Max representable timestamp: seconds=253402300799, nanos=999999999
    val maxTs = Timestamp(253402300799L, 999999999)
    // Expected micros: 253402300799 * 1,000,000 + 999999 (nanos truncated to micros)
    val expectedMicros = 253402300799L * 1000000L + 999999L
    val expr = timestampToUnixMicros(constant(maxTs))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(expectedMicros), "timestampToUnixMicros(maxTimestamp)")
  }

  @Test
  fun timestampToUnixMicros_minTimestamp_returnsMicros() {
    // Min representable timestamp: seconds=-62135596800, nanos=0
    val minTs = Timestamp(-62135596800L, 0)
    // Expected micros: -62135596800 * 1,000,000 = -62135596800000000
    val expectedMicros = -62135596800L * 1000000L
    val expr = timestampToUnixMicros(constant(minTs))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(expectedMicros), "timestampToUnixMicros(minTimestamp)")
  }

  @Test
  fun timestampToUnixMicros_timestampTruncatesToMicros() {
    // Timestamp: seconds=-1, nanos=999999999 (which is 999999.999 micros)
    // Expected Micros: -1 * 1,000,000 + 999999 = -1
    val ts = Timestamp(-1, 999999999)
    val expr = timestampToUnixMicros(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(-1L), "timestampToUnixMicros(Timestamp(-1, 999999999))")
  }

  // --- TimestampToUnixMillis Tests ---

  @Test
  fun timestampToUnixMillis_nonTimestampType_returnsError() {
    val expr = timestampToUnixMillis(constant(123L))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "timestampToUnixMillis(123L)")
  }

  @Test
  fun timestampToUnixMillis_timestamp_returnsMillis() {
    val ts = Timestamp(347068800, 0) // March 1, 1981 00:00:00 UTC
    val expr = timestampToUnixMillis(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(347068800000L),
      "timestampToUnixMillis(Timestamp(347068800, 0))"
    )
  }

  @Test
  fun timestampToUnixMillis_epochTimestamp_returnsMillis() {
    val ts = Timestamp(0, 0)
    val expr = timestampToUnixMillis(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(0L), "timestampToUnixMillis(Timestamp(0, 0))")
  }

  @Test
  fun timestampToUnixMillis_currentTimestamp_returnsMillis() {
    // Example: March 15, 2023 12:00:00.123 UTC
    val ts = Timestamp(1678886400, 123000000)
    val expectedMillis = 1678886400L * 1000L + 123L
    val expr = timestampToUnixMillis(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(expectedMillis),
      "timestampToUnixMillis(Timestamp(1678886400, 123000000))"
    )
  }

  @Test
  fun timestampToUnixMillis_maxTimestamp_returnsMillis() {
    // Max representable timestamp: seconds=253402300799, nanos=999999999
    // Millis calculation truncates nanos part: 999999999 / 1,000,000 = 999
    val maxTs = Timestamp(253402300799L, 999000000) // Nanos for 999ms
    val expectedMillis = 253402300799L * 1000L + 999L
    val expr = timestampToUnixMillis(constant(maxTs))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(expectedMillis), "timestampToUnixMillis(maxTimestamp)")
  }

  @Test
  fun timestampToUnixMillis_minTimestamp_returnsMillis() {
    // Min representable timestamp: seconds=-62135596800, nanos=0
    val minTs = Timestamp(-62135596800L, 0)
    val expectedMillis = -62135596800L * 1000L
    val expr = timestampToUnixMillis(constant(minTs))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(expectedMillis), "timestampToUnixMillis(minTimestamp)")
  }

  @Test
  fun timestampToUnixMillis_timestampTruncatesToMillis() {
    // Timestamp: seconds=-1, nanos=999999999 (which is 999.999999 ms)
    // Expected Millis: -1 * 1000 + 999 = -1
    val ts = Timestamp(-1, 999999999)
    val expr = timestampToUnixMillis(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(-1L), "timestampToUnixMillis(Timestamp(-1, 999999999))")
  }

  // --- TimestampToUnixSeconds Tests ---

  @Test
  fun timestampToUnixSeconds_nonTimestampType_returnsError() {
    val expr = timestampToUnixSeconds(constant(123L))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "timestampToUnixSeconds(123L)")
  }

  @Test
  fun timestampToUnixSeconds_timestamp_returnsSeconds() {
    val ts = Timestamp(347068800, 0) // March 1, 1981 00:00:00 UTC
    val expr = timestampToUnixSeconds(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(347068800L),
      "timestampToUnixSeconds(Timestamp(347068800, 0))"
    )
  }

  @Test
  fun timestampToUnixSeconds_epochTimestamp_returnsSeconds() {
    val ts = Timestamp(0, 0)
    val expr = timestampToUnixSeconds(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(0L), "timestampToUnixSeconds(Timestamp(0, 0))")
  }

  @Test
  fun timestampToUnixSeconds_currentTimestamp_returnsSeconds() {
    // Example: March 15, 2023 12:00:00.123456789 UTC
    val ts = Timestamp(1678886400, 123456789)
    val expectedSeconds = 1678886400L // Nanos are truncated
    val expr = timestampToUnixSeconds(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(expectedSeconds),
      "timestampToUnixSeconds(Timestamp(1678886400, 123456789))"
    )
  }

  @Test
  fun timestampToUnixSeconds_maxTimestamp_returnsSeconds() {
    // Max representable timestamp: seconds=253402300799, nanos=999999999
    val maxTs = Timestamp(253402300799L, 999999999)
    val expectedSeconds = 253402300799L
    val expr = timestampToUnixSeconds(constant(maxTs))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(expectedSeconds), "timestampToUnixSeconds(maxTimestamp)")
  }

  @Test
  fun timestampToUnixSeconds_minTimestamp_returnsSeconds() {
    // Min representable timestamp: seconds=-62135596800, nanos=0
    val minTs = Timestamp(-62135596800L, 0)
    val expectedSeconds = -62135596800L
    val expr = timestampToUnixSeconds(constant(minTs))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(expectedSeconds), "timestampToUnixSeconds(minTimestamp)")
  }

  @Test
  fun timestampToUnixSeconds_timestampTruncatesToSeconds() {
    // Timestamp: seconds=-1, nanos=999999999
    // Expected Seconds: -1
    val ts = Timestamp(-1, 999999999)
    val expr = timestampToUnixSeconds(constant(ts))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(-1L), "timestampToUnixSeconds(Timestamp(-1, 999999999))")
  }

  // --- TimestampAdd Tests ---
  // Note: The C++ tests use SharedConstant(nullptr) for null values.
  // In Kotlin, we'll use `nullValue()` or `constant(null)` where appropriate,
  // and `assertEvaluatesToNull` for checking null results.

  @Test
  fun timestampAdd_timestampAddStringType_returnsError() {
    val expr = timestampAdd(constant("abc"), constant("second"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "timestampAdd(string, \"second\", 1L)")
  }

  @Test
  fun timestampAdd_zeroValue_returnsTimestampEpoch() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("second"), constant(0L))
    assertEvaluatesTo(evaluate(expr), encodeValue(epoch), "timestampAdd(epoch, \"second\", 0L)")
  }

  @Test
  fun timestampAdd_intType_returnsTimestamp() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("second"), constant(1L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Timestamp(1, 0)),
      "timestampAdd(epoch, \"second\", 1L)"
    )
  }

  @Test
  fun timestampAdd_longType_returnsTimestamp() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("second"), constant(9876543210L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Timestamp(9876543210L, 0)),
      "timestampAdd(epoch, \"second\", 9876543210L)"
    )
  }

  @Test
  fun timestampAdd_longTypeNegative_returnsTimestamp() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("second"), constant(-10000L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Timestamp(-10000L, 0)),
      "timestampAdd(epoch, \"second\", -10000L)"
    )
  }

  @Test
  fun timestampAdd_longTypeNegativeOverflow_returnsError() {
    val minTs = Timestamp(-62135596800L, 0) // Min Firestore seconds

    // Test adding 0 (boundary)
    val exprBoundary = timestampAdd(constant(minTs), constant("second"), constant(0L))
    assertEvaluatesTo(
      evaluate(exprBoundary),
      encodeValue(minTs),
      "timestampAdd(minTs, \"second\", 0L)"
    )

    // Test adding -1 second (overflow)
    val exprOverflow = timestampAdd(constant(minTs), constant("second"), constant(-1L))
    assertEvaluatesToError(evaluate(exprOverflow), "timestampAdd(minTs, \"second\", -1L)")
  }

  @Test
  fun timestampAdd_longTypePositiveOverflow_returnsError() {
    // Max Firestore timestamp: seconds=253402300799, nanos=999999999
    // Use nanos that are multiple of 1000 for microsecond precision test
    val maxTs = Timestamp(253402300799L, 999999000)

    // Test adding 0 microsecond (boundary)
    val exprBoundary = timestampAdd(constant(maxTs), constant("microsecond"), constant(0L))
    assertEvaluatesTo(
      evaluate(exprBoundary),
      encodeValue(maxTs),
      "timestampAdd(maxTs, \"microsecond\", 0L)"
    )

    // Test adding 1 microsecond (should overflow because maxTs.nanos + 1000 > 999999999)
    // Max nanos is 999,999,999. maxTs has 999,999,000. Adding 1 micro (1000 nanos)
    // would result in 1,000,999,000 nanos, which should carry over to seconds and overflow.
    val exprOverflowMicro = timestampAdd(constant(maxTs), constant("microsecond"), constant(1L))
    assertEvaluatesToError(evaluate(exprOverflowMicro), "timestampAdd(maxTs, \"microsecond\", 1L)")

    // Test adding 1 second to a timestamp at max seconds but zero nanos
    val nearMaxSecTs = Timestamp(253402300799L, 0)
    val exprNearMaxBoundary = timestampAdd(constant(nearMaxSecTs), constant("second"), constant(0L))
    assertEvaluatesTo(
      evaluate(exprNearMaxBoundary),
      encodeValue(nearMaxSecTs),
      "timestampAdd(nearMaxSecTs, \"second\", 0L)"
    )

    val exprNearMaxOverflow = timestampAdd(constant(nearMaxSecTs), constant("second"), constant(1L))
    assertEvaluatesToError(
      evaluate(exprNearMaxOverflow),
      "timestampAdd(nearMaxSecTs, \"second\", 1L)"
    )
  }

  @Test
  fun timestampAdd_longTypeMinute_returnsTimestamp() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("minute"), constant(1L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Timestamp(60, 0)),
      "timestampAdd(epoch, \"minute\", 1L)"
    )
  }

  @Test
  fun timestampAdd_longTypeHour_returnsTimestamp() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("hour"), constant(1L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Timestamp(3600, 0)),
      "timestampAdd(epoch, \"hour\", 1L)"
    )
  }

  @Test
  fun timestampAdd_longTypeDay_returnsTimestamp() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("day"), constant(1L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Timestamp(86400, 0)),
      "timestampAdd(epoch, \"day\", 1L)"
    )
  }

  @Test
  fun timestampAdd_longTypeMillisecond_returnsTimestamp() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("millisecond"), constant(1L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Timestamp(0, 1000000)),
      "timestampAdd(epoch, \"millisecond\", 1L)"
    )
  }

  @Test
  fun timestampAdd_longTypeMicrosecond_returnsTimestamp() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("microsecond"), constant(1L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Timestamp(0, 1000)),
      "timestampAdd(epoch, \"microsecond\", 1L)"
    )
  }

  @Test
  fun timestampAdd_invalidTimeUnit_returnsError() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("abc"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "timestampAdd(epoch, \"abc\", 1L)")
  }

  @Test
  fun timestampAdd_invalidAmount_returnsError() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), constant("second"), constant("abc"))
    assertEvaluatesToError(evaluate(expr), "timestampAdd(epoch, \"second\", \"abc\")")
  }

  @Test
  fun timestampAdd_nullAmount_returnsNull() {
    val epoch = Timestamp(0, 0)
    // C++ uses SharedConstant(nullptr). In Kotlin, this translates to `nullValue()` for an
    // expression
    // or `constant(null)` if the constant itself is null.
    // `evaluateTimestampAdd` expects the amount to be a number. If it's null, it should error.
    // However, if the *expression* for amount evaluates to null (e.g. field that is null),
    // then the C++ test `ReturnsNull()` implies the operation results in SQL NULL.
    // Let's assume `constant(nullValue())` represents a SQL NULL value.
    val expr = timestampAdd(constant(epoch), constant("second"), nullValue())
    assertEvaluatesToNull(evaluate(expr), "timestampAdd(epoch, \"second\", nullValue())")
  }

  @Test
  fun timestampAdd_nullTimeUnit_returnsError() {
    val epoch = Timestamp(0, 0)
    val expr = timestampAdd(constant(epoch), nullValue(), constant(1L))
    assertEvaluatesToError(evaluate(expr), "timestampAdd(epoch, nullValue(), 1L)")
  }

  @Test
  fun timestampAdd_nullTimestamp_returnsNull() {
    val expr = timestampAdd(nullValue(), constant("second"), constant(1L))
    assertEvaluatesToNull(evaluate(expr), "timestampAdd(nullValue(), \"second\", 1L)")
  }
}
