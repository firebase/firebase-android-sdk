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

// use
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.firestore.model.Values.NULL_VALUE
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.array // For the helper & direct
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayConcat
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAll
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayGet
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayLength
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayReverse
import com.google.firebase.firestore.pipeline.Expression.Companion.constant // For the helper
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.join
import com.google.firebase.firestore.pipeline.Expression.Companion.map // For map literals
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue // For the helper
import com.google.firestore.v1.Value
import com.google.protobuf.ByteString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArrayTests {
  // --- ArrayContainsAll Tests ---
  @Test
  fun `arrayContainsAll - contains all`() {
    val arrayToSearch = array("1", 42L, true, "additional", "values", "in", "array")
    val valuesToFind = array("1", 42L, true)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll basic true case")
  }

  @Test
  fun `arrayContainsAll - does not contain all`() {
    val arrayToSearch = array("1", 42L, true)
    val valuesToFind = array("1", 99L)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAll basic false case")
  }

  @Test
  fun `arrayContainsAll - equivalent numerics`() {
    val arrayToSearch = array(42L, true, "additional", "values", "in", "array")
    val valuesToFind = array(42.0, true)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll equivalent numerics")
  }

  @Test
  fun `arrayContainsAll - array to search is empty`() {
    val arrayToSearch = array()
    val valuesToFind = array(42.0, true)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAll empty array to search")
  }

  @Test
  fun `arrayContainsAll - search value is empty`() {
    val arrayToSearch = array(42.0, true)
    val valuesToFind = array()
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll empty search values")
  }

  @Test
  fun `arrayContainsAll - search value is NaN`() {
    val arrayToSearch = array(Double.NaN, 42.0)
    val valuesToFind = array(Double.NaN)
    // Firestore/backend behavior: NaN comparisons are always false.
    // arrayContainsAll uses standard equality which means NaN == NaN is false.
    // If arrayToSearch contains NaN and valuesToFind contains NaN, it won't find it.
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAll with NaN in search values")
  }

  @Test
  fun `arrayContainsAll - search value has duplicates`() {
    val arrayToSearch = array(true, "hi")
    val valuesToFind = array(true, true, true)
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll with duplicate search values")
  }

  @Test
  fun `arrayContainsAll - array to search is empty and search value is empty`() {
    val arrayToSearch = array()
    val valuesToFind = array()
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll both empty")
  }

  @Test
  fun `arrayContainsAll - large number of elements`() {
    val elements = (1..500).map { it.toLong() }
    // Use the statically imported 'array' directly here as it takes List<Expression>
    // The elements.map { constant(it) } is correct as Expression.array(List<Expression>) expects
    // Expression elements
    val arrayToSearch = array(elements.map { constant(it) })
    val valuesToFind = array(elements.map { constant(it) })
    val expr = arrayContainsAll(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAll large number of elements")
  }

  // --- ArrayContainsAny Tests ---
  @Test
  fun `arrayContainsAny - value found in array`() {
    val arrayToSearch = array(42L, "matang", true)
    val valuesToFind = array("matang", false)
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny value found")
  }

  @Test
  fun `arrayContainsAny - equivalent numerics`() {
    val arrayToSearch = array(42L, "matang", true)
    val valuesToFind = array(42.0, 2L)
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny equivalent numerics")
  }

  @Test
  fun `arrayContainsAny - values not found in array`() {
    val arrayToSearch = array(42L, "matang", true)
    val valuesToFind = array(99L, "false")
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContainsAny values not found")
  }

  @Test
  fun `arrayContainsAny - both input type is array`() {
    val arrayToSearch = array(array(1L, 2L, 3L), array(4L, 5L, 6L), array(7L, 8L, 9L))
    val valuesToFind = array(array(1L, 2L, 3L), array(4L, 5L, 6L))
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContainsAny nested arrays")
  }

  @Test
  fun `arrayContainsAny - search is null returns null`() {
    val arrayToSearch = array(null, 1L, "matang", true)
    val valuesToFind = array(nullValue()) // Searching for a null
    val expr = arrayContainsAny(arrayToSearch, valuesToFind)
    // Firestore/backend behavior: null comparisons return null.
    assertEvaluatesToNull(evaluate(expr), "arrayContainsAny search for null")
  }

  @Test
  fun `arrayContainsAny - array is not array type returns error`() {
    val expr = arrayContainsAny(constant("matang"), array("matang", false))
    assertEvaluatesToError(evaluate(expr), "arrayContainsAny first arg not array")
  }

  @Test
  fun `arrayContainsAny - search is not array type returns error`() {
    val expr = arrayContainsAny(array("matang", false), constant("matang"))
    assertEvaluatesToError(evaluate(expr), "arrayContainsAny second arg not array")
  }

  @Test
  fun `arrayContainsAny - array not found returns error`() {
    val expr = arrayContainsAny(field("not-exist"), array("matang", false))
    // Accessing a non-existent field results in UNSET, which then causes an error in
    // arrayContainsAny
    assertEvaluatesToError(evaluate(expr), "arrayContainsAny field not-exist for array")
  }

  @Test
  fun `arrayContainsAny - search not found returns error`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContainsAny(arrayToSearch, field("not-exist"))
    // Accessing a non-existent field results in UNSET, which then causes an error in
    // arrayContainsAny
    assertEvaluatesToError(evaluate(expr), "arrayContainsAny field not-exist for search values")
  }

  // --- ArrayContains Tests ---
  @Test
  fun `arrayContains - value found in array`() {
    val expr = arrayContains(array("hello", "world"), constant("hello"))
    assertEvaluatesTo(evaluate(expr), true, "arrayContains value found")
  }

  @Test
  fun `arrayContains - value not found in array`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContains(arrayToSearch, constant(4L))
    assertEvaluatesTo(evaluate(expr), false, "arrayContains value not found")
  }

  @Test
  fun `arrayContains - equivalent numerics`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContains(arrayToSearch, constant(42.0))
    assertEvaluatesTo(evaluate(expr), true, "arrayContains equivalent numerics")
  }

  @Test
  fun `arrayContains - both input type is array`() {
    val arrayToSearch = array(array(1L, 2L, 3L), array(4L, 5L, 6L), array(7L, 8L, 9L))
    val valueToFind = array(1L, 2L, 3L)
    val expr = arrayContains(arrayToSearch, valueToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContains nested arrays")
  }

  @Test
  fun `arrayContains - search value is null returns null`() {
    val arrayToSearch = array(null, 1L, "matang", true)
    val expr = arrayContains(arrayToSearch, nullValue())
    // Firestore/backend behavior: null comparisons return null.
    assertEvaluatesToNull(evaluate(expr), "arrayContains search for null")
  }

  @Test
  fun `arrayContains - search value is null empty values array returns null`() {
    val expr = arrayContains(array(), nullValue())
    // Firestore/backend behavior: null comparisons return null.
    assertEvaluatesToNull(evaluate(expr), "arrayContains search for null in empty array")
  }

  @Test
  fun `arrayContains - search value is map`() {
    val arrayToSearch = array(123L, mapOf("foo" to 123L), mapOf("bar" to 42L), mapOf("foo" to 42L))
    val valueToFind = map(mapOf("foo" to 42L)) // Use Expression.map directly
    val expr = arrayContains(arrayToSearch, valueToFind)
    assertEvaluatesTo(evaluate(expr), true, "arrayContains search for map")
  }

  @Test
  fun `arrayContains - search value is NaN`() {
    val arrayToSearch = array(Double.NaN, "foo")
    val valueToFind = constant(Double.NaN)
    // Firestore/backend behavior: NaN comparisons are always false.
    val expr = arrayContains(arrayToSearch, valueToFind)
    assertEvaluatesTo(evaluate(expr), false, "arrayContains search for NaN")
  }

  @Test
  fun `arrayContains - array to search is not array type returns error`() {
    val expr = arrayContains(constant("matang"), constant("values"))
    assertEvaluatesToError(evaluate(expr), "arrayContains first arg not array")
  }

  @Test
  fun `arrayContains - array to search not found returns error`() {
    val expr = arrayContains(field("not-exist"), constant("matang"))
    // Accessing a non-existent field results in UNSET, which then causes an error in arrayContains
    assertEvaluatesToError(evaluate(expr), "arrayContains field not-exist for array")
  }

  @Test
  fun `arrayContains - array to search is empty returns false`() {
    val expr = arrayContains(array(), constant("matang"))
    assertEvaluatesTo(evaluate(expr), false, "arrayContains empty array")
  }

  @Test
  fun `arrayContains - search value reference not found returns error`() {
    val arrayToSearch = array(42L, "matang", true)
    val expr = arrayContains(arrayToSearch, field("not-exist"))
    // Accessing a non-existent field for the search value results in UNSET.
    // arrayContains then attempts to compare with UNSET, which is an error.
    assertEvaluatesToError(evaluate(expr), "arrayContains field not-exist for search value")
  }

  // --- ArrayLength Tests ---
  @Test
  fun `arrayLength - length`() {
    val expr = arrayLength(array("1", 42L, true))
    val result = evaluate(expr)
    assertWithMessage("arrayLength basic").that(result.isSuccess).isTrue()
    assertWithMessage("arrayLength basic value").that(result.value).isEqualTo(encodeValue(3L))
  }

  @Test
  fun `arrayLength - empty array`() {
    val expr = arrayLength(array())
    val result = evaluate(expr)
    assertWithMessage("arrayLength empty").that(result.isSuccess).isTrue()
    assertWithMessage("arrayLength empty value").that(result.value).isEqualTo(encodeValue(0L))
  }

  @Test
  fun `arrayLength - array with duplicate elements`() {
    val expr = arrayLength(array(true, true))
    val result = evaluate(expr)
    assertWithMessage("arrayLength duplicates").that(result.isSuccess).isTrue()
    assertWithMessage("arrayLength duplicates value").that(result.value).isEqualTo(encodeValue(2L))
  }

  @Test
  fun `arrayLength - not array type returns error`() {
    assertEvaluatesToError(evaluate(arrayLength(constant("notAnArray"))), "arrayLength string")
    assertEvaluatesToError(evaluate(arrayLength(constant(123L))), "arrayLength long")
    assertEvaluatesToError(evaluate(arrayLength(constant(true))), "arrayLength boolean")
    assertEvaluatesToError(evaluate(arrayLength(map(mapOf("a" to 1)))), "arrayLength map")
  }

  // --- ArrayGet Tests ---
  private data class ArrayGetTestCase(
    val array: Expression,
    val index: Expression,
    val expected: EvaluateResult,
    val description: String
  )

  @Test
  fun `arrayGet - general cases`() {
    val testCases =
      listOf(
        // Positive indexes
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(0),
          EvaluateResultValue(encodeValue("a")),
          "positive index 0"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(1),
          EvaluateResultValue(encodeValue("b")),
          "positive index 1"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(2),
          EvaluateResultValue(encodeValue("c")),
          "positive index 2"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(3),
          EvaluateResultValue(encodeValue("d")),
          "positive index 3"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(4),
          EvaluateResultUnset,
          "positive index out of bounds"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(0L),
          EvaluateResultValue(encodeValue("a")),
          "positive long index 0"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(1L),
          EvaluateResultValue(encodeValue("b")),
          "positive long index 1"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(2L),
          EvaluateResultValue(encodeValue("c")),
          "positive long index 2"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(3L),
          EvaluateResultValue(encodeValue("d")),
          "positive long index 3"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(4L),
          EvaluateResultUnset,
          "positive long index out of bounds"
        ),

        // Negative indexes
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-1),
          EvaluateResultValue(encodeValue("d")),
          "negative index -1"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-2),
          EvaluateResultValue(encodeValue("c")),
          "negative index -2"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-3),
          EvaluateResultValue(encodeValue("b")),
          "negative index -3"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-4),
          EvaluateResultValue(encodeValue("a")),
          "negative index -4"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-5),
          EvaluateResultUnset,
          "negative index out of bounds"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-1L),
          EvaluateResultValue(encodeValue("d")),
          "negative long index -1"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-2L),
          EvaluateResultValue(encodeValue("c")),
          "negative long index -2"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-3L),
          EvaluateResultValue(encodeValue("b")),
          "negative long index -3"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-4L),
          EvaluateResultValue(encodeValue("a")),
          "negative long index -4"
        ),
        ArrayGetTestCase(
          array("a", "b", "c", "d"),
          constant(-5L),
          EvaluateResultUnset,
          "negative long index out of bounds"
        ),

        // Far out of bounds indexes
        ArrayGetTestCase(array(), constant(0), EvaluateResultUnset, "empty array"),
        ArrayGetTestCase(array(), constant(0L), EvaluateResultUnset, "empty array long index"),
        ArrayGetTestCase(array(), constant(42), EvaluateResultUnset, "empty array far out"),
        ArrayGetTestCase(
          array(),
          constant(-42),
          EvaluateResultUnset,
          "empty array far out negative"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(42L),
          EvaluateResultUnset,
          "far positive index"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(-42L),
          EvaluateResultUnset,
          "far negative index"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(Long.MAX_VALUE),
          EvaluateResultUnset,
          "max long index"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(Long.MIN_VALUE),
          EvaluateResultUnset,
          "min long index"
        ),
      )

    for (testCase in testCases) {
      val expr = arrayGet(testCase.array, testCase.index)
      val result = evaluate(expr)
      assertWithMessage("arrayGet - ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayGet - invalid array types`() {
    val testCases =
      listOf(
        ArrayGetTestCase(field("nonexistent"), constant(0), EvaluateResultUnset, "array is UNSET"),
        ArrayGetTestCase(nullValue(), constant(0), EvaluateResultUnset, "array is NULL"),
        ArrayGetTestCase(map(mapOf("0" to 123L)), constant(0), EvaluateResultUnset, "array is MAP"),
        ArrayGetTestCase(map(mapOf()), constant(0), EvaluateResultUnset, "array is empty MAP"),
        ArrayGetTestCase(constant("foo"), constant(0), EvaluateResultUnset, "array is STRING"),
        ArrayGetTestCase(constant(2), constant(0), EvaluateResultUnset, "array is INT"),
        ArrayGetTestCase(constant(2.0), constant(0), EvaluateResultUnset, "array is DOUBLE"),
      )
    for (testCase in testCases) {
      val expr = arrayGet(testCase.array, testCase.index)
      val result = evaluate(expr)
      assertWithMessage("arrayGet - ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  @Test
  fun `arrayGet - invalid index types`() {
    val testCases =
      listOf(
        // Invalid index with a valid array.
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(0.0),
          EvaluateResultError,
          "index is DOUBLE"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(-0.0),
          EvaluateResultError,
          "index is -0.0"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant(1.5),
          EvaluateResultError,
          "index is FLOAT"
        ),
        ArrayGetTestCase(
          array("a", "b", "c"),
          field("nonexistent"),
          EvaluateResultError,
          "index is UNSET"
        ),
        ArrayGetTestCase(array("a", "b", "c"), nullValue(), EvaluateResultError, "index is NULL"),
        ArrayGetTestCase(
          array("a", "b", "c"),
          constant("foo"),
          EvaluateResultError,
          "index is STRING"
        ),
        ArrayGetTestCase(array("a", "b", "c"), array(1), EvaluateResultError, "index is ARRAY"),
        ArrayGetTestCase(
          array("a", "b", "c"),
          map(mapOf("foo" to 1L)),
          EvaluateResultError,
          "index is MAP"
        ),

        // Invalid index with an invalid array.
        ArrayGetTestCase(
          nullValue(),
          constant(0.0),
          EvaluateResultError,
          "NULL array, DOUBLE index"
        ),
        ArrayGetTestCase(
          nullValue(),
          constant(-0.0),
          EvaluateResultError,
          "NULL array, -0.0 index"
        ),
        ArrayGetTestCase(
          nullValue(),
          constant(1.5),
          EvaluateResultError,
          "NULL array, FLOAT index"
        ),
        ArrayGetTestCase(
          nullValue(),
          field("nonexistent"),
          EvaluateResultError,
          "NULL array, UNSET index"
        ),
        ArrayGetTestCase(nullValue(), nullValue(), EvaluateResultError, "NULL array, NULL index"),
        ArrayGetTestCase(
          nullValue(),
          constant("foo"),
          EvaluateResultError,
          "NULL array, STRING index"
        ),
        ArrayGetTestCase(nullValue(), array(1), EvaluateResultError, "NULL array, ARRAY index"),
        ArrayGetTestCase(
          nullValue(),
          map(mapOf("foo" to 1L)),
          EvaluateResultError,
          "NULL array, MAP index"
        ),
      )

    for (testCase in testCases) {
      val expr = arrayGet(testCase.array, testCase.index)
      val result = evaluate(expr)
      assertWithMessage("arrayGet - ${testCase.description}")
        .that(result)
        .isEqualTo(testCase.expected)
    }
  }

  // --- ArrayReverse Tests ---
  @Test
  fun `arrayReverse - one element`() {
    val expr = arrayReverse(array(42L))
    val result = evaluate(expr)
    assertWithMessage("arrayReverse one element success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(42L).map { encodeValue(it) })
    assertWithMessage("arrayReverse one element value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayReverse - duplicate elements`() {
    val expr = arrayReverse(array(1L, 2L, 2L, 3L))
    val result = evaluate(expr)
    assertWithMessage("arrayReverse duplicate elements success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(3L, 2L, 2L, 1L).map { encodeValue(it) })
    assertWithMessage("arrayReverse duplicate elements value")
      .that(result.value)
      .isEqualTo(expected)
  }

  @Test
  fun `arrayReverse - mixed types`() {
    val input = array("1", 42L, true)
    val expr = arrayReverse(input)
    val result = evaluate(expr)
    assertWithMessage("arrayReverse mixed types success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(encodeValue(true), encodeValue(42L), encodeValue("1")))
    assertWithMessage("arrayReverse mixed types value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayReverse - large array`() {
    val elements = (1..500).map { it.toLong() }
    val arrayToReverse = array(elements.map { constant(it) })
    val expr = arrayReverse(arrayToReverse)
    val result = evaluate(expr)
    assertWithMessage("arrayReverse large array success").that(result.isSuccess).isTrue()
    val expected = encodeValue(elements.reversed().map { encodeValue(it) })
    assertWithMessage("arrayReverse large array value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayReverse - not array type returns error`() {
    assertEvaluatesToError(evaluate(arrayReverse(constant("notAnArray"))), "arrayReverse string")
    assertEvaluatesToError(evaluate(arrayReverse(constant(123L))), "arrayReverse long")
    assertEvaluatesToError(evaluate(arrayReverse(constant(true))), "arrayReverse boolean")
    assertEvaluatesToError(evaluate(arrayReverse(map(mapOf("a" to 1)))), "arrayReverse map")
  }

  // --- ArrayConcat Tests ---
  @Test
  fun `arrayConcat - two arrays`() {
    val expr = arrayConcat(array(1L, 2L), array(3L, 4L))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat two arrays success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(1L, 2L, 3L, 4L).map { encodeValue(it) })
    assertWithMessage("arrayConcat two arrays value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayConcat - three arrays`() {
    val expr = arrayConcat(array(1L, 2L), array(3L, 4L), array(5L, 6L))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat three arrays success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(1L, 2L, 3L, 4L, 5L, 6L).map { encodeValue(it) })
    assertWithMessage("arrayConcat three arrays value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayConcat - with empty arrays`() {
    val expr = arrayConcat(array(), array(1L, 2L), array())
    val result = evaluate(expr)
    assertWithMessage("arrayConcat with empty arrays success").that(result.isSuccess).isTrue()
    val expected = encodeValue(listOf(1L, 2L).map { encodeValue(it) })
    assertWithMessage("arrayConcat with empty arrays value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayConcat - with mixed types`() {
    val expr = arrayConcat(array(1L, "a"), array(true, 3.0))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat with mixed types success").that(result.isSuccess).isTrue()
    val expected =
      encodeValue(listOf(encodeValue(1L), encodeValue("a"), encodeValue(true), encodeValue(3.0)))
    assertWithMessage("arrayConcat with mixed types value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayConcat - with null input returns null`() {
    val expr = arrayConcat(array(1L, 2L), nullValue(), array(3L, 4L))
    val result = evaluate(expr)
    assertEvaluatesToNull(result, "arrayConcat with null input")
  }

  @Test
  fun `arrayConcat - with non array returns error`() {
    val expr = arrayConcat(array(1L, 2L), constant("not an array"))
    val result = evaluate(expr)
    assertEvaluatesToError(result, "arrayConcat with non array")
  }

  @Test
  fun `arrayConcat - with NaN`() {
    val expr = arrayConcat(array(1L, Double.NaN), array(3L, 4L))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat with NaN success").that(result.isSuccess).isTrue()
    val expected =
      encodeValue(
        listOf(encodeValue(1L), encodeValue(Double.NaN), encodeValue(3L), encodeValue(4L))
      )
    assertWithMessage("arrayConcat with NaN value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayConcat - with nested arrays`() {
    val nestedArray1 = array(1L, 2L)
    val nestedArray2 = array(3L, 4L)
    val expr = arrayConcat(array(nestedArray1), array(nestedArray2))
    val result = evaluate(expr)
    assertWithMessage("arrayConcat with nested arrays success").that(result.isSuccess).isTrue()
    val expected =
      encodeValue(
        listOf(
          encodeValue(listOf(1L, 2L).map { encodeValue(it) }),
          encodeValue(listOf(3L, 4L).map { encodeValue(it) })
        )
      )
    assertWithMessage("arrayConcat with nested arrays value").that(result.value).isEqualTo(expected)
  }

  @Test
  fun `arrayConcat - general provider cases`() {
    // [1.0, 2, 3] and [4, Double.NaN, 6] -> [1.0, 2, 3, 4, Double.NaN, 6]
    val expr1 = arrayConcat(array(1.0, 2L, 3L), array(4L, Double.NaN, 6L))
    val result1 = evaluate(expr1)
    assertWithMessage("arrayConcat general case 1 success").that(result1.isSuccess).isTrue()
    val expected1 =
      encodeValue(
        listOf(
          encodeValue(1.0),
          encodeValue(2L),
          encodeValue(3L),
          encodeValue(4L),
          encodeValue(Double.NaN),
          encodeValue(6L)
        )
      )
    assertWithMessage("arrayConcat general case 1 value").that(result1.value).isEqualTo(expected1)

    // [] and [1, 2, 3] -> [1, 2, 3]
    val expr2 = arrayConcat(array(), array(1L, 2L, 3L))
    val result2 = evaluate(expr2)
    assertWithMessage("arrayConcat empty with non-empty success").that(result2.isSuccess).isTrue()
    val expected2 = encodeValue(listOf(1L, 2L, 3L).map { encodeValue(it) })
    assertWithMessage("arrayConcat empty with non-empty value")
      .that(result2.value)
      .isEqualTo(expected2)

    // [1, 2, 3] and [] -> [1, 2, 3]
    val expr3 = arrayConcat(array(1L, 2L, 3L), array())
    val result3 = evaluate(expr3)
    assertWithMessage("arrayConcat non-empty with empty success").that(result3.isSuccess).isTrue()
    val expected3 = encodeValue(listOf(1L, 2L, 3L).map { encodeValue(it) })
    assertWithMessage("arrayConcat non-empty with empty value")
      .that(result3.value)
      .isEqualTo(expected3)

    // [] and [] -> []
    val expr4 = arrayConcat(array(), array())
    val result4 = evaluate(expr4)
    assertWithMessage("arrayConcat two empty arrays success").that(result4.isSuccess).isTrue()
    val expected4 = encodeValue(emptyArray<Value>().asIterable())
    assertWithMessage("arrayConcat two empty arrays value").that(result4.value).isEqualTo(expected4)

    // [null] and ["d", "l", "c"] -> [null, "d", "l", "c"]
    val expr5 = arrayConcat(array(nullValue()), array("d", "l", "c"))
    val result5 = evaluate(expr5)
    assertWithMessage("arrayConcat with array containing null success")
      .that(result5.isSuccess)
      .isTrue()
    val expected5 =
      encodeValue(listOf(NULL_VALUE, encodeValue("d"), encodeValue("l"), encodeValue("c")))
    assertWithMessage("arrayConcat with array containing null value")
      .that(result5.value)
      .isEqualTo(expected5)

    // [null] and [null] -> [null, null]
    val expr6 = arrayConcat(array(nullValue()), array(nullValue()))
    val result6 = evaluate(expr6)
    assertWithMessage("arrayConcat with two arrays containing null success")
      .that(result6.isSuccess)
      .isTrue()
    val expected6 = encodeValue(listOf(NULL_VALUE, NULL_VALUE))
    assertWithMessage("arrayConcat with two arrays containing null value")
      .that(result6.value)
      .isEqualTo(expected6)
  }

  @Test
  fun `arrayConcat - with null argument returns null`() {
    val expr = arrayConcat(nullValue(), array(nullValue()))
    val result = evaluate(expr)
    assertEvaluatesToNull(result, "arrayConcat with null argument")
  }

  @Test
  fun `arrayConcat - with unset argument returns error`() {
    val expr = arrayConcat(field("non-existent"), array(1L, 2L))
    val result = evaluate(expr)
    assertEvaluatesToNull(result, "arrayConcat with unset argument")
  }

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
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(ByteString.copyFromUtf8("a,b,c").toByteArray()),
      "join_bytes"
    )
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
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(ByteString.copyFromUtf8("a,c").toByteArray()),
      "joinWithNulls_bytes"
    )
  }

  @Test
  fun `joinWithNulls_strings`() {
    val expr = join(array(nullValue(), constant("a"), nullValue(), constant("c")), constant(","))
    assertEvaluatesTo(evaluate(expr), "a,c", "joinWithNulls_strings")
  }

  @Test
  fun `joinEmptyArray_bytes`() {
    val expr = join(array(), constant(ByteString.copyFromUtf8(",").toByteArray()))
    assertEvaluatesTo(evaluate(expr), encodeValue(ByteArray(0)), "joinEmptyArray_bytes")
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
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(ByteString.copyFromUtf8("a,c").toByteArray()),
      "joinWithLeadingNull_bytes"
    )
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
    assertEvaluatesTo(
      evaluate(expr),
      encodeValue(ByteString.copyFromUtf8("a").toByteArray()),
      "joinSingleElement_bytes"
    )
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
      encodeValue(ByteString.copyFromUtf8("abc").toByteArray()),
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
    assertEvaluatesTo(evaluate(expr), encodeValue(ByteArray(0)), "joinAllNulls_bytes")
  }

  @Test
  fun `joinSingleNull_strings`() {
    val expr = join(array(nullValue()), constant(","))
    assertEvaluatesTo(evaluate(expr), "", "joinSingleNull_strings")
  }

  @Test
  fun `joinSingleNull_bytes`() {
    val expr = join(array(nullValue()), constant(ByteString.copyFromUtf8(",").toByteArray()))
    assertEvaluatesTo(evaluate(expr), encodeValue(ByteArray(0)), "joinSingleNull_bytes")
  }

  @Test
  fun `joinWithNonStringValue_strings`() {
    val expr = join(array(1L, "b"), constant(","))
    assertEvaluatesToError(evaluate(expr), "joinWithNonStringValue_strings")
  }

  @Test
  fun `joinWithNonBytesValue_bytes`() {
    val expr =
      join(
        array(constant(1L), constant(ByteString.copyFromUtf8("b").toByteArray())),
        constant(ByteString.copyFromUtf8(",").toByteArray())
      )
    assertEvaluatesToError(evaluate(expr), "joinWithNonBytesValue_bytes")
  }

  @Test
  fun `join_numberArray_returnsError`() {
    val expr = join(array(1L, 2L), constant(","))
    assertEvaluatesToError(evaluate(expr), "join_numberArray_returnsError")
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
    assertEvaluatesToError(evaluate(expr), "join_bytesArray_stringDelimiter_returnsError")
  }

  @Test
  fun `invalidDelimiterType_returnsError`() {
    val expr = join(array("a", "b"), constant(1L))
    assertEvaluatesToError(evaluate(expr), "invalidDelimiterType_returnsError")
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
    assertEvaluatesToError(evaluate(expr), "mixedTypesStringArrayBytesDelimiterReturnsError")
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
    assertEvaluatesToError(evaluate(expr), "mixedTypesBytesArrayStringDelimiterReturnsError")
  }

  @Test
  fun `invalidArrayElementType_returnsError`() {
    val expr = join(array(constant(ByteString.copyFromUtf8("a").toByteArray())), constant(","))
    assertEvaluatesToError(evaluate(expr), "invalidArrayElementType_returnsError")
  }

  @Test
  fun `nullDelimiterReturnsNull_invalidArrayElementType`() {
    val expr = join(array(constant(ByteString.copyFromUtf8("a").toByteArray())), nullValue())
    assertEvaluatesToNull(evaluate(expr), "nullDelimiterReturnsNull_invalidArrayElementType")
  }

  @Test
  fun `errorHasPrecedenceOverNull_invalidDelimiter`() {
    val expr = join(nullValue(), constant(1L))
    assertEvaluatesToError(evaluate(expr), "errorHasPrecedenceOverNull_invalidDelimiter")
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
