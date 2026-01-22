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
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqualAny
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
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
      RealtimePipelineSource(db).collection("users").where(field("score").equal(constant(-0.0)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
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
        .where(field("score").equal(constant(0L))) // Firestore -0LL is 0L

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
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
      RealtimePipelineSource(db).collection("users").where(field("score").equal(constant(0.0)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
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
      RealtimePipelineSource(db).collection("users").where(field("score").equal(constant(0L)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3, doc4))
  }

  @Test
  fun `equal Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("age").equal(constant(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `less than Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to null))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("age").lessThan(constant(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `less than equal Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to null))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("age").lessThanOrEqual(constant(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `greater than equal Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 100L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("age").greaterThanOrEqual(constant(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `greater than Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 100L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("age").greaterThan(constant(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `not equal Nan`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("age").notEqual(constant(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3))
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
        .where(field("name").equalAny(array(Double.NaN, "alice")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `eqAny contains Nan only is empty`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to Double.NaN))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("age").equalAny(array(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
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

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3))
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
        .where(notEqualAny(field("age"), array(Double.NaN, 42L)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
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
        .where(notEqualAny(field("age"), array(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3))
  }

  @Test
  fun `array with Nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to listOf(Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("foo" to listOf(42L)))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(field("foo").equal(array(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `where eq across types`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("x" to 20))
    val doc2 = doc("users/b", 1000, mapOf("x" to 20L))
    val doc3 = doc("users/c", 1000, mapOf("x" to 20.0))
    val doc4 = doc("users/d", 1000, mapOf("x" to 21))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("x").equal(constant(20)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3))
  }

  @Test
  fun `where neq across types`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("x" to 20))
    val doc2 = doc("users/b", 1000, mapOf("x" to 20L))
    val doc3 = doc("users/c", 1000, mapOf("x" to 20.0))
    val doc4 = doc("users/d", 1000, mapOf("x" to 21))
    val doc5 = doc("users/e", 1000, mapOf("x" to Double.NaN))
    val doc6 = doc("users/f", 1000, mapOf("x" to null))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("x").notEqual(constant(20)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc4, doc5, doc6))
  }

  @Test
  fun `where lt across types`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("x" to 20))
    val doc2 = doc("users/b", 1000, mapOf("x" to 10))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("x").lessThan(constant(20)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `where lte across types`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("x" to 20))
    val doc2 = doc("users/b", 1000, mapOf("x" to 10))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("x").lessThanOrEqual(constant(20)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun `where gt across types`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("x" to 20))
    val doc2 = doc("users/b", 1000, mapOf("x" to 30))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("x").greaterThan(constant(20)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `where gte across types`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("x" to 20))
    val doc2 = doc("users/b", 1000, mapOf("x" to 30))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("x").greaterThanOrEqual(constant(20)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun `inequality with singleton range equivalent numerics`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to 42L))
    val doc2 = doc("users/b", 1000, mapOf("score" to 42.0))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("score").greaterThanOrEqual(constant(42L)))
        .where(field("score").lessThanOrEqual(constant(42.0)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun `number type flip flop`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to 42L))
    val doc2 = doc("users/b", 1000, mapOf("score" to 42.0))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(field("score").equal(constant(42L)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun `array neq with Nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to listOf(Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("foo" to listOf(42L)))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(field("foo").notEqual(array(Double.NaN)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `map eq with Nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to mapOf("a" to Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("foo" to mapOf("a" to 42L)))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(field("foo").equal(Expression.map(mapOf("a" to Double.NaN))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `map neq with Nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to mapOf("a" to Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("foo" to mapOf("a" to 42L)))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(field("foo").notEqual(Expression.map(mapOf("a" to Double.NaN))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `sort zero with null ascending`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to null))
    val doc2 = doc("users/b", 1000, mapOf("score" to 0))
    val documents = listOf(doc1, doc2)

    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("score").ascending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2).inOrder()
  }

  @Test
  fun `sort equivalent zeros descending`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to 0.0))
    val doc2 = doc("users/b", 1000, mapOf("score" to -0.0))
    val documents = listOf(doc1, doc2)

    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("score").descending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun `sort nan in array ascending`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to listOf(Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("foo" to listOf(1L)))
    val documents = listOf(doc1, doc2)

    val pipeline = RealtimePipelineSource(db).collection("k").sort(field("foo").ascending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2).inOrder()
  }

  @Test
  fun `sort nan in array descending`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to listOf(Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("foo" to listOf(1L)))
    val documents = listOf(doc1, doc2)

    val pipeline = RealtimePipelineSource(db).collection("k").sort(field("foo").descending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1).inOrder()
  }

  @Test
  fun `sort nan in map ascending`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to mapOf("a" to Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("foo" to mapOf("a" to 1L)))
    val documents = listOf(doc1, doc2)

    val pipeline = RealtimePipelineSource(db).collection("k").sort(field("foo").ascending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2).inOrder()
  }

  @Test
  fun `sort nan in map descending`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("foo" to mapOf("a" to Double.NaN)))
    val doc2 = doc("k/b", 1000, mapOf("foo" to mapOf("a" to 1L)))
    val documents = listOf(doc1, doc2)

    val pipeline = RealtimePipelineSource(db).collection("k").sort(field("foo").descending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1).inOrder()
  }

  @Test
  fun `sort mixed types with null and unset ascending`(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf())
    val doc2 = doc("k/2", 1000, mapOf("field" to null))
    val doc3 = doc("k/3", 1000, mapOf("field" to false))
    val doc4 = doc("k/4", 1000, mapOf("field" to true))
    val doc5 = doc("k/5", 1000, mapOf("field" to 1))
    val doc6 = doc("k/6", 1000, mapOf("field" to Double.NaN))
    val doc7 = doc("k/7", 1000, mapOf("field" to Double.NEGATIVE_INFINITY))
    val doc8 = doc("k/8", 1000, mapOf("field" to Double.POSITIVE_INFINITY))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline = RealtimePipelineSource(db).collection("k").sort(field("field").ascending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result)
      .isAnyOf(
        listOf(doc1, doc2, doc3, doc4, doc6, doc7, doc5, doc8),
        listOf(doc2, doc1, doc3, doc4, doc6, doc7, doc5, doc8),
        listOf(doc2, doc1, doc3, doc4, doc7, doc6, doc5, doc8),
        listOf(doc1, doc2, doc3, doc4, doc7, doc6, doc5, doc8)
      )
  }

  @Test
  fun `where exists across types`(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf())
    val doc2 = doc("users/2", 1000, mapOf("x" to null))
    val doc3 = doc("users/3", 1000, mapOf("x" to 10))
    val doc4 = doc("users/4", 1000, mapOf("x" to 20L))
    val doc5 = doc("users/5", 1000, mapOf("x" to 20.0))
    val doc6 = doc("users/6", 1000, mapOf("x" to Double.NaN))

    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(Expression.exists(field("x")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3, doc4, doc5, doc6))
  }

  @Test
  fun `eq deeply nested map with nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("data" to mapOf("a" to mapOf("b" to Double.NaN))))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(field("data").equal(Expression.map(mapOf("a" to mapOf("b" to Double.NaN)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `neq deeply nested map with nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("data" to mapOf("a" to mapOf("b" to Double.NaN))))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(field("data").notEqual(Expression.map(mapOf("a" to mapOf("b" to Double.NaN)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `eq array containing map with nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("data" to listOf(mapOf("a" to Double.NaN))))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(field("data").equal(array(listOf(mapOf("a" to Double.NaN)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `neq array containing map with nan`(): Unit = runBlocking {
    val doc1 = doc("k/a", 1000, mapOf("data" to listOf(mapOf("a" to Double.NaN))))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(field("data").notEqual(array(listOf(mapOf("a" to Double.NaN)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }
}
