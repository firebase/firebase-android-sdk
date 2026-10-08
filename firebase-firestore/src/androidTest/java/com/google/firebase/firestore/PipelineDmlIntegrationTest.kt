// Copyright 2026 Google LLC
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

package com.google.firebase.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.Pipeline.ExecuteOptions
import com.google.firebase.firestore.pipeline.Expression.Companion.add
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.equal
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.multiply
import com.google.firebase.firestore.testutil.IntegrationTestUtil
import com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor
import com.google.firebase.firestore.testutil.IntegrationTestUtil.writeAllDocs
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipelineDmlIntegrationTest {
  private lateinit var db: FirebaseFirestore
  private lateinit var collRef: CollectionReference

  private val testBookDocs: Map<String, Map<String, Any>> =
    mapOf(
      "book1" to
        mapOf(
          "title" to "The Hitchhiker's Guide to the Galaxy",
          "author" to "Douglas Adams",
          "genre" to "Science Fiction",
          "rating" to 4.2,
          "awards" to mapOf("hugo" to true, "nebula" to false)
        ),
      "book2" to
        mapOf(
          "title" to "Pride and Prejudice",
          "author" to "Jane Austen",
          "genre" to "Romance",
          "rating" to 4.5
        ),
      "book3" to
        mapOf(
          "title" to "One Hundred Years of Solitude",
          "author" to "Gabriel García Márquez",
          "genre" to "Magical Realism",
          "rating" to 4.3
        ),
      "book10" to
        mapOf(
          "title" to "Dune",
          "author" to "Frank Herbert",
          "genre" to "Science Fiction",
          "rating" to 4.8,
          "awards" to mapOf("hugo" to true)
        )
    )

  @Before
  fun setUp() {
    Assume.assumeTrue(
      "DML tests require enterprise backend edition",
      IntegrationTestUtil.getBackendEdition() == IntegrationTestUtil.BackendEdition.ENTERPRISE
    )

    collRef = IntegrationTestUtil.testCollection()
    db = collRef.firestore
    writeAllDocs(collRef, testBookDocs)
  }

  @After
  fun tearDown() {
    IntegrationTestUtil.tearDown()
  }

  // =========================================================================
  // Delete Stage (5 tests)
  // =========================================================================

  @Test
  fun testDeleteSingleDocument() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book2")))
          .delete()
          .execute()
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book2").get())
    assertThat(docSnap.exists()).isFalse()
  }

  @Test
  fun testDeleteMultipleDocumentsWithWhereFilter() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("genre"), constant("Science Fiction")))
          .delete()
          .execute()
      )
    assertThat(snapshot).isNotNull()
    val docSnap1 = waitFor(collRef.document("book1").get())
    assertThat(docSnap1.exists()).isFalse()
    val docSnap10 = waitFor(collRef.document("book10").get())
    assertThat(docSnap10.exists()).isFalse()
  }

  @Test
  fun testDeleteNonExistingDocument() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("nonExistingId_999")))
          .delete()
          .execute()
      )
    assertThat(snapshot).isNotNull()
  }

  @Test
  fun testDeleteWithAtomicTrue() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .delete()
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book1").get())
    assertThat(docSnap.exists()).isFalse()
  }

  @Test
  fun testDeleteWithAtomicFalse() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book3")))
          .delete()
          .execute(ExecuteOptions().withAtomic(false))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book3").get())
    assertThat(docSnap.exists()).isFalse()
  }

  // =========================================================================
  // Update Stage (8 tests)
  // =========================================================================

  @Test
  fun testUpdateSingleDocWithAddFields() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book3")))
          .addFields(field("__name__").documentId().alias("id"))
          .update(constant("baz").alias("foo"))
          .execute()
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book3").get())
    assertThat(docSnap.getString("foo")).isEqualTo("baz")
    assertThat(docSnap.getString("id")).isEqualTo("book3")
  }

  @Test
  fun testUpdateMultipleDocsWithRemoveFields() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("genre"), constant("Science Fiction")))
          .removeFields("awards")
          .update(constant("Updated").alias("status"))
          .execute()
      )
    assertThat(snapshot).isNotNull()
    val docSnap1 = waitFor(collRef.document("book1").get())
    assertThat(docSnap1.getString("status")).isEqualTo("Updated")
    assertThat(docSnap1.contains("awards")).isFalse()

    val docSnap10 = waitFor(collRef.document("book10").get())
    assertThat(docSnap10.getString("status")).isEqualTo("Updated")
    assertThat(docSnap10.contains("awards")).isFalse()
  }

  @Test
  fun testUpdateWithExpressions() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .update(add(field("rating"), constant(1.0)).alias("rating"))
          .execute()
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book1").get())
    assertThat(docSnap.getDouble("rating")).isEqualTo(5.2)
  }

  @Test
  fun testUpdateWithSingleVarargExpression() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .update(constant("UpdatedVariadic").alias("status"))
          .execute()
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book1").get())
    assertThat(docSnap.getString("status")).isEqualTo("UpdatedVariadic")
  }

  @Test
  fun testUpdateWithMultipleVarargExpressions() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .update(constant("UpdatedMulti").alias("status"), constant(99L).alias("newField"))
          .execute()
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book1").get())
    assertThat(docSnap.getString("status")).isEqualTo("UpdatedMulti")
    assertThat(docSnap.getLong("newField")).isEqualTo(99L)
  }

  @Test
  fun testUpdateNonExistingDocModifiesZeroDocs() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("nonExistingId_123")))
          .update(constant("Updated").alias("status"))
          .execute()
      )
    assertThat(snapshot).isNotNull()
  }

  @Test
  fun testUpdateAtomically() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .update(constant("AtomicUpdate").alias("status"))
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book1").get())
    assertThat(docSnap.getString("status")).isEqualTo("AtomicUpdate")
  }

  // =========================================================================
  // Insert Stage (6 tests, all with withAtomic(true))
  // =========================================================================

  @Test
  fun testInsertAutoGeneratedId() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .removeFields("__name__")
          .insert(collRef.path)
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val allDocs = waitFor(collRef.get())
    assertThat(allDocs.size()).isEqualTo(5)
  }

  @Test
  fun testInsertSpecifiedDocumentIdExpression() {
    val targetCol = IntegrationTestUtil.testCollection()
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .insert(targetCol.path, constant("my_custom_id_123"))
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(targetCol.document("my_custom_id_123").get())
    assertThat(docSnap.exists()).isTrue()
    assertThat(docSnap.getString("title")).isEqualTo("The Hitchhiker's Guide to the Galaxy")
  }

  @Test
  fun testInsertFailsWhenDocumentAlreadyExists() {
    assertThrows(Exception::class.java) {
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .insert(collRef.path, constant("book2"))
          .execute(ExecuteOptions().withAtomic(true))
      )
    }
  }

  @Test
  fun testInsertIntoDifferentCollection() {
    val targetCol = IntegrationTestUtil.testCollection()
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .insert(targetCol.path)
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val targetDocs = waitFor(targetCol.get())
    assertThat(targetDocs.size()).isEqualTo(1)
    assertThat(targetDocs.documents[0].getString("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")
  }

  @Test
  fun testInsertWithOnlyDocumentIdExpressionUsesInputParentCollection() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .insert(documentIdExpression = constant("book1_copy"))
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book1_copy").get())
    assertThat(docSnap.exists()).isTrue()
    assertThat(docSnap.getString("title")).isEqualTo("The Hitchhiker's Guide to the Galaxy")
  }

  @Test
  fun testInsertWithoutTargetFailsForExistingDocuments() {
    val error =
      assertThrows(Exception::class.java) {
        waitFor(
          db
            .pipeline()
            .collection(collRef.path)
            .where(equal(field("__name__").documentId(), constant("book1")))
            .insert()
            .execute(ExecuteOptions().withAtomic(true))
        )
      }
    var cause: Throwable? = error
    while (cause != null && cause !is FirebaseFirestoreException) cause = cause.cause
    assertThat(cause).isInstanceOf(FirebaseFirestoreException::class.java)
    assertThat((cause as FirebaseFirestoreException).code)
      .isEqualTo(FirebaseFirestoreException.Code.ALREADY_EXISTS)
  }

  // =========================================================================
  // Upsert Stage (5 tests)
  // =========================================================================

  @Test
  fun testUpsertExistingDocWithVarargs() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .upsert(
            constant("Comedy Sci-Fi").alias("genre"),
            add(field("rating"), constant(0.5)).alias("rating")
          )
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book1").get())
    assertThat(docSnap.getString("genre")).isEqualTo("Comedy Sci-Fi")
    assertThat(docSnap.getDouble("rating")).isEqualTo(4.7)
  }

  @Test
  fun testUpsertExistingDocWithList() {
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .upsert(
            listOf(constant("Updated Genre List").alias("genre"), constant(5.0).alias("rating"))
          )
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document("book1").get())
    assertThat(docSnap.getString("genre")).isEqualTo("Updated Genre List")
    assertThat(docSnap.getDouble("rating")).isEqualTo(5.0)
  }

  @Test
  fun testUpsertNewDocWhenDoesNotExist() {
    val newDocId = "new_upsert_doc_id"
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .upsert(
            collectionPath = collRef.path,
            documentIdExpression = constant(newDocId),
            additionalFields =
              arrayOf(constant("New Book Title").alias("title"), constant("Sci-Fi").alias("genre"))
          )
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(collRef.document(newDocId).get())
    assertThat(docSnap.exists()).isTrue()
    assertThat(docSnap.getString("title")).isEqualTo("New Book Title")
    assertThat(docSnap.getString("genre")).isEqualTo("Sci-Fi")
  }

  @Test
  fun testUpsertIntoDifferentCollectionWithAdditionalFields() {
    val targetCol = IntegrationTestUtil.testCollection()
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .upsert(
            collectionPath = targetCol.path,
            documentIdExpression = constant("target_doc_1"),
            additionalFields =
              arrayOf(
                constant("Target Upsert Title").alias("title"),
                constant("Target Genre").alias("genre")
              )
          )
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(targetCol.document("target_doc_1").get())
    assertThat(docSnap.exists()).isTrue()
    assertThat(docSnap.getString("title")).isEqualTo("Target Upsert Title")
    assertThat(docSnap.getString("genre")).isEqualTo("Target Genre")
  }

  @Test
  fun testUpsertIntoDifferentCollectionWithoutAdditionalFields() {
    val targetCol = IntegrationTestUtil.testCollection()
    val snapshot =
      waitFor(
        db
          .pipeline()
          .collection(collRef.path)
          .where(equal(field("__name__").documentId(), constant("book1")))
          .upsert(collectionPath = targetCol.path, documentIdExpression = constant("target_doc_2"))
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val docSnap = waitFor(targetCol.document("target_doc_2").get())
    assertThat(docSnap.exists()).isTrue()
    assertThat(docSnap.getString("title")).isEqualTo("The Hitchhiker's Guide to the Galaxy")
  }

  // =========================================================================
  // Literals Stage (5 tests, using .union to satisfy Firebase Security Rules)
  // =========================================================================

  @Test
  fun testLiteralsBasicDocuments() {
    val emptyCol = IntegrationTestUtil.testCollection()
    val snapshot =
      waitFor(
        db
          .pipeline()
          .literals(mapOf("name" to "Alice", "age" to 30L), mapOf("name" to "Bob", "age" to 25L))
          .union(db.pipeline().collection(emptyCol.path))
          .execute()
      )
    assertThat(snapshot.results).hasSize(2)
    val first = snapshot.results[0].getData()
    val second = snapshot.results[1].getData()
    assertThat(first).containsExactly("name", "Alice", "age", 30L)
    assertThat(second).containsExactly("name", "Bob", "age", 25L)
  }

  @Test
  fun testLiteralsWithExpressions() {
    val emptyCol = IntegrationTestUtil.testCollection()
    val snapshot =
      waitFor(
        db
          .pipeline()
          .literals(mapOf("base" to 10L, "doubled" to multiply(constant(10L), constant(2L))))
          .union(db.pipeline().collection(emptyCol.path))
          .execute()
      )
    assertThat(snapshot.results).hasSize(1)
    val first = snapshot.results[0].getData()
    assertThat(first).containsExactly("base", 10L, "doubled", 20L)
  }

  @Test
  fun testLiteralsWithExpressionNestedInMap() {
    val emptyCol = IntegrationTestUtil.testCollection()
    val snapshot =
      waitFor(
        db
          .pipeline()
          .literals(
            mapOf(
              "nested" to mapOf("doubled" to multiply(constant(10L), constant(2L)), "label" to "x")
            )
          )
          .union(db.pipeline().collection(emptyCol.path))
          .execute()
      )
    assertThat(snapshot.results).hasSize(1)
    val first = snapshot.results[0].getData()
    assertThat(first).containsExactly("nested", mapOf("doubled" to 20L, "label" to "x"))
  }

  @Test
  fun testLiteralsWithExpressionNestedInList() {
    val emptyCol = IntegrationTestUtil.testCollection()
    val snapshot =
      waitFor(
        db
          .pipeline()
          .literals(mapOf("list" to listOf(multiply(constant(10L), constant(2L)), 1L)))
          .union(db.pipeline().collection(emptyCol.path))
          .execute()
      )
    assertThat(snapshot.results).hasSize(1)
    val first = snapshot.results[0].getData()
    assertThat(first).containsExactly("list", listOf(20L, 1L))
  }

  @Test
  fun testLiteralsCombinedWithInsert() {
    val targetCol = IntegrationTestUtil.testCollection()
    val emptyCol = IntegrationTestUtil.testCollection()
    val snapshot =
      waitFor(
        db
          .pipeline()
          .literals(mapOf("name" to "Literal Inserted", "age" to 42L))
          .union(db.pipeline().collection(emptyCol.path))
          .insert(targetCol.path)
          .execute(ExecuteOptions().withAtomic(true))
      )
    assertThat(snapshot).isNotNull()
    val targetDocs = waitFor(targetCol.get())
    assertThat(targetDocs.size()).isEqualTo(1)
    assertThat(targetDocs.documents[0].getString("name")).isEqualTo("Literal Inserted")
    assertThat(targetDocs.documents[0].getLong("age")).isEqualTo(42L)
  }
}
