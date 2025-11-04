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
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampToUnixSeconds
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TimestampToUnixSecondsTests {

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
}
