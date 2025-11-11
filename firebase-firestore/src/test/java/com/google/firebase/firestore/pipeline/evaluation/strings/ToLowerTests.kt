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
import com.google.firebase.firestore.pipeline.Expression.Companion.toLower
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import com.google.protobuf.ByteString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ToLowerTests {

  @Test
  fun toLower_mirror() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(toLower(testCase.input)), "toLower(${testCase.name})")
    }
  }

  @Test
  fun toLower_onLowercaseString() {
    val expr = toLower(constant("foo"))
    assertEvaluatesTo(evaluate(expr), encodeValue("foo"), "toLower('foo')")
  }

  @Test
  fun toLower_onLatinChar() {
    val expr = toLower(constant("Ÿ"))
    assertEvaluatesTo(evaluate(expr), encodeValue("ÿ"), "toLower('Ÿ')")
  }

  @Test
  fun toLower_onGreekChars() {
    val expr = toLower(constant("Δ"))
    assertEvaluatesTo(evaluate(expr), encodeValue("δ"), "toLower('Δ')")
  }

  @Test
  fun toLower_onCyrillicChars() {
    val expr = toLower(constant("ЧЖД"))
    assertEvaluatesTo(evaluate(expr), encodeValue("чжд"), "toLower('ЧЖД')")
  }

  @Test
  fun toLower_onChineseChars() {
    val expr = toLower(constant("宋体"))
    assertEvaluatesTo(evaluate(expr), encodeValue("宋体"), "toLower('宋体')")
  }

  @Test
  fun toLower_onUppercaseString() {
    val expr = toLower(constant("FOO"))
    assertEvaluatesTo(evaluate(expr), encodeValue("foo"), "toLower('FOO')")
  }

  @Test
  fun toLower_onMixedCaseString() {
    val expr = toLower(constant("fOobAR"))
    assertEvaluatesTo(evaluate(expr), encodeValue("foobar"), "toLower('fOobAR')")
  }

  @Test
  fun toLower_onEmptyString() {
    val expr = toLower(constant(""))
    assertEvaluatesTo(evaluate(expr), encodeValue(""), "toLower('')")
  }

  @Test
  fun toLower_onStringWithNonAscii() {
    val expr = toLower(constant("é🦆"))
    assertEvaluatesTo(evaluate(expr), encodeValue("é🦆"), "toLower('é🦆')")
  }

  @Test
  fun toLower_onLowercaseBytes() {
    val expr = toLower(constant(Blob.fromByteString(ByteString.copyFromUtf8("foo"))))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Blob.fromByteString(ByteString.copyFromUtf8("foo"))),
      "toLower(blob('foo'))"
    )
  }

  @Test
  fun toLower_onUppercaseBytes() {
    val expr = toLower(constant(Blob.fromByteString(ByteString.copyFromUtf8("FOO"))))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Blob.fromByteString(ByteString.copyFromUtf8("foo"))),
      "toLower(blob('FOO'))"
    )
  }

  @Test
  fun toLower_onMixedCaseBytes() {
    val expr = toLower(constant(Blob.fromByteString(ByteString.copyFromUtf8("fOobAR"))))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Blob.fromByteString(ByteString.copyFromUtf8("foobar"))),
      "toLower(blob('fOobAR'))"
    )
  }

  @Test
  fun toLower_onBytesWithNonAscii() {
    val nonAscii = Blob.fromByteString(ByteString.fromHex("F9FAFBFC"))
    val expr = toLower(constant(nonAscii))
    assertEvaluatesTo(evaluate(expr), encodeValue(nonAscii), "toLower(blob(non-ascii))")
  }

  @Test
  fun toLower_onBytesWithNonAsciiAndAscii() {
    val mixedBytes =
      Blob.fromByteString(ByteString.copyFromUtf8("foOBaR").concat(ByteString.fromHex("F9FAFBFC")))
    val expectedBytes =
      Blob.fromByteString(ByteString.copyFromUtf8("foobar").concat(ByteString.fromHex("F9FAFBFC")))
    val expr = toLower(constant(mixedBytes))
    assertEvaluatesTo(evaluate(expr), encodeValue(expectedBytes), "toLower(blob(mixed))")
  }

  @Test
  fun toLower_onEmptyBytes() {
    val expr = toLower(constant(Blob.fromByteString(ByteString.EMPTY)))
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(Blob.fromByteString(ByteString.EMPTY)),
      "toLower(blob())"
    )
  }
}
