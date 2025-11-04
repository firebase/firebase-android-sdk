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
import com.google.firebase.firestore.pipeline.Expression.Companion.startsWith
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class StartsWithTests {

  // --- StartsWith Tests ---
  @Test
  fun startsWith_getNonStringValue_isError() {
    val expr = startsWith(constant(42L), constant("search"))
    assertEvaluatesToError(evaluate(expr), "startsWith(42L, \"search\")")
  }

  @Test
  fun startsWith_getNonStringPrefix_isError() {
    val expr = startsWith(constant("search"), constant(42L))
    assertEvaluatesToError(evaluate(expr), "startsWith(\"search\", 42L)")
  }

  @Test
  fun startsWith_emptyInputs_returnsTrue() {
    val expr = startsWith(constant(""), constant(""))
    assertEvaluatesTo(evaluate(expr), true, "startsWith(\"\", \"\")")
  }

  @Test
  fun startsWith_emptyValue_returnsFalse() {
    val expr = startsWith(constant(""), constant("v"))
    assertEvaluatesTo(evaluate(expr), false, "startsWith(\"\", \"v\")")
  }

  @Test
  fun startsWith_emptyPrefix_returnsTrue() {
    val expr = startsWith(constant("value"), constant(""))
    assertEvaluatesTo(evaluate(expr), true, "startsWith(\"value\", \"\")")
  }

  @Test
  fun startsWith_returnsTrue() {
    val expr = startsWith(constant("search"), constant("sea"))
    assertEvaluatesTo(evaluate(expr), true, "startsWith(\"search\", \"sea\")")
  }

  @Test
  fun startsWith_returnsFalse() {
    val expr = startsWith(constant("search"), constant("Sea")) // Case-sensitive
    assertEvaluatesTo(evaluate(expr), false, "startsWith(\"search\", \"Sea\")")
  }

  @Test
  fun startsWith_largePrefix_returnsFalse() {
    val expr = startsWith(constant("val"), constant("a very long prefix"))
    assertEvaluatesTo(evaluate(expr), false, "startsWith(\"val\", \"a very long prefix\")")
  }
}
