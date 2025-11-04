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
import com.google.firebase.firestore.pipeline.Expression.Companion.regexContains
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class RegexContainsTests {

  // --- RegexContains Tests ---
  @Test
  fun regexContains_getNonStringRegex_isError() {
    val expr = regexContains(constant(42L), constant("search"))
    assertEvaluatesToError(evaluate(expr), "regexContains(42L, \"search\")")
  }

  @Test
  fun regexContains_getNonStringValue_isError() {
    val expr = regexContains(constant("ear"), constant(42L))
    assertEvaluatesToError(evaluate(expr), "regexContains(\"ear\", 42L)")
  }

  @Test
  fun regexContains_getInvalidRegex_isError() {
    val expr = regexContains(constant("abcabc"), constant("(abc)\\1"))
    assertEvaluatesToError(evaluate(expr), "regexContains invalid regex")
  }

  @Test
  fun regexContains_getStaticRegex() {
    val expr = regexContains(constant("yummy food"), constant(".*oo.*"))
    assertEvaluatesTo(evaluate(expr), true, "regexContains static")
  }

  @Test
  fun regexContains_getSubStringLiteral() {
    val expr = regexContains(constant("yummy good food"), constant("good"))
    assertEvaluatesTo(evaluate(expr), true, "regexContains substringing literal")
  }

  @Test
  fun regexContains_getSubStringRegex() {
    val expr = regexContains(constant("yummy good food"), constant("go*d"))
    assertEvaluatesTo(evaluate(expr), true, "regexContains substringing regex")
  }

  @Test
  fun regexContains_getDynamicRegex() {
    val expr = regexContains(constant("yummy food"), field("regex"))
    val doc1 = doc("coll/doc1", 0, mapOf("regex" to "^yummy.*"))
    val doc2 = doc("coll/doc2", 0, mapOf("regex" to "fooood$")) // This should be false for contains
    val doc3 = doc("coll/doc3", 0, mapOf("regex" to ".*"))

    assertEvaluatesTo(evaluate(expr, doc1), true, "regexContains dynamic doc1")
    assertEvaluatesTo(evaluate(expr, doc2), false, "regexContains dynamic doc2")
    assertEvaluatesTo(evaluate(expr, doc3), true, "regexContains dynamic doc3")
  }
}
