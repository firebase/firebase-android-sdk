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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayLastN
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultError
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayLastNTests {
  private data class ArrayLastNTestCase(
    val array: Expression,
    val n: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayLastN - general cases`() {
    val testCases =
      listOf(
        ArrayLastNTestCase(
          array(1, 2, 3),
          constant(2),
          EvaluateResult.list(listOf(encodeValue(2), encodeValue(3))),
          "basic last 2"
        ),
        ArrayLastNTestCase(array(1, 2, 3), constant(0), EvaluateResult.list(emptyList()), "last 0"),
        ArrayLastNTestCase(
          array(1, 2, 3),
          constant(5),
          EvaluateResult.list(listOf(encodeValue(1), encodeValue(2), encodeValue(3))),
          "last 5 (overflow)"
        ),
        ArrayLastNTestCase(array(), constant(1), EvaluateResult.list(emptyList()), "empty array"),
        ArrayLastNTestCase(nullValue(), constant(1), EvaluateResultValue(NULL_VALUE), "null input"),
        ArrayLastNTestCase(
          field("nonexistent"),
          constant(1),
          EvaluateResultValue(NULL_VALUE),
          "non-existent input"
        )
      )

    for (testCase in testCases) {
      val expr = arrayLastN(testCase.array, testCase.n)
      val result = evaluate(expr)
      assertWithMessage("arrayLastN ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayLastN - error cases`() {
    val testCases =
      listOf(
        ArrayLastNTestCase(array(1), nullValue(), EvaluateResultError, "null n"),
        ArrayLastNTestCase(array(1, 2, 3), constant(-1), EvaluateResultError, "negative n"),
        ArrayLastNTestCase(
          constant("not array"),
          constant(1),
          EvaluateResultError,
          "invalid array type"
        ),
        ArrayLastNTestCase(array(1), constant("not number"), EvaluateResultError, "invalid n type"),
        ArrayLastNTestCase(array(1), constant(1.5), EvaluateResultError, "float n type")
      )

    for (testCase in testCases) {
      val expr = arrayLastN(testCase.array, testCase.n)
      val result = evaluate(expr)
      assertWithMessage("arrayLastN ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }
}
