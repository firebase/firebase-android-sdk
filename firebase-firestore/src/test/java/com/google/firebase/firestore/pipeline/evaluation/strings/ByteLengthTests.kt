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
import com.google.firebase.firestore.pipeline.Expression.Companion.byteLength
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ByteLengthTests {

  @Test
  fun byteLength_mirror() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(byteLength(testCase.input)), "byteLength(${testCase.name})")
    }
  }

  // --- ByteLength Tests ---
  @Test
  fun byteLength_emptyString_returnsZero() {
    val expr = byteLength(constant(""))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(0L), "byteLength(\"\")")
  }

  @Test
  fun byteLength_emptyByte_returnsZero() {
    val expr = byteLength(constant(byteArrayOf()))
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(0L), "byteLength(blob(byteArrayOf()))")
  }

  @Test
  fun byteLength_nonStringOrBytes_returnsErrorOrCorrectLength() {
    // Test with non-string/byte types - should error
    assertEvaluatesToError(evaluate(byteLength(constant(123L))), "byteLength(123L)")
    assertEvaluatesToError(evaluate(byteLength(constant(true))), "byteLength(true)")

    // Test with a valid Blob
    val bytesForBlob = byteArrayOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte())
    val exprAsBlob =
      byteLength(constant(bytesForBlob)) // Renamed exprBlob to avoid conflict if it was a var
    val resultBlob = evaluate(exprAsBlob)
    assertEvaluatesTo(resultBlob, encodeValue(3L), "byteLength(blob(1,2,3))")

    // Test with a valid ByteArray
    val bytesArray = byteArrayOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte())
    val exprByteArray = byteLength(constant(bytesArray))
    val resultByteArray = evaluate(exprByteArray)
    assertEvaluatesTo(resultByteArray, encodeValue(4L), "byteLength(byteArrayOf(1,2,3,4))")
  }

  @Test
  fun byteLength_highSurrogateOnly() {
    // UTF-8 encoding of a lone high surrogate is invalid.
    // U+D83C (high surrogate) incorrectly encoded as 3 bytes in ISO-8859-1
    // This test assumes the underlying string processing correctly identifies invalid UTF-8
    val expr = byteLength(constant("\uD83C")) // Java string with lone high surrogate
    val result = evaluate(expr)
    // Depending on implementation, this might error or give a byte length
    // Based on C++ test, it should be an error if strict UTF-8 validation is done.
    // The Kotlin `evaluateByteLength` uses `string.toByteArray(Charsets.UTF_8).size`
    // which for a lone surrogate might throw an exception or produce replacement characters.
    // Let's assume it should error if the input string is not valid UTF-8 representable.
    // Java's toByteArray(UTF_8) replaces unpaired surrogates with '?', which is 1 byte.
    // This behavior differs from the C++ test's expectation of an error.
    // For now, let's match the likely Java behavior. '?' is one byte.
    // UPDATE: The C++ test `\xED\xA0\xBC` is an invalid UTF-8 sequence for U+D83C.
    // Java's `"\uD83C".toByteArray(StandardCharsets.UTF_8)` results in `[0x3f]` (the replacement
    // char '?')
    // So length is 1. The C++ test is more about the validity of the byte sequence itself.
    // The current Kotlin `evaluateByteLength` directly converts string to UTF-8 bytes.
    // If the string itself contains invalid sequences from a C++ perspective,
    // the Java/Kotlin layer might "fix" it before byte conversion.
    // The C++ test `SharedConstant(u"\xED\xA0\xBC")` passes an invalid byte sequence.
    // We can't directly do that with `constant("string")` in Kotlin.
    // We'd have to construct a Blob from invalid bytes if we wanted to test that.
    // For `byteLength(constant("string"))`, if the string is representable, it will give a length.
    // Let's assume the goal is to test the `byteLength` function with string inputs.
    // A lone surrogate in a Java string is valid at the string level.
    // Its UTF-8 representation is a replacement character.
    assertEvaluatesTo(result, encodeValue(1L), "byteLength(\"\\uD83C\") - lone high surrogate")
  }

  @Test
  fun byteLength_lowSurrogateOnly() {
    // Similar to high surrogate, Java's toByteArray(UTF_8) replaces with '?'
    val expr = byteLength(constant("\uDF53")) // Java string with lone low surrogate
    val result = evaluate(expr)
    assertEvaluatesTo(result, encodeValue(1L), "byteLength(\"\\uDF53\") - lone low surrogate")
  }

  @Test
  fun byteLength_lowAndHighSurrogateSwapped() {
    // "\uDF53\uD83C" - two replacement characters '??'
    val expr = byteLength(constant("\uDF53\uD83C"))
    val result = evaluate(expr)
    assertEvaluatesTo(
      result,
      encodeValue(2L),
      "byteLength(\"\\uDF53\\uD83C\") - swapped surrogates"
    )
  }

  @Test
  fun byteLength_ascii() {
    assertEvaluatesTo(evaluate(byteLength(constant("abc"))), encodeValue(3L), "byteLength(\"abc\")")
    assertEvaluatesTo(
      evaluate(byteLength(constant("1234"))),
      encodeValue(4L),
      "byteLength(\"1234\")"
    )
    assertEvaluatesTo(
      evaluate(byteLength(constant("abc123!@"))),
      encodeValue(8L),
      "byteLength(\"abc123!@\")"
    )
  }

  @Test
  fun byteLength_largeString() {
    val largeA = "a".repeat(1500)
    val largeAbBuilder = StringBuilder(3000)
    for (i in 0 until 1500) {
      largeAbBuilder.append("ab")
    }
    val largeAb = largeAbBuilder.toString()

    assertEvaluatesTo(
      evaluate(byteLength(constant(largeA))),
      encodeValue(1500L),
      "byteLength(largeA)"
    )
    assertEvaluatesTo(
      evaluate(byteLength(constant(largeAb))),
      encodeValue(3000L),
      "byteLength(largeAb)"
    )
  }

  @Test
  fun byteLength_twoBytesPerCharacter() {
    // UTF-8: é=2, ç=2, ñ=2, ö=2, ü=2 => 10 bytes
    val str = "éçñöü" // Each char is 2 bytes in UTF-8
    assertEvaluatesTo(
      evaluate(byteLength(constant(str))),
      encodeValue(10L),
      "byteLength(\"éçñöü\")"
    )

    val bytesTwo =
      byteArrayOf(
        0xc3.toByte(),
        0xa9.toByte(),
        0xc3.toByte(),
        0xa7.toByte(),
        0xc3.toByte(),
        0xb1.toByte(),
        0xc3.toByte(),
        0xb6.toByte(),
        0xc3.toByte(),
        0xbc.toByte()
      )
    assertEvaluatesTo(
      evaluate(byteLength(constant(bytesTwo))),
      encodeValue(10L),
      "byteLength(blob for \"éçñöü\")"
    )
  }

  @Test
  fun byteLength_threeBytesPerCharacter() {
    // UTF-8: 你=3, 好=3, 世=3, 界=3 => 12 bytes
    val str = "你好世界" // Each char is 3 bytes in UTF-8
    assertEvaluatesTo(evaluate(byteLength(constant(str))), encodeValue(12L), "byteLength(\"你好世界\")")

    val bytesThree =
      byteArrayOf(
        0xe4.toByte(),
        0xbd.toByte(),
        0xa0.toByte(),
        0xe5.toByte(),
        0xa5.toByte(),
        0xbd.toByte(),
        0xe4.toByte(),
        0xb8.toByte(),
        0x96.toByte(),
        0xe7.toByte(),
        0x95.toByte(),
        0x8c.toByte()
      )
    assertEvaluatesTo(
      evaluate(byteLength(constant(bytesThree))),
      encodeValue(12L),
      "byteLength(blob for \"你好世界\")"
    )
  }

  @Test
  fun byteLength_fourBytesPerCharacter() {
    // UTF-8: 🀘=4, 🂡=4 => 8 bytes (U+1F018, U+1F0A1)
    val str = "🀘🂡" // Each char is 4 bytes in UTF-8
    assertEvaluatesTo(evaluate(byteLength(constant(str))), encodeValue(8L), "byteLength(\"🀘🂡\")")
    val bytesFour =
      byteArrayOf(
        0xF0.toByte(),
        0x9F.toByte(),
        0x80.toByte(),
        0x98.toByte(),
        0xF0.toByte(),
        0x9F.toByte(),
        0x82.toByte(),
        0xA1.toByte()
      )
    assertEvaluatesTo(
      evaluate(byteLength(constant(bytesFour))),
      encodeValue(8L),
      "byteLength(blob for \"🀘🂡\")"
    )
  }

  @Test
  fun byteLength_mixOfDifferentEncodedLengths() {
    // a=1, é=2, 好=3, 🂡=4 => 10 bytes
    val str = "aé好🂡"
    assertEvaluatesTo(
      evaluate(byteLength(constant(str))),
      encodeValue(10L),
      "byteLength(\"aé好🂡\")"
    )
    val bytesMix =
      byteArrayOf(
        0x61.toByte(),
        0xc3.toByte(),
        0xa9.toByte(),
        0xe5.toByte(),
        0xa5.toByte(),
        0xbd.toByte(),
        0xF0.toByte(),
        0x9F.toByte(),
        0x82.toByte(),
        0xA1.toByte()
      )
    assertEvaluatesTo(
      evaluate(byteLength(constant(bytesMix))),
      encodeValue(10L),
      "byteLength(blob for \"aé好🂡\")"
    )
  }
}
