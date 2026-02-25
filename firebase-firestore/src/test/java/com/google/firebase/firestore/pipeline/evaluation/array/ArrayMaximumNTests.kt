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
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayMaximumN
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultError
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultUnset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayMaximumNTests {
  private data class TestCase(
    val array: Expression,
    val n: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayMaximumN - general cases`() {
    val testCases =
      listOf(
        TestCase(
          array(3, 1, 2),
          constant(2),
          EvaluateResult.list(listOf(encodeValue(3), encodeValue(2))),
          "basic max 2 (descending)"
        ),
        // The strictCompare function doesn't handle mixed types.
        // TODO: Uncomment when the comparison function is fixed.
        //        TestCase(
        //          array(1, null),
        //          constant(2),
        //          EvaluateResult.list(listOf(encodeValue(1), NULL_VALUE)),
        //          "null value"
        //        ),
        //        TestCase(
        //          array(1, "a"),
        //          constant(2),
        //          EvaluateResult.list(listOf(encodeValue("a"), encodeValue(1))),
        //          "number < string"
        //        ),
        TestCase(array(1, 2), constant(0), EvaluateResult.list(emptyList()), "n=0"),
        TestCase(
          array(1, 2),
          constant(5),
          EvaluateResult.list(listOf(encodeValue(2), encodeValue(1))),
          "n > size"
        ),
        TestCase(array(), constant(1), EvaluateResult.list(emptyList()), "empty array")
      )

    for (testCase in testCases) {
      val expr = arrayMaximumN(testCase.array, testCase.n)
      val result = evaluate(expr)
      assertWithMessage("arrayMaximumN ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayMaximumN - error cases`() {
    val testCases =
      listOf(
        TestCase(array(1), constant(-1), EvaluateResultError, "negative n"),
        TestCase(constant("not array"), constant(1), EvaluateResultUnset, "invalid array type")
      )

    for (testCase in testCases) {
      val expr = arrayMaximumN(testCase.array, testCase.n)
      val result = evaluate(expr)
      assertWithMessage("arrayMaximumN ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }
}
