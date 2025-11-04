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

package com.google.firebase.firestore.pipeline.evaluation.logical

import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.equalAny
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqualAny
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InTests {
  private val nullExpr = nullValue()
  private val nanExpr = constant(Double.NaN)
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- EqAny Tests ---
  @Test
  fun `eqAny - value found in array`() {
    val expr = equalAny(constant("hello"), array(constant("hello"), constant("world")))
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "eqAny(hello, [hello, world])")
  }

  @Test
  fun `eqAny - value not found in array`() {
    val expr = equalAny(constant(4L), array(constant(42L), constant("matang"), constant(true)))
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "eqAny(4, [42, matang, true])")
  }

  @Test
  fun `notEqAny - value not found in array`() {
    val expr = notEqualAny(constant(4L), array(constant(42L), constant("matang"), constant(true)))
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "notEqAny(4, [42, matang, true])")
  }

  @Test
  fun `notEqAny - value found in array`() {
    val expr = notEqualAny(constant("hello"), array(constant("hello"), constant("world")))
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "notEqAny(hello, [hello, world])")
  }

  @Test
  fun `eqAny - equivalent numerics`() {
    assertEvaluatesTo(
      evaluate(
        equalAny(constant(42L), array(constant(42.0), constant("matang"), constant(true))),
        emptyDoc
      ),
      true,
      "eqAny(42L, [42.0,...])"
    )
    assertEvaluatesTo(
      evaluate(
        equalAny(constant(42.0), array(constant(42L), constant("matang"), constant(true))),
        emptyDoc
      ),
      true,
      "eqAny(42.0, [42L,...])"
    )
  }

  @Test
  fun `eqAny - both input type is array`() {
    val searchArray = array(constant(1L), constant(2L), constant(3L))
    val valuesArray =
      array(
        array(constant(1L), constant(2L), constant(3L)),
        array(constant(4L), constant(5L), constant(6L))
      )
    assertEvaluatesTo(
      evaluate(equalAny(searchArray, valuesArray), emptyDoc),
      true,
      "eqAny([1,2,3], [[1,2,3],...])"
    )
  }

  @Test
  fun `eqAny - array not found returns error`() {
    val expr = equalAny(constant("matang"), field("non-existent-field"))
    assertEvaluatesToError(evaluate(expr, emptyDoc), "eqAny(matang, non-existent-field)")
  }

  @Test
  fun `eqAny - array is empty returns false`() {
    val expr = equalAny(constant(42L), array())
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "eqAny(42L, [])")
  }

  @Test
  fun `eqAny - search reference not found returns error`() {
    val expr = equalAny(field("non-existent-field"), array(constant(42L)))
    assertEvaluatesToError(evaluate(expr, emptyDoc), "eqAny(non-existent-field, [42L])")
  }

  @Test
  fun `eqAny - search is null`() {
    val expr = equalAny(nullExpr, array(nullExpr, constant(1L), constant("matang")))
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "eqAny(null, [null,1,matang])")
  }

  @Test
  fun `eqAny - search is null empty values array returns null`() {
    val expr = equalAny(nullExpr, array())
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "eqAny(null, [])")
  }

  @Test
  fun `eqAny - search is nan`() {
    val expr = equalAny(nanExpr, array(nanExpr, constant(42L), constant(3.14)))
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "eqAny(NaN, [NaN,42,3.14])")
  }

  @Test
  fun `eqAny - search is empty array is empty`() {
    val expr = equalAny(array(), array())
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "eqAny([], [])")
  }

  @Test
  fun `eqAny - search is empty array contains empty array returns true`() {
    val expr = equalAny(array(), array(array()))
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "eqAny([], [[]])")
  }

  @Test
  fun `eqAny - search is map`() {
    val searchMap = map(mapOf("foo" to constant(42L)))
    val valuesArray =
      array(
        array(constant(123L)),
        map(mapOf("bar" to constant(42L))),
        map(mapOf("foo" to constant(42L)))
      )
    assertEvaluatesTo(
      evaluate(equalAny(searchMap, valuesArray), emptyDoc),
      true,
      "eqAny(map, [...,map])"
    )
  }
}
