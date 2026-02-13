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

import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.and
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndTests {

  private val trueExpr = constant(true)
  private val falseExpr = constant(false)
  private val nullExpr = nullBoolean()

  private val unsetExpr = unsetBoolean()
  private val errorExpr = Expression.error("test").equal(constant("random"))
  private val stringExpr = stringBoolean()

  private val errorDoc =
    doc("coll/docError", 1, mapOf("error" to 123)) // "error.field" will be UNSET
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // Test setup follows: https://en.wikipedia.org/wiki/Three-valued_logic#Kleene_and_Priest_logics
  //     F | U | T
  // F | F | F | F
  // U | F | U | U
  // T | F | U | T
  // In our case, U (Unknown) can be NULL or UNSET.

  @Test
  fun `false_false_isFalse`() {
    assertThat(and(falseExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_error_isFalse`() {
    assertThat(and(falseExpr, errorExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_null_isFalse`() {
    assertThat(and(falseExpr, nullExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_true_isFalse`() {
    assertThat(and(falseExpr, trueExpr)).evaluatesTo(false)
  }

  @Test
  fun `error_false_isError`() {
    assertThat(and(errorExpr, falseExpr)).evaluatesToError()
  }

  @Test
  fun `null_false_isFalse`() {
    assertThat(and(nullExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `error_error_isError`() {
    assertThat(and(errorExpr, errorExpr)).evaluatesToError()
  }

  @Test
  fun `null_null_isNull`() {
    assertThat(and(nullExpr, nullExpr)).evaluatesToNull()
  }

  @Test
  fun `error_true_isError`() {
    assertThat(and(errorExpr, trueExpr)).evaluatesToError()
  }

  @Test
  fun `null_true_isNull`() {
    assertThat(and(nullExpr, trueExpr)).evaluatesToNull()
  }

  @Test
  fun `true_false_isFalse`() {
    assertThat(and(trueExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `true_error_isError`() {
    assertThat(and(trueExpr, errorExpr)).evaluatesToError()
  }

  @Test
  fun `true_null_isNull`() {
    assertThat(and(trueExpr, nullExpr)).evaluatesToNull()
  }

  @Test
  fun `true_true_isTrue`() {
    assertThat(and(trueExpr, trueExpr)).evaluatesTo(true)
  }

  @Test
  fun `false_false_false_isFalse`() {
    assertThat(and(falseExpr, falseExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_false_error_isFalse`() {
    assertThat(and(falseExpr, falseExpr, errorExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_false_null_isFalse`() {
    assertThat(and(falseExpr, falseExpr, nullExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_false_true_isFalse`() {
    assertThat(and(falseExpr, falseExpr, trueExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_error_false_isFalse`() {
    assertThat(and(falseExpr, errorExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_null_false_isFalse`() {
    assertThat(and(falseExpr, nullExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_error_error_isFalse`() {
    assertThat(and(falseExpr, errorExpr, errorExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_null_null_isFalse`() {
    assertThat(and(falseExpr, nullExpr, nullExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_error_true_isFalse`() {
    assertThat(and(falseExpr, errorExpr, trueExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_null_true_isFalse`() {
    assertThat(and(falseExpr, nullExpr, trueExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_true_false_isFalse`() {
    assertThat(and(falseExpr, trueExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_true_error_isFalse`() {
    assertThat(and(falseExpr, trueExpr, errorExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_true_null_isFalse`() {
    assertThat(and(falseExpr, trueExpr, nullExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_true_true_isFalse`() {
    assertThat(and(falseExpr, trueExpr, trueExpr)).evaluatesTo(false)
  }

  @Test
  fun `error_false_false_isError`() {
    assertThat(and(errorExpr, falseExpr, falseExpr)).evaluatesToError()
  }

  @Test
  fun `null_false_false_isFalse`() {
    assertThat(and(nullExpr, falseExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `error_false_error_isError`() {
    assertThat(and(errorExpr, falseExpr, errorExpr)).evaluatesToError()
  }

  @Test
  fun `null_false_null_isFalse`() {
    assertThat(and(nullExpr, falseExpr, nullExpr)).evaluatesTo(false)
  }

  @Test
  fun `error_false_true_isError`() {
    assertThat(and(errorExpr, falseExpr, trueExpr)).evaluatesToError()
  }

  @Test
  fun `null_false_true_isFalse`() {
    assertThat(and(nullExpr, falseExpr, trueExpr)).evaluatesTo(false)
  }

  @Test
  fun `error_error_false_isError`() {
    assertThat(and(errorExpr, errorExpr, falseExpr)).evaluatesToError()
  }

  @Test
  fun `null_null_false_isFalse`() {
    assertThat(and(nullExpr, nullExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `error_error_error_isError`() {
    assertThat(and(errorExpr, errorExpr, errorExpr)).evaluatesToError()
  }

  @Test
  fun `null_null_null_isNull`() {
    assertThat(and(nullExpr, nullExpr, nullExpr)).evaluatesToNull()
  }

  @Test
  fun `error_error_true_isError`() {
    assertThat(and(errorExpr, errorExpr, trueExpr)).evaluatesToError()
  }

  @Test
  fun `null_null_true_isNull`() {
    assertThat(and(nullExpr, nullExpr, trueExpr)).evaluatesToNull()
  }

  @Test
  fun `error_true_false_isError`() {
    assertThat(and(errorExpr, trueExpr, falseExpr)).evaluatesToError()
  }

  @Test
  fun `null_true_false_isFalse`() {
    assertThat(and(nullExpr, trueExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `error_true_error_isError`() {
    assertThat(and(errorExpr, trueExpr, errorExpr)).evaluatesToError()
  }

  @Test
  fun `null_true_null_isNull`() {
    assertThat(and(nullExpr, trueExpr, nullExpr)).evaluatesToNull()
  }

  @Test
  fun `error_true_true_isError`() {
    assertThat(and(errorExpr, trueExpr, trueExpr)).evaluatesToError()
  }

  @Test
  fun `null_true_true_isNull`() {
    assertThat(and(nullExpr, trueExpr, trueExpr)).evaluatesToNull()
  }

  @Test
  fun `true_false_false_isFalse`() {
    assertThat(and(trueExpr, falseExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `true_false_error_isFalse`() {
    assertThat(and(trueExpr, falseExpr, errorExpr)).evaluatesTo(false)
  }

  @Test
  fun `true_false_null_isFalse`() {
    assertThat(and(trueExpr, falseExpr, nullExpr)).evaluatesTo(false)
  }

  @Test
  fun `true_false_true_isFalse`() {
    assertThat(and(trueExpr, falseExpr, trueExpr)).evaluatesTo(false)
  }

  @Test
  fun `true_error_false_isError`() {
    assertThat(and(trueExpr, errorExpr, falseExpr)).evaluatesToError()
  }

  @Test
  fun `true_null_false_isFalse`() {
    assertThat(and(trueExpr, nullExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `true_error_error_isError`() {
    assertThat(and(trueExpr, errorExpr, errorExpr)).evaluatesToError()
  }

  @Test
  fun `true_null_null_isNull`() {
    assertThat(and(trueExpr, nullExpr, nullExpr)).evaluatesToNull()
  }

  @Test
  fun `true_error_true_isError`() {
    assertThat(and(trueExpr, errorExpr, trueExpr)).evaluatesToError()
  }

  @Test
  fun `true_null_true_isNull`() {
    assertThat(and(trueExpr, nullExpr, trueExpr)).evaluatesToNull()
  }

  @Test
  fun `true_true_false_isFalse`() {
    assertThat(and(trueExpr, trueExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `true_true_error_isError`() {
    assertThat(and(trueExpr, trueExpr, errorExpr)).evaluatesToError()
  }

  @Test
  fun `true_true_null_isNull`() {
    assertThat(and(trueExpr, trueExpr, nullExpr)).evaluatesToNull()
  }

  @Test
  fun `true_true_true_isTrue`() {
    assertThat(and(trueExpr, trueExpr, trueExpr)).evaluatesTo(true)
  }

  @Test
  fun `string_string_isError`() {
    assertThat(and(stringExpr, stringExpr)).evaluatesToError()
  }

  @Test
  fun `string_unset_isError`() {
    assertThat(and(stringExpr, unsetExpr)).evaluatesToError()
  }

  @Test
  fun `unset_string_isError`() {
    assertThat(and(unsetExpr, stringExpr)).evaluatesToError()
  }

  @Test
  fun `string_error_isError`() {
    assertThat(and(stringExpr, errorExpr)).evaluatesToError()
  }

  @Test
  fun `error_string_isError`() {
    assertThat(and(errorExpr, stringExpr)).evaluatesToError()
  }

  @Test
  fun `string_null_isError`() {
    assertThat(and(stringExpr, nullExpr)).evaluatesToError()
  }

  @Test
  fun `null_string_isError`() {
    assertThat(and(nullExpr, stringExpr)).evaluatesToError()
  }

  @Test
  fun `string_true_isError`() {
    assertThat(and(stringExpr, trueExpr)).evaluatesToError()
  }

  @Test
  fun `true_string_isError`() {
    assertThat(and(trueExpr, stringExpr)).evaluatesToError()
  }

  @Test
  fun `string_false_isError`() {
    assertThat(and(stringExpr, falseExpr)).evaluatesToError()
  }

  @Test
  fun `false_string_isFalse`() {
    assertThat(and(falseExpr, stringExpr)).evaluatesTo(false)
  }

  @Test
  fun `unset_unset_isUnset`() {
    assertThat(and(unsetExpr, unsetExpr)).evaluatesToNull()
  }

  @Test
  fun `unset_error_isError`() {
    assertThat(and(unsetExpr, errorExpr)).evaluatesToError()
  }

  @Test
  fun `error_unset_isError`() {
    assertThat(and(errorExpr, unsetExpr)).evaluatesToError()
  }

  @Test
  fun `unset_null_isNull`() {
    assertThat(and(unsetExpr, nullExpr)).evaluatesToNull()
  }

  @Test
  fun `null_unset_isNull`() {
    assertThat(and(nullExpr, unsetExpr)).evaluatesToNull()
  }

  @Test
  fun `unset_true_isNull`() {
    assertThat(and(unsetExpr, trueExpr)).evaluatesToNull()
  }

  @Test
  fun `true_unset_isNull`() {
    assertThat(and(trueExpr, unsetExpr)).evaluatesToNull()
  }

  @Test
  fun `unset_false_isFalse`() {
    assertThat(and(unsetExpr, falseExpr)).evaluatesTo(false)
  }

  @Test
  fun `false_unset_isFalse`() {
    assertThat(and(falseExpr, unsetExpr)).evaluatesTo(false)
  }

  @Test
  fun `nested_and`() {
    val child = and(trueExpr, falseExpr)
    val f = and(child, trueExpr)
    assertThat(f).evaluatesTo(false)
  }

  @Test
  fun `multipleArguments`() {
    assertThat(and(trueExpr, trueExpr, trueExpr)).evaluatesTo(true)
  }

  @Test
  fun `error_null_isError`() {
    assertThat(and(errorExpr, nullExpr)).evaluatesToError()
  }

  @Test
  fun `error_null_false_isError`() {
    assertThat(and(errorExpr, nullExpr, falseExpr)).evaluatesToError()
  }

  private fun assertThat(expr: Expression) = ExpressionAsserter(expr)

  private inner class ExpressionAsserter(private val expr: Expression) {
    fun evaluatesTo(expected: Boolean) {
      assertEvaluatesTo(evaluate(expr, emptyDoc), expected, "$expr != $expected")
    }

    fun evaluatesToError() {
      assertEvaluatesToError(evaluate(expr, errorDoc), "$expr != ERROR")
    }

    fun evaluatesToNull() {
      assertEvaluatesToNull(evaluate(expr, emptyDoc), "$expr != NULL")
    }
  }
}
