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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayContainsAnyTests {
  // --- ArrayContainsAny Tests ---
  @Test
  fun `arrayContainsAny - value found in array`() {
    val arrayToSearch = array(42L, "matang", true)
    val valuesToFind = array("matang", false)
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny value found")
  }

  @Test
  fun `arrayContainsAny - value found in array with null returns true`() {
    val arrayToSearch = array(nullValue(), "hello")
    val valuesToFind = array(nullValue(), "hello")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny with null")
  }

  @Test
  fun `arrayContainsAny - equivalent numerics`() {
    val arrayToSearch = array(42L, "matang", true)
    val valuesToFind = array(42.0, 2L)
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny equivalent numerics")
  }

  @Test
  fun `arrayContainsAny - values not found in array`() {
    val arrayToSearch = array(42L, "matang", true)
    val valuesToFind = array(99L, "false")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAny values not found")
  }

  @Test
  fun `arrayContainsAny - values not found in array with null returns false`() {
    val arrayToSearch = array(nullValue(), 42L)
    val valuesToFind = array(99L, "false")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAny values not found with null")
  }

  @Test
  fun `arrayContainsAny - values not found in array only null returns false`() {
    val arrayToSearch = array(nullValue(), nullValue())
    val valuesToFind = array(99L, "false")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAny values not found in only null")
  }

  @Test
  fun `arrayContainsAny - search values with null returns true`() {
    val arrayToSearch = array(nullValue(), 42L)
    val valuesToFind = array(nullValue(), "false")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny search values with null")
  }

  @Test
  fun `arrayContainsAny - search values only null returns true`() {
    val arrayToSearch = array(nullValue(), nullValue())
    val valuesToFind = array(nullValue(), nullValue())
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny search values only null")
  }

  @Test
  fun `arrayContainsAny - search values array with null and match returns true`() {
    val arrayToSearch = array(array(nullValue()), "a")
    val valuesToFind = array(array(nullValue()), "a")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny with array with null and match")
  }

  @Test
  fun `arrayContainsAny - search values array with null and no match returns true`() {
    val arrayToSearch = array(array(nullValue()), "a")
    val valuesToFind = array(array(nullValue()), "b")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny with array with null and no match")
  }

  @Test
  fun `arrayContainsAny - search values map with null and match returns true`() {
    val arrayToSearch = array(mapOf("a" to nullValue()), "b")
    val valuesToFind = array(mapOf("a" to nullValue()), "b")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny with map with null and match")
  }

  @Test
  fun `arrayContainsAny - search values map with null and no match returns true`() {
    val arrayToSearch = array(mapOf("a" to nullValue()), "b")
    val valuesToFind = array(mapOf("a" to nullValue()), "c")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny with map with null and no match")
  }

  @Test
  fun `arrayContainsAny - both input type is array`() {
    val arrayToSearch = array(array(1L, 2L, 3L), array(4L, 5L, 6L), array(7L, 8L, 9L))
    val valuesToFind = array(array(1L, 2L, 3L), array(4L, 5L, 6L))
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny nested arrays")
  }

  @Test
  fun `arrayContainsAny - search is null returns true`() {
    val arrayToSearch = array(null, 1L, "matang", true)
    val valuesToFind = array(nullValue()) // Searching for a null
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny search for null")
  }

  @Test
  fun `arrayContainsAny - array is not array type returns error`() {
    val expr = arrayContainsAny(constant("matang"), array("matang", false))
    assertEvaluatesToError(evaluate(expr), "arrayContainsAny first arg not array")
  }

  @Test
  fun `arrayContainsAny - search is not array type returns error`() {
    val expr = arrayContainsAny(array("matang", false), constant("matang"))
    assertEvaluatesToError(evaluate(expr), "arrayContainsAny second arg not array")
  }

  @Test
  fun `arrayContainsAny - array not found returns null`() {
    val expr = arrayContainsAny(field("not-exist"), array("matang", false))
    // Accessing a non-existent field results in UNSET, which evaluates to NULL.
    assertEvaluatesToNull(evaluate(expr), "arrayContainsAny field not-exist for array")
  }

  @Test
  fun `arrayContainsAny - search not found returns null`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContainsAny(arrayToSearch, field("not-exist"))
    // Accessing a non-existent field results in UNSET, which evaluates to NULL.
    assertEvaluatesToNull(evaluate(expr), "arrayContainsAny field not-exist for search values")
  }
}
