// Copyright 2026 Google LLC
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

package com.google.firebase.firestore.pipeline.evaluation.array

import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.firestore.model.Values.NULL_VALUE
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayIndexOfAll
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultError
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayIndexOfAllTests {
  private data class ArrayIndexOfAllTestCase(
    val array: Expression,
    val value: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayIndexOfAll - general cases`() {
    val testCases =
      listOf(
        ArrayIndexOfAllTestCase(
          array("1", 42L, true),
          constant("1"),
          EvaluateResultValue(encodeValue(listOf(0L).map { encodeValue(it) })),
          "basic match single element"
        ),
        ArrayIndexOfAllTestCase(
          array("1", 42L, true, 42L),
          constant(42L),
          EvaluateResultValue(encodeValue(listOf(1L, 3L).map { encodeValue(it) })),
          "basic match multiple elements"
        ),
        ArrayIndexOfAllTestCase(
          array("1", 42L, true),
          constant("missing"),
          EvaluateResultValue(encodeValue(emptyList<Long>().map { encodeValue(it) })),
          "no match"
        ),
        ArrayIndexOfAllTestCase(
          array(1L, 1L, 1L),
          constant(1L),
          EvaluateResultValue(encodeValue(listOf(0L, 1L, 2L).map { encodeValue(it) })),
          "match all duplicates"
        ),
        ArrayIndexOfAllTestCase(
          array(),
          constant("anything"),
          EvaluateResultValue(encodeValue(emptyList<Long>().map { encodeValue(it) })),
          "empty array"
        ),
        ArrayIndexOfAllTestCase(
          array("1", null, true, null),
          nullValue(),
          EvaluateResultValue(encodeValue(listOf(1L, 3L).map { encodeValue(it) })),
          "match null element"
        ),
        ArrayIndexOfAllTestCase(
          nullValue(),
          constant("anything"),
          EvaluateResultValue(NULL_VALUE),
          "null input array"
        ),
        ArrayIndexOfAllTestCase(
          field("nonexistent"),
          constant("anything"),
          EvaluateResultValue(NULL_VALUE),
          "unset input array"
        ),
        ArrayIndexOfAllTestCase(
          array("1", 2L),
          field("nonexistent"),
          EvaluateResultValue(NULL_VALUE),
          "unset input value"
        )
      )

    for (testCase in testCases) {
      val expr = arrayIndexOfAll(testCase.array, testCase.value)
      val result = evaluate(expr)
      assertWithMessage("arrayIndexOfAll ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayIndexOfAll - error cases`() {
    val testCases =
      listOf(
        ArrayIndexOfAllTestCase(
          Expression.vector(doubleArrayOf(1.0, 2.0)),
          constant(1.0),
          EvaluateResultError,
          "received unexpected input type vector"
        ),
        ArrayIndexOfAllTestCase(
          constant("notAnArray"),
          constant("a"),
          EvaluateResultError,
          "received unexpected input type string"
        ),
        ArrayIndexOfAllTestCase(
          constant(123L),
          constant(123L),
          EvaluateResultError,
          "received unexpected input type long"
        ),
        ArrayIndexOfAllTestCase(
          constant(true),
          constant(true),
          EvaluateResultError,
          "received unexpected input type boolean"
        ),
        ArrayIndexOfAllTestCase(
          map(mapOf("a" to 1)),
          constant("a"),
          EvaluateResultError,
          "received unexpected input type map"
        )
      )

    for (testCase in testCases) {
      val expr = arrayIndexOfAll(testCase.array, testCase.value)
      val result = evaluate(expr)
      assertWithMessage("arrayIndexOfAll ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }
}
