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
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayReverse
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayReverseTests {
  // --- ArrayReverse Tests ---
  @Test
  fun `arrayReverse - one element`() {
    val expr = arrayReverse(array(42L))
    val result = evaluate(expr)
    assertWithMessage("arrayReverse one element success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(42L).map { encodeValue(it) })
    assertWithMessage("arrayReverse one element value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayReverse - duplicate elements`() {
    val expr = arrayReverse(array(1L, 2L, 2L, 3L))
    val result = evaluate(expr)
    assertWithMessage("arrayReverse duplicate elements success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(3L, 2L, 2L, 1L).map { encodeValue(it) })
    assertWithMessage("arrayReverse duplicate elements value")
      .that(result.value)
      .isEqualTo(expected)
  }

  @Test
  fun `arrayReverse - mixed types`() {
    val input = array("1", 42L, true)
    val expr = arrayReverse(input)
    val result = evaluate(expr)
    assertWithMessage("arrayReverse mixed types success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(encodeValue(true), encodeValue(42L), encodeValue("1")))
    assertWithMessage("arrayReverse mixed types value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayReverse - large array`() {
    val elements = (1..500).map { it.toLong() }
    val arrayToReverse = array(elements.map { constant(it) })
    val expr = arrayReverse(arrayToReverse)
    val result = evaluate(expr)
    assertWithMessage("arrayReverse large array success").that(result.isSuccess).isTrue()
    val expected = encodeValue(elements.reversed().map { encodeValue(it) })
    assertWithMessage("arrayReverse large array value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayReverse - not array type returns error`() {
    assertEvaluatesToError(evaluate(arrayReverse(constant("notAnArray"))), "arrayReverse string")
    assertEvaluatesToError(evaluate(arrayReverse(constant(123L))), "arrayReverse long")
    assertEvaluatesToError(evaluate(arrayReverse(constant(true))), "arrayReverse boolean")
    assertEvaluatesToError(evaluate(arrayReverse(map(mapOf("a" to 1)))), "arrayReverse map")
  }
}
