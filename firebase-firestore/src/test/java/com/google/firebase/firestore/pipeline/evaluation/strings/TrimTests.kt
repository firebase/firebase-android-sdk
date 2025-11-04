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

import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.Expression.Companion.trim
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TrimTests {

  // --- Trim Tests ---
  @Test
  fun trim_basic() {
    val expr = trim(constant("  foo bar  "))
    assertEvaluatesTo(evaluate(expr), encodeValue("foo bar"), "trim(\"  foo bar  \")")
  }

  @Test
  fun trim_noTrimNeeded() {
    val expr = trim(constant("foo bar"))
    assertEvaluatesTo(evaluate(expr), encodeValue("foo bar"), "trim(\"foo bar\")")
  }

  @Test
  fun trim_onlyWhitespace() {
    val expr = trim(constant("   \t\n  "))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "trim(\"   \t\n  \")")
  }

  @Test
  fun trim_empty() {
    val expr = trim(constant(""))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "trim(\"\")")
  }

  @Test
  fun trim_nonString() {
    val expr = trim(constant(123L))
    assertEvaluatesToError(evaluate(expr), "trim(123L)")
  }

  @Test
  fun trim_null() {
    val expr = trim(nullValue())
    assertEvaluatesToNull(evaluate(expr), "trim(null)")
  }
}
