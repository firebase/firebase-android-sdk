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
import com.google.firebase.firestore.pipeline.Expression.Companion.and
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAll
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.equal
import com.google.firebase.firestore.pipeline.Expression.Companion.equalAny
import com.google.firebase.firestore.pipeline.Expression.Companion.exists
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThan
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThanOrEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.isError
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThan
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThanOrEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.not
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqualAny
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.Expression.Companion.or
import com.google.firebase.firestore.pipeline.Expression.Companion.xor
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
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

    val pipeline = RealtimePipelineSource(db).collection("users").where(equal(field("score"), nullValue()))

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

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(exists(field("score")), field("score").notEqual(nullValue())))

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
        .where(
          and(
            equal(field("score"), nullValue()),
            and(exists(field("score")), field("score").notEqual(nullValue()))
          )
        )

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
      RealtimePipelineSource(db).collection("users").where(equal(field("score"), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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
      RealtimePipelineSource(db).collection("users").where(equal(field("score"), field("rank")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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
      RealtimePipelineSource(db).collection("users").where(equal(field("score.bonus"), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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
        .where(and(equal(field("score.bonus"), nullValue()), equal(field("rank"), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun whereEqNullInArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to listOf(null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(1.0, null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(null, Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(equal(field("foo"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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
        .where(equal(field("foo"), array(constant(1.0), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc3)
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
        .where(equal(field("foo"), array(nullValue(), constant(Double.NaN))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
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
        .where(equal(field("foo"), map(mapOf("a" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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
        .where(equal(field("foo"), map(mapOf("a" to constant(1.0), "b" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc3)
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
        .where(equal(field("foo"), map(mapOf("a" to nullValue(), "b" to constant(Double.NaN)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
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
        .where(equal(field("foo"), map(mapOf("a" to array(nullValue())))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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
        .where(equal(field("foo"), map(mapOf("a" to array(constant(1.0), nullValue())))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc3)
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
        .where(equal(field("foo"), map(mapOf("a" to array(nullValue(), constant(Double.NaN))))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun whereCompositeConditionWithNull(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to 42L, "rank" to null))
    val doc2 = doc("users/b", 1000, mapOf("score" to 42L, "rank" to 42L))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(equal(field("score"), constant(42L)), equal(field("rank"), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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
        .where(equalAny(field("score"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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
        .where(equalAny(field("score"), array(nullValue(), constant(100L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc4))
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
        .where(arrayContains(field("score"), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc3, doc4, doc5))
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
        .where(arrayContainsAny(field("score"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc3, doc4, doc5))
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
    assertThat(result).containsExactlyElementsIn(listOf(doc3, doc4, doc5, doc6))
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
        .where(arrayContainsAll(field("score"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc3, doc4, doc5))
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
        .where(arrayContainsAll(field("score"), array(nullValue(), constant(42L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun whereNeqConstantAsNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to Double.NaN))
    val doc4 = doc("users/4", 1000, mapOf("not-score" to 42L))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(notEqual(field("score"), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3, doc4))
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
      RealtimePipelineSource(db).collection("users").where(notEqual(field("score"), field("rank")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3, doc4, doc5))
  }

  // ... (Tests between these are unchanged, but the replace tool needs context or separate calls. I
  // will use separate calls or a large block if contiguous)
  // The tests are not contiguous. I will use separate replacements.
  // Actually, wait. `whereNeqConstantAsNull` is followed by `whereNeqFieldAsNull` which is followed
  // by others. `whereGt` is further down.
  // I will do `whereNeq` tests first.

  @Test
  fun whereNeqNullInArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to listOf(null)))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(1.0, null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(null, Double.NaN)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(notEqual(field("foo"), array(nullValue())))

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
        .where(notEqual(field("foo"), array(constant(1.0), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc4))
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
        .where(notEqual(field("foo"), array(nullValue(), constant(Double.NaN))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
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
        .where(notEqual(field("foo"), map(mapOf("a" to nullValue()))))

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
        .where(notEqual(field("foo"), map(mapOf("a" to constant(1.0), "b" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc4))
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
        .where(notEqual(field("foo"), map(mapOf("a" to nullValue(), "b" to constant(Double.NaN)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun whereNotEqAnyWithNull(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("score" to null))
    val doc2 = doc("users/b", 1000, mapOf("score" to 42L))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(notEqualAny(field("score"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
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
      RealtimePipelineSource(db).collection("users").where(greaterThan(field("score"), nullValue()))

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
        .where(greaterThanOrEqual(field("score"), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
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
      RealtimePipelineSource(db).collection("users").where(lessThan(field("score"), nullValue()))

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
        .where(lessThanOrEqual(field("score"), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun whereAnd(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true, "b" to null))
    val doc2 = doc("k/2", 1000, mapOf("a" to false, "b" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null, "b" to null))
    val doc4 = doc("k/4", 1000, mapOf("a" to true, "b" to true))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(and(field("a").asBoolean(), field("b").asBoolean()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun whereIsErrorAnd(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf<String, Any?>())
    val doc2 = doc("k/2", 1000, mapOf("a" to null, "b" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null))
    val doc4 = doc("k/4", 1000, mapOf("a" to null, "b" to true))
    val doc5 = doc("k/5", 1000, mapOf("a" to null, "b" to false))
    val doc6 = doc("k/6", 1000, mapOf("b" to null))
    val doc7 = doc("k/7", 1000, mapOf("a" to true, "b" to null))
    val doc8 = doc("k/8", 1000, mapOf("a" to false, "b" to null))
    val doc9 = doc("k/9", 1000, mapOf("not-a" to true, "not-b" to true))
    val doc10 = doc("k/10", 1000, mapOf("a" to 1L, "b" to 2L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9, doc10)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(isError(and(field("a").asBoolean(), field("b").asBoolean())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc10)
  }

  @Test
  fun whereOr(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true, "b" to null))
    val doc2 = doc("k/2", 1000, mapOf("a" to false, "b" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null, "b" to null))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(or(field("a").asBoolean(), field("b").asBoolean()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun whereEqNullOr(): Unit = runBlocking {
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
        .where(equal(or(field("a").asBoolean(), field("b").asBoolean()), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc4, doc5, doc7, doc8)
  }

  @Test
  fun whereIsErrorOr(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf<String, Any?>())
    val doc2 = doc("k/2", 1000, mapOf("a" to null, "b" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null))
    val doc4 = doc("k/4", 1000, mapOf("a" to null, "b" to true))
    val doc5 = doc("k/5", 1000, mapOf("a" to null, "b" to false))
    val doc6 = doc("k/6", 1000, mapOf("b" to null))
    val doc7 = doc("k/7", 1000, mapOf("a" to true, "b" to null))
    val doc8 = doc("k/8", 1000, mapOf("a" to false, "b" to null))
    val doc9 = doc("k/9", 1000, mapOf("not-a" to true, "not-b" to true))
    val doc10 = doc("k/10", 1000, mapOf("a" to 1L, "b" to 2L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9, doc10)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(isError(or(field("a").asBoolean(), field("b").asBoolean())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc10)
  }

  @Test
  fun whereXor(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true, "b" to null))
    val doc2 = doc("k/2", 1000, mapOf("a" to false, "b" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null, "b" to null))
    val doc4 = doc("k/4", 1000, mapOf("a" to true, "b" to false))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(xor(field("a").asBoolean(), field("b").asBoolean()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun whereEqNullXor(): Unit = runBlocking {
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
        .where(equal(xor(field("a").asBoolean(), field("b").asBoolean()), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)
  }

  @Test
  fun whereIsErrorXor(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf<String, Any?>())
    val doc2 = doc("k/2", 1000, mapOf("a" to null, "b" to null))
    val doc3 = doc("k/3", 1000, mapOf("a" to null))
    val doc4 = doc("k/4", 1000, mapOf("a" to null, "b" to true))
    val doc5 = doc("k/5", 1000, mapOf("a" to null, "b" to false))
    val doc6 = doc("k/6", 1000, mapOf("b" to null))
    val doc7 = doc("k/7", 1000, mapOf("a" to true, "b" to null))
    val doc8 = doc("k/8", 1000, mapOf("a" to false, "b" to null))
    val doc9 = doc("k/9", 1000, mapOf("not-a" to true, "not-b" to true))
    val doc10 = doc("k/10", 1000, mapOf("a" to 1L, "b" to 2L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9, doc10)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(isError(xor(field("a").asBoolean(), field("b").asBoolean())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc10)
  }

  @Test
  fun whereNot(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to true))
    val doc2 = doc("k/2", 1000, mapOf("a" to false))
    val doc3 = doc("k/3", 1000, mapOf("a" to null))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline = RealtimePipelineSource(db).collection("k").where(not(field("a").asBoolean()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun whereEqNullNot(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to null))
    val doc2 = doc("k/2", 1000, mapOf("a" to true))
    val doc3 = doc("k/3", 1000, mapOf("a" to false))
    val doc4 = doc("k/4", 1000, mapOf("not-a" to true))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(equal(not(field("a").asBoolean()), nullValue()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc4)
  }

  @Test
  fun whereIsErrorNot(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("a" to null))
    val doc2 = doc("k/2", 1000, mapOf("a" to true))
    val doc3 = doc("k/3", 1000, mapOf("a" to false))
    val doc4 = doc("k/4", 1000, mapOf("not-a" to true))
    val doc5 = doc("k/5", 1000, mapOf("a" to 1L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db).collection("k").where(isError(not(field("a").asBoolean())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc5)
  }

  @Test
  fun whereEqFieldAsUnset(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("rank" to null))
    val doc2 = doc("users/2", 1000, mapOf("unset" to null))
    val doc3 = doc("users/3", 1000, mapOf("rank" to 42L))
    val doc4 = doc("users/4", 1000, mapOf("unset" to "foo"))
    val doc5 = doc("users/5", 1000, mapOf<String, Any?>())
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(equal(field("unset"), field("rank")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc5)
  }

  @Test
  fun whereEqAnyNullInArray(): Unit = runBlocking {
    val doc1 = doc("k/1", 1000, mapOf("foo" to null))
    val doc2 = doc("k/2", 1000, mapOf("foo" to listOf(null)))
    val doc3 = doc("k/3", 1000, mapOf("foo" to listOf(listOf(null))))
    val doc4 = doc("k/4", 1000, mapOf("foo" to listOf(1.0, null)))
    val doc5 = doc("k/5", 1000, mapOf("foo" to listOf(null, Double.NaN)))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .where(equalAny(field("foo"), array(array(nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun whereGtArrayEmpty(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(greaterThan(field("score"), array()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc14, doc15, doc16, doc17, doc18, doc19)
  }

  @Test
  fun whereGtArraySingleton(): Unit = runBlocking {
    // Docs same as above
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThan(field("score"), array(constant(42L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc15, doc18, doc19)
  }

  @Test
  fun whereGtArraySingletonNull(): Unit = runBlocking {
    // Docs same as above
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThan(field("score"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc15, doc16, doc17, doc18, doc19)
  }

  @Test
  fun whereGtArrayNullFirst(): Unit = runBlocking {
    // Docs same as above
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThan(field("score"), array(nullValue(), constant(42L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc15, doc16, doc18, doc19)
  }

  @Test
  fun whereGtArrayNullLast(): Unit = runBlocking {
    // Docs same as above
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val doc20 = doc("users/20", 1000, mapOf("score" to listOf(43L, null)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19,
        doc20
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThan(field("score"), array(constant(42L), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc15, doc19, doc20)
  }

  @Test
  fun whereGtMapEmpty(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThan(field("score"), map(mapOf())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc7, doc8, doc9, doc10, doc11, doc12)
  }

  @Test
  fun whereGtMapSingleton(): Unit = runBlocking {
    // Docs same as above
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThan(field("score"), map(mapOf("a" to constant(42L)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc8, doc11, doc12)
  }

  @Test
  fun whereGtMapSingletonNull(): Unit = runBlocking {
    // Docs same as above
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThan(field("score"), map(mapOf("a" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc7, doc8, doc10, doc11, doc12)
  }

  @Test
  fun whereGtMapNullFirst(): Unit = runBlocking {
    // Docs same as above
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    // Java adds doc13 with "c" to null, but logic same without it if not referenced
    // Java test `where_gt_map_nullFirst_database` creates:
    // doc13: {c:null}
    // Query: {a:null, b:42}
    // result: doc7, doc8, doc11, doc12, doc13.
    // doc10 ({a:null, b:42}) is EQUAL, so not GT.
    // doc9 ({a:null}) < {a:null, b:42}
    // Wait, {a:null} is shorter than {a:null, b:42}, so it's smaller. Correct.

    // I'll add doc20 for completeness if I want to match Java exactly but I'll stick to doc1-19 +
    // maybe doc20 if needed.
    // Java: doc13 = {c:null}.
    // Let's add it to be safe.
    val doc20 = doc("users/20", 1000, mapOf("score" to mapOf("c" to null)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19,
        doc20
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThan(field("score"), map(mapOf("a" to nullValue(), "b" to constant(42L)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc7, doc8, doc11, doc12, doc20)
  }

  @Test
  fun whereGtMapNullLast(): Unit = runBlocking {
    // Docs same as above
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThan(field("score"), map(mapOf("a" to constant(42L), "b" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc8, doc12)
  }

  @Test
  fun whereGteArrayEmpty(): Unit = runBlocking {
    // Reuse docs setup
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThanOrEqual(field("score"), array()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13, doc14, doc15, doc16, doc17, doc18, doc19)
  }

  @Test
  fun whereGteArraySingleton(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThanOrEqual(field("score"), array(constant(42L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc15, doc16, doc18, doc19)
  }

  @Test
  fun whereGteArraySingletonNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThanOrEqual(field("score"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc14, doc15, doc16, doc17, doc18, doc19)
  }

  @Test
  fun whereGteArrayNullFirst(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThanOrEqual(field("score"), array(nullValue(), constant(42L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc15, doc16, doc17, doc18, doc19)
  }

  @Test
  fun whereGteArrayNullLast(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThanOrEqual(field("score"), array(constant(42L), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc15, doc18, doc19)
  }

  @Test
  fun whereGteMapEmpty(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThanOrEqual(field("score"), map(mapOf())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6, doc7, doc8, doc9, doc10, doc11, doc12)
  }

  @Test
  fun whereGteMapSingleton(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThanOrEqual(field("score"), map(mapOf("a" to constant(42L)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc7, doc8, doc11, doc12)
  }

  @Test
  fun whereGteMapSingletonNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val doc20 = doc("users/20", 1000, mapOf("score" to mapOf("c" to null)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19,
        doc20
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(greaterThanOrEqual(field("score"), map(mapOf("a" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc7, doc8, doc9, doc10, doc11, doc12, doc20)
  }

  @Test
  fun whereGteMapNullFirst(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val doc20 = doc("users/20", 1000, mapOf("score" to mapOf("c" to null)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19,
        doc20
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(
          greaterThanOrEqual(field("score"), map(mapOf("a" to nullValue(), "b" to constant(42L))))
        )

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc7, doc8, doc10, doc11, doc12, doc20)
  }

  @Test
  fun whereGteMapNullLast(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(
          greaterThanOrEqual(field("score"), map(mapOf("a" to constant(42L), "b" to nullValue())))
        )

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc8, doc11, doc12)
  }

  @Test
  fun whereLtArrayEmpty(): Unit = runBlocking {
    // Reuse docs setup
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(lessThan(field("score"), array()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereLtArraySingleton(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val doc21 = doc("users/21", 1000, mapOf("score" to listOf(41L, null)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19,
        doc21
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThan(field("score"), array(constant(42L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13, doc14, doc17, doc21)
  }

  @Test
  fun whereLtArraySingletonNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThan(field("score"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13)
  }

  @Test
  fun whereLtArrayNullFirst(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThan(field("score"), array(nullValue(), constant(42L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13, doc14)
  }

  @Test
  fun whereLtArrayNullLast(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThan(field("score"), array(constant(42L), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13, doc14, doc16, doc17)
  }

  @Test
  fun whereLtMapEmpty(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(lessThan(field("score"), map(mapOf())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun whereLtMapSingleton(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThan(field("score"), map(mapOf("a" to constant(42L)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6, doc9, doc10)
  }

  @Test
  fun whereLtMapSingletonNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThan(field("score"), map(mapOf("a" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6)
  }

  @Test
  fun whereLtMapNullFirst(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThan(field("score"), map(mapOf("a" to nullValue(), "b" to constant(42L)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6, doc9)
  }

  @Test
  fun whereLtMapNullLast(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThan(field("score"), map(mapOf("a" to constant(42L), "b" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6, doc7, doc9, doc10)
  }

  @Test
  fun whereLteArrayEmpty(): Unit = runBlocking {
    // Reuse docs setup
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db).collection("users").where(lessThanOrEqual(field("score"), array()))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13)
  }

  @Test
  fun whereLteArraySingleton(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val doc21 = doc("users/21", 1000, mapOf("score" to listOf(41L, null)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19,
        doc21
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThanOrEqual(field("score"), array(constant(42L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13, doc14, doc16, doc17, doc21)
  }

  @Test
  fun whereLteArraySingletonNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThanOrEqual(field("score"), array(nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13, doc14)
  }

  @Test
  fun whereLteArrayNullFirst(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThanOrEqual(field("score"), array(nullValue(), constant(42L))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13, doc14, doc17)
  }

  @Test
  fun whereLteArrayNullLast(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThanOrEqual(field("score"), array(constant(42L), nullValue())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc13, doc14, doc16, doc17, doc18)
  }

  @Test
  fun whereLteMapEmpty(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThanOrEqual(field("score"), map(mapOf())))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6)
  }

  @Test
  fun whereLteMapSingleton(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThanOrEqual(field("score"), map(mapOf("a" to constant(42L)))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6, doc7, doc9, doc10)
  }

  @Test
  fun whereLteMapSingletonNull(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(lessThanOrEqual(field("score"), map(mapOf("a" to nullValue()))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6, doc9)
  }

  @Test
  fun whereLteMapNullFirst(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(
          lessThanOrEqual(field("score"), map(mapOf("a" to nullValue(), "b" to constant(42L))))
        )

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6, doc9, doc10)
  }

  @Test
  fun whereLteMapNullLast(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("score" to null))
    val doc2 = doc("users/2", 1000, mapOf("score" to 42L))
    val doc3 = doc("users/3", 1000, mapOf("score" to "hello world"))
    val doc4 = doc("users/4", 1000, mapOf("score" to Double.NaN))
    val doc5 = doc("users/5", 1000, mapOf("not-score" to 42L))
    val doc6 = doc("users/6", 1000, mapOf("score" to emptyMap<String, Any>()))
    val doc7 = doc("users/7", 1000, mapOf("score" to mapOf("a" to 42L)))
    val doc8 = doc("users/8", 1000, mapOf("score" to mapOf("a" to mapOf("b" to null))))
    val doc9 = doc("users/9", 1000, mapOf("score" to mapOf("a" to null)))
    val doc10 = doc("users/10", 1000, mapOf("score" to mapOf("a" to null, "b" to 42L)))
    val doc11 = doc("users/11", 1000, mapOf("score" to mapOf("a" to 42L, "b" to null)))
    val doc12 = doc("users/12", 1000, mapOf("score" to mapOf("a" to 42L, "b" to 43L)))
    val doc13 = doc("users/13", 1000, mapOf("score" to emptyList<Any>()))
    val doc14 = doc("users/14", 1000, mapOf("score" to listOf(null)))
    val doc15 = doc("users/15", 1000, mapOf("score" to listOf(listOf(null))))
    val doc16 = doc("users/16", 1000, mapOf("score" to listOf(42L)))
    val doc17 = doc("users/17", 1000, mapOf("score" to listOf(null, 42L)))
    val doc18 = doc("users/18", 1000, mapOf("score" to listOf(42L, null)))
    val doc19 = doc("users/19", 1000, mapOf("score" to listOf(42L, 43L)))
    val documents =
      listOf(
        doc1,
        doc2,
        doc3,
        doc4,
        doc5,
        doc6,
        doc7,
        doc8,
        doc9,
        doc10,
        doc11,
        doc12,
        doc13,
        doc14,
        doc15,
        doc16,
        doc17,
        doc18,
        doc19
      )

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(
          lessThanOrEqual(field("score"), map(mapOf("a" to constant(42L), "b" to nullValue())))
        )

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc6, doc7, doc9, doc10, doc11)
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
