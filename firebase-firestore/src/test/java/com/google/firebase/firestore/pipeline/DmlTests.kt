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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreIntegrationTestFactory
import com.google.firebase.firestore.Pipeline
import com.google.firebase.firestore.Pipeline.ExecuteOptions
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.model.DatabaseId
import com.google.firebase.firestore.pipeline.Expression.Companion.add
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firestore.v1.TransactionOptions
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class DmlTests {

  private val db = TestUtil.firestore()
  private val otherDb =
    FirebaseFirestoreIntegrationTestFactory(DatabaseId.forDatabase("otherProject", "otherDb"))
      .firestore

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
    val pipeline = db.pipeline().collection("books").update(constant("Updated").alias("status"))
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
        .update(
          constant("Updated").alias("status"),
          add(field("count"), constant(1)).alias("count")
        )
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
  fun `insert stage without arguments generates insert proto without options`() {
    val pipeline = db.pipeline().collection("books").insert()
    val stage = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline.getStages(1)
    assertThat(stage.name).isEqualTo("insert")
    assertThat(stage.argsCount).isEqualTo(0)
    assertThat(stage.optionsCount).isEqualTo(0)
  }

  @Test
  fun `insert stage with only documentIdExpression generates insert proto without collection`() {
    val pipeline =
      db.pipeline().collection("books").insert(documentIdExpression = constant("book1_copy"))
    val stage = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline.getStages(1)
    assertThat(stage.name).isEqualTo("insert")
    assertThat(stage.optionsMap.containsKey("collection")).isFalse()
    assertThat(stage.optionsMap["document_id"]?.stringValue).isEqualTo("book1_copy")
  }

  @Test
  fun `insert stage with CollectionReference generates insert proto with collection option`() {
    val pipeline =
      db.pipeline().literals(mapOf("title" to "New Book")).insert(db.collection("books"))
    val stage = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline.getStages(1)
    assertThat(stage.name).isEqualTo("insert")
    assertThat(stage.optionsMap["collection"]?.referenceValue).isEqualTo("/books")
    assertThat(stage.optionsMap.containsKey("document_id")).isFalse()
  }

  @Test
  fun `insert stage with CollectionReference and documentIdExpression generates insert proto`() {
    val pipeline =
      db
        .pipeline()
        .literals(mapOf("title" to "New Book"))
        .insert(db.collection("books"), constant("book1"))
    val stage = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline.getStages(1)
    assertThat(stage.name).isEqualTo("insert")
    assertThat(stage.optionsMap["collection"]?.referenceValue).isEqualTo("/books")
    assertThat(stage.optionsMap["document_id"]?.stringValue).isEqualTo("book1")
  }

  @Test
  fun `insert stage rejects a CollectionReference from another Firestore instance`() {
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        db.pipeline().collection("books").insert(otherDb.collection("books"))
      }
    assertThat(error)
      .hasMessageThat()
      .isEqualTo("Provided collection reference is from a different Firestore instance.")
  }

  @Test
  fun `target-collection upsert with CollectionReference and documentIdExpression generates upsert proto`() {
    val pipeline =
      db
        .pipeline()
        .literals(mapOf("title" to "Upserted Book"))
        .upsert(db.collection("books"), constant("book1"))
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
  fun `in-place upsert with varargs generates upsert proto without options`() {
    val pipeline =
      db.pipeline().collection("books").upsert(add(field("count"), constant(1)).alias("count"))
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
  fun `target-collection upsert with CollectionReference only generates upsert proto`() {
    val pipeline = db.pipeline().collection("books").upsert(db.collection("books_backup"))
    val stage = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline.getStages(1)
    assertThat(stage.name).isEqualTo("upsert")
    assertThat(stage.optionsMap["collection"]?.referenceValue).isEqualTo("/books_backup")
    assertThat(stage.optionsMap.containsKey("document_id")).isFalse()
  }

  @Test
  fun `target-collection upsert rejects a CollectionReference from another Firestore instance`() {
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        db.pipeline().collection("books").upsert(otherDb.collection("books"), constant("book1"))
      }
    assertThat(error)
      .hasMessageThat()
      .isEqualTo("Provided collection reference is from a different Firestore instance.")
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
        .upsert(
          constant("In-Place").alias("status"),
          add(field("count"), constant(1)).alias("count")
        )
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
  fun `literals stage wraps nested map containing expressions in a map expression`() {
    val pipeline =
      db
        .pipeline()
        .literals(
          mapOf("nested" to mapOf("sum" to add(constant(1L), constant(2L)), "label" to "x"))
        )
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    val fields = proto.getStages(0).getArgs(0).mapValue.fieldsMap

    val nested = fields["nested"]!!
    assertThat(nested.hasFunctionValue()).isTrue()
    assertThat(nested.functionValue.name).isEqualTo("map")
    // map(key1, value1, key2, value2): keys are constants, values keep their expressions.
    val args = nested.functionValue.argsList
    assertThat(args).hasSize(4)
    val entries = args.chunked(2).associate { (k, v) -> k.stringValue to v }
    assertThat(entries["sum"]?.functionValue?.name).isEqualTo("add")
    assertThat(entries["label"]?.stringValue).isEqualTo("x")
  }

  @Test
  fun `literals stage wraps list containing expressions in an array expression`() {
    val pipeline =
      db.pipeline().literals(mapOf("list" to listOf(add(constant(1L), constant(2L)), 1L, null)))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    val list = proto.getStages(0).getArgs(0).mapValue.fieldsMap["list"]!!

    assertThat(list.hasFunctionValue()).isTrue()
    assertThat(list.functionValue.name).isEqualTo("array")
    val args = list.functionValue.argsList
    assertThat(args).hasSize(3)
    assertThat(args[0].functionValue.name).isEqualTo("add")
    assertThat(args[1].integerValue).isEqualTo(1L)
    assertThat(args[2].hasNullValue()).isTrue()
  }

  @Test
  fun `literals stage wraps deeply nested expressions at the top-level field`() {
    val pipeline =
      db.pipeline().literals(mapOf("outer" to mapOf("inner" to listOf(mapOf("v" to constant(1L))))))
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    val outer = proto.getStages(0).getArgs(0).mapValue.fieldsMap["outer"]!!

    assertThat(outer.functionValue.name).isEqualTo("map")
    val inner = outer.functionValue.getArgs(1)
    assertThat(inner.functionValue.name).isEqualTo("array")
    val element = inner.functionValue.getArgs(0)
    assertThat(element.functionValue.name).isEqualTo("map")
    assertThat(element.functionValue.getArgs(1).integerValue).isEqualTo(1L)
  }

  @Test
  fun `literals stage encodes nested values without expressions as plain values`() {
    val pipeline =
      db
        .pipeline()
        .literals(
          mapOf("nested" to mapOf("key" to "value", "n" to null), "list" to listOf(1L, "a"))
        )
    val proto = pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline
    val fields = proto.getStages(0).getArgs(0).mapValue.fieldsMap

    assertThat(fields["nested"]!!.hasMapValue()).isTrue()
    assertThat(fields["nested"]!!.mapValue.fieldsMap["key"]?.stringValue).isEqualTo("value")
    assertThat(fields["nested"]!!.mapValue.fieldsMap["n"]?.hasNullValue()).isTrue()
    assertThat(fields["list"]!!.hasArrayValue()).isTrue()
    assertThat(fields["list"]!!.arrayValue.valuesList.map { it.valueTypeCase.name })
      .containsExactly("INTEGER_VALUE", "STRING_VALUE")
      .inOrder()
  }

  @Test
  fun `literals without documents is left for the backend to reject`() {
    val stage =
      db
        .pipeline()
        .literals()
        .toExecutePipelineRequest(null)
        .structuredPipeline
        .pipeline
        .getStages(0)
    assertThat(stage.name).isEqualTo("literals")
    assertThat(stage.argsCount).isEqualTo(0)
  }

  @Test
  fun `literals leaves empty field names for the backend to reject`() {
    val stage =
      db
        .pipeline()
        .literals(mapOf("" to 1L, "m" to mapOf("" to 2L)))
        .toExecutePipelineRequest(null)
        .structuredPipeline
        .pipeline
        .getStages(0)
    val fields = stage.getArgs(0).mapValue.fieldsMap
    assertThat(fields[""]?.integerValue).isEqualTo(1L)
    assertThat(fields["m"]?.mapValue?.fieldsMap?.get("")?.integerValue).isEqualTo(2L)
  }

  @Test
  fun `literals rejects top-level FieldValue sentinel with literals context and field path`() {
    val pipeline = db.pipeline().literals(mapOf("a" to 1L, "ts" to FieldValue.serverTimestamp()))
    val error =
      assertThrows(IllegalArgumentException::class.java) { pipeline.toExecutePipelineRequest(null) }
    assertThat(error)
      .hasMessageThat()
      .isEqualTo(
        "Function literals() called with invalid data. " +
          "FieldValue.serverTimestamp() can only be used with set() and update() (found in field ts)"
      )
  }

  @Test
  fun `literals rejects nested FieldValue sentinel with full field path`() {
    val pipeline = db.pipeline().literals(mapOf("a" to mapOf("b" to FieldValue.increment(1))))
    val error =
      assertThrows(IllegalArgumentException::class.java) { pipeline.toExecutePipelineRequest(null) }
    assertThat(error)
      .hasMessageThat()
      .isEqualTo(
        "Function literals() called with invalid data. " +
          "FieldValue.increment() can only be used with set() and update() (found in field a.b)"
      )
  }

  @Test
  fun `literals rejects FieldValue sentinel next to a nested expression with field path`() {
    val pipeline =
      db
        .pipeline()
        .literals(
          mapOf("a" to mapOf("sum" to add(constant(1L), constant(2L)), "d" to FieldValue.delete()))
        )
    val error =
      assertThrows(IllegalArgumentException::class.java) { pipeline.toExecutePipelineRequest(null) }
    assertThat(error)
      .hasMessageThat()
      .isEqualTo(
        "Function literals() called with invalid data. " +
          "FieldValue.delete() can only be used with set() and update() (found in field a.d)"
      )
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
