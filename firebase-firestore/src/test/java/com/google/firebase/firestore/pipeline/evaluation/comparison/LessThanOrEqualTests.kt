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
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThanOrEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class LessThanOrEqualTests {
  @Test
  fun `lte equal cases returns true`() {
    ComparisonTestData.equivalentValues.forEach { (left, right) ->
      assertEvaluatesTo(evaluate(lessThanOrEqual(left, right)), true, "lte(%s, %s)", left, right)
    }
  }

  @Test
  fun `lte equal cases reversed returns true`() {
    ComparisonTestData.equivalentValues.forEach { (left, right) ->
      assertEvaluatesTo(evaluate(lessThanOrEqual(right, left)), true, "lte(%s, %s)", right, left)
    }
  }

  @Test
  fun `lte unequal values ascending returns true`() {
    ComparisonTestData.unequalValues.forEach { (lesser, greater) ->
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(lesser, greater)),
        true,
        "lte(%s, %s)",
        lesser,
        greater
      )
    }
  }

  @Test
  fun `lte unequal values descending returns false`() {
    ComparisonTestData.unequalValues.forEach { (lesser, greater) ->
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(greater, lesser)),
        false,
        "lte(%s, %s)",
        greater,
        lesser
      )
    }
  }

  @Test
  fun `lte cross-type on greater returns false`() {
    ComparisonTestData.crossTypeValues.forEach { (lesser, greater) ->
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(greater, lesser)),
        false,
        "lte(%s, %s)",
        greater,
        lesser
      )
    }
  }

  @Test
  fun `lte cross-type on lesser returns false`() {
    ComparisonTestData.crossTypeValues.forEach { (lesser, greater) ->
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(lesser, greater)),
        false,
        "lte(%s, %s)",
        lesser,
        greater
      )
    }
  }

  @Test
  fun `error null is error`() {
    val errorExpr = Expression.error("test")
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(errorExpr, nullValue())),
      "lte(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun `null error is error`() {
    val errorExpr = Expression.error("test")
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(nullValue(), errorExpr)),
      "lte(%s, %s)",
      nullValue(),
      errorExpr
    )
  }
}
