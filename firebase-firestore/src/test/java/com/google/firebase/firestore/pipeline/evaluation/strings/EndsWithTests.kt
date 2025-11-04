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
import com.google.firebase.firestore.pipeline.Expression.Companion.endsWith
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class EndsWithTests {

  // --- EndsWith Tests ---
  @Test
  fun endsWith_getNonStringValue_isError() {
    val expr = endsWith(constant(42L), constant("search"))
    assertEvaluatesToError(evaluate(expr), "endsWith(42L, \"search\")")
  }

  @Test
  fun endsWith_getNonStringSuffix_isError() {
    val expr = endsWith(constant("search"), constant(42L))
    assertEvaluatesToError(evaluate(expr), "endsWith(\"search\", 42L)")
  }

  @Test
  fun endsWith_emptyInputs_returnsTrue() {
    val expr = endsWith(constant(""), constant(""))
    assertEvaluatesTo(evaluate(expr), true, "endsWith(\"\", \"\")")
  }

  @Test
  fun endsWith_emptyValue_returnsFalse() {
    val expr = endsWith(constant(""), constant("v"))
    assertEvaluatesTo(evaluate(expr), false, "endsWith(\"\", \"v\")")
  }

  @Test
  fun endsWith_emptySuffix_returnsTrue() {
    val expr = endsWith(constant("value"), constant(""))
    assertEvaluatesTo(evaluate(expr), true, "endsWith(\"value\", \"\")")
  }

  @Test
  fun endsWith_returnsTrue() {
    val expr = endsWith(constant("search"), constant("rch"))
    assertEvaluatesTo(evaluate(expr), true, "endsWith(\"search\", \"rch\")")
  }

  @Test
  fun endsWith_returnsFalse() {
    val expr = endsWith(constant("search"), constant("rcH")) // Case-sensitive
    assertEvaluatesTo(evaluate(expr), false, "endsWith(\"search\", \"rcH\")")
  }

  @Test
  fun endsWith_largeSuffix_returnsFalse() {
    val expr = endsWith(constant("val"), constant("a very long suffix"))
    assertEvaluatesTo(evaluate(expr), false, "endsWith(\"val\", \"a very long suffix\")")
  }
}
