package com.google.firebase.firestore.pipeline

import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.model.DatabaseId
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.Values.NULL_VALUE
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.testutil.TestUtilKtx.doc

val DATABASE_ID = UserDataReader(DatabaseId.forDatabase("project", "(default)"))
val EMPTY_DOC: MutableDocument = doc("foo/1", 0, mapOf())
internal val EVALUATION_CONTEXT = EvaluationContext(DATABASE_ID)

internal fun evaluate(expr: Expr): EvaluateResult = evaluate(expr, EMPTY_DOC)

internal fun evaluate(expr: Expr, doc: MutableDocument): EvaluateResult {
  val function = expr.evaluateContext(EVALUATION_CONTEXT)
  return function(doc)
}

// Helper to check for successful evaluation to a boolean value
internal fun assertEvaluatesTo(result: EvaluateResult, expected: Boolean, message: () -> String) {
  assertWithMessage(message()).that(result.isSuccess).isTrue()
  assertWithMessage(message()).that(result.value).isEqualTo(encodeValue(expected))
}

// Helper to check for evaluation resulting in NULL
internal fun assertEvaluatesToNull(result: EvaluateResult, message: () -> String) {
  assertWithMessage(message()).that(result.isSuccess).isTrue() // Null is a successful evaluation
  assertWithMessage(message()).that(result.value).isEqualTo(NULL_VALUE)
}

// Helper to check for evaluation resulting in UNSET (e.g. field not found)
internal fun assertEvaluatesToUnset(result: EvaluateResult, message: () -> String) {
  assertWithMessage(message()).that(result).isSameInstanceAs(EvaluateResultUnset)
}

// Helper to check for evaluation resulting in an error
internal fun assertEvaluatesToError(result: EvaluateResult, message: () -> String) {
  assertWithMessage(message()).that(result).isSameInstanceAs(EvaluateResultError)
}
