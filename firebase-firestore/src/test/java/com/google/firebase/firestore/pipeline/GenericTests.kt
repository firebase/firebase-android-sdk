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

package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.concat
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.protobuf.ByteString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenericTests {
  @Test
  fun `stringConcat_withMultipleStrings`() {
    val expr = concat(constant("hello"), constant(" "), constant("world"))
    assertEvaluatesTo(evaluate(expr), "hello world", "stringConcat_withMultipleStrings")
  }

  @Test
  fun `stringConcat_withEmptyString`() {
    val expr = concat(constant("hello"), constant(""), constant("world"))
    assertEvaluatesTo(evaluate(expr), "helloworld", "stringConcat_withEmptyString")
  }

  @Test
  fun `string_null_string_isNull`() {
    val expr = concat(constant("hello"), nullValue(), constant("world"))
    assertEvaluatesToNull(evaluate(expr), "string_null_string_isNull")
  }

  @Test
  fun `string_unset_string_isError`() {
    val expr = concat(constant("hello"), field("non-existent"), constant("world"))
    assertEvaluatesToNull(evaluate(expr), "string_unset_string_isError")
  }

  @Test
  fun `bytesConcat_withMultipleByteStrings`() {
    val expr =
      concat(
        constant(ByteString.copyFromUtf8("hello").toByteArray()),
        constant(ByteString.copyFromUtf8(" ").toByteArray()),
        constant(ByteString.copyFromUtf8("world").toByteArray())
      )
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(ByteString.copyFromUtf8("hello world").toByteArray()),
      "bytesConcat_withMultipleByteStrings"
    )
  }

  @Test
  fun `bytesConcat_withEmptyByteString`() {
    val expr =
      concat(
        constant(ByteString.copyFromUtf8("hello").toByteArray()),
        constant(ByteString.EMPTY.toByteArray()),
        constant(ByteString.copyFromUtf8("world").toByteArray())
      )
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(ByteString.copyFromUtf8("helloworld").toByteArray()),
      "bytesConcat_withEmptyByteString"
    )
  }

  @Test
  fun `bytesConcat_withNull`() {
    val expr =
      concat(
        constant(ByteString.copyFromUtf8("hello").toByteArray()),
        nullValue(),
        constant(ByteString.copyFromUtf8("world").toByteArray())
      )
    assertEvaluatesToNull(evaluate(expr), "bytesConcat_withNull")
  }

  @Test
  fun `mixedTypes_stringAndBytes_throwsError`() {
    val expr = concat(constant("hello"), constant(ByteString.copyFromUtf8("world").toByteArray()))
    assertEvaluatesToError(evaluate(expr), "mixedTypes_stringAndBytes_throwsError")
  }

  @Test
  fun `mixedTypes_stringAndLong_throwsError`() {
    val expr = concat(constant("hello"), constant(123L))
    assertEvaluatesToError(evaluate(expr), "mixedTypes_stringAndLong_throwsError")
  }

  @Test
  fun `mixedTypes_bytesAndLong_throwsError`() {
    val expr = concat(constant(ByteString.copyFromUtf8("hello").toByteArray()), constant(123L))
    assertEvaluatesToError(evaluate(expr), "mixedTypes_bytesAndLong_throwsError")
  }

  @Test
  fun `arrayConcat_withMultipleArrays`() {
    val expr = concat(array(1L, 2L), array(3L, 4L), array(5L, 6L))
    val expected = encodeValue(listOf(1L, 2L, 3L, 4L, 5L, 6L).map { encodeValue(it) })
    assertEvaluatesTo(evaluate(expr), expected, "arrayConcat_withMultipleArrays")
  }

  @Test
  fun `arrayConcat_withMixedTypesInArrays`() {
    val expr = concat(array(1L, 2L), array("three", "four"))
    val expected =
      encodeValue(
        listOf(encodeValue(1L), encodeValue(2L), encodeValue("three"), encodeValue("four"))
      )
    assertEvaluatesTo(evaluate(expr), expected, "arrayConcat_withMixedTypesInArrays")
  }

  @Test
  fun `arrayConcat_withEmptyArray`() {
    val expr = concat(array(1L, 2L), array())
    val expected = encodeValue(listOf(1L, 2L).map { encodeValue(it) })
    assertEvaluatesTo(evaluate(expr), expected, "arrayConcat_withEmptyArray")
  }

  @Test
  fun `arrayConcat_withNull`() {
    val expr = concat(array(1L, 2L), nullValue(), array(3L, 4L))
    assertEvaluatesToNull(evaluate(expr), "arrayConcat_withNull")
  }

  @Test
  fun `mixedTypes_arrayAndString_throwsError`() {
    val expr = concat(array(1L, 2L), constant("hello"))
    assertEvaluatesToError(evaluate(expr), "mixedTypes_arrayAndString_throwsError")
  }

  @Test
  fun `mixedTypes_arrayAndBytes_throwsError`() {
    val expr = concat(array(1L, 2L), constant(ByteString.copyFromUtf8("world").toByteArray()))
    assertEvaluatesToError(evaluate(expr), "mixedTypes_arrayAndBytes_throwsError")
  }

  @Test
  fun `mixedTypes_arrayAndLong_throwsError`() {
    val expr = concat(array(1L, 2L), constant(123L))
    assertEvaluatesToError(evaluate(expr), "mixedTypes_arrayAndLong_throwsError")
  }

  @Test
  fun `mixedTypes_stringAndArray_throwsError`() {
    val expr = concat(constant("foo"), array(2L))
    assertEvaluatesToError(evaluate(expr), "mixedTypes_stringAndArray_throwsError")
  }

  @Test
  fun `mixedTypes_unsupportedAndNull_throwsError`() {
    val expr = concat(constant("foo"), constant(2L), constant(1L), nullValue())
    assertEvaluatesToError(evaluate(expr), "mixedTypes_unsupportedAndNull_throwsError")
  }

  @Test
  fun `mixedTypes_stringAndArrayWithNull_throwsError`() {
    val expr = concat(constant("foo"), nullValue(), array(2L))
    assertEvaluatesToError(evaluate(expr), "mixedTypes_stringAndArrayWithNull_throwsError")
  }
}
