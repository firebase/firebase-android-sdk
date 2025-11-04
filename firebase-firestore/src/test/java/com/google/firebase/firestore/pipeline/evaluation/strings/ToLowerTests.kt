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
import com.google.firebase.firestore.pipeline.Expression.Companion.toLower
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ToLowerTests {

  // --- ToLower Tests ---
  @Test
  fun toLower_basic() {
    val expr = toLower(constant("FOO Bar"))
    assertEvaluatesTo(evaluate(expr), encodeValue("foo bar"), "toLower(\"FOO Bar\")")
  }

  @Test
  fun toLower_empty() {
    val expr = toLower(constant(""))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "toLower(\"\")")
  }

  @Test
  fun toLower_nonString() {
    val expr = toLower(constant(123L))
    assertEvaluatesToError(evaluate(expr), "toLower(123L)")
  }

  @Test
  fun toLower_null() {
    val expr = toLower(nullValue()) // Use Expression.nullValue() for Firestore null
    assertEvaluatesToNull(evaluate(expr), "toLower(null)")
  }
}
