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
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampToUnixMillis
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TimestampToUnixMillisTests {

  // --- TimestampToUnixMillis Tests ---

  @Test
  fun timestampToUnixMillis_nonTimestampType_returnsError() {
    val expr = timestampToUnixMillis(constant(123L))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "timestampToUnixMillis(123L)")
  }

  @Test
  fun timestampToUnixMillis_mirroring_errors() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(
        evaluate(timestampToUnixMillis(testCase.input)),
        "timestampToUnixMillis(${'$'}{testCase.name})"
      )
    }
  }

  @Test
  fun timestampToUnixMillis_timestamp_returnsMillis() {
    val ts = Timestamp(347068800, 0) // December 31, 1980 00:00:00 UTC
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
}
