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

import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expr.Companion.add
import com.google.firebase.firestore.pipeline.Expr.Companion.and
import com.google.firebase.firestore.pipeline.Expr.Companion.array
import com.google.firebase.firestore.pipeline.Expr.Companion.cond
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.eqAny
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.isNan
import com.google.firebase.firestore.pipeline.Expr.Companion.isNotNan
import com.google.firebase.firestore.pipeline.Expr.Companion.isNotNull
import com.google.firebase.firestore.pipeline.Expr.Companion.isNull
import com.google.firebase.firestore.pipeline.Expr.Companion.logicalMaximum
import com.google.firebase.firestore.pipeline.Expr.Companion.logicalMinimum
import com.google.firebase.firestore.pipeline.Expr.Companion.map
import com.google.firebase.firestore.pipeline.Expr.Companion.not
import com.google.firebase.firestore.pipeline.Expr.Companion.notEqAny
import com.google.firebase.firestore.pipeline.Expr.Companion.nullValue
import com.google.firebase.firestore.pipeline.Expr.Companion.or
import com.google.firebase.firestore.pipeline.Expr.Companion.xor
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogicalTests {

  private val trueExpr = constant(true)
  private val falseExpr = constant(false)
  private val nullExpr = nullValue() // Changed
  private val nanExpr = constant(Double.NaN)
  private val errorExpr = field("error.field").eq(constant("random"))

  // Corrected document creation using doc() from TestUtilKtx
  private val testDocWithNan =
    doc("coll/docNan", 1, mapOf("nanValue" to Double.NaN, "field" to "value"))
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
    val expr = and(falseExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(false, error)")
  }

  @Test
  fun `and - false, true is false`() {
    val expr = and(falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(false, true)")
  }

  @Test
  fun `and - error, false is false`() {
    val expr = and(errorExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(error, false)")
  }

  @Test
  fun `and - error, error is error`() {
    val expr = and(errorExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(error, error)")
  }

  @Test
  fun `and - error, true is error`() {
    val expr = and(errorExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(error, true)")
  }

  @Test
  fun `and - true, false is false`() {
    val expr = and(trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(true, false)")
  }

  @Test
  fun `and - true, error is error`() {
    val expr = and(trueExpr, errorExpr)
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
    val expr = and(falseExpr, falseExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,F,E)")
  }

  @Test
  fun `and - false, false, true is false`() {
    val expr = and(falseExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(F,F,T)")
  }

  @Test
  fun `and - false, error, false is false`() {
    val expr = and(falseExpr, errorExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,E,F)")
  }

  @Test
  fun `and - false, error, error is false`() {
    val expr = and(falseExpr, errorExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,E,E)")
  }

  @Test
  fun `and - false, error, true is false`() {
    val expr = and(falseExpr, errorExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,E,T)")
  }

  @Test
  fun `and - false, true, false is false`() {
    val expr = and(falseExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(F,T,F)")
  }

  @Test
  fun `and - false, true, error is false`() {
    val expr = and(falseExpr, trueExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(F,T,E)")
  }

  @Test
  fun `and - false, true, true is false`() {
    val expr = and(falseExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(F,T,T)")
  }

  @Test
  fun `and - error, false, false is false`() {
    val expr = and(errorExpr, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,F,F)")
  }

  @Test
  fun `and - error, false, error is false`() {
    val expr = and(errorExpr, falseExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,F,E)")
  }

  @Test
  fun `and - error, false, true is false`() {
    val expr = and(errorExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,F,T)")
  }

  @Test
  fun `and - error, error, false is false`() {
    val expr = and(errorExpr, errorExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,E,F)")
  }

  @Test
  fun `and - error, error, error is error`() {
    val expr = and(errorExpr, errorExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(E,E,E)")
  }

  @Test
  fun `and - error, error, true is error`() {
    val expr = and(errorExpr, errorExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(E,E,T)")
  }

  @Test
  fun `and - error, true, false is false`() {
    val expr = and(errorExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(E,T,F)")
  }

  @Test
  fun `and - error, true, error is error`() {
    val expr = and(errorExpr, trueExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(E,T,E)")
  }

  @Test
  fun `and - error, true, true is error`() {
    val expr = and(errorExpr, trueExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(E,T,T)")
  }

  @Test
  fun `and - true, false, false is false`() {
    val expr = and(trueExpr, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(T,F,F)")
  }

  @Test
  fun `and - true, false, error is false`() {
    val expr = and(trueExpr, falseExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(T,F,E)")
  }

  @Test
  fun `and - true, false, true is false`() {
    val expr = and(trueExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(T,F,T)")
  }

  @Test
  fun `and - true, error, false is false`() {
    val expr = and(trueExpr, errorExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), false, "AND(T,E,F)")
  }

  @Test
  fun `and - true, error, error is error`() {
    val expr = and(trueExpr, errorExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(T,E,E)")
  }

  @Test
  fun `and - true, error, true is error`() {
    val expr = and(trueExpr, errorExpr, trueExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "AND(T,E,T)")
  }

  @Test
  fun `and - true, true, false is false`() {
    val expr = and(trueExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "AND(T,T,F)")
  }

  @Test
  fun `and - true, true, error is error`() {
    val expr = and(trueExpr, trueExpr, errorExpr)
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

  // --- Cond (? :) Tests ---
  @Test
  fun `cond - true condition returns true case`() {
    val expr = cond(trueExpr, constant("true case"), errorExpr)
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue("true case"), "cond(true, 'true case', error)")
  }

  @Test
  fun `cond - false condition returns false case`() {
    val expr = cond(falseExpr, errorExpr, constant("false case"))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue("false case"), "cond(false, error, 'false case')")
  }

  @Test
  fun `cond - error condition returns error`() {
    val expr = cond(errorExpr, constant("true case"), constant("false case"))
    assertEvaluatesToError(evaluate(expr, errorDoc), "Cond with error condition")
  }

  @Test
  fun `cond - true condition but true case is error returns error`() {
    val expr = cond(trueExpr, errorExpr, constant("false case"))
    assertEvaluatesToError(evaluate(expr, errorDoc), "Cond with error true-case")
  }

  @Test
  fun `cond - false condition but false case is error returns error`() {
    val expr = cond(falseExpr, constant("true case"), errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "Cond with error false-case")
  }

  // --- Or (||) Tests ---
  // 2 Operands
  @Test
  fun `or - false, false is false`() {
    val expr = or(falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "OR(F,F)")
  }

  @Test
  fun `or - false, error is error`() {
    val expr = or(falseExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(F,E)")
  }

  @Test
  fun `or - false, true is true`() {
    val expr = or(falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,T)")
  }

  @Test
  fun `or - error, false is error`() {
    val expr = or(errorExpr, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,F)")
  }

  @Test
  fun `or - error, error is error`() {
    val expr = or(errorExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,E)")
  }

  @Test
  fun `or - error, true is true`() {
    val expr = or(errorExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(E,T)")
  }

  @Test
  fun `or - true, false is true`() {
    val expr = or(trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,F)")
  }

  @Test
  fun `or - true, error is true`() {
    val expr = or(trueExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,E)")
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
    val expr = or(falseExpr, falseExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(F,F,E)")
  }

  @Test
  fun `or - false, false, true is true`() {
    val expr = or(falseExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,F,T)")
  }

  @Test
  fun `or - false, error, false is error`() {
    val expr = or(falseExpr, errorExpr, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(F,E,F)")
  }

  @Test
  fun `or - false, error, error is error`() {
    val expr = or(falseExpr, errorExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(F,E,E)")
  }

  @Test
  fun `or - false, error, true is true`() {
    val expr = or(falseExpr, errorExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(F,E,T)")
  }

  @Test
  fun `or - false, true, false is true`() {
    val expr = or(falseExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,T,F)")
  }

  @Test
  fun `or - false, true, error is true`() {
    val expr = or(falseExpr, trueExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(F,T,E)")
  }

  @Test
  fun `or - false, true, true is true`() {
    val expr = or(falseExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(F,T,T)")
  }

  @Test
  fun `or - error, false, false is error`() {
    val expr = or(errorExpr, falseExpr, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,F,F)")
  }

  @Test
  fun `or - error, false, error is error`() {
    val expr = or(errorExpr, falseExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,F,E)")
  }

  @Test
  fun `or - error, false, true is true`() {
    val expr = or(errorExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(E,F,T)")
  }

  @Test
  fun `or - error, error, false is error`() {
    val expr = or(errorExpr, errorExpr, falseExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,E,F)")
  }

  @Test
  fun `or - error, error, error is error`() {
    val expr = or(errorExpr, errorExpr, errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "OR(E,E,E)")
  }

  @Test
  fun `or - error, error, true is true`() {
    val expr = or(errorExpr, errorExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(E,E,T)")
  }

  @Test
  fun `or - error, true, false is true`() {
    val expr = or(errorExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(E,T,F)")
  }

  @Test
  fun `or - error, true, error is true`() {
    val expr = or(errorExpr, trueExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(E,T,E)")
  }

  @Test
  fun `or - error, true, true is true`() {
    val expr = or(errorExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(E,T,T)")
  }

  @Test
  fun `or - true, false, false is true`() {
    val expr = or(trueExpr, falseExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,F,F)")
  }

  @Test
  fun `or - true, false, error is true`() {
    val expr = or(trueExpr, falseExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,F,E)")
  }

  @Test
  fun `or - true, false, true is true`() {
    val expr = or(trueExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,F,T)")
  }

  @Test
  fun `or - true, error, false is true`() {
    val expr = or(trueExpr, errorExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,E,F)")
  }

  @Test
  fun `or - true, error, error is true`() {
    val expr = or(trueExpr, errorExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,E,E)")
  }

  @Test
  fun `or - true, error, true is true`() {
    val expr = or(trueExpr, errorExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,E,T)")
  }

  @Test
  fun `or - true, true, false is true`() {
    val expr = or(trueExpr, trueExpr, falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,T,F)")
  }

  @Test
  fun `or - true, true, error is true`() {
    val expr = or(trueExpr, trueExpr, errorExpr)
    assertEvaluatesTo(evaluate(expr, errorDoc), true, "OR(T,T,E)")
  }

  @Test
  fun `or - true, true, true is true`() {
    val expr = or(trueExpr, trueExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "OR(T,T,T)")
  }

  // Nested
  @Test
  fun `or - nested or`() {
    val child = or(trueExpr, falseExpr) // true
    val expr = or(child, falseExpr) // true OR false -> true
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "Nested OR")
  }

  // Multiple Arguments (already covered by 3-operand tests)
  @Test
  fun `or - multiple arguments`() {
    val expr = or(trueExpr, falseExpr, trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "Multiple args OR")
  }

  // --- Not (!) Tests ---
  @Test
  fun `not - true to false`() {
    val expr = not(trueExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "NOT(true)")
  }

  @Test
  fun `not - false to true`() {
    val expr = not(falseExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "NOT(false)")
  }

  @Test
  fun `not - error is error`() {
    val expr = not(errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "NOT(error)")
  }

  // --- Xor Tests ---
  // 2 Operands
  @Test
  fun `xor - false, false is false`() {
    val expr = xor(falseExpr, falseExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(F,F)")
  }

  @Test
  fun `xor - false, error is error`() {
    val expr = xor(falseExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,E)")
  }

  @Test
  fun `xor - false, true is true`() {
    val expr = xor(falseExpr, trueExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(F,T)")
  }

  @Test
  fun `xor - error, false is error`() {
    val expr = xor(errorExpr as BooleanExpr, falseExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,F)")
  }

  @Test
  fun `xor - error, error is error`() {
    val expr = xor(errorExpr as BooleanExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,E)")
  }

  @Test
  fun `xor - error, true is error`() {
    val expr = xor(errorExpr as BooleanExpr, trueExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,T)")
  }

  @Test
  fun `xor - true, false is true`() {
    val expr = xor(trueExpr, falseExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(T,F)")
  }

  @Test
  fun `xor - true, error is error`() {
    val expr = xor(trueExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,E)")
  }

  @Test
  fun `xor - true, true is false`() {
    val expr = xor(trueExpr, trueExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(T,T)")
  }

  // 3 Operands (XOR is true if an odd number of inputs are true)
  @Test
  fun `xor - false, false, false is false`() {
    val expr = xor(falseExpr, falseExpr, falseExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(F,F,F)")
  }

  @Test
  fun `xor - false, false, error is error`() {
    val expr = xor(falseExpr, falseExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,F,E)")
  }

  @Test
  fun `xor - false, false, true is true`() {
    val expr = xor(falseExpr, falseExpr, trueExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(F,F,T)")
  }

  @Test
  fun `xor - false, error, false is error`() {
    val expr = xor(falseExpr, errorExpr as BooleanExpr, falseExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,E,F)")
  }

  @Test
  fun `xor - false, error, error is error`() {
    val expr = xor(falseExpr, errorExpr as BooleanExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,E,E)")
  }

  @Test
  fun `xor - false, error, true is error`() {
    val expr = xor(falseExpr, errorExpr as BooleanExpr, trueExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,E,T)")
  }

  @Test
  fun `xor - false, true, false is true`() {
    val expr = xor(falseExpr, trueExpr, falseExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(F,T,F)")
  }

  @Test
  fun `xor - false, true, error is error`() {
    val expr = xor(falseExpr, trueExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(F,T,E)")
  }

  @Test
  fun `xor - false, true, true is false`() {
    val expr = xor(falseExpr, trueExpr, trueExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(F,T,T)")
  }

  @Test
  fun `xor - error, false, false is error`() {
    val expr = xor(errorExpr as BooleanExpr, falseExpr, falseExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,F,F)")
  }

  @Test
  fun `xor - error, false, error is error`() {
    val expr = xor(errorExpr as BooleanExpr, falseExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,F,E)")
  }

  @Test
  fun `xor - error, false, true is error`() {
    val expr = xor(errorExpr as BooleanExpr, falseExpr, trueExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,F,T)")
  }

  @Test
  fun `xor - error, error, false is error`() {
    val expr = xor(errorExpr as BooleanExpr, errorExpr as BooleanExpr, falseExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,E,F)")
  }

  @Test
  fun `xor - error, error, error is error`() {
    val expr =
      xor(errorExpr as BooleanExpr, errorExpr as BooleanExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,E,E)")
  }

  @Test
  fun `xor - error, error, true is error`() {
    val expr = xor(errorExpr as BooleanExpr, errorExpr as BooleanExpr, trueExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,E,T)")
  }

  @Test
  fun `xor - error, true, false is error`() {
    val expr = xor(errorExpr as BooleanExpr, trueExpr, falseExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,T,F)")
  }

  @Test
  fun `xor - error, true, error is error`() {
    val expr = xor(errorExpr as BooleanExpr, trueExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,T,E)")
  }

  @Test
  fun `xor - error, true, true is error`() {
    val expr = xor(errorExpr as BooleanExpr, trueExpr, trueExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(E,T,T)")
  }

  @Test
  fun `xor - true, false, false is true`() {
    val expr = xor(trueExpr, falseExpr, falseExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(T,F,F)")
  }

  @Test
  fun `xor - true, false, error is error`() {
    val expr = xor(trueExpr, falseExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,F,E)")
  }

  @Test
  fun `xor - true, false, true is false`() {
    val expr = xor(trueExpr, falseExpr, trueExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(T,F,T)")
  }

  @Test
  fun `xor - true, error, false is error`() {
    val expr = xor(trueExpr, errorExpr as BooleanExpr, falseExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,E,F)")
  }

  @Test
  fun `xor - true, error, error is error`() {
    val expr = xor(trueExpr, errorExpr as BooleanExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,E,E)")
  }

  @Test
  fun `xor - true, error, true is error`() {
    val expr = xor(trueExpr, errorExpr as BooleanExpr, trueExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,E,T)")
  }

  @Test
  fun `xor - true, true, false is false`() {
    val expr = xor(trueExpr, trueExpr, falseExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "XOR(T,T,F)")
  }

  @Test
  fun `xor - true, true, error is error`() {
    val expr = xor(trueExpr, trueExpr, errorExpr as BooleanExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "XOR(T,T,E)")
  }

  @Test
  fun `xor - true, true, true is true`() {
    val expr = xor(trueExpr, trueExpr, trueExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "XOR(T,T,T)")
  }

  // Nested
  @Test
  fun `xor - nested xor`() {
    val child = xor(trueExpr, falseExpr) // Changed
    val expr = xor(child, trueExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "Nested XOR")
  }

  // Multiple Arguments (already covered by 3-operand tests)
  @Test
  fun `xor - multiple arguments`() {
    val expr = xor(trueExpr, falseExpr, trueExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "Multiple args XOR")
  }

  // --- IsNull Tests ---
  @Test
  fun `isNull - null returns true`() {
    val expr = isNull(nullExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "isNull(null)")
  }

  @Test
  fun `isNull - error returns error`() {
    val expr = isNull(errorExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "isNull(error)")
  }

  @Test
  fun `isNull - unset field returns error`() {
    val expr = isNull(field("non-existent-field")) // Changed
    assertEvaluatesToError(evaluate(expr, emptyDoc), "isNull(unset)")
  }

  @Test
  fun `isNull - anything but null returns false`() {
    val values =
      listOf(
        constant(true),
        constant(false),
        constant(0),
        constant(1.0),
        constant("abc"),
        constant(Double.NaN),
        array(constant(1)),
        map(mapOf("a" to 1))
      )
    for (valueExpr in values) {
      val expr = isNull(valueExpr) // Changed
      assertEvaluatesTo(evaluate(expr, emptyDoc), false, "isNull(${valueExpr})")
    }
  }

  // --- IsNotNull Tests ---
  @Test
  fun `isNotNull - null returns false`() {
    val expr = isNotNull(nullExpr) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "isNotNull(null)")
  }

  @Test
  fun `isNotNull - error returns error`() {
    val expr = isNotNull(errorExpr) // Changed
    assertEvaluatesToError(evaluate(expr, errorDoc), "isNotNull(error)")
  }

  @Test
  fun `isNotNull - unset field returns error`() {
    val expr = isNotNull(field("non-existent-field")) // Changed
    assertEvaluatesToError(evaluate(expr, emptyDoc), "isNotNull(unset)")
  }

  @Test
  fun `isNotNull - anything but null returns true`() {
    val values =
      listOf(
        constant(true),
        constant(false),
        constant(0),
        constant(1.0),
        constant("abc"),
        constant(Double.NaN),
        array(constant(1)),
        map(mapOf("a" to 1))
      )
    for (valueExpr in values) {
      val expr = isNotNull(valueExpr) // Changed
      assertEvaluatesTo(evaluate(expr, emptyDoc), true, "isNotNull(${valueExpr})")
    }
  }

  // --- IsNan / IsNotNan Tests ---
  @Test
  fun `isNan - nan returns true`() {
    assertEvaluatesTo(evaluate(isNan(nanExpr), emptyDoc), true, "isNan(NaN)") // Changed
    assertEvaluatesTo(
      evaluate(isNan(field("nanValue")), testDocWithNan),
      true,
      "isNan(field(nanValue))"
    ) // Changed
  }

  @Test
  fun `isNan - not nan returns false`() {
    assertEvaluatesTo(evaluate(isNan(constant(42.0)), emptyDoc), false, "isNan(42.0)") // Changed
    assertEvaluatesTo(evaluate(isNan(constant(42L)), emptyDoc), false, "isNan(42L)") // Changed
  }

  @Test
  fun `isNotNan - not nan returns true`() {
    assertEvaluatesTo(
      evaluate(isNotNan(constant(42.0)), emptyDoc),
      true,
      "isNotNan(42.0)"
    ) // Changed
    assertEvaluatesTo(evaluate(isNotNan(constant(42L)), emptyDoc), true, "isNotNan(42L)") // Changed
  }

  @Test
  fun `isNotNan - nan returns false`() {
    assertEvaluatesTo(evaluate(isNotNan(nanExpr), emptyDoc), false, "isNotNan(NaN)") // Changed
    assertEvaluatesTo(
      evaluate(isNotNan(field("nanValue")), testDocWithNan),
      false,
      "isNotNan(field(nanValue))"
    ) // Changed
  }

  @Test
  fun `isNan - other nan representations returns true`() {
    val nanPlusOne = add(nanExpr, constant(1L)) // Changed
    assertEvaluatesTo(evaluate(isNan(nanPlusOne), emptyDoc), true, "isNan(NaN + 1)") // Changed
  }

  @Test
  fun `isNan - non numeric returns error`() {
    assertEvaluatesToError(
      evaluate(isNan(constant(true)), emptyDoc),
      "isNan(true) should be error"
    ) // Changed
    assertEvaluatesToError(
      evaluate(isNan(constant("abc")), emptyDoc),
      "isNan(abc) should be error"
    ) // Changed
    assertEvaluatesToError(
      evaluate(isNan(array()), emptyDoc),
      "isNan([]) should be error"
    ) // Changed
    assertEvaluatesToError(
      evaluate(isNan(map(emptyMap())), emptyDoc),
      "isNan({}) should be error"
    ) // Changed
  }

  @Test
  fun `isNan - null returns null`() {
    assertEvaluatesToNull(
      evaluate(isNan(nullExpr), emptyDoc),
      "isNan(null) should be null"
    ) // Changed
  }

  // --- EqAny Tests ---
  @Test
  fun `eqAny - value found in array`() {
    val expr = eqAny(constant("hello"), array(constant("hello"), constant("world")))
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "eqAny(hello, [hello, world])")
  }

  @Test
  fun `eqAny - value not found in array`() {
    val expr = eqAny(constant(4L), array(constant(42L), constant("matang"), constant(true)))
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "eqAny(4, [42, matang, true])")
  }

  @Test
  fun `notEqAny - value not found in array`() {
    val expr =
      notEqAny(constant(4L), array(constant(42L), constant("matang"), constant(true))) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "notEqAny(4, [42, matang, true])")
  }

  @Test
  fun `notEqAny - value found in array`() {
    val expr = notEqAny(constant("hello"), array(constant("hello"), constant("world"))) // Changed
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "notEqAny(hello, [hello, world])")
  }

  @Test
  fun `eqAny - equivalent numerics`() {
    assertEvaluatesTo(
      evaluate(
        eqAny(constant(42L), array(constant(42.0), constant("matang"), constant(true))),
        emptyDoc
      ),
      true,
      "eqAny(42L, [42.0,...])"
    )
    assertEvaluatesTo(
      evaluate(
        eqAny(constant(42.0), array(constant(42L), constant("matang"), constant(true))),
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
      evaluate(eqAny(searchArray, valuesArray), emptyDoc),
      true,
      "eqAny([1,2,3], [[1,2,3],...])"
    )
  }

  @Test
  fun `eqAny - array not found returns error`() {
    val expr = eqAny(constant("matang"), field("non-existent-field"))
    assertEvaluatesToError(evaluate(expr, emptyDoc), "eqAny(matang, non-existent-field)")
  }

  @Test
  fun `eqAny - array is empty returns false`() {
    val expr = eqAny(constant(42L), array())
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "eqAny(42L, [])")
  }

  @Test
  fun `eqAny - search reference not found returns error`() {
    val expr = eqAny(field("non-existent-field"), array(constant(42L)))
    assertEvaluatesToError(evaluate(expr, emptyDoc), "eqAny(non-existent-field, [42L])")
  }

  @Test
  fun `eqAny - search is null`() {
    val expr = eqAny(nullExpr, array(nullExpr, constant(1L), constant("matang")))
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "eqAny(null, [null,1,matang])")
  }

  @Test
  fun `eqAny - search is null empty values array returns null`() {
    val expr = eqAny(nullExpr, array())
    assertEvaluatesToNull(evaluate(expr, emptyDoc), "eqAny(null, [])")
  }

  @Test
  fun `eqAny - search is nan`() {
    val expr = eqAny(nanExpr, array(nanExpr, constant(42L), constant(3.14)))
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "eqAny(NaN, [NaN,42,3.14])")
  }

  @Test
  fun `eqAny - search is empty array is empty`() {
    val expr = eqAny(array(), array())
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "eqAny([], [])")
  }

  @Test
  fun `eqAny - search is empty array contains empty array returns true`() {
    val expr = eqAny(array(), array(array()))
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
      evaluate(eqAny(searchMap, valuesArray), emptyDoc),
      true,
      "eqAny(map, [...,map])"
    )
  }

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
