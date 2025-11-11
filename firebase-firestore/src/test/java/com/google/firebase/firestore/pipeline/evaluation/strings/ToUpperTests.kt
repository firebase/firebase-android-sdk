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

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.Expression.Companion.toUpper
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
internal class ToUpperTests {

  @Test
  fun toUpper_mirror() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(toUpper(testCase.input)), "toUpper(${'$'}{testCase.name})")
    }
  }

  @Test
  fun toUpper_onLowercaseString() {
    val expr = toUpper(constant("foo"))
    assertEvaluatesTo(evaluate(expr), encodeValue("FOO"), "toUpper('foo')")
  }

  @Test
  fun toUpper_onLatinChar() {
    val expr = toUpper(constant("ÿ"))
    assertEvaluatesTo(evaluate(expr), encodeValue("Ÿ"), "toUpper('ÿ')")
  }

  @Test
  fun toUpper_onGreekChars() {
    val expr = toUpper(constant("αβδ"))
    assertEvaluatesTo(evaluate(expr), encodeValue("ΑΒΔ"), "toUpper('αβδ')")
  }

  @Test
  fun toUpper_onCyrillicChars() {
    val expr = toUpper(constant("чжд"))
    assertEvaluatesTo(evaluate(expr), encodeValue("ЧЖД"), "toUpper('чжд')")
  }

  @Test
  fun toUpper_onChineseChars() {
    val expr = toUpper(constant("宋体"))
    assertEvaluatesTo(evaluate(expr), encodeValue("宋体"), "toUpper('宋体')")
  }

  @Test
  fun toUpper_onUppercaseString() {
    val expr = toUpper(constant("FOO"))
    assertEvaluatesTo(evaluate(expr), encodeValue("FOO"), "toUpper('FOO')")
  }

  @Test
  fun toUpper_onMixedCaseString() {
    val expr = toUpper(constant("fOobAR"))
    assertEvaluatesTo(evaluate(expr), encodeValue("FOOBAR"), "toUpper('fOobAR')")
  }

  @Test
  fun toUpper_onEmptyString() {
    val expr = toUpper(constant(""))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "toUpper('')")
  }

  @Test
  fun toUpper_onLowercaseBytes() {
    val expr = toUpper(constant(Blob.fromByteString(ByteString.copyFromUtf8("foo"))))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Blob.fromByteString(ByteString.copyFromUtf8("FOO"))),
      "toUpper(blob('foo'))"
    )
  }

  @Test
  fun toUpper_onUppercaseBytes() {
    val expr = toUpper(constant(Blob.fromByteString(ByteString.copyFromUtf8("FOO"))))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Blob.fromByteString(ByteString.copyFromUtf8("FOO"))),
      "toUpper(blob('FOO'))"
    )
  }

  @Test
  fun toUpper_onMixedCaseBytes() {
    val expr = toUpper(constant(Blob.fromByteString(ByteString.copyFromUtf8("fOobAR"))))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Blob.fromByteString(ByteString.copyFromUtf8("FOOBAR"))),
      "toUpper(blob('fOobAR'))"
    )
  }

  @Test
  fun toUpper_onBytesWithNonAscii() {
    val nonAscii = Blob.fromByteString(ByteString.fromHex("F9FAFBFC"))
    val expr = toUpper(constant(nonAscii))
    assertEvaluatesTo(evaluate(expr), encodeValue(nonAscii), "toUpper(blob(non-ascii))")
  }

  @Test
  fun toUpper_onBytesWithNonAsciiAndAscii() {
    val mixedBytes =
      Blob.fromByteString(ByteString.copyFromUtf8("foOBaR").concat(ByteString.fromHex("F9FAFBFC")))
    val expectedBytes =
      Blob.fromByteString(ByteString.copyFromUtf8("FOOBAR").concat(ByteString.fromHex("F9FAFBFC")))
    val expr = toUpper(constant(mixedBytes))
    assertEvaluatesTo(evaluate(expr), encodeValue(expectedBytes), "toUpper(blob(mixed))")
  }

  @Test
  fun toUpper_onEmptyBytes() {
    val expr = toUpper(constant(Blob.fromByteString(ByteString.EMPTY)))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Blob.fromByteString(ByteString.EMPTY)),
      "toUpper(blob())"
    )
  }

  @Test
  fun toUpper_onNull() {
    val expr = toUpper(nullValue())
    assertEvaluatesToNull(evaluate(expr), "toUpper(null)")
  }

  @Test
  fun toUpper_onUnsupportedType() {
    val expr = toUpper(constant(1))
    assertEvaluatesToError(
      evaluate(expr),
      "The function to_upper(...) requires `String | Bytes` but got `INT`"
    )
  }
}
