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

import com.google.firebase.Timestamp // For creating Timestamp instances
import com.google.firebase.firestore.GeoPoint // For creating GeoPoint instances
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.testutil.TestUtil // For test helpers like map, array, etc.

// Helper data similar to C++ ComparisonValueTestData
internal object ComparisonTestData {
  private const val MAX_LONG_EXACTLY_REPRESENTABLE_AS_DOUBLE = 1L shl 53

  private val BOOLEAN_VALUES: List<Expression> = listOf(constant(false), constant(true))

  private val NUMERIC_VALUES: List<Expression> =
    listOf(
      constant(Double.NEGATIVE_INFINITY),
      constant(-Double.MAX_VALUE),
      constant(Long.MIN_VALUE),
      constant(-MAX_LONG_EXACTLY_REPRESENTABLE_AS_DOUBLE),
      constant(-1L),
      constant(-0.5),
      constant(-Double.MIN_VALUE), // Smallest positive normal, negated
      constant(0.0), // Represents both +0.0 and -0.0 for ordering
      constant(Double.MIN_VALUE), // Smallest positive normal
      constant(0.5),
      constant(1L),
      constant(42L),
      constant(MAX_LONG_EXACTLY_REPRESENTABLE_AS_DOUBLE),
      constant(Long.MAX_VALUE),
      constant(Double.MAX_VALUE),
      constant(Double.POSITIVE_INFINITY),
      // doubleNaN is handled separately due to its comparison properties
      )

  val doubleNaN = constant(Double.NaN)

  private val TIMESTAMP_VALUES: List<Expression> =
    listOf(
      constant(Timestamp(-42, 0)),
      constant(Timestamp(-42, 42000000)),
      constant(Timestamp(0, 0)),
      constant(Timestamp(0, 42000000)),
      constant(Timestamp(42, 0)),
      constant(Timestamp(42, 42000000))
    )

  private val STRING_VALUES: List<Expression> =
    listOf(
      constant(""),
      constant("a"),
      constant("abcdefgh"),
      constant("santé"),
      constant("santé et bonheur"),
      constant("z")
    )

  private val BLOB_VALUES: List<Expression> =
    listOf(
      constant(TestUtil.blob()), // Empty
      constant(TestUtil.blob(0, 2, 56, 42)),
      constant(TestUtil.blob(2, 26)),
      constant(TestUtil.blob(2, 26, 31))
    )

  // Note: TestUtil.ref uses a default project "project" and default database "(default)"
  // So TestUtil.ref("foo/bar") becomes "projects/project/databases/(default)/documents/foo/bar"
  private val REF_VALUES: List<Expression> =
    listOf(
      constant(TestUtil.ref("foo/bar")),
      constant(TestUtil.ref("foo/bar/qux/a")),
      constant(TestUtil.ref("foo/bar/qux/bleh")),
      constant(TestUtil.ref("foo/bar/qux/hi")),
      constant(TestUtil.ref("foo/bar/tonk/a")),
      constant(TestUtil.ref("foo/baz"))
    )

  private val GEO_POINT_VALUES: List<Expression> =
    listOf(
      constant(GeoPoint(-87.0, -92.0)),
      constant(GeoPoint(-87.0, 0.0)),
      constant(GeoPoint(-87.0, 42.0)),
      constant(GeoPoint(0.0, -92.0)),
      constant(GeoPoint(0.0, 0.0)),
      constant(GeoPoint(0.0, 42.0)),
      constant(GeoPoint(42.0, -92.0)),
      constant(GeoPoint(42.0, 0.0)),
      constant(GeoPoint(42.0, 42.0))
    )

  private val ARRAY_VALUES: List<Expression> =
    listOf(
      array(),
      array(constant(true), constant(15L)),
      array(constant(1L), constant(2L)),
      array(constant(Timestamp(12, 0))),
      array(constant("foo")),
      array(constant("foo"), constant("bar")),
      array(constant(GeoPoint(0.0, 0.0))),
      array(map(emptyMap()))
    )

