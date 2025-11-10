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

package com.google.firebase.firestore.pipeline.evaluation.comparison

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.testutil.TestUtil

// Test data ported from Hyperstore's ComparisonFunctionTestCases.java to ensure alignment.
internal object ComparisonTestData {
  private const val MAX_DOUBLE_SAFE_INTEGER = 9007199254740992L // 2^53
  private const val MIN_DOUBLE_SAFE_INTEGER = -9007199254740992L // -2^53

  val doubleNaN = constant(Double.NaN)

  val unsetValue = Expression.field("nonexistent")

  /** Test cases for values that should be considered equal. */
  val equivalentValues: List<Pair<Expression, Expression>> =
    listOf(
      // Int / Long
      constant(1L) to constant(1),
      constant(-1L) to constant(-1),
      constant(0L) to constant(0),
      constant(Int.MAX_VALUE.toLong()) to constant(Int.MAX_VALUE),
      constant(Int.MIN_VALUE.toLong()) to constant(Int.MIN_VALUE),
      constant(-1) to constant(-1L),

      // Int / Double
      constant(1) to constant(1.0),
      constant(0) to constant(0.0),
      constant(0) to constant(-0.0),
      constant(-1) to constant(-1.0),
      constant(Int.MAX_VALUE) to constant(Int.MAX_VALUE.toDouble()),
      constant(Int.MIN_VALUE) to constant(Int.MIN_VALUE.toDouble()),

      // Long / Double
      constant(1L) to constant(1.0),
      constant(0L) to constant(0.0),
      constant(0L) to constant(-0.0),
      constant(-1L) to constant(-1.0),
      constant(MAX_DOUBLE_SAFE_INTEGER) to constant(MAX_DOUBLE_SAFE_INTEGER.toDouble()),
      constant(MIN_DOUBLE_SAFE_INTEGER) to constant(MIN_DOUBLE_SAFE_INTEGER.toDouble()),
      constant(0L) to constant(-0.0),
      constant(-0.0) to constant(0.0),

      // NaN
      doubleNaN to doubleNaN,

      // NaN in lists
      array(doubleNaN) to array(doubleNaN),
      array(doubleNaN, doubleNaN) to array(doubleNaN, doubleNaN),
      array(doubleNaN, constant(1L)) to array(doubleNaN, constant(1L)),
      array(constant(1L), doubleNaN) to array(constant(1L), doubleNaN),
      array(doubleNaN, nullValue()) to array(doubleNaN, nullValue()),
      array(nullValue(), doubleNaN) to array(nullValue(), doubleNaN),

      // NaN in maps
      map(mapOf("a" to nullValue(), "b" to doubleNaN)) to
        map(mapOf("a" to nullValue(), "b" to doubleNaN)),
      map(mapOf("a" to doubleNaN, "b" to nullValue())) to
        map(mapOf("a" to doubleNaN, "b" to nullValue())),

      // Null
      nullValue() to nullValue(),
      unsetValue to unsetValue,

      // Null in Lists
      array(nullValue()) to array(nullValue()),
      array(nullValue(), nullValue()) to array(nullValue(), nullValue()),
      array(nullValue(), constant(1L)) to array(nullValue(), constant(1L)),
      array(constant(1L), nullValue()) to array(constant(1L), nullValue()),

      // Null in maps
      map(mapOf("a" to nullValue())) to map(mapOf("a" to nullValue())),
      map(mapOf("a" to nullValue(), "b" to nullValue())) to
        map(mapOf("a" to nullValue(), "b" to nullValue())),
      map(mapOf("a" to constant(1L), "b" to nullValue())) to
        map(mapOf("a" to constant(1L), "b" to nullValue())),
      map(mapOf("a" to nullValue(), "b" to constant(1L))) to
        map(mapOf("a" to nullValue(), "b" to constant(1L))),

      // Empty / simple collections
      array() to array(),
      array(constant(1L), constant(2L)) to array(constant(1L), constant(2L)),
      map(emptyMap()) to map(emptyMap()),
      map(mapOf("a" to constant(1L))) to map(mapOf("a" to constant(1L))),

      // Deep fuzzed equality
      array(constant(2L)) to array(constant(2.0)),
      map(mapOf("a" to constant(2.0))) to map(mapOf("a" to constant(2L))),
      map(mapOf("foo" to constant(1L), "bar" to constant(42.0))) to
        map(mapOf("bar" to constant(42L), "foo" to constant(1.0))),

      // Bytes
      constant(TestUtil.blob()) to constant(TestUtil.blob()),
      constant(TestUtil.blob(0x66, 0x6f, 0x6f)) to
        constant(TestUtil.blob(0x66, 0x6f, 0x6f)), // "foo"

      // Strings
      constant("") to constant(""),
      constant("foo") to constant("foo"),

      // Booleans
      constant(true) to constant(true),
      constant(false) to constant(false),

      // Geo Points
      constant(GeoPoint(10.0, 20.0)) to constant(GeoPoint(10.0, 20.0)),
      constant(GeoPoint(-10.0, -20.0)) to constant(GeoPoint(-10.0, -20.0)),

      // Entity References
      constant(TestUtil.ref("c2/doc1")) to constant(TestUtil.ref("c2/doc1")),
    )

