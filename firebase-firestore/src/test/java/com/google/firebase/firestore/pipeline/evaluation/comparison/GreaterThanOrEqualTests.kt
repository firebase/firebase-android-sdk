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
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThanOrEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class GreaterThanOrEqualTests {
  @Test
  fun greaterThanOrEqual_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThanOrEqual(v1, v2)), true, "gte(%s, %s)", v1, v2)
    }
  }

  @Test
  fun greaterThanOrEqual_equivalentValues_reversed_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThanOrEqual(v2, v1)), true, "gte(%s, %s)", v2, v1)
    }
  }

  @Test
  fun greaterThanOrEqual_unequalValues_onLesser_returnsFalse() {
    ComparisonTestData.unequalValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThanOrEqual(v1, v2)), false, "gte(%s, %s)", v1, v2)
    }
  }

  @Test
  fun greaterThanOrEqual_unequalValues_onGreater_returnsTrue() {
    ComparisonTestData.unequalValues.forEach { (less, greater) ->
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
  fun greaterThanOrEqual_crossTypeValues_returnsFalse() {
    ComparisonTestData.crossTypeValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThanOrEqual(v1, v2)), false, "gte(%s, %s)", v1, v2)
    }
  }

  @Test
  fun greaterThanOrEqual_crossTypeValues_reversed_returnsFalse() {
    ComparisonTestData.crossTypeValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThanOrEqual(v2, v1)), false, "gte(%s, %s)", v2, v1)
    }
  }

  @Test
  fun gte_errorHandling_returnsError() {
    val errorExpr = Expression.error("test")
    val testDoc = doc("test/gteError", 0, mapOf("a" to 123))

    ComparisonTestData.allValues.forEach { value ->
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

    assertEvaluatesTo(
      evaluate(greaterThanOrEqual(missingField, presentValue), testDoc),
      false,
      "gte(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesTo(
      evaluate(greaterThanOrEqual(presentValue, missingField), testDoc),
      false,
      "gte(%s, %s)",
      presentValue,
      missingField
    )
  }
}
