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
import com.google.firebase.firestore.pipeline.Expression.Companion.unixSecondsToTimestamp
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class UnixSecondsToTimestampTests {

  // --- UnixSecondsToTimestamp Tests ---
  @Test
  fun unixSecondsToTimestamp_mirrors_errors() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(
        evaluate(unixSecondsToTimestamp(testCase.input)),
        "unixSecondsToTimestamp(${'$'}{testCase.name})"
      )
    }
  }

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
}
