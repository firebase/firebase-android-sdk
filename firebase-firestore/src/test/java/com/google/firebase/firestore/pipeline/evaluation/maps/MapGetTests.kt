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

package com.google.firebase.firestore.pipeline.evaluation.maps

import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.mapGet
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToUnset
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapGetTests {

  @Test
  fun `mapGet - get existing key returns value`() {
    val mapExpr = map(mapOf("a" to 1L, "b" to 2L, "c" to 3L))
    val expr = mapGet(mapExpr, constant("b"))
    assertEvaluatesTo(evaluate(expr), encodeValue(2L), "mapGet existing key should return value")
  }

  @Test
  fun `mapGet - get missing key returns unset`() {
    val mapExpr = map(mapOf("a" to 1L, "b" to 2L, "c" to 3L))
    val expr = mapGet(mapExpr, "d")
    assertEvaluatesToUnset(evaluate(expr), "mapGet missing key should return unset")
  }

  @Test
  fun `mapGet - get from empty map returns unset`() {
    val mapExpr = map(emptyMap())
    val expr = mapGet(mapExpr, "d")
    assertEvaluatesToUnset(evaluate(expr), "mapGet from empty map should return unset")
  }

  @Test
  fun `mapGet - wrong map type returns error`() {
    val mapExpr = constant("not a map") // Pass a string instead of a map
    val expr = mapGet(mapExpr, "d")
    // This should evaluate to an error because the first argument is not a map.
    assertEvaluatesToError(evaluate(expr), "mapGet with wrong map type should return error")
  }

  @Test
  fun `mapGet - wrong key type returns error`() {
    val mapExpr = map(emptyMap())
    val expr = mapGet(mapExpr, constant(false))
    // This should evaluate to an error because the key argument is not a string.
    assertEvaluatesToError(evaluate(expr), "mapGet with wrong key type should return error")
  }
}
