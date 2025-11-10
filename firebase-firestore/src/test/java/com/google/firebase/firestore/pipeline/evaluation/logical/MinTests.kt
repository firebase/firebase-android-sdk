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

import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.logicalMinimum
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MinTests {
  private val nullExpr = nullValue()
  private val nanExpr = constant(Double.NaN)
  private val errorExpr = Expression.error("error.field").equal(constant("random"))
  private val errorDoc =
    doc("coll/docError", 1, mapOf("error" to 123)) // "error.field" will be UNSET
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- LogicalMinimum Tests ---

  @Test
  fun `logicalMinimum - numeric type`() {
    val expr = logicalMinimum(constant(1L), logicalMinimum(constant(2.0), constant(3L)))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue(1L), "Min(1L, Min(2.0, 3L)) should be 1L")
  }

  @Test
  fun `logicalMinimum - string type`() {
    val expr = logicalMinimum(logicalMinimum(constant("a"), constant("b")), constant("c"))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue("a"), "Min(Min('a', 'b'), 'c') should be 'a'")
  }

  @Test
  fun `logicalMinimum - mixed type`() {
    val expr = logicalMinimum(constant(1L), logicalMinimum(constant("1"), constant(0L)))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue(0L), "Min(1L, Min('1', 0L)) should be 0L")
  }

  @Test
  fun `logicalMinimum - only null and error returns null`() {
    val expr = logicalMinimum(nullExpr, errorExpr)
    val result = evaluate(expr, errorDoc)
    assertEvaluatesToNull(result, "Min(Null, Error) should be Null")
  }

  @Test
  fun `logicalMinimum - nan and numbers`() {
    val expr1 = logicalMinimum(nanExpr, constant(0L))
    assertEvaluatesTo(
      evaluate(expr1, emptyDoc),
      encodeValue(Double.NaN),
      "Min(NaN, 0L) should be NaN"
    )

    val expr2 = logicalMinimum(constant(0L), nanExpr)
    assertEvaluatesTo(
      evaluate(expr2, emptyDoc),
      encodeValue(Double.NaN),
      "Min(0L, NaN) should be NaN"
    )

    val expr3 = logicalMinimum(nanExpr, nullExpr, errorExpr)
    assertEvaluatesTo(
      evaluate(expr3, errorDoc),
      encodeValue(Double.NaN),
      "Min(NaN, Null, Error) should be NaN"
    )

    val expr4 = logicalMinimum(nanExpr, errorExpr)
    assertEvaluatesTo(
      evaluate(expr4, errorDoc),
      encodeValue(Double.NaN),
      "Min(NaN, Error) should be NaN"
    )
  }

  @Test
  fun `logicalMinimum - error input skip`() {
    val expr = logicalMinimum(errorExpr, constant(1L))
    val result = evaluate(expr, errorDoc)
    assertEvaluatesTo(result, encodeValue(1L), "Min(Error, 1L) should be 1L")
  }

  @Test
  fun `logicalMinimum - null input skip`() {
    val expr = logicalMinimum(nullExpr, constant(1L))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue(1L), "Min(Null, 1L) should be 1L")
  }

  @Test
  fun `logicalMinimum - equivalent numerics`() {
    val expr = logicalMinimum(constant(1L), constant(1.0))
    val result = evaluate(expr, emptyDoc)
    // Similar to Max, asserting against integer form.
    assertEvaluatesTo(result, encodeValue(1L), "Min(1L, 1.0) should be numerically 1")
  }
}
