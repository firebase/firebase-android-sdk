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
import com.google.firebase.firestore.pipeline.Expression.Companion.equal
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class EqualTests {

  @Test
  fun eq_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      val result = evaluate(equal(v1, v2))
      assertEvaluatesTo(result, true, "eq(%s, %s)", v1, v2)
    }
  }

  @Test
  fun eq_unequalValues_returnFalse() {
    ComparisonTestData.unequalValues.forEach { (v1, v2) ->
      // eq(v1, v2)
      val result1 = evaluate(equal(v1, v2))
      assertEvaluatesTo(result1, false, "eq(%s, %s)", v1, v2)
      // eq(v2, v1)
      val result2 = evaluate(equal(v2, v1))
      assertEvaluatesTo(result2, false, "eq(%s, %s)", v2, v1)
    }
  }

  @Test
  fun eq_crossTypeValues_returnFalse() {
    ComparisonTestData.crossTypeValues.forEach { (v1, v2) ->
      val result1 = evaluate(equal(v1, v2))
      assertEvaluatesTo(result1, false, "eq(%s, %s)", v1, v2)
      val result2 = evaluate(equal(v2, v1))
      assertEvaluatesTo(result2, false, "eq(%s, %s)", v2, v1)
    }
  }

  @Test
  fun eq_errorHandling_returnsError() {
    val errorExpr = Expression.error("test error")
    val testDoc = doc("test/eqError", 0, mapOf("a" to 123))

    ComparisonTestData.allValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(equal(errorExpr, value), testDoc),
        "eq(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(equal(value, errorExpr), testDoc),
        "eq(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(equal(errorExpr, errorExpr), testDoc),
      "eq(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(equal(errorExpr, nullValue()), testDoc),
      "eq(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun eq_missingField_returnsFalse() {
    val missingField = field("nonexistent")
    val presentValue = ComparisonTestData.allValues.first()
    val testDoc = doc("test/eqMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesTo(
      evaluate(equal(missingField, presentValue), testDoc),
      false,
      "eq(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesTo(
      evaluate(equal(presentValue, missingField), testDoc),
      false,
      "eq(%s, %s)",
      presentValue,
      missingField
    )
  }
}
