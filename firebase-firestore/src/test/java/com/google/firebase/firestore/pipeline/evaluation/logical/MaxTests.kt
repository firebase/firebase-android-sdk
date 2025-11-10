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
import com.google.firebase.firestore.pipeline.Expression.Companion.logicalMaximum
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaxTests {
  private val nullExpr = nullValue()
  private val nanExpr = constant(Double.NaN)
  private val errorExpr = Expression.error("error.field").equal(constant("random"))
  private val errorDoc =
    doc("coll/docError", 1, mapOf("error" to 123)) // "error.field" will be UNSET
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- LogicalMaximum Tests ---
  // Note: logicalMaximum is notImplemented in expressions.kt.
  // Tests will fail if NotImplementedError is thrown, which is the desired behavior
  // until the function is implemented. Assertions check for correctness once implemented.
  @Test
  fun `logicalMaximum - numeric type`() {
    val expr = logicalMaximum(constant(1L), logicalMaximum(constant(2.0), constant(3L)))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue(3L), "Max(1L, Max(2.0, 3L)) should be 3L")
  }

  @Test
  fun `logicalMaximum - string type`() {
    val expr = logicalMaximum(logicalMaximum(constant("a"), constant("b")), constant("c"))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue("c"), "Max(Max('a', 'b'), 'c') should be 'c'")
  }

  @Test
  fun `logicalMaximum - mixed type`() {
    val expr = logicalMaximum(constant(1L), logicalMaximum(constant("1"), constant(0L)))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue("1"), "Max(1L, Max('1', 0L)) should be '1'")
  }

  @Test
  fun `logicalMaximum - only null and error returns null`() {
    val expr = logicalMaximum(nullExpr, errorExpr)
    val result = evaluate(expr, errorDoc)
    assertEvaluatesToNull(result, "Max(Null, Error) should be Null")
  }

  @Test
  fun `logicalMaximum - nan and numbers`() {
    val expr1 = logicalMaximum(nanExpr, constant(0L))
    assertEvaluatesTo(evaluate(expr1, emptyDoc), encodeValue(0L), "Max(NaN, 0L) should be 0L")

    val expr2 = logicalMaximum(constant(0L), nanExpr)
    assertEvaluatesTo(evaluate(expr2, emptyDoc), encodeValue(0L), "Max(0L, NaN) should be 0L")

    val expr3 = logicalMaximum(nanExpr, nullExpr, errorExpr)
    assertEvaluatesTo(
      evaluate(expr3, errorDoc),
      encodeValue(Double.NaN),
      "Max(NaN, Null, Error) should be NaN"
    )

    val expr4 = logicalMaximum(nanExpr, errorExpr)
    assertEvaluatesTo(
      evaluate(expr4, errorDoc),
      encodeValue(Double.NaN),
      "Max(NaN, Error) should be NaN"
    )
  }

  @Test
  fun `logicalMaximum - error input skip`() {
    val expr = logicalMaximum(errorExpr, constant(1L))
    val result = evaluate(expr, errorDoc)
    assertEvaluatesTo(result, encodeValue(1L), "Max(Error, 1L) should be 1L")
  }

  @Test
  fun `logicalMaximum - null input skip`() {
    val expr = logicalMaximum(nullExpr, constant(1L))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue(1L), "Max(Null, 1L) should be 1L")
  }

  @Test
  fun `logicalMaximum - equivalent numerics`() {
    val expr = logicalMaximum(constant(1L), constant(1.0))
    val result = evaluate(expr, emptyDoc)
    // Firestore considers 1L and 1.0 equivalent for comparison. Max could return either.
    // C++ test implies it might return based on the first type if equivalent, or a preferred type.
    // Let's assert it's numerically 1. The exact Value proto might differ.
    // A more robust check might be needed if the exact proto type matters and varies.
    // For now, assuming it might return the integer form if an integer is dominant or first.
    assertEvaluatesTo(result, encodeValue(1L), "Max(1L, 1.0) should be numerically 1")
  }
}
