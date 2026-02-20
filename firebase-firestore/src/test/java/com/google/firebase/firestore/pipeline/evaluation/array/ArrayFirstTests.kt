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
import com.google.firebase.firestore.model.Values.NULL_VALUE
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayFirst
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
class ArrayFirstTests {
  // --- ArrayFirst Tests ---
  private data class ArrayFirstTestCase(
    val array: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayFirst - general cases`() {
    val testCases =
      listOf(
        ArrayFirstTestCase(
          array("1", 42L, true),
          EvaluateResultValue(encodeValue("1")),
          "basic"
        ),
        ArrayFirstTestCase(
          array(),
          EvaluateResultUnset,
          "empty array"
        ),
        ArrayFirstTestCase(
          array(null, "second"),
          EvaluateResultValue(NULL_VALUE),
          "null first element"
        ),
        ArrayFirstTestCase(
          array(array(1L, 2L), 3L),
          EvaluateResultValue(encodeValue(listOf(encodeValue(1L), encodeValue(2L)))),
          "nested arrays"
        ),
        ArrayFirstTestCase(
          array("single"),
          EvaluateResultValue(encodeValue("single")),
          "single element"
        ),
        ArrayFirstTestCase(
          nullValue(),
          EvaluateResultValue(NULL_VALUE),
          "null input"
        ),
        ArrayFirstTestCase(
          field("nonexistent"),
          EvaluateResultValue(NULL_VALUE),
          "unset input"
        )
      )

    for (testCase in testCases) {
      val expr = arrayFirst(testCase.array)
      val result = evaluate(expr)
      assertWithMessage("arrayFirst ${testCase.description}").that(result).isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayFirst - error cases`() {
    val testCases =
      listOf(
        ArrayFirstTestCase(
          Expression.vector(doubleArrayOf(1.0, 2.0)),
          EvaluateResultError,
          "received unexpected input type vector"
        ),
        ArrayFirstTestCase(
          constant("notAnArray"),
          EvaluateResultError,
          "received unexpected input type string"
        ),
        ArrayFirstTestCase(
          constant(123L),
          EvaluateResultError,
          "received unexpected input type long"
        ),
        ArrayFirstTestCase(
          constant(true),
          EvaluateResultError,
          "received unexpected input type boolean"
        ),
        ArrayFirstTestCase(
          map(mapOf("a" to 1)),
          EvaluateResultError,
          "received unexpected input type map"
        )
      )

    for (testCase in testCases) {
      val expr = arrayFirst(testCase.array)
      val result = evaluate(expr)
      assertWithMessage("arrayFirst ${testCase.description}").that(result).isEqualTo(testCase.expected)
    }
  }
}
