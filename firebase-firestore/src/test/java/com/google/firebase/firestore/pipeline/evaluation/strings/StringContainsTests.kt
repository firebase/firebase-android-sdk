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
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class StringContainsTests {

  @Test
  fun stringContains_mirrorError() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = stringContains(left, right)
      assertEvaluatesToNull(evaluate(expr), "stringContains($name)")
    }
  }

  @Test
  fun stringContains_value_nonString_isError() {
    val expr = stringContains(constant(42L), constant("value"))
    assertEvaluatesToError(evaluate(expr), "stringContains(42L, \"value\")")
  }

  @Test
  fun stringContains_subString_nonString_isError() {
    val expr = stringContains(constant("search space"), constant(42L))
    assertEvaluatesToError(evaluate(expr), "stringContains(\"search space\", 42L)")
  }

  @Test
  fun stringContains_evaluatesToTrue() {
    val testCases =
      mapOf(
        "abc" to "c",
        "abc" to "bc",
        "abc" to "abc",
        "abc" to "",
        "" to "",
        "☃☃☃" to "☃",
      )

    for ((value, substring) in testCases) {
      val expr = stringContains(constant(value), constant(substring))
      assertEvaluatesTo(evaluate(expr), true, "stringContains(\"$value\", \"$substring\")")
    }
  }

  @Test
  fun stringContains_evaluatesToFalse() {
    val testCases =
      mapOf(
        "abc" to "abcd",
        "abc" to "d",
        "" to "a",
        "" to "abcde",
      )

    for ((value, substring) in testCases) {
      val expr = stringContains(constant(value), constant(substring))
      assertEvaluatesTo(evaluate(expr), false, "stringContains(\"$value\", \"$substring\")")
    }
  }
}
