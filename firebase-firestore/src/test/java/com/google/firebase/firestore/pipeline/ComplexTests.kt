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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.RealtimePipelineSource
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.pipeline.Expr.Companion.add
import com.google.firebase.firestore.pipeline.Expr.Companion.and
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.field
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
internal class ComplexTests {

  private val db: FirebaseFirestore = TestUtil.firestore()
  private val collectionId = "test"
  private var docIdCounter = 1

  private fun nextDocId(): String = "${collectionId}/${docIdCounter++}"

  private fun seedDatabase(
    numOfDocuments: Int,
    numOfFields: Int,
    valueSupplier: (Int, Int) -> Any // docIndex, fieldIndex
  ): List<MutableDocument> {
    docIdCounter = 1 // Reset for each seed
    return List(numOfDocuments) { docIndex ->
      val fields =
        (1..numOfFields).associate { fieldIndex ->
          "field_$fieldIndex" to valueSupplier(docIndex, fieldIndex)
        }
      doc(nextDocId(), 1000, fields)
    }
  }

  @Test
  fun `where with max number of stages`(): Unit = runBlocking {
    val numOfFields = 127
    var valueCounter = 1L
    val documents = seedDatabase(10, numOfFields) { _, _ -> valueCounter++ }

    var pipeline = RealtimePipelineSource(db).collection(collectionId)
    for (i in 1..numOfFields) {
      pipeline = pipeline.where(field("field_$i").gt(0L))
    }

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(documents)
  }

