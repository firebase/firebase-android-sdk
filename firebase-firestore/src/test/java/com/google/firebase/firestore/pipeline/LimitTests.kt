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
import com.google.firebase.firestore.runPipeline
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class LimitTests {

  private val db = TestUtil.firestore()

  private fun createDocs(): List<MutableDocument> {
    val doc1 = doc("k/a", 1000, mapOf("a" to 1L, "b" to 2L))
    val doc2 = doc("k/b", 1000, mapOf("a" to 3L, "b" to 4L))
    val doc3 = doc("k/c", 1000, mapOf("a" to 5L, "b" to 6L))
    val doc4 = doc("k/d", 1000, mapOf("a" to 7L, "b" to 8L))
    return listOf(doc1, doc2, doc3, doc4)
  }

  @Test
  fun `limit zero`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(0)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `limit zero duplicated`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(0).limit(0).limit(0)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `limit one`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(1)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(1)
  }

  @Test
  fun `limit one duplicated`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(1).limit(1).limit(1)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(1)
  }

  @Test
  fun `limit two`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(2)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(2)
  }

  @Test
  fun `limit two duplicated`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(2).limit(2).limit(2)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(2)
  }

  @Test
  fun `limit three`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(3)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(3)
  }

  @Test
  fun `limit three duplicated`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(3).limit(3).limit(3)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(3)
  }

  @Test
  fun `limit four`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(4)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(4)
  }

  @Test
  fun `limit four duplicated`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(4).limit(4).limit(4)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(4)
  }

  @Test
  fun `limit five`(): Unit = runBlocking {
    val documents = createDocs() // Only 4 docs created
    val pipeline = RealtimePipelineSource(db).collection("k").limit(5)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(4) // Limited by actual doc count
  }

  @Test
  fun `limit five duplicated`(): Unit = runBlocking {
    val documents = createDocs() // Only 4 docs created
    val pipeline = RealtimePipelineSource(db).collection("k").limit(5).limit(5).limit(5)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(4) // Limited by actual doc count
  }

  @Test
  fun `limit max`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline = RealtimePipelineSource(db).collection("k").limit(Int.MAX_VALUE)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(4)
  }

  @Test
  fun `limit max duplicated`(): Unit = runBlocking {
    val documents = createDocs()
    val pipeline =
      RealtimePipelineSource(db)
        .collection("k")
        .limit(Int.MAX_VALUE)
        .limit(Int.MAX_VALUE)
        .limit(Int.MAX_VALUE)

    val result = runPipeline(pipeline, listOf(*documents.toTypedArray())).toList()
    assertThat(result).hasSize(4)
  }
}
