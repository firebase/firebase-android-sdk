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

package com.google.firebase.firestore.pipeline

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.Pipeline
import com.google.firebase.firestore.Pipeline.ExecuteOptions
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.pipeline.Expression.Companion.add
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class DmlTests {

  private val db = TestUtil.firestore()

  @Test
  fun `delete stage generates delete proto`() {
    val pipeline = db.pipeline().collection("books").delete()
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("delete")
    assertThat(stage.argsCount).isEqualTo(0)
  }

  @Test
  fun `update stage generates update proto with fields`() {
    val pipeline = db.pipeline().collection("books").update(constant("Updated").`as`("status"))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("update")
    assertThat(stage.argsCount).isEqualTo(1)
    assertThat(stage.getArgs(0).mapValue.fieldsMap["status"]?.stringValue).isEqualTo("Updated")
  }

  @Test
  fun `insert stage generates insert proto with options`() {
    val pipeline =
      db.pipeline().literals(mapOf("title" to "New Book")).insert("books", constant("book1"))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("insert")
    assertThat(stage.optionsMap["collection"]?.referenceValue).isEqualTo("/books")
    assertThat(stage.optionsMap["document_id"]?.stringValue).isEqualTo("book1")
  }

  @Test
  fun `insert stage without documentIdExpr generates insert proto with only collection option`() {
    val pipeline = db.pipeline().literals(mapOf("title" to "New Book")).insert("books")
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("insert")
    assertThat(stage.optionsMap["collection"]?.referenceValue).isEqualTo("/books")
    assertThat(stage.optionsMap.containsKey("document_id")).isFalse()
  }

  @Test
  fun `upsert stage generates upsert proto with transforms and options`() {
    val pipeline =
      db
        .pipeline()
        .literals(mapOf("title" to "Upserted Book", "count" to 1))
        .upsert(
          add(field("count"), constant(1)).`as`("count"),
          collectionPath = "books",
          documentIdExpr = constant("book1")
        )
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("upsert")
    assertThat(stage.argsCount).isEqualTo(1)
    assertThat(stage.optionsMap["collection"]?.referenceValue).isEqualTo("/books")
    assertThat(stage.optionsMap["document_id"]?.stringValue).isEqualTo("book1")
  }

  @Test
  fun `atomic execution options configure newTransaction and autoCommitTransaction`() {
    val pipeline =
      db.pipeline().literals(mapOf("title" to "Atomic")).insert("books", constant("book1"))
    val executeOptions = Pipeline.ExecuteOptions().withAtomic(true)
    val request = pipeline.toExecutePipelineRequest(executeOptions.options)
    assertThat(request.hasNewTransaction()).isTrue()
    assertThat(request.newTransaction.hasReadWrite()).isTrue()
    assertThat(request.autoCommitTransaction).isTrue()
  }

  @Test
  fun `non-atomic execution options do not configure newTransaction or autoCommitTransaction`() {
    val pipeline =
      db.pipeline().literals(mapOf("title" to "Non-Atomic")).insert("books", constant("book1"))

    val executeOptionsDisabled = Pipeline.ExecuteOptions().withAtomic(false)
    val requestDisabled = pipeline.toExecutePipelineRequest(executeOptionsDisabled.options)
    assertThat(requestDisabled.hasNewTransaction()).isFalse()
    assertThat(requestDisabled.autoCommitTransaction).isFalse()

    val executeOptionsDefault = Pipeline.ExecuteOptions()
    val requestDefault = pipeline.toExecutePipelineRequest(executeOptionsDefault.options)
    assertThat(requestDefault.hasNewTransaction()).isFalse()
    assertThat(requestDefault.autoCommitTransaction).isFalse()

    val requestNull = pipeline.toExecutePipelineRequest(null)
    assertThat(requestNull.hasNewTransaction()).isFalse()
    assertThat(requestNull.autoCommitTransaction).isFalse()
  }
}
