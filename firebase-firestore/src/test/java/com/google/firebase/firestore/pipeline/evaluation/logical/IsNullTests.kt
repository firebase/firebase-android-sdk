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

package com.google.firebase.firestore.pipeline.evaluation.logical

import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.isNull
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IsNullTests {
  private val nullExpr = nullValue()
  private val errorExpr = field("error.field").equal(constant("random"))
  private val errorDoc =
    doc("coll/docError", 1, mapOf("error" to 123)) // "error.field" will be UNSET
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- IsNull Tests ---
  @Test
  fun `isNull - null returns true`() {
    val expr = isNull(nullExpr)
    assertEvaluatesTo(evaluate(expr, emptyDoc), true, "isNull(null)")
  }

  @Test
  fun `isNull - error returns error`() {
    val expr = isNull(errorExpr)
    assertEvaluatesToError(evaluate(expr, errorDoc), "isNull(error)")
  }

  @Test
  fun `isNull - unset field returns error`() {
    val expr = isNull(field("non-existent-field"))
    assertEvaluatesToError(evaluate(expr, emptyDoc), "isNull(unset)")
  }

  @Test
  fun `isNull - anything but null returns false`() {
    val values =
      listOf(
        constant(true),
        constant(false),
        constant(0),
        constant(1.0),
        constant("abc"),
        constant(Double.NaN),
        array(constant(1)),
        map(mapOf("a" to 1))
      )
    for (valueExpr in values) {
      val expr = isNull(valueExpr)
      assertEvaluatesTo(evaluate(expr, emptyDoc), false, "isNull(${valueExpr})")
    }
  }
}
