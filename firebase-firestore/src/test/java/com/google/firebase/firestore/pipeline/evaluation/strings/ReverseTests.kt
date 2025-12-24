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
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.reverse
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import com.google.protobuf.ByteString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ReverseTests {

  // --- Reverse Tests ---

  @Test
  fun reverse_mirror() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(reverse(testCase.input)), "reverse(${testCase.name})")
    }
  }

  @Test
  fun reverse_onSimpleString() {
    val expr = reverse(constant("foobar"))
    assertEvaluatesTo(evaluate(expr), encodeValue("raboof"), "reverse on simple string")
  }

  @Test
  fun reverse_onSingleLengthString() {
    val expr = reverse(constant("t"))
    assertEvaluatesTo(evaluate(expr), encodeValue("t"), "reverse on single length string")
  }

  @Test
  fun reverse_onMultiCodePointGrapheme_breaksGrapheme() {
    // Since we only support code-point level support, multi-code point graphemes are treated as two
    // separate characters.
    val expr = reverse(constant("🖖🏻"))
    val expected = String(charArrayOf(0xD83C.toChar(), 0xDFFB.toChar())) + "🖖"
    assertEvaluatesTo(evaluate(expr), encodeValue(expected), "reverse on multi-codepoint grapheme")
  }

  @Test
  fun reverse_onComposedCharacter_treatedAsSingleCharacter() {
    val expr = reverse(constant("ü"))
    assertEvaluatesTo(evaluate(expr), encodeValue("ü"), "reverse on composed character")
  }

  @Test
  fun reverse_onDecomposedCharacter_treatedAsSeparateCharacters() {
    val umlaut = String(charArrayOf(0x0308.toChar()))
    val decomposedChar = "u" + umlaut
    val expr = reverse(constant(decomposedChar))
    assertEvaluatesTo(evaluate(expr), encodeValue(umlaut + "u"), "reverse on decomposed character")
  }

  @Test
  fun reverse_onEmptyString() {
    val expr = reverse(constant(""))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "reverse on empty string")
  }

  @Test
  fun reverse_onStringWithNonAscii() {
    val expr = reverse(constant("é🦆🖖🌎"))
    assertEvaluatesTo(evaluate(expr), encodeValue("🌎🖖🦆é"), "reverse on string with non-ascii")
  }

  @Test
  fun reverse_onStringWithAsciiAndNonAscii() {
    val expr = reverse(constant("é🦆foo🖖b🌎ar"))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue("ra🌎b🖖oof🦆é"),
      "reverse on string with ascii and non-ascii"
    )
  }

  @Test
  fun reverse_onBytes() {
    val expr = reverse(constant(ByteString.copyFromUtf8("foo").toByteArray()))
    val expected = com.google.firebase.firestore.Blob.fromByteString(ByteString.copyFromUtf8("oof"))
    assertEvaluatesTo(evaluate(expr), encodeValue(expected), "reverse on bytes")
  }

  @Test
  fun reverse_onBytesWithNonAsciiAndAscii() {
    val nonAscii = ByteString.copyFromUtf8("foOBaR").concat(ByteString.fromHex("F9FAFBFC"))
    val expr = reverse(constant(nonAscii.toByteArray()))
    val expectedBytes = ByteString.fromHex("FCFBFAF9").concat(ByteString.copyFromUtf8("RaBOof"))
    val expected = com.google.firebase.firestore.Blob.fromByteString(expectedBytes)
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(expected),
      "reverse on bytes with non-ascii and ascii"
    )
  }

  @Test
  fun reverse_onEmptyBytes() {
    val expr = reverse(constant(ByteString.EMPTY.toByteArray()))
    val expected = com.google.firebase.firestore.Blob.fromByteString(ByteString.EMPTY)
    assertEvaluatesTo(evaluate(expr), encodeValue(expected), "reverse on empty bytes")
  }

  @Test
  fun reverse_onSingleByte() {
    val expr = reverse(constant(ByteString.copyFromUtf8("a").toByteArray()))
    val expected = com.google.firebase.firestore.Blob.fromByteString(ByteString.copyFromUtf8("a"))
    assertEvaluatesTo(evaluate(expr), encodeValue(expected), "reverse on single byte")
  }

  @Test
  fun reverse_onUnsupportedType() {
    val expr = reverse(constant(1L))
    assertEvaluatesToError(
      evaluate(expr),
      "The function string_reverse(...) requires `String | Bytes` but got `LONG`"
    )
  }

  @Test
  fun reverse_onBoolean() {
    val expr = reverse(constant(true))
    assertEvaluatesToError(
      evaluate(expr),
      "The function string_reverse(...) requires `String | Bytes` but got `BOOLEAN`"
    )
  }

  @Test
  fun reverse_onDouble() {
    val expr = reverse(constant(1.0))
    assertEvaluatesToError(
      evaluate(expr),
      "The function string_reverse(...) requires `String | Bytes` but got `DOUBLE`"
    )
  }

  @Test
  fun reverse_onMap() {
    val expr = reverse(Expression.map(mapOf()))
    assertEvaluatesToError(
      evaluate(expr),
      "The function string_reverse(...) requires `String | Bytes` but got `MAP`"
    )
  }
}
