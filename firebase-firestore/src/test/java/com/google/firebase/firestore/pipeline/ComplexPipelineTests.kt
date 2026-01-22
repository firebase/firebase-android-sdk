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
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.pipeline.Expression.Companion.add
import com.google.firebase.firestore.pipeline.Expression.Companion.and
import com.google.firebase.firestore.pipeline.Expression.Companion.conditional
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.multiply
import com.google.firebase.firestore.pipeline.Expression.Companion.not
import com.google.firebase.firestore.pipeline.Expression.Companion.or
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ComplexPipelineTests {

  private val db = TestUtil.firestore()

  @Test
  fun `max stages with interleaved complex expressions`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("val" to 1L, "str" to "s"))
    val documents = listOf(doc1)

    var currentValExpr: Expression = field("val")

    for (i in 1..31) {
      val prevVal = currentValExpr
      val valExpr =
        if (i % 5 == 0) {
          conditional(
            prevVal.greaterThan(constant(0L)),
            add(prevVal, multiply(prevVal, constant(3L))),
            prevVal
          )
        } else {
          add(prevVal, constant(1L))
        }
      currentValExpr = valExpr
    }

    val pipeline =
      RealtimePipelineSource(db).collection("/k").where(currentValExpr.equal(constant(25937L)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `extreme conditional logic`(): Unit = runBlocking {
    val docs = mutableListOf<MutableDocument>()
    for (c in 'A'..'C') {
      docs.add(doc("k/doc$c", 1000, mapOf("type" to c.toString())))
    }
    val docDefault = doc("k/docDefault", 1000, mapOf("type" to "Unknown"))
    docs.add(docDefault)

    var categoryExpr: Expression = constant("DefaultResult")
    for (c in 'C' downTo 'A') {
      val type = c.toString()
      val result = "Result$type"
      categoryExpr =
        conditional(field("type").equal(constant(type)), constant(result), categoryExpr)
    }

    val pipeline =
      RealtimePipelineSource(db).collection("/k").where(categoryExpr.equal(constant("ResultB")))

    val result = runPipeline(pipeline, docs).toList()
    assertThat(result).containsExactly(docs[1]) // docB
  }

  @Test
  fun `union with many stages just under limit`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to 1L))
    val doc2 = doc("k/b", 1000, mapOf("foo" to 2L))
    val documents = listOf(doc1, doc2)

    val condition1 = field("foo").equal(constant(1L))

    var condition2: BooleanExpression = field("foo").equal(constant(2L))
    for (i in 0 until 50) {
      condition2 = and(condition2, field("foo").greaterThan(constant(0L)))
    }

    var unionCondition = or(condition1, condition2)

    for (i in 0 until 10) {
      unionCondition = and(unionCondition, field("foo").greaterThan(constant(0L)))
    }

    val pipeline = RealtimePipelineSource(db).collection("/k").where(unionCondition)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(documents)
  }

  @Test
  fun `deeply nested conditional in filter`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("x" to 1L, "y" to 2L)) // x < y
    val doc2 = doc("k/b", 1000, mapOf("x" to 2L, "y" to 1L)) // x > y
    val documents = listOf(doc1, doc2)

    var deeplyNestedFilter: Expression = field("x").equal(field("y"))
    for (i in 0 until 11) {
      val prevBoolean = deeplyNestedFilter.equal(constant(true))

      deeplyNestedFilter =
        conditional(field("x").greaterThan(field("y")), deeplyNestedFilter, not(prevBoolean))
    }

    val pipeline =
      RealtimePipelineSource(db).collection("/k").where(deeplyNestedFilter.equal(constant(true)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }
}
