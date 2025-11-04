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
import com.google.firebase.firestore.pipeline.Expression.Companion.regexMatch
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class RegexMatchTests {

  // --- RegexMatch Tests ---
  @Test
  fun regexMatch_getNonStringRegex_isError() {
    val expr = regexMatch(constant(42L), constant("search"))
    assertEvaluatesToError(evaluate(expr), "regexMatch(42L, \"search\")")
  }

  @Test
  fun regexMatch_getNonStringValue_isError() {
    val expr = regexMatch(constant("ear"), constant(42L))
    assertEvaluatesToError(evaluate(expr), "regexMatch(\"ear\", 42L)")
  }

  @Test
  fun regexMatch_getInvalidRegex_isError() {
    val expr = regexMatch(constant("abcabc"), constant("(abc)\\1"))
    assertEvaluatesToError(evaluate(expr), "regexMatch invalid regex")
  }

  @Test
  fun regexMatch_getStaticRegex() {
    val expr = regexMatch(constant("yummy food"), constant(".*oo.*"))
    assertEvaluatesTo(evaluate(expr), true, "regexMatch static")
  }

  @Test
  fun regexMatch_getSubStringLiteral() {
    val expr = regexMatch(constant("yummy good food"), constant("good"))
    assertEvaluatesTo(evaluate(expr), false, "regexMatch substringing literal (false)")
  }

  @Test
  fun regexMatch_getSubStringRegex() {
    val expr = regexMatch(constant("yummy good food"), constant("go*d"))
    assertEvaluatesTo(evaluate(expr), false, "regexMatch substringing regex (false)")
  }

  @Test
  fun regexMatch_getDynamicRegex() {
    val expr = regexMatch(constant("yummy food"), field("regex"))
    val doc1 = doc("coll/doc1", 0, mapOf("regex" to "^yummy.*")) // Should be true
    val doc2 = doc("coll/doc2", 0, mapOf("regex" to "fooood$"))
    val doc3 = doc("coll/doc3", 0, mapOf("regex" to ".*"))
    val doc4 = doc("coll/doc4", 0, mapOf("regex" to "yummy")) // Should be false

    assertEvaluatesTo(evaluate(expr, doc1), true, "regexMatch dynamic doc1")
    assertEvaluatesTo(evaluate(expr, doc2), false, "regexMatch dynamic doc2")
    assertEvaluatesTo(evaluate(expr, doc3), true, "regexMatch dynamic doc3")
    assertEvaluatesTo(evaluate(expr, doc4), false, "regexMatch dynamic doc4")
  }
}
