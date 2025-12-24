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
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampAdd
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TimestampAddTest {

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
      val expr = timestampAdd(timestamp, constant("second"), amount)
      assertEvaluatesToNull(evaluate(expr), "timestampAdd with args: $timestamp, $amount")
    }
  }

  @Test
  fun `timestampAdd with invalid timestamp returns error`() {
    val expr = timestampAdd(constant("not a timestamp"), constant("second"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampAdd with invalid amount returns error`() {
    val expr =
      timestampAdd(constant(Timestamp(0, 0)), constant("second"), constant("not an amount"))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampAdd with invalid time unit returns error`() {
    val expr = timestampAdd(constant(Timestamp(0, 0)), constant("not a unit"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampAdd with null time unit returns error`() {
    val expr = timestampAdd(constant(Timestamp(0, 0)), nullValue(), constant(1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampAdd to max timestamp`() {
    // Corresponds to 9999-12-30T23:59:59Z + 1 day
    val start = Timestamp(253402214399L, 0)
    val expected = Timestamp(253402300799L, 0)
    val expr = timestampAdd(constant(start), constant("day"), constant(1L))
    assertEvaluatesTo(evaluate(expr), encodeValue(expected), "$expr")
  }

  @Test
  fun `timestampAdd to min timestamp`() {
    // Corresponds to 0001-01-02T00:00:00Z - 1 day
    val start = Timestamp(-62135510400L, 0)
    val expected = Timestamp(-62135596800L, 0)
    val expr = timestampAdd(constant(start), constant("day"), constant(-1L))
    assertEvaluatesTo(evaluate(expr), encodeValue(expected), "$expr")
  }

  @Test
  fun `timestampAdd positive overflow returns error`() {
    // Corresponds to 9999-12-31T23:59:59Z + 1 day
    val expr = timestampAdd(constant(Timestamp(253402300799L, 0)), constant("day"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampAdd negative overflow returns error`() {
    // Corresponds to 0001-01-01T00:00:00Z - 1 day
    val expr = timestampAdd(constant(Timestamp(-62135596800L, 0)), constant("day"), constant(-1L))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampAdd with amount too large returns error`() {
    val expr = timestampAdd(constant(Timestamp(0, 0)), constant("day"), constant(Long.MAX_VALUE))
    assertEvaluatesToError(evaluate(expr), "$expr")
  }

  @Test
  fun `timestampAdd with negative amount`() {
    val expr = timestampAdd(constant(Timestamp(0, 2000)), constant("microsecond"), constant(-1L))
    assertEvaluatesTo(evaluate(expr), encodeValue(Timestamp(0, 1000)), "$expr")
  }

  @Test
  fun `timestampAdd with int amount`() {
    val expr = timestampAdd(constant(Timestamp(0, 1000)), constant("microsecond"), constant(1))
    assertEvaluatesTo(evaluate(expr), encodeValue(Timestamp(0, 2000)), "$expr")
  }

  @Test
  fun `timestampAdd with long amount`() {
    val expr = timestampAdd(constant(Timestamp(0, 1000)), constant("microsecond"), constant(1L))
    assertEvaluatesTo(evaluate(expr), encodeValue(Timestamp(0, 2000)), "$expr")
  }

  @Test
  fun `timestampAdd with various units`() {
    val start = Timestamp(1672531200, 0) // 2023-01-01T00:00:00Z
    val testCases =
      mapOf(
        "microsecond" to Timestamp(1672531200, 1000),
        "millisecond" to Timestamp(1672531200, 1000000),
        "second" to Timestamp(1672531201, 0),
        "minute" to Timestamp(1672531260, 0),
        "hour" to Timestamp(1672534800, 0),
        "day" to Timestamp(1672617600, 0),
      )

    for ((unit, expected) in testCases) {
      val expr = timestampAdd(constant(start), constant(unit), constant(1L))
      assertEvaluatesTo(evaluate(expr), encodeValue(expected), "Adding 1 $unit")
    }
  }
}
