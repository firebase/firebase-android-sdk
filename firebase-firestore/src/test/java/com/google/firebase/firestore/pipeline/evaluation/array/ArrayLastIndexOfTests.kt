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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayLastIndexOf
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
class ArrayLastIndexOfTests {
  private data class ArrayLastIndexOfTestCase(
    val array: Expression,
    val value: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayLastIndexOf - general cases`() {
    val testCases =
      listOf(
        ArrayLastIndexOfTestCase(
          array("1", 42L, true),
          constant("1"),
          EvaluateResultValue(encodeValue(0L)),
          "basic match"
        ),
        ArrayLastIndexOfTestCase(
          array("1", 42L, true),
          constant(true),
          EvaluateResultValue(encodeValue(2L)),
          "basic match of boolean"
        ),
        ArrayLastIndexOfTestCase(
          array("1", 42L, true),
          constant("missing"),
          EvaluateResultValue(encodeValue(-1L)),
          "no match"
        ),
        ArrayLastIndexOfTestCase(
          array(1L, 2L, 2L),
          constant(2L),
          EvaluateResultValue(encodeValue(2L)),
          "match last duplicate"
        ),
        ArrayLastIndexOfTestCase(
          array(),
          constant("anything"),
          EvaluateResultValue(encodeValue(-1L)),
          "empty array"
        ),
        ArrayLastIndexOfTestCase(
          array("1", null, true),
          nullValue(),
          EvaluateResultValue(encodeValue(1L)),
          "match null element"
        ),
        ArrayLastIndexOfTestCase(
          nullValue(),
          constant("anything"),
          EvaluateResultValue(NULL_VALUE),
          "null input array"
        ),
        ArrayLastIndexOfTestCase(
          field("nonexistent"),
          constant("anything"),
          EvaluateResultValue(NULL_VALUE),
          "unset input array"
        ),
        ArrayLastIndexOfTestCase(
          array("1", 2L),
          field("nonexistent"),
          EvaluateResultValue(NULL_VALUE),
          "unset input value"
        ),
        ArrayLastIndexOfTestCase(
          field("nonexistent"),
          field("nonexistent"),
          EvaluateResultValue(NULL_VALUE),
          "unset input array and value"
        ),
        ArrayLastIndexOfTestCase(
          nullValue(),
          nullValue(),
          EvaluateResultValue(NULL_VALUE),
          "null array and null value"
        )
      )

    for (testCase in testCases) {
      val expr = arrayLastIndexOf(testCase.array, testCase.value)
      val result = evaluate(expr)
      assertWithMessage("arrayLastIndexOf ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayLastIndexOf - error cases`() {
    val testCases =
      listOf(
        ArrayLastIndexOfTestCase(
          Expression.vector(doubleArrayOf(1.0, 2.0)),
          constant(1.0),
          EvaluateResultError,
          "received unexpected input type vector"
        ),
        ArrayLastIndexOfTestCase(
          constant("notAnArray"),
          constant("a"),
          EvaluateResultError,
          "received unexpected input type string"
        ),
        ArrayLastIndexOfTestCase(
          constant(123L),
          constant(123L),
          EvaluateResultError,
          "received unexpected input type long"
        ),
        ArrayLastIndexOfTestCase(
          constant(true),
          constant(true),
          EvaluateResultError,
          "received unexpected input type boolean"
        ),
        ArrayLastIndexOfTestCase(
          map(mapOf("a" to 1)),
          constant("a"),
          EvaluateResultError,
          "received unexpected input type map"
        )
      )

    for (testCase in testCases) {
      val expr = arrayLastIndexOf(testCase.array, testCase.value)
      val result = evaluate(expr)
      assertWithMessage("arrayLastIndexOf ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }
}
