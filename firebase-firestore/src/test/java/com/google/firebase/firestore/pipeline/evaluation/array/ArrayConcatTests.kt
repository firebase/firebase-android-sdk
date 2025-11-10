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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayConcat
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayConcatTests {
  private data class ConcatTestCase(
    val left: Expression,
    val right: Expression,
    val expected: Expression,
    val description: String = ""
  ) {
    constructor(
      left: List<Any?>,
      right: List<Any?>,
      expected: List<Any?>,
      description: String = ""
    ) : this(array(left), array(right), array(expected), description)
  }

  // --- ArrayConcat Tests ---
  @Test
  fun `arrayConcat - general cases`() {
    val testCases =
      listOf(
        ConcatTestCase(
          left = emptyList(),
          right = emptyList(),
          expected = emptyList(),
          description = "two empty arrays"
        ),
        ConcatTestCase(
          left = listOf(1.0, 2, 3),
          right = listOf(4, Double.NaN, 6),
          expected = listOf(1.0, 2, 3, 4, Double.NaN, 6),
          description = "two arrays with mixed types"
        ),
        ConcatTestCase(
          left = emptyList(),
          right = listOf(1, 2, 3),
          expected = listOf(1, 2, 3),
          description = "empty array with non-empty array"
        ),
        ConcatTestCase(
          left = listOf(1, 2, 3),
          right = emptyList(),
          expected = listOf(1, 2, 3),
          description = "non-empty array with empty array"
        ),
        ConcatTestCase(
          left = listOf(null),
          right = listOf("d", "l", "c"),
          expected = listOf(null, "d", "l", "c"),
          description = "array with null with another array"
        ),
        ConcatTestCase(
          left = listOf(null),
          right = listOf(null),
          expected = listOf(null, null),
          description = "two arrays with null"
        ),
        ConcatTestCase(
          left = nullValue(),
          right = array(nullValue()),
          expected = nullValue(),
          description = "null with array"
        ),
        ConcatTestCase(
          left = field("non-existent"),
          right = array(1, 2),
          expected = nullValue(),
          description = "unset with array"
        )
      )

    for (testCase in testCases) {
      val expr = arrayConcat(testCase.left, testCase.right)
      val result = evaluate(expr)
      val expected = evaluate(testCase.expected)
      if (testCase.expected == null) {
        assertEvaluatesToNull(result, "arrayConcat with ${testCase.description}")
      } else {
        assertWithMessage("arrayConcat with ${testCase.description} success")
          .that(result.isSuccess)
          .isTrue()
        assertWithMessage("arrayConcat with ${testCase.description} value")
          .that(result)
          .isEqualTo(expected)
      }
    }
  }

  @Test
  fun `arrayConcat - three arrays`() {
    val expr = arrayConcat(array(1L, 2L), array(3L, 4L), array(5L, 6L))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat three arrays success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(1L, 2L, 3L, 4L, 5L, 6L).map { encodeValue(it) })
    assertWithMessage("arrayConcat three arrays value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayConcat - with null input returns null`() {
    val expr = arrayConcat(array(1L, 2L), nullValue(), array(3L, 4L))
    val result = evaluate(expr)
    assertEvaluatesToNull(result, "arrayConcat with null input")
  }

  @Test
  fun `arrayConcat - with non array returns error`() {
    val expr = arrayConcat(array(1L, 2L), constant("not an array"))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "arrayConcat with non array")
  }

  @Test
  fun `arrayConcat - with nested arrays`() {
    val nestedArray1 = array(1L, 2L)
    val nestedArray2 = array(3L, 4L)
    val expr = arrayConcat(array(nestedArray1), array(nestedArray2))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat with nested arrays success").that(result.isSuccess).isTrue()
    val expected =
      encodeValue(
        listOf(
          encodeValue(listOf(1L, 2L).map { encodeValue(it) }),
          encodeValue(listOf(3L, 4L).map { encodeValue(it) })
        )
      )
    assertWithMessage("arrayConcat with nested arrays value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayConcat - mirror behavior`() {
    assertEvaluatesToNull(
      evaluate(arrayConcat(nullValue(), nullValue())),
      "arrayConcat(null, null)"
    )
    assertEvaluatesToNull(
      evaluate(arrayConcat(nullValue(), field("non-existent"))),
      "arrayConcat(null, unset)"
    )
    assertEvaluatesToNull(
      evaluate(arrayConcat(field("non-existent"), nullValue())),
      "arrayConcat(unset, null)"
    )
    assertEvaluatesToNull(
      evaluate(arrayConcat(field("non-existent"), field("non-existent"))),
      "arrayConcat(unset, unset)"
    )
  }

  @Test
  fun `arrayConcat - unsupported argument after null returns error`() {
    val expr = arrayConcat(array(1, 2), nullValue(), constant("not an array"))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "arrayConcat with unsupported argument after null")
  }

  @Test
  fun `arrayConcat - with multiple arrays and mixed types`() {
    val expr =
      arrayConcat(
        array(1, 1L, 2L),
        array(2, Double.POSITIVE_INFINITY, 3L),
        array("string", "a", "b", "c", "d", "e", "f", "g", "h", "i")
      )
    val result = evaluate(expr)
    assertWithMessage("arrayConcat with multiple arrays and mixed types success")
      .that(result.isSuccess)
      .isTrue()
    val expected =
      evaluate(
        array(
          listOf(
            1,
            1L,
            2L,
            2,
            Double.POSITIVE_INFINITY,
            3L,
            "string",
            "a",
            "b",
            "c",
            "d",
            "e",
            "f",
            "g",
            "h",
            "i"
          )
        )
      )
    assertWithMessage("arrayConcat with multiple arrays and mixed types value")
      .that(result)
      .isEqualTo(expected)
  }
}
