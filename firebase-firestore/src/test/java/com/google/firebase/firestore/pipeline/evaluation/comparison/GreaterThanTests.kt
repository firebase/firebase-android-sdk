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
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThan
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
internal class GreaterThanTests {
  @Test
  fun gt_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(greaterThan(v1, v2)), "gt(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(evaluate(greaterThan(v1, v2)), false, "gt(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun gt_lessThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThan(v1, v2)), false, "gt(%s, %s)", v1, v2)
    }
  }

  @Test
  fun gt_greaterThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(greaterThan(greater, less)), true, "gt(%s, %s)", greater, less)
    }
  }

  @Test
  fun gt_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(greaterThan(v1, v2)), "gt(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(greaterThan(v2, v1)), "gt(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(greaterThan(v1, v2)), false, "gt(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(greaterThan(v2, v1)), false, "gt(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun gt_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(greaterThan(nullVal, value)), "gt(%s, %s)", nullVal, value)
      assertEvaluatesToNull(evaluate(greaterThan(value, nullVal)), "gt(%s, %s)", value, nullVal)
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(evaluate(greaterThan(nullVal, nullVal)), "gt(%s, %s)", nullVal, nullVal)
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(greaterThan(nullVal, missingField)),
      "gt(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun gt_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(
      evaluate(greaterThan(nanExpr, nanExpr)),
      false,
      "gt(%s, %s)",
      nanExpr,
      nanExpr
    )

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(
        evaluate(greaterThan(nanExpr, numVal)),
        false,
        "gt(%s, %s)",
        nanExpr,
        numVal
      )
      assertEvaluatesTo(
        evaluate(greaterThan(numVal, nanExpr)),
        false,
        "gt(%s, %s)",
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
            evaluate(greaterThan(nanExpr, otherVal)),
            false,
            "gt(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(greaterThan(otherVal, nanExpr)),
            false,
            "gt(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(greaterThan(arrayWithNaN1, arrayWithNaN2)),
      false,
      "gt(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )
  }

  @Test
  fun gt_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/gtError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(greaterThan(errorExpr, value), testDoc),
        "gt(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(greaterThan(value, errorExpr), testDoc),
        "gt(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(greaterThan(errorExpr, errorExpr), testDoc),
      "gt(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(greaterThan(errorExpr, nullValue()), testDoc),
      "gt(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun gt_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/gtMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(greaterThan(missingField, presentValue), testDoc),
      "gt(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(greaterThan(presentValue, missingField), testDoc),
      "gt(%s, %s)",
      presentValue,
      missingField
    )
  }
}
