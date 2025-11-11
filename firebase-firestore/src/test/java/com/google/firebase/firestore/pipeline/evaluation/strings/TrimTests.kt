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
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import com.google.firebase.firestore.testutil.TestUtil.blob
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TrimTests {

  // --- Trim Tests ---

  @Test
  fun trim_mirror() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(trim(testCase.input)), "trim(${'$'}{testCase.name})")
    }
  }

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
  fun trim_extendedWhitespace() {
    val expr = trim(constant("\t\n\r\n\u000cfoobar\t\n\r\n\u000c"))
    assertEvaluatesTo(evaluate(expr), encodeValue("foobar"), "trim_extendedWhitespace")
  }

  @Test
  fun trim_singleLengthString() {
    val expr = trim(constant("t"))
    assertEvaluatesTo(evaluate(expr), encodeValue("t"), "trim('t')")
  }

  @Test
  fun trim_singleLengthSpace() {
    val expr = trim(constant(" "))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "trim(' ')")
  }

  @Test
  fun trim_singleUnicodeString() {
    val expr = trim(constant("🖖🏻"))
    assertEvaluatesTo(evaluate(expr), encodeValue("🖖🏻"), "trim('🖖🏻')")
  }

  @Test
  fun trim_bytes_noWhitespace() {
    val expr = trim(constant(blob(102, 111, 111, 98, 97, 114))) // "foobar"
    assertEvaluatesTo(evaluate(expr), encodeValue(blob(102, 111, 111, 98, 97, 114)), "trim(bytes)")
  }

  @Test
  fun trim_bytes_withWhitespace() {
    val expr =
      trim(
        constant(blob(32, 32, 46, 32, 102, 111, 111, 98, 97, 114, 32, 46, 32, 32))
      ) // "  . foobar .  "
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(blob(46, 32, 102, 111, 111, 98, 97, 114, 32, 46)),
      "trim(bytes_whitespace)"
    )
  }

  @Test
  fun trim_bytes_withExtendedWhitespace() {
    val expr =
      trim(
        constant(blob(9, 10, 13, 10, 12, 102, 111, 111, 98, 97, 114, 9, 10, 13, 10, 12))
      ) // "\t\n\r\n\ffoobar\t\n\r\n\f"
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(blob(102, 111, 111, 98, 97, 114)),
      "trim(bytes_extended_whitespace)"
    )
  }

  @Test
  fun trim_emptyBytes() {
    val expr = trim(constant(blob()))
    assertEvaluatesTo(evaluate(expr), encodeValue(blob()), "trim(empty_bytes)")
  }

  @Test
  fun trim_singleByte() {
    val expr = trim(constant(blob(97))) // "a"
    assertEvaluatesTo(evaluate(expr), encodeValue(blob(97)), "trim(single_byte)")
  }

  @Test
  fun trim_bytesAllWhitespace() {
    val expr = trim(constant(blob(32))) // " "
    assertEvaluatesTo(evaluate(expr), encodeValue(blob()), "trim(bytes_all_whitespace)")
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