  /** Test cases for values that should be considered not equal, ordered from lesser to greater. */
  val unequalValues: List<Pair<Expression, Expression>> =
    listOf(
      // Boolean Comparison
      constant(false) to constant(true),

      // Numeric Value Comparison
      constant(Double.NEGATIVE_INFINITY) to constant(-Double.MAX_VALUE),
      constant(-Double.MAX_VALUE) to constant(Long.MIN_VALUE),
      constant(Long.MIN_VALUE) to constant(-2L),
      constant(-2L) to constant(-1),
      constant(-1) to constant(-0.5),
      constant(-0.5) to constant(-0.0),
      constant(0L) to constant(Double.MIN_VALUE),
      constant(0L) to constant(java.lang.Double.MIN_NORMAL),
      constant(-0.0) to constant(0.5),
      constant(0.5) to constant(1L),
      constant(1L) to constant(1.1),
      constant(1L) to constant(2),
      constant(2) to constant(Long.MAX_VALUE),
      constant(Long.MAX_VALUE) to constant(Double.MAX_VALUE),
      constant(Double.MAX_VALUE) to constant(Double.POSITIVE_INFINITY),

      // Timestamp Comparison
      constant(Timestamp(-62_135_596_800, 0)) to constant(Timestamp(0, 0)), // MIN_VALUE vs EPOCH
      constant(Timestamp(0, 0)) to
        constant(Timestamp(253402300799, 999999999)), // EPOCH vs MAX_VALUE
      constant(Timestamp(-42, 0)) to constant(Timestamp(-41, 0)),
      constant(Timestamp(0, 0)) to constant(Timestamp(0, 10000)),
      constant(Timestamp(42, 0)) to constant(Timestamp(42, 10000)),

      // GeoPoint Comparison
      constant(GeoPoint(-87.0, -92.0)) to constant(GeoPoint(-87.0, 0.0)),
      constant(GeoPoint(-87.0, 0.0)) to constant(GeoPoint(-87.0, 42.0)),
      constant(GeoPoint(-87.0, 42.0)) to constant(GeoPoint(0.0, -92.0)),
      constant(GeoPoint(0.0, -92.0)) to constant(GeoPoint(0.0, 0.0)),
      constant(GeoPoint(0.0, 0.0)) to constant(GeoPoint(0.0, 42.0)),
      constant(GeoPoint(0.0, 42.0)) to constant(GeoPoint(42.0, -92.0)),
      constant(GeoPoint(42.0, -92.0)) to constant(GeoPoint(42.0, 0.0)),
      constant(GeoPoint(42.0, 0.0)) to constant(GeoPoint(42.0, 42.0)),

      // String Comparison
      constant("") to constant("abc"),
      constant("abc") to constant("santé"),
      constant("a") to constant("aa"),

      // Byte Comparison
      constant(TestUtil.blob()) to constant(TestUtil.blob(0, 2, 56, 42)),
      constant(TestUtil.blob(2, 26)) to constant(TestUtil.blob(2, 26, 31)),

      // EntityRef Comparison
      // This is a simplified version.
      constant(TestUtil.ref("foo/bar")) to constant(TestUtil.ref("foo/baz")),
      constant(TestUtil.ref("foo/bar/qux/a")) to constant(TestUtil.ref("foo/bar/qux/b")),
      constant(TestUtil.ref("foo/bar")) to constant(TestUtil.ref("foo/bar/baz/foo")),

      // Array Comparison
      array() to array(constant(true), constant(15L)),
      array(constant(1L)) to array(constant(2L)),
      array(constant(1L), constant(2L)) to array(constant(2L)),
      array(constant(1L), constant(2L), constant(3L)) to array(constant(2L), constant(1L)),
      array(map(mapOf("a" to constant(1L)))) to array(map(mapOf("a" to constant(2L)))),
      array(constant(1L)) to array(constant(1L), constant(2L)),
      array(nullValue()) to array(nullValue(), constant(1L)),
      array(nullValue()) to array(nullValue(), nullValue()),
      array(doubleNaN) to array(doubleNaN, nullValue()),

      // Array with Null/NaN
      array(constant(1L)) to array(constant(1L), doubleNaN),
      array(doubleNaN) to array(doubleNaN, doubleNaN),
      array(doubleNaN) to
        array(constant(Double.NEGATIVE_INFINITY)), // This is a cross-type comparison
      array(constant(1L), nullValue()) to array(constant(2L), nullValue()),
      array(constant(1L)) to array(constant(1L), nullValue()),
      array(nullValue()) to array(constant(1L)),

      // Map comparison
      map(mapOf("a" to constant(1L), "b" to nullValue())) to map(mapOf("b" to constant(2L))),
      map(mapOf("a" to constant(1L))) to map(mapOf("a" to constant(1L), "b" to constant(2L))),
      map(emptyMap()) to map(mapOf("a" to nullValue())),
      map(emptyMap()) to map(mapOf("a" to doubleNaN)),
      map(mapOf("a" to doubleNaN)) to map(mapOf("a" to doubleNaN, "b" to nullValue())),
      map(mapOf("a" to nullValue(), "b" to constant(1L))) to
        map(mapOf("a" to nullValue(), "b" to constant(1L), "c" to constant(2))),
      map(mapOf("a" to nullValue())) to map(mapOf("a" to nullValue(), "b" to constant(1L))),
      map(mapOf("a" to nullValue(), "b" to nullValue())) to
        map(mapOf("a" to nullValue(), "b" to doubleNaN)),
      map(mapOf("a" to nullValue(), "b" to doubleNaN)) to
        map(mapOf("a" to nullValue(), "b" to constant("foo"))),
      map(mapOf("a" to nullValue(), "b" to constant(1L))) to
        map(mapOf("a" to nullValue(), "b" to constant(2L))),
    )

