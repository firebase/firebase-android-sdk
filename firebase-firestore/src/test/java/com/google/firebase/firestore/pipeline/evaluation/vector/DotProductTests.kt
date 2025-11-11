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
import com.google.firebase.firestore.pipeline.Expression.Companion.dotProduct
import com.google.firebase.firestore.pipeline.Expression.Companion.vector
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DotProductTests {
  @Test
  fun `dotProduct - mirroring errors`() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = dotProduct(left, right)
      assertEvaluatesToNull(evaluate(expr), "dotProduct($name)")
    }
  }

  @Test
  fun `dotProduct - calculates dot product`() {
    val vector1 = vector(listOf(2.0, 1.0).toDoubleArray())
    val vector2 = vector(listOf(1.0, 5.0).toDoubleArray())
    val expr = dotProduct(vector1, vector2)
    val result = evaluate(expr)
    assertWithMessage("dotProduct basic").that(result.isSuccess).isTrue()
    assertWithMessage("dotProduct basic value").that(result.value).isEqualTo(encodeValue(7.0))
  }

  @Test
  fun `dotProduct - orthogonal vectors`() {
    val vector1 = vector(listOf(1.0, 0.0).toDoubleArray())
    val vector2 = vector(listOf(0.0, 5.0).toDoubleArray())
    val expr = dotProduct(vector1, vector2)
    val result = evaluate(expr)
    assertWithMessage("dotProduct orthogonal").that(result.isSuccess).isTrue()
    assertWithMessage("dotProduct orthogonal value").that(result.value).isEqualTo(encodeValue(0.0))
  }

  @Test
  fun `dotProduct - zero vector returns zero`() {
    val vector1 = vector(listOf(0.0, 0.0).toDoubleArray())
    val vector2 = vector(listOf(5.0, 100.0).toDoubleArray())
    val expr = dotProduct(vector1, vector2)
    val result = evaluate(expr)
    assertWithMessage("dotProduct zero vector").that(result.isSuccess).isTrue()
    assertWithMessage("dotProduct zero vector value").that(result.value).isEqualTo(encodeValue(0.0))
  }

  @Test
  fun `dotProduct - empty vectors returns zero`() {
    val vector1 = vector(emptyList<Double>().toDoubleArray())
    val vector2 = vector(emptyList<Double>().toDoubleArray())
    val expr = dotProduct(vector1, vector2)
    val result = evaluate(expr)
    assertWithMessage("dotProduct empty vectors").that(result.isSuccess).isTrue()
    assertWithMessage("dotProduct empty vectors value")
      .that(result.value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun `dotProduct - different vector lengths returns error`() {
    val vector1 = vector(listOf(1.0).toDoubleArray())
    val vector2 = vector(listOf(2.0, 3.0).toDoubleArray())
    val expr = dotProduct(vector1, vector2)
    assertEvaluatesToError(evaluate(expr), "dotProduct different vector lengths")
  }

  @Test
  fun `dotProduct - wrong input type returns error`() {
    val vector1 = vector(listOf(1.0, 2.0).toDoubleArray())
    val array2 = array(3.0, 4.0)
    val expr = dotProduct(vector1, array2)
    assertEvaluatesToError(evaluate(expr), "dotProduct wrong input type")
  }
}
