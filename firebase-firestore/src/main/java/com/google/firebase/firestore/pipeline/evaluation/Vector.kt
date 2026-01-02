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

package com.google.firebase.firestore.pipeline.evaluation

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.model.Values.isVectorValue
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeCase
import kotlin.math.sqrt

// === Vector Functions ===
internal val evaluateVectorLength = unaryFunction { value: Value ->
  if (value.valueTypeCase == ValueTypeCase.MAP_VALUE && isVectorValue(value)) {
    EvaluateResult.long(vectorLengthImpl(value))
  } else EvaluateResultError
}

internal val evaluateCosineDistance = binaryFunction { left: DoubleArray, right: DoubleArray ->
  cosineDistance(left, right)
}

internal val evaluateDotProductDistance = binaryFunction { left: DoubleArray, right: DoubleArray ->
  dotProductDistance(left, right)
}

internal val evaluateEuclideanDistance = binaryFunction { left: DoubleArray, right: DoubleArray ->
  euclideanDistance(left, right)
}

/**
 * Computes the Cosine Distance between two vectors: distance = 1 - <a></a>,b>/|a||b|.
 *
 * See [wikipedia](https://en.wikipedia.org/wiki/Cosine_similarity) for more info.
 *
 * It is recommended that customers use unit normalized vectors and dotProductDistance instead of
 * cosine distance which is mathematically equivalent, but avoids divisions and square roots.
 *
 * @throws IllegalArgumentException if the vectors are different dimensions.
 */
internal fun cosineDistance(vector1: DoubleArray, vector2: DoubleArray): EvaluateResult {
  if (vector1.size != vector2.size) return EvaluateResultError

  var sum1 = 0.0
  var sum2 = 0.0
  var sum3 = 0.0
  var sum4 = 0.0

  var norm11 = 0.0
  var norm12 = 0.0
  var norm13 = 0.0
  var norm14 = 0.0

  var norm21 = 0.0
  var norm22 = 0.0
  var norm23 = 0.0
  var norm24 = 0.0

  val limit = vector1.size and (4 - 1).inv()
  run {
    var i = 0
    while (i < limit) {
      sum1 = fma(vector1[i + 0], vector2[i + 0], sum1)
      sum2 = fma(vector1[i + 1], vector2[i + 1], sum2)
      sum3 = fma(vector1[i + 2], vector2[i + 2], sum3)
      sum4 = fma(vector1[i + 3], vector2[i + 3], sum4)

      norm11 = fma(vector1[i + 0], vector1[i + 0], norm11)
      norm12 = fma(vector1[i + 1], vector1[i + 1], norm12)
      norm13 = fma(vector1[i + 2], vector1[i + 2], norm13)
      norm14 = fma(vector1[i + 3], vector1[i + 3], norm14)

      norm21 = fma(vector2[i + 0], vector2[i + 0], norm21)
      norm22 = fma(vector2[i + 1], vector2[i + 1], norm22)
      norm23 = fma(vector2[i + 2], vector2[i + 2], norm23)
      norm24 = fma(vector2[i + 3], vector2[i + 3], norm24)
      i += 4
    }
  }

  var sum = sum1 + sum2 + sum3 + sum4
  var norm1 = norm11 + norm12 + norm13 + norm14
  var norm2 = norm21 + norm22 + norm23 + norm24

  for (i in limit until vector1.size) {
    val val1 = vector1[i]
    val val2 = vector2[i]
    sum += val1 * val2
    norm1 += val1 * val1
    norm2 += val2 * val2
  }
  val result = 1.0 - (sum / sqrt(norm1 * norm2))
  if (result.isNaN()) return EvaluateResultError
  return EvaluateResult.double(result)
}

/**
 * Computes the euclidean distance between two vectors: distance = |a-b|^2.
 *
 * See [wikipedia](https://en.wikipedia.org/wiki/Euclidean_distance) for more info.
 *
 * @throws IllegalArgumentException if the vectors are different dimensions.
 */
internal fun euclideanDistance(vector1: DoubleArray, vector2: DoubleArray): EvaluateResult {
  if (vector1.size != vector2.size) return EvaluateResultError

  var a1 = 0.0
  var a2 = 0.0
  var a3 = 0.0
  var a4 = 0.0

  val limit = vector1.size and (4 - 1).inv()
  run {
    var i = 0
    while (i < limit) {
      val diff1 = vector1[i + 0] - vector2[i + 0]
      val diff2 = vector1[i + 1] - vector2[i + 1]
      val diff3 = vector1[i + 2] - vector2[i + 2]
      val diff4 = vector1[i + 3] - vector2[i + 3]
      a1 = fma(diff1, diff1, a1)
      a2 = fma(diff2, diff2, a2)
      a3 = fma(diff3, diff3, a3)
      a4 = fma(diff4, diff4, a4)
      i += 4
    }
  }

  var result = a1 + a2 + a3 + a4

  // Process the remainder one by one.
  for (i in limit until vector1.size) {
    val diff = vector1[i] - vector2[i]
    result = fma(diff, diff, result)
  }
  return EvaluateResult.double(sqrt(result))
}

/**
 * Computes the sum of the products of two vectors.
 *
 * See [wikipedia](https://en.wikipedia.org/wiki/dot_product) for more info.
 *
 * @throws IllegalArgumentException if the vectors are different dimensions.
 */
internal fun dotProductDistance(vector1: DoubleArray, vector2: DoubleArray): EvaluateResult {
  if (vector1.size != vector2.size) return EvaluateResultError

  var a1 = 0.0
  var a2 = 0.0
  var a3 = 0.0
  var a4 = 0.0

  // Process data in independent chunks to reduce branching & data dependencies.
  val limit = vector1.size and (4 - 1).inv()
  run {
    var i = 0
    while (i < limit) {
      a1 = fma(vector1[i + 0], vector2[i + 0], a1)
      a2 = fma(vector1[i + 1], vector2[i + 1], a2)
      a3 = fma(vector1[i + 2], vector2[i + 2], a3)
      a4 = fma(vector1[i + 3], vector2[i + 3], a4)
      i += 4
    }
  }

  var result = a1 + a2 + a3 + a4

  // Process the remainder one by one.
  for (i in limit until vector1.size) {
    result += vector1[i] * vector2[i]
  }

  return EvaluateResult.double(result)
}

/** Computes the fused multiply-add operation a * b + c. */
private fun fma(a: Double, b: Double, c: Double): Double {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    // Use the native Java 9+ implementation for higher accuracy on modern Android.
    return nativeFma(a, b, c)
  } else {
    // Fallback to the standard (a * b) + c operation for older versions.
    return (a * b) + c
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun nativeFma(a: Double, b: Double, c: Double): Double {
  return Math.fma(a, b, c)
}
