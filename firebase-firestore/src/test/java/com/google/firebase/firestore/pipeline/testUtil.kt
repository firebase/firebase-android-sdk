package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.model.DatabaseId
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.testutil.TestUtilKtx.doc

val DATABASE_ID = UserDataReader(DatabaseId.forDatabase("projectId", "databaseId"))
val EMPTY_DOC: MutableDocument = doc("foo/1", 0, mapOf())
internal val EVALUATION_CONTEXT = EvaluationContext(DATABASE_ID)

internal fun evaluate(expr: Expr): EvaluateResult {
    val function = expr.evaluateContext(EVALUATION_CONTEXT)
    return function(EMPTY_DOC)
}