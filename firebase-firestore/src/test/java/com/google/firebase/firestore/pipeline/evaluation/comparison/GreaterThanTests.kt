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
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThan
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class GreaterThanTests {
  @Test
  fun `gt equivalent values returns false`() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThan(v1, v2)), false, "gt(%s, %s)", v1, v2)
    }
  }

  @Test
  fun `gt equivalent values reversed returns false`() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThan(v2, v1)), false, "gt(%s, %s)", v2, v1)
    }
  }

  @Test
  fun `gt unequal values on greater returns true`() {
    ComparisonTestData.unequalValues.forEach { (lesser, greater) ->
      assertEvaluatesTo(evaluate(greaterThan(greater, lesser)), true, "gt(%s, %s)", greater, lesser)
    }
  }

  @Test
  fun `gt unequal values on lesser returns false`() {
    ComparisonTestData.unequalValues.forEach { (lesser, greater) ->
      assertEvaluatesTo(
        evaluate(greaterThan(lesser, greater)),
        false,
        "gt(%s, %s)",
        lesser,
        greater
      )
    }
  }

  @Test
  fun `gt cross-type on greater returns false`() {
    ComparisonTestData.crossTypeValues.forEach { (lesser, greater) ->
      assertEvaluatesTo(
        evaluate(greaterThan(greater, lesser)),
        false,
        "gt(%s, %s)",
        greater,
        lesser
      )
    }
  }

  @Test
  fun `gt cross-type on lesser returns false`() {
    ComparisonTestData.crossTypeValues.forEach { (lesser, greater) ->
      assertEvaluatesTo(
        evaluate(greaterThan(lesser, greater)),
        false,
        "gt(%s, %s)",
        lesser,
        greater
      )
    }
  }

  @Test
  fun gt_errorHandling_returnsError() {
    val errorExpr = Expression.error("test")
    val testDoc = doc("test/gtError", 0, mapOf("a" to 123))

    ComparisonTestData.allValues.forEach { value ->
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
  fun gt_missingField_returnsFalse() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/gtMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesTo(
      evaluate(greaterThan(missingField, presentValue), testDoc),
      false,
      "gt(%s, %s)",
      missingField,
      presentValue
    )

    assertEvaluatesTo(
      evaluate(greaterThan(presentValue, missingField), testDoc),
      false,
      "gt(%s, %s)",
      presentValue,
      missingField
    )
  }
}
