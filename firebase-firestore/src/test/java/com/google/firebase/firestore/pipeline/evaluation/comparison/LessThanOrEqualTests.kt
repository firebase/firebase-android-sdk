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

import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThanOrEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class LessThanOrEqualTests {
  @Test
  fun lte_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lessThanOrEqual(v1, v2)), "lte(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(evaluate(lessThanOrEqual(v1, v2)), true, "lte(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun lte_lessThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(lessThanOrEqual(v1, v2)), true, "lte(%s, %s)", v1, v2)
    }
  }

  @Test
  fun lte_greaterThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(greater, less)),
        false,
        "lte(%s, %s)",
        greater,
        less
      )
    }
  }

  @Test
  fun lte_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lessThanOrEqual(v1, v2)), "lte(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(lessThanOrEqual(v2, v1)), "lte(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(lessThanOrEqual(v1, v2)), false, "lte(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(lessThanOrEqual(v2, v1)), false, "lte(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun lte_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(
        evaluate(lessThanOrEqual(nullVal, value)),
        "lte(%s, %s)",
        nullVal,
        value
      )
      assertEvaluatesToNull(
        evaluate(lessThanOrEqual(value, nullVal)),
        "lte(%s, %s)",
        value,
        nullVal
      )
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(
      evaluate(lessThanOrEqual(nullVal, nullVal)),
      "lte(%s, %s)",
      nullVal,
      nullVal
    )
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(nullVal, missingField)),
      "lte(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun lte_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(
      evaluate(lessThanOrEqual(nanExpr, nanExpr)),
      false,
      "lte(%s, %s)",
      nanExpr,
      nanExpr
    )

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(nanExpr, numVal)),
        false,
        "lte(%s, %s)",
        nanExpr,
        numVal
      )
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(numVal, nanExpr)),
        false,
        "lte(%s, %s)",
        numVal,
        nanExpr
      )
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(
            evaluate(lessThanOrEqual(nanExpr, otherVal)),
            false,
            "lte(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(lessThanOrEqual(otherVal, nanExpr)),
            false,
            "lte(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(lessThanOrEqual(arrayWithNaN1, arrayWithNaN2)),
      false,
      "lte(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )
  }

  @Test
  fun lte_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/lteError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(lessThanOrEqual(errorExpr, value), testDoc),
        "lte(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(lessThanOrEqual(value, errorExpr), testDoc),
        "lte(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(errorExpr, errorExpr), testDoc),
      "lte(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(errorExpr, nullValue()), testDoc),
      "lte(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun lte_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/lteMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(lessThanOrEqual(missingField, presentValue), testDoc),
      "lte(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(presentValue, missingField), testDoc),
      "lte(%s, %s)",
      presentValue,
      missingField
    )
  }
}
