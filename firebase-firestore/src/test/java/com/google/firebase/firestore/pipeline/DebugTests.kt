package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.pipeline.Expr.Companion.array
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayLength
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.exists
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.isError
import com.google.firebase.firestore.pipeline.Expr.Companion.map
import com.google.firebase.firestore.pipeline.Expr.Companion.not
import com.google.firebase.firestore.pipeline.Expr.Companion.nullValue
import com.google.firebase.firestore.testutil.TestUtil
import com.google.firebase.firestore.testutil.TestUtil.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugTests {

  // --- Exists Tests ---

  @Test
  fun `valid field returns true for exists`() {
    val existsExpr = exists(field("x"))
    val doc = doc("coll/doc1", 1, mapOf("x" to 1))
    assertEvaluatesTo(evaluate(existsExpr, doc), true, "exists(existent-field))")
  }

  @Test
  fun `anything but unset returns true for exists`() {
    ComparisonTestData.allSupportedComparableValues.forEach { valueExpr ->
      assertEvaluatesTo(evaluate(exists(valueExpr)), true, "exists(%s)", valueExpr)
    }
  }

  @Test
  fun `null returns true for exists`() {
    assertEvaluatesTo(evaluate(exists(nullValue())), true, "exists(null)")
  }

  @Test
  fun `error returns error for exists`() {
    val errorProducingExpr = arrayLength(constant("notAnArray"))
    assertEvaluatesToError(evaluate(exists(errorProducingExpr)), "exists(error_expr)")
  }

  @Test
  fun `unset with not exists returns true`() {
    val unsetExpr = field("non-existent-field")
    val existsExpr = exists(unsetExpr)
    assertEvaluatesTo(evaluate(not(existsExpr)), true, "not(exists(non-existent-field))")
  }

  @Test
  fun `unset returns false for exists`() {
    val unsetExpr = field("non-existent-field")
    assertEvaluatesTo(evaluate(exists(unsetExpr)), false, "exists(non-existent-field)")
  }

  @Test
  fun `empty array returns true for exists`() {
    assertEvaluatesTo(evaluate(exists(array())), true, "exists([])")
  }

  @Test
  fun `empty map returns true for exists`() {
    // Expr.map() creates an empty map expression
    assertEvaluatesTo(evaluate(exists(map(emptyMap()))), true, "exists({})")
  }

  // --- IsError Tests ---

  @Test
  fun `isError error returns true`() {
    val errorProducingExpr = arrayLength(constant("notAnArray"))
    assertEvaluatesTo(evaluate(isError(errorProducingExpr)), true, "isError(error_expr)")
  }

  @Test
  fun `isError field missing returns false`() {
    // Evaluating a missing field results in UNSET. isError(UNSET) should be false.
    val fieldExpr = field("target")
    assertEvaluatesTo(evaluate(isError(fieldExpr)), false, "isError(missing_field)")
  }

  @Test
  fun `isError non-error returns false`() {
    assertEvaluatesTo(evaluate(isError(constant(42L))), false, "isError(42L)")
  }

  @Test
  fun `isError explicit null returns false`() {
    assertEvaluatesTo(evaluate(isError(nullValue())), false, "isError(null)")
  }

  @Test
  fun `isError unset returns false`() {
    // Evaluating a non-existent field results in UNSET. isError(UNSET) should be false.
    val unsetExpr = field("non-existent-field")
    assertEvaluatesTo(evaluate(isError(unsetExpr)), false, "isError(non-existent-field)")
  }

  @Test
  fun `isError anything but error returns false`() {
    ComparisonTestData.allSupportedComparableValues.forEach { valueExpr ->
      assertEvaluatesTo(evaluate(isError(valueExpr)), false, "isError(%s)", valueExpr)
    }
    assertEvaluatesTo(evaluate(isError(nullValue())), false, "isError(null)")
    assertEvaluatesTo(evaluate(isError(constant(0L))), false, "isError(0L)")
  }
}
