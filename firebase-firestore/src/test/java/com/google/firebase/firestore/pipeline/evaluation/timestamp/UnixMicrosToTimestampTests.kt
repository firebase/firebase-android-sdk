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
import com.google.firebase.firestore.pipeline.Expression.Companion.unixMicrosToTimestamp
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class UnixMicrosToTimestampTests {

  // --- UnixMicrosToTimestamp Tests ---

  @Test
  fun unixMicrosToTimestamp_stringType_returnsError() {
    val expr = unixMicrosToTimestamp(constant("abc"))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "unixMicrosToTimestamp(\"abc\")")
  }

  @Test
  fun unixMicrosToTimestamp_mirrors_errors() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(
        evaluate(unixMicrosToTimestamp(testCase.input)),
        "unixMicrosToTimestamp(${'$'}{testCase.name})"
      )
    }
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
}
