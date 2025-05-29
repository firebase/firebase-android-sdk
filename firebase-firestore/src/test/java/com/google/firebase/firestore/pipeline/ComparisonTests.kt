package com.google.firebase.firestore.pipeline

import com.google.firebase.Timestamp // For creating Timestamp instances
import com.google.firebase.firestore.GeoPoint // For creating GeoPoint instances
import com.google.firebase.firestore.model.Values.NULL_VALUE
import com.google.firebase.firestore.pipeline.Expr.Companion.array
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.eq
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.gt
import com.google.firebase.firestore.pipeline.Expr.Companion.gte
import com.google.firebase.firestore.pipeline.Expr.Companion.lt
import com.google.firebase.firestore.pipeline.Expr.Companion.lte
import com.google.firebase.firestore.pipeline.Expr.Companion.map
import com.google.firebase.firestore.pipeline.Expr.Companion.neq
import com.google.firebase.firestore.pipeline.Expr.Companion.nullValue
import com.google.firebase.firestore.testutil.TestUtil // For test helpers like map, array, etc.
import com.google.firebase.firestore.testutil.TestUtilKtx.doc // For creating MutableDocument
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Helper data similar to C++ ComparisonValueTestData
internal object ComparisonTestData {
  private const val MAX_LONG_EXACTLY_REPRESENTABLE_AS_DOUBLE = 1L shl 53

  private val BOOLEAN_VALUES: List<Expr> = listOf(constant(false), constant(true))

  private val NUMERIC_VALUES: List<Expr> =
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

  private val TIMESTAMP_VALUES: List<Expr> =
    listOf(
      constant(Timestamp(-42, 0)),
      constant(Timestamp(-42, 42000000)),
      constant(Timestamp(0, 0)),
      constant(Timestamp(0, 42000000)),
      constant(Timestamp(42, 0)),
      constant(Timestamp(42, 42000000))
    )

  private val STRING_VALUES: List<Expr> =
    listOf(
      constant(""),
      constant("a"),
      constant("abcdefgh"),
      constant("santé"),
      constant("santé et bonheur"),
      constant("z")
    )

  private val BLOB_VALUES: List<Expr> =
    listOf(
      constant(TestUtil.blob()), // Empty
      constant(TestUtil.blob(0, 2, 56, 42)),
      constant(TestUtil.blob(2, 26)),
      constant(TestUtil.blob(2, 26, 31))
    )

  // Note: TestUtil.ref uses a default project "project" and default database "(default)"
  // So TestUtil.ref("foo/bar") becomes "projects/project/databases/(default)/documents/foo/bar"
  private val REF_VALUES: List<Expr> =
    listOf(
      constant(TestUtil.ref("foo/bar")),
      constant(TestUtil.ref("foo/bar/qux/a")),
      constant(TestUtil.ref("foo/bar/qux/bleh")),
      constant(TestUtil.ref("foo/bar/qux/hi")),
      constant(TestUtil.ref("foo/bar/tonk/a")),
      constant(TestUtil.ref("foo/baz"))
    )

  private val GEO_POINT_VALUES: List<Expr> =
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

  private val ARRAY_VALUES: List<Expr> =
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

  private val MAP_VALUES: List<Expr> =
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
  val allSupportedComparableValues: List<Expr> =
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
  val numericValuesForNanTest: List<Expr> = NUMERIC_VALUES // This list already excludes NaN

  // --- Dynamically generated comparison pairs based on Firestore type ordering ---
  // Type Order: Null < Boolean < Number < Timestamp < String < Blob < Reference < GeoPoint < Array
  // < Map

  private val allValueCategories: List<List<Expr>> =
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

  val equivalentValues: List<Pair<Expr, Expr>> = buildList {
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

  val lessThanValues: List<Pair<Expr, Expr>> = buildList {
    // Intra-type comparisons
    for (category in allValueCategories) {
      for (i in 0 until category.size - 1) {
        for (j in i + 1 until category.size) {
          add(category[i] to category[j])
        }
      }
    }
  }

  val mixedTypeValues: List<Pair<Expr, Expr>> = buildList {
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
      val result = evaluate(eq(v1, v2))
      assertEvaluatesTo(result, true) { "eq($v1, $v2)" }
    }
  }

