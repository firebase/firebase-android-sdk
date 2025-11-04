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

package com.google.firebase.firestore.pipeline.evaluation.debug

import com.google.firebase.firestore.pipeline.Expression.Companion.arrayLength
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.isError
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.comparison.ComparisonTestData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IsErrorTests {

  // --- IsError Tests ---

  @Test
  fun `isError error returns true`() {
    val errorProducingExpr = arrayLength(constant("notAnArray"))
    assertEvaluatesTo(evaluate(isError(errorProducingExpr)), true, "isError(error_expr)")
  }

  @Test
  fun `isError field missing returns false`() {
    // Evaluating a missing field results in UNSET. isError(UNSET) should be false.
    val fieldExpr = field("target")
    assertEvaluatesTo(evaluate(isError(fieldExpr)), false, "isError(missing_field)")
  }

  @Test
  fun `isError non-error returns false`() {
    assertEvaluatesTo(evaluate(isError(constant(42L))), false, "isError(42L)")
  }

  @Test
  fun `isError explicit null returns false`() {
    assertEvaluatesTo(evaluate(isError(nullValue())), false, "isError(null)")
  }

  @Test
  fun `isError unset returns false`() {
    // Evaluating a non-existent field results in UNSET. isError(UNSET) should be false.
    val unsetExpr = field("non-existent-field")
    assertEvaluatesTo(evaluate(isError(unsetExpr)), false, "isError(non-existent-field)")
  }

  @Test
  fun `isError anything but error returns false`() {
    ComparisonTestData.allSupportedComparableValues.forEach { valueExpr ->
      assertEvaluatesTo(evaluate(isError(valueExpr)), false, "isError(%s)", valueExpr)
    }
    assertEvaluatesTo(evaluate(isError(nullValue())), false, "isError(null)")
    assertEvaluatesTo(evaluate(isError(constant(0L))), false, "isError(0L)")
  }
}