  /** Test cases for comparing different types, ordered by Firestore's type hierarchy. */
  val crossTypeValues: List<Pair<Expression, Expression>> =
    listOf(
      // unset < null
      unsetValue to nullValue(),

      // Null < Boolean
      nullValue() to constant(false),

      // Boolean < NaN
      constant(true) to doubleNaN,

      // NaN < Numeric
      doubleNaN to constant(Double.NEGATIVE_INFINITY),

      // Numeric < Timestamp
      constant(Double.POSITIVE_INFINITY) to constant(Timestamp(-62_135_596_800, 0)),

      // Timestamp < String
      constant(Timestamp(253_402, 999_999_999)) to constant(""),

      // String < ByteString
      constant("foo") to constant(TestUtil.blob()),

      // ByteString < EntityRef
      constant(TestUtil.blob(1, 2, 3)) to constant(TestUtil.ref("foo/bar")),

      // EntityRef < GeoPoint
      constant(TestUtil.ref("foo/bar")) to constant(GeoPoint(-90.0, -180.0)),

      // GeoPoint < Array
      constant(GeoPoint(90.0, 180.0)) to array(),

      // Array < Map
      array(constant("foo"), constant("bar")) to map(emptyMap()),
    )

  // A collection of all values for testing against null, NaN, or errors.
  val allValues: List<Expression> =
    (equivalentValues.flatMap { (a, b) -> listOf(a, b) } +
        unequalValues.flatMap { (a, b) -> listOf(a, b) } +
        crossTypeValues.flatMap { (a, b) -> listOf(a, b) })
      .distinct()
}
