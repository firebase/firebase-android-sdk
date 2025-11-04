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

package com.google.firebase.firestore.pipeline.evaluation.strings

import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.like
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class LikeTests {

  // --- Like Tests --- (Expected to be failing/error due to notImplemented)
  @Test
  fun like_getNonStringLike_isError() {
    val expr = like(constant(42L), constant("search"))
    assertEvaluatesToError(evaluate(expr), "like(42L, \"search\")")
  }

  @Test
  fun like_getNonStringValue_isError() {
    val expr = like(constant("ear"), constant(42L))
    assertEvaluatesToError(evaluate(expr), "like(\"ear\", 42L)")
  }

  @Test
  fun like_getStaticLike() {
    val expr = like(constant("yummy food"), constant("%food"))
    assertEvaluatesTo(evaluate(expr), true, "like(\"yummy food\", \"%food\")")
  }

  @Test
  fun like_getEmptySearchString() {
    val expr = like(constant(""), constant("%hi%"))
    assertEvaluatesTo(evaluate(expr), false, "like(\"\", \"%hi%\")")
  }

  @Test
  fun like_getEmptyLike() {
    val expr = like(constant("yummy food"), constant(""))
    assertEvaluatesTo(evaluate(expr), false, "like(\"yummy food\", \"\")")
  }

  @Test
  fun like_getEscapedLike() {
    val expr = like(constant("yummy food??"), constant("%food??"))
    assertEvaluatesTo(evaluate(expr), true, "like(\"yummy food??\", \"%food??\")")
  }

  @Test
  fun like_getDynamicLike() {
    val expr = like(constant("yummy food"), field("regex"))
    val doc1 = doc("coll/doc1", 0, mapOf("regex" to "yummy%"))
    val doc2 = doc("coll/doc2", 0, mapOf("regex" to "food%"))
    val doc3 = doc("coll/doc3", 0, mapOf("regex" to "yummy_food"))

    assertEvaluatesTo(evaluate(expr, doc1), true, "like dynamic doc1")
    assertEvaluatesTo(evaluate(expr, doc2), false, "like dynamic doc2")
    assertEvaluatesTo(evaluate(expr, doc3), true, "like dynamic doc3")
  }
}
