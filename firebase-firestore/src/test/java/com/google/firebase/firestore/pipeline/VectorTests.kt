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

package com.google.firebase.firestore.pipeline

import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.cosineDistance
import com.google.firebase.firestore.pipeline.Expression.Companion.dotProduct
import com.google.firebase.firestore.pipeline.Expression.Companion.euclideanDistance
import com.google.firebase.firestore.pipeline.Expression.Companion.vector
import com.google.firebase.firestore.pipeline.Expression.Companion.vectorLength
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VectorTests {

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

  // --- CosineDistance Tests ---
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

  // --- DotProduct Tests ---
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

  // --- EuclideanDistance Tests ---
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