  @Test
  fun eq_lessThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      // eq(v1, v2)
      val result1 = evaluate(eq(v1, v2))
      assertEvaluatesTo(result1, false) { "eq($v1, $v2)" }
      // eq(v2, v1)
      val result2 = evaluate(eq(v2, v1))
      assertEvaluatesTo(result2, false) { "eq($v2, $v1)" }
    }
  }

  // GreaterThanValues can be derived from LessThanValues by swapping pairs
  @Test
  fun eq_greaterThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      // eq(greater, less)
      val result = evaluate(eq(greater, less))
      assertEvaluatesTo(result, false) { "eq($greater, $less)" }
    }
  }

  @Test
  fun eq_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      val result1 = evaluate(eq(v1, v2))
      assertEvaluatesTo(result1, false) { "eq($v1, $v2)" }
      val result2 = evaluate(eq(v2, v1))
      assertEvaluatesTo(result2, false) { "eq($v2, $v1)" }
    }
  }

  @Test
  fun eq_nullEqualsNull_returnsNull() {
    // In SQL-like semantics, NULL == NULL is NULL, not TRUE.
    // Firestore's behavior for direct comparison of two NULL constants:
    val v1 = nullValue()
    val v2 = nullValue()
    val result = evaluate(eq(v1, v2))
    assertEvaluatesToNull(result) { "eq($v1, $v2)" }
  }

  @Test
  fun eq_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      // eq(null, value)
      assertEvaluatesToNull(evaluate(eq(nullVal, value))) { "eq($nullVal, $value)" }
      // eq(value, null)
      assertEvaluatesToNull(evaluate(eq(value, nullVal))) { "eq($value, $nullVal)" }
    }
    // eq(null, nonExistentField)
    val nullVal = nullValue()
    val missingField = field("nonexistent")
    assertEvaluatesToError(evaluate(eq(nullVal, missingField))) { "eq($nullVal, $missingField)" }
  }

  @Test
  fun eq_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN

    // NaN == NaN is false
    assertEvaluatesTo(evaluate(eq(nanExpr, nanExpr)), false) { "eq($nanExpr, $nanExpr)" }

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(eq(nanExpr, numVal)), false) { "eq($nanExpr, $numVal)" }
      assertEvaluatesTo(evaluate(eq(numVal, nanExpr)), false) { "eq($numVal, $nanExpr)" }
    }

    // Compare NaN with non-numeric types
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) { // Ensure we are not re-testing NaN vs NaN or NaN vs Numeric
          assertEvaluatesTo(evaluate(eq(nanExpr, otherVal)), false) { "eq($nanExpr, $otherVal)" }
          assertEvaluatesTo(evaluate(eq(otherVal, nanExpr)), false) { "eq($otherVal, $nanExpr)" }
        }
      }

    // NaN in array
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(evaluate(eq(arrayWithNaN1, arrayWithNaN2)), false) {
      "eq($arrayWithNaN1, $arrayWithNaN2)"
    }

    // NaN in map
    val mapWithNaN1 = map(mapOf("foo" to Double.NaN))
    val mapWithNaN2 = map(mapOf("foo" to Double.NaN))
    assertEvaluatesTo(evaluate(eq(mapWithNaN1, mapWithNaN2)), false) {
      "eq($mapWithNaN1, $mapWithNaN2)"
    }
  }

  @Test
  fun eq_nullContainerEquality_various() {
    val nullArray = array(nullValue()) // Array containing a Firestore Null

    assertEvaluatesTo(evaluate(eq(nullArray, constant(1L))), false) { "eq($nullArray, 1L)" }
    assertEvaluatesTo(evaluate(eq(nullArray, constant("1"))), false) { "eq($nullArray, \"1\")" }
    assertEvaluatesToNull(evaluate(eq(nullArray, nullValue()))) { "eq($nullArray, ${nullValue()})" }
    assertEvaluatesTo(evaluate(eq(nullArray, ComparisonTestData.doubleNaN)), false) {
      "eq($nullArray, ${ComparisonTestData.doubleNaN})"
    }
    assertEvaluatesTo(evaluate(eq(nullArray, array())), false) { "eq($nullArray, [])" }

    val nanArray = array(constant(Double.NaN))
    assertEvaluatesToNull(evaluate(eq(nullArray, nanArray))) { "eq($nullArray, $nanArray)" }

    val anotherNullArray = array(nullValue())
    assertEvaluatesToNull(evaluate(eq(nullArray, anotherNullArray))) {
      "eq($nullArray, $anotherNullArray)"
    }

    val nullMap = map(mapOf("foo" to NULL_VALUE)) // Map containing a Firestore Null
    val anotherNullMap = map(mapOf("foo" to NULL_VALUE))
    assertEvaluatesToNull(evaluate(eq(nullMap, anotherNullMap))) { "eq($nullMap, $anotherNullMap)" }
    assertEvaluatesTo(evaluate(eq(nullMap, map(emptyMap()))), false) { "eq($nullMap, {})" }
  }

  @Test
  fun eq_errorHandling_returnsError() {
    val errorExpr =
      field("a.b") // Accessing a nested field that might not exist or be of wrong type
    val testDoc = doc("test/eqError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(evaluate(eq(errorExpr, value), testDoc)) { "eq($errorExpr, $value)" }
      assertEvaluatesToError(evaluate(eq(value, errorExpr), testDoc)) { "eq($value, $errorExpr)" }
    }
    assertEvaluatesToError(evaluate(eq(errorExpr, errorExpr), testDoc)) {
      "eq($errorExpr, $errorExpr)"
    }
    assertEvaluatesToError(evaluate(eq(errorExpr, nullValue()), testDoc)) {
      "eq($errorExpr, ${nullValue()})"
    }
  }

  @Test
  fun eq_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/eqMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(evaluate(eq(missingField, presentValue), testDoc)) {
      "eq($missingField, $presentValue)"
    }
    assertEvaluatesToError(evaluate(eq(presentValue, missingField), testDoc)) {
      "eq($presentValue, $missingField)"
    }
  }

  // --- Neq (!=) Tests ---

  @Test
  fun neq_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      val result = evaluate(neq(v1, v2))
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(result) { "neq($v1, $v2)" }
      } else {
        assertEvaluatesTo(result, false) { "neq($v1, $v2)" }
      }
    }
  }

  @Test
  fun neq_lessThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(neq(v1, v2)), true) { "neq($v1, $v2)" }
      assertEvaluatesTo(evaluate(neq(v2, v1)), true) { "neq($v2, $v1)" }
    }
  }

  @Test
  fun neq_greaterThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(neq(greater, less)), true) { "neq($greater, $less)" }
    }
  }

  @Test
  fun neq_mixedTypeValues_returnTrue() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(neq(v1, v2))) { "neq($v1, $v2)" }
        assertEvaluatesToNull(evaluate(neq(v2, v1))) { "neq($v2, $v1)" }
      } else {
        assertEvaluatesTo(evaluate(neq(v1, v2)), true) { "neq($v1, $v2)" }
        assertEvaluatesTo(evaluate(neq(v2, v1)), true) { "neq($v2, $v1)" }
      }
    }
  }

  @Test
  fun neq_nullNotEqualsNull_returnsNull() {
    val v1 = nullValue()
    val v2 = nullValue()
    val result = evaluate(neq(v1, v2))
    assertEvaluatesToNull(result) { "neq($v1, $v2)" }
  }

  @Test
  fun neq_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(neq(nullVal, value))) { "neq($nullVal, $value)" }
      assertEvaluatesToNull(evaluate(neq(value, nullVal))) { "neq($value, $nullVal)" }
    }
    val nullVal = nullValue()
    val missingField = field("nonexistent")
    assertEvaluatesToError(evaluate(neq(nullVal, missingField))) { "neq($nullVal, $missingField)" }
  }

  @Test
  fun neq_nanComparisons_returnTrue() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(neq(nanExpr, nanExpr)), true) { "neq($nanExpr, $nanExpr)" }

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(neq(nanExpr, numVal)), true) { "neq($nanExpr, $numVal)" }
      assertEvaluatesTo(evaluate(neq(numVal, nanExpr)), true) { "neq($numVal, $nanExpr)" }
    }

    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(evaluate(neq(nanExpr, otherVal)), true) { "neq($nanExpr, $otherVal)" }
          assertEvaluatesTo(evaluate(neq(otherVal, nanExpr)), true) { "neq($otherVal, $nanExpr)" }
        }
      }

    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(evaluate(neq(arrayWithNaN1, arrayWithNaN2)), true) {
      "neq($arrayWithNaN1, $arrayWithNaN2)"
    }

    val mapWithNaN1 = map(mapOf("foo" to Double.NaN))
    val mapWithNaN2 = map(mapOf("foo" to Double.NaN))
    assertEvaluatesTo(evaluate(neq(mapWithNaN1, mapWithNaN2)), true) {
      "neq($mapWithNaN1, $mapWithNaN2)"
    }
  }

  @Test
  fun neq_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/neqError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(evaluate(neq(errorExpr, value), testDoc)) { "neq($errorExpr, $value)" }
      assertEvaluatesToError(evaluate(neq(value, errorExpr), testDoc)) { "neq($value, $errorExpr)" }
    }
    assertEvaluatesToError(evaluate(neq(errorExpr, errorExpr), testDoc)) {
      "neq($errorExpr, $errorExpr)"
    }
    assertEvaluatesToError(evaluate(neq(errorExpr, nullValue()), testDoc)) {
      "neq($errorExpr, ${nullValue()})"
    }
  }

  @Test
  fun neq_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/neqMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(evaluate(neq(missingField, presentValue), testDoc)) {
      "neq($missingField, $presentValue)"
    }
    assertEvaluatesToError(evaluate(neq(presentValue, missingField), testDoc)) {
      "neq($presentValue, $missingField)"
    }
  }

  // --- Lt (<) Tests ---

  @Test
  fun lt_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lt(v1, v2))) { "lt($v1, $v2)" }
      } else {
        assertEvaluatesTo(evaluate(lt(v1, v2)), false) { "lt($v1, $v2)" }
      }
    }
  }

  @Test
  fun lt_lessThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      val result = evaluate(lt(v1, v2))
      assertEvaluatesTo(result, true) { "lt($v1, $v2)" }
    }
  }

  @Test
  fun lt_greaterThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(lt(greater, less)), false) { "lt($greater, $less)" }
    }
  }

  @Test
  fun lt_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lt(v1, v2))) { "lt($v1, $v2)" }
        assertEvaluatesToNull(evaluate(lt(v2, v1))) { "lt($v2, $v1)" }
      } else {
        assertEvaluatesTo(evaluate(lt(v1, v2)), false) { "lt($v1, $v2)" }
        assertEvaluatesTo(evaluate(lt(v2, v1)), false) { "lt($v2, $v1)" }
      }
    }
  }

  @Test
  fun lt_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(lt(nullVal, value))) { "lt($nullVal, $value)" }
      assertEvaluatesToNull(evaluate(lt(value, nullVal))) { "lt($value, $nullVal)" }
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(evaluate(lt(nullVal, nullVal))) { "lt($nullVal, $nullVal)" }
    val missingField = field("nonexistent")
    assertEvaluatesToError(evaluate(lt(nullVal, missingField))) { "lt($nullVal, $missingField)" }
  }

  @Test
  fun lt_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(lt(nanExpr, nanExpr)), false) { "lt($nanExpr, $nanExpr)" }

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(lt(nanExpr, numVal)), false) { "lt($nanExpr, $numVal)" }
      assertEvaluatesTo(evaluate(lt(numVal, nanExpr)), false) { "lt($numVal, $nanExpr)" }
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(evaluate(lt(nanExpr, otherVal)), false) { "lt($nanExpr, $otherVal)" }
          assertEvaluatesTo(evaluate(lt(otherVal, nanExpr)), false) { "lt($otherVal, $nanExpr)" }
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(evaluate(lt(arrayWithNaN1, arrayWithNaN2)), false) {
      "lt($arrayWithNaN1, $arrayWithNaN2)"
    }
  }

  @Test
  fun lt_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/ltError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(evaluate(lt(errorExpr, value), testDoc)) { "lt($errorExpr, $value)" }
      assertEvaluatesToError(evaluate(lt(value, errorExpr), testDoc)) { "lt($value, $errorExpr)" }
    }
    assertEvaluatesToError(evaluate(lt(errorExpr, errorExpr), testDoc)) {
      "lt($errorExpr, $errorExpr)"
    }
    assertEvaluatesToError(evaluate(lt(errorExpr, nullValue()), testDoc)) {
      "lt($errorExpr, ${nullValue()})"
    }
  }

  @Test
  fun lt_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/ltMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(evaluate(lt(missingField, presentValue), testDoc)) {
      "lt($missingField, $presentValue)"
    }
    assertEvaluatesToError(evaluate(lt(presentValue, missingField), testDoc)) {
      "lt($presentValue, $missingField)"
    }
  }

  // --- Lte (<=) Tests ---

  @Test
  fun lte_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lte(v1, v2))) { "lte($v1, $v2)" }
      } else {
        assertEvaluatesTo(evaluate(lte(v1, v2)), true) { "lte($v1, $v2)" }
      }
    }
  }

  @Test
  fun lte_lessThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(lte(v1, v2)), true) { "lte($v1, $v2)" }
    }
  }

  @Test
  fun lte_greaterThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(lte(greater, less)), false) { "lte($greater, $less)" }
    }
  }

  @Test
  fun lte_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(lte(v1, v2))) { "lte($v1, $v2)" }
        assertEvaluatesToNull(evaluate(lte(v2, v1))) { "lte($v2, $v1)" }
      } else {
        assertEvaluatesTo(evaluate(lte(v1, v2)), false) { "lte($v1, $v2)" }
        assertEvaluatesTo(evaluate(lte(v2, v1)), false) { "lte($v2, $v1)" }
      }
    }
  }

  @Test
  fun lte_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(lte(nullVal, value))) { "lte($nullVal, $value)" }
      assertEvaluatesToNull(evaluate(lte(value, nullVal))) { "lte($value, $nullVal)" }
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(evaluate(lte(nullVal, nullVal))) { "lte($nullVal, $nullVal)" }
    val missingField = field("nonexistent")
    assertEvaluatesToError(evaluate(lte(nullVal, missingField))) { "lte($nullVal, $missingField)" }
  }

  @Test
  fun lte_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(lte(nanExpr, nanExpr)), false) { "lte($nanExpr, $nanExpr)" }

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(lte(nanExpr, numVal)), false) { "lte($nanExpr, $numVal)" }
      assertEvaluatesTo(evaluate(lte(numVal, nanExpr)), false) { "lte($numVal, $nanExpr)" }
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(evaluate(lte(nanExpr, otherVal)), false) { "lte($nanExpr, $otherVal)" }
          assertEvaluatesTo(evaluate(lte(otherVal, nanExpr)), false) { "lte($otherVal, $nanExpr)" }
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(evaluate(lte(arrayWithNaN1, arrayWithNaN2)), false) {
      "lte($arrayWithNaN1, $arrayWithNaN2)"
    }
  }

  @Test
  fun lte_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/lteError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(evaluate(lte(errorExpr, value), testDoc)) { "lte($errorExpr, $value)" }
      assertEvaluatesToError(evaluate(lte(value, errorExpr), testDoc)) { "lte($value, $errorExpr)" }
    }
    assertEvaluatesToError(evaluate(lte(errorExpr, errorExpr), testDoc)) {
      "lte($errorExpr, $errorExpr)"
    }
    assertEvaluatesToError(evaluate(lte(errorExpr, nullValue()), testDoc)) {
      "lte($errorExpr, ${nullValue()})"
    }
  }

  @Test
  fun lte_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/lteMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(evaluate(lte(missingField, presentValue), testDoc)) {
      "lte($missingField, $presentValue)"
    }
    assertEvaluatesToError(evaluate(lte(presentValue, missingField), testDoc)) {
      "lte($presentValue, $missingField)"
    }
  }

  // --- Gt (>) Tests ---

  @Test
  fun gt_equivalentValues_returnFalse() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(gt(v1, v2))) { "gt($v1, $v2)" }
      } else {
        assertEvaluatesTo(evaluate(gt(v1, v2)), false) { "gt($v1, $v2)" }
      }
    }
  }

  @Test
  fun gt_lessThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(gt(v1, v2)), false) { "gt($v1, $v2)" }
    }
  }

  @Test
  fun gt_greaterThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(gt(greater, less)), true) { "gt($greater, $less)" }
    }
  }

  @Test
  fun gt_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(gt(v1, v2))) { "gt($v1, $v2)" }
        assertEvaluatesToNull(evaluate(gt(v2, v1))) { "gt($v2, $v1)" }
      } else {
        assertEvaluatesTo(evaluate(gt(v1, v2)), false) { "gt($v1, $v2)" }
        assertEvaluatesTo(evaluate(gt(v2, v1)), false) { "gt($v2, $v1)" }
      }
    }
  }

  @Test
  fun gt_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(gt(nullVal, value))) { "gt($nullVal, $value)" }
      assertEvaluatesToNull(evaluate(gt(value, nullVal))) { "gt($value, $nullVal)" }
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(evaluate(gt(nullVal, nullVal))) { "gt($nullVal, $nullVal)" }
    val missingField = field("nonexistent")
    assertEvaluatesToError(evaluate(gt(nullVal, missingField))) { "gt($nullVal, $missingField)" }
  }

  @Test
  fun gt_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(gt(nanExpr, nanExpr)), false) { "gt($nanExpr, $nanExpr)" }

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(gt(nanExpr, numVal)), false) { "gt($nanExpr, $numVal)" }
      assertEvaluatesTo(evaluate(gt(numVal, nanExpr)), false) { "gt($numVal, $nanExpr)" }
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(evaluate(gt(nanExpr, otherVal)), false) { "gt($nanExpr, $otherVal)" }
          assertEvaluatesTo(evaluate(gt(otherVal, nanExpr)), false) { "gt($otherVal, $nanExpr)" }
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(evaluate(gt(arrayWithNaN1, arrayWithNaN2)), false) {
      "gt($arrayWithNaN1, $arrayWithNaN2)"
    }
  }

  @Test
  fun gt_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/gtError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(evaluate(gt(errorExpr, value), testDoc)) { "gt($errorExpr, $value)" }
      assertEvaluatesToError(evaluate(gt(value, errorExpr), testDoc)) { "gt($value, $errorExpr)" }
    }
    assertEvaluatesToError(evaluate(gt(errorExpr, errorExpr), testDoc)) {
      "gt($errorExpr, $errorExpr)"
    }
    assertEvaluatesToError(evaluate(gt(errorExpr, nullValue()), testDoc)) {
      "gt($errorExpr, ${nullValue()})"
    }
  }

  @Test
  fun gt_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/gtMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(evaluate(gt(missingField, presentValue), testDoc)) {
      "gt($missingField, $presentValue)"
    }
    assertEvaluatesToError(evaluate(gt(presentValue, missingField), testDoc)) {
      "gt($presentValue, $missingField)"
    }
  }

  // --- Gte (>=) Tests ---

  @Test
  fun gte_equivalentValues_returnTrue() {
    ComparisonTestData.equivalentValues.forEach { (v1, v2) ->
      if (v1 == nullValue() && v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(gte(v1, v2))) { "gte($v1, $v2)" }
      } else {
        assertEvaluatesTo(evaluate(gte(v1, v2)), true) { "gte($v1, $v2)" }
      }
    }
  }

  @Test
  fun gte_lessThanValues_returnFalse() {
    ComparisonTestData.lessThanValues.forEach { (v1, v2) ->
      assertEvaluatesTo(evaluate(gte(v1, v2)), false) { "gte($v1, $v2)" }
    }
  }

  @Test
  fun gte_greaterThanValues_returnTrue() {
    ComparisonTestData.lessThanValues.forEach { (less, greater) ->
      assertEvaluatesTo(evaluate(gte(greater, less)), true) { "gte($greater, $less)" }
    }
  }

  @Test
  fun gte_mixedTypeValues_returnFalse() {
    ComparisonTestData.mixedTypeValues.forEach { (v1, v2) ->
      if (v1 == nullValue() || v2 == nullValue()) {
        assertEvaluatesToNull(evaluate(gte(v1, v2))) { "gte($v1, $v2)" }
        assertEvaluatesToNull(evaluate(gte(v2, v1))) { "gte($v2, $v1)" }
      } else {
        assertEvaluatesTo(evaluate(gte(v1, v2)), false) { "gte($v1, $v2)" }
        assertEvaluatesTo(evaluate(gte(v2, v1)), false) { "gte($v2, $v1)" }
      }
    }
  }

  @Test
  fun gte_nullOperand_returnsNullOrError() {
    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      val nullVal = nullValue()
      assertEvaluatesToNull(evaluate(gte(nullVal, value))) { "gte($nullVal, $value)" }
      assertEvaluatesToNull(evaluate(gte(value, nullVal))) { "gte($value, $nullVal)" }
    }
    val nullVal = nullValue()
    assertEvaluatesToNull(evaluate(gte(nullVal, nullVal))) { "gte($nullVal, $nullVal)" }
    val missingField = field("nonexistent")
    assertEvaluatesToError(evaluate(gte(nullVal, missingField))) { "gte($nullVal, $missingField)" }
  }

  @Test
  fun gte_nanComparisons_returnFalse() {
    val nanExpr = ComparisonTestData.doubleNaN
    assertEvaluatesTo(evaluate(gte(nanExpr, nanExpr)), false) { "gte($nanExpr, $nanExpr)" }

    ComparisonTestData.numericValuesForNanTest.forEach { numVal ->
      assertEvaluatesTo(evaluate(gte(nanExpr, numVal)), false) { "gte($nanExpr, $numVal)" }
      assertEvaluatesTo(evaluate(gte(numVal, nanExpr)), false) { "gte($numVal, $nanExpr)" }
    }
    (ComparisonTestData.allSupportedComparableValues -
        ComparisonTestData.numericValuesForNanTest.toSet() -
        nanExpr)
      .forEach { otherVal ->
        if (otherVal != nanExpr) {
          assertEvaluatesTo(evaluate(gte(nanExpr, otherVal)), false) { "gte($nanExpr, $otherVal)" }
          assertEvaluatesTo(evaluate(gte(otherVal, nanExpr)), false) { "gte($otherVal, $nanExpr)" }
        }
      }
    val arrayWithNaN1 = array(constant(Double.NaN))
    val arrayWithNaN2 = array(constant(Double.NaN))
    assertEvaluatesTo(evaluate(gte(arrayWithNaN1, arrayWithNaN2)), false) {
      "gte($arrayWithNaN1, $arrayWithNaN2)"
    }
  }

  @Test
  fun gte_errorHandling_returnsError() {
    val errorExpr = field("a.b")
    val testDoc = doc("test/gteError", 0, mapOf("a" to 123))

    ComparisonTestData.allSupportedComparableValues.forEach { value ->
      assertEvaluatesToError(evaluate(gte(errorExpr, value), testDoc)) { "gte($errorExpr, $value)" }
      assertEvaluatesToError(evaluate(gte(value, errorExpr), testDoc)) { "gte($value, $errorExpr)" }
    }
    assertEvaluatesToError(evaluate(gte(errorExpr, errorExpr), testDoc)) {
      "gte($errorExpr, $errorExpr)"
    }
    assertEvaluatesToError(evaluate(gte(errorExpr, nullValue()), testDoc)) {
      "gte($errorExpr, ${nullValue()})"
    }
  }

  @Test
  fun gte_missingField_returnsError() {
    val missingField = field("nonexistent")
    val presentValue = constant(1L)
    val testDoc = doc("test/gteMissing", 0, mapOf("exists" to 10L))

    assertEvaluatesToError(evaluate(gte(missingField, presentValue), testDoc)) {
      "gte($missingField, $presentValue)"
    }
    assertEvaluatesToError(evaluate(gte(presentValue, missingField), testDoc)) {
      "gte($presentValue, $missingField)"
    }
  }
}
