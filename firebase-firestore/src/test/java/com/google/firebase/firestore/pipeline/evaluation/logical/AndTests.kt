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

import com.google.firebase.firestore.pipeline.BooleanExpression
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.and
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndTests {

  private val trueExpr = constant(true)
  private val falseExpr = constant(false)
  private val errorExpr = Expression.error("test").equal(constant("random"))

  private val errorDoc =
    doc("coll/docError", 1, mapOf("error" to 123)) // "error.field" will be UNSET
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- And (&&) Tests ---
  // 2 Operands
  @Test
  fun `and - false, false is false`() {
    val expr = and(falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(false, false)")
  }

  @Test
  fun `and - false, error is false`() {
    val expr = and(falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(false, error)")
  }

  @Test
  fun `and - false, true is false`() {
    val expr = and(falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(false, true)")
  }

  @Test
  fun `and - error, false is false`() {
    val expr = and(errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(error, false)")
  }

  @Test
  fun `and - error, error is error`() {
    val expr = and(errorExpr as BooleanExpression, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(error, error)")
  }

  @Test
  fun `and - error, true is error`() {
    val expr = and(errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(error, true)")
  }

  @Test
  fun `and - true, false is false`() {
    val expr = and(trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(true, false)")
  }

  @Test
  fun `and - true, error is error`() {
    val expr = and(trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(true, error)")
  }

  @Test
  fun `and - true, true is true`() {
    val expr = and(trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "AND(true, true)")
  }

  // 3 Operands
  @Test
  fun `and - false, false, false is false`() {
    val expr = and(falseExpr, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(F,F,F)")
  }

  @Test
  fun `and - false, false, error is false`() {
    val expr = and(falseExpr, falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,F,E)")
  }

  @Test
  fun `and - false, false, true is false`() {
    val expr = and(falseExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(F,F,T)")
  }

  @Test
  fun `and - false, error, false is false`() {
    val expr = and(falseExpr, errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,E,F)")
  }

  @Test
  fun `and - false, error, error is false`() {
    val expr = and(falseExpr, errorExpr as BooleanExpression, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,E,E)")
  }

  @Test
  fun `and - false, error, true is false`() {
    val expr = and(falseExpr, errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,E,T)")
  }

  @Test
  fun `and - false, true, false is false`() {
    val expr = and(falseExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(F,T,F)")
  }

  @Test
  fun `and - false, true, error is false`() {
    val expr = and(falseExpr, trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,T,E)")
  }

  @Test
  fun `and - false, true, true is false`() {
    val expr = and(falseExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(F,T,T)")
  }

  @Test
  fun `and - error, false, false is false`() {
    val expr = and(errorExpr as BooleanExpression, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,F,F)")
  }

  @Test
  fun `and - error, false, error is false`() {
    val expr = and(errorExpr as BooleanExpression, falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,F,E)")
  }

  @Test
  fun `and - error, false, true is false`() {
    val expr = and(errorExpr as BooleanExpression, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,F,T)")
  }

  @Test
  fun `and - error, error, false is false`() {
    val expr = and(errorExpr as BooleanExpression, errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,E,F)")
  }

  @Test
  fun `and - error, error, error is error`() {
    val expr =
      and(
        errorExpr as BooleanExpression,
        errorExpr as BooleanExpression,
        errorExpr as BooleanExpression
      )
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(E,E,E)")
  }

  @Test
  fun `and - error, error, true is error`() {
    val expr = and(errorExpr as BooleanExpression, errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(E,E,T)")
  }

  @Test
  fun `and - error, true, false is false`() {
    val expr = and(errorExpr as BooleanExpression, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,T,F)")
  }

  @Test
  fun `and - error, true, error is error`() {
    val expr = and(errorExpr as BooleanExpression, trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(E,T,E)")
  }

  @Test
  fun `and - error, true, true is error`() {
    val expr = and(errorExpr as BooleanExpression, trueExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(E,T,T)")
  }

  @Test
  fun `and - true, false, false is false`() {
    val expr = and(trueExpr, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(T,F,F)")
  }

  @Test
  fun `and - true, false, error is false`() {
    val expr = and(trueExpr, falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(T,F,E)")
  }

  @Test
  fun `and - true, false, true is false`() {
    val expr = and(trueExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(T,F,T)")
  }

  @Test
  fun `and - true, error, false is false`() {
    val expr = and(trueExpr, errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(T,E,F)")
  }

  @Test
  fun `and - true, error, error is error`() {
    val expr = and(trueExpr, errorExpr as BooleanExpression, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(T,E,E)")
  }

  @Test
  fun `and - true, error, true is error`() {
    val expr = and(trueExpr, errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(T,E,T)")
  }

  @Test
  fun `and - true, true, false is false`() {
    val expr = and(trueExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(T,T,F)")
  }

  @Test
  fun `and - true, true, error is error`() {
    val expr = and(trueExpr, trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(T,T,E)")
  }

  @Test
  fun `and - true, true, true is true`() {
    val expr = and(trueExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "AND(T,T,T)")
  }

  // Nested
  @Test
  fun `and - nested and`() {
    val child = and(trueExpr, falseExpr) // false
    val expr = and(child, trueExpr) // false AND true -> false
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "Nested AND failed")
  }

  // Multiple Arguments (already covered by 3-operand tests)
  @Test
  fun `and - multiple arguments`() {
    val expr = and(trueExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "Multiple args AND failed")
  }
}
