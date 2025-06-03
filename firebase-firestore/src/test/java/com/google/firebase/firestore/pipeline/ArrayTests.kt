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
import com.google.firebase.firestore.pipeline.Expr.Companion.array // For the helper & direct use
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContainsAll
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayLength
import com.google.firebase.firestore.pipeline.Expr.Companion.constant // For the helper
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.map // For map literals
import com.google.firebase.firestore.pipeline.Expr.Companion.nullValue // For the helper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayTests {
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
    // Use the statically imported 'array' directly here as it takes List<Expr>
    // The elements.map { constant(it) } is correct as Expr.array(List<Expr>) expects Expr elements
    val arrayToSearch = array(elements.map { constant(it) })
    val valuesToFind = array(elements.map { constant(it) })
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll large number of elements")
  }

  // --- ArrayContainsAny Tests ---
  @Test
  fun `arrayContainsAny - value found in array`() {
    val arrayToSearch = array(42L, "matang", true)
    val valuesToFind = array("matang", false)
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny value found")
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
  fun `arrayContainsAny - both input type is array`() {
    val arrayToSearch = array(array(1L, 2L, 3L), array(4L, 5L, 6L), array(7L, 8L, 9L))
    val valuesToFind = array(array(1L, 2L, 3L), array(4L, 5L, 6L))
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny nested arrays")
  }

  @Test
  fun `arrayContainsAny - search is null returns null`() {
    val arrayToSearch = array(null, 1L, "matang", true)
    val valuesToFind = array(nullValue()) // Searching for a null
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    // Firestore/backend behavior: null comparisons return null.
    assertEvaluatesToNull(evaluate(expr), "arrayContainsAny search for null")
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
  fun `arrayContainsAny - array not found returns error`() {
    val expr = arrayContainsAny(field("not-exist"), array("matang", false))
    // Accessing a non-existent field results in UNSET, which then causes an error in
    // arrayContainsAny
    assertEvaluatesToError(evaluate(expr), "arrayContainsAny field not-exist for array")
  }

  @Test
  fun `arrayContainsAny - search not found returns error`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContainsAny(arrayToSearch, field("not-exist"))
    // Accessing a non-existent field results in UNSET, which then causes an error in
    // arrayContainsAny
    assertEvaluatesToError(evaluate(expr), "arrayContainsAny field not-exist for search values")
  }

  // --- ArrayContains Tests ---
  @Test
  fun `arrayContains - value found in array`() {
    val expr = arrayContains(array("hello", "world"), constant("hello"))
    assertEvaluatesTo(evaluate(expr), true, "arrayContains value found")
  }

  @Test
  fun `arrayContains - value not found in array`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContains(arrayToSearch, constant(4L))
    assertEvaluatesTo(evaluate(expr), false, "arrayContains value not found")
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
  fun `arrayContains - search value is null returns null`() {
    val arrayToSearch = array(null, 1L, "matang", true)
    val expr = arrayContains(arrayToSearch, nullValue())
    // Firestore/backend behavior: null comparisons return null.
    assertEvaluatesToNull(evaluate(expr), "arrayContains search for null")
  }

  @Test
  fun `arrayContains - search value is null empty values array returns null`() {
    val expr = arrayContains(array(), nullValue())
    // Firestore/backend behavior: null comparisons return null.
    assertEvaluatesToNull(evaluate(expr), "arrayContains search for null in empty array")
  }

  @Test
  fun `arrayContains - search value is map`() {
    val arrayToSearch = array(123L, mapOf("foo" to 123L), mapOf("bar" to 42L), mapOf("foo" to 42L))
    val valueToFind = map(mapOf("foo" to 42L)) // Use Expr.map directly
    val expr = arrayContains(arrayToSearch, valueToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContains search for map")
  }

  @Test
  fun `arrayContains - search value is NaN`() {
    val arrayToSearch = array(Double.NaN, "foo")
    val valueToFind = constant(Double.NaN)
    // Firestore/backend behavior: NaN comparisons are always false.
    val expr = arrayContains(arrayToSearch, valueToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContains search for NaN")
  }

  @Test
  fun `arrayContains - array to search is not array type returns error`() {
    val expr = arrayContains(constant("matang"), constant("values"))
    assertEvaluatesToError(evaluate(expr), "arrayContains first arg not array")
  }

  @Test
  fun `arrayContains - array to search not found returns error`() {
    val expr = arrayContains(field("not-exist"), constant("matang"))
    // Accessing a non-existent field results in UNSET, which then causes an error in arrayContains
    assertEvaluatesToError(evaluate(expr), "arrayContains field not-exist for array")
  }

  @Test
  fun `arrayContains - array to search is empty returns false`() {
    val expr = arrayContains(array(), constant("matang"))
    assertEvaluatesTo(evaluate(expr), false, "arrayContains empty array")
  }

  @Test
  fun `arrayContains - search value reference not found returns error`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContains(arrayToSearch, field("not-exist"))
    // Accessing a non-existent field for the search value results in UNSET.
    // arrayContains then attempts to compare with UNSET, which is an error.
    assertEvaluatesToError(evaluate(expr), "arrayContains field not-exist for search value")
  }

  // --- ArrayLength Tests ---
  @Test
  fun `arrayLength - length`() {
    val expr = arrayLength(array("1", 42L, true))
    val result = evaluate(expr)
    assertWithMessage("arrayLength basic").that(result.isSuccess).isTrue()
    assertWithMessage("arrayLength basic value").that(result.value).isEqualTo(encodeValue(3L))
  }

  @Test
  fun `arrayLength - empty array`() {
    val expr = arrayLength(array())
    val result = evaluate(expr)
    assertWithMessage("arrayLength empty").that(result.isSuccess).isTrue()
    assertWithMessage("arrayLength empty value").that(result.value).isEqualTo(encodeValue(0L))
  }

  @Test
  fun `arrayLength - array with duplicate elements`() {
    val expr = arrayLength(array(true, true))
    val result = evaluate(expr)
    assertWithMessage("arrayLength duplicates").that(result.isSuccess).isTrue()
    assertWithMessage("arrayLength duplicates value").that(result.value).isEqualTo(encodeValue(2L))
  }

  @Test
  fun `arrayLength - not array type returns error`() {
    assertEvaluatesToError(evaluate(arrayLength(constant("notAnArray"))), "arrayLength string")
    assertEvaluatesToError(evaluate(arrayLength(constant(123L))), "arrayLength long")
    assertEvaluatesToError(evaluate(arrayLength(constant(true))), "arrayLength boolean")
    assertEvaluatesToError(evaluate(arrayLength(map(mapOf("a" to 1)))), "arrayLength map")
  }
}
