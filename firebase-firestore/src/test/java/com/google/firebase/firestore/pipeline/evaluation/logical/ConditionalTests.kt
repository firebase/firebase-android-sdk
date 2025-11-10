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
import com.google.firebase.firestore.pipeline.Expression.Companion.conditional
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConditionalTests {
  private val trueExpr = constant(true)
  private val falseExpr = constant(false)
  private val errorExpr = Expression.error("error").equal(constant("random"))

  private val errorDoc =
    doc("coll/docError", 1, mapOf("error" to 123)) // "error.field" will be UNSET
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- Cond (? :) Tests ---
  @Test
  fun `cond - true condition returns true case`() {
    val expr = conditional(trueExpr, constant("true case"), errorExpr)
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue("true case"), "cond(true, 'true case', error)")
  }

  @Test
  fun `cond - false condition returns false case`() {
    val expr = conditional(falseExpr, errorExpr, constant("false case"))
    val result = evaluate(expr, emptyDoc)
    assertEvaluatesTo(result, encodeValue("false case"), "cond(false, error, 'false case')")
  }

  @Test
  fun `cond - error condition returns error`() {
    val expr = conditional(errorExpr, constant("true case"), constant("false case"))
    assertEvaluatesToError(evaluate(expr, errorDoc), "Cond with error condition")
  }

  @Test
  fun `cond - true condition but true case is error returns error`() {
    val expr = conditional(trueExpr, errorExpr, constant("false case"))
    assertEvaluatesToError(evaluate(expr, errorDoc), "Cond with error true-case")
  }

  @Test
  fun `cond - false condition but false case is error returns error`() {
    val expr = conditional(falseExpr, constant("true case"), errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "Cond with error false-case")
  }
}
