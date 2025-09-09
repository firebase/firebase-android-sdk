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
import com.google.firebase.firestore.pipeline.Expr.Companion.array
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.notEqAny
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class NumberSemanticsTests {

  private val db = TestUtil.firestore()

  @Test
  fun `zero negative double zero`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to 0L)) // Integer 0
    val doc3 = doc("users/c", 1000, mapOf("score" to 0.0)) // Double 0.0
    val doc4 = doc("users/d", 1000, mapOf("score" to -0.0)) // Double -0.0
    val doc5 = doc("users/e", 1000, mapOf("score" to 1L)) // Integer 1
    val documents = listOf(doc1, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("score").eq(constant(-0.0)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3, doc4))
  }

  @Test
  fun `zero negative integer zero`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to 0L))
    val doc3 = doc("users/c", 1000, mapOf("score" to 0.0))
    val doc4 = doc("users/d", 1000, mapOf("score" to -0.0))
    val doc5 = doc("users/e", 1000, mapOf("score" to 1L))
    val documents = listOf(doc1, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("score").eq(constant(0L))) // Firestore -0LL is 0L

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3, doc4))
  }

  @Test
  fun `zero positive double zero`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to 0L))
    val doc3 = doc("users/c", 1000, mapOf("score" to 0.0))
    val doc4 = doc("users/d", 1000, mapOf("score" to -0.0))
    val doc5 = doc("users/e", 1000, mapOf("score" to 1L))
    val documents = listOf(doc1, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("score").eq(constant(0.0)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3, doc4))
  }

  @Test
  fun `zero positive integer zero`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to 0L))
    val doc3 = doc("users/c", 1000, mapOf("score" to 0.0))
    val doc4 = doc("users/d", 1000, mapOf("score" to -0.0))
    val doc5 = doc("users/e", 1000, mapOf("score" to 1L))
    val documents = listOf(doc1, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("score").eq(constant(0L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3, doc4))
  }

  @Test
  fun `equal Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("age").eq(constant(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `less than Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to null))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("age").lt(constant(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `less than equal Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to null))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("age").lte(constant(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `greater than equal Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 100L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("age").gte(constant(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `greater than Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 100L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("age").gt(constant(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `not equal Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("age").neq(constant(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3))
  }

  @Test
  fun `eqAny contains Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("name").eqAny(array(Double.NaN, "alice")))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `eqAny contains Nan only is empty`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("age").eqAny(array(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `arrayContains Nan only is empty`(): Unit = runBlocking {
    // Documents where 'age' is scalar, not an array.
    // arrayContains should not match if the field is not an array or if element is NaN.
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    // Example doc if 'age' were an array:
    // val docWithArray = doc("users/d", 1000, mapOf("name" to "diana", "age" to
    // listOf(Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(arrayContains(field("age"), constant(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `arrayContainsAny with Nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("field" to listOf(Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("field" to listOf(Double.NaN, 42L)))
    val doc3 = doc("k/c", 1000, mapOf("field" to listOf("foo", 42L)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(arrayContainsAny(field("field"), array(Double.NaN, "foo")))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `notEqAny contains Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("age" to 42L))
    val doc2 = doc("users/b", 1000, mapOf("age" to Double.NaN))
    val doc3 = doc("users/c", 1000, mapOf("age" to 25L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(notEqAny(field("age"), array(Double.NaN, 42L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3))
  }

  @Test
  fun `notEqAny contains Nan only matches all`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("age" to 42L))
    val doc2 = doc("users/b", 1000, mapOf("age" to Double.NaN))
    val doc3 = doc("users/c", 1000, mapOf("age" to 25L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(notEqAny(field("age"), array(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3))
  }

  @Test
  fun `array with Nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to listOf(Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("foo" to listOf(42L)))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(field("foo").eq(array(Double.NaN)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }
}
