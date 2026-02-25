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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayLast
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
class ArrayLastTests {
  private data class ArrayLastTestCase(
    val array: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayLast - general cases`() {
    val testCases =
      listOf(
        ArrayLastTestCase(array("1", 42L, true), EvaluateResultValue(encodeValue(true)), "basic"),
        ArrayLastTestCase(array(), EvaluateResultUnset, "empty array"),
        ArrayLastTestCase(
          array("first", null),
          EvaluateResultValue(NULL_VALUE),
          "null last element"
        ),
        ArrayLastTestCase(
          array(array(1L, 2L)),
          EvaluateResultValue(encodeValue(listOf(encodeValue(1L), encodeValue(2L)))),
          "nested arrays"
        ),
        ArrayLastTestCase(
          array("single"),
          EvaluateResultValue(encodeValue("single")),
          "single element"
        ),
        ArrayLastTestCase(nullValue(), EvaluateResultValue(NULL_VALUE), "null input"),
        ArrayLastTestCase(
          field("nonexistent"),
          EvaluateResultValue(NULL_VALUE),
          "non-existent input"
        )
      )

    for (testCase in testCases) {
      val expr = arrayLast(testCase.array)
      val result = evaluate(expr)
      assertWithMessage("arrayLast ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayLast - error cases`() {
    val testCases =
      listOf(
        ArrayLastTestCase(
          Expression.vector(doubleArrayOf(1.0, 2.0)),
          EvaluateResultError,
          "received unexpected input type vector"
        ),
        ArrayLastTestCase(
          constant("notAnArray"),
          EvaluateResultError,
          "received unexpected input type string"
        ),
        ArrayLastTestCase(
          constant(123L),
          EvaluateResultError,
          "received unexpected input type long"
        ),
        ArrayLastTestCase(
          constant(true),
          EvaluateResultError,
          "received unexpected input type boolean"
        ),
        ArrayLastTestCase(
          map(mapOf("a" to 1)),
          EvaluateResultError,
          "received unexpected input type map"
        )
      )

    for (testCase in testCases) {
      val expr = arrayLast(testCase.array)
      val result = evaluate(expr)
      assertWithMessage("arrayLast ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }
}
