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
import com.google.firebase.firestore.pipeline.Expression.Companion.stringContains
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class StringContainsTests {

  // --- StrContains Tests ---
  @Test
  fun stringContains_valueNonString_isError() {
    val expr = stringContains(constant(42L), constant("value"))
    assertEvaluatesToError(evaluate(expr), "stringContains(42L, \"value\")")
  }

  @Test
  fun stringContains_subStringNonString_isError() {
    val expr = stringContains(constant("search space"), constant(42L))
    assertEvaluatesToError(evaluate(expr), "stringContains(\"search space\", 42L)")
  }

  @Test
  fun stringContains_executeTrue() {
    assertEvaluatesTo(
      evaluate(stringContains(constant("abc"), constant("c"))),
      true,
      "stringContains true 1"
    )
    assertEvaluatesTo(
      evaluate(stringContains(constant("abc"), constant("bc"))),
      true,
      "stringContains true 2"
    )
    assertEvaluatesTo(
      evaluate(stringContains(constant("abc"), constant("abc"))),
      true,
      "stringContains true 3"
    )
    assertEvaluatesTo(
      evaluate(stringContains(constant("abc"), constant(""))),
      true,
      "stringContains true 4"
    ) // Empty string is a substringing
    assertEvaluatesTo(
      evaluate(stringContains(constant(""), constant(""))),
      true,
      "stringContains true 5"
    ) // Empty string in empty string
    assertEvaluatesTo(
      evaluate(stringContains(constant("☃☃☃"), constant("☃"))),
      true,
      "stringContains true 6"
    )
  }

  @Test
  fun stringContains_executeFalse() {
    assertEvaluatesTo(
      evaluate(stringContains(constant("abc"), constant("abcd"))),
      false,
      "stringContains false 1"
    )
    assertEvaluatesTo(
      evaluate(stringContains(constant("abc"), constant("d"))),
      false,
      "stringContains false 2"
    )
    assertEvaluatesTo(
      evaluate(stringContains(constant(""), constant("a"))),
      false,
      "stringContains false 3"
    )
  }
}
