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
import com.google.firebase.firestore.pipeline.Expression.Companion.toUpper
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ToUpperTests {

  // --- ToUpper Tests ---
  @Test
  fun toUpper_basic() {
    val expr = toUpper(constant("foo Bar"))
    assertEvaluatesTo(evaluate(expr), encodeValue("FOO BAR"), "toUpper(\"foo Bar\")")
  }

  @Test
  fun toUpper_empty() {
    val expr = toUpper(constant(""))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "toUpper(\"\")")
  }

  @Test
  fun toUpper_nonString() {
    val expr = toUpper(constant(123L))
    assertEvaluatesToError(evaluate(expr), "toUpper(123L)")
  }

  @Test
  fun toUpper_null() {
    val expr = toUpper(nullValue())
    assertEvaluatesToNull(evaluate(expr), "toUpper(null)")
  }
}
