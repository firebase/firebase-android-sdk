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

package com.google.firebase.firestore.pipeline.evaluation.array

import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayGet
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultError
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultUnset
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayGetTests {
  // --- ArrayGet Tests ---
  private data class ArrayGetTestCase(
    val array: Expression,
    val index: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayGet - general cases`() {
    val testCases =
      listOf(
        // Positive indexes
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(0),
          EvaluateResultValue(encodeValue("a")),
          "positive index 0"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(1),
          EvaluateResultValue(encodeValue("b")),
          "positive index 1"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(2),
          EvaluateResultValue(encodeValue("c")),
          "positive index 2"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(3),
          EvaluateResultValue(encodeValue("d")),
          "positive index 3"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(4),
          EvaluateResultUnset,
          "positive index out of bounds"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(0L),
          EvaluateResultValue(encodeValue("a")),
          "positive long index 0"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(1L),
          EvaluateResultValue(encodeValue("b")),
          "positive long index 1"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(2L),
          EvaluateResultValue(encodeValue("c")),
          "positive long index 2"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(3L),
          EvaluateResultValue(encodeValue("d")),
          "positive long index 3"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(4L),
          EvaluateResultUnset,
          "positive long index out of bounds"
        ),

        // Negative indexes
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-1),
          EvaluateResultValue(encodeValue("d")),
          "negative index -1"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-2),
          EvaluateResultValue(encodeValue("c")),
          "negative index -2"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-3),
          EvaluateResultValue(encodeValue("b")),
          "negative index -3"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-4),
          EvaluateResultValue(encodeValue("a")),
          "negative index -4"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-5),
          EvaluateResultUnset,
          "negative index out of bounds"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-1L),
          EvaluateResultValue(encodeValue("d")),
          "negative long index -1"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-2L),
          EvaluateResultValue(encodeValue("c")),
          "negative long index -2"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-3L),
          EvaluateResultValue(encodeValue("b")),
          "negative long index -3"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-4L),
          EvaluateResultValue(encodeValue("a")),
          "negative long index -4"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-5L),
          EvaluateResultUnset,
          "negative long index out of bounds"
        ),

        // Far out of bounds indexes
        ArrayGetTestCase(array(), constant(0), EvaluateResultUnset, "empty array"),
        ArrayGetTestCase(array(), constant(0L), EvaluateResultUnset, "empty array long index"),
        ArrayGetTestCase(array(), constant(42), EvaluateResultUnset, "empty array far out"),
        ArrayGetTestCase(
          array(),
          constant(-42),
          EvaluateResultUnset,
          "empty array far out negative"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(42L),
          EvaluateResultUnset,
          "far positive index"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(-42L),
          EvaluateResultUnset,
          "far negative index"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(Long.MAX_VALUE),
          EvaluateResultUnset,
          "max long index"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(Long.MIN_VALUE),
          EvaluateResultUnset,
          "min long index"
        ),
      )

    for (testCase in testCases) {
      val expr = arrayGet(testCase.array, testCase.index)
      val result = evaluate(expr)
      assertWithMessage("arrayGet - ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayGet - invalid array types`() {
    val testCases =
      listOf(
        ArrayGetTestCase(field("nonexistent"), constant(0), EvaluateResultUnset, "array is UNSET"),
        ArrayGetTestCase(nullValue(), constant(0), EvaluateResultUnset, "array is NULL"),
        ArrayGetTestCase(map(mapOf("0" to 123L)), constant(0), EvaluateResultUnset, "array is MAP"),
        ArrayGetTestCase(map(mapOf()), constant(0), EvaluateResultUnset, "array is empty MAP"),
        ArrayGetTestCase(constant("foo"), constant(0), EvaluateResultUnset, "array is STRING"),
        ArrayGetTestCase(constant(2), constant(0), EvaluateResultUnset, "array is INT"),
        ArrayGetTestCase(constant(2.0), constant(0), EvaluateResultUnset, "array is DOUBLE"),
      )
    for (testCase in testCases) {
      val expr = arrayGet(testCase.array, testCase.index)
      val result = evaluate(expr)
      assertWithMessage("arrayGet - ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayGet - invalid index types`() {
    val testCases =
      listOf(
        // Invalid index with a valid array.
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(0.0),
          EvaluateResultError,
          "index is DOUBLE"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(-0.0),
          EvaluateResultError,
          "index is -0.0"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(1.5),
          EvaluateResultError,
          "index is FLOAT"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          field("nonexistent"),
          EvaluateResultError,
          "index is UNSET"
        ),
        ArrayGetTestCase(array("a", "b", "c"), nullValue(), EvaluateResultError, "index is NULL"),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant("foo"),
          EvaluateResultError,
          "index is STRING"
        ),
        ArrayGetTestCase(array("a", "b", "c"), array(1), EvaluateResultError, "index is ARRAY"),
        ArrayGetTestCase(
          array("a", "b", "c"),
          map(mapOf("foo" to 1L)),
          EvaluateResultError,
          "index is MAP"
        ),

        // Invalid index with an invalid array.
        ArrayGetTestCase(
          nullValue(),
          constant(0.0),
          EvaluateResultError,
          "NULL array, DOUBLE index"
        ),
        ArrayGetTestCase(
          nullValue(),
          constant(-0.0),
          EvaluateResultError,
          "NULL array, -0.0 index"
        ),
        ArrayGetTestCase(
          nullValue(),
          constant(1.5),
          EvaluateResultError,
          "NULL array, FLOAT index"
        ),
        ArrayGetTestCase(
          nullValue(),
          field("nonexistent"),
          EvaluateResultError,
          "NULL array, UNSET index"
        ),
        ArrayGetTestCase(nullValue(), nullValue(), EvaluateResultError, "NULL array, NULL index"),
        ArrayGetTestCase(
          nullValue(),
          constant("foo"),
          EvaluateResultError,
          "NULL array, STRING index"
        ),
        ArrayGetTestCase(nullValue(), array(1), EvaluateResultError, "NULL array, ARRAY index"),
        ArrayGetTestCase(
          nullValue(),
          map(mapOf("foo" to 1L)),
          EvaluateResultError,
          "NULL array, MAP index"
        ),
      )

    for (testCase in testCases) {
      val expr = arrayGet(testCase.array, testCase.index)
      val result = evaluate(expr)
      assertWithMessage("arrayGet - ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }
}
