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
import com.google.firebase.firestore.pipeline.Expr.Companion.array
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expr.Companion.eqAny
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.gt
import com.google.firebase.firestore.pipeline.Expr.Companion.neq
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class CollectionGroupTests {

  private val db = TestUtil.firestore()

  @Test
  fun `returns no result from empty db`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collectionGroup("users")
    val documents = emptyList<MutableDocument>()
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `returns single document`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collectionGroup("users")
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 1L))
    val documents = listOf(doc1)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `returns multiple documents`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collectionGroup("users")
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rank" to 1L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L, "rank" to 3L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L, "rank" to 2L))
    val documents = listOf(doc1, doc2, doc3)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(documents)
  }

  @Test
  fun `skips other collection ids`(): Unit = runBlocking {
    val pipeline = RealtimePipelineSource(db).collectionGroup("users")
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users-other/bob", 1000, mapOf("score" to 90L))
    val doc3 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc4 = doc("users-other/alice", 1000, mapOf("score" to 50L))
    val doc5 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val doc6 = doc("users-other/charlie", 1000, mapOf("score" to 97L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)
    val expectedDocs = listOf(doc1, doc3, doc5) // alice, bob, charlie (from 'users' only)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs)
  }

  @Test
  fun `different parents`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db).collectionGroup("games").sort(field("order").ascending())
    val doc1 = doc("users/bob/games/game1", 1000, mapOf("score" to 90L, "order" to 1L))
    val doc2 = doc("users/alice/games/game1", 1000, mapOf("score" to 90L, "order" to 2L))
    val doc3 = doc("users/bob/games/game2", 1000, mapOf("score" to 20L, "order" to 3L))
    val doc4 = doc("users/charlie/games/game1", 1000, mapOf("score" to 20L, "order" to 4L))
    val doc5 = doc("users/bob/games/game3", 1000, mapOf("score" to 30L, "order" to 5L))
    val doc6 = doc("users/alice/games/game2", 1000, mapOf("score" to 30L, "order" to 6L))
    val doc7 =
      doc("users/charlie/profiles/profile1", 1000, mapOf("order" to 7L)) // Different collection ID

    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7)
    val expectedDocs = listOf(doc1, doc2, doc3, doc4, doc5, doc6)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs).inOrder()
  }

  @Test
  fun `different parents stable ordering on path`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("games")
        .sort(field(PublicFieldPath.documentId()).ascending())

    val doc1 = doc("users/bob/games/1", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice/games/2", 1000, mapOf("score" to 90L))
    val doc3 = doc("users/bob/games/3", 1000, mapOf("score" to 20L))
    val doc4 = doc("users/charlie/games/4", 1000, mapOf("score" to 20L))
    val doc5 = doc("users/bob/games/5", 1000, mapOf("score" to 30L))
    val doc6 = doc("users/alice/games/6", 1000, mapOf("score" to 30L))
    val doc7 =
      doc("users/charlie/profiles/7", 1000, mapOf<String, Any>()) // Different collection ID

    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7)
    // Expected order:
    // users/alice/games/2
    // users/alice/games/6
    // users/bob/games/1
    // users/bob/games/3
    // users/bob/games/5
    // users/charlie/games/4
    val expectedDocs = listOf(doc2, doc6, doc1, doc3, doc5, doc4)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs)
  }

  @Test
  fun `different parents stable ordering on key`(): Unit = runBlocking {
    // This test is identical to DifferentParentsStableOrderingOnPath
    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("games")
        .sort(field(PublicFieldPath.documentId()).ascending())

    val doc1 = doc("users/bob/games/1", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice/games/2", 1000, mapOf("score" to 90L))
    val doc3 = doc("users/bob/games/3", 1000, mapOf("score" to 20L))
    val doc4 = doc("users/charlie/games/4", 1000, mapOf("score" to 20L))
    val doc5 = doc("users/bob/games/5", 1000, mapOf("score" to 30L))
    val doc6 = doc("users/alice/games/6", 1000, mapOf("score" to 30L))
    val doc7 =
      doc("users/charlie/profiles/7", 1000, mapOf<String, Any>()) // Different collection ID

    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7)
    val expectedDocs = listOf(doc2, doc6, doc1, doc3, doc5, doc4)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs).inOrder()
  }

  @Test
  fun `where on values`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .where(eqAny(field("score"), array(90L, 97L)))

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val doc4 = doc("users/diane", 1000, mapOf("score" to 97L))
    val doc5 =
      doc(
        "profiles/admin/users/bob",
        1000,
        mapOf("score" to 90L)
      ) // Different path, same collection ID

    val documents = listOf(doc1, doc2, doc3, doc4, doc5)
    // Expected: bob(users), charlie(users), diane(users), bob(profiles/admin/users)
    val expectedDocs = listOf(doc1, doc3, doc4, doc5)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs)
  }

  @Test
  fun `where inequality on values`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db).collectionGroup("users").where(gt(field("score"), 80L))

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val doc4 = doc("profiles/admin/users/bob", 1000, mapOf("score" to 90L)) // Different path

    val documents = listOf(doc1, doc2, doc3, doc4)
    // Expected: bob(users), charlie(users), bob(profiles)
    val expectedDocs = listOf(doc1, doc3, doc4)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs)
  }

  @Test
  fun `where not equal on values`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db).collectionGroup("users").where(neq(field("score"), 50L))

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L)) // This will be filtered out
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val doc4 = doc("profiles/admin/users/bob", 1000, mapOf("score" to 90L)) // Different path

    val documents = listOf(doc1, doc2, doc3, doc4)
    // Expected: bob(users), charlie(users), bob(profiles)
    val expectedDocs = listOf(doc1, doc3, doc4)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs)
  }

  @Test
  fun `where array contains values`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .where(arrayContains(field("rounds"), "round3"))

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L, "rounds" to listOf("round1", "round3")))
    val doc2 =
      doc("users/alice", 1000, mapOf("score" to 50L, "rounds" to listOf("round2", "round4")))
    val doc3 =
      doc(
        "users/charlie",
        1000,
        mapOf("score" to 97L, "rounds" to listOf("round2", "round3", "round4"))
      )
    val doc4 =
      doc(
        "profiles/admin/users/bob",
        1000,
        mapOf("score" to 90L, "rounds" to listOf("round1", "round3"))
      ) // Different path

    val documents = listOf(doc1, doc2, doc3, doc4)
    // Expected: bob(users), charlie(users), bob(profiles)
    val expectedDocs = listOf(doc1, doc3, doc4)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs)
  }

  @Test
  fun `sort on values`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db).collectionGroup("users").sort(field("score").descending())

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val doc4 = doc("profiles/admin/users/bob", 1000, mapOf("score" to 90L)) // Different path

    val documents = listOf(doc1, doc2, doc3, doc4)
    // Expected: charlie(97), bob(profiles, 90), bob(users, 90), alice(50)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    // Tie exists for doc1 and doc4, so check both orders are valid.
    assertThat(result).containsAtLeast(doc3, doc1, doc2).inOrder()
    assertThat(result).containsAtLeast(doc3, doc4, doc2).inOrder()
  }

  @Test
  fun `sort on values has dense semantics`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db).collectionGroup("users").sort(field("score").descending())

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("number" to 97L)) // Missing 'score'
    val doc4 = doc("profiles/admin/users/bob", 1000, mapOf("score" to 90L)) // Different path

    val documents = listOf(doc1, doc2, doc3, doc4)
    // Missing fields sort last in descending order (or first in ascending).
    // So, charlie (doc3) with missing 'score' comes after alice (doc2) with score 50.
    // Order for scores: 90, 90, 50, missing.
    val expectedDocs = listOf(doc4, doc1, doc2, doc3)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    // Tie exists for doc1 and doc4, so check both orders are valid.
    assertThat(result).containsAtLeast(doc1, doc2, doc3).inOrder()
    assertThat(result).containsAtLeast(doc4, doc2, doc3).inOrder()
  }

  @Test
  fun `sort on path`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .sort(field(PublicFieldPath.documentId()).ascending())

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val doc4 = doc("profiles/admin/users/bob", 1000, mapOf("score" to 90L)) // Different path

    val documents = listOf(doc1, doc2, doc3, doc4)
    // Expected: sorted by path:
    // profiles/admin/users/bob (doc4)
    // users/alice (doc2)
    // users/bob (doc1)
    // users/charlie (doc3)
    val expectedDocs = listOf(doc4, doc2, doc1, doc3)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs).inOrder()
  }

  @Test
  fun `limit`(): Unit = runBlocking {
    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .sort(field(PublicFieldPath.documentId()).ascending())
        .limit(2)

    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val doc4 = doc("profiles/admin/users/bob", 1000, mapOf("score" to 90L)) // Different path

    val documents = listOf(doc1, doc2, doc3, doc4)
    // Expected: sorted by path, then limited:
    // profiles/admin/users/bob (doc4)
    // users/alice (doc2)
    val expectedDocs = listOf(doc4, doc2)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs).inOrder()
  }
}
