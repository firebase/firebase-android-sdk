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

import com.google.firebase.Timestamp // For creating Timestamp instances
import com.google.firebase.firestore.GeoPoint // For creating GeoPoint instances
import com.google.firebase.firestore.model.Values.NULL_VALUE
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.equal
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThan
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThanOrEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThan
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThanOrEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.testutil.TestUtil // For test helpers like map, array, etc.
import com.google.firebase.firestore.testutil.TestUtilKtx.doc // For creating MutableDocument
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

// Using RobolectricTestRunner if any Android-specific classes are indirectly used by model classes.
// Firestore model classes might depend on Android context for certain initializations.
@RunWith(RobolectricTestRunner::class)
internal class ComparisonTests {

  // --- Eq (==) Tests ---

  @Test
  fun eq_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      val result = evaluate(equal(v1, v2))
      assertEvaluatesTo(result, true, "eq(%s, %s)", v1, v2)
    }
  }

  @Test
  fun eq_lessThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      // eq(v1, v2)
      val result1 = evaluate(equal(v1, v2))
      assertEvaluatesTo(result1, false, "eq(%s, %s)", v1, v2)
      // eq(v2, v1)
      val result2 = evaluate(equal(v2, v1))
      assertEvaluatesTo(result2, false, "eq(%s, %s)", v2, v1)
    }
  }

  // GreaterThanValues can be derived from LessThanValues by swapping pairs
  @Test
  fun eq_greaterThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      // eq(greater, less)
      val result = evaluate(equal(greater, less))
      assertEvaluatesTo(result, false, "eq(%s, %s)", greater, less)
    }
  }

  @Test
  fun eq_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      val result1 = evaluate(equal(v1, v2))
      assertEvaluatesTo(result1, false, "eq(%s, %s)", v1, v2)
      val result2 = evaluate(equal(v2, v1))
      assertEvaluatesTo(result2, false, "eq(%s, %s)", v2, v1)
    }
  }

  @Test
  fun eq_nullEqualsNull_returnsNull() {
    // In SQL-like semantics, NULL == NULL is NULL, not TRUE.
    // Firestore's behavior for direct comparison of two NULL constants:
    val v1 = nullValue()
    val v2 = nullValue()
    val result = evaluate(equal(v1, v2))
    assertEvaluatesToNull(result, "eq(%s, %s)", v1, v2)
  }

  @Test
  fun eq_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      // eq(null, value)
      assertEvaluatesToNull(evaluate(equal(nullVal, value)), "eq(%s, %s)", nullVal, value)
      // eq(value, null)
      assertEvaluatesToNull(evaluate(equal(value, nullVal)), "eq(%s, %s)", value, nullVal)
    }
    // eq(null, nonExistentField)
    val nullVal = nullValue()
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(equal(nullVal, missingField)),
      "eq(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun eq_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN

    // NaN == NaN is false
    assertEvaluatesTo(evaluate(equal(nanExpr, nanExpr)), false, "eq(%s, %s)", nanExpr, nanExpr)

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(equal(nanExpr, numVal)), false, "eq(%s, %s)", nanExpr, numVal)
      assertEvaluatesTo(evaluate(equal(numVal, nanExpr)), false, "eq(%s, %s)", numVal, nanExpr)
    }

    // Compare NaN with non-numeric types
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) { // Ensure we are not re-testing NaN vs NaN or NaN vs Numeric
          assertEvaluatesTo(
            evaluate(equal(nanExpr, otherVal)),
            false,
            "eq(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(equal(otherVal, nanExpr)),
            false,
            "eq(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }

    // NaN in array
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(equal(arrayWithNaN1, arrayWithNaN2)),
      false,
      "eq(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )

    // NaN in map
    val mapWithNaN1 = map(mapOf("foo" to Double.NaN))
    val mapWithNaN2 = map(mapOf("foo" to Double.NaN))
    assertEvaluatesTo(
      evaluate(equal(mapWithNaN1, mapWithNaN2)),
      false,
      "eq(%s, %s)",
      mapWithNaN1,
      mapWithNaN2
    )
  }

  @Test
  fun eq_nullContainerEquality_various() {
    val nullArray = array(nullValue()) // Array containing a Firestore Null

    assertEvaluatesTo(evaluate(equal(nullArray, constant(1L))), false, "eq(%s, 1L)", nullArray)
    assertEvaluatesTo(
      evaluate(equal(nullArray, constant("1"))),
      false,
      "eq(%s, \\\"1\\\")",
      nullArray
    )
    assertEvaluatesToNull(
      evaluate(equal(nullArray, nullValue())),
      "eq(%s, %s)",
      nullArray,
      nullValue()
    )
    assertEvaluatesTo(
      evaluate(equal(nullArray, ComparisonTestData.doubleNaN)),
      false,
      "eq(%s, %s)",
      nullArray,
      ComparisonTestData.doubleNaN
    )
    assertEvaluatesTo(evaluate(equal(nullArray, array())), false, "eq(%s, [])", nullArray)

    val nanArray = array(constant(Double.NaN))
    assertEvaluatesToNull(evaluate(equal(nullArray, nanArray)), "eq(%s, %s)", nullArray, nanArray)

    val anotherNullArray = array(nullValue())
    assertEvaluatesToNull(
      evaluate(equal(nullArray, anotherNullArray)),
      "eq(%s, %s)",
      nullArray,
      anotherNullArray
    )

    val nullMap = map(mapOf("foo" to NULL_VALUE)) // Map containing a Firestore Null
    val anotherNullMap = map(mapOf("foo" to NULL_VALUE))
    assertEvaluatesToNull(
      evaluate(equal(nullMap, anotherNullMap)),
      "eq(%s, %s)",
      nullMap,
      anotherNullMap
    )
    assertEvaluatesTo(evaluate(equal(nullMap, map(emptyMap()))), false, "eq(%s, {})", nullMap)
  }

  @Test
  fun eq_errorHandling_returnsError() {
    val errorExpr =
      field("a.b") // Accessing a nested field that might not exist or be of wrong type
    val testDoc = doc("test/eqError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(equal(errorExpr, value), testDoc),
        "eq(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(equal(value, errorExpr), testDoc),
        "eq(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(equal(errorExpr, errorExpr), testDoc),
      "eq(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(equal(errorExpr, nullValue()), testDoc),
      "eq(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun eq_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/eqMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(equal(missingField, presentValue), testDoc),
      "eq(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(equal(presentValue, missingField), testDoc),
      "eq(%s, %s)",
      presentValue,
      missingField
    )
  }

  // --- Neq (!=) Tests ---

  @Test
  fun neq_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      val result = evaluate(notEqual(v1, v2))
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(result, "neq(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(result, false, "neq(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun neq_lessThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(notEqual(v1, v2)), true, "neq(%s, %s)", v1, v2)
      assertEvaluatesTo(evaluate(notEqual(v2, v1)), true, "neq(%s, %s)", v2, v1)
    }
  }

  @Test
  fun neq_greaterThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(notEqual(greater, less)), true, "neq(%s, %s)", greater, less)
    }
  }

  @Test
  fun neq_mixedTypeValues_returnTrue() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(notEqual(v1, v2)), "neq(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(notEqual(v2, v1)), "neq(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(notEqual(v1, v2)), true, "neq(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(notEqual(v2, v1)), true, "neq(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun neq_nullNotEqualsNull_returnsNull() {
    val v1 = nullValue()
    val v2 = nullValue()
    val result = evaluate(notEqual(v1, v2))
    assertEvaluatesToNull(result, "neq(%s, %s)", v1, v2)
  }

  @Test
  fun neq_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(notEqual(nullVal, value)), "neq(%s, %s)", nullVal, value)
      assertEvaluatesToNull(evaluate(notEqual(value, nullVal)), "neq(%s, %s)", value, nullVal)
    }
    val nullVal = nullValue()
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(notEqual(nullVal, missingField)),
      "neq(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun neq_nanComparisons_returnTrue() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(notEqual(nanExpr, nanExpr)), true, "neq(%s, %s)", nanExpr, nanExpr)

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(notEqual(nanExpr, numVal)), true, "neq(%s, %s)", nanExpr, numVal)
      assertEvaluatesTo(evaluate(notEqual(numVal, nanExpr)), true, "neq(%s, %s)", numVal, nanExpr)
    }

    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(
            evaluate(notEqual(nanExpr, otherVal)),
            true,
            "neq(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(notEqual(otherVal, nanExpr)),
            true,
            "neq(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }

    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(notEqual(arrayWithNaN1, arrayWithNaN2)),
      true,
      "neq(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )

    val mapWithNaN1 = map(mapOf("foo" to Double.NaN))
    val mapWithNaN2 = map(mapOf("foo" to Double.NaN))
    assertEvaluatesTo(
      evaluate(notEqual(mapWithNaN1, mapWithNaN2)),
      true,
      "neq(%s, %s)",
      mapWithNaN1,
      mapWithNaN2
    )
  }

  @Test
  fun neq_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/neqError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(notEqual(errorExpr, value), testDoc),
        "neq(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(notEqual(value, errorExpr), testDoc),
        "neq(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(notEqual(errorExpr, errorExpr), testDoc),
      "neq(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(notEqual(errorExpr, nullValue()), testDoc),
      "neq(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun neq_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/neqMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(notEqual(missingField, presentValue), testDoc),
      "neq(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(notEqual(presentValue, missingField), testDoc),
      "neq(%s, %s)",
      presentValue,
      missingField
    )
  }

  // --- Lt (<) Tests ---

  @Test
  fun lt_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lessThan(v1, v2)), "lt(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(evaluate(lessThan(v1, v2)), false, "lt(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun lt_lessThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      val result = evaluate(lessThan(v1, v2))
      assertEvaluatesTo(result, true, "lt(%s, %s)", v1, v2)
    }
  }

  @Test
  fun lt_greaterThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(lessThan(greater, less)), false, "lt(%s, %s)", greater, less)
    }
  }

  @Test
  fun lt_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lessThan(v1, v2)), "lt(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(lessThan(v2, v1)), "lt(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(lessThan(v1, v2)), false, "lt(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(lessThan(v2, v1)), false, "lt(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun lt_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(lessThan(nullVal, value)), "lt(%s, %s)", nullVal, value)
      assertEvaluatesToNull(evaluate(lessThan(value, nullVal)), "lt(%s, %s)", value, nullVal)
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(evaluate(lessThan(nullVal, nullVal)), "lt(%s, %s)", nullVal, nullVal)
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(lessThan(nullVal, missingField)),
      "lt(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun lt_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(lessThan(nanExpr, nanExpr)), false, "lt(%s, %s)", nanExpr, nanExpr)

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(lessThan(nanExpr, numVal)), false, "lt(%s, %s)", nanExpr, numVal)
      assertEvaluatesTo(evaluate(lessThan(numVal, nanExpr)), false, "lt(%s, %s)", numVal, nanExpr)
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(
            evaluate(lessThan(nanExpr, otherVal)),
            false,
            "lt(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(lessThan(otherVal, nanExpr)),
            false,
            "lt(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(lessThan(arrayWithNaN1, arrayWithNaN2)),
      false,
      "lt(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )
  }

  @Test
  fun lt_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/ltError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(lessThan(errorExpr, value), testDoc),
        "lt(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(lessThan(value, errorExpr), testDoc),
        "lt(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(lessThan(errorExpr, errorExpr), testDoc),
      "lt(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(lessThan(errorExpr, nullValue()), testDoc),
      "lt(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun lt_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/ltMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(lessThan(missingField, presentValue), testDoc),
      "lt(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(lessThan(presentValue, missingField), testDoc),
      "lt(%s, %s)",
      presentValue,
      missingField
    )
  }

  // --- Lte (<=) Tests ---

  @Test
  fun lte_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lessThanOrEqual(v1, v2)), "lte(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(evaluate(lessThanOrEqual(v1, v2)), true, "lte(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun lte_lessThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(lessThanOrEqual(v1, v2)), true, "lte(%s, %s)", v1, v2)
    }
  }

  @Test
  fun lte_greaterThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(greater, less)),
        false,
        "lte(%s, %s)",
        greater,
        less
      )
    }
  }

  @Test
  fun lte_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lessThanOrEqual(v1, v2)), "lte(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(lessThanOrEqual(v2, v1)), "lte(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(lessThanOrEqual(v1, v2)), false, "lte(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(lessThanOrEqual(v2, v1)), false, "lte(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun lte_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(
        evaluate(lessThanOrEqual(nullVal, value)),
        "lte(%s, %s)",
        nullVal,
        value
      )
      assertEvaluatesToNull(
        evaluate(lessThanOrEqual(value, nullVal)),
        "lte(%s, %s)",
        value,
        nullVal
      )
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(
      evaluate(lessThanOrEqual(nullVal, nullVal)),
      "lte(%s, %s)",
      nullVal,
      nullVal
    )
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(nullVal, missingField)),
      "lte(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun lte_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(
      evaluate(lessThanOrEqual(nanExpr, nanExpr)),
      false,
      "lte(%s, %s)",
      nanExpr,
      nanExpr
    )

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(nanExpr, numVal)),
        false,
        "lte(%s, %s)",
        nanExpr,
        numVal
      )
      assertEvaluatesTo(
        evaluate(lessThanOrEqual(numVal, nanExpr)),
        false,
        "lte(%s, %s)",
        numVal,
        nanExpr
      )
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(
            evaluate(lessThanOrEqual(nanExpr, otherVal)),
            false,
            "lte(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(lessThanOrEqual(otherVal, nanExpr)),
            false,
            "lte(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(lessThanOrEqual(arrayWithNaN1, arrayWithNaN2)),
      false,
      "lte(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )
  }

  @Test
  fun lte_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/lteError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(lessThanOrEqual(errorExpr, value), testDoc),
        "lte(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(lessThanOrEqual(value, errorExpr), testDoc),
        "lte(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(errorExpr, errorExpr), testDoc),
      "lte(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(errorExpr, nullValue()), testDoc),
      "lte(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun lte_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/lteMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(lessThanOrEqual(missingField, presentValue), testDoc),
      "lte(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(lessThanOrEqual(presentValue, missingField), testDoc),
      "lte(%s, %s)",
      presentValue,
      missingField
    )
  }

  // --- Gt (>) Tests ---

  @Test
  fun gt_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(greaterThan(v1, v2)), "gt(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(evaluate(greaterThan(v1, v2)), false, "gt(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun gt_lessThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThan(v1, v2)), false, "gt(%s, %s)", v1, v2)
    }
  }

  @Test
  fun gt_greaterThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(greaterThan(greater, less)), true, "gt(%s, %s)", greater, less)
    }
  }

  @Test
  fun gt_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(greaterThan(v1, v2)), "gt(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(greaterThan(v2, v1)), "gt(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(greaterThan(v1, v2)), false, "gt(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(greaterThan(v2, v1)), false, "gt(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun gt_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(greaterThan(nullVal, value)), "gt(%s, %s)", nullVal, value)
      assertEvaluatesToNull(evaluate(greaterThan(value, nullVal)), "gt(%s, %s)", value, nullVal)
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(evaluate(greaterThan(nullVal, nullVal)), "gt(%s, %s)", nullVal, nullVal)
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(greaterThan(nullVal, missingField)),
      "gt(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun gt_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(
      evaluate(greaterThan(nanExpr, nanExpr)),
      false,
      "gt(%s, %s)",
      nanExpr,
      nanExpr
    )

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(
        evaluate(greaterThan(nanExpr, numVal)),
        false,
        "gt(%s, %s)",
        nanExpr,
        numVal
      )
      assertEvaluatesTo(
        evaluate(greaterThan(numVal, nanExpr)),
        false,
        "gt(%s, %s)",
        numVal,
        nanExpr
      )
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(
            evaluate(greaterThan(nanExpr, otherVal)),
            false,
            "gt(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(greaterThan(otherVal, nanExpr)),
            false,
            "gt(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(greaterThan(arrayWithNaN1, arrayWithNaN2)),
      false,
      "gt(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )
  }

  @Test
  fun gt_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/gtError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(greaterThan(errorExpr, value), testDoc),
        "gt(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(greaterThan(value, errorExpr), testDoc),
        "gt(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(greaterThan(errorExpr, errorExpr), testDoc),
      "gt(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(greaterThan(errorExpr, nullValue()), testDoc),
      "gt(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun gt_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/gtMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(greaterThan(missingField, presentValue), testDoc),
      "gt(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(greaterThan(presentValue, missingField), testDoc),
      "gt(%s, %s)",
      presentValue,
      missingField
    )
  }

  // --- Gte (>=) Tests ---

  @Test
  fun gte_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(greaterThanOrEqual(v1, v2)), "gte(%s, %s)", v1, v2)
      } else {
        assertEvaluatesTo(evaluate(greaterThanOrEqual(v1, v2)), true, "gte(%s, %s)", v1, v2)
      }
    }
  }

  @Test
  fun gte_lessThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(greaterThanOrEqual(v1, v2)), false, "gte(%s, %s)", v1, v2)
    }
  }

  @Test
  fun gte_greaterThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(
        evaluate(greaterThanOrEqual(greater, less)),
        true,
        "gte(%s, %s)",
        greater,
        less
      )
    }
  }

  @Test
  fun gte_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(greaterThanOrEqual(v1, v2)), "gte(%s, %s)", v1, v2)
        assertEvaluatesToNull(evaluate(greaterThanOrEqual(v2, v1)), "gte(%s, %s)", v2, v1)
      } else {
        assertEvaluatesTo(evaluate(greaterThanOrEqual(v1, v2)), false, "gte(%s, %s)", v1, v2)
        assertEvaluatesTo(evaluate(greaterThanOrEqual(v2, v1)), false, "gte(%s, %s)", v2, v1)
      }
    }
  }

  @Test
  fun gte_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(
        evaluate(greaterThanOrEqual(nullVal, value)),
        "gte(%s, %s)",
        nullVal,
        value
      )
      assertEvaluatesToNull(
        evaluate(greaterThanOrEqual(value, nullVal)),
        "gte(%s, %s)",
        value,
        nullVal
      )
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(
      evaluate(greaterThanOrEqual(nullVal, nullVal)),
      "gte(%s, %s)",
      nullVal,
      nullVal
    )
    val missingField = field("nonexistent")
    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(nullVal, missingField)),
      "gte(%s, %s)",
      nullVal,
      missingField
    )
  }

  @Test
  fun gte_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(
      evaluate(greaterThanOrEqual(nanExpr, nanExpr)),
      false,
      "gte(%s, %s)",
      nanExpr,
      nanExpr
    )

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(
        evaluate(greaterThanOrEqual(nanExpr, numVal)),
        false,
        "gte(%s, %s)",
        nanExpr,
        numVal
      )
      assertEvaluatesTo(
        evaluate(greaterThanOrEqual(numVal, nanExpr)),
        false,
        "gte(%s, %s)",
        numVal,
        nanExpr
      )
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(
            evaluate(greaterThanOrEqual(nanExpr, otherVal)),
            false,
            "gte(%s, %s)",
            nanExpr,
            otherVal
          )
          assertEvaluatesTo(
            evaluate(greaterThanOrEqual(otherVal, nanExpr)),
            false,
            "gte(%s, %s)",
            otherVal,
            nanExpr
          )
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(
      evaluate(greaterThanOrEqual(arrayWithNaN1, arrayWithNaN2)),
      false,
      "gte(%s, %s)",
      arrayWithNaN1,
      arrayWithNaN2
    )
  }

  @Test
  fun gte_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/gteError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(
        evaluate(greaterThanOrEqual(errorExpr, value), testDoc),
        "gte(%s, %s)",
        errorExpr,
        value
      )
      assertEvaluatesToError(
        evaluate(greaterThanOrEqual(value, errorExpr), testDoc),
        "gte(%s, %s)",
        value,
        errorExpr
      )
    }
    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(errorExpr, errorExpr), testDoc),
      "gte(%s, %s)",
      errorExpr,
      errorExpr
    )
    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(errorExpr, nullValue()), testDoc),
      "gte(%s, %s)",
      errorExpr,
      nullValue()
    )
  }

  @Test
  fun gte_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/gteMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(missingField, presentValue), testDoc),
      "gte(%s, %s)",
      missingField,
      presentValue
    )
    assertEvaluatesToError(
      evaluate(greaterThanOrEqual(presentValue, missingField), testDoc),
      "gte(%s, %s)",
      presentValue,
      missingField
    )
  }
}
