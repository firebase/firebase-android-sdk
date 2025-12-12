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

import com.google.firebase.firestore.pipeline.Expression.Companion.add
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.isNan
import com.google.firebase.firestore.pipeline.Expression.Companion.isNotNan
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
class IsNanTests {
  private val nullExpr = nullValue()
  private val nanExpr = constant(Double.NaN)
  private val testDocWithNan =
    doc("coll/docNan", 1, mapOf("nanValue" to Double.NaN, "field" to "value"))
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  // --- IsNan / IsNotNan Tests ---
  @Test
  fun `isNan - nan returns true`() {
    assertEvaluatesTo(evaluate(isNan(nanExpr), emptyDoc), true, "isNan(NaN)")
    assertEvaluatesTo(
      evaluate(isNan(field("nanValue")), testDocWithNan),
      true,
      "isNan(field(nanValue))"
    )
  }

  @Test
  fun `isNan - not nan returns false`() {
    assertEvaluatesTo(evaluate(isNan(constant(42.0)), emptyDoc), false, "isNan(42.0)")
    assertEvaluatesTo(evaluate(isNan(constant(42L)), emptyDoc), false, "isNan(42L)")
  }

  @Test
  fun `isNotNan - not nan returns true`() {
    assertEvaluatesTo(evaluate(isNotNan(constant(42.0)), emptyDoc), true, "isNotNan(42.0)")
    assertEvaluatesTo(evaluate(isNotNan(constant(42L)), emptyDoc), true, "isNotNan(42L)")
  }

  @Test
  fun `isNotNan - nan returns false`() {
    assertEvaluatesTo(evaluate(isNotNan(nanExpr), emptyDoc), false, "isNotNan(NaN)")
    assertEvaluatesTo(
      evaluate(isNotNan(field("nanValue")), testDocWithNan),
      false,
      "isNotNan(field(nanValue))"
    )
  }

  @Test
  fun `isNan - other nan representations returns true`() {
    val nanPlusOne = add(nanExpr, constant(1L))
    assertEvaluatesTo(evaluate(isNan(nanPlusOne), emptyDoc), true, "isNan(NaN + 1)")
  }

  @Test
  fun `isNan - non numeric returns error`() {
    assertEvaluatesToError(evaluate(isNan(constant(true)), emptyDoc), "isNan(true) should be error")
    assertEvaluatesToError(evaluate(isNan(constant("abc")), emptyDoc), "isNan(abc) should be error")
    assertEvaluatesToError(evaluate(isNan(array()), emptyDoc), "isNan([]) should be error")
    assertEvaluatesToError(evaluate(isNan(map(emptyMap())), emptyDoc), "isNan({}) should be error")
  }

  @Test
  fun `isNan - null returns null`() {
    assertEvaluatesToNull(evaluate(isNan(nullExpr), emptyDoc), "isNan(null) should be null")
  }
}
