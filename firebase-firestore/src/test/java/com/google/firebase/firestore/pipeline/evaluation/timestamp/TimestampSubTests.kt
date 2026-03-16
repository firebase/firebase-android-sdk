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
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampSubtract
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TimestampSubTests {

  private fun unsetValue(): Expression = Expression.field("nonexistent")

  @Test
  fun `mirror_error for null and unset values`() {
    val testCases =
      listOf(
        nullValue() to constant(1L),
        unsetValue() to constant(1L),
        constant(Timestamp(0, 0)) to nullValue(),
        constant(Timestamp(0, 0)) to unsetValue(),
        nullValue() to nullValue(),
        unsetValue() to unsetValue(),
        nullValue() to unsetValue(),
        unsetValue() to nullValue(),
      )

    for ((timestamp, amount) in testCases) {
      val expr = timestampSubtract(timestamp, constant("second"), amount)
      assertEvaluatesToNull(evaluate(expr), "timestampSubtract with args: $timestamp, $amount")
    }
  }

  @Test
  fun `timestampSubtract with invalid timestamp returns error`() {
    val expr = timestampSubtract(constant("not a timestamp"), constant("second"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampSubtract with invalid amount returns error`() {
    val expr =
      timestampSubtract(constant(Timestamp(0, 0)), constant("second"), constant("not an amount"))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampSubtract with invalid time unit returns error`() {
    val expr = timestampSubtract(constant(Timestamp(0, 0)), constant("not a unit"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampSubtract with null time unit returns error`() {
    val expr = timestampSubtract(constant(Timestamp(0, 0)), nullValue(), constant(1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampSubtract from max timestamp`() {
    // Corresponds to 9999-12-31T23:59:59Z - 1 day
    val start = Timestamp(253402300799L, 0)
    val expected = Timestamp(253402214399L, 0)
    val expr = timestampSubtract(constant(start), constant("day"), constant(1L))
    assertEvaluatesTo(evaluate(expr), encodeValue(expected), "$expr")
  }

  @Test
  fun `timestampSubtract from min timestamp`() {
    // Corresponds to 0001-01-01T00:00:00Z - (-1 day) -> + 1 day
    val start = Timestamp(-62135596800L, 0)
    val expected = Timestamp(-62135510400L, 0)
    val expr = timestampSubtract(constant(start), constant("day"), constant(-1L))
    assertEvaluatesTo(evaluate(expr), encodeValue(expected), "$expr")
  }

  @Test
  fun `timestampSubtract positive overflow returns error`() {
    // Corresponds to 9999-12-31T23:59:59Z - (-1 day) -> + 1 day -> Overflow
    val expr =
      timestampSubtract(constant(Timestamp(253402300799L, 0)), constant("day"), constant(-1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampSubtract negative overflow returns error`() {
    // Corresponds to 0001-01-01T00:00:00Z - 1 day -> Underflow
    val expr =
      timestampSubtract(constant(Timestamp(-62135596800L, 0)), constant("day"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampSubtract with amount too large returns error`() {
    val expr =
      timestampSubtract(constant(Timestamp(0, 0)), constant("day"), constant(Long.MAX_VALUE))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampSubtract with negative amount`() {
    val expr =
      timestampSubtract(constant(Timestamp(0, 1000)), constant("microsecond"), constant(-1L))
    assertEvaluatesTo(evaluate(expr), encodeValue(Timestamp(0, 2000)), "$expr")
  }

  @Test
  fun `timestampSubtract with int amount`() {
    val expr = timestampSubtract(constant(Timestamp(0, 2000)), constant("microsecond"), constant(1))
    assertEvaluatesTo(evaluate(expr), encodeValue(Timestamp(0, 1000)), "$expr")
  }

  @Test
  fun `timestampSubtract with long amount`() {
    val expr =
      timestampSubtract(constant(Timestamp(0, 2000)), constant("microsecond"), constant(1L))
    assertEvaluatesTo(evaluate(expr), encodeValue(Timestamp(0, 1000)), "$expr")
  }

  @Test
  fun `timestampSubtract with various units`() {
    val start = Timestamp(1672531200, 0) // 2023-01-01T00:00:00Z
    val testCases =
      mapOf(
        "microsecond" to Timestamp(1672531199, 999999000),
        "millisecond" to Timestamp(1672531199, 999000000),
        "second" to Timestamp(1672531199, 0),
        "minute" to Timestamp(1672531140, 0),
        "hour" to Timestamp(1672527600, 0),
        "day" to Timestamp(1672444800, 0),
      )

    for ((unit, expected) in testCases) {
      val expr = timestampSubtract(constant(start), constant(unit), constant(1L))
      assertEvaluatesTo(evaluate(expr), encodeValue(expected), "Subtracting 1 $unit")
    }
  }
}
