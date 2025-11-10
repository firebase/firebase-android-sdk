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
import com.google.firebase.firestore.pipeline.Expression.Companion.not
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotTests {
  private val trueExpr = constant(true)
  private val falseExpr = constant(false)
  private val errorExpr = Expression.error("error.field").equal(constant("random"))
  private val errorDoc =
    doc("coll/docError", 1, mapOf("error" to 123)) // "error.field" will be UNSET
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- Not (!) Tests ---
  @Test
  fun `not - true to false`() {
    val expr = not(trueExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), false, "NOT(true)")
  }

  @Test
  fun `not - false to true`() {
    val expr = not(falseExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "NOT(false)")
  }

  @Test
  fun `not - error is error`() {
    val expr = not(errorExpr as BooleanExpression)
    assertEvaluatesToError(evaluate(expr, errorDoc), "NOT(error)")
  }
}
