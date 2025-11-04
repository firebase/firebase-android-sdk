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
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAll
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayContainsAllTests {
  // --- ArrayContainsAll Tests ---
  @Test
  fun `arrayContainsAll - contains all`() {
    val arrayToSearch = array("1", 42L, true, "additional", "values", "in", "array")
    val valuesToFind = array("1", 42L, true)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll basic true case")
  }

  @Test
  fun `arrayContainsAll - does not contain all`() {
    val arrayToSearch = array("1", 42L, true)
    val valuesToFind = array("1", 99L)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAll basic false case")
  }

  @Test
  fun `arrayContainsAll - equivalent numerics`() {
    val arrayToSearch = array(42L, true, "additional", "values", "in", "array")
    val valuesToFind = array(42.0, true)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll equivalent numerics")
  }

  @Test
  fun `arrayContainsAll - array to search is empty`() {
    val arrayToSearch = array()
    val valuesToFind = array(42.0, true)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAll empty array to search")
  }

  @Test
  fun `arrayContainsAll - search value is empty`() {
    val arrayToSearch = array(42.0, true)
    val valuesToFind = array()
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll empty search values")
  }

  @Test
  fun `arrayContainsAll - search value is NaN`() {
    val arrayToSearch = array(Double.NaN, 42.0)
    val valuesToFind = array(Double.NaN)
    // Firestore/backend behavior: NaN comparisons are always false.
    // arrayContainsAll uses standard equality which means NaN == NaN is false.
    // If arrayToSearch contains NaN and valuesToFind contains NaN, it won't find it.
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAll with NaN in search values")
  }

  @Test
  fun `arrayContainsAll - search value has duplicates`() {
    val arrayToSearch = array(true, "hi")
    val valuesToFind = array(true, true, true)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll with duplicate search values")
  }

  @Test
  fun `arrayContainsAll - array to search is empty and search value is empty`() {
    val arrayToSearch = array()
    val valuesToFind = array()
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll both empty")
  }

  @Test
  fun `arrayContainsAll - large number of elements`() {
    val elements = (1..500).map { it.toLong() }
    // Use the statically imported 'array' directly here as it takes List<Expression>
    // The elements.map { constant(it) } is correct as Expression.array(List<Expression>) expects
    // Expression elements
    val arrayToSearch = array(elements.map { constant(it) })
    val valuesToFind = array(elements.map { constant(it) })
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll large number of elements")
  }
}
