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
import com.google.firebase.firestore.pipeline.Expression.Companion.cosineDistance
import com.google.firebase.firestore.pipeline.Expression.Companion.vector
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CosineDistanceTests {

  @Test
  fun `cosineDistance - calculates distance`() {
    val vector1 = vector(listOf(0.0, 1.0).toDoubleArray())
    val vector2 = vector(listOf(5.0, 100.0).toDoubleArray())
    val expr = cosineDistance(vector1, vector2)
    val result = evaluate(expr)
    assertWithMessage("cosineDistance basic").that(result.isSuccess).isTrue()
    assertWithMessage("cosineDistance basic value")
      .that(result.value)
      .isEqualTo(encodeValue(0.0012476611221553524))
  }

  @Test
  fun `cosineDistance - zero vector returns error`() {
    val vector1 = vector(listOf(0.0, 0.0).toDoubleArray())
    val vector2 = vector(listOf(5.0, 100.0).toDoubleArray())
    val expr = cosineDistance(vector1, vector2)
    assertEvaluatesToError(evaluate(expr), "cosineDistance zero vector")
  }

  @Test
  fun `cosineDistance - empty vectors returns error`() {
    val vector1 = vector(emptyList<Double>().toDoubleArray())
    val vector2 = vector(emptyList<Double>().toDoubleArray())
    val expr = cosineDistance(vector1, vector2)
    assertEvaluatesToError(evaluate(expr), "cosineDistance empty vectors")
  }

  @Test
  fun `cosineDistance - different vector lengths returns error`() {
    val vector1 = vector(listOf(1.0).toDoubleArray())
    val vector2 = vector(listOf(2.0, 3.0).toDoubleArray())
    val expr = cosineDistance(vector1, vector2)
    assertEvaluatesToError(evaluate(expr), "cosineDistance different vector lengths")
  }

  @Test
  fun `cosineDistance - wrong input type returns error`() {
    val vector1 = vector(listOf(1.0, 2.0).toDoubleArray())
    val array2 = array(3.0, 4.0)
    val expr = cosineDistance(vector1, array2)
    assertEvaluatesToError(evaluate(expr), "cosineDistance wrong input type")
  }
}
