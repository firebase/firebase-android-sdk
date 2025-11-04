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

import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayLength
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.exists
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.not
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.comparison.ComparisonTestData
import com.google.firebase.firestore.testutil.TestUtil.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExistsTests {

  // --- Exists Tests ---

  @Test
  fun `valid field returns true for exists`() {
    val existsExpr = exists(field("x"))
    val doc = doc("coll/doc1", 1, mapOf("x" to 1))
    assertEvaluatesTo(evaluate(existsExpr, doc), true, "exists(existent-field))")
  }

  @Test
  fun `anything but unset returns true for exists`() {
    ComparisonTestData.allSupportedComparableValues.forEach { valueExpr ->
      assertEvaluatesTo(evaluate(exists(valueExpr)), true, "exists(%s)", valueExpr)
    }
  }

  @Test
  fun `null returns true for exists`() {
    assertEvaluatesTo(evaluate(exists(nullValue())), true, "exists(null)")
  }

  @Test
  fun `error returns error for exists`() {
    val errorProducingExpr = arrayLength(constant("notAnArray"))
    assertEvaluatesToError(evaluate(exists(errorProducingExpr)), "exists(error_expr)")
  }

  @Test
  fun `unset with not exists returns true`() {
    val unsetExpr = field("non-existent-field")
    val existsExpr = exists(unsetExpr)
    assertEvaluatesTo(evaluate(not(existsExpr)), true, "not(exists(non-existent-field))")
  }

  @Test
  fun `unset returns false for exists`() {
    val unsetExpr = field("non-existent-field")
    assertEvaluatesTo(evaluate(exists(unsetExpr)), false, "exists(non-existent-field)")
  }

  @Test
  fun `empty array returns true for exists`() {
    assertEvaluatesTo(evaluate(exists(array())), true, "exists([])")
  }

  @Test
  fun `empty map returns true for exists`() {
    // Expression.map() creates an empty map expression
    assertEvaluatesTo(evaluate(exists(map(emptyMap()))), true, "exists({})")
  }
}
