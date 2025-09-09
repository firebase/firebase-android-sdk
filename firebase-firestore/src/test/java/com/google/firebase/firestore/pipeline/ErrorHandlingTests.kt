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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.RealtimePipelineSource
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.pipeline.Expr.Companion.and
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.divide
import com.google.firebase.firestore.pipeline.Expr.Companion.eq
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.or
import com.google.firebase.firestore.pipeline.Expr.Companion.xor
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ErrorHandlingTests {

  private val db = TestUtil.firestore()

  @Test
  fun `where partial error or`(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to "true", "b" to true, "c" to false))
    val doc2 = doc("k/2", 1000, mapOf("a" to true, "b" to "true", "c" to false))
    val doc3 = doc("k/3", 1000, mapOf("a" to true, "b" to false, "c" to "true"))
    val doc4 = doc("k/4", 1000, mapOf("a" to "true", "b" to "true", "c" to true))
    val doc5 = doc("k/5", 1000, mapOf("a" to "true", "b" to true, "c" to "true"))
    val doc6 = doc("k/6", 1000, mapOf("a" to true, "b" to "true", "c" to "true"))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(
          or(
            eq(field("a"), constant(true)),
            eq(field("b"), constant(true)),
            eq(field("c"), constant(true))
          )
        )

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    // In Firestore, comparisons between different types are generally false.
    // The OR evaluates to true if *any* of the fields 'a', 'b', or 'c' is the
    // boolean value `true`. All documents have at least one field that is boolean
    // `true`.
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3, doc4, doc5, doc6))
  }

  @Test
  fun `where partial error and`(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to "true", "b" to true, "c" to false))
    val doc2 = doc("k/2", 1000, mapOf("a" to true, "b" to "true", "c" to false))
    val doc3 = doc("k/3", 1000, mapOf("a" to true, "b" to false, "c" to "true"))
    val doc4 = doc("k/4", 1000, mapOf("a" to "true", "b" to "true", "c" to true))
    val doc5 = doc("k/5", 1000, mapOf("a" to "true", "b" to true, "c" to "true"))
    val doc6 = doc("k/6", 1000, mapOf("a" to true, "b" to "true", "c" to "true"))
    val doc7 = doc("k/7", 1000, mapOf("a" to true, "b" to true, "c" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(
          and(
            eq(field("a"), constant(true)),
            eq(field("b"), constant(true)),
            eq(field("c"), constant(true))
          )
        )

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    // AND requires all conditions to be true. Type mismatches evaluate EqExpr to
    // false. Only doc7 has a=true, b=true, AND c=true.
    assertThat(result).containsExactly(doc7)
  }

  @Test
  fun `where partial error xor`(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to "true", "b" to true, "c" to false))
    val doc2 = doc("k/2", 1000, mapOf("a" to true, "b" to "true", "c" to false))
    val doc3 = doc("k/3", 1000, mapOf("a" to true, "b" to false, "c" to "true"))
    val doc4 = doc("k/4", 1000, mapOf("a" to "true", "b" to "true", "c" to true))
    val doc5 = doc("k/5", 1000, mapOf("a" to "true", "b" to true, "c" to "true"))
    val doc6 = doc("k/6", 1000, mapOf("a" to true, "b" to "true", "c" to "true"))
    val doc7 = doc("k/7", 1000, mapOf("a" to true, "b" to true, "c" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(
          xor(
            eq(field("a"), constant(true)),
            eq(field("b"), constant(true)),
            eq(field("c"), constant(true))
          )
        )

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    // XOR is true if an odd number of inputs are true.
    // Assuming type mismatches evaluate EqExpr to false:
    // doc1: F xor T xor F = T
    // doc2: T xor F xor F = T
    // doc3: T xor F xor F = T
    // doc4: F xor F xor T = T
    // doc5: F xor T xor F = T
    // doc6: T xor F xor F = T
    // doc7: T xor T xor T = T
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7))
  }

  @Test
  fun `where not error`(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to false))
    val doc2 = doc("k/2", 1000, mapOf("a" to "true"))
    val doc3 = doc("k/3", 1000, mapOf("b" to true))
    val documents = listOf(doc1, doc2, doc3)

    // This test case in C++ was adjusted to match a TS behavior,
    // resulting in a condition `field("a") == false`.
    val pipeline = RealtimePipelineSource(db).collection("k").where(eq(field("a"), constant(false)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    // Only doc1 has a == false.
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `where error producing function returns empty`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to true))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to "42"))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 0))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(eq(divide(constant("100"), constant("50")), constant(2L)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    // Division of string constants should cause an evaluation error,
    // leading to no documents matching.
    assertThat(result).isEmpty()
  }
}
