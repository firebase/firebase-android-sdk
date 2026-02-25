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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayFirstN
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
class ArrayFirstNTests {
  private data class ArrayFirstNTestCase(
    val array: Expression,
    val n: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayFirstN - general cases`() {
    val testCases =
      listOf(
        ArrayFirstNTestCase(
          array(1, 2, 3),
          constant(2),
          EvaluateResult.list(listOf(encodeValue(1), encodeValue(2))),
          "basic first 2"
        ),
        ArrayFirstNTestCase(
          array(1, 2, 3),
          constant(0),
          EvaluateResult.list(emptyList()),
          "first 0"
        ),
        ArrayFirstNTestCase(
          array(1, 2, 3),
          constant(5),
          EvaluateResult.list(listOf(encodeValue(1), encodeValue(2), encodeValue(3))),
          "first 5 (overflow)"
        ),
        ArrayFirstNTestCase(array(), constant(1), EvaluateResult.list(emptyList()), "empty array"),
        ArrayFirstNTestCase(
          nullValue(),
          constant(1),
          EvaluateResultValue(NULL_VALUE),
          "null input"
        ),
        ArrayFirstNTestCase(
          field("nonexistent"),
          constant(1),
          EvaluateResultValue(NULL_VALUE),
          "non-existent input"
        )
      )

    for (testCase in testCases) {
      val expr = arrayFirstN(testCase.array, testCase.n)
      val result = evaluate(expr)
      assertWithMessage("arrayFirstN ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayFirstN - error cases`() {
    val testCases =
      listOf(
        ArrayFirstNTestCase(array(1), nullValue(), EvaluateResultError, "null n"),
        ArrayFirstNTestCase(array(1, 2, 3), constant(-1), EvaluateResultError, "negative n"),
        ArrayFirstNTestCase(
          constant("not array"),
          constant(1),
          EvaluateResultError,
          "invalid array type"
        ),
        ArrayFirstNTestCase(
          array(1),
          constant("not number"),
          EvaluateResultError,
          "invalid n type"
        ),
        ArrayFirstNTestCase(array(1), constant(1.5), EvaluateResultError, "float n type")
      )

    for (testCase in testCases) {
      val expr = arrayFirstN(testCase.array, testCase.n)
      val result = evaluate(expr)
      assertWithMessage("arrayFirstN ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }
}
