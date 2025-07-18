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
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class UnicodeTests {

  private val db = TestUtil.firestore()

  @Test
  fun `basic unicode`(): Unit = runBlocking {
    val doc1 = doc("🐵/Łukasiewicz", 1000, mapOf("Ł" to "Jan Łukasiewicz"))
    val doc2 = doc("🐵/Sierpiński", 1000, mapOf("Ł" to "Wacław Sierpiński"))
    val doc3 = doc("🐵/iwasawa", 1000, mapOf("Ł" to "岩澤"))

    val documents = listOf(doc1, doc2, doc3)
    val pipeline = RealtimePipelineSource(db).collection("/🐵").sort(field("Ł").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc2, doc3).inOrder()
  }

  @Test
  fun `unicode surrogates`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("str" to "🄟"))
    val doc2 = doc("users/b", 1000, mapOf("str" to "Ｐ"))
    val doc3 =
      doc("users/c", 1000, mapOf("str" to "︒")) // This char is U+FE12, sorts before P and 🄟

    val documents = listOf(doc1, doc2, doc3)
    val pipeline =
      RealtimePipelineSource(db)
        .collection("users") // C++ uses DatabaseSource, "users" collection matches doc paths
        .where(
          and(
            field("str").lte(constant("🄟")),
            field("str").gte(constant("Ｐ")),
          )
        )
        .sort(field("str").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc2, doc1).inOrder()
  }

  @Test
  fun `unicode surrogates in array`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("foo" to listOf("🄟")))
    val doc2 = doc("users/b", 1000, mapOf("foo" to listOf("Ｐ")))
    val doc3 = doc("users/c", 1000, mapOf("foo" to listOf("︒")))

    val documents = listOf(doc1, doc2, doc3)
    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("foo").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc3, doc2, doc1).inOrder()
  }

  @Test
  fun `unicode surrogates in map keys`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("map" to mapOf("︒" to true, "z" to true)))
    val doc2 = doc("users/b", 1000, mapOf("map" to mapOf("🄟" to true, "︒" to true)))
    val doc3 = doc("users/c", 1000, mapOf("map" to mapOf("Ｐ" to true, "︒" to true)))

    val documents = listOf(doc1, doc2, doc3)
    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("map").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3, doc2).inOrder()
  }

  @Test
  fun `unicode surrogates in map values`(): Unit = runBlocking {
    val doc1 = doc("users/a", 1000, mapOf("map" to mapOf("foo" to "︒")))
    val doc2 = doc("users/b", 1000, mapOf("map" to mapOf("foo" to "🄟")))
    val doc3 = doc("users/c", 1000, mapOf("map" to mapOf("foo" to "Ｐ")))

    val documents = listOf(doc1, doc2, doc3)
    val pipeline = RealtimePipelineSource(db).collection("users").sort(field("map").ascending())

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactly(doc1, doc3, doc2).inOrder()
  }
}
