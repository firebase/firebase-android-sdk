// Copyright 2026 Google LLC
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
import com.google.firebase.firestore.pipeline.Expression.Companion.regexFindAll
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import com.google.firebase.firestore.testutil.TestUtil.wrap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class RegexFindAllTests {

  @Test
  fun regexFindAll_mirroringError() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = regexFindAll(left, right)
      assertEvaluatesToNull(evaluate(expr), "regexFindAll($name)")
    }
  }

  @Test
  fun regexFindAll_getStaticRegex_match() {
    val cases =
      listOf(
        Triple("b", "(a)?b", listOf(null)),
        Triple("b", "a?b", listOf("b")),
        Triple("ac", "a(b)?c", listOf(null)),
        Triple("ac", "ab?c", listOf("ac")),
        Triple("acac", "a(b)?c", listOf(null, null)),
        Triple("acac", "ab?c", listOf("ac", "ac")),
        Triple("acabcac", "a(b)?c", listOf(null, "b", null)),
        Triple("acabcac", "ab?c", listOf("ac", "abc", "ac")),
        Triple("acabcacabcac", "a(b)?c", listOf(null, "b", null, "b", null)),
        Triple("acabcacabcac", "ab?c", listOf("ac", "abc", "ac", "abc", "ac")),
        Triple("yummy food", "foo.", listOf("food")),
        Triple("yummy food", "food.", emptyList<String>()),
        Triple("yummy food", "bar", emptyList<String>()),
        Triple("yummy good food", "oo", listOf("oo", "oo")),
        Triple("yummy good food", ".oo.", listOf("good", "food")),
        Triple("yummy good food", "good", listOf("good")),
        Triple("yummy good food", "Good", emptyList<String>()),
        Triple("yummy good food", "(?i)Good", listOf("good")),
        Triple("yummy Good food", "good", emptyList<String>()),
        Triple("yummy Good food", "(?i)good", listOf("Good")),
        Triple("yummy good food", "go*d", listOf("good")),
        Triple("yummy food", ".", listOf("y", "u", "m", "m", "y", " ", "f", "o", "o", "d")),
        Triple("Try `func(x)` or `func(y)`", "`.+?`", listOf("`func(x)`", "`func(y)`")),
        Triple("Try `func(x)` or `func(y)`", "`(.+?)`", listOf("func(x)", "func(y)"))
      )

    for ((value, regex, result) in cases) {
      val expr = regexFindAll(constant(value), constant(regex))
      assertEvaluatesTo(evaluate(expr), wrap(result), "regexFindAll($value, $regex)")
    }
  }

  @Test
  fun regexFindAll_getInvalidRegex_isError() {
    val expr = regexFindAll(constant("abcabc"), constant("(abc)\\1"))
    assertEvaluatesToError(evaluate(expr), "regexFindAll invalid regex")
  }

  @Test
  fun regexFindAll_getNonStringRegex_isError() {
    val expr = regexFindAll(constant(42L), constant("search"))
    assertEvaluatesToError(evaluate(expr), "regexFindAll(42L, \"search\")")
  }

  @Test
  fun regexFindAll_getNonStringValue_isError() {
    val expr = regexFindAll(constant("ear"), constant(42L))
    assertEvaluatesToError(evaluate(expr), "regexFindAll(\"ear\", 42L)")
  }

  @Test
  fun regexFindAll_getMultipleCaptureGroups_isError() {
    val expr =
      regexFindAll(constant("Date: 2025-11-15"), constant("Date: (\\d{4})-(\\d{2})-(\\d{2})"))
    assertEvaluatesToError(evaluate(expr), "regexFindAll multiple capture groups")
  }
}