  @Test
  fun `eqAny with max number of elements`(): Unit = runBlocking {
    val numOfDocuments = 1000
    val maxElements = 3000
    var valueCounter = 1L
    val documentsSource = seedDatabase(numOfDocuments, 1) { _, _ -> valueCounter++ }
    val nonMatchingDoc = doc(nextDocId(), 1000, mapOf("field_1" to 3001L))
    val allDocuments = documentsSource + nonMatchingDoc

    val values = List(maxElements) { i -> i + 1 }

    val pipeline =
      RealtimePipelineSource(db).collection(collectionId).where(field("field_1").eqAny(values))

    val result = runPipeline(pipeline, flowOf(*allDocuments.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(documentsSource)
  }

  @Test
  fun `eqAny with max number of elements on multiple fields`(): Unit = runBlocking {
    val numOfFields = 10
    val numOfDocuments = 100
    val maxElements = 3000
    var valueCounter = 1L
    val documentsSource = seedDatabase(numOfDocuments, numOfFields) { _, _ -> valueCounter++ }
    val nonMatchingDoc = doc(nextDocId(), 1000, mapOf("field_1" to 3001L))
    val allDocuments = documentsSource + nonMatchingDoc

    val values = List(maxElements) { i -> i + 1 }
    val conditions = (1..numOfFields).map { i -> field("field_$i").eqAny(values) }

    val pipeline =
      RealtimePipelineSource(db)
        .collection(collectionId)
        .where(and(conditions.first(), *conditions.drop(1).toTypedArray()))

    val result = runPipeline(pipeline, flowOf(*allDocuments.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(documentsSource)
  }

  @Test
  fun `notEqAny with max number of elements`(): Unit = runBlocking {
    val numOfDocuments = 1000
    val maxElements = 3000
    var valueCounter = 1L
    val documentsSource = seedDatabase(numOfDocuments, 1) { _, _ -> valueCounter++ }
    val matchingDoc = doc(nextDocId(), 1000, mapOf("field_1" to 3001L))
    val allDocuments = documentsSource + matchingDoc

    val values = List(maxElements) { i -> i + 1 }

    val pipeline =
      RealtimePipelineSource(db).collection(collectionId).where(field("field_1").notEqAny(values))

    val result = runPipeline(pipeline, flowOf(*allDocuments.toTypedArray())).toList()
    assertThat(result).containsExactly(matchingDoc)
  }

  @Test
  fun `notEqAny with max number of elements on multiple fields`(): Unit = runBlocking {
    val numOfFields = 10
    val numOfDocuments = 100
    val maxElements = 3000
    // Seed documents where field_x = (docIndex * numOfFields) + fieldIndex_1_based
    // This makes values unique and predictable.
    // For doc 0, field_1=1, field_2=2 ... field_10=10
    // For doc 1, field_1=11, field_2=12 ... field_10=20
    // Max value will be (99*10)+10 = 990+10 = 1000.
    val documentsSource =
      seedDatabase(numOfDocuments, numOfFields) { docIdx, fieldIdx ->
        (docIdx * numOfFields) + fieldIdx
      }

    // This doc has field_1 = 3001L (which is NOT IN 1..3000)
    // Other fields are not set, so they are absent.
    // An absent field when checked with notEqAny(someList) should evaluate to true (as it's not in
    // the list).
    val matchingDocData = mutableMapOf<String, Any>("field_1" to (maxElements + 1))
    // For the OR condition to be specific to field_1, other fields in matchingDoc
    // must be IN the `values` list if they exist.
    // Let's make other fields in matchingDoc have values that are in the `values` list.
    for (i in 2..numOfFields) {
      matchingDocData["field_$i"] = i // value i is in 1..3000
    }
    val matchingDoc = doc(nextDocId(), 1000, matchingDocData)
    val allDocuments = documentsSource + matchingDoc

    val values = List(maxElements) { i -> (i + 1) } // 1 to 3000

    val conditions = (1..numOfFields).map { i -> field("field_$i").notEqAny(values) }

    val pipeline =
      RealtimePipelineSource(db)
        .collection(collectionId)
        .where(or(conditions.first(), *conditions.drop(1).toTypedArray()))

    val result = runPipeline(pipeline, flowOf(*allDocuments.toTypedArray())).toList()
    // matchingDoc: field_1=3001 (not in values) -> true. Other fields are in values. So OR is true.
    // documentsSource: All fields have values from 1 to 1000. All are IN `values`. So notEqAny is
    // false for all fields. OR is false.
    assertThat(result).containsExactly(matchingDoc)
  }

  @Test
  fun `arrayContainsAny with large number of elements`(): Unit = runBlocking {
    val numOfDocuments = 1000
    val maxElements = 3000
    var valueCounter = 1
    val documentsSource =
      seedDatabase(numOfDocuments, 1) { _, _ ->
        listOf(valueCounter++)
      } // field_1 contains [valueCounter]
    val nonMatchingDoc = doc(nextDocId(), 1000, mapOf("field_1" to listOf((maxElements + 1))))
    val allDocuments = documentsSource + nonMatchingDoc

    val valuesToSearch = List(maxElements) { i -> (i + 1) }

    val pipeline =
      RealtimePipelineSource(db)
        .collection(collectionId)
        .where(field("field_1").arrayContainsAny(valuesToSearch))

    val result = runPipeline(pipeline, flowOf(*allDocuments.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(documentsSource)
  }

  @Test
  fun `arrayContainsAny with max number of elements on multiple fields`(): Unit = runBlocking {
    val numOfFields = 10
    val numOfDocuments = 100
    val maxElements = 3000
    var valueCounter = 1
    val documentsSource =
      seedDatabase(numOfDocuments, numOfFields) { _, _ -> listOf(valueCounter++) }

    // nonMatchingDoc: field_1 = [3001L]. Other fields will be arrays like [3002L], [3003L] etc.
    // if we use valueCounter for them.
    // To make it non-matching for an OR condition, all its array fields must not contain any of
    // valuesToSearch.
    val nonMatchingDocData =
      (1..numOfFields).associate { i -> "field_$i" to listOf((maxElements + i)) }
    val nonMatchingDoc = doc(nextDocId(), 1000, nonMatchingDocData)
    val allDocuments = documentsSource + nonMatchingDoc

    val valuesToSearch = List(maxElements) { i -> i + 1 } // 1 to 3000

    val conditions =
      (1..numOfFields).map { i -> field("field_$i").arrayContainsAny(valuesToSearch) }

    val pipeline =
      RealtimePipelineSource(db)
        .collection(collectionId)
        .where(or(conditions.first(), *conditions.drop(1).toTypedArray()))

    val result = runPipeline(pipeline, flowOf(*allDocuments.toTypedArray())).toList()
    // documentsSource: each field_i has a list like [some_value_between_1_and_1000].
    // Since valuesToSearch is [1..3000], arrayContainsAny will be true for each field. So OR is
    // true.
    // nonMatchingDoc: field_i has list like [3000+i]. None of these are in valuesToSearch.
    // So arrayContainsAny is false for all fields. OR is false.
    assertThat(result).containsExactlyElementsIn(documentsSource)
  }

  @Test
  fun `sortBy max num of fields without index`(): Unit = runBlocking {
    val numOfFields = 31
    val numOfDocuments = 100
    // All docs have field_i = 10L
    val documents = seedDatabase(numOfDocuments, numOfFields) { _, _ -> 10L }

    val sortOrders =
      (1..numOfFields)
        .map { i -> field("field_$i").ascending() }
        .plus(field(PublicFieldPath.documentId()).ascending())

    val pipeline =
      RealtimePipelineSource(db)
        .collection(collectionId)
        .sort(sortOrders.first(), *sortOrders.drop(1).toTypedArray())

    // Since all field values are the same, sort order is determined by document ID.
    val expectedDocs = documents.sortedBy { it.key }

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(expectedDocs).inOrder()
  }

  @Test
  fun `where with nested add function max depth`(): Unit = runBlocking {
    val numOfFields = 1
    val numOfDocuments = 10
    val depth = 31
    // All docs have field_1 = 0L
    val documents = seedDatabase(numOfDocuments, numOfFields) { _, _ -> 0L }

    var addExpr: Expr = field("field_1")
    for (i in 1..depth) {
      addExpr = add(addExpr, constant(1L))
    }
    // addExpr is field_1 + 1 (depth times) = field_1 + depth = 0 + 31 = 31

    val pipeline =
      RealtimePipelineSource(db).collection(collectionId).where(addExpr.gt(0L)) // 31 > 0L is true

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    assertThat(result).containsExactlyElementsIn(documents)
  }

  @Test
  fun `where with large number ors`(): Unit = runBlocking {
    val numOfFields = 100
    val numOfDocuments = 50
    // valueCounter removed as it was unused here. Seed values are generated based on docIdx and
    // fieldIdx.
    // field_1 = 1, field_2 = 2, ..., field_100 = 100 (for first doc)
    // field_1 = 101, field_2 = 102, ..., field_100 = 200 (for second doc)
    // ...
    // Max value assigned will be for the last field of the last document:
    // (49 * 100) + 100 = 4900 + 100 = 5000
    val documents =
      seedDatabase(numOfDocuments, numOfFields) { docIdx, fieldIdx ->
        (docIdx * numOfFields) + fieldIdx
      }
    val maxValueInDb = (numOfDocuments - 1) * numOfFields + numOfFields // 5000L

    val orConditions = (1..numOfFields).map { i -> field("field_$i").lte(maxValueInDb) }

    val pipeline =
      RealtimePipelineSource(db)
        .collection(collectionId)
        .where(or(orConditions.first(), *orConditions.drop(1).toTypedArray()))

    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    // Every document will have at least one field_i <= maxValueInDb (actually all fields are)
    assertThat(result).containsExactlyElementsIn(documents)
  }

  @Test
  fun `where with large number of conjunctions`(): Unit = runBlocking {
    val numOfFields = 50
    val numOfDocuments = 100
    // Values from 1 up to 100 * 50 = 5000
    val documents =
      seedDatabase(numOfDocuments, numOfFields) { docIdx, fieldIdx ->
        (docIdx * numOfFields) + fieldIdx
      }

    val andConditions1 =
      (1..numOfFields).map { i -> field("field_$i").gt(0L) } // Use 0L for clarity with Long types
    val andConditions2 = (1..numOfFields).map { i -> field("field_$i").lt(Long.MAX_VALUE) }

    val pipeline =
      RealtimePipelineSource(db)
        .collection(collectionId)
        .where(
          or(
            and(andConditions1.first(), *andConditions1.drop(1).toTypedArray()),
            and(andConditions2.first(), *andConditions2.drop(1).toTypedArray())
          )
        )
    val result = runPipeline(pipeline, flowOf(*documents.toTypedArray())).toList()
    // All seeded values are > 0 and < Long.MAX_VALUE, so all documents match.
    assertThat(result).containsExactlyElementsIn(documents)
  }
}