  private val MAP_VALUES: List<Expression> =
    listOf(
      map(emptyMap()),
      map(mapOf("ABA" to "qux")),
      map(mapOf("aba" to "hello")),
      map(mapOf("aba" to "hello", "foo" to true)),
      map(mapOf("aba" to "qux")),
      map(mapOf("foo" to "aaa"))
    )

  // Combine all comparable, non-NaN, non-Null values from the categorized lists
  // This is useful for testing against Null or NaN.
  val allSupportedComparableValues: List<Expression> =
    BOOLEAN_VALUES +
      NUMERIC_VALUES + // numericValuesForNanTest already excludes NaN
      TIMESTAMP_VALUES +
      STRING_VALUES +
      BLOB_VALUES +
      REF_VALUES +
      GEO_POINT_VALUES +
      ARRAY_VALUES +
      MAP_VALUES

  // For tests specifically about numeric comparisons against NaN
  val numericValuesForNanTest: List<Expression> = NUMERIC_VALUES // This list already excludes NaN

  // --- Dynamically generated comparison pairs based on Firestore type ordering ---
  // Type Order: Null < Boolean < Number < Timestamp < String < Blob < Reference < GeoPoint < Array
  // < Map

  private val allValueCategories: List<List<Expression>> =
    listOf(
      listOf(nullValue()), // Null first
      BOOLEAN_VALUES,
      NUMERIC_VALUES, // NaN is not in this list
      TIMESTAMP_VALUES,
      STRING_VALUES,
      BLOB_VALUES,
      REF_VALUES,
      GEO_POINT_VALUES,
      ARRAY_VALUES,
      MAP_VALUES
    )

  val equivalentValues: List<Pair<Expression, Expression>> = buildList {
    // Self-equality for all defined values (except NaN, which is special)
    allSupportedComparableValues.forEach { add(it to it) }

    // Specific numeric equivalences
    add(constant(0L) to constant(0.0))
    add(constant(1L) to constant(1.0))
    add(constant(-5L) to constant(-5.0))
    add(
      constant(MAX_LONG_EXACTLY_REPRESENTABLE_AS_DOUBLE) to
        constant(MAX_LONG_EXACTLY_REPRESENTABLE_AS_DOUBLE.toDouble())
    )

    // Map key order doesn't matter for equality
    add(map(mapOf("a" to 1L, "b" to 2L)) to map(mapOf("b" to 2L, "a" to 1L)))
  }

  val lessThanValues: List<Pair<Expression, Expression>> = buildList {
    // Intra-type comparisons
    for (category in allValueCategories) {
      for (i in 0 until category.size - 1) {
        for (j in i + 1 until category.size) {
          add(category[i] to category[j])
        }
      }
    }
  }

  val mixedTypeValues: List<Pair<Expression, Expression>> = buildList {
    val categories = allValueCategories.filter { it.isNotEmpty() }
    for (i in categories.indices) {
      for (j in i + 1 until categories.size) {
        // Only add pairs if they are not already covered by lessThan (inter-type)
        // This list is for types that are strictly non-comparable by value for <, >, <=, >= (should
        // yield false)
        // or where one is null (should yield null for <, >, <=, >=)
        val val1 = categories[i].first()
        val val2 = categories[j].first()

        // If one is null, it's a null-operand case, handled elsewhere for <, >, etc.
        // For eq/neq, null vs non-null is false/true (or null if other is also null).
        // Here, we are interested in pairs that, if not null, would typically result in 'false' for
        // relational ops.
        if (val1 != nullValue() && val2 != nullValue()) {
          add(val1 to val2)
        }
      }
    }
    // Add some specific tricky mixed types not covered by systematic generation
    add(constant(true) to constant(0L))
    add(constant(Timestamp(0, 0)) to constant("abc"))
    add(array(constant(1L)) to map(mapOf("a" to 1L)))
  }
}
