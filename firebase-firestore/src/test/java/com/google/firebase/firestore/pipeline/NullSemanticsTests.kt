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
import com.google.firebase.firestore.pipeline.Expr.Companion.array
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContainsAll
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.eq
import com.google.firebase.firestore.pipeline.Expr.Companion.eqAny
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.gt
import com.google.firebase.firestore.pipeline.Expr.Companion.gte
import com.google.firebase.firestore.pipeline.Expr.Companion.isError
import com.google.firebase.firestore.pipeline.Expr.Companion.isNotNull
import com.google.firebase.firestore.pipeline.Expr.Companion.isNull
import com.google.firebase.firestore.pipeline.Expr.Companion.lt
import com.google.firebase.firestore.pipeline.Expr.Companion.lte
import com.google.firebase.firestore.pipeline.Expr.Companion.map
import com.google.firebase.firestore.pipeline.Expr.Companion.neq
import com.google.firebase.firestore.pipeline.Expr.Companion.not
import com.google.firebase.firestore.pipeline.Expr.Companion.notEqAny
import com.google.firebase.firestore.pipeline.Expr.Companion.nullValue
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
internal class NullSemanticsTests {

  private val db = TestUtil.firestore()

  // ===================================================================
  // Where Tests
  // ===================================================================
  @Test
  fun whereIsNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null)) // score: null -> Match
    val doc2 = doc("users/2", 1000, mapOf("score" to emptyList<Any?>())) // score: []
    val doc3 = doc("users/3", 1000, mapOf("score" to listOf(null))) // score: [null]
    val doc4 = doc("users/4", 1000, mapOf("score" to emptyMap<String, Any?>())) // score: {}
    val doc5 = doc("users/5", 1000, mapOf("score" to 42L)) // score: 42
    val doc6 = doc("users/6", 1000, mapOf("score" to Double.NaN)) // score: NaN
    val doc7 = doc("users/7", 1000, mapOf("not-score" to 42L)) // score: missing
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7)

    val pipeline = RealtimePipelineSource(db).collection("users").where(isNull(field("score")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun whereIsNotNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null)) // score: null
    val doc2 = doc("users/2", 1000, mapOf("score" to emptyList<Any?>())) // score: [] -> Match
    val doc3 = doc("users/3", 1000, mapOf("score" to listOf(null))) // score: [null] -> Match
    val doc4 =
      doc("users/4", 1000, mapOf("score" to emptyMap<String, Any?>())) // score: {} -> Match
    val doc5 = doc("users/5", 1000, mapOf("score" to 42L)) // score: 42 -> Match
    val doc6 = doc("users/6", 1000, mapOf("score" to Double.NaN)) // score: NaN -> Match
    val doc7 = doc("users/7", 1000, mapOf("not-score" to 42L)) // score: missing
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7)

    val pipeline = RealtimePipelineSource(db).collection("users").where(isNotNull(field("score")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3, doc4, doc5, doc6))
  }

  @Test
  fun whereIsNullAndIsNotNullEmpty(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to null))
    val doc2 = doc("users/b", 1000, mapOf("score" to listOf(null)))
    val doc3 = doc("users/c", 1000, mapOf("score" to 42L))
    val doc4 = doc("users/d", 1000, mapOf("bar" to 42L))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(isNull(field("score")), isNotNull(field("score"))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqConstantAsNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to Double.NaN))
    val doc4 = doc("users/4", 1000, mapOf("not-score" to 42L))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(eq(field("score"), nullValue())) // Equality filters never match null or missing

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqFieldAsNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null, "rank" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L, "rank" to null))
    val doc3 = doc("users/3", 1000, mapOf("score" to null, "rank" to 42L))
    val doc4 = doc("users/4", 1000, mapOf("score" to null))
    val doc5 = doc("users/5", 1000, mapOf("rank" to null))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(eq(field("score"), field("rank"))) // Equality filters never match null

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqSegmentField(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to mapOf("bonus" to null)))
    val doc2 = doc("users/2", 1000, mapOf("score" to mapOf("bonus" to 42L)))
    val doc3 = doc("users/3", 1000, mapOf("score" to mapOf("bonus" to Double.NaN)))
    val doc4 = doc("users/4", 1000, mapOf("score" to mapOf("not-bonus" to 42L)))
    val doc5 = doc("users/5", 1000, mapOf("score" to "foo-bar"))
    val doc6 = doc("users/6", 1000, mapOf("not-score" to mapOf("bonus" to 42L)))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(eq(field("score.bonus"), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqSingleFieldAndSegmentField(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to mapOf("bonus" to null), "rank" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to mapOf("bonus" to 42L), "rank" to null))
    val doc3 = doc("users/3", 1000, mapOf("score" to mapOf("bonus" to Double.NaN), "rank" to null))
    val doc4 = doc("users/4", 1000, mapOf("score" to mapOf("not-bonus" to 42L), "rank" to null))
    val doc5 = doc("users/5", 1000, mapOf("score" to "foo-bar"))
    val doc6 = doc("users/6", 1000, mapOf("not-score" to mapOf("bonus" to 42L), "rank" to null))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(eq(field("score.bonus"), nullValue()), eq(field("rank"), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqNullInArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to listOf(null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(1.0, null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(null, Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(eq(field("foo"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqNullOtherInArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to listOf(null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(1.0, null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(1L, null))) // Note: 1L becomes 1.0
    val doc4 = doc("k/4", 1000, mapOf("foo" to listOf(null, Double.NaN)))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(eq(field("foo"), array(constant(1.0), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqNullNanInArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to listOf(null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(1.0, null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(null, Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(eq(field("foo"), array(nullValue(), constant(Double.NaN))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqNullInMap(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to mapOf("a" to null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to 1.0, "b" to null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to null, "b" to Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(eq(field("foo"), map(mapOf("a" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqNullOtherInMap(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to mapOf("a" to null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to 1.0, "b" to null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to 1L, "b" to null)))
    val doc4 = doc("k/4", 1000, mapOf("foo" to mapOf("a" to null, "b" to Double.NaN)))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(eq(field("foo"), map(mapOf("a" to constant(1.0), "b" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqNullNanInMap(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to mapOf("a" to null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to 1.0, "b" to null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to null, "b" to Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(eq(field("foo"), map(mapOf("a" to nullValue(), "b" to constant(Double.NaN)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqMapWithNullArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to mapOf("a" to listOf(null))))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to listOf(1.0, null))))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to listOf(null, Double.NaN))))
    val doc4 = doc("k/4", 1000, mapOf("foo" to mapOf("a" to emptyList<Any?>())))
    val doc5 = doc("k/5", 1000, mapOf("foo" to mapOf("a" to listOf(1.0))))
    val doc6 = doc("k/6", 1000, mapOf("foo" to mapOf("a" to listOf(null, 1.0))))
    val doc7 = doc("k/7", 1000, mapOf("foo" to mapOf("not-a" to listOf(null))))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(eq(field("foo"), map(mapOf("a" to array(nullValue())))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqMapWithNullOtherArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to mapOf("a" to listOf(null))))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to listOf(1.0, null))))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to listOf(1L, null))))
    val doc4 = doc("k/4", 1000, mapOf("foo" to mapOf("a" to listOf(null, Double.NaN))))
    val doc5 = doc("k/5", 1000, mapOf("foo" to mapOf("a" to emptyList<Any?>())))
    val doc6 = doc("k/6", 1000, mapOf("foo" to mapOf("a" to listOf(1.0))))
    val doc7 = doc("k/7", 1000, mapOf("foo" to mapOf("a" to listOf(null, 1.0))))
    val doc8 = doc("k/8", 1000, mapOf("foo" to mapOf("not-a" to listOf(null))))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(eq(field("foo"), map(mapOf("a" to array(constant(1.0), nullValue())))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqMapWithNullNanArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to mapOf("a" to listOf(null))))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to listOf(1.0, null))))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to listOf(null, Double.NaN))))
    val doc4 = doc("k/4", 1000, mapOf("foo" to mapOf("a" to emptyList<Any?>())))
    val doc5 = doc("k/5", 1000, mapOf("foo" to mapOf("a" to listOf(1.0))))
    val doc6 = doc("k/6", 1000, mapOf("foo" to mapOf("a" to listOf(null, 1.0))))
    val doc7 = doc("k/7", 1000, mapOf("foo" to mapOf("not-a" to listOf(null))))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(eq(field("foo"), map(mapOf("a" to array(nullValue(), constant(Double.NaN))))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereCompositeConditionWithNull(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to 42L, "rank" to null))
    val doc2 = doc("users/b", 1000, mapOf("score" to 42L, "rank" to 42L))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(eq(field("score"), constant(42L)), eq(field("rank"), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqAnyNullOnly(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to null))
    val doc2 = doc("users/b", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/c", 1000, mapOf("rank" to 42L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(eqAny(field("score"), array(nullValue()))) // IN filters never match null

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereEqAnyPartialNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to emptyList<Any?>()))
    val doc3 = doc("users/3", 1000, mapOf("score" to 25L))
    val doc4 = doc("users/4", 1000, mapOf("score" to 100L)) // Match
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 100L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(eqAny(field("score"), array(nullValue(), constant(100L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun whereArrayContainsNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to emptyList<Any?>()))
    val doc3 = doc("users/3", 1000, mapOf("score" to listOf(null)))
    val doc4 = doc("users/4", 1000, mapOf("score" to listOf(null, 42L)))
    val doc5 = doc("users/5", 1000, mapOf("score" to listOf(101L, null)))
    val doc6 = doc("users/6", 1000, mapOf("score" to listOf("foo", "bar")))
    val doc7 = doc("users/7", 1000, mapOf("not-score" to listOf("foo", "bar")))
    val doc8 = doc("users/8", 1000, mapOf("not-score" to listOf("foo", null)))
    val doc9 = doc("users/9", 1000, mapOf("not-score" to listOf(null, "foo")))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(arrayContains(field("score"), nullValue())) // arrayContains does not match null

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereArrayContainsAnyOnlyNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to emptyList<Any?>()))
    val doc3 = doc("users/3", 1000, mapOf("score" to listOf(null)))
    val doc4 = doc("users/4", 1000, mapOf("score" to listOf(null, 42L)))
    val doc5 = doc("users/5", 1000, mapOf("score" to listOf(101L, null)))
    val doc6 = doc("users/6", 1000, mapOf("score" to listOf("foo", "bar")))
    val doc7 = doc("users/7", 1000, mapOf("not-score" to listOf("foo", "bar")))
    val doc8 = doc("users/8", 1000, mapOf("not-score" to listOf("foo", null)))
    val doc9 = doc("users/9", 1000, mapOf("not-score" to listOf(null, "foo")))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(
          arrayContainsAny(field("score"), array(nullValue()))
        ) // arrayContainsAny does not match null

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereArrayContainsAnyPartialNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to emptyList<Any?>()))
    val doc3 = doc("users/3", 1000, mapOf("score" to listOf(null)))
    val doc4 = doc("users/4", 1000, mapOf("score" to listOf(null, 42L)))
    val doc5 = doc("users/5", 1000, mapOf("score" to listOf(101L, null)))
    val doc6 = doc("users/6", 1000, mapOf("score" to listOf("foo", "bar"))) // Match 'foo'
    val doc7 = doc("users/7", 1000, mapOf("not-score" to listOf("foo", "bar")))
    val doc8 = doc("users/8", 1000, mapOf("not-score" to listOf("foo", null)))
    val doc9 = doc("users/9", 1000, mapOf("not-score" to listOf(null, "foo")))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(arrayContainsAny(field("score"), array(nullValue(), constant("foo"))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6)
  }

  @Test
  fun whereArrayContainsAllOnlyNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to emptyList<Any?>()))
    val doc3 = doc("users/3", 1000, mapOf("score" to listOf(null)))
    val doc4 = doc("users/4", 1000, mapOf("score" to listOf(null, 42L)))
    val doc5 = doc("users/5", 1000, mapOf("score" to listOf(101L, null)))
    val doc6 = doc("users/6", 1000, mapOf("score" to listOf("foo", "bar")))
    val doc7 = doc("users/7", 1000, mapOf("not-score" to listOf("foo", "bar")))
    val doc8 = doc("users/8", 1000, mapOf("not-score" to listOf("foo", null)))
    val doc9 = doc("users/9", 1000, mapOf("not-score" to listOf(null, "foo")))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(
          arrayContainsAll(field("score"), array(nullValue()))
        ) // arrayContainsAll does not match null

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereArrayContainsAllPartialNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to emptyList<Any?>()))
    val doc3 = doc("users/3", 1000, mapOf("score" to listOf(null)))
    val doc4 = doc("users/4", 1000, mapOf("score" to listOf(null, 42L)))
    val doc5 = doc("users/5", 1000, mapOf("score" to listOf(101L, null)))
    val doc6 = doc("users/6", 1000, mapOf("score" to listOf("foo", "bar")))
    val doc7 = doc("users/7", 1000, mapOf("not-score" to listOf("foo", "bar")))
    val doc8 = doc("users/8", 1000, mapOf("not-score" to listOf("foo", null)))
    val doc9 = doc("users/9", 1000, mapOf("not-score" to listOf(null, "foo")))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(
          arrayContainsAll(field("score"), array(nullValue(), constant(42L)))
        ) // arrayContainsAll does not match null

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereNeqConstantAsNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to Double.NaN))
    val doc4 = doc("users/4", 1000, mapOf("not-score" to 42L))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(neq(field("score"), nullValue())) // != null is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereNeqFieldAsNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null, "rank" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L, "rank" to null))
    val doc3 = doc("users/3", 1000, mapOf("score" to null, "rank" to 42L))
    val doc4 = doc("users/4", 1000, mapOf("score" to null))
    val doc5 = doc("users/5", 1000, mapOf("rank" to null))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(neq(field("score"), field("rank"))) // != null is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereNeqNullInArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to listOf(null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(1.0, null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(null, Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(neq(field("foo"), array(nullValue()))) // != [null] is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3))
  }

  @Test
  fun whereNeqNullOtherInArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to listOf(null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(1.0, null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(1L, null)))
    val doc4 = doc("k/4", 1000, mapOf("foo" to listOf(null, Double.NaN)))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(
          neq(field("foo"), array(constant(1.0), nullValue()))
        ) // != [1.0, null] is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun whereNeqNullNanInArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to listOf(null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(1.0, null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(null, Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(
          neq(field("foo"), array(nullValue(), constant(Double.NaN)))
        ) // != [null, NaN] is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3))
  }

  @Test
  fun whereNeqNullInMap(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to mapOf("a" to null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to 1.0, "b" to null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to null, "b" to Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(neq(field("foo"), map(mapOf("a" to nullValue())))) // != {a:null} is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3))
  }

  @Test
  fun whereNeqNullOtherInMap(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to mapOf("a" to null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to 1.0, "b" to null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to 1L, "b" to null)))
    val doc4 = doc("k/4", 1000, mapOf("foo" to mapOf("a" to null, "b" to Double.NaN)))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(
          neq(field("foo"), map(mapOf("a" to constant(1.0), "b" to nullValue())))
        ) // != {a:1.0,b:null} not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun whereNeqNullNanInMap(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to mapOf("a" to null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to 1.0, "b" to null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to null, "b" to Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(
          neq(field("foo"), map(mapOf("a" to nullValue(), "b" to constant(Double.NaN))))
        ) // != {a:null,b:NaN} not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3))
  }

  @Test
  fun whereNotEqAnyWithNull(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to null))
    val doc2 = doc("users/b", 1000, mapOf("score" to 42L))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(notEqAny(field("score"), array(nullValue()))) // NOT IN [null] is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereGt(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(gt(field("score"), nullValue())) // > null is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereGte(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(gte(field("score"), nullValue())) // >= null is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereLt(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lt(field("score"), nullValue())) // < null is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereLte(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lte(field("score"), nullValue())) // <= null is not supported

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereAnd(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true, "b" to null))
    val doc2 = doc("k/2", 1000, mapOf("a" to false, "b" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null, "b" to null))
    val doc4 = doc("k/4", 1000, mapOf("a" to true, "b" to true)) // Match
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(and(eq(field("a"), constant(true)), eq(field("b"), constant(true))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun whereIsNullAnd(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to null, "b" to null))
    val doc2 = doc("k/2", 1000, mapOf("a" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null, "b" to true))
    val doc4 = doc("k/4", 1000, mapOf("a" to null, "b" to false))
    val doc5 = doc("k/5", 1000, mapOf("b" to null))
    val doc6 = doc("k/6", 1000, mapOf("a" to true, "b" to null))
    val doc7 = doc("k/7", 1000, mapOf("a" to false, "b" to null))
    val doc8 = doc("k/8", 1000, mapOf("not-a" to true, "not-b" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(isNull(and(eq(field("a"), constant(true)), eq(field("b"), constant(true)))))
    // (a==true AND b==true) is NULL if:
    // (true AND null) -> null (doc6)
    // (null AND true) -> null (doc3)
    // (null AND null) -> null (doc1)
    // (false AND null) -> false
    // (null AND false) -> false
    // (missing AND true) -> error
    // (true AND missing) -> error
    // (missing AND null) -> error
    // (null AND missing) -> error
    // (missing AND missing) -> error
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3, doc6))
  }

  @Test
  fun whereIsErrorAnd(): Unit = runBlocking {
    val doc1 =
      doc(
        "k/1",
        1000,
        mapOf("a" to null, "b" to null)
      ) // a=null, b=null -> AND is null -> isError(null) is false
    val doc2 =
      doc(
        "k/2",
        1000,
        mapOf("a" to null)
      ) // a=null, b=missing -> AND is error -> isError(error) is true -> Match
    val doc3 =
      doc(
        "k/3",
        1000,
        mapOf("a" to null, "b" to true)
      ) // a=null, b=true -> AND is null -> isError(null) is false
    val doc4 =
      doc(
        "k/4",
        1000,
        mapOf("a" to null, "b" to false)
      ) // a=null, b=false -> AND is false -> isError(false) is false
    val doc5 =
      doc(
        "k/5",
        1000,
        mapOf("b" to null)
      ) // a=missing, b=null -> AND is error -> isError(error) is true -> Match
    val doc6 =
      doc(
        "k/6",
        1000,
        mapOf("a" to true, "b" to null)
      ) // a=true, b=null -> AND is null -> isError(null) is false
    val doc7 =
      doc(
        "k/7",
        1000,
        mapOf("a" to false, "b" to null)
      ) // a=false, b=null -> AND is false -> isError(false) is false
    val doc8 =
      doc(
        "k/8",
        1000,
        mapOf("not-a" to true, "not-b" to true)
      ) // a=missing, b=missing -> AND is error -> isError(error) is true -> Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(isError(and(eq(field("a"), constant(true)), eq(field("b"), constant(true)))))
    // This happens if either a or b is missing.
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc5, doc8))
  }

  @Test
  fun whereOr(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true, "b" to null)) // Match
    val doc2 = doc("k/2", 1000, mapOf("a" to false, "b" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null, "b" to null))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(or(eq(field("a"), constant(true)), eq(field("b"), constant(true))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun whereIsNullOr(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to null, "b" to null))
    val doc2 = doc("k/2", 1000, mapOf("a" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null, "b" to true))
    val doc4 = doc("k/4", 1000, mapOf("a" to null, "b" to false))
    val doc5 = doc("k/5", 1000, mapOf("b" to null))
    val doc6 = doc("k/6", 1000, mapOf("a" to true, "b" to null))
    val doc7 = doc("k/7", 1000, mapOf("a" to false, "b" to null))
    val doc8 = doc("k/8", 1000, mapOf("not-a" to true, "not-b" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(isNull(or(eq(field("a"), constant(true)), eq(field("b"), constant(true)))))
    // (a==true OR b==true) is NULL if:
    // (false OR null) -> null (doc7)
    // (null OR false) -> null (doc4)
    // (null OR null) -> null (doc1)
    // (true OR null) -> true
    // (null OR true) -> true
    // (missing OR false) -> error
    // (false OR missing) -> error
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc4, doc7))
  }

  @Test
  fun whereIsErrorOr(): Unit = runBlocking {
    val doc1 =
      doc(
        "k/1",
        1000,
        mapOf("a" to null, "b" to null)
      ) // a=null, b=null -> OR is null -> isError(null) is false
    val doc2 =
      doc(
        "k/2",
        1000,
        mapOf("a" to null)
      ) // a=null, b=missing -> OR is error -> isError(error) is true -> Match
    val doc3 =
      doc(
        "k/3",
        1000,
        mapOf("a" to null, "b" to true)
      ) // a=null, b=true -> OR is true -> isError(true) is false
    val doc4 =
      doc(
        "k/4",
        1000,
        mapOf("a" to null, "b" to false)
      ) // a=null, b=false -> OR is null -> isError(null) is false
    val doc5 =
      doc(
        "k/5",
        1000,
        mapOf("b" to null)
      ) // a=missing, b=null -> OR is error -> isError(error) is true -> Match
    val doc6 =
      doc(
        "k/6",
        1000,
        mapOf("a" to true, "b" to null)
      ) // a=true, b=null -> OR is true -> isError(true) is false
    val doc7 =
      doc(
        "k/7",
        1000,
        mapOf("a" to false, "b" to null)
      ) // a=false, b=null -> OR is null -> isError(null) is false
    val doc8 =
      doc(
        "k/8",
        1000,
        mapOf("not-a" to true, "not-b" to true)
      ) // a=missing, b=missing -> OR is error -> isError(error) is true -> Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(isError(or(eq(field("a"), constant(true)), eq(field("b"), constant(true)))))
    // This happens if either a or b is missing.
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc5, doc8))
  }

  @Test
  fun whereXor(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true, "b" to null)) // a=T, b=null -> XOR is null
    val doc2 = doc("k/2", 1000, mapOf("a" to false, "b" to null)) // a=F, b=null -> XOR is null
    val doc3 = doc("k/3", 1000, mapOf("a" to null, "b" to null)) // a=null, b=null -> XOR is null
    val doc4 =
      doc("k/4", 1000, mapOf("a" to true, "b" to false)) // a=T, b=F -> XOR is true -> Match
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(xor(eq(field("a"), constant(true)), eq(field("b"), constant(true))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun whereIsNullXor(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to null, "b" to null))
    val doc2 = doc("k/2", 1000, mapOf("a" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null, "b" to true))
    val doc4 = doc("k/4", 1000, mapOf("a" to null, "b" to false))
    val doc5 = doc("k/5", 1000, mapOf("b" to null))
    val doc6 = doc("k/6", 1000, mapOf("a" to true, "b" to null))
    val doc7 = doc("k/7", 1000, mapOf("a" to false, "b" to null))
    val doc8 = doc("k/8", 1000, mapOf("not-a" to true, "not-b" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(isNull(xor(eq(field("a"), constant(true)), eq(field("b"), constant(true)))))
    // (a==true XOR b==true) is NULL if:
    // (true XOR null) -> null (doc6)
    // (false XOR null) -> null (doc7)
    // (null XOR true) -> null (doc3)
    // (null XOR false) -> null (doc4)
    // (null XOR null) -> null (doc1)
    // (missing XOR true) -> error
    // (true XOR missing) -> error
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3, doc4, doc6, doc7))
  }

  @Test
  fun whereIsErrorXor(): Unit = runBlocking {
    val doc1 =
      doc(
        "k/1",
        1000,
        mapOf("a" to null, "b" to null)
      ) // a=null, b=null -> XOR is null -> isError(null) is false
    val doc2 =
      doc(
        "k/2",
        1000,
        mapOf("a" to null)
      ) // a=null, b=missing -> XOR is error -> isError(error) is true -> Match
    val doc3 =
      doc(
        "k/3",
        1000,
        mapOf("a" to null, "b" to true)
      ) // a=null, b=true -> XOR is null -> isError(null) is false
    val doc4 =
      doc(
        "k/4",
        1000,
        mapOf("a" to null, "b" to false)
      ) // a=null, b=false -> XOR is null -> isError(null) is false
    val doc5 =
      doc(
        "k/5",
        1000,
        mapOf("b" to null)
      ) // a=missing, b=null -> XOR is error -> isError(error) is true -> Match
    val doc6 =
      doc(
        "k/6",
        1000,
        mapOf("a" to true, "b" to null)
      ) // a=true, b=null -> XOR is null -> isError(null) is false
    val doc7 =
      doc(
        "k/7",
        1000,
        mapOf("a" to false, "b" to null)
      ) // a=false, b=null -> XOR is null -> isError(null) is false
    val doc8 =
      doc(
        "k/8",
        1000,
        mapOf("not-a" to true, "not-b" to true)
      ) // a=missing, b=missing -> XOR is error -> isError(error) is true -> Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(isError(xor(eq(field("a"), constant(true)), eq(field("b"), constant(true)))))
    // This happens if either a or b is missing.
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc5, doc8))
  }

  @Test
  fun whereNot(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true))
    val doc2 = doc("k/2", 1000, mapOf("a" to false)) // Match
    val doc3 = doc("k/3", 1000, mapOf("a" to null))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(not(eq(field("a"), constant(true))))

    // Based on C++ test's interpretation of TS behavior for NOT
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun whereIsNullNot(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true))
    val doc2 = doc("k/2", 1000, mapOf("a" to false))
    val doc3 = doc("k/3", 1000, mapOf("a" to null)) // Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(isNull(not(eq(field("a"), constant(true)))))
    // NOT(null_operand) -> null. So ISNULL(null) -> true.
    // NOT(true) -> false. ISNULL(false) -> false.
    // NOT(false) -> true. ISNULL(true) -> false.
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun whereIsErrorNot(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true)) // a=T -> NOT(a==T) is F -> isError(F) is false
    val doc2 = doc("k/2", 1000, mapOf("a" to false)) // a=F -> NOT(a==T) is T -> isError(T) is false
    val doc3 =
      doc("k/3", 1000, mapOf("a" to null)) // a=null -> NOT(a==T) is null -> isError(T) is false
    val doc4 =
      doc(
        "k/4",
        1000,
        mapOf("not-a" to true)
      ) // a=missing -> NOT(a==T) is error -> isError(error) is true -> Match
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(isError(not(eq(field("a"), constant(true)))))
    // This happens if a is missing.
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  // ===================================================================
  // Sort Tests
  // ===================================================================
  @Test
  fun sortNullInArrayAscending(): Unit = runBlocking {
    val doc0 = doc("k/0", 1000, mapOf("not-foo" to emptyList<Any?>())) // foo missing
    val doc1 = doc("k/1", 1000, mapOf("foo" to emptyList<Any?>())) // []
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(null))) // [null]
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(null, null))) // [null, null]
    val doc4 = doc("k/4", 1000, mapOf("foo" to listOf(null, 1L))) // [null, 1]
    val doc5 = doc("k/5", 1000, mapOf("foo" to listOf(null, 2L))) // [null, 2]
    val doc6 = doc("k/6", 1000, mapOf("foo" to listOf(1L, null))) // [1, null]
    val doc7 = doc("k/7", 1000, mapOf("foo" to listOf(2L, null))) // [2, null]
    val doc8 = doc("k/8", 1000, mapOf("foo" to listOf(2L, 1L))) // [2, 1]
    val documents = listOf(doc0, doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline = RealtimePipelineSource(db).collection("k").sort(field("foo").ascending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result)
      .containsExactly(doc0, doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)
      .inOrder()
  }

  @Test
  fun sortNullInArrayDescending(): Unit = runBlocking {
    val doc0 = doc("k/0", 1000, mapOf("not-foo" to emptyList<Any?>()))
    val doc1 = doc("k/1", 1000, mapOf("foo" to emptyList<Any?>()))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(null, null)))
    val doc4 = doc("k/4", 1000, mapOf("foo" to listOf(null, 1L)))
    val doc5 = doc("k/5", 1000, mapOf("foo" to listOf(null, 2L)))
    val doc6 = doc("k/6", 1000, mapOf("foo" to listOf(1L, null)))
    val doc7 = doc("k/7", 1000, mapOf("foo" to listOf(2L, null)))
    val doc8 = doc("k/8", 1000, mapOf("foo" to listOf(2L, 1L)))
    val documents = listOf(doc0, doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline = RealtimePipelineSource(db).collection("k").sort(field("foo").descending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result)
      .containsExactly(doc8, doc7, doc6, doc5, doc4, doc3, doc2, doc1, doc0)
      .inOrder()
  }

  @Test
  fun sortNullInMapAscending(): Unit = runBlocking {
    val doc0 = doc("k/0", 1000, mapOf("not-foo" to emptyMap<String, Any?>())) // foo missing
    val doc1 = doc("k/1", 1000, mapOf("foo" to emptyMap<String, Any?>())) // {}
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to null))) // {a:null}
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to null, "b" to null))) // {a:null, b:null}
    val doc4 = doc("k/4", 1000, mapOf("foo" to mapOf("a" to null, "b" to 1L))) // {a:null, b:1}
    val doc5 = doc("k/5", 1000, mapOf("foo" to mapOf("a" to null, "b" to 2L))) // {a:null, b:2}
    val doc6 = doc("k/6", 1000, mapOf("foo" to mapOf("a" to 1L, "b" to null))) // {a:1, b:null}
    val doc7 = doc("k/7", 1000, mapOf("foo" to mapOf("a" to 2L, "b" to null))) // {a:2, b:null}
    val doc8 = doc("k/8", 1000, mapOf("foo" to mapOf("a" to 2L, "b" to 1L))) // {a:2, b:1}
    val documents = listOf(doc0, doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline = RealtimePipelineSource(db).collection("k").sort(field("foo").ascending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result)
      .containsExactly(doc0, doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)
      .inOrder()
  }

  @Test
  fun sortNullInMapDescending(): Unit = runBlocking {
    val doc0 = doc("k/0", 1000, mapOf("not-foo" to emptyMap<String, Any?>()))
    val doc1 = doc("k/1", 1000, mapOf("foo" to emptyMap<String, Any?>()))
    val doc2 = doc("k/2", 1000, mapOf("foo" to mapOf("a" to null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to mapOf("a" to null, "b" to null)))
    val doc4 = doc("k/4", 1000, mapOf("foo" to mapOf("a" to null, "b" to 1L)))
    val doc5 = doc("k/5", 1000, mapOf("foo" to mapOf("a" to null, "b" to 2L)))
    val doc6 = doc("k/6", 1000, mapOf("foo" to mapOf("a" to 1L, "b" to null)))
    val doc7 = doc("k/7", 1000, mapOf("foo" to mapOf("a" to 2L, "b" to null)))
    val doc8 = doc("k/8", 1000, mapOf("foo" to mapOf("a" to 2L, "b" to 1L)))
    val documents = listOf(doc0, doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline = RealtimePipelineSource(db).collection("k").sort(field("foo").descending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result)
      .containsExactly(doc8, doc7, doc6, doc5, doc4, doc3, doc2, doc1, doc0)
      .inOrder()
  }
}
