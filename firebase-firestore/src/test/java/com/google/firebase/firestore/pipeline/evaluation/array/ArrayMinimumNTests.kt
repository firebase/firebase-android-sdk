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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayMinimumN
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResult
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultError
import com.google.firebase.firestore.pipeline.evaluation.EvaluateResultUnset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayMinimumNTests {
  private data class TestCase(
    val array: Expression,
    val n: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayMinimumN - general cases`() {
    val testCases =
      listOf(
        TestCase(
          array(3, 1, 2),
          constant(2),
          EvaluateResult.list(listOf(encodeValue(1), encodeValue(2))),
          "basic min 2"
        ),
        TestCase(array(1, 2), constant(0), EvaluateResult.list(emptyList()), "n=0"),
        TestCase(
          array(1, 2),
          constant(5),
          EvaluateResult.list(listOf(encodeValue(1), encodeValue(2))),
          "n > size"
        ),
        TestCase(array(), constant(1), EvaluateResult.list(emptyList()), "empty array")
      )

    // Fix expectation for empty array:
    // array() -> empty list
    val fixedTestCases =
      testCases.map {
        if (it.description == "empty array") it.copy(expected = EvaluateResult.list(emptyList()))
        else it
      }

    for (testCase in fixedTestCases) {
      val expr = arrayMinimumN(testCase.array, testCase.n)
      val result = evaluate(expr)
      assertWithMessage("arrayMinimumN ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayMinimumN - error cases`() {
    val testCases =
      listOf(
        TestCase(array(1), constant(-1), EvaluateResultError, "negative n"),
        TestCase(constant("not array"), constant(1), EvaluateResultUnset, "invalid array type")
      )

    for (testCase in testCases) {
      val expr = arrayMinimumN(testCase.array, testCase.n)
      val result = evaluate(expr)
      assertWithMessage("arrayMinimumN ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }
}
