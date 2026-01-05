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

package com.google.firebase.firestore.pipeline.evaluation.array

import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.join
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.protobuf.ByteString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JoinTests {
  // --- Join Tests ---
  @Test
  fun `join_bytes`() {
    val expr =
      join(
        array(
          constant(ByteString.copyFromUtf8("a").toByteArray()),
          constant(ByteString.copyFromUtf8("b").toByteArray()),
          constant(ByteString.copyFromUtf8("c").toByteArray())
        ),
        constant(ByteString.copyFromUtf8(",").toByteArray())
      )
    assertEvaluatesTo(evaluate(expr), encodeValue("a,b,c".toByteArray()), "join_bytes")
  }

  @Test
  fun `join_strings`() {
    val expr = join(array("a", "b", "c"), constant(","))
    assertEvaluatesTo(evaluate(expr), "a,b,c", "join_strings")
  }

  @Test
  fun `joinWithNulls_bytes`() {
    val expr =
      join(
        array(
          constant(ByteString.copyFromUtf8("a").toByteArray()),
          nullValue(),
          constant(ByteString.copyFromUtf8("c").toByteArray())
        ),
        constant(ByteString.copyFromUtf8(",").toByteArray())
      )
    assertEvaluatesTo(evaluate(expr), encodeValue("a,c".toByteArray()), "joinWithNulls_bytes")
  }

  @Test
  fun `joinWithNulls_strings`() {
    val expr = join(array(nullValue(), constant("a"), nullValue(), constant("c")), constant(","))
    assertEvaluatesTo(evaluate(expr), "a,c", "joinWithNulls_strings")
  }

  @Test
  fun `joinEmptyArray_bytes`() {
    val expr = join(array(), constant(ByteString.copyFromUtf8(",").toByteArray()))
    assertEvaluatesTo(evaluate(expr), encodeValue("".toByteArray()), "joinEmptyArray_bytes")
  }

  @Test
  fun `joinEmptyArray_strings`() {
    val expr = join(array(), constant(","))
    assertEvaluatesTo(evaluate(expr), "", "joinEmptyArray_strings")
  }

  @Test
  fun `joinWithLeadingNull_strings`() {
    val expr = join(array(nullValue(), constant("a"), constant("c")), constant(","))
    assertEvaluatesTo(evaluate(expr), "a,c", "joinWithLeadingNull_strings")
  }

  @Test
  fun `joinWithLeadingNull_bytes`() {
    val expr =
      join(
        array(
          nullValue(),
          constant(ByteString.copyFromUtf8("a").toByteArray()),
          constant(ByteString.copyFromUtf8("c").toByteArray())
        ),
        constant(ByteString.copyFromUtf8(",").toByteArray())
      )
    assertEvaluatesTo(evaluate(expr), encodeValue("a,c".toByteArray()), "joinWithLeadingNull_bytes")
  }

  @Test
  fun `joinSingleElement_strings`() {
    val expr = join(array("a"), constant(","))
    assertEvaluatesTo(evaluate(expr), "a", "joinSingleElement_strings")
  }

  @Test
  fun `joinSingleElement_bytes`() {
    val expr =
      join(
        array(constant(ByteString.copyFromUtf8("a").toByteArray())),
        constant(ByteString.copyFromUtf8(",").toByteArray())
      )
    assertEvaluatesTo(evaluate(expr), encodeValue("a".toByteArray()), "joinSingleElement_bytes")
  }

  @Test
  fun `joinWithEmptyDelimiter_strings`() {
    val expr = join(array("a", "b", "c"), constant(""))
    assertEvaluatesTo(evaluate(expr), "abc", "joinWithEmptyDelimiter_strings")
  }

  @Test
  fun `joinWithEmptyDelimiter_bytes`() {
    val expr =
      join(
        array(
          constant(ByteString.copyFromUtf8("a").toByteArray()),
          constant(ByteString.copyFromUtf8("b").toByteArray()),
          constant(ByteString.copyFromUtf8("c").toByteArray())
        ),
        constant(ByteString.EMPTY.toByteArray())
      )
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue("abc".toByteArray()),
      "joinWithEmptyDelimiter_bytes"
    )
  }

  @Test
  fun `joinAllNulls_strings`() {
    val expr = join(array(nullValue(), nullValue()), constant(","))
    assertEvaluatesTo(evaluate(expr), "", "joinAllNulls_strings")
  }

  @Test
  fun `joinAllNulls_bytes`() {
    val expr =
      join(array(nullValue(), nullValue()), constant(ByteString.copyFromUtf8(",").toByteArray()))
    assertEvaluatesTo(evaluate(expr), encodeValue("".toByteArray()), "joinAllNulls_bytes")
  }

  @Test
  fun `joinSingleNull_strings`() {
    val expr = join(array(nullValue()), constant(","))
    assertEvaluatesTo(evaluate(expr), "", "joinSingleNull_strings")
  }

  @Test
  fun `joinSingleNull_bytes`() {
    val expr = join(array(nullValue()), constant(ByteString.copyFromUtf8(",").toByteArray()))
    assertEvaluatesTo(evaluate(expr), encodeValue("".toByteArray()), "joinSingleNull_bytes")
  }

  @Test
  fun `joinWithNonStringValue_strings`() {
    val expr = join(array(1L, "b"), constant(","))
    assertEvaluatesToError(evaluate(expr), "Cannot join non-string types")
  }

  @Test
  fun `joinWithNonBytesValue_bytes`() {
    val expr =
      join(
        array(constant(1L), constant(ByteString.copyFromUtf8("b").toByteArray())),
        constant(ByteString.copyFromUtf8(",").toByteArray())
      )
    assertEvaluatesToError(evaluate(expr), "Cannot join non-string types")
  }

  @Test
  fun `join_numberArray_returnsError`() {
    val expr = join(array(1L, 2L), constant(","))
    assertEvaluatesToError(evaluate(expr), "Cannot join non-string types")
  }

  @Test
  fun `join_bytesArray_stringDelimiter_returnsError`() {
    val expr =
      join(
        array(
          constant(ByteString.copyFromUtf8("a").toByteArray()),
          constant(ByteString.copyFromUtf8("b").toByteArray())
        ),
        constant(",")
      )
    assertEvaluatesToError(evaluate(expr), "Cannot join non-string types")
  }

  @Test
  fun `invalidDelimiterType_returnsError`() {
    val expr = join(array("a", "b"), constant(1L))
    assertEvaluatesToError(
      evaluate(expr),
      "The function join(...) requires `String` but got `LONG`"
    )
  }

  @Test
  fun `nullArrayReturnsNull`() {
    val expr = join(nullValue(), constant(","))
    assertEvaluatesToNull(evaluate(expr), "nullArrayReturnsNull")
  }

  @Test
  fun `nullDelimiterReturnsNull`() {
    val expr = join(array("a", "b"), nullValue())
    assertEvaluatesToNull(evaluate(expr), "nullDelimiterReturnsNull")
  }

  @Test
  fun `mixedTypesStringArrayBytesDelimiterReturnsError`() {
    val expr = join(array("a", null, "c"), constant(ByteString.copyFromUtf8(",").toByteArray()))
    assertEvaluatesToError(evaluate(expr), "Cannot join non-string types")
  }

  @Test
  fun `mixedTypesBytesArrayStringDelimiterReturnsError`() {
    val expr =
      join(
        array(
          constant(ByteString.copyFromUtf8("a").toByteArray()),
          constant(ByteString.copyFromUtf8("b").toByteArray())
        ),
        constant(",")
      )
    assertEvaluatesToError(evaluate(expr), "Cannot join non-string types")
  }

  @Test
  fun `invalidArrayElementType_returnsError`() {
    val expr = join(array(constant(ByteString.copyFromUtf8("a").toByteArray())), constant(","))
    assertEvaluatesToError(evaluate(expr), "Cannot join non-string types")
  }

  @Test
  fun `nullDelimiterReturnsNull_invalidArrayElementType`() {
    val expr = join(array(constant(ByteString.copyFromUtf8("a").toByteArray())), nullValue())
    assertEvaluatesToNull(evaluate(expr), "nullDelimiterReturnsNull_invalidArrayElementType")
  }

  @Test
  fun `errorHasPrecedenceOverNull_invalidDelimiter`() {
    val expr = join(nullValue(), constant(1L))
    assertEvaluatesToError(
      evaluate(expr),
      "The function join(...) requires `String` but got `LONG`"
    )
  }

  @Test
  fun `errorHasPrecedenceOverNull_invalidArrayElement`() {
    val expr = join(array("a", 1L), nullValue())
    assertEvaluatesToNull(evaluate(expr), "errorHasPrecedenceOverNull_invalidArrayElement")
  }

  @Test
  fun `errorHasPrecedenceOverNull_mixedArrayElementTypes`() {
    val expr = join(array("a", constant(ByteString.copyFromUtf8("b").toByteArray())), nullValue())
    assertEvaluatesToNull(evaluate(expr), "errorHasPrecedenceOverNull_mixedArrayElementTypes")
  }
}
