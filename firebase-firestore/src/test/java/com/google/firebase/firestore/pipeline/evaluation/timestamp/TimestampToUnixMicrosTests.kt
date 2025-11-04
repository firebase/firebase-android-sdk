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
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampToUnixMicros
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TimestampToUnixMicrosTests {

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
}
