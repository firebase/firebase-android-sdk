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
import com.google.firestore.v1.TransactionOptions
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
  fun `update stage with 0 args generates update proto with empty map`() {
    val pipeline = db.pipeline().collection("books").update()
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("update")
    assertThat(stage.argsCount).isEqualTo(1)
    assertThat(stage.getArgs(0).mapValue.fieldsCount).isEqualTo(0)
  }

  @Test
  fun `update stage with multiple varargs generates update proto with all fields`() {
    val pipeline =
      db
        .pipeline()
        .collection("books")
        .update(constant("Updated").`as`("status"), add(field("count"), constant(1)).`as`("count"))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("update")
    assertThat(stage.argsCount).isEqualTo(1)
    val fields = stage.getArgs(0).mapValue.fieldsMap
    assertThat(fields["status"]?.stringValue).isEqualTo("Updated")
    assertThat(fields.containsKey("count")).isTrue()
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
  fun `insert stage without documentIdExpression generates insert proto with only collection option`() {
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
          collectionPath = "books",
          documentIdExpression = constant("book1"),
          additionalFields = arrayOf(add(field("count"), constant(1)).`as`("count"))
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
  fun `in-place upsert with varargs generates upsert proto without options`() {
    val pipeline =
      db.pipeline().collection("books").upsert(add(field("count"), constant(1)).`as`("count"))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("upsert")
    assertThat(stage.argsCount).isEqualTo(1)
    assertThat(stage.optionsCount).isEqualTo(0)
  }

  @Test
  fun `in-place upsert with list generates upsert proto without options`() {
    val pipeline =
      db
        .pipeline()
        .collection("books")
        .upsert(listOf(add(field("count"), constant(1)).`as`("count")))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("upsert")
    assertThat(stage.argsCount).isEqualTo(1)
    assertThat(stage.optionsCount).isEqualTo(0)
  }

  @Test
  fun `target-collection upsert with collectionPath only generates upsert proto`() {
    val pipeline = db.pipeline().collection("books").upsert("books_backup")
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("upsert")
    assertThat(stage.argsCount).isEqualTo(1)
    assertThat(stage.getArgs(0).mapValue.fieldsCount).isEqualTo(0)
    assertThat(stage.optionsMap["collection"]?.referenceValue).isEqualTo("/books_backup")
    assertThat(stage.optionsMap.containsKey("document_id")).isFalse()
  }

  @Test
  fun `target-collection upsert with collectionPath and documentIdExpression generates upsert proto`() {
    val pipeline =
      db.pipeline().literals(mapOf("title" to "Upserted Book")).upsert("books", constant("book1"))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("upsert")
    assertThat(stage.argsCount).isEqualTo(1)
    assertThat(stage.getArgs(0).mapValue.fieldsCount).isEqualTo(0)
    assertThat(stage.optionsMap["collection"]?.referenceValue).isEqualTo("/books")
    assertThat(stage.optionsMap["document_id"]?.stringValue).isEqualTo("book1")
  }

  @Test
  fun `target-collection upsert with collectionPath, documentIdExpression, and additionalFields list generates upsert proto`() {
    val pipeline =
      db
        .pipeline()
        .literals(mapOf("title" to "Upserted Book", "count" to 1))
        .upsert(
          collectionPath = "books",
          documentIdExpression = constant("book1"),
          additionalFields = listOf(add(field("count"), constant(1)).`as`("count"))
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
  fun `in-place upsert with 0 args generates upsert proto without options`() {
    val pipeline = db.pipeline().collection("books").upsert()
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("upsert")
    assertThat(stage.argsCount).isEqualTo(1)
    assertThat(stage.getArgs(0).mapValue.fieldsCount).isEqualTo(0)
    assertThat(stage.optionsCount).isEqualTo(0)
  }

  @Test
  fun `in-place upsert with multiple varargs generates upsert proto without options`() {
    val pipeline =
      db
        .pipeline()
        .collection("books")
        .upsert(constant("In-Place").`as`("status"), add(field("count"), constant(1)).`as`("count"))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(2)

    val stage = proto.getStages(1)
    assertThat(stage.name).isEqualTo("upsert")
    assertThat(stage.argsCount).isEqualTo(1)
    val fields = stage.getArgs(0).mapValue.fieldsMap
    assertThat(fields["status"]?.stringValue).isEqualTo("In-Place")
    assertThat(fields.containsKey("count")).isTrue()
    assertThat(stage.optionsCount).isEqualTo(0)
  }

  @Test
  fun `literals stage with multiple maps generates literals proto with documents`() {
    val pipeline = db.pipeline().literals(mapOf("title" to "Book 1"), mapOf("title" to "Book 2"))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(1)

    val stage = proto.getStages(0)
    assertThat(stage.name).isEqualTo("literals")
    assertThat(stage.argsCount).isEqualTo(2)
    assertThat(stage.getArgs(0).mapValue.fieldsMap["title"]?.stringValue).isEqualTo("Book 1")
    assertThat(stage.getArgs(1).mapValue.fieldsMap["title"]?.stringValue).isEqualTo("Book 2")
  }

  @Test
  fun `literals stage with expressions and nested maps generates literals proto`() {
    val pipeline =
      db
        .pipeline()
        .literals(
          mapOf(
            "title" to "Book 1",
            "nested" to mapOf("key" to "value"),
            "computed" to add(constant(1), constant(2))
          )
        )
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    assertThat(proto.stagesCount).isEqualTo(1)

    val stage = proto.getStages(0)
    assertThat(stage.name).isEqualTo("literals")
    assertThat(stage.argsCount).isEqualTo(1)
    val fields = stage.getArgs(0).mapValue.fieldsMap
    assertThat(fields["title"]?.stringValue).isEqualTo("Book 1")
    assertThat(fields["nested"]?.mapValue?.fieldsMap?.get("key")?.stringValue).isEqualTo("value")
    assertThat(fields["computed"]?.functionValue?.name).isEqualTo("add")
  }

  @Test
  fun `atomic execution options configure newTransaction and autoCommitTransaction`() {
    val pipeline =
      db.pipeline().literals(mapOf("title" to "Atomic")).insert("books", constant("book1"))
    val executeOptions = Pipeline.ExecuteOptions().withAtomic(true)
    val request = pipeline.toExecutePipelineRequest(executeOptions.options)
    assertThat(request.hasNewTransaction()).isTrue()
    assertThat(request.newTransaction.hasReadWrite()).isTrue()
    assertThat(request.newTransaction.readWrite.concurrencyMode)
      .isEqualTo(TransactionOptions.ConcurrencyMode.OPTIMISTIC)
    assertThat(request.autoCommitTransaction).isTrue()
    assertThat(request.structuredPipeline.optionsMap).doesNotContainKey("atomic")
  }

  @Test
  fun `non-atomic execution options do not configure newTransaction or autoCommitTransaction`() {
    val pipeline =
      db.pipeline().literals(mapOf("title" to "Non-Atomic")).insert("books", constant("book1"))

    val executeOptionsDisabled = Pipeline.ExecuteOptions().withAtomic(false)
    val requestDisabled = pipeline.toExecutePipelineRequest(executeOptionsDisabled.options)
    assertThat(requestDisabled.hasNewTransaction()).isFalse()
    assertThat(requestDisabled.autoCommitTransaction).isFalse()
    assertThat(requestDisabled.structuredPipeline.optionsMap).doesNotContainKey("atomic")

    val executeOptionsDefault = Pipeline.ExecuteOptions()
    val requestDefault = pipeline.toExecutePipelineRequest(executeOptionsDefault.options)
    assertThat(requestDefault.hasNewTransaction()).isFalse()
    assertThat(requestDefault.autoCommitTransaction).isFalse()

    val requestNull = pipeline.toExecutePipelineRequest(null)
    assertThat(requestNull.hasNewTransaction()).isFalse()
    assertThat(requestNull.autoCommitTransaction).isFalse()
  }
}
