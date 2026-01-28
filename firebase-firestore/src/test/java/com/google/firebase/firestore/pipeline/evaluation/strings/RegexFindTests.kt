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
import com.google.firebase.firestore.pipeline.Expression.Companion.regexFind
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class RegexFindTests {

  // --- RegexFind Tests ---

  @Test
  fun regexFind_mirroringError() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = regexFind(left, right)
      assertEvaluatesToNull(evaluate(expr), "regexFind($name)")
    }
  }

  @Test
  fun regexFind_getStaticRegex_match() {
    val cases =
      listOf(
        Triple("b", "(a)?b", null),
        Triple("b", "a?b", "b"),
        Triple("", ".*", ""),
        Triple("funny", "", ""),
        Triple("ac", "a(b)?c", null),
        Triple("ac", "ab?c", "ac"),
        Triple("acac", "a(b)?c", null),
        Triple("acac", "ab?c", "ac"),
        Triple("acabcac", "a(b)?c", null),
        Triple("acabcac", "ab?c", "ac"),
        Triple("acabcacabcac", "a(b)?c", null),
        Triple("acabcacabcac", "ab?c", "ac"),
        Triple("yummy food", "foo.", "food"),
        Triple("yummy food", "food.", null),
        Triple("yummy food", "bar", null),
        Triple("yummy good food", "oo", "oo"),
        Triple("yummy good food", ".oo.", "good"),
        Triple("yummy good food", "good", "good"),
        Triple("yummy good food", "Good", null),
        Triple("yummy good food", "(?i)Good", "good"),
        Triple("yummy Good food", "good", null),
        Triple("yummy Good food", "(?i)good", "Good"),
        Triple("yummy good food", "go*d", "good"),
        Triple("yummy good food", ".", "y"),
        Triple("Try `func(x)` or `func(y)`", "`.+?`", "`func(x)`"),
        Triple("Try `func(x)` or `func(y)`", "`(.+?)`", "func(x)")
      )

    for ((value, regex, result) in cases) {
      val expr = regexFind(constant(value), constant(regex))
      if (result == null) {
        assertEvaluatesToNull(evaluate(expr), "regexFind($value, $regex)")
      } else {
        assertEvaluatesTo(evaluate(expr), result, "regexFind($value, $regex)")
      }
    }
  }

  @Test
  fun regexFind_getDynamicRegex() {
    val expr = regexFind(constant("yummy food"), field("regex"))
    val doc1 = doc("coll/doc1", 0, mapOf("regex" to "yummy"))
    val doc2 = doc("coll/doc2", 0, mapOf("regex" to "food"))
    val doc3 = doc("coll/doc3", 0, mapOf("regex" to ".*"))

    assertEvaluatesTo(evaluate(expr, doc1), "yummy", "regexFind dynamic doc1")
    assertEvaluatesTo(evaluate(expr, doc2), "food", "regexFind dynamic doc2")
    assertEvaluatesTo(evaluate(expr, doc3), "yummy food", "regexFind dynamic doc3")
  }

  @Test
  fun regexFind_getInvalidRegex_isError() {
    val expr = regexFind(constant("abcabc"), constant("(abc)\\1"))
    assertEvaluatesToError(evaluate(expr), "regexFind invalid regex")
  }

  @Test
  fun regexFind_getNonStringRegex_isError() {
    val expr = regexFind(constant(42L), constant("search"))
    assertEvaluatesToError(evaluate(expr), "regexFind(42L, \"search\")")
  }

  @Test
  fun regexFind_getNonStringValue_isError() {
    val expr = regexFind(constant("ear"), constant(42L))
    assertEvaluatesToError(evaluate(expr), "regexFind(\"ear\", 42L)")
  }

  @Test
  fun regexFind_getMultipleCaptureGroups_isError() {
    val expr = regexFind(constant("Date: 2025-11-15"), constant("Date: (\\d{4})-(\\d{2})-(\\d{2})"))
    assertEvaluatesToError(evaluate(expr), "regexFind multiple capture groups")
  }
}
