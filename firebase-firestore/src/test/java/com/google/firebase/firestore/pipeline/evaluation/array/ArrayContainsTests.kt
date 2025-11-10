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

import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayContainsTests {
  // --- ArrayContains Tests ---
  @Test
  fun `arrayContains - value found in array`() {
    val expr = arrayContains(array("hello", "world"), constant("hello"))
    assertEvaluatesTo(evaluate(expr), true, "arrayContains value found")
  }

  @Test
  fun `arrayContains - equivalent numerics`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContains(arrayToSearch, constant(42.0))
    assertEvaluatesTo(evaluate(expr), true, "arrayContains equivalent numerics")
  }

  @Test
  fun `arrayContains - both input type is array`() {
    val arrayToSearch = array(array(1L, 2L, 3L), array(4L, 5L, 6L), array(7L, 8L, 9L))
    val valueToFind = array(1L, 2L, 3L)
    val expr = arrayContains(arrayToSearch, valueToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContains nested arrays")
  }

  @Test
  fun `arrayContains - search value is null returns true`() {
    val arrayToSearch = array(null, 1L, "matang", true)
    val expr = arrayContains(arrayToSearch, nullValue())
    assertEvaluatesTo(evaluate(expr), true, "arrayContains search for null")
  }

  @Test
  fun `arrayContains - search value is null empty values array returns false`() {
    val expr = arrayContains(array(), nullValue())
    assertEvaluatesTo(evaluate(expr), false, "arrayContains search for null in empty array")
  }

  @Test
  fun `arrayContains - search value is map`() {
    val arrayToSearch = array(123L, mapOf("foo" to 123L), mapOf("bar" to 42L), mapOf("foo" to 42L))
    val valueToFind = map(mapOf("foo" to 42L)) // Use Expression.map directly
    val expr = arrayContains(arrayToSearch, valueToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContains search for map")
  }

  @Test
  fun `arrayContains - search value is NaN`() {
    val arrayToSearch = array(Double.NaN, "foo")
    val valueToFind = constant(Double.NaN)
    val expr = arrayContains(arrayToSearch, valueToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContains search for NaN")
  }

  @Test
  fun `arrayContains - array to search is not array type returns error`() {
    val expr = arrayContains(constant("matang"), constant("values"))
    assertEvaluatesToError(evaluate(expr), "arrayContains first arg not array")
  }

  @Test
  fun `arrayContains - array to search not found returns null`() {
    val expr = arrayContains(field("not-exist"), constant("matang"))
    // Accessing a non-existent field results in UNSET, which then causes an error in arrayContains
    assertEvaluatesToNull(evaluate(expr), "arrayContains field not-exist for array")
  }

  @Test
  fun `arrayContains - array to search is empty returns false`() {
    val expr = arrayContains(array(), constant("matang"))
    assertEvaluatesTo(evaluate(expr), false, "arrayContains empty array")
  }

  @Test
  fun `arrayContains - search value reference not found returns false`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContains(arrayToSearch, field("not-exist"))
    // Accessing a non-existent field for the search value results in UNSET.
    // arrayContains then attempts to compare with UNSET, which is an error.
    assertEvaluatesTo(evaluate(expr), false, "arrayContains field not-exist for search value")
  }
}
