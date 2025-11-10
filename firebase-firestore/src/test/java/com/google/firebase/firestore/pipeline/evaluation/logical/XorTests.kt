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
import com.google.firebase.firestore.pipeline.Expression.Companion.xor
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XorTests {
  private val trueExpr = constant(true)
  private val falseExpr = constant(false)
  private val errorExpr = Expression.error("error.field").equal(constant("random"))
  private val errorDoc =
    doc("coll/docError", 1, mapOf("error" to 123)) // "error.field" will be UNSET
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- Xor Tests ---
  // 2 Operands
  @Test
  fun `xor - false, false is false`() {
    val expr = xor(falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(F,F)")
  }

  @Test
  fun `xor - false, error is error`() {
    val expr = xor(falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,E)")
  }

  @Test
  fun `xor - false, true is true`() {
    val expr = xor(falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(F,T)")
  }

  @Test
  fun `xor - error, false is error`() {
    val expr = xor(errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,F)")
  }

  @Test
  fun `xor - error, error is error`() {
    val expr = xor(errorExpr as BooleanExpression, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,E)")
  }

  @Test
  fun `xor - error, true is error`() {
    val expr = xor(errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,T)")
  }

  @Test
  fun `xor - true, false is true`() {
    val expr = xor(trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(T,F)")
  }

  @Test
  fun `xor - true, error is error`() {
    val expr = xor(trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,E)")
  }

  @Test
  fun `xor - true, true is false`() {
    val expr = xor(trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(T,T)")
  }

  // 3 Operands (XOR is true if an odd number of inputs are true)
  @Test
  fun `xor - false, false, false is false`() {
    val expr = xor(falseExpr, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(F,F,F)")
  }

  @Test
  fun `xor - false, false, error is error`() {
    val expr = xor(falseExpr, falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,F,E)")
  }

  @Test
  fun `xor - false, false, true is true`() {
    val expr = xor(falseExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(F,F,T)")
  }

  @Test
  fun `xor - false, error, false is error`() {
    val expr = xor(falseExpr, errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,E,F)")
  }

  @Test
  fun `xor - false, error, error is error`() {
    val expr = xor(falseExpr, errorExpr as BooleanExpression, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,E,E)")
  }

  @Test
  fun `xor - false, error, true is error`() {
    val expr = xor(falseExpr, errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,E,T)")
  }

  @Test
  fun `xor - false, true, false is true`() {
    val expr = xor(falseExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(F,T,F)")
  }

  @Test
  fun `xor - false, true, error is error`() {
    val expr = xor(falseExpr, trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,T,E)")
  }

  @Test
  fun `xor - false, true, true is false`() {
    val expr = xor(falseExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(F,T,T)")
  }

  @Test
  fun `xor - error, false, false is error`() {
    val expr = xor(errorExpr as BooleanExpression, falseExpr, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,F,F)")
  }

  @Test
  fun `xor - error, false, error is error`() {
    val expr = xor(errorExpr as BooleanExpression, falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,F,E)")
  }

  @Test
  fun `xor - error, false, true is error`() {
    val expr = xor(errorExpr as BooleanExpression, falseExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,F,T)")
  }

  @Test
  fun `xor - error, error, false is error`() {
    val expr = xor(errorExpr as BooleanExpression, errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,E,F)")
  }

  @Test
  fun `xor - error, error, error is error`() {
    val expr =
      xor(
        errorExpr as BooleanExpression,
        errorExpr as BooleanExpression,
        errorExpr as BooleanExpression
      )
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,E,E)")
  }

  @Test
  fun `xor - error, error, true is error`() {
    val expr = xor(errorExpr as BooleanExpression, errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,E,T)")
  }

  @Test
  fun `xor - error, true, false is error`() {
    val expr = xor(errorExpr as BooleanExpression, trueExpr, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,T,F)")
  }

  @Test
  fun `xor - error, true, error is error`() {
    val expr = xor(errorExpr as BooleanExpression, trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,T,E)")
  }

  @Test
  fun `xor - error, true, true is error`() {
    val expr = xor(errorExpr as BooleanExpression, trueExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,T,T)")
  }

  @Test
  fun `xor - true, false, false is true`() {
    val expr = xor(trueExpr, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(T,F,F)")
  }

  @Test
  fun `xor - true, false, error is error`() {
    val expr = xor(trueExpr, falseExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,F,E)")
  }

  @Test
  fun `xor - true, false, true is false`() {
    val expr = xor(trueExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(T,F,T)")
  }

  @Test
  fun `xor - true, error, false is error`() {
    val expr = xor(trueExpr, errorExpr as BooleanExpression, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,E,F)")
  }

  @Test
  fun `xor - true, error, error is error`() {
    val expr = xor(trueExpr, errorExpr as BooleanExpression, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,E,E)")
  }

  @Test
  fun `xor - true, error, true is error`() {
    val expr = xor(trueExpr, errorExpr as BooleanExpression, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,E,T)")
  }

  @Test
  fun `xor - true, true, false is false`() {
    val expr = xor(trueExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(T,T,F)")
  }

  @Test
  fun `xor - true, true, error is error`() {
    val expr = xor(trueExpr, trueExpr, errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,T,E)")
  }

  @Test
  fun `xor - true, true, true is true`() {
    val expr = xor(trueExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(T,T,T)")
  }

  // Nested
  @Test
  fun `xor - nested xor`() {
    val child = xor(trueExpr, falseExpr)
    val expr = xor(child, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "Nested XOR")
  }

  // Multiple Arguments (already covered by 3-operand tests)
  @Test
  fun `xor - multiple arguments`() {
    val expr = xor(trueExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "Multiple args XOR")
  }
}
