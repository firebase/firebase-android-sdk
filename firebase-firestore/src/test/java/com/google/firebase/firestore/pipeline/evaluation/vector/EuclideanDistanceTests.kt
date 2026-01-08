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
import com.google.firebase.firestore.pipeline.Expression.Companion.euclideanDistance
import com.google.firebase.firestore.pipeline.Expression.Companion.vector
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EuclideanDistanceTests {
  @Test
  fun `euclideanDistance - mirroring errors`() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = euclideanDistance(left, right)
      assertEvaluatesToNull(evaluate(expr), "euclideanDistance($name)")
    }
  }

  @Test
  fun `euclideanDistance - calculates distance`() {
    val vector1 = vector(listOf(0.0, 0.0).toDoubleArray())
    val vector2 = vector(listOf(3.0, 4.0).toDoubleArray())
    val expr = euclideanDistance(vector1, vector2)
    val result = evaluate(expr)
    assertWithMessage("euclideanDistance basic").that(result.isSuccess).isTrue()
    assertWithMessage("euclideanDistance basic value")
      .that(result.value)
      .isEqualTo(encodeValue(5.0))
  }

  @Test
  fun `euclideanDistance - zero vector`() {
    val vector1 = vector(listOf(0.0, 0.0).toDoubleArray())
    val vector2 = vector(listOf(0.0, 0.0).toDoubleArray())
    val expr = euclideanDistance(vector1, vector2)
    val result = evaluate(expr)
    assertWithMessage("euclideanDistance zero vector").that(result.isSuccess).isTrue()
    assertWithMessage("euclideanDistance zero vector value")
      .that(result.value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun `euclideanDistance - empty vectors`() {
    val vector1 = vector(emptyList<Double>().toDoubleArray())
    val vector2 = vector(emptyList<Double>().toDoubleArray())
    val expr = euclideanDistance(vector1, vector2)
    val result = evaluate(expr)
    assertWithMessage("euclideanDistance empty vectors").that(result.isSuccess).isTrue()
    assertWithMessage("euclideanDistance empty vectors value")
      .that(result.value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun `euclideanDistance - different vector lengths returns error`() {
    val vector1 = vector(listOf(1.0).toDoubleArray())
    val vector2 = vector(listOf(2.0, 3.0).toDoubleArray())
    val expr = euclideanDistance(vector1, vector2)
    assertEvaluatesToError(evaluate(expr), "euclideanDistance different vector lengths")
  }

  @Test
  fun `euclideanDistance - wrong input type returns error`() {
    val vector1 = vector(listOf(1.0, 2.0).toDoubleArray())
    val array2 = array(3.0, 4.0)
    val expr = euclideanDistance(vector1, array2)
    assertEvaluatesToError(evaluate(expr), "euclideanDistance wrong input type")
  }
}
