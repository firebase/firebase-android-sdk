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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayLength
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayLengthTests {
  // --- ArrayLength Tests ---
  @Test
  fun `arrayLength - length`() {
    val expr = arrayLength(array("1", 42L, true))
    val result = evaluate(expr)
    assertWithMessage("arrayLength basic").that(result.isSuccess).isTrue()
    assertWithMessage("arrayLength basic value").that(result.value).isEqualTo(encodeValue(3L))
  }

  @Test
  fun `arrayLength - empty array`() {
    val expr = arrayLength(array())
    val result = evaluate(expr)
    assertWithMessage("arrayLength empty").that(result.isSuccess).isTrue()
    assertWithMessage("arrayLength empty value").that(result.value).isEqualTo(encodeValue(0L))
  }

  @Test
  fun `arrayLength - array with duplicate elements`() {
    val expr = arrayLength(array(true, true))
    val result = evaluate(expr)
    assertWithMessage("arrayLength duplicates").that(result.isSuccess).isTrue()
    assertWithMessage("arrayLength duplicates value").that(result.value).isEqualTo(encodeValue(2L))
  }

  @Test
  fun `arrayLength - not array type returns error`() {
    assertEvaluatesToError(evaluate(arrayLength(constant("notAnArray"))), "arrayLength string")
    assertEvaluatesToError(evaluate(arrayLength(constant(123L))), "arrayLength long")
    assertEvaluatesToError(evaluate(arrayLength(constant(true))), "arrayLength boolean")
    assertEvaluatesToError(evaluate(arrayLength(map(mapOf("a" to 1)))), "arrayLength map")
  }
}
