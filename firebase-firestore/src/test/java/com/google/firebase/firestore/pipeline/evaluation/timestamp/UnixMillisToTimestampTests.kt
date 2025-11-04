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

package com.google.firebase.firestore.pipeline.evaluation.timestamp

import com.google.firebase.Timestamp
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.unixMillisToTimestamp
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class UnixMillisToTimestampTests {

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
}
