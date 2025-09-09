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
import com.google.firebase.firestore.pipeline.Expr.Companion.and
import com.google.firebase.firestore.pipeline.Expr.Companion.array
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.eqAny
import com.google.firebase.firestore.pipeline.Expr.Companion.exists
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.not
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
internal class WhereTests {

  private val db = TestUtil.firestore()

  @Test
  fun `empty database returns no results`(): Unit = runBlocking {
    val documents = emptyList<MutableDocument>()
    val pipeline =
      RealtimePipelineSource(TestUtil.firestore()).collection("users").where(field("age").gte(10L))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `duplicate conditions`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0)) // Match
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(and(field("age").gte(10.0), field("age").gte(20.0)))
    // age >= 10.0 AND age >= 20.0 => age >= 20.0
    // Matches: doc1 (75.5), doc2 (25.0), doc3 (100.0)
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3)
  }

  @Test
  fun `logical equivalent condition equal`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline1 =
      RealtimePipelineSource(TestUtil.firestore()).collection("users").where(field("age").eq(25.0))

    val pipeline2 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(constant(25.0).eq(field("age")))

    val result1 = runPipeline(pipeline1, listOf(*documents.toTypedArray())).toList()
    val result2 = runPipeline(pipeline2, listOf(*documents.toTypedArray())).toList()

    assertThat(result1).containsExactly(doc2)
    assertThat(result1).isEqualTo(result2)
  }

  @Test
  fun `logical equivalent condition and`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline1 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(and(field("age").gt(10.0), field("age").lt(70.0)))

    val pipeline2 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(and(field("age").lt(70.0), field("age").gt(10.0)))

    val result1 = runPipeline(pipeline1, listOf(*documents.toTypedArray())).toList()
    val result2 = runPipeline(pipeline2, listOf(*documents.toTypedArray())).toList()

    assertThat(result1).containsExactly(doc2)
    assertThat(result1).isEqualTo(result2)
  }

  @Test
  fun `logical equivalent condition or`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0)) // Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline1 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(or(field("age").lt(10.0), field("age").gt(80.0)))

    val pipeline2 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(or(field("age").gt(80.0), field("age").lt(10.0)))
    val result1 = runPipeline(pipeline1, listOf(*documents.toTypedArray())).toList()
    val result2 = runPipeline(pipeline2, listOf(*documents.toTypedArray())).toList()

    assertThat(result1).containsExactly(doc3)
    assertThat(result1).isEqualTo(result2)
  }

  @Test
  fun `logical equivalent condition in`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Match
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val documents = listOf(doc1, doc2, doc3)

    val values = listOf("alice", "matthew", "joe")

    val pipeline1 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(field("name").eqAny(values))

    val pipeline2 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(eqAny(field("name"), array(values)))

    val result1 = runPipeline(pipeline1, listOf(*documents.toTypedArray())).toList()
    val result2 = runPipeline(pipeline2, listOf(*documents.toTypedArray())).toList()

    assertThat(result1).containsExactly(doc1)
    assertThat(result1).isEqualTo(result2)
  }

  @Test
  fun `repeated stages`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Match
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0)) // Match
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(field("age").gte(10.0))
        .where(field("age").gte(20.0))

    // age >= 10.0 THEN age >= 20.0 => age >= 20.0
    // Matches: doc1 (75.5), doc2 (25.0), doc3 (100.0)
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3)
  }

  @Test
  fun `composite equalities`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("height" to 60L, "age" to 75L))
    val doc2 = doc("users/b", 1000, mapOf("height" to 55L, "age" to 50L))
    val doc3 = doc("users/c", 1000, mapOf("height" to 55.0, "age" to 75L)) // Match
    val doc4 = doc("users/d", 1000, mapOf("height" to 50L, "age" to 41L))
    val doc5 = doc("users/e", 1000, mapOf("height" to 80L, "age" to 75L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(field("age").eq(75L))
        .where(field("height").eq(55L)) // 55L will also match 55.0

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `composite inequalities`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("height" to 60L, "age" to 75L)) // Match
    val doc2 = doc("users/b", 1000, mapOf("height" to 55L, "age" to 50L))
    val doc3 = doc("users/c", 1000, mapOf("height" to 55.0, "age" to 75L)) // Match
    val doc4 = doc("users/d", 1000, mapOf("height" to 50L, "age" to 41L))
    val doc5 = doc("users/e", 1000, mapOf("height" to 80L, "age" to 75L))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(field("age").gt(50L))
        .where(field("height").lt(75L))

    // age > 50 AND height < 75
    // doc1: 75 > 50 (T) AND 60 < 75 (T) -> True
    // doc2: 50 > 50 (F)
    // doc3: 75 > 50 (T) AND 55.0 < 75 (T) -> True
    // doc4: 41 > 50 (F)
    // doc5: 75 > 50 (T) AND 80 < 75 (F) -> False
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3)
  }

  @Test
  fun `composite non seekable`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("first" to "alice", "last" to "smith"))
    val doc2 = doc("users/b", 1000, mapOf("first" to "bob", "last" to "smith"))
    val doc3 = doc("users/c", 1000, mapOf("first" to "charlie", "last" to "baker")) // Match
    val doc4 = doc("users/d", 1000, mapOf("first" to "diane", "last" to "miller")) // Match
    val doc5 = doc("users/e", 1000, mapOf("first" to "eric", "last" to "davis"))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        // Using regexMatch for LIKE '%a%' -> ".*a.*"
        .where(field("first").regexMatch(".*a.*"))
        // Using regexMatch for LIKE '%er' -> ".*er$"
        .where(field("last").regexMatch(".*er$"))

    // first contains 'a' AND last ends with 'er'
    // doc1: alice (yes), smith (no)
    // doc2: bob (no), smith (no)
    // doc3: charlie (yes), baker (yes) -> Match
    // doc4: diane (yes), miller (yes) -> Match
    // doc5: eric (no), davis (no)
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc4)
  }

  @Test
  fun `composite mixed`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf("first" to "alice", "last" to "smith", "age" to 75L, "height" to 40L)
      )
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf("first" to "bob", "last" to "smith", "age" to 75L, "height" to 50L)
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("first" to "charlie", "last" to "baker", "age" to 75L, "height" to 50L)
      ) // Match
    val doc4 =
      doc(
        "users/d",
        1000,
        mapOf("first" to "diane", "last" to "miller", "age" to 75L, "height" to 50L)
      ) // Match
    val doc5 =
      doc(
        "users/e",
        1000,
        mapOf("first" to "eric", "last" to "davis", "age" to 80L, "height" to 50L)
      )
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(field("age").eq(75L))
        .where(field("height").gt(45L))
        .where(field("last").regexMatch(".*er$")) // ends with 'er'

    // age == 75 AND height > 45 AND last ends with 'er'
    // doc1: 75==75 (T), 40>45 (F) -> False
    // doc2: 75==75 (T), 50>45 (T), smith ends er (F) -> False
    // doc3: 75==75 (T), 50>45 (T), baker ends er (T) -> True
    // doc4: 75==75 (T), 50>45 (T), miller ends er (T) -> True
    // doc5: 80==75 (F) -> False
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc4)
  }

  @Test
  fun exists(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Match
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie")) // Match
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0))
    val doc5 = doc("users/e", 1000, mapOf("other" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore()).collection("users").where(exists(field("name")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3)
  }

  @Test
  fun `not exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie"))
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0)) // Match
    val doc5 = doc("users/e", 1000, mapOf("other" to true)) // Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(not(exists(field("name"))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5)
  }

  @Test
  fun `not not exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Match
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie")) // Match
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0))
    val doc5 = doc("users/e", 1000, mapOf("other" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(not(not(exists(field("name")))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3)
  }

  @Test
  fun `exists and exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Match
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie"))
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0))
    val doc5 = doc("users/e", 1000, mapOf("other" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(and(exists(field("name")), exists(field("age"))))
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2)
  }

  @Test
  fun `exists or exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Match
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie")) // Match
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0)) // Match
    val doc5 = doc("users/e", 1000, mapOf("other" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(or(exists(field("name")), exists(field("age"))))
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3, doc4)
  }

  @Test
  fun `not exists and exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie")) // Match
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0)) // Match
    val doc5 = doc("users/e", 1000, mapOf("other" to true)) // Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(not(and(exists(field("name")), exists(field("age")))))
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc4, doc5)
  }

  @Test
  fun `not exists or exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie"))
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0))
    val doc5 = doc("users/e", 1000, mapOf("other" to true)) // Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(not(or(exists(field("name")), exists(field("age")))))
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc5)
  }

  @Test
  fun `not exists xor exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Match
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie"))
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0))
    val doc5 = doc("users/e", 1000, mapOf("other" to true)) // Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(not(xor(exists(field("name")), exists(field("age")))))
    // NOT ( (name exists AND NOT age exists) OR (NOT name exists AND age exists) )
    // = (name exists AND age exists) OR (NOT name exists AND NOT age exists)
    // Matches: doc1, doc2, doc5
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc5)
  }

  @Test
  fun `and not exists not exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie"))
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0))
    val doc5 = doc("users/e", 1000, mapOf("other" to true)) // Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(and(not(exists(field("name"))), not(exists(field("age")))))
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc5)
  }

  @Test
  fun `or not exists not exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie")) // Match
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0)) // Match
    val doc5 = doc("users/e", 1000, mapOf("other" to true)) // Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(or(not(exists(field("name"))), not(exists(field("age")))))
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc4, doc5)
  }

  @Test
  fun `xor not exists not exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie")) // Match
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0)) // Match
    val doc5 = doc("users/e", 1000, mapOf("other" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(xor(not(exists(field("name"))), not(exists(field("age")))))
    // (NOT name exists AND NOT (NOT age exists)) OR (NOT (NOT name exists) AND NOT age exists)
    // (NOT name exists AND age exists) OR (name exists AND NOT age exists)
    // Matches: doc3, doc4
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc4)
  }

  @Test
  fun `and not exists exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie"))
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0)) // Match
    val doc5 = doc("users/e", 1000, mapOf("other" to true))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(and(not(exists(field("name"))), exists(field("age"))))
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun `or not exists exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Match
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie"))
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0)) // Match
    val doc5 = doc("users/e", 1000, mapOf("other" to true)) // Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(or(not(exists(field("name"))), exists(field("age"))))
    // (NOT name exists) OR (age exists)
    // Matches: doc1, doc2, doc4, doc5
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc4, doc5)
  }

  @Test
  fun `xor not exists exists`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Match
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Match
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie"))
    val doc4 = doc("users/d", 1000, mapOf("age" to 30.0))
    val doc5 = doc("users/e", 1000, mapOf("other" to true)) // Match
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(xor(not(exists(field("name"))), exists(field("age"))))
    // (NOT name exists AND NOT age exists) OR (name exists AND age exists)
    // Matches: doc1, doc2, doc5
    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc5)
  }

  @Test
  fun `and expression logically equivalent to separated stages`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("a" to 1L, "b" to 1L))
    val doc2 = doc("users/b", 1000, mapOf("a" to 1L, "b" to 2L)) // Match
    val doc3 = doc("users/c", 1000, mapOf("a" to 2L, "b" to 2L))
    val documents = listOf(doc1, doc2, doc3)

    val equalityArgument1 = field("a").eq(1L)
    val equalityArgument2 = field("b").eq(2L)

    // Combined AND
    val pipelineAnd1 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(and(equalityArgument1, equalityArgument2))
    val resultAnd1 = runPipeline(pipelineAnd1, listOf(*documents.toTypedArray())).toList()
    assertThat(resultAnd1).containsExactly(doc2)

    // Combined AND (reversed order)
    val pipelineAnd2 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(and(equalityArgument2, equalityArgument1))
    val resultAnd2 = runPipeline(pipelineAnd2, listOf(*documents.toTypedArray())).toList()
    assertThat(resultAnd2).containsExactly(doc2)

    // Separate Stages
    val pipelineSep1 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(equalityArgument1)
        .where(equalityArgument2)
    val resultSep1 = runPipeline(pipelineSep1, listOf(*documents.toTypedArray())).toList()
    assertThat(resultSep1).containsExactly(doc2)

    // Separate Stages (reversed order)
    val pipelineSep2 =
      RealtimePipelineSource(TestUtil.firestore())
        .collection("users")
        .where(equalityArgument2)
        .where(equalityArgument1)
    val resultSep2 = runPipeline(pipelineSep2, listOf(*documents.toTypedArray())).toList()
    assertThat(resultSep2).containsExactly(doc2)
  }
}
