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
import com.google.firebase.firestore.pipeline.Expression.Companion.substring
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.protobuf.ByteString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class SubstringTests {

  @Test
  fun substring_onString_returnsSubstring() {
    val expr = substring(constant("abc"), constant(1L), constant(2L))
    assertEvaluatesTo(evaluate(expr), encodeValue("bc"), "substring(\"abc\", 1, 2)")
  }

  @Test
  fun substring_onString_largePosition_returnsEmptyString() {
    val expr = substring(constant("abc"), constant(Long.MAX_VALUE), constant(1L))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "substring('abc', Long.MAX_VALUE, 1)")
  }

  @Test
  fun substring_onString_positionOnLast_returnsLastCharacter() {
    val expr = substring(constant("abc"), constant(2L), constant(2L))
    assertEvaluatesTo(evaluate(expr), encodeValue("c"), "substring(\"abc\", 2, 2)")
  }

  @Test
  fun substring_onString_positionPastLast_returnsEmptyString() {
    val expr = substring(constant("abc"), constant(3L), constant(2L))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "substring(\"abc\", 3, 2)")
  }

  @Test
  fun substring_onString_positionOnZero_startsFromZero() {
    val expr = substring(constant("abc"), constant(0L), constant(6L))
    assertEvaluatesTo(evaluate(expr), encodeValue("abc"), "substring(\"abc\", 0, 6)")
  }

  @Test
  fun substring_onString_oversizedLength_returnsTruncatedString() {
    val expr = substring(constant("abc"), constant(1L), constant(Long.MAX_VALUE))
    assertEvaluatesTo(evaluate(expr), encodeValue("bc"), "substring(\"abc\", 1, Long.MAX_VALUE)")
  }

  @Test
  fun substring_onString_negativePosition() {
    val expr = substring(constant("abcd"), constant(-3L), constant(2L))
    assertEvaluatesTo(evaluate(expr), encodeValue("bc"), "substring(\"abcd\", -3, 2)")
  }

  @Test
  fun substring_onString_negativePosition_startsFromLast() {
    val expr = substring(constant("abc"), constant(-1L), constant(1L))
    assertEvaluatesTo(evaluate(expr), encodeValue("c"), "substring(\"abc\", -1, 1)")
  }

  @Test
  fun substring_onCodePoints_negativePosition_startsFromLast() {
    val expr = substring(constant("㉇🀄"), constant(-1L), constant(1L))
    assertEvaluatesTo(evaluate(expr), encodeValue("🀄"), "substring(\"㉇🀄\", -1, 1)")
  }

  @Test
  fun substring_onString_maxNegativePosition_startsFromZero() {
    val expr = substring(constant("abc".toByteArray()), constant(-Long.MAX_VALUE), constant(2L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromBytes("ab".toByteArray())),
      "substring(blob(abc), -Long.MAX_VALUE, 2)"
    )
  }

  @Test
  fun substring_onString_oversizedNegativePosition_startsFromZero() {
    val expr = substring(constant("abc".toByteArray()), constant(-4L), constant(2L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromBytes("ab".toByteArray())),
      "substring(blob(abc), -4, 2)"
    )
  }

  @Test
  fun substring_onNonAsciiString() {
    val expr = substring(constant("ϖϗϠ"), constant(1L), constant(1L))
    assertEvaluatesTo(evaluate(expr), encodeValue("ϗ"), "substring(\"ϖϗϠ\", 1, 1)")
  }

  @Test
  fun substring_onCharacterDecomposition_treatedAsSeparateCharacters() {
    val umlaut = String(charArrayOf(0x0308.toChar()))
    val decomposedChar = "u" + umlaut

    // Assert that the component characters of a decomposed character are trimmed correctly.
    val expr1 = substring(constant(decomposedChar), constant(1), constant(2))
    assertEvaluatesTo(evaluate(expr1), encodeValue(umlaut), "substring(decomposed, 1, 2)")

    val expr2 = substring(constant(decomposedChar), constant(0), constant(1))
    assertEvaluatesTo(evaluate(expr2), encodeValue("u"), "substring(decomposed, 0, 1)")
  }

  @Test
  fun substring_onComposedCharacter_treatedAsSingleCharacter() {
    val expr1 = substring(constant("ü"), constant(1), constant(1))
    assertEvaluatesTo(evaluate(expr1), encodeValue(""), "substring(\"ü\", 1, 1)")

    val expr2 = substring(constant("ü"), constant(0), constant(1))
    assertEvaluatesTo(evaluate(expr2), encodeValue("ü"), "substring(\"ü\", 0, 1)")
  }

  @Test
  fun substring_mixedAsciiNonAsciiString_returnsSubstring() {
    val expr = substring(constant("aϗbϖϗϠc"), constant(1), constant(3))
    assertEvaluatesTo(evaluate(expr), encodeValue("ϗbϖ"), "substring(\"aϗbϖϗϠc\", 1, 3)")
  }

  @Test
  fun substring_mixedAsciiNonAsciiString_afterNonAscii() {
    val expr = substring(constant("aϗbϖϗϠc"), constant(4), constant(2))
    assertEvaluatesTo(evaluate(expr), encodeValue("ϗϠ"), "substring(\"aϗbϖϗϠc\", 4, 2)")
  }

  @Test
  fun substring_onString_negativeLength_throws() {
    val expr = substring(constant("abc".toByteArray()), constant(1L), constant(-1L))
    assertEvaluatesToError(evaluate(expr), "substring with negative length")
  }

  @Test
  fun substring_onBytes_returnsSubstring() {
    val expr = substring(constant("abc".toByteArray()), constant(1L), constant(2L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromBytes("bc".toByteArray())),
      "substring(blob(abc), 1, 2)"
    )
  }

  @Test
  fun substring_onBytes_returnsInvalidUTF8Substring() {
    val expr =
      substring(
        constant(ByteString.fromHex("F9FAFB").toByteArray()),
        constant(1L),
        constant(Long.MAX_VALUE)
      )
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromByteString(ByteString.fromHex("FAFB"))),
      "substring invalid utf8"
    )
  }

  @Test
  fun substring_onCodePoints_returnsSubstring() {
    val codePoints = "🌎㉇🀄⛹"
    val expr = substring(constant(codePoints), constant(1L), constant(2L))
    assertEvaluatesTo(evaluate(expr), encodeValue("㉇🀄"), "substring(\"🌎㉇🀄⛹\", 1, 2)")
  }

  @Test
  fun substring_onCodePoints_andAscii_returnsSubstring() {
    val codePoints = "🌎㉇foo🀄bar⛹"
    val expr = substring(constant(codePoints), constant(4L), constant(4L))
    assertEvaluatesTo(evaluate(expr), encodeValue("o🀄ba"), "substring(\"🌎㉇foo🀄bar⛹\", 4, 4)")
  }

  @Test
  fun substring_onCodePoints_oversizedLength_returnsSubstring() {
    val codePoints = "🌎㉇🀄⛹"
    val expr = substring(constant(codePoints), constant(1L), constant(6L))
    assertEvaluatesTo(evaluate(expr), encodeValue("㉇🀄⛹"), "substring(\"🌎㉇🀄⛹\", 1, 6)")
  }

  @Test
  fun substring_onCodePoints_startingAtZero_returnsSubstring() {
    val codePoints = "🌎㉇🀄⛹"
    val expr = substring(constant(codePoints), constant(0L), constant(3L))
    assertEvaluatesTo(evaluate(expr), encodeValue("🌎㉇🀄"), "substring(\"🌎㉇🀄⛹\", 0, 3)")
  }

  @Test
  fun substring_onSingleCodePointGrapheme_doesNotSplit() {
    val expr1 = substring(constant("🖖"), constant(0L), constant(1L))
    assertEvaluatesTo(evaluate(expr1), encodeValue("🖖"), "substring(\"🖖\", 0, 1)")
    val expr2 = substring(constant("🖖"), constant(1L), constant(1L))
    assertEvaluatesTo(evaluate(expr2), encodeValue(""), "substring(\"🖖\", 1, 1)")
  }

  @Test
  fun substring_onMultiCodePointGrapheme_splitsGrapheme() {
    val expr1 = substring(constant("🖖🏻"), constant(0L), constant(1L))
    assertEvaluatesTo(evaluate(expr1), encodeValue("🖖"), "substring(\"🖖🏻\", 0, 1)")
    // Asserting that when the second half is split, it only returns the skin tone code point.
    val expr2 = substring(constant("🖖🏻"), constant(1L), constant(1L))
    val skinTone = String(charArrayOf(0xD83C.toChar(), 0xDFFB.toChar()))
    assertEvaluatesTo(evaluate(expr2), encodeValue(skinTone), "substring(\"🖖🏻\", 1, 1)")
  }

  @Test
  fun substring_onBytes_largePosition_returnsEmptyString() {
    val expr = substring(constant("abc".toByteArray()), constant(Long.MAX_VALUE), constant(3L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromByteString(ByteString.EMPTY)),
      "substring(blob(abc), Long.MAX_VALUE, 3)"
    )
  }

  @Test
  fun substring_onBytes_positionOnLast_returnsLastByte() {
    val expr = substring(constant("abc".toByteArray()), constant(2L), constant(2L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromBytes("c".toByteArray())),
      "substring(blob(abc), 2, 2)"
    )
  }

  @Test
  fun substring_onBytes_positionPastLast_returnsEmptyByteString() {
    val expr = substring(constant("abc".toByteArray()), constant(3L), constant(2L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromByteString(ByteString.EMPTY)),
      "substring(blob(abc), 3, 2)"
    )
  }

  @Test
  fun substring_onBytes_positionOnZero_startsFromZero() {
    val expr = substring(constant("abc".toByteArray()), constant(0L), constant(6L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromBytes("abc".toByteArray())),
      "substring(blob(abc), 0, 6)"
    )
  }

  @Test
  fun substring_onBytes_negativePosition_startsFromLast() {
    val expr = substring(constant("abc".toByteArray()), constant(-1L), constant(1L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromBytes("c".toByteArray())),
      "substring(blob(abc), -1, 1)"
    )
  }

  @Test
  fun substring_onBytes_oversizedNegativePosition_startsFromZero() {
    val expr = substring(constant("abc".toByteArray()), constant(-Long.MAX_VALUE), constant(3L))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(com.google.firebase.firestore.Blob.fromBytes("abc".toByteArray())),
      "substring(blob(abc), -Long.MAX_VALUE, 3)"
    )
  }

  @Test
  fun substring_unknownValueType_returnsError() {
    val expr = substring(constant(20L), constant(4L), constant(1L))
    assertEvaluatesToError(evaluate(expr), "substring on non-string/blob")
  }

  @Test
  fun substring_unknownPositionType_returnsError() {
    val expr = substring(constant("abc"), constant("foo"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "substring with non-integer position")
  }

  @Test
  fun substring_unknownLengthType_returnsError() {
    val expr = substring(constant("abc"), constant(1L), constant("foo"))
    assertEvaluatesToError(evaluate(expr), "substring with non-integer length")
  }
}
