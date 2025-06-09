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
import com.google.firebase.firestore.pipeline.Expr.Companion.add
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.exists
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.not
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class SortTests {

  private val db = TestUtil.firestore()

  @Test
  fun `empty ascending`(): Unit = runBlocking {
    val documents = emptyList<MutableDocument>()
    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `empty descending`(): Unit = runBlocking {
    val documents = emptyList<MutableDocument>()
    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("age").descending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `single result ascending`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val documents = listOf(doc1)
    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `single result ascending explicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field("age")))
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `single result ascending explicit not exists empty`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(not(exists(field("age"))))
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `single result ascending implicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("age").eq(10L))
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `single result descending`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val documents = listOf(doc1)
    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("age").descending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `single result descending explicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field("age")))
        .sort(field("age").descending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `single result descending implicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("age").eq(10L))
        .sort(field("age").descending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `multiple results ambiguous order`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("age").descending())

    // Order: doc3 (100.0), doc1 (75.5), doc2 (25.0), then doc4 and doc5 (10.0) are ambiguous
    // Firestore backend sorts by document key as a tie-breaker.
    // So expected order: doc3, doc1, doc2, doc4, doc5 (if 'd' < 'e') or doc3, doc1, doc2, doc5,
    // doc4 (if 'e' < 'd')
    // Since the C++ test uses UnorderedElementsAre, we'll use containsExactlyElementsIn.
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc3, doc1, doc2, doc4, doc5)).inOrder()
    // Actually, the local pipeline implementation might not guarantee tie-breaking by key unless
    // explicitly added.
    // The C++ test uses UnorderedElementsAre, which means the exact order of doc4 and doc5 is not
    // tested.
    // Let's stick to what the C++ test implies: the overall set is correct, but the order of tied
    // elements is not strictly defined by this single sort.
    // However, the local pipeline *does* sort by key as a final tie-breaker.
    // Expected: doc3 (100.0), doc1 (75.5), doc2 (25.0), doc4 (10.0, key d), doc5 (10.0, key e)
    // So the order should be doc3, doc1, doc2, doc4, doc5
    assertThat(result).containsExactly(doc3, doc1, doc2, doc4, doc5).inOrder()
  }

  @Test
  fun `multiple results ambiguous order explicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field("age")))
        .sort(field("age").descending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1, doc2, doc4, doc5).inOrder()
  }

  @Test
  fun `multiple results ambiguous order implicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("age").gt(0.0))
        .sort(field("age").descending())
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1, doc2, doc4, doc5).inOrder()
  }

  @Test
  fun `multiple results full order`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .sort(field("age").descending(), field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    // age desc: 100(c), 75.5(a), 25(b), 10(d), 10(e)
    // name asc for 10: diane(d), eric(e)
    // Expected: c, a, b, d, e
    assertThat(result).containsExactly(doc3, doc1, doc2, doc4, doc5).inOrder()
  }

  @Test
  fun `multiple results full order explicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field("age")))
        .where(exists(field("name")))
        .sort(field("age").descending(), field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1, doc2, doc4, doc5).inOrder()
  }

  @Test
  fun `multiple results full order explicit not exists empty`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob"))
    val doc3 = doc("users/c", 1000, mapOf("age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("other_name" to "diane")) // Matches
    val doc5 = doc("users/e", 1000, mapOf("other_age" to 10.0)) // Matches
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(not(exists(field("age"))))
        .where(not(exists(field("name"))))
        .sort(field("age").descending(), field("name").ascending())
    // Filtered: doc4, doc5
    // Sort by missing age (no op), then missing name (no op), then by key ascending.
    // d < e
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5).inOrder()
  }

  @Test
  fun `multiple results full order implicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("age").eq(field("age"))) // Implicit exists age
        .where(field("name").regexMatch(".*")) // Implicit exists name
        .sort(field("age").descending(), field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1, doc2, doc4, doc5).inOrder()
  }

  @Test
  fun `multiple results full order partial explicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field("name")))
        .sort(field("age").descending(), field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1, doc2, doc4, doc5).inOrder()
  }

  @Test
  fun `multiple results full order partial explicit not exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("age" to 25.0)) // name missing -> Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane")) // age missing, name exists
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric")) // age missing, name exists
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(not(exists(field("name")))) // Only doc2 matches
        .sort(field("age").descending(), field("name").descending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `multiple results full order partial explicit not exists sort on non exist field first`():
    Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("age" to 25.0)) // name missing -> Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane")) // age missing, name exists
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric")) // age missing, name exists
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(not(exists(field("name")))) // Only doc2 matches
        .sort(field("name").descending(), field("age").descending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `multiple results full order partial implicit exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(field("name").regexMatch(".*"))
        .sort(field("age").descending(), field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc1, doc2, doc4, doc5).inOrder()
  }

  @Test
  fun `missing field all fields`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db).collection("users").sort(field("not_age").descending())

    // Sorting by a missing field results in undefined order relative to each other,
    // but documents are secondarily sorted by key.
    // Since it's descending for not_age (all are null essentially), key order will be ascending.
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3, doc4, doc5).inOrder()
  }

  @Test
  fun `missing field with exist empty`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field("not_age")))
        .sort(field("not_age").descending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `missing field partial fields`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob")) // age missing
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane")) // age missing
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("age").ascending())

    // Missing fields sort first in ascending order, then by key. b < d
    // Then existing fields sorted by value: e (10.0) < a (75.5) < c (100.0)
    // Expected: doc2, doc4, doc5, doc1, doc3
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc4, doc5, doc1, doc3).inOrder()
  }

  @Test
  fun `missing field partial fields with exist`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob"))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane"))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field("age"))) // Filters to doc1, doc3, doc5
        .sort(field("age").ascending())

    // Sort remaining: doc5 (10.0), doc1 (75.5), doc3 (100.0)
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc5, doc1, doc3).inOrder()
  }

  @Test
  fun `missing field partial fields with not exist`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob")) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane")) // Match
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(not(exists(field("age")))) // Filters to doc2, doc4
        .sort(field("age").ascending()) // Sort by non-existent field, then key

    // Sort remaining by key: doc2, doc4
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc4).inOrder()
  }

  @Test
  fun `limit after sort`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .sort(field("age").ascending()) // Sort: d, e, b, a, c (key tie-break for d,e)
        .limit(2)

    // Expected: doc4, doc5
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5).inOrder()
  }

  @Test
  fun `limit after sort with exist`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("age" to 25.0)) // name missing
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane")) // age missing
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field("age"))) // Filter: a, b, c, e
        .sort(field("age").ascending()) // Sort: e (10), b (25), a (75.5), c (100)
        .limit(2) // Limit 2: e, b

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc5, doc2).inOrder()
  }

  @Test
  fun `limit after sort with not exist`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("age" to 25.0)) // name missing
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane")) // age missing -> Match
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric")) // age missing -> Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(not(exists(field("age")))) // Filter: d, e
        .sort(field("age").ascending()) // Sort by missing field -> key order: d, e
        .limit(2) // Limit 2: d, e

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5).inOrder()
  }

  @Test
  fun `limit zero after sort`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db).collection("users").sort(field("age").ascending()).limit(0)

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `limit before sort`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)
    // Note: Limit before sort has different semantics online vs offline.
    // Offline evaluation applies limit first based on implicit key order.
    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users") // C++ test uses CollectionGroupSource here
        .limit(1) // Limits to doc1 (key "users/a" is first by default key order)
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `limit before sort with exist`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane"))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .where(exists(field("age"))) // Filter: a,b,c,e. Implicit key order: a,b,c,e
        .limit(1) // Limits to doc1 (key "users/a")
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `limit before sort with not exist`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane")) // Match
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric")) // Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .where(not(exists(field("age")))) // Filter: d, e. Implicit key order: d, e
        .limit(1) // Limits to doc4 (key "users/d")
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun `limit before not exist filter`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane"))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric"))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .limit(2) // Limit to a, b (by key)
        .where(not(exists(field("age")))) // Filter out a, b (both have age)
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `limit zero before sort`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val documents = listOf(doc1)
    val pipeline =
      RealtimePipelineSource(db).collectionGroup("users").limit(0).sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `sort expression`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 30L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 50L))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 40L))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 20L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .sort(add(field("age"), constant(10L)).descending()) // age + 10

    // Sort by (age+10) desc:
    // doc3: 50+10 = 60
    // doc4: 40+10 = 50
    // doc2: 30+10 = 40
    // doc5: 20+10 = 30
    // doc1: 10+10 = 20
    // Expected: doc3, doc4, doc2, doc5, doc1
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc4, doc2, doc5, doc1).inOrder()
  }

  @Test
  fun `sort expression with exist`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val doc2 = doc("users/b", 1000, mapOf("age" to 30L)) // name missing
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 50L))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane")) // age missing
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 20L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .where(exists(field("age"))) // Filter: a, b, c, e
        .sort(add(field("age"), constant(10L)).descending())

    // Filtered: doc1 (10), doc2 (30), doc3 (50), doc5 (20)
    // Sort by (age+10) desc:
    // doc3: 50+10 = 60
    // doc2: 30+10 = 40
    // doc5: 20+10 = 30
    // doc1: 10+10 = 20
    // Expected: doc3, doc2, doc5, doc1
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc2, doc5, doc1).inOrder()
  }

  @Test
  fun `sort expression with not exist`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 10L))
    val doc2 = doc("users/b", 1000, mapOf("age" to 30L))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 50L))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane")) // age missing -> Match
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric")) // age missing -> Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .where(not(exists(field("age")))) // Filter: d, e
        .sort(add(field("age"), constant(10L)).descending()) // Sort by missing field -> key order

    // Filtered: doc4, doc5
    // Sort by (age+10) desc where age is missing. This means they are treated as null for the
    // expression.
    // Then tie-broken by key: d, e
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5).inOrder()
  }

  @Test
  fun `sort on path and other field on different stages`(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("name" to "alice", "age" to 40L))
    val doc2 = doc("users/2", 1000, mapOf("name" to "bob", "age" to 30L))
    val doc3 = doc("users/3", 1000, mapOf("name" to "charlie", "age" to 50L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field(PublicFieldPath.documentId()))) // Ensure __name__ is considered
        .sort(field(PublicFieldPath.documentId()).ascending()) // Sort by key: 1, 2, 3
        .sort(
          field("age").ascending()
        ) // Sort by age: 2(30), 1(40), 3(50) - Last sort takes precedence

    // The C++ test implies that the *last* sort stage defines the primary sort order.
    // This is different from how multiple orderBy clauses usually work in Firestore (they form a
    // composite sort).
    // However, if these are separate stages, the last one would indeed re-sort the entire output of
    // the previous.
    // Let's assume the Kotlin pipeline behaves this way for separate .orderBy() calls.
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1, doc3).inOrder()
  }

  @Test
  fun `sort on other field and path on different stages`(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("name" to "alice", "age" to 40L))
    val doc2 = doc("users/2", 1000, mapOf("name" to "bob", "age" to 30L))
    val doc3 = doc("users/3", 1000, mapOf("name" to "charlie", "age" to 50L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field(PublicFieldPath.documentId())))
        .sort(field("age").ascending()) // Sort by age: 2(30), 1(40), 3(50)
        .sort(field(PublicFieldPath.documentId()).ascending()) // Sort by key: 1, 2, 3

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3).inOrder()
  }

  // The C++ tests `SortOnKeyAndOtherFieldOnMultipleStages` and
  // `SortOnOtherFieldAndKeyOnMultipleStages`
  // are identical to the `Path` versions because `kDocumentKeyPath` is used.
  // These are effectively duplicates of the above two tests in Kotlin if we use
  // `PublicFieldPath.documentId()`.
  // I'll include them for completeness, mirroring the C++ structure.

  @Test
  fun `sort on key and other field on multiple stages`(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("name" to "alice", "age" to 40L))
    val doc2 = doc("users/2", 1000, mapOf("name" to "bob", "age" to 30L))
    val doc3 = doc("users/3", 1000, mapOf("name" to "charlie", "age" to 50L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field(PublicFieldPath.documentId())))
        .sort(field(PublicFieldPath.documentId()).ascending())
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1, doc3).inOrder()
  }

  @Test
  fun `sort on other field and key on multiple stages`(): Unit = runBlocking {
    val doc1 = doc("users/1", 1000, mapOf("name" to "alice", "age" to 40L))
    val doc2 = doc("users/2", 1000, mapOf("name" to "bob", "age" to 30L))
    val doc3 = doc("users/3", 1000, mapOf("name" to "charlie", "age" to 50L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("users")
        .where(exists(field(PublicFieldPath.documentId())))
        .sort(field("age").ascending())
        .sort(field(PublicFieldPath.documentId()).ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3).inOrder()
  }
}
