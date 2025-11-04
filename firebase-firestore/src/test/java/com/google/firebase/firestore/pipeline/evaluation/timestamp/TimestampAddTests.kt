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
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampAdd
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TimestampAddTests {

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
