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
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThan
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
internal class LessThanTests {
  @Test
  fun lt_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lessThan(v1, v2)), "lt(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(evaluate(lessThan(v1, v2)), false, "lt(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun lt_lessThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      val result = evaluate(lessThan(v1, v2))
      assertEvaluatesTo(result, true, "lt(%s, %s)", v1, v2)
    }
  }

  @Test
  fun lt_greaterThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(lessThan(greater, less)), false, "lt(%s, %s)", greater, less)
    }
  }

  @Test
  fun lt_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lessThan(v1, v2)), "lt(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(lessThan(v2, v1)), "lt(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(lessThan(v1, v2)), false, "lt(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(lessThan(v2, v1)), false, "lt(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun lt_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(lessThan(nullVal, value)), "lt(%s, %s)", nullVal, value)
      assertEvaluatesToNull(evaluate(lessThan(value, nullVal)), "lt(%s, %s)", value, nullVal)
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(evaluate(lessThan(nullVal, nullVal)), "lt(%s, %s)", nullVal, nullVal)
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(lessThan(nullVal, missingField)),
      "lt(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun lt_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(lessThan(nanExpr, nanExpr)), false, "lt(%s, %s)", nanExpr, nanExpr)

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(lessThan(nanExpr, numVal)), false, "lt(%s, %s)", nanExpr, numVal)
      assertEvaluatesTo(evaluate(lessThan(numVal, nanExpr)), false, "lt(%s, %s)", numVal, nanExpr)
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(
            evaluate(lessThan(nanExpr, otherVal)),
            false,
            "lt(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(lessThan(otherVal, nanExpr)),
            false,
            "lt(%s, %s)",
            otherVal,
            nanExpr
          )
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
    val errorExpr = field("a.b")
    val testDoc = doc("test/ltError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
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
  fun lt_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/ltMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(lessThan(missingField, presentValue), testDoc),
      "lt(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(lessThan(presentValue, missingField), testDoc),
      "lt(%s, %s)",
      presentValue,
      missingField
    )
  }
}
