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
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThanOrEqual
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
internal class GreaterThanOrEqualTests {
  @Test
  fun gte_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(greaterThanOrEqual(v1, v2)), "gte(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(evaluate(greaterThanOrEqual(v1, v2)), true, "gte(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun gte_lessThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThanOrEqual(v1, v2)), false, "gte(%s, %s)", v1, v2)
    }
  }

  @Test
  fun gte_greaterThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(
        evaluate(greaterThanOrEqual(greater, less)),
        true,
        "gte(%s, %s)",
        greater,
        less
      )
    }
  }

  @Test
  fun gte_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(greaterThanOrEqual(v1, v2)), "gte(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(greaterThanOrEqual(v2, v1)), "gte(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(greaterThanOrEqual(v1, v2)), false, "gte(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(greaterThanOrEqual(v2, v1)), false, "gte(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun gte_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(
        evaluate(greaterThanOrEqual(nullVal, value)),
        "gte(%s, %s)",
        nullVal,
        value
      )
      assertEvaluatesToNull(
        evaluate(greaterThanOrEqual(value, nullVal)),
        "gte(%s, %s)",
        value,
        nullVal
      )
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(
      evaluate(greaterThanOrEqual(nullVal, nullVal)),
      "gte(%s, %s)",
      nullVal,
      nullVal
    )
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(nullVal, missingField)),
      "gte(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun gte_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(
      evaluate(greaterThanOrEqual(nanExpr, nanExpr)),
      false,
      "gte(%s, %s)",
      nanExpr,
      nanExpr
    )

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(
        evaluate(greaterThanOrEqual(nanExpr, numVal)),
        false,
        "gte(%s, %s)",
        nanExpr,
        numVal
      )
      assertEvaluatesTo(
        evaluate(greaterThanOrEqual(numVal, nanExpr)),
        false,
        "gte(%s, %s)",
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
            evaluate(greaterThanOrEqual(nanExpr, otherVal)),
            false,
            "gte(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(greaterThanOrEqual(otherVal, nanExpr)),
            false,
            "gte(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(greaterThanOrEqual(arrayWithNaN1, arrayWithNaN2)),
      false,
      "gte(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )
  }

  @Test
  fun gte_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/gteError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(greaterThanOrEqual(errorExpr, value), testDoc),
        "gte(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(greaterThanOrEqual(value, errorExpr), testDoc),
        "gte(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(errorExpr, errorExpr), testDoc),
      "gte(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(errorExpr, nullValue()), testDoc),
      "gte(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun gte_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/gteMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(missingField, presentValue), testDoc),
      "gte(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(presentValue, missingField), testDoc),
      "gte(%s, %s)",
      presentValue,
      missingField
    )
  }
}
