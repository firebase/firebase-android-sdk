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
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqual
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
internal class NotEqualTests {
  @Test
  fun neq_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      val result = evaluate(notEqual(v1, v2))
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(result, "neq(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(result, false, "neq(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun neq_lessThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(notEqual(v1, v2)), true, "neq(%s, %s)", v1, v2)
      assertEvaluatesTo(evaluate(notEqual(v2, v1)), true, "neq(%s, %s)", v2, v1)
    }
  }

  @Test
  fun neq_greaterThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(notEqual(greater, less)), true, "neq(%s, %s)", greater, less)
    }
  }

  @Test
  fun neq_mixedTypeValues_returnTrue() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(notEqual(v1, v2)), "neq(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(notEqual(v2, v1)), "neq(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(notEqual(v1, v2)), true, "neq(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(notEqual(v2, v1)), true, "neq(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun neq_nullNotEqualsNull_returnsNull() {
    val v1 = nullValue()
    val v2 = nullValue()
    val result = evaluate(notEqual(v1, v2))
    assertEvaluatesToNull(result, "neq(%s, %s)", v1, v2)
  }

  @Test
  fun neq_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(notEqual(nullVal, value)), "neq(%s, %s)", nullVal, value)
      assertEvaluatesToNull(evaluate(notEqual(value, nullVal)), "neq(%s, %s)", value, nullVal)
    }
    val nullVal = nullValue()
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(notEqual(nullVal, missingField)),
      "neq(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun neq_nanComparisons_returnTrue() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(notEqual(nanExpr, nanExpr)), true, "neq(%s, %s)", nanExpr, nanExpr)

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(notEqual(nanExpr, numVal)), true, "neq(%s, %s)", nanExpr, numVal)
      assertEvaluatesTo(evaluate(notEqual(numVal, nanExpr)), true, "neq(%s, %s)", numVal, nanExpr)
    }

    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(
            evaluate(notEqual(nanExpr, otherVal)),
            true,
            "neq(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(notEqual(otherVal, nanExpr)),
            true,
            "neq(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }

    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(notEqual(arrayWithNaN1, arrayWithNaN2)),
      true,
      "neq(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )

    val mapWithNaN1 = map(mapOf("foo" to Double.NaN))
    val mapWithNaN2 = map(mapOf("foo" to Double.NaN))
    assertEvaluatesTo(
      evaluate(notEqual(mapWithNaN1, mapWithNaN2)),
      true,
      "neq(%s, %s)",
      mapWithNaN1,
      mapWithNaN2
    )
  }

  @Test
  fun neq_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/neqError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
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
  }

  @Test
  fun neq_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/neqMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(notEqual(missingField, presentValue), testDoc),
      "neq(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(notEqual(presentValue, missingField), testDoc),
      "neq(%s, %s)",
      presentValue,
      missingField
    )
  }
}
