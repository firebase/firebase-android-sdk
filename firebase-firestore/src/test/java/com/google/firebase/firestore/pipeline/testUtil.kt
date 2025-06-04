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

import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.firestore.AbstractPipeline
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.model.DatabaseId
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.Values.NULL_VALUE
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import com.google.firestore.v1.Value
import kotlinx.coroutines.flow.Flow

val DATABASE_ID = UserDataReader(DatabaseId.forDatabase("project", "(default)"))
val EMPTY_DOC: MutableDocument = doc("foo/1", 0, mapOf())
internal val EVALUATION_CONTEXT = EvaluationContext(DATABASE_ID)

internal fun evaluate(expr: Expr): EvaluateResult = evaluate(expr, EMPTY_DOC)

internal fun evaluate(expr: Expr, doc: MutableDocument): EvaluateResult {
  val function = expr.evaluateContext(EVALUATION_CONTEXT)
  return function(doc)
}

// Helper to check for successful evaluation to a boolean value
internal fun assertEvaluatesTo(
  result: EvaluateResult,
  expected: Boolean,
  format: String,
  vararg args: Any?
) = assertEvaluatesTo(result, encodeValue(expected), format, *args)

// Helper to check for successful evaluation to a value
internal fun assertEvaluatesTo(
  result: EvaluateResult,
  expected: Value,
  format: String,
  vararg args: Any?
) {
  assertWithMessage(format, *args).that(result.isSuccess).isTrue()
  assertWithMessage(format, *args).that(result.value).isEqualTo(expected)
}

// Helper to check for evaluation resulting in NULL
internal fun assertEvaluatesToNull(result: EvaluateResult, format: String, vararg args: Any?) {
  assertWithMessage(format, *args)
    .that(result.isSuccess)
    .isTrue() // Null is a successful evaluation
  assertWithMessage(format, *args).that(result.value).isEqualTo(NULL_VALUE)
}

// Helper to check for evaluation resulting in UNSET (e.g. field not found)
internal fun assertEvaluatesToUnset(result: EvaluateResult, format: String, vararg args: Any?) {
  assertWithMessage(format, *args).that(result).isSameInstanceAs(EvaluateResultUnset)
}

// Helper to check for evaluation resulting in an error
internal fun assertEvaluatesToError(result: EvaluateResult, format: String, vararg args: Any?) {
  assertWithMessage(format, *args).that(result).isSameInstanceAs(EvaluateResultError)
}

internal fun runPipeline(
  pipeline: AbstractPipeline,
  input: Flow<MutableDocument>
): Flow<MutableDocument> {
  val context = EvaluationContext(pipeline.userDataReader)
  return pipeline.stages.fold(input) { documentFlow, stage ->
    stage.evaluate(context, documentFlow)
  }
}
