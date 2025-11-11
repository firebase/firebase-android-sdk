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
import com.google.firebase.firestore.FieldPath as PublicFieldPath
import com.google.firebase.firestore.RealtimePipelineSource
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.equalAny
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class CollectionTests {

  private val db = TestUtil.firestore()

  @Test
  fun `empty database returns no results`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users")
    val inputDocs = emptyList<MutableDocument>()
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `empty collection other collection ids returns no results`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users/bob/games")
    val doc1 = doc("users/alice/games/doc1", 1000, mapOf("title" to "minecraft"))
    val doc2 = doc("users/charlie/games/doc1", 1000, mapOf("title" to "halo"))
    val inputDocs = listOf(doc1, doc2)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `empty collection other parents returns no results`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users/bob/games")
    val doc1 = doc("users/bob/addresses/doc1", 1000, mapOf("city" to "New York"))
    val doc2 = doc("users/bob/inventories/doc1", 1000, mapOf("item_id" to 42L))
    val inputDocs = listOf(doc1, doc2)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `singleton at root returns single document`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users")
    val doc1 = doc("games/42", 1000, mapOf("title" to "minecraft"))
    val doc2 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 1L))
    val inputDocs = listOf(doc1, doc2)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `singleton nested collection returns single document`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users/bob/games")
    val doc1 = doc("users/bob/addresses/doc1", 1000, mapOf("city" to "New York"))
    val doc2 = doc("users/bob/games/doc1", 1000, mapOf("title" to "minecraft"))
    val doc3 = doc("users/alice/games/doc1", 1000, mapOf("title" to "halo"))
    val inputDocs = listOf(doc1, doc2, doc3)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `multiple documents at root returns documents`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users")
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 1L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 2L))
    val doc4 = doc("games/doc1", 1000, mapOf("title" to "minecraft"))
    val inputDocs = listOf(doc1, doc2, doc3, doc4)
    // Firestore backend sorts by document key as a tie-breaker.
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1, doc3)
  }

  @Test
  fun `multiple documents nested collection returns documents`(): Unit = runBlocking {
    // This test seems identical to MultipleDocumentsAtRootReturnsDocuments in C++?
    // Replicating the C++ test name and logic.
    val pipeline = RealtimePipelineSource(db).collection("/users")
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 1L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 2L))
    val doc4 = doc("games/doc1", 1000, mapOf("title" to "minecraft"))
    val inputDocs = listOf(doc1, doc2, doc3, doc4)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1, doc3)
  }

  @Test
  fun `subcollection not returned`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users")
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 1L))
    val doc2 = doc("users/bob/games/minecraft", 1000, mapOf("title" to "minecraft"))
    val doc3 = doc("users/bob/games/minecraft/players/player1", 1000, mapOf("location" to "sf"))
    val inputDocs = listOf(doc1, doc2, doc3)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `skips other collection ids`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users")
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 1L))
    val doc2 = doc("users-other/bob", 1000, mapOf("score" to 90L, "rank" to 1L))
    val doc3 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L))
    val doc4 = doc("users-other/alice", 1000, mapOf("score" to 50L, "rank" to 3L))
    val doc5 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 2L))
    val doc6 = doc("users-other/charlie", 1000, mapOf("score" to 97L, "rank" to 2L))
    val inputDocs = listOf(doc1, doc2, doc3, doc4, doc5, doc6)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1, doc5)
  }

  @Test
  fun `skips other parents`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users/bob/games")
    val doc1 = doc("users/bob/games/doc1", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice/games/doc1", 1000, mapOf("score" to 90L))
    val doc3 = doc("users/bob/games/doc2", 1000, mapOf("score" to 20L))
    val doc4 = doc("users/charlie/games/doc1", 1000, mapOf("score" to 20L))
    val doc5 = doc("users/bob/games/doc3", 1000, mapOf("score" to 30L))
    val doc6 = doc("users/alice/games/doc1", 1000, mapOf("score" to 30L))
    val inputDocs = listOf(doc1, doc2, doc3, doc4, doc5, doc6)
    // Expected order based on key for user bob's games
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3, doc5).inOrder()
  }

  // --- Where Tests ---

  @Test
  fun `where on values`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(equalAny(field("score"), array(constant(90L), constant(97L))))

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val doc4 = doc("users/diane", 1000, mapOf("score" to 97L))
    val inputDocs = listOf(doc1, doc2, doc3, doc4)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3, doc4)
  }

  @Test
  fun `where inequality on values`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db).collection("/users").where(field("score").greaterThan(80L))

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val inputDocs = listOf(doc1, doc2, doc3)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3)
  }

  @Test
  fun `where not equal on values`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db).collection("/users").where(field("score").notEqual(50L))

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val inputDocs = listOf(doc1, doc2, doc3)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3)
  }

  @Test
  fun `where array contains values`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("rounds").arrayContains(constant("round3")))

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rounds" to listOf("round1", "round3")))
    val doc2 =
      doc("users/alice", 1000, mapOf("score" to 50L, "rounds" to listOf("round2", "round4")))
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("score" to 97L, "rounds" to listOf("round2", "round3", "round4"))
      )
    val inputDocs = listOf(doc1, doc2, doc3)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3)
  }

  // --- Sort Tests ---

  @Test
  fun `sort on values`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collection("/users").sort(field("score").descending())

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val inputDocs = listOf(doc1, doc2, doc3)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1, doc2).inOrder()
  }

  @Test
  fun `sort on path`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .sort(field(PublicFieldPath.documentId()).ascending())

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val inputDocs = listOf(doc1, doc2, doc3)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1, doc3).inOrder()
  }

  // --- Limit Tests ---

  @Test
  fun limit(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .sort(field(PublicFieldPath.documentId()).ascending())
        .limit(2)

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val inputDocs = listOf(doc1, doc2, doc3)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1).inOrder()
  }

  // --- Sort on Key Tests ---

  @Test
  fun `sort on key ascending`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users/bob/games")
        .sort(field(PublicFieldPath.documentId()).ascending())

    val doc1 = doc("users/bob/games/a", 1000, mapOf("title" to "minecraft"))
    val doc2 = doc("users/bob/games/b", 1000, mapOf("title" to "halo"))
    val doc3 = doc("users/bob/games/c", 1000, mapOf("title" to "mariocart"))
    val doc4 = doc("users/bob/inventories/a", 1000, mapOf("type" to "sword"))
    val doc5 = doc("users/alice/games/c", 1000, mapOf("title" to "skyrim"))
    val inputDocs = listOf(doc1, doc2, doc3, doc4, doc5)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3).inOrder()
  }

  @Test
  fun `sort on key descending`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users/bob/games")
        .sort(field(PublicFieldPath.documentId()).descending())

    val doc1 = doc("users/bob/games/a", 1000, mapOf("title" to "minecraft"))
    val doc2 = doc("users/bob/games/b", 1000, mapOf("title" to "halo"))
    val doc3 = doc("users/bob/games/c", 1000, mapOf("title" to "mariocart"))
    val doc4 = doc("users/bob/inventories/a", 1000, mapOf("type" to "sword"))
    val doc5 = doc("users/alice/games/c", 1000, mapOf("title" to "skyrim"))
    val inputDocs = listOf(doc1, doc2, doc3, doc4, doc5)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc2, doc1).inOrder()
  }

  @Test
  fun `duplicate collection name`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db).collection("/users/alice/matches/1/opponents/bob/matches")
    val doc1 = doc("users/alice/matches/1/opponents/bob/matches/1", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice/matches/1/opponents/bob/matches/2", 1000, mapOf("score" to 90L))
    val doc3 = doc("users/not-alice/matches/1/opponents/bob/matches/1", 1000, mapOf("score" to 90L))
    val inputDocs = listOf(doc1, doc2, doc3)
    val result = runPipeline(pipeline, listOf(*inputDocs.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2)
  }
}
