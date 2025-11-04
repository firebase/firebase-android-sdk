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
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayConcat
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firestore.v1.Value
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayConcatTests {
  // --- ArrayConcat Tests ---
  @Test
  fun `arrayConcat - two arrays`() {
    val expr = arrayConcat(array(1L, 2L), array(3L, 4L))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat two arrays success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(1L, 2L, 3L, 4L).map { encodeValue(it) })
    assertWithMessage("arrayConcat two arrays value").that(result.value).isEqualTo(expected)
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
  fun `arrayConcat - with empty arrays`() {
    val expr = arrayConcat(array(), array(1L, 2L), array())
    val result = evaluate(expr)
    assertWithMessage("arrayConcat with empty arrays success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(1L, 2L).map { encodeValue(it) })
    assertWithMessage("arrayConcat with empty arrays value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayConcat - with mixed types`() {
    val expr = arrayConcat(array(1L, "a"), array(true, 3.0))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat with mixed types success").that(result.isSuccess).isTrue()
    val expected =
      encodeValue(listOf(encodeValue(1L), encodeValue("a"), encodeValue(true), encodeValue(3.0)))
    assertWithMessage("arrayConcat with mixed types value").that(result.value).isEqualTo(expected)
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
  fun `arrayConcat - with NaN`() {
    val expr = arrayConcat(array(1L, Double.NaN), array(3L, 4L))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat with NaN success").that(result.isSuccess).isTrue()
    val expected =
      encodeValue(
        listOf(encodeValue(1L), encodeValue(Double.NaN), encodeValue(3L), encodeValue(4L))
      )
    assertWithMessage("arrayConcat with NaN value").that(result.value).isEqualTo(expected)
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
  fun `arrayConcat - general provider cases`() {
    // [1.0, 2, 3] and [4, Double.NaN, 6] -> [1.0, 2, 3, 4, Double.NaN, 6]
    val expr1 = arrayConcat(array(1.0, 2L, 3L), array(4L, Double.NaN, 6L))
    val result1 = evaluate(expr1)
    assertWithMessage("arrayConcat general case 1 success").that(result1.isSuccess).isTrue()
    val expected1 =
      encodeValue(
        listOf(
          encodeValue(1.0),
          encodeValue(2L),
          encodeValue(3L),
          encodeValue(4L),
          encodeValue(Double.NaN),
          encodeValue(6L)
        )
      )
    assertWithMessage("arrayConcat general case 1 value").that(result1.value).isEqualTo(expected1)

    // [] and [1, 2, 3] -> [1, 2, 3]
    val expr2 = arrayConcat(array(), array(1L, 2L, 3L))
    val result2 = evaluate(expr2)
    assertWithMessage("arrayConcat empty with non-empty success").that(result2.isSuccess).isTrue()
    val expected2 = encodeValue(listOf(1L, 2L, 3L).map { encodeValue(it) })
    assertWithMessage("arrayConcat empty with non-empty value")
      .that(result2.value)
      .isEqualTo(expected2)

    // [1, 2, 3] and [] -> [1, 2, 3]
    val expr3 = arrayConcat(array(1L, 2L, 3L), array())
    val result3 = evaluate(expr3)
    assertWithMessage("arrayConcat non-empty with empty success").that(result3.isSuccess).isTrue()
    val expected3 = encodeValue(listOf(1L, 2L, 3L).map { encodeValue(it) })
    assertWithMessage("arrayConcat non-empty with empty value")
      .that(result3.value)
      .isEqualTo(expected3)

    // [] and [] -> []
    val expr4 = arrayConcat(array(), array())
    val result4 = evaluate(expr4)
    assertWithMessage("arrayConcat two empty arrays success").that(result4.isSuccess).isTrue()
    val expected4 = encodeValue(emptyArray<Value>().asIterable())
    assertWithMessage("arrayConcat two empty arrays value").that(result4.value).isEqualTo(expected4)

    // [null] and ["d", "l", "c"] -> [null, "d", "l", "c"]
    val expr5 = arrayConcat(array(nullValue()), array("d", "l", "c"))
    val result5 = evaluate(expr5)
    assertWithMessage("arrayConcat with array containing null success")
      .that(result5.isSuccess)
      .isTrue()
    val expected5 =
      encodeValue(listOf(NULL_VALUE, encodeValue("d"), encodeValue("l"), encodeValue("c")))
    assertWithMessage("arrayConcat with array containing null value")
      .that(result5.value)
      .isEqualTo(expected5)

    // [null] and [null] -> [null, null]
    val expr6 = arrayConcat(array(nullValue()), array(nullValue()))
    val result6 = evaluate(expr6)
    assertWithMessage("arrayConcat with two arrays containing null success")
      .that(result6.isSuccess)
      .isTrue()
    val expected6 = encodeValue(listOf(NULL_VALUE, NULL_VALUE))
    assertWithMessage("arrayConcat with two arrays containing null value")
      .that(result6.value)
      .isEqualTo(expected6)
  }

  @Test
  fun `arrayConcat - with null argument returns null`() {
    val expr = arrayConcat(nullValue(), array(nullValue()))
    val result = evaluate(expr)
    assertEvaluatesToNull(result, "arrayConcat with null argument")
  }

  @Test
  fun `arrayConcat - with unset argument returns error`() {
    val expr = arrayConcat(field("non-existent"), array(1L, 2L))
    val result = evaluate(expr)
    assertEvaluatesToNull(result, "arrayConcat with unset argument")
  }
}
