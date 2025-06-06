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
internal class PipelineTests {

  @Test
  fun `runPipeline executes without error`(): Unit = runBlocking {
    val firestore = TestUtil.firestore()
    val pipeline = RealtimePipelineSource(firestore).collection("foo").where(field("bar").eq(42))

    val doc1: MutableDocument = doc("foo/1", 0, mapOf("bar" to 42))
    val doc2: MutableDocument = doc("foo/2", 0, mapOf("bar" to "43"))
    val doc3: MutableDocument = doc("xxx/1", 0, mapOf("bar" to 42))

    val list = runPipeline(firestore, pipeline, flowOf(doc1, doc2, doc3)).toList()

    assertThat(list).hasSize(1)
  }
}
