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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.RealtimePipelineSource
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.pipeline.Expr.Companion.and
import com.google.firebase.firestore.pipeline.Expr.Companion.array
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.not
import com.google.firebase.firestore.pipeline.Expr.Companion.or
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class InequalityTests {

  private val db = TestUtil.firestore()

  @Test
  fun `greater than`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline = RealtimePipelineSource(db).collection("users").where(field("score").gt(90L))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `greater than or equal`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline = RealtimePipelineSource(db).collection("users").where(field("score").gte(90L))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3))
  }

  @Test
  fun `less than`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline = RealtimePipelineSource(db).collection("users").where(field("score").lt(90L))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `less than or equal`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline = RealtimePipelineSource(db).collection("users").where(field("score").lte(90L))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun `not equal`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline = RealtimePipelineSource(db).collection("users").where(field("score").neq(90L))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3))
  }

  @Test
  fun `not equal returns mixed types`(): Unit = runBlocking {
    val doc1 = doc("users/alice", 1000, mapOf("score" to 90L)) // Should be filtered out
    val doc2 = doc("users/boc", 1000, mapOf("score" to true))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 42.0))
    val doc4 = doc("users/drew", 1000, mapOf("score" to "abc"))
    val doc5 = doc("users/eric", 1000, mapOf("score" to Timestamp(0, 2000000)))
    val doc6 = doc("users/francis", 1000, mapOf("score" to GeoPoint(0.0, 0.0)))
    val doc7 = doc("users/george", 1000, mapOf("score" to listOf(42L)))
    val doc8 = doc("users/hope", 1000, mapOf("score" to mapOf("foo" to 42L)))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline = RealtimePipelineSource(db).collection("users").where(field("score").neq(90L))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3, doc4, doc5, doc6, doc7, doc8))
  }

  @Test
  fun `comparison has implicit bound`(): Unit = runBlocking {
    val doc1 = doc("users/alice", 1000, mapOf("score" to 42L))
    val doc2 = doc("users/boc", 1000, mapOf("score" to 100.0)) // Matches > 42
    val doc3 = doc("users/charlie", 1000, mapOf("score" to true))
    val doc4 = doc("users/drew", 1000, mapOf("score" to "abc"))
    val doc5 = doc("users/eric", 1000, mapOf("score" to Timestamp(0, 2000000)))
    val doc6 = doc("users/francis", 1000, mapOf("score" to GeoPoint(0.0, 0.0)))
    val doc7 = doc("users/george", 1000, mapOf("score" to listOf(42L)))
    val doc8 = doc("users/hope", 1000, mapOf("score" to mapOf("foo" to 42L)))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline = RealtimePipelineSource(db).collection("users").where(field("score").gt(42L))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `not comparison returns mixed type`(): Unit = runBlocking {
    val doc1 = doc("users/alice", 1000, mapOf("score" to 42L)) // !(42 > 90) -> !F -> T
    val doc2 = doc("users/boc", 1000, mapOf("score" to 100.0)) // !(100 > 90) -> !T -> F
    val doc3 = doc("users/charlie", 1000, mapOf("score" to true)) // !(true > 90) -> !F -> T
    val doc4 = doc("users/drew", 1000, mapOf("score" to "abc")) // !("abc" > 90) -> !F -> T
    val doc5 =
      doc("users/eric", 1000, mapOf("score" to Timestamp(0, 2000000))) // !(T > 90) -> !F -> T
    val doc6 =
      doc("users/francis", 1000, mapOf("score" to GeoPoint(0.0, 0.0))) // !(G > 90) -> !F -> T
    val doc7 = doc("users/george", 1000, mapOf("score" to listOf(42L))) // !(A > 90) -> !F -> T
    val doc8 =
      doc("users/hope", 1000, mapOf("score" to mapOf("foo" to 42L))) // !(M > 90) -> !F -> T
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline = RealtimePipelineSource(db).collection("users").where(not(field("score").gt(90L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3, doc4, doc5, doc6, doc7, doc8))
  }

  @Test
  fun `inequality with equality on different field`(): Unit = runBlocking {
    val doc1 =
      doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // rank=2, score=90 > 80 -> Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // rank!=2
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // rank!=2
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").eq(2L), field("score").gt(80L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `inequality with equality on same field`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L)) // score=90, score > 80 -> Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L)) // score!=90
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L)) // score!=90
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("score").eq(90L), field("score").gt(80L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `with sort on same field`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L)) // score < 90
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("score").gte(90L))
        .sort(field("score").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3).inOrder()
  }

  @Test
  fun `with sort on different fields`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score < 90
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("score").gte(90L))
        .sort(field("rank").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1).inOrder()
  }

  @Test
  fun `with or on single field`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L)) // score not > 90 and not < 60
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L)) // score < 60 -> Match
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L)) // score > 90 -> Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(or(field("score").gt(90L), field("score").lt(60L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3))
  }

  @Test
  fun `with or on different fields`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // score > 80 -> Match
    val doc2 =
      doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score !> 80, rank !< 2
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("score" to 97L, "rank" to 1L)
      ) // score > 80, rank < 2 -> Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(or(field("score").gt(80L), field("rank").lt(2L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3))
  }

  @Test
  fun `with eqAny on single field`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L)) // score > 80, but not in [50, 80, 97]
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L)) // score !> 80
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("score" to 97L)
      ) // score > 80, score in [50, 80, 97] -> Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("score").gt(80L), field("score").eqAny(listOf(50L, 80L, 97L))))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `with eqAny on different fields`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/bob",
        1000,
        mapOf("score" to 90L, "rank" to 2L)
      ) // rank < 3, score not in [50, 80, 97]
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // rank !< 3
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("score" to 97L, "rank" to 1L)
      ) // rank < 3, score in [50, 80, 97] -> Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").lt(3L), field("score").eqAny(listOf(50L, 80L, 97L))))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `with notEqAny on single field`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("notScore" to 90L)) // score missing
    val doc2 =
      doc("users/alice", 1000, mapOf("score" to 90L)) // score > 80, but score is in [90, 95]
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 50L)) // score !> 80
    val doc4 =
      doc("users/diane", 1000, mapOf("score" to 97L)) // score > 80, score not in [90, 95] -> Match
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("score").gt(80L), field("score").notEqAny(listOf(90L, 95L))))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun `with notEqAny returns mixed types`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("notScore" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 90L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to true))
    val doc4 = doc("users/diane", 1000, mapOf("score" to 42.0))
    val doc5 = doc("users/eric", 1000, mapOf("score" to Double.NaN))
    val doc6 = doc("users/francis", 1000, mapOf("score" to "abc"))
    val doc7 = doc("users/george", 1000, mapOf("score" to Timestamp(0, 2000000)))
    val doc8 = doc("users/hope", 1000, mapOf("score" to GeoPoint(0.0, 0.0)))
    val doc9 = doc("users/isla", 1000, mapOf("score" to listOf(42L)))
    val doc10 = doc("users/jack", 1000, mapOf("score" to mapOf("foo" to 42L)))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9, doc10)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("score").notEqAny(listOf("foo", 90L, false)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result)
      .containsExactlyElementsIn(listOf(doc3, doc4, doc5, doc6, doc7, doc8, doc9, doc10))
  }

  @Test
  fun `with notEqAny on different fields`(): Unit = runBlocking {
    val doc1 =
      doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // rank < 3, score is in [90, 95]
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // rank !< 3
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("score" to 97L, "rank" to 1L)
      ) // rank < 3, score not in [90, 95] -> Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").lt(3L), field("score").notEqAny(listOf(90L, 95L))))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `sort by equality`(): Unit = runBlocking {
    val doc1 =
      doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // rank=2, score > 80 -> Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 4L)) // rank!=2
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // rank!=2
    val doc4 =
      doc("users/david", 1000, mapOf("score" to 91L, "rank" to 2L)) // rank=2, score > 80 -> Match
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").eq(2L), field("score").gt(80L)))
        .sort(field("rank").ascending(), field("score").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc4).inOrder()
  }

  @Test
  fun `with eqAny sort by equality`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/bob",
        1000,
        mapOf("score" to 90L, "rank" to 3L)
      ) // rank in [2,3,4], score > 80 -> Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 4L)) // score !> 80
    val doc3 =
      doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // rank not in [2,3,4]
    val doc4 =
      doc(
        "users/david",
        1000,
        mapOf("score" to 91L, "rank" to 2L)
      ) // rank in [2,3,4], score > 80 -> Match
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").eqAny(listOf(2L, 3L, 4L)), field("score").gt(80L)))
        .sort(field("rank").ascending(), field("score").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc1).inOrder()
  }

  @Test
  fun `with array`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/bob",
        1000,
        mapOf("scores" to listOf(80L, 85L, 90L), "rounds" to listOf(1L, 2L, 3L))
      ) // scores <= [90,90,90], rounds > [1,2] -> Match
    val doc2 =
      doc(
        "users/alice",
        1000,
        mapOf("scores" to listOf(50L, 65L), "rounds" to listOf(1L, 2L))
      ) // rounds !> [1,2]
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("scores" to listOf(90L, 95L, 97L), "rounds" to listOf(1L, 2L, 4L))
      ) // scores !<= [90,90,90]
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("scores").lte(array(90L, 90L, 90L)), field("rounds").gt(array(1L, 2L))))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `with arrayContainsAny`(): Unit = runBlocking { // Renamed from C++: withArrayContains
    val doc1 =
      doc(
        "users/bob",
        1000,
        mapOf("scores" to listOf(80L, 85L, 90L), "rounds" to listOf(1L, 2L, 3L))
      ) // scores <= [90,90,90], rounds contains 3 -> Match
    val doc2 =
      doc(
        "users/alice",
        1000,
        mapOf("scores" to listOf(50L, 65L), "rounds" to listOf(1L, 2L))
      ) // rounds does not contain 3
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("scores" to listOf(90L, 95L, 97L), "rounds" to listOf(1L, 2L, 4L))
      ) // scores !<= [90,90,90]
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(
          and(
            field("scores").lte(array(90L, 90L, 90L)),
            field("rounds").arrayContains(3L) // C++ used ArrayContainsExpr
          )
        )
    // In Kotlin, arrayContains is the equivalent of C++ ArrayContainsExpr for a single element.
    // For multiple elements, it would be arrayContainsAny.

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `with sort and limit`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 3L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 4L)) // score !> 80
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L))
    val doc4 = doc("users/david", 1000, mapOf("score" to 91L, "rank" to 2L))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("score").gt(80L))
        .sort(field("rank").ascending())
        .limit(2)

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc4).inOrder()
  }

  @Test
  fun `multiple inequalities on single field`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L)) // score !> 90
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L)) // score !> 90
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L)) // score > 90 and < 100 -> Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("score").gt(90L), field("score").lt(100L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `multiple inequalities on different fields single match`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // rank !< 2
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score !> 90
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("score" to 97L, "rank" to 1L)
      ) // score > 90, rank < 2 -> Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("score").gt(90L), field("rank").lt(2L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `multiple inequalities on different fields multiple match`(): Unit = runBlocking {
    val doc1 =
      doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // score > 80, rank < 3 -> Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score !> 80
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("score" to 97L, "rank" to 1L)
      ) // score > 80, rank < 3 -> Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("score").gt(80L), field("rank").lt(3L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3))
  }

  @Test
  fun `multiple inequalities on different fields all match`(): Unit = runBlocking {
    val doc1 =
      doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // score > 40, rank < 4 -> Match
    val doc2 =
      doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score > 40, rank < 4 -> Match
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("score" to 97L, "rank" to 1L)
      ) // score > 40, rank < 4 -> Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("score").gt(40L), field("rank").lt(4L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3))
  }

  @Test
  fun `multiple inequalities on different fields no match`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // rank !> 3
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score !< 90
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // rank !> 3
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("score").lt(90L), field("rank").gt(3L)))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `multiple inequalities with bounded ranges`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/bob",
        1000,
        mapOf("score" to 90L, "rank" to 2L)
      ) // rank > 0 & < 4, score > 80 & < 95 -> Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 4L)) // rank !< 4
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // score !< 95
    val doc4 = doc("users/david", 1000, mapOf("score" to 80L, "rank" to 3L)) // score !> 80
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(
          and(
            field("rank").gt(0L),
            field("rank").lt(4L),
            field("score").gt(80L),
            field("score").lt(95L)
          )
        )

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `multiple inequalities with single sort asc`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score !> 80
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").lt(3L), field("score").gt(80L)))
        .sort(field("rank").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1).inOrder()
  }

  @Test
  fun `multiple inequalities with single sort desc`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score !> 80
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").lt(3L), field("score").gt(80L)))
        .sort(field("rank").descending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3).inOrder()
  }

  @Test
  fun `multiple inequalities with multiple sort asc`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score !> 80
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").lt(3L), field("score").gt(80L)))
        .sort(field("rank").ascending(), field("score").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1).inOrder()
  }

  @Test
  fun `multiple inequalities with multiple sort desc`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score !> 80
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").lt(3L), field("score").gt(80L)))
        .sort(field("rank").descending(), field("score").descending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3).inOrder()
  }

  @Test
  fun `multiple inequalities with multiple sort desc on reverse index`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 2L)) // Match
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L)) // score !> 80
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 1L)) // Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(and(field("rank").lt(3L), field("score").gt(80L)))
        .sort(field("score").descending(), field("rank").descending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1).inOrder()
  }
}
