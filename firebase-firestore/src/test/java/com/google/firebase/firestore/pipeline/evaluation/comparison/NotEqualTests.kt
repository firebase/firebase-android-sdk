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

package com.google.firebase.firestore.pipeline.evaluation.comparison

import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class NotEqualTests {
  @Test
  fun neq_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(notEqual(v1, v2)), false, "neq(%s, %s)", v1, v2)
    }
  }

  @Test
  fun neq_unequalValues_returnTrue() {
    ComparisonTestData.unequalValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(notEqual(v1, v2)), true, "neq(%s, %s)", v1, v2)
      assertEvaluatesTo(evaluate(notEqual(v2, v1)), true, "neq(%s, %s)", v2, v1)
    }
  }

  @Test
  fun neq_crossTypeValues_returnTrue() {
    ComparisonTestData.crossTypeValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(notEqual(v1, v2)), true, "neq(%s, %s)", v1, v2)
      assertEvaluatesTo(evaluate(notEqual(v2, v1)), true, "neq(%s, %s)", v2, v1)
    }
  }

  @Test
  fun neq_errorHandling_returnsError() {
    val errorExpr = Expression.error("sample error")
    val testDoc = doc("test/neqError", 0, mapOf("a" to 123))
    val nanExpr = ComparisonTestData.doubleNaN

    ComparisonTestData.allValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(notEqual(errorExpr, value), testDoc),
        "neq(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(notEqual(value, errorExpr), testDoc),
        "neq(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(notEqual(errorExpr, errorExpr), testDoc),
      "neq(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(notEqual(errorExpr, nullValue()), testDoc),
      "neq(%s, %s)",
      errorExpr,
      nullValue()
    )
    assertEvaluatesToError(
      evaluate(notEqual(errorExpr, nanExpr), testDoc),
      "neq(%s, %s)",
      errorExpr,
      nanExpr
    )
    assertEvaluatesToError(
      evaluate(notEqual(nanExpr, errorExpr), testDoc),
      "neq(%s, %s)",
      nanExpr,
      errorExpr
    )
  }
}
