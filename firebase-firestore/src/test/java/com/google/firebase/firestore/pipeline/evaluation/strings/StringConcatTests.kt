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
import com.google.firebase.firestore.pipeline.Expression.Companion.stringConcat
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class StringConcatTests {

  // --- StrConcat Tests ---
  @Test
  fun stringConcat_multipleStringChildren_returnsCombination() {
    val expr = stringConcat(constant("foo"), constant(" "), constant("bar"))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue("foo bar"), "stringConcat(\"foo\", \" \", \"bar\")")
  }

  @Test
  fun stringConcat_multipleNonStringChildren_returnsError() {
    // stringConcat should only accept strings or expressions that evaluate to strings.
    // The Kotlin `stringConcat` vararg is `Any`, then converted via `toArrayOfExprOrConstant`.
    // `evaluateStrConcat` checks if all resolved params are strings.
    val expr = stringConcat(constant("foo"), constant(42L), constant("bar"))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "stringConcat(\"foo\", 42L, \"bar\")")
  }

  @Test
  fun stringConcat_multipleCalls() {
    val expr = stringConcat(constant("foo"), constant(" "), constant("bar"))
    assertEvaluatesTo(evaluate(expr), encodeValue("foo bar"), "stringConcat call 1")
    assertEvaluatesTo(evaluate(expr), encodeValue("foo bar"), "stringConcat call 2")
    assertEvaluatesTo(evaluate(expr), encodeValue("foo bar"), "stringConcat call 3")
  }

  @Test
  fun stringConcat_largeNumberOfInputs() {
    val argCount = 500
    val args = Array(argCount) { constant("a") }
    val expectedResult = "a".repeat(argCount)
    val expr = stringConcat(args.first(), *args.drop(1).toTypedArray()) // Pass varargs correctly
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(expectedResult), "stringConcat large number of inputs")
  }

  @Test
  fun stringConcat_largeStrings() {
    val a500 = "a".repeat(500)
    val b500 = "b".repeat(500)
    val c500 = "c".repeat(500)
    val expr = stringConcat(constant(a500), constant(b500), constant(c500))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(a500 + b500 + c500), "stringConcat large strings")
  }

  @Test
  fun stringConcat_mirrorError() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = stringConcat(left, right)
      assertEvaluatesToNull(evaluate(expr), "stringConcat($name)")
    }
  }
}
