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
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.exists
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.isNull
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.not
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class NestedPropertiesTests {

  private val db = TestUtil.firestore()

  @Test
  fun `where equality deeply nested`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf(
          "a" to
            mapOf(
              "b" to
                mapOf(
                  "c" to
                    mapOf(
                      "d" to
                        mapOf(
                          "e" to
                            mapOf(
                              "f" to
                                mapOf(
                                  "g" to mapOf("h" to mapOf("i" to mapOf("j" to mapOf("k" to 42L))))
                                )
                            )
                        )
                    )
                )
            )
        )
      ) // Match
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "a" to
            mapOf(
              "b" to
                mapOf(
                  "c" to
                    mapOf(
                      "d" to
                        mapOf(
                          "e" to
                            mapOf(
                              "f" to
                                mapOf(
                                  "g" to
                                    mapOf("h" to mapOf("i" to mapOf("j" to mapOf("k" to "42"))))
                                )
                            )
                        )
                    )
                )
            )
        )
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf(
          "a" to
            mapOf(
              "b" to
                mapOf(
                  "c" to
                    mapOf(
                      "d" to
                        mapOf(
                          "e" to
                            mapOf(
                              "f" to
                                mapOf(
                                  "g" to mapOf("h" to mapOf("i" to mapOf("j" to mapOf("k" to 0L))))
                                )
                            )
                        )
                    )
                )
            )
        )
      )
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("a.b.c.d.e.f.g.h.i.j.k").equal(constant(42L)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `where inequality deeply nested`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf(
          "a" to
            mapOf(
              "b" to
                mapOf(
                  "c" to
                    mapOf(
                      "d" to
                        mapOf(
                          "e" to
                            mapOf(
                              "f" to
                                mapOf(
                                  "g" to mapOf("h" to mapOf("i" to mapOf("j" to mapOf("k" to 42L))))
                                )
                            )
                        )
                    )
                )
            )
        )
      ) // Match
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "a" to
            mapOf(
              "b" to
                mapOf(
                  "c" to
                    mapOf(
                      "d" to
                        mapOf(
                          "e" to
                            mapOf(
                              "f" to
                                mapOf(
                                  "g" to
                                    mapOf("h" to mapOf("i" to mapOf("j" to mapOf("k" to "42"))))
                                )
                            )
                        )
                    )
                )
            )
        )
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf(
          "a" to
            mapOf(
              "b" to
                mapOf(
                  "c" to
                    mapOf(
                      "d" to
                        mapOf(
                          "e" to
                            mapOf(
                              "f" to
                                mapOf(
                                  "g" to mapOf("h" to mapOf("i" to mapOf("j" to mapOf("k" to 0L))))
                                )
                            )
                        )
                    )
                )
            )
        )
      ) // Match
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("a.b.c.d.e.f.g.h.i.j.k").greaterThanOrEqual(constant(0L)))
        .sort(field(PublicFieldPath.documentId()).ascending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3).inOrder()
  }

  @Test
  fun `where equality`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf("address" to mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L))
      )
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      ) // Match
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      )
    val doc4 = doc("users/d", 1000, mapOf())
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("address.street").equal(constant("76")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `multiple filters`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf("address" to mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L))
      ) // Match
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      )
    val doc4 = doc("users/d", 1000, mapOf())
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("address.city").equal(constant("San Francisco")))
        .where(field("address.zip").greaterThan(constant(90000L)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `multiple filters redundant`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf("address" to mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L))
      ) // Match
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      )
    val doc4 = doc("users/d", 1000, mapOf())
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(
          field("address")
            .equal(map(mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L)))
        )
        .where(field("address.zip").greaterThan(constant(90000L)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `multiple filters with composite index`(): Unit = runBlocking {
    // This test is functionally identical to MultipleFilters
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf("address" to mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L))
      ) // Match
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      )
    val doc4 = doc("users/d", 1000, mapOf())
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("address.city").equal(constant("San Francisco")))
        .where(field("address.zip").greaterThan(constant(90000L)))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `where inequality`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf("address" to mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L))
      ) // zip > 90k
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      ) // zip < 90k
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      ) // zip > 90k
    val doc4 = doc("users/d", 1000, mapOf())
    val doc5 = doc("users/d", 1000, mapOf("address" to "1 Front Street"))
    val doc6 = doc("users/d", 1000, mapOf("address" to null))
    val doc7 = doc("users/d", 1000, mapOf("different" to mapOf("zip" to 94105L)))
    val doc8 = doc("users/d", 1000, mapOf("not-address" to "1 Front Street"))
    val documents = listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8)

    val pipeline1 =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("address.zip").greaterThan(constant(90000L)))
    assertThat(runPipeline(pipeline1, listOf(*documents.toTypedArray())).toList())
      .containsExactly(doc1, doc3)

    val pipeline2 =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("address.zip").lessThan(constant(90000L)))
    assertThat(runPipeline(pipeline2, listOf(*documents.toTypedArray())).toList())
      .containsExactly(doc2)

    val pipeline3 =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("address.zip").lessThan(constant(0L)))
    assertThat(runPipeline(pipeline3, listOf(*documents.toTypedArray())).toList()).isEmpty()

    val pipeline4 =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("address.zip").notEqual(constant(10011L)))
    assertThat(runPipeline(pipeline4, listOf(*documents.toTypedArray())).toList())
      .containsExactly(doc1, doc3, doc4, doc5, doc6, doc7, doc8)
  }

  @Test
  fun `where exists`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf("address" to mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L))
      )
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      ) // Match
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      )
    val doc4 = doc("users/d", 1000, mapOf())
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db).collection("/users").where(exists(field("address.street")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `where not exists`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf("address" to mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L))
      ) // Match
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      ) // Match
    val doc4 = doc("users/d", 1000, mapOf()) // Match
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db).collection("/users").where(not(exists(field("address.street"))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3, doc4).inOrder()
  }

  @Test
  fun `where is null`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf(
          "address" to
            mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L, "street" to null)
        )
      ) // Match
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      )
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("/users").where(isNull(field("address.street")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }

  @Test
  fun `where is not null`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf(
          "address" to
            mapOf("city" to "San Francisco", "state" to "CA", "zip" to 94105L, "street" to null)
        )
      )
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      ) // Match
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      ) // street is missing, so it's not "not null" in the context of this filter
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db).collection("/users").where(not(isNull(field("address.street"))))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `sort with exists`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "41", "city" to "San Francisco", "state" to "CA", "zip" to 94105L)
        )
      ) // Match
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      ) // Match
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      )
    val doc4 = doc("users/d", 1000, mapOf())
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(exists(field("address.street")))
        .sort(field("address.street").ascending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2).inOrder()
  }

  @Test
  fun `sort without exists`(): Unit = runBlocking {
    val doc1 =
      doc(
        "users/a",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "41", "city" to "San Francisco", "state" to "CA", "zip" to 94105L)
        )
      )
    val doc2 =
      doc(
        "users/b",
        1000,
        mapOf(
          "address" to
            mapOf("street" to "76", "city" to "New York", "state" to "NY", "zip" to 10011L)
        )
      )
    val doc3 =
      doc(
        "users/c",
        1000,
        mapOf("address" to mapOf("city" to "Mountain View", "state" to "CA", "zip" to 94043L))
      )
    val doc4 = doc("users/d", 1000, mapOf())
    val documents = listOf(doc1, doc2, doc3, doc4)

    val pipeline =
      RealtimePipelineSource(db).collection("/users").sort(field("address.street").ascending())

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    // Missing fields sort first, then by key (c < d). Then existing fields by value ("41" < "76").
    assertThat(result).containsExactly(doc3, doc4, doc1, doc2).inOrder()
  }

  @Test
  fun `quoted nested property filter nested`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("address.city" to "San Francisco"))
    val doc2 = doc("users/b", 1000, mapOf("address" to mapOf("city" to "San Francisco"))) // Match
    val doc3 = doc("users/c", 1000, mapOf())
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field("address.city").equal(constant("San Francisco")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2)
  }

  @Test
  fun `quoted nested property filter quoted nested`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("address.city" to "San Francisco")) // Match
    val doc2 = doc("users/b", 1000, mapOf("address" to mapOf("city" to "San Francisco")))
    val doc3 = doc("users/c", 1000, mapOf())
    val documents = listOf(doc1, doc2, doc3)

    val pipeline =
      RealtimePipelineSource(db)
        .collection("/users")
        .where(field(PublicFieldPath.of("address.city")).equal(constant("San Francisco")))

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1)
  }
}
