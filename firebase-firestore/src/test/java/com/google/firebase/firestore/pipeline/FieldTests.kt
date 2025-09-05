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

import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FieldTests {

  @Test
  fun `can get field`() {
    val docWithField = doc("coll/doc1", 1, mapOf("exists" to true))
    val fieldExpr = Expression.field("exists")
    val result = evaluate(fieldExpr, docWithField) // Using evaluate from pipeline.testUtil
    assertEvaluatesTo(result, true, "Expected field 'exists' to evaluate to true")
  }

  @Test
  fun `returns unset if not found`() {
    val doc = doc("coll/doc1", 1, emptyMap())
    val fieldExpr = Expression.field("not-exists")
    val result = evaluate(fieldExpr, doc) // Using evaluate from pipeline.testUtil
    assertEvaluatesToUnset(result, "Expected non-existent field to evaluate to UNSET")
  }
}
