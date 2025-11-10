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
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThan
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class LessThanTests {
  @Test
  fun lessThan_equalCases_returnsFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(lessThan(v1, v2)), false, "lt(%s, %s)", v1, v2)
    }
  }

  @Test
  fun lessThan_unequalValues_onLesser_returnsTrue() {
    ComparisonTestData.unequalValues.forEach { (v1, v2) ->
      val result = evaluate(lessThan(v1, v2))
      assertEvaluatesTo(result, true, "lt(%s, %s)", v1, v2)
    }
  }

  @Test
  fun lessThan_unequalValues_onGreater_returnsFalse() {
    ComparisonTestData.unequalValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(lessThan(greater, less)), false, "lt(%s, %s)", greater, less)
    }
  }

  @Test
  fun lessThan_crossTypeComparison_returnsFalse() {
    ComparisonTestData.crossTypeValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(lessThan(v1, v2)), false, "lt(%s, %s)", v1, v2)
      assertEvaluatesTo(evaluate(lessThan(v2, v1)), false, "lt(%s, %s)", v2, v1)
    }
  }

  @Test
  fun lt_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(lessThan(nanExpr, nanExpr)), false, "lt(%s, %s)", nanExpr, nanExpr)

    ComparisonTestData.allValues.forEach { value ->
      if (value != nanExpr) {
        assertEvaluatesTo(evaluate(lessThan(nanExpr, value)), false, "lt(%s, %s)", nanExpr, value)
        assertEvaluatesTo(evaluate(lessThan(value, nanExpr)), false, "lt(%s, %s)", value, nanExpr)
      }
    }

    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(lessThan(arrayWithNaN1, arrayWithNaN2)),
      false,
      "lt(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )
  }

  @Test
  fun lt_errorHandling_returnsError() {
    val errorExpr = Expression.error("test")
    val testDoc = doc("test/ltError", 0, mapOf("a" to 123))

    ComparisonTestData.allValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(lessThan(errorExpr, value), testDoc),
        "lt(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(lessThan(value, errorExpr), testDoc),
        "lt(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(lessThan(errorExpr, errorExpr), testDoc),
      "lt(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(lessThan(errorExpr, nullValue()), testDoc),
      "lt(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun lt_missingField_returnsFalse() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/ltMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesTo(
      evaluate(lessThan(missingField, presentValue), testDoc),
      false,
      "lt(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesTo(
      evaluate(lessThan(presentValue, missingField), testDoc),
      false,
      "lt(%s, %s)",
      presentValue,
      missingField
    )
  }
}
