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

import com.google.firebase.firestore.model.Values.NULL_VALUE
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.equal
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.map
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
internal class EqualTests {

  // --- Eq (==) Tests ---

  @Test
  fun eq_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      val result = evaluate(equal(v1, v2))
      assertEvaluatesTo(result, true, "eq(%s, %s)", v1, v2)
    }
  }

  @Test
  fun eq_lessThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      // eq(v1, v2)
      val result1 = evaluate(equal(v1, v2))
      assertEvaluatesTo(result1, false, "eq(%s, %s)", v1, v2)
      // eq(v2, v1)
      val result2 = evaluate(equal(v2, v1))
      assertEvaluatesTo(result2, false, "eq(%s, %s)", v2, v1)
    }
  }

  // GreaterThanValues can be derived from LessThanValues by swapping pairs
  @Test
  fun eq_greaterThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      // eq(greater, less)
      val result = evaluate(equal(greater, less))
      assertEvaluatesTo(result, false, "eq(%s, %s)", greater, less)
    }
  }

  @Test
  fun eq_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      val result1 = evaluate(equal(v1, v2))
      assertEvaluatesTo(result1, false, "eq(%s, %s)", v1, v2)
      val result2 = evaluate(equal(v2, v1))
      assertEvaluatesTo(result2, false, "eq(%s, %s)", v2, v1)
    }
  }

  @Test
  fun eq_nullEqualsNull_returnsNull() {
    // In SQL-like semantics, NULL == NULL is NULL, not TRUE.
    // Firestore's behavior for direct comparison of two NULL constants:
    val v1 = nullValue()
    val v2 = nullValue()
    val result = evaluate(equal(v1, v2))
    assertEvaluatesToNull(result, "eq(%s, %s)", v1, v2)
  }

  @Test
  fun eq_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      // eq(null, value)
      assertEvaluatesToNull(evaluate(equal(nullVal, value)), "eq(%s, %s)", nullVal, value)
      // eq(value, null)
      assertEvaluatesToNull(evaluate(equal(value, nullVal)), "eq(%s, %s)", value, nullVal)
    }
    // eq(null, nonExistentField)
    val nullVal = nullValue()
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(equal(nullVal, missingField)),
      "eq(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun eq_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN

    // NaN == NaN is false
    assertEvaluatesTo(evaluate(equal(nanExpr, nanExpr)), false, "eq(%s, %s)", nanExpr, nanExpr)

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(equal(nanExpr, numVal)), false, "eq(%s, %s)", nanExpr, numVal)
      assertEvaluatesTo(evaluate(equal(numVal, nanExpr)), false, "eq(%s, %s)", numVal, nanExpr)
    }

    // Compare NaN with non-numeric types
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) { // Ensure we are not re-testing NaN vs NaN or NaN vs Numeric
          assertEvaluatesTo(
            evaluate(equal(nanExpr, otherVal)),
            false,
            "eq(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(equal(otherVal, nanExpr)),
            false,
            "eq(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }

    // NaN in array
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(equal(arrayWithNaN1, arrayWithNaN2)),
      false,
      "eq(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )

    // NaN in map
    val mapWithNaN1 = map(mapOf("foo" to Double.NaN))
    val mapWithNaN2 = map(mapOf("foo" to Double.NaN))
    assertEvaluatesTo(
      evaluate(equal(mapWithNaN1, mapWithNaN2)),
      false,
      "eq(%s, %s)",
      mapWithNaN1,
      mapWithNaN2
    )
  }

  @Test
  fun eq_nullContainerEquality_various() {
    val nullArray = array(nullValue()) // Array containing a Firestore Null

    assertEvaluatesTo(evaluate(equal(nullArray, constant(1L))), false, "eq(%s, 1L)", nullArray)
    assertEvaluatesTo(evaluate(equal(nullArray, constant("1"))), false, "eq(%s, \"1\")", nullArray)
    assertEvaluatesToNull(
      evaluate(equal(nullArray, nullValue())),
      "eq(%s, %s)",
      nullArray,
      nullValue()
    )
    assertEvaluatesTo(
      evaluate(equal(nullArray, ComparisonTestData.doubleNaN)),
      false,
      "eq(%s, %s)",
      nullArray,
      ComparisonTestData.doubleNaN
    )
    assertEvaluatesTo(evaluate(equal(nullArray, array())), false, "eq(%s, [])", nullArray)

    val nanArray = array(constant(Double.NaN))
    assertEvaluatesToNull(evaluate(equal(nullArray, nanArray)), "eq(%s, %s)", nullArray, nanArray)

    val anotherNullArray = array(nullValue())
    assertEvaluatesToNull(
      evaluate(equal(nullArray, anotherNullArray)),
      "eq(%s, %s)",
      nullArray,
      anotherNullArray
    )

    val nullMap = map(mapOf("foo" to NULL_VALUE)) // Map containing a Firestore Null
    val anotherNullMap = map(mapOf("foo" to NULL_VALUE))
    assertEvaluatesToNull(
      evaluate(equal(nullMap, anotherNullMap)),
      "eq(%s, %s)",
      nullMap,
      anotherNullMap
    )
    assertEvaluatesTo(evaluate(equal(nullMap, map(emptyMap()))), false, "eq(%s, {})", nullMap)
  }

  @Test
  fun eq_errorHandling_returnsError() {
    val errorExpr =
      field("a.b") // Accessing a nested field that might not exist or be of wrong type
    val testDoc = doc("test/eqError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
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
  fun eq_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/eqMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(equal(missingField, presentValue), testDoc),
      "eq(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(equal(presentValue, missingField), testDoc),
      "eq(%s, %s)",
      presentValue,
      missingField
    )
  }
}
