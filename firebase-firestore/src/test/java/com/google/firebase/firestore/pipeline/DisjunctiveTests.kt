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
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContainsAll
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.eq
import com.google.firebase.firestore.pipeline.Expr.Companion.eqAny
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.gt
import com.google.firebase.firestore.pipeline.Expr.Companion.gte
import com.google.firebase.firestore.pipeline.Expr.Companion.isNan
import com.google.firebase.firestore.pipeline.Expr.Companion.isNull
import com.google.firebase.firestore.pipeline.Expr.Companion.lt
import com.google.firebase.firestore.pipeline.Expr.Companion.lte
import com.google.firebase.firestore.pipeline.Expr.Companion.neq
import com.google.firebase.firestore.pipeline.Expr.Companion.not
import com.google.firebase.firestore.pipeline.Expr.Companion.notEqAny
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
internal class DisjunctiveTests {

  private val db = TestUtil.firestore()

  @Test
  fun `basic eqAny`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          field("name")
            .eqAny(
              array(
                constant("alice"),
                constant("bob"),
                constant("charlie"),
                constant("diane"),
                constant("eric")
              )
            )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3, doc4, doc5))
  }

  @Test
  fun `multiple eqAny`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name")
              .eqAny(
                array(
                  constant("alice"),
                  constant("bob"),
                  constant("charlie"),
                  constant("diane"),
                  constant("eric")
                )
              ),
            field("age").eqAny(array(constant(10.0), constant(25.0)))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc4, doc5))
  }

  @Test
  fun `eqAny multiple stages`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          field("name")
            .eqAny(
              array(
                constant("alice"),
                constant("bob"),
                constant("charlie"),
                constant("diane"),
                constant("eric")
              )
            )
        )
        .where(field("age").eqAny(array(constant(10.0), constant(25.0))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc4, doc5))
  }

  @Test
  fun `multiple eqAnys with or`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          or(
            field("name").eqAny(array(constant("alice"), constant("bob"))),
            field("age").eqAny(array(constant(10.0), constant(25.0)))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc4, doc5))
  }

  @Test
  fun `eqAny on collectionGroup`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("other_users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("root/child/users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("root/child/other_users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .where(
          field("name")
            .eqAny(array(constant("alice"), constant("bob"), constant("diane"), constant("eric")))
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc4))
  }

  @Test
  fun `eqAny with sort on different field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0)) // Not matched
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          field("name")
            .eqAny(array(constant("alice"), constant("bob"), constant("diane"), constant("eric")))
        )
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5, doc2, doc1).inOrder()
  }

  @Test
  fun `eqAny with sort on eqAny field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0)) // Not matched
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          field("name")
            .eqAny(array(constant("alice"), constant("bob"), constant("diane"), constant("eric")))
        )
        .sort(field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc4, doc5).inOrder()
  }

  @Test
  fun `eqAny with additional equality different fields`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name")
              .eqAny(
                array(
                  constant("alice"),
                  constant("bob"),
                  constant("charlie"),
                  constant("diane"),
                  constant("eric")
                )
              ),
            field("age").eq(constant(10.0))
          )
        )
        .sort(field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5).inOrder()
  }

  @Test
  fun `eqAny with additional equality same field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").eqAny(array(constant("alice"), constant("diane"), constant("eric"))),
            field("name").eq(constant("eric"))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc5)
  }

  @Test
  fun `eqAny with additional equality same field empty result`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").eqAny(array(constant("alice"), constant("bob"))),
            field("name").eq(constant("other"))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `eqAny with inequalities exclusive range`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0)) // Not matched
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name")
              .eqAny(
                array(constant("alice"), constant("bob"), constant("charlie"), constant("diane"))
              ),
            field("age").gt(constant(10.0)),
            field("age").lt(constant(100.0))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun `eqAny with inequalities inclusive range`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0)) // Not matched
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name")
              .eqAny(
                array(constant("alice"), constant("bob"), constant("charlie"), constant("diane"))
              ),
            field("age").gte(constant(10.0)),
            field("age").lte(constant(100.0))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3, doc4))
  }

  @Test
  fun `eqAny with inequalities and sort`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0)) // Not matched
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name")
              .eqAny(
                array(constant("alice"), constant("bob"), constant("charlie"), constant("diane"))
              ),
            field("age").gt(constant(10.0)),
            field("age").lt(constant(100.0))
          )
        )
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1).inOrder()
  }

  @Test
  fun `eqAny with notEqual`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0)) // Not matched
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name")
              .eqAny(
                array(constant("alice"), constant("bob"), constant("charlie"), constant("diane"))
              ),
            field("age").neq(constant(100.0))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc4))
  }

  @Test
  fun `eqAny sort on eqAny field again`(): Unit = runBlocking { // Renamed from C++ duplicate
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0)) // Not matched
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          field("name")
            .eqAny(
              array(constant("alice"), constant("bob"), constant("charlie"), constant("diane"))
            )
        )
        .sort(field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3, doc4).inOrder()
  }

  @Test
  fun `eqAny single value sort on in field ambiguous order`(): Unit = runBlocking {
    val doc1 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0)) // Not matched
    val doc2 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc3 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("age").eqAny(array(constant(10.0))))
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    // Order of doc2 and doc3 is by key after sorting by constant age
    assertThat(result).containsExactly(doc2, doc3).inOrder()
  }

  @Test
  fun `eqAny with extra equality sort on eqAny field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name")
              .eqAny(
                array(
                  constant("alice"),
                  constant("bob"),
                  constant("charlie"),
                  constant("diane"),
                  constant("eric")
                )
              ),
            field("age").eq(constant(10.0))
          )
        )
        .sort(field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5).inOrder()
  }

  @Test
  fun `eqAny with extra equality sort on equality`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name")
              .eqAny(
                array(
                  constant("alice"),
                  constant("bob"),
                  constant("charlie"),
                  constant("diane"),
                  constant("eric")
                )
              ),
            field("age").eq(constant(10.0))
          )
        )
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5).inOrder() // Sorted by key after age
  }

  @Test
  fun `eqAny with inequality on same field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5)) // Not matched
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0)) // Not matched
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0)) // Not matched
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("age").eqAny(array(constant(10.0), constant(25.0), constant(100.0))),
            field("age").gt(constant(20.0))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3))
  }

  @Test
  fun `eqAny with different inequality sort on eqAny field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0)) // Not matched
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0)) // Not matched
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name")
              .eqAny(
                array(constant("alice"), constant("bob"), constant("charlie"), constant("diane"))
              ),
            field("age").gt(constant(20.0))
          )
        )
        .sort(field("age").ascending()) // C++ test sorts by age (inequality field)

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1, doc3).inOrder()
  }

  @Test
  fun `eqAny contains null`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to null, "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("age" to 100.0)) // name missing
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("name").eqAny(array(Expr.nullValue(), constant("alice"))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1) // Nulls are not matched by IN
  }

  @Test
  fun `arrayContains null`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("field" to listOf(null, 42L)))
    val doc2 = doc("users/b", 1000, mapOf("field" to listOf(101L, null)))
    val doc3 = doc("users/c", 1000, mapOf("field" to listOf<Any?>(null)))
    val doc4 = doc("users/d", 1000, mapOf("field" to listOf("foo", "bar")))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(Expr.arrayContains(field("field"), Expr.nullValue()))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty() // arrayContains does not match null
  }

  @Test
  fun `arrayContainsAny null`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("field" to listOf(null, 42L)))
    val doc2 = doc("users/b", 1000, mapOf("field" to listOf(101L, null)))
    val doc3 = doc("users/c", 1000, mapOf("field" to listOf("foo", "bar")))
    val doc4 = doc("users/d", 1000, mapOf("not_field" to listOf("foo", "bar")))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("field").arrayContainsAny(array(Expr.nullValue(), constant("foo"))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3) // arrayContainsAny does not match null
  }

  @Test
  fun `eqAny contains null only`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to null))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("age").eqAny(array(Expr.nullValue())))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty() // Nulls are not matched by IN
  }

  @Test
  fun `basic arrayContainsAny`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "groups" to listOf(1L, 2L, 3L)))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "groups" to listOf(1L, 2L, 4L)))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "groups" to listOf(2L, 3L, 4L)))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "groups" to listOf(2L, 3L, 5L)))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "groups" to listOf(3L, 4L, 5L)))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("groups").arrayContainsAny(array(constant(1L), constant(5L))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc4, doc5))
  }

  @Test
  fun `multiple arrayContainsAny`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf("name" to "alice", "groups" to listOf(1L, 2L, 3L), "records" to listOf("a", "b", "c"))
      )
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf("name" to "bob", "groups" to listOf(1L, 2L, 4L), "records" to listOf("b", "c", "d"))
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf(
          "name" to "charlie",
          "groups" to listOf(2L, 3L, 4L),
          "records" to listOf("b", "c", "e")
        )
      )
    val doc4 =
      doc(
        "users/d",
        1000,
        mapOf("name" to "diane", "groups" to listOf(2L, 3L, 5L), "records" to listOf("c", "d", "e"))
      )
    val doc5 =
      doc(
        "users/e",
        1000,
        mapOf("name" to "eric", "groups" to listOf(3L, 4L, 5L), "records" to listOf("c", "d", "f"))
      )
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("groups").arrayContainsAny(array(constant(1L), constant(5L))),
            field("records").arrayContainsAny(array(constant("a"), constant("e")))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc4))
  }

  @Test
  fun `arrayContainsAny with inequality`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "groups" to listOf(1L, 2L, 3L)))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "groups" to listOf(1L, 2L, 4L)))
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("name" to "charlie", "groups" to listOf(2L, 3L, 4L))
      ) // Filtered by LT
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "groups" to listOf(2L, 3L, 5L)))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "groups" to listOf(3L, 4L, 5L)))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("groups").arrayContainsAny(array(constant(1L), constant(5L))),
            field("groups").lt(array(constant(3L), constant(4L), constant(5L)))
          )
        )
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc4))
  }

  @Test
  fun `arrayContainsAny with in`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "groups" to listOf(1L, 2L, 3L)))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "groups" to listOf(1L, 2L, 4L)))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "groups" to listOf(2L, 3L, 4L)))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "groups" to listOf(2L, 3L, 5L)))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "groups" to listOf(3L, 4L, 5L)))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("groups").arrayContainsAny(array(constant(1L), constant(5L))),
            field("name").eqAny(array(constant("alice"), constant("bob")))
          )
        )
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun `basic or`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("name").eq(constant("bob")), field("age").eq(constant(10.0))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc4))
  }

  @Test
  fun `multiple or`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          or(
            field("name").eq(constant("bob")),
            field("name").eq(constant("diane")),
            field("age").eq(constant(25.0)),
            field("age").eq(constant(100.0))
          )
        )
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3, doc4))
  }

  @Test
  fun `or multiple stages`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("name").eq(constant("bob")), field("age").eq(constant(10.0))))
        .where(or(field("name").eq(constant("diane")), field("age").eq(constant(100.0))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4) // (name=bob OR age=10) AND (name=diane OR age=100)
  }

  @Test
  fun `or two conjunctions`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          or(
            and(field("name").eq(constant("bob")), field("age").eq(constant(25.0))),
            and(field("name").eq(constant("diane")), field("age").eq(constant(10.0)))
          )
        )
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc4))
  }

  @Test
  fun `or with in and`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            or(field("name").eq(constant("bob")), field("age").eq(constant(10.0))),
            field("age").lt(constant(80.0))
          )
        )
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc4))
  }

  @Test
  fun `and of two ors`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            or(field("name").eq(constant("bob")), field("age").eq(constant(10.0))),
            or(field("name").eq(constant("diane")), field("age").eq(constant(100.0)))
          )
        )
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4)
  }

  @Test
  fun `or of two ors`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          or(
            or(field("name").eq(constant("bob")), field("age").eq(constant(10.0))),
            or(field("name").eq(constant("diane")), field("age").eq(constant(100.0)))
          )
        )
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3, doc4))
  }

  @Test
  fun `or with empty range in one disjunction`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          or(
            field("name").eq(constant("bob")),
            and(field("age").eq(constant(10.0)), field("age").gt(constant(20.0)))
          )
        )
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `or with sort`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("name").eq(constant("diane")), field("age").gt(constant(20.0))))
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc2, doc1, doc3).inOrder()
  }

  @Test
  fun `or with inequality and sort same field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Not matched
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("age").lt(constant(20.0)), field("age").gt(constant(50.0))))
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc1, doc3).inOrder()
  }

  @Test
  fun `or with inequality and sort different fields`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0)) // Not matched
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("age").lt(constant(20.0)), field("age").gt(constant(50.0))))
        .sort(field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3, doc4).inOrder()
  }

  @Test
  fun `or with inequality and sort multiple fields`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 25.0, "height" to 170.0))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0, "height" to 180.0))
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("name" to "charlie", "age" to 100.0, "height" to 155.0)
      ) // Not matched
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0, "height" to 150.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 25.0, "height" to 170.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("age").lt(constant(80.0)), field("height").gt(constant(160.0))))
        .sort(field("age").ascending(), field("height").descending(), field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc2, doc1, doc5).inOrder()
  }

  @Test
  fun `or with sort on partial missing field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "diane")) // age missing
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "height" to 150.0)) // age missing
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("name").eq(constant("diane")), field("age").gt(constant(20.0))))
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc4, doc2, doc1).inOrder()
  }

  @Test
  fun `or with limit`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("name").eq(constant("diane")), field("age").gt(constant(20.0))))
        .sort(field("age").ascending())
        .limit(2)

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc2).inOrder()
  }

  @Test
  fun `or isNull and eq on same field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("a" to 1L))
    val doc2 = doc("users/b", 1000, mapOf("a" to 1.0))
    val doc3 = doc("users/c", 1000, mapOf("a" to 1L, "b" to 1L))
    val doc4 = doc("users/d", 1000, mapOf("a" to null))
    val doc5 = doc("users/e", 1000, mapOf("a" to Double.NaN))
    val doc6 = doc("users/f", 1000, mapOf("b" to "abc")) // 'a' missing
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("a").eq(constant(1L)), isNull(field("a"))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    // C++ test expects 1.0 to match 1L in this context.
    // isNull matches explicit nulls.
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3, doc4))
  }

  @Test
  fun `or isNull and eq on different field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("a" to 1L))
    val doc2 = doc("users/b", 1000, mapOf("a" to 1.0))
    val doc3 = doc("users/c", 1000, mapOf("a" to 1L, "b" to 1L))
    val doc4 = doc("users/d", 1000, mapOf("a" to null))
    val doc5 = doc("users/e", 1000, mapOf("a" to Double.NaN))
    val doc6 = doc("users/f", 1000, mapOf("b" to "abc"))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("b").eq(constant(1L)), isNull(field("a"))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc3, doc4))
  }

  @Test
  fun `or isNotNull and eq on same field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("a" to 1L))
    val doc2 = doc("users/b", 1000, mapOf("a" to 1.0))
    val doc3 = doc("users/c", 1000, mapOf("a" to 1L, "b" to 1L))
    val doc4 = doc("users/d", 1000, mapOf("a" to null))
    val doc5 = doc("users/e", 1000, mapOf("a" to Double.NaN))
    val doc6 = doc("users/f", 1000, mapOf("b" to "abc")) // 'a' missing
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("a").gt(constant(1L)), not(isNull(field("a")))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    // a > 1L (none) OR a IS NOT NULL (doc1, doc2, doc3, doc5)
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3, doc5))
  }

  @Test
  fun `or isNotNull and eq on different field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("a" to 1L))
    val doc2 = doc("users/b", 1000, mapOf("a" to 1.0))
    val doc3 = doc("users/c", 1000, mapOf("a" to 1L, "b" to 1L))
    val doc4 = doc("users/d", 1000, mapOf("a" to null))
    val doc5 = doc("users/e", 1000, mapOf("a" to Double.NaN))
    val doc6 = doc("users/f", 1000, mapOf("b" to "abc"))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(field("b").eq(constant(1L)), not(isNull(field("a")))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    // b == 1L (doc3) OR a IS NOT NULL (doc1, doc2, doc3, doc5)
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3, doc5))
  }

  @Test
  fun `or isNull and isNaN on same field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("a" to null))
    val doc2 = doc("users/b", 1000, mapOf("a" to Double.NaN))
    val doc3 = doc("users/c", 1000, mapOf("a" to "abc"))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(isNull(field("a")), isNan(field("a"))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2))
  }

  @Test
  fun `or isNull and isNaN on different field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("a" to null))
    val doc2 = doc("users/b", 1000, mapOf("a" to Double.NaN))
    val doc3 = doc("users/c", 1000, mapOf("a" to "abc"))
    val doc4 = doc("users/d", 1000, mapOf("b" to null))
    val doc5 = doc("users/e", 1000, mapOf("b" to Double.NaN))
    val doc6 = doc("users/f", 1000, mapOf("b" to "abc"))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(or(isNull(field("a")), isNan(field("b"))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc5))
  }

  @Test
  fun `basic notEqAny`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("name").notEqAny(array(constant("alice"), constant("bob"))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc3, doc4, doc5))
  }

  @Test
  fun `multiple notEqAnys`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("bob"))),
            field("age").notEqAny(array(constant(10.0), constant(25.0)))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `multiple notEqAnys with or`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          or(
            field("name").notEqAny(array(constant("alice"), constant("bob"))),
            field("age").notEqAny(array(constant(10.0), constant(25.0)))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3, doc4, doc5))
  }

  @Test
  fun `notEqAny on collectionGroup`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("other_users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("root/child/users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("root/child/other_users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collectionGroup("users")
        .where(field("name").notEqAny(array(constant("alice"), constant("bob"), constant("diane"))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3)
  }

  @Test
  fun `notEqAny with sort`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("name").notEqAny(array(constant("alice"), constant("diane"))))
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc5, doc2, doc3).inOrder()
  }

  @Test
  fun `notEqAny with additional equality different fields`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("bob"))),
            field("age").eq(constant(10.0))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc4, doc5))
  }

  @Test
  fun `notEqAny with additional equality same field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("diane"))),
            field("name").eq(constant("eric"))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc5)
  }

  @Test
  fun `notEqAny with inequalities exclusive range`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("charlie"))),
            field("age").gt(constant(10.0)),
            field("age").lt(constant(100.0))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `notEqAny with inequalities inclusive range`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("bob"), constant("eric"))),
            field("age").gte(constant(10.0)),
            field("age").lte(constant(100.0))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc3, doc4))
  }

  @Test
  fun `notEqAny with inequalities and sort`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("diane"))),
            field("age").gt(constant(10.0)),
            field("age").lte(constant(100.0))
          )
        )
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc3).inOrder()
  }

  @Test
  fun `notEqAny with notEqual`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("bob"))),
            field("age").neq(constant(100.0))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc4, doc5))
  }

  @Test
  fun `notEqAny sort on notEqAny field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("name").notEqAny(array(constant("alice"), constant("bob"))))
        .sort(field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc4, doc5).inOrder()
  }

  @Test
  fun `notEqAny single value sort on notEqAny field ambiguous order`(): Unit = runBlocking {
    val doc1 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc2 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc3 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("age").notEqAny(array(constant(100.0))))
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc3).inOrder() // Sorted by key after age
  }

  @Test
  fun `notEqAny with extra equality sort on notEqAny field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("bob"))),
            field("age").eq(constant(10.0))
          )
        )
        .sort(field("name").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5).inOrder()
  }

  @Test
  fun `notEqAny with extra equality sort on equality`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("bob"))),
            field("age").eq(constant(10.0))
          )
        )
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc4, doc5).inOrder() // Sorted by key after age
  }

  @Test
  fun `notEqAny with inequality on same field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("age").notEqAny(array(constant(10.0), constant(100.0))),
            field("age").gt(constant(20.0))
          )
        )
        .sort(field("age").ascending())

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1).inOrder()
  }

  @Test
  fun `notEqAny with different inequality sort on in field`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 75.5))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 10.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          and(
            field("name").notEqAny(array(constant("alice"), constant("diane"))),
            field("age").gt(constant(20.0))
          )
        )
        .sort(field("age").ascending()) // C++ test sorts by age (inequality field)

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc3).inOrder()
  }

  @Test
  fun `no limit on num of disjunctions`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("name" to "alice", "age" to 25.0, "height" to 170.0))
    val doc2 = doc("users/b", 1000, mapOf("name" to "bob", "age" to 25.0, "height" to 180.0))
    val doc3 = doc("users/c", 1000, mapOf("name" to "charlie", "age" to 100.0, "height" to 155.0))
    val doc4 = doc("users/d", 1000, mapOf("name" to "diane", "age" to 10.0, "height" to 150.0))
    val doc5 = doc("users/e", 1000, mapOf("name" to "eric", "age" to 25.0, "height" to 170.0))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          or(
            field("name").eq(constant("alice")),
            field("name").eq(constant("bob")),
            field("name").eq(constant("charlie")),
            field("name").eq(constant("diane")),
            field("age").eq(constant(10.0)),
            field("age").eq(constant(25.0)),
            field("age").eq(constant(40.0)), // No doc matches this
            field("age").eq(constant(100.0)),
            field("height").eq(constant(150.0)),
            field("height").eq(constant(160.0)), // No doc matches this
            field("height").eq(constant(170.0)),
            field("height").eq(constant(180.0))
          )
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc2, doc3, doc4, doc5))
  }

  @Test
  fun `eqAny duplicate values`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          field("score").eqAny(array(constant(50L), constant(97L), constant(97L), constant(97L)))
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc2, doc3))
  }

  @Test
  fun `notEqAny duplicate values`(): Unit = runBlocking {
    val doc1 = doc("users/bob", 1000, mapOf("score" to 90L))
    val doc2 = doc("users/alice", 1000, mapOf("score" to 50L))
    val doc3 = doc("users/charlie", 1000, mapOf("score" to 97L))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("score").notEqAny(array(constant(50L), constant(50L))))

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(listOf(doc1, doc3))
  }

  @Test
  fun `arrayContainsAny duplicate values`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("scores" to listOf(1L, 2L, 3L)))
    val doc2 = doc("users/b", 1000, mapOf("scores" to listOf(4L, 5L, 6L)))
    val doc3 = doc("users/c", 1000, mapOf("scores" to listOf(7L, 8L, 9L)))
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          field("scores")
            .arrayContainsAny(array(constant(1L), constant(2L), constant(2L), constant(2L)))
        )

    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `arrayContainsAll duplicate values`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("scores" to listOf(1L, 2L, 3L)))
    val doc2 = doc("users/b", 1000, mapOf("scores" to listOf(1L, 2L, 2L, 2L, 3L)))
    val documents = listOf(doc1, doc2)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          field("scores")
            .arrayContainsAll(
              array(constant(1L), constant(2L), constant(2L), constant(2L), constant(3L))
            )
        )
    val result = runPipeline(db, pipeline, flowOf(*documents.toTypedArray())).toList()
    // The C++ test `EXPECT_THAT(RunPipeline(pipeline, documents), ElementsAre(doc1, doc2));`
    // indicates an ordered check. Aligning with this.
    assertThat(result).containsExactly(doc1, doc2).inOrder()
  }
}
