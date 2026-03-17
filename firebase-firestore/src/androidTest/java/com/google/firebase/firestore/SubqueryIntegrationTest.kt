/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.pipeline.Expression.Companion.and
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.currentDocument
import com.google.firebase.firestore.pipeline.Expression.Companion.equal
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.or
import com.google.firebase.firestore.pipeline.Expression.Companion.variable
import com.google.firebase.firestore.testutil.IntegrationTestUtil
import com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor
import com.google.firebase.firestore.util.Util.autoId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubqueryIntegrationTest {
  private lateinit var db: FirebaseFirestore
  private lateinit var collection: CollectionReference

  private val bookDocs =
    mapOf(
      "book1" to
        mapOf(
          "title" to "The Hitchhiker's Guide to the Galaxy",
          "author" to "Douglas Adams",
          "genre" to "Science Fiction",
          "published" to 1979,
          "rating" to 4.2,
          "tags" to listOf("comedy", "space", "adventure"),
          "awards" to mapOf("hugo" to true, "nebula" to false),
          "embedding" to
            FieldValue.vector(doubleArrayOf(10.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
        ),
      "book2" to
        mapOf(
          "title" to "Pride and Prejudice",
          "author" to "Jane Austen",
          "genre" to "Romance",
          "published" to 1813,
          "rating" to 4.5,
          "tags" to listOf("classic", "social commentary", "love"),
          "awards" to mapOf("none" to true),
          "embedding" to
            FieldValue.vector(doubleArrayOf(1.0, 10.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
        ),
      "book3" to
        mapOf(
          "title" to "One Hundred Years of Solitude",
          "author" to "Gabriel García Márquez",
          "genre" to "Magical Realism",
          "published" to 1967,
          "rating" to 4.3,
          "tags" to listOf("family", "history", "fantasy"),
          "awards" to mapOf("nobel" to true, "nebula" to false),
          "embedding" to
            FieldValue.vector(doubleArrayOf(1.0, 1.0, 10.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
        ),
      "book4" to
        mapOf(
          "title" to "The Lord of the Rings",
          "author" to "J.R.R. Tolkien",
          "genre" to "Fantasy",
          "published" to 1954,
          "rating" to 4.7,
          "tags" to listOf("adventure", "magic", "epic"),
          "awards" to mapOf("hugo" to false, "nebula" to false),
          "cost" to Double.NaN,
          "embedding" to
            FieldValue.vector(doubleArrayOf(1.0, 1.0, 1.0, 10.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
        ),
      "book5" to
        mapOf(
          "title" to "The Handmaid's Tale",
          "author" to "Margaret Atwood",
          "genre" to "Dystopian",
          "published" to 1985,
          "rating" to 4.1,
          "tags" to listOf("feminism", "totalitarianism", "resistance"),
          "awards" to mapOf("arthur c. clarke" to true, "booker prize" to false),
          "embedding" to
            FieldValue.vector(doubleArrayOf(1.0, 1.0, 1.0, 1.0, 10.0, 1.0, 1.0, 1.0, 1.0, 1.0))
        ),
      "book6" to
        mapOf(
          "title" to "Crime and Punishment",
          "author" to "Fyodor Dostoevsky",
          "genre" to "Psychological Thriller",
          "published" to 1866,
          "rating" to 4.3,
          "tags" to listOf("philosophy", "crime", "redemption"),
          "awards" to mapOf("none" to true),
          "embedding" to
            FieldValue.vector(doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 10.0, 1.0, 1.0, 1.0, 1.0))
        ),
      "book7" to
        mapOf(
          "title" to "To Kill a Mockingbird",
          "author" to "Harper Lee",
          "genre" to "Southern Gothic",
          "published" to 1960,
          "rating" to 4.2,
          "tags" to listOf("racism", "injustice", "coming-of-age"),
          "awards" to mapOf("pulitzer" to true),
          "embedding" to
            FieldValue.vector(doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0, 1.0, 1.0, 1.0))
        ),
      "book8" to
        mapOf(
          "title" to "1984",
          "author" to "George Orwell",
          "genre" to "Dystopian",
          "published" to 1949,
          "rating" to 4.2,
          "tags" to listOf("surveillance", "totalitarianism", "propaganda"),
          "awards" to mapOf("prometheus" to true),
          "embedding" to
            FieldValue.vector(doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0, 1.0, 1.0))
        ),
      "book9" to
        mapOf(
          "title" to "The Great Gatsby",
          "author" to "F. Scott Fitzgerald",
          "genre" to "Modernist",
          "published" to 1925,
          "rating" to 4.0,
          "tags" to listOf("wealth", "american dream", "love"),
          "awards" to mapOf("none" to true),
          "embedding" to
            FieldValue.vector(doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0, 1.0))
        ),
      "book10" to
        mapOf(
          "title" to "Dune",
          "author" to "Frank Herbert",
          "genre" to "Science Fiction",
          "published" to 1965,
          "rating" to 4.6,
          "tags" to listOf("politics", "desert", "ecology"),
          "awards" to mapOf("hugo" to true, "nebula" to true),
          "embedding" to
            FieldValue.vector(doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0))
        ),
      "book11" to
        mapOf(
          "title" to "Timestamp Book",
          "author" to "Timestamp Author",
          "timestamp" to java.util.Date()
        )
    )

  @Before
  fun setUp() {
    org.junit.Assume.assumeTrue(
      "Skip SubqueryIntegrationTest on standard backend",
      IntegrationTestUtil.getBackendEdition() == IntegrationTestUtil.BackendEdition.ENTERPRISE
    )

    // Using IntegrationTestUtil.testCollectionWithDocs to populate data
    collection = IntegrationTestUtil.testCollectionWithDocs(bookDocs)
    db = collection.firestore
  }

  @After
  fun tearDown() {
    IntegrationTestUtil.tearDown()
  }

  @Test
  fun testZeroResultScalarReturnsNull() {
    val testDocs = mapOf("book1" to mapOf("title" to "A Book Title"))
    for ((key, value) in testDocs) {
      waitFor(collection.document(key).set(value))
    }

    val emptyScalar =
      db
        .pipeline()
        .collection(collection.document("book1").collection("reviews").path)
        .where(equal("reviewer", "Alice"))
        .select(currentDocument().alias("data"))

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collection.path)
          .select(emptyScalar.toScalarExpression().alias("first_review_data"))
          .limit(1)
          .execute()
      )

    assertThat(results.map { it.getData() }).containsExactly(mapOf("first_review_data" to null))
  }

  @Test
  fun testArraySubqueryJoinAndEmptyResult() {
    val reviewsCollName = "book_reviews_" + autoId()
    val reviewsDocs =
      mapOf(
        "r1" to mapOf("bookTitle" to "The Hitchhiker's Guide to the Galaxy", "reviewer" to "Alice"),
        "r2" to mapOf("bookTitle" to "The Hitchhiker's Guide to the Galaxy", "reviewer" to "Bob")
      )

    val reviewsCollection = db.collection(reviewsCollName)
    for ((key, value) in reviewsDocs) {
      waitFor(reviewsCollection.document(key).set(value))
    }

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookTitle", variable("book_title")))
        .select(field("reviewer").alias("reviewer"))
        .sort(field("reviewer").ascending())

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collection.path)
          .where(
            or(
              equal("title", "The Hitchhiker's Guide to the Galaxy"),
              equal("title", "Pride and Prejudice")
            )
          )
          .define(field("title").alias("book_title"))
          .addFields(reviewsSub.toArrayExpression().alias("reviews_data"))
          .select("title", "reviews_data")
          .sort(field("title").descending())
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(
        mapOf(
          "title" to "The Hitchhiker's Guide to the Galaxy",
          "reviews_data" to listOf("Alice", "Bob")
        ),
        mapOf("title" to "Pride and Prejudice", "reviews_data" to emptyList<Any>())
      )
      .inOrder()
  }

  @Test
  fun testMultipleArraySubqueriesOnBooks() {
    val reviewsCollName = "reviews_multi_" + autoId()
    val authorsCollName = "authors_multi_" + autoId()

    waitFor(
      db.collection(reviewsCollName).document("r1").set(mapOf("bookTitle" to "1984", "rating" to 5))
    )

    waitFor(
      db
        .collection(authorsCollName)
        .document("a1")
        .set(mapOf("authorName" to "George Orwell", "nationality" to "British"))
    )

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookTitle", variable("book_title")))
        .select(field("rating").alias("rating"))

    val authorsSub =
      db
        .pipeline()
        .collection(authorsCollName)
        .where(equal("authorName", variable("author_name")))
        .select(field("nationality").alias("nationality"))

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collection.path)
          .where(equal("title", "1984"))
          .define(field("title").alias("book_title"), field("author").alias("author_name"))
          .addFields(
            reviewsSub.toArrayExpression().alias("reviews_data"),
            authorsSub.toArrayExpression().alias("authors_data")
          )
          .select("title", "reviews_data", "authors_data")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(
        mapOf("title" to "1984", "reviews_data" to listOf(5L), "authors_data" to listOf("British"))
      )
  }

  @Test
  fun testArraySubqueryJoinMultipleFieldsPreservesMap() {
    val reviewsCollName = "reviews_map_" + autoId()
    val reviewsCollection = db.collection(reviewsCollName)

    waitFor(
      reviewsCollection
        .document("r1")
        .set(mapOf("bookTitle" to "1984", "reviewer" to "Alice", "rating" to 5))
    )
    waitFor(
      reviewsCollection
        .document("r2")
        .set(mapOf("bookTitle" to "1984", "reviewer" to "Bob", "rating" to 4))
    )

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookTitle", variable("book_title")))
        .select(field("reviewer").alias("reviewer"), field("rating").alias("rating"))
        .sort(field("reviewer").ascending())

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collection.path)
          .where(equal("title", "1984"))
          .define(field("title").alias("book_title"))
          .addFields(reviewsSub.toArrayExpression().alias("reviews_data"))
          .select("title", "reviews_data")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(
        mapOf(
          "title" to "1984",
          "reviews_data" to
            listOf(
              mapOf("reviewer" to "Alice", "rating" to 5L),
              mapOf("reviewer" to "Bob", "rating" to 4L)
            )
        )
      )
  }

  @Test
  fun testArraySubqueryInWhereStageOnBooks() {
    val reviewsCollName = "reviews_where_" + autoId()
    val reviewsCollection = db.collection(reviewsCollName)

    waitFor(
      reviewsCollection.document("r1").set(mapOf("bookTitle" to "Dune", "reviewer" to "Paul"))
    )
    waitFor(
      reviewsCollection.document("r2").set(mapOf("bookTitle" to "Foundation", "reviewer" to "Hari"))
    )

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookTitle", variable("book_title")))
        .select(field("reviewer").alias("reviewer"))

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collection.path)
          .where(or(equal("title", "Dune"), equal("title", "The Great Gatsby")))
          .define(field("title").alias("book_title"))
          .where(reviewsSub.toArrayExpression().arrayContains("Paul"))
          .select("title")
          .execute()
      )

    assertThat(results.map { it.getData() }).containsExactly(mapOf("title" to "Dune"))
  }

  @Test
  fun testScalarSubquerySingleAggregationUnwrapping() {
    val reviewsCollName = "reviews_agg_single_" + autoId()

    val reviewsCollection = db.collection(reviewsCollName)
    waitFor(reviewsCollection.document("r1").set(mapOf("bookTitle" to "1984", "rating" to 4)))
    waitFor(reviewsCollection.document("r2").set(mapOf("bookTitle" to "1984", "rating" to 5)))

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookTitle", variable("book_title")))
        .aggregate(
          com.google.firebase.firestore.pipeline.AggregateFunction.average("rating").alias("val")
        )

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collection.path)
          .where(equal("title", "1984"))
          .define(field("title").alias("book_title"))
          .addFields(reviewsSub.toScalarExpression().alias("average_rating"))
          .select("title", "average_rating")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("title" to "1984", "average_rating" to 4.5))
  }

  @Test
  fun testScalarSubqueryMultipleAggregationsMapWrapping() {
    val reviewsCollName = "reviews_agg_multi_" + autoId()

    val reviewsCollection = db.collection(reviewsCollName)
    waitFor(reviewsCollection.document("r1").set(mapOf("bookTitle" to "1984", "rating" to 4)))
    waitFor(reviewsCollection.document("r2").set(mapOf("bookTitle" to "1984", "rating" to 5)))

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookTitle", variable("book_title")))
        .aggregate(
          com.google.firebase.firestore.pipeline.AggregateFunction.average("rating").alias("avg"),
          com.google.firebase.firestore.pipeline.AggregateFunction.countAll().alias("count")
        )

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collection.path)
          .where(equal("title", "1984"))
          .define(field("title").alias("book_title"))
          .addFields(reviewsSub.toScalarExpression().alias("stats"))
          .select("title", "stats")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("title" to "1984", "stats" to mapOf("avg" to 4.5, "count" to 2L)))
  }

  @Test
  fun testScalarSubqueryZeroResults() {
    val reviewsCollName = "reviews_zero_" + autoId()

    // No reviews for "1984"

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookTitle", variable("book_title")))
        .aggregate(
          com.google.firebase.firestore.pipeline.AggregateFunction.average("rating").alias("avg")
        )

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collection.path)
          .where(equal("title", "1984")) // "1984" exists in the main collection from setup
          .define(field("title").alias("book_title"))
          .addFields(reviewsSub.toScalarExpression().alias("average_rating"))
          .select("title", "average_rating")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("title" to "1984", "average_rating" to null))
  }

  @Test
  fun testScalarSubqueryMultipleResultsRuntimeError() {
    val reviewsCollName = "reviews_multiple_" + autoId()

    val reviewsCollection = db.collection(reviewsCollName)
    waitFor(reviewsCollection.document("r1").set(mapOf("bookTitle" to "1984", "rating" to 4)))
    waitFor(reviewsCollection.document("r2").set(mapOf("bookTitle" to "1984", "rating" to 5)))

    // This subquery will return 2 documents, which is invalid for toScalarExpression()
    val reviewsSub =
      db.pipeline().collection(reviewsCollName).where(equal("bookTitle", variable("book_title")))

    val exception =
      org.junit.Assert.assertThrows(RuntimeException::class.java) {
        waitFor(
          db
            .pipeline()
            .collection(collection.path)
            .where(equal("title", "1984"))
            .define(field("title").alias("book_title"))
            .addFields(reviewsSub.toScalarExpression().alias("review_data"))
            .execute()
        )
      }

    // Assert that it's an API error from the backend complaining about multiple results
    assertThat(exception.cause?.message).contains("Subpipeline returned multiple results.")
  }

  @Test
  fun testMixedScalarAndArraySubqueries() {
    val reviewsCollName = "reviews_mixed_" + autoId()
    val reviewsCollection = db.collection(reviewsCollName)

    // Set up some reviews
    waitFor(
      reviewsCollection
        .document("r1")
        .set(mapOf("bookTitle" to "1984", "reviewer" to "Alice", "rating" to 4))
    )
    waitFor(
      reviewsCollection
        .document("r2")
        .set(mapOf("bookTitle" to "1984", "reviewer" to "Bob", "rating" to 5))
    )

    // Array subquery for all reviewers
    val arraySub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookTitle", variable("book_title")))
        .select(field("reviewer").alias("reviewer"))
        .sort(field("reviewer").ascending())

    // Scalar subquery for the average rating
    val scalarSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookTitle", variable("book_title")))
        .aggregate(
          com.google.firebase.firestore.pipeline.AggregateFunction.average("rating").alias("val")
        )

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collection.path)
          .where(equal("title", "1984"))
          .define(field("title").alias("book_title"))
          .addFields(
            arraySub.toArrayExpression().alias("all_reviewers"),
            scalarSub.toScalarExpression().alias("average_rating")
          )
          .select("title", "all_reviewers", "average_rating")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(
        mapOf("title" to "1984", "all_reviewers" to listOf("Alice", "Bob"), "average_rating" to 4.5)
      )
  }

  @Test
  fun testSingleScopeVariableUsage() {
    val collName = "single_scope_" + autoId()
    waitFor(db.collection(collName).document("doc1").set(mapOf("price" to 100)))

    var results =
      waitFor(
        db
          .pipeline()
          .collection(collName)
          .define(field("price").multiply(0.8).alias("discount"))
          .where(variable("discount").lessThan(50.0))
          .select("price")
          .execute()
      )

    assertThat(results).isEmpty()

    waitFor(db.collection(collName).document("doc2").set(mapOf("price" to 50)))

    results =
      waitFor(
        db
          .pipeline()
          .collection(collName)
          .define(field("price").multiply(0.8).alias("discount"))
          .where(variable("discount").lessThan(50.0))
          .select("price")
          .execute()
      )

    assertThat(results.map { it.getData() }).containsExactly(mapOf("price" to 50L))
  }

  @Test
  fun testExplicitFieldBindingScopeBridging() {
    val outerCollName = "outer_scope_" + autoId()
    waitFor(
      db.collection(outerCollName).document("doc1").set(mapOf("title" to "1984", "id" to "1"))
    )

    val reviewsCollName = "reviews_scope_" + autoId()
    waitFor(
      db
        .collection(reviewsCollName)
        .document("r1")
        .set(mapOf("bookId" to "1", "reviewer" to "Alice"))
    )

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookId", variable("rid")))
        .select(field("reviewer").alias("reviewer"))

    val results =
      waitFor(
        db
          .pipeline()
          .collection(outerCollName)
          .where(equal("title", "1984"))
          .define(field("id").alias("rid"))
          .addFields(reviewsSub.toArrayExpression().alias("reviews"))
          .select("title", "reviews")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("title" to "1984", "reviews" to listOf("Alice")))
  }

  @Test
  fun testMultipleVariableBindings() {
    val outerCollName = "outer_multi_" + autoId()
    waitFor(
      db
        .collection(outerCollName)
        .document("doc1")
        .set(mapOf("title" to "1984", "id" to "1", "category" to "sci-fi"))
    )

    val reviewsCollName = "reviews_multi_" + autoId()
    waitFor(
      db
        .collection(reviewsCollName)
        .document("r1")
        .set(mapOf("bookId" to "1", "category" to "sci-fi", "reviewer" to "Alice"))
    )

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(and(equal("bookId", variable("rid")), equal("category", variable("rcat"))))
        .select(field("reviewer").alias("reviewer"))

    val results =
      waitFor(
        db
          .pipeline()
          .collection(outerCollName)
          .where(equal("title", "1984"))
          .define(field("id").alias("rid"), field("category").alias("rcat"))
          .addFields(reviewsSub.toArrayExpression().alias("reviews"))
          .select("title", "reviews")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("title" to "1984", "reviews" to listOf("Alice")))
  }

  @Test
  fun testCurrentDocumentBinding() {
    val outerCollName = "outer_currentdoc_" + autoId()
    waitFor(
      db
        .collection(outerCollName)
        .document("doc1")
        .set(mapOf("title" to "1984", "author" to "George Orwell"))
    )

    val reviewsCollName = "reviews_currentdoc_" + autoId()
    waitFor(
      db
        .collection(reviewsCollName)
        .document("r1")
        .set(mapOf("authorName" to "George Orwell", "reviewer" to "Alice"))
    )

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("authorName", variable("doc").getField("author")))
        .select(field("reviewer").alias("reviewer"))

    val results =
      waitFor(
        db
          .pipeline()
          .collection(outerCollName)
          .where(equal("title", "1984"))
          .define(currentDocument().alias("doc"))
          .addFields(reviewsSub.toArrayExpression().alias("reviews"))
          .select("title", "reviews")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("title" to "1984", "reviews" to listOf("Alice")))
  }

  @Test
  fun testUnboundVariableCornerCase() {
    val outerCollName = "outer_unbound_" + autoId()

    val exception =
      org.junit.Assert.assertThrows(RuntimeException::class.java) {
        waitFor(
          db
            .pipeline()
            .collection(outerCollName)
            .where(equal("title", variable("unknown_var")))
            .execute()
        )
      }

    // Assert that it's an API error from the backend complaining about unknown variable
    assertThat(exception.cause?.message).contains("unknown variable")
  }

  @Test
  fun testVariableShadowingCollision() {
    val outerCollName = "outer_shadow_" + autoId()
    waitFor(db.collection(outerCollName).document("doc1").set(mapOf("title" to "1984")))

    val innerCollName = "inner_shadow_" + autoId()
    waitFor(db.collection(innerCollName).document("i1").set(mapOf("id" to "test")))

    // Inner subquery re-defines variable "x" to be "inner_val"
    val sub =
      db
        .pipeline()
        .collection(innerCollName)
        .define(constant("inner_val").alias("x"))
        .select(variable("x").alias("val"))

    // Outer pipeline defines variable "x" to be "outer_val"
    val results =
      waitFor(
        db
          .pipeline()
          .collection(outerCollName)
          .where(equal("title", "1984"))
          .limit(1)
          .define(constant("outer_val").alias("x"))
          .addFields(sub.toArrayExpression().alias("shadowed"))
          .select("shadowed")
          .execute()
      )

    // Due to innermost scope winning, the result should use "inner_val"
    // Scalar unwrapping applies because it's a single field
    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("shadowed" to listOf("inner_val")))
  }

  @Test
  fun testMissingFieldOnCurrentDocument() {
    val outerCollName = "outer_missing_" + autoId()
    waitFor(db.collection(outerCollName).document("doc1").set(mapOf("title" to "1984")))

    val reviewsCollName = "reviews_missing_" + autoId()
    waitFor(
      db
        .collection(reviewsCollName)
        .document("r1")
        .set(mapOf("bookId" to "1", "reviewer" to "Alice"))
    )

    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(equal("bookId", variable("doc").getField("does_not_exist")))
        .select(field("reviewer").alias("reviewer"))

    val results =
      waitFor(
        db
          .pipeline()
          .collection(outerCollName)
          .where(equal("title", "1984"))
          .define(currentDocument().alias("doc"))
          .addFields(reviewsSub.toArrayExpression().alias("reviews"))
          .select("title", "reviews")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("title" to "1984", "reviews" to emptyList<Any>()))
  }

  @Test
  fun test3LevelDeepJoin() {
    val publishersCollName = "publishers_" + autoId()
    val booksCollName = "books_" + autoId()
    val reviewsCollName = "reviews_" + autoId()

    waitFor(
      db
        .collection(publishersCollName)
        .document("p1")
        .set(mapOf("publisherId" to "pub1", "name" to "Penguin"))
    )

    waitFor(
      db
        .collection(booksCollName)
        .document("b1")
        .set(mapOf("bookId" to "book1", "publisherId" to "pub1", "title" to "1984"))
    )

    waitFor(
      db
        .collection(reviewsCollName)
        .document("r1")
        .set(mapOf("bookId" to "book1", "reviewer" to "Alice"))
    )

    // reviews need to know if the publisher is Penguin
    val reviewsSub =
      db
        .pipeline()
        .collection(reviewsCollName)
        .where(
          and(
            equal("bookId", variable("book_id")),
            equal(variable("pub_name"), "Penguin") // accessing top-level pub_name
          )
        )
        .select(field("reviewer").alias("reviewer"))

    val booksSub =
      db
        .pipeline()
        .collection(booksCollName)
        .where(equal("publisherId", variable("pub_id")))
        .define(field("bookId").alias("book_id"))
        .addFields(reviewsSub.toArrayExpression().alias("reviews"))
        .select("title", "reviews")

    val results =
      waitFor(
        db
          .pipeline()
          .collection(publishersCollName)
          .where(equal("publisherId", "pub1"))
          .define(field("publisherId").alias("pub_id"), field("name").alias("pub_name"))
          .addFields(booksSub.toArrayExpression().alias("books"))
          .select("name", "books")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(
        mapOf(
          "name" to "Penguin",
          "books" to listOf(mapOf("title" to "1984", "reviews" to listOf("Alice")))
        )
      )
  }

  @Test
  fun testDeepAggregation() {
    val outerColl = "outer_agg_" + autoId()
    val innerColl = "inner_agg_" + autoId()

    waitFor(db.collection(outerColl).document("doc1").set(mapOf("id" to "1")))
    waitFor(db.collection(outerColl).document("doc2").set(mapOf("id" to "2")))

    val innerCollection = db.collection(innerColl)
    waitFor(innerCollection.document("i1").set(mapOf("outer_id" to "1", "score" to 10)))
    waitFor(innerCollection.document("i2").set(mapOf("outer_id" to "2", "score" to 20)))
    waitFor(innerCollection.document("i3").set(mapOf("outer_id" to "1", "score" to 30)))

    // subquery calculates the score for the outer doc
    val innerSub =
      db
        .pipeline()
        .collection(innerColl)
        .where(equal("outer_id", variable("oid")))
        .aggregate(
          com.google.firebase.firestore.pipeline.AggregateFunction.average("score").alias("s")
        )

    val results =
      waitFor(
        db
          .pipeline()
          .collection(outerColl)
          .define(field("id").alias("oid"))
          .addFields(innerSub.toScalarExpression().alias("doc_score"))
          // Now we aggregate over the calculated subquery results
          .aggregate(
            com.google.firebase.firestore.pipeline.AggregateFunction.sum("doc_score")
              .alias("total_score")
          )
          .execute()
      )

    assertThat(results.map { it.getData() }).containsExactly(mapOf("total_score" to 40.0))
  }

  @Test
  fun testPipelineStageSupport10Layers() {
    val collName = "depth_" + autoId()
    waitFor(db.collection(collName).document("doc1").set(mapOf("val" to "hello")))

    // Create a nested pipeline of depth 10
    var currentSubquery =
      db.pipeline().collection(collName).limit(1).select(field("val").alias("val"))

    for (i in 0 until 9) {
      currentSubquery =
        db
          .pipeline()
          .collection(collName)
          .limit(1)
          .addFields(currentSubquery.toArrayExpression().alias("nested_$i"))
          .select("nested_$i")
    }

    val results = waitFor(currentSubquery.execute())
    assertThat(results).isNotEmpty()
  }

  @Test
  fun testStandardSubcollectionQuery() {
    val collName = "subcoll_test_" + autoId()

    waitFor(db.collection(collName).document("doc1").set(mapOf("title" to "1984")))

    waitFor(
      db
        .collection(collName)
        .document("doc1")
        .collection("reviews")
        .document("r1")
        .set(mapOf("reviewer" to "Alice"))
    )

    val reviewsSub =
      PipelineSource.subcollection("reviews").select(field("reviewer").alias("reviewer"))

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collName)
          .where(equal("title", "1984"))
          .addFields(reviewsSub.toArrayExpression().alias("reviews"))
          .select("title", "reviews")
          .execute()
      )

    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("title" to "1984", "reviews" to listOf("Alice")))
  }

  @Test
  fun testMissingSubcollection() {
    val collName = "subcoll_missing_" + autoId()

    waitFor(db.collection(collName).document("doc1").set(mapOf("id" to "no_subcollection_here")))

    // Notably NO subcollections are added to doc1

    val missingSub =
      PipelineSource.subcollection("does_not_exist").select(variable("p").alias("sub_p"))

    val results =
      waitFor(
        db
          .pipeline()
          .collection(collName)
          .define(currentDocument().alias("p"))
          .select(missingSub.toArrayExpression().alias("missing_data"))
          .limit(1)
          .execute()
      )

    // Ensure it's not null and evaluates properly to an empty array []
    assertThat(results.map { it.getData() })
      .containsExactly(mapOf("missing_data" to emptyList<Any>()))
  }

  @Test
  fun testDirectExecutionOfSubcollectionPipeline() {
    val sub = PipelineSource.subcollection("reviews")

    val exception =
      org.junit.Assert.assertThrows(IllegalStateException::class.java) {
        // Attempting to execute a relative subcollection pipeline directly should fail
        sub.execute()
      }

    assertThat(exception.message)
      .contains(
        "This pipeline was created without a database (e.g., as a subcollection pipeline) and cannot be executed directly. It can only be used as part of another pipeline."
      )
  }
}
