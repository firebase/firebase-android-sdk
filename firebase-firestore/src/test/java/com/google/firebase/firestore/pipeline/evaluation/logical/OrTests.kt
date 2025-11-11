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
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.or
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrTests {
  private val trueExpr = constant(true)
  private val falseExpr = constant(false)
  private val nullExpr = nullBoolean()
  private val unsetExpr = unsetBoolean()
  private val stringExpr = stringBoolean()
  private val errorExpr = Expression.error("error.field").equal(constant("random"))
  private val errorDoc =
    doc("coll/docError", 1, mapOf("error" to 123)) // "error.field" will be UNSET
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- Or (||) Tests ---
  // 2 Operands
  @Test
  fun `or - false, false is false`() {
    val expr = or(falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "OR(F,F)")
  }

  @Test
  fun `or - false, error is error`() {
    val expr = or(falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(F,E)")
  }

  @Test
  fun `or - false, null is null`() {
    val expr = or(falseExpr, nullExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(F,N)")
  }

  @Test
  fun `or - false, true is true`() {
    val expr = or(falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,T)")
  }

  @Test
  fun `or - error, false is error`() {
    val expr = or(errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,F)")
  }

  @Test
  fun `or - null, false is null`() {
    val expr = or(nullExpr, falseExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(N,F)")
  }

  @Test
  fun `or - error, error is error`() {
    val expr = or(errorExpr as BooleanExpression, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,E)")
  }

  @Test
  fun `or - null, null is null`() {
    val expr = or(nullExpr, nullExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(N,N)")
  }

  @Test
  fun `or - error, true is error`() {
    val expr = or(errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,T)")
  }

  @Test
  fun `or - null, true is true`() {
    val expr = or(nullExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(N,T)")
  }

  @Test
  fun `or - true, false is true`() {
    val expr = or(trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,F)")
  }

  @Test
  fun `or - true, error is true`() {
    val expr = or(trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,E)")
  }

  @Test
  fun `or - true, null is true`() {
    val expr = or(trueExpr, nullExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,N)")
  }

  @Test
  fun `or - true, true is true`() {
    val expr = or(trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,T)")
  }

  // 3 Operands
  @Test
  fun `or - false, false, false is false`() {
    val expr = or(falseExpr, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "OR(F,F,F)")
  }

  @Test
  fun `or - false, false, error is error`() {
    val expr = or(falseExpr, falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(F,F,E)")
  }

  @Test
  fun `or - false, false, null is null`() {
    val expr = or(falseExpr, falseExpr, nullExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(F,F,N)")
  }

  @Test
  fun `or - false, false, true is true`() {
    val expr = or(falseExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,F,T)")
  }

  @Test
  fun `or - false, error, false is error`() {
    val expr = or(falseExpr, errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(F,E,F)")
  }

  @Test
  fun `or - false, null, false is null`() {
    val expr = or(falseExpr, nullExpr, falseExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(F,N,F)")
  }

  @Test
  fun `or - false, error, error is error`() {
    val expr = or(falseExpr, errorExpr as BooleanExpression, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(F,E,E)")
  }

  @Test
  fun `or - false, null, null is null`() {
    val expr = or(falseExpr, nullExpr, nullExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(F,N,N)")
  }

  @Test
  fun `or - false, error, true is error`() {
    val expr = or(falseExpr, errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(F,E,T)")
  }

  @Test
  fun `or - false, null, true is true`() {
    val expr = or(falseExpr, nullExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,N,T)")
  }

  @Test
  fun `or - false, true, false is true`() {
    val expr = or(falseExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,T,F)")
  }

  @Test
  fun `or - false, true, error is true`() {
    val expr = or(falseExpr, trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(F,T,E)")
  }

  @Test
  fun `or - false, true, null is true`() {
    val expr = or(falseExpr, trueExpr, nullExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,T,N)")
  }

  @Test
  fun `or - false, true, true is true`() {
    val expr = or(falseExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,T,T)")
  }

  @Test
  fun `or - error, false, false is error`() {
    val expr = or(errorExpr as BooleanExpression, falseExpr, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,F,F)")
  }

  @Test
  fun `or - null, false, false is null`() {
    val expr = or(nullExpr, falseExpr, falseExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(N,F,F)")
  }

  @Test
  fun `or - error, false, error is error`() {
    val expr = or(errorExpr as BooleanExpression, falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,F,E)")
  }

  @Test
  fun `or - null, false, null is null`() {
    val expr = or(nullExpr, falseExpr, nullExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(N,F,N)")
  }

  @Test
  fun `or - error, false, true is error`() {
    val expr = or(errorExpr as BooleanExpression, falseExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,F,T)")
  }

  @Test
  fun `or - null, false, true is true`() {
    val expr = or(nullExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(N,F,T)")
  }

  @Test
  fun `or - error, error, false is error`() {
    val expr = or(errorExpr as BooleanExpression, errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,E,F)")
  }

  @Test
  fun `or - null, null, false is null`() {
    val expr = or(nullExpr, nullExpr, falseExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(N,N,F)")
  }

  @Test
  fun `or - error, error, error is error`() {
    val expr =
      or(
        errorExpr as BooleanExpression,
        errorExpr as BooleanExpression,
        errorExpr as BooleanExpression
      )
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,E,E)")
  }

  @Test
  fun `or - null, null, null is null`() {
    val expr = or(nullExpr, nullExpr, nullExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(N,N,N)")
  }

  @Test
  fun `or - error, error, true is error`() {
    val expr = or(errorExpr as BooleanExpression, errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,E,T)")
  }

  @Test
  fun `or - null, null, true is true`() {
    val expr = or(nullExpr, nullExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(N,N,T)")
  }

  @Test
  fun `or - error, true, false is error`() {
    val expr = or(errorExpr as BooleanExpression, trueExpr, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,T,F)")
  }

  @Test
  fun `or - null, true, false is true`() {
    val expr = or(nullExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(N,T,F)")
  }

  @Test
  fun `or - error, true, error is error`() {
    val expr = or(errorExpr as BooleanExpression, trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,T,E)")
  }

  @Test
  fun `or - null, true, null is true`() {
    val expr = or(nullExpr, trueExpr, nullExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(N,T,N)")
  }

  @Test
  fun `or - error, true, true is error`() {
    val expr = or(errorExpr as BooleanExpression, trueExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,T,T)")
  }

  @Test
  fun `or - null, true, true is true`() {
    val expr = or(nullExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(N,T,T)")
  }

  @Test
  fun `or - true, false, false is true`() {
    val expr = or(trueExpr, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,F,F)")
  }

  @Test
  fun `or - true, false, error is true`() {
    val expr = or(trueExpr, falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,F,E)")
  }

  @Test
  fun `or - true, false, null is true`() {
    val expr = or(trueExpr, falseExpr, nullExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,F,N)")
  }

  @Test
  fun `or - true, false, true is true`() {
    val expr = or(trueExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,F,T)")
  }

  @Test
  fun `or - true, error, false is true`() {
    val expr = or(trueExpr, errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,E,F)")
  }

  @Test
  fun `or - true, null, false is true`() {
    val expr = or(trueExpr, nullExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,N,F)")
  }

  @Test
  fun `or - true, error, error is true`() {
    val expr = or(trueExpr, errorExpr as BooleanExpression, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,E,E)")
  }

  @Test
  fun `or - true, null, null is true`() {
    val expr = or(trueExpr, nullExpr, nullExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,N,N)")
  }

  @Test
  fun `or - true, error, true is true`() {
    val expr = or(trueExpr, errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,E,T)")
  }

  @Test
  fun `or - true, null, true is true`() {
    val expr = or(trueExpr, nullExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,N,T)")
  }

  @Test
  fun `or - true, true, false is true`() {
    val expr = or(trueExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,T,F)")
  }

  @Test
  fun `or - true, true, error is true`() {
    val expr = or(trueExpr, trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,T,E)")
  }

  @Test
  fun `or - true, true, null is true`() {
    val expr = or(trueExpr, trueExpr, nullExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,T,N)")
  }

  @Test
  fun `or - true, true, true is true`() {
    val expr = or(trueExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,T,T)")
  }

  // Nested
  @Test
  fun `or - nested or`() {
    val child = or(trueExpr, trueExpr) // true
    val expr = or(child, falseExpr) // true OR false -> true
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "Nested OR")
  }

  // Multiple Arguments (already covered by 3-operand tests)
  @Test
  fun `or - multiple arguments`() {
    val expr = or(trueExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "Multiple args OR")
  }

  @Test
  fun `or - error, null is error`() {
    val expr = or(errorExpr as BooleanExpression, nullExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,N)")
  }

  @Test
  fun `or - error, null, true is error`() {
    val expr = or(errorExpr as BooleanExpression, nullExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,N,T)")
  }

  @Test
  fun `or - true, unset is true`() {
    val expr = or(trueExpr, unsetExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,U)")
  }

  @Test
  fun `or - unset, true is true`() {
    val expr = or(unsetExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(U,T)")
  }

  @Test
  fun `or - false, unset is null`() {
    val expr = or(falseExpr, unsetExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(F,U)")
  }

  @Test
  fun `or - unset, false is null`() {
    val expr = or(unsetExpr, falseExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(U,F)")
  }

  @Test
  fun `or - unset, unset is null`() {
    val expr = or(unsetExpr, unsetExpr)
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "OR(U,U)")
  }

  @Test
  fun `or - unset, error is error`() {
    val expr = or(unsetExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(U,E)")
  }

  @Test
  fun `or - error, unset is error`() {
    val expr = or(errorExpr as BooleanExpression, unsetExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,U)")
  }

  @Test
  fun `or - unset, string is error`() {
    val expr = or(unsetExpr, stringExpr)
    assertEvaluatesToError(evaluate(expr, emptyDoc), "OR(U,S)")
  }
}
