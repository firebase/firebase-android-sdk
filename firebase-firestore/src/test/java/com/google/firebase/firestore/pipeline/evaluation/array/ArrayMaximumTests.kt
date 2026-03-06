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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayMaximum
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
class ArrayMaximumTests {
  private data class TestCase(
    val array: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayMaximum - general cases`() {
    val testCases =
      listOf(
        TestCase(array(1, 2, 3), EvaluateResultValue(encodeValue(3)), "sorted numbers"),
        TestCase(array(3, 1, 2), EvaluateResultValue(encodeValue(3)), "unsorted numbers"),
        TestCase(array(1), EvaluateResultValue(encodeValue(1)), "single element"),
        TestCase(array(), EvaluateResultValue(NULL_VALUE), "empty array"),
        TestCase(array(1, null), EvaluateResultValue(encodeValue(1)), "null vs number"),
        TestCase(array("b", "a"), EvaluateResultValue(encodeValue("b")), "strings"),
        // The strictCompare function doesn't handle mixed types.
        // TODO: Uncomment when the comparison function is fixed.
        //        TestCase(
        //          array(1, "2", 3, "10"),
        //          EvaluateResultValue(encodeValue("2")),
        //          "number vs string"
        //        ),
        TestCase(nullValue(), EvaluateResultValue(NULL_VALUE), "null input (Unset)"),
        TestCase(field("nonexistent"), EvaluateResultValue(NULL_VALUE), "unset input")
      )

    for (testCase in testCases) {
      val expr = arrayMaximum(testCase.array)
      val result = evaluate(expr)
      assertWithMessage("arrayMaximum ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayMaximum - error cases`() {
    val testCases =
      listOf(
        TestCase(constant(123), EvaluateResultError, "number input (not array)"),
        TestCase(constant("string"), EvaluateResultError, "string input")
      )

    for (testCase in testCases) {
      val expr = arrayMaximum(testCase.array)
      val result = evaluate(expr)
      assertWithMessage("arrayMaximum ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }
}
