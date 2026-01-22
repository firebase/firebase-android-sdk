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

package com.google.firebase.firestore.pipeline.evaluation.vector

import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.vector
import com.google.firebase.firestore.pipeline.Expression.Companion.vectorLength
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VectorLengthTests {
  @Test
  fun `vectorLength - mirroring errors`() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(
        evaluate(vectorLength(testCase.input)),
        "vectorLength(${'$'}{testCase.name})"
      )
    }
  }

  @Test
  fun `vectorLength - length`() {
    val vector = vector(listOf(1.0, 2.0).toDoubleArray())
    val expr = vectorLength(vector)
    val result = evaluate(expr)
    assertWithMessage("vectorLength basic").that(result.isSuccess).isTrue()
    assertWithMessage("vectorLength basic value").that(result.value).isEqualTo(encodeValue(2L))
  }

  @Test
  fun `vectorLength - empty vector`() {
    val vector = vector(emptyList<Double>().toDoubleArray())
    val expr = vectorLength(vector)
    val result = evaluate(expr)
    assertWithMessage("vectorLength empty").that(result.isSuccess).isTrue()
    assertWithMessage("vectorLength empty value").that(result.value).isEqualTo(encodeValue(0L))
  }

  @Test
  fun `vectorLength - zero vector`() {
    val vector = vector(listOf(2.0).toDoubleArray())
    val expr = vectorLength(vector)
    val result = evaluate(expr)
    assertWithMessage("vectorLength zero").that(result.isSuccess).isTrue()
    assertWithMessage("vectorLength zero value").that(result.value).isEqualTo(encodeValue(1L))
  }

  @Test
  fun `vectorLength - not vector type returns error`() {
    assertEvaluatesToError(evaluate(vectorLength(array(1L))), "vectorLength array")
    assertEvaluatesToError(evaluate(vectorLength(constant("notAnArray"))), "vectorLength string")
  }
}
