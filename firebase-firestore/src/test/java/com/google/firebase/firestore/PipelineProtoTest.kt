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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.model.DatabaseId
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.SearchStage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PipelineProtoTest {

  @Test
  fun testSearchStageProtoEncoding() {
    val databaseId = DatabaseId.forDatabase("new-project", "(default)")
    val firestore = FirebaseFirestoreIntegrationTestFactory(databaseId).firestore

    val pipeline =
      firestore
        .pipeline()
        .collection("foo")
        .search(
          SearchStage.withQuery("foo")
            // TODO(search) enable with backend support
            // .withLimit(1)
            // .withRetrievalDepth(2)
            // .withOffset(3)
            // .withQueryEnhancement(SearchStage.QueryEnhancement.REQUIRED)
            // .withLanguageCode("en-US")
            .withSort(field("foo").ascending())
            .withAddFields(constant(true).alias("bar"))
          // .withSelect(field("id"))
          )

    val request = pipeline.toExecutePipelineRequest(null)

    assertThat(request.database).isEqualTo("projects/new-project/databases/(default)")

    val structuredPipeline = request.structuredPipeline
    val protoPipeline = structuredPipeline.pipeline
    assertThat(protoPipeline.stagesCount).isEqualTo(2)

    val collectionStage = protoPipeline.getStages(0)
    assertThat(collectionStage.name).isEqualTo("collection")
    assertThat(collectionStage.getArgs(0).referenceValue).isEqualTo("/foo")

    val searchStage = protoPipeline.getStages(1)
    assertThat(searchStage.name).isEqualTo("search")

    val options = searchStage.optionsMap

    // query
    val query = options["query"]!!
    assertThat(query.functionValue.name).isEqualTo("document_matches")
    assertThat(query.functionValue.getArgs(0).stringValue).isEqualTo("foo")

    // TODO(search) enable with backend support
    // // limit
    // assertThat(options["limit"]?.integerValue).isEqualTo(1L)

    // // retrieval_depth
    // assertThat(options["retrieval_depth"]?.integerValue).isEqualTo(2L)

    // // offset
    // assertThat(options["offset"]?.integerValue).isEqualTo(3L)

    // // query_enhancement
    // assertThat(options["query_enhancement"]?.stringValue).isEqualTo("required")

    // // language_code
    // assertThat(options["language_code"]?.stringValue).isEqualTo("en-US")

    // // select
    // val select = options["select"]!!
    // assertThat(select.mapValue.fieldsMap["id"]?.fieldReferenceValue).isEqualTo("id")

    // sort
    val sort = options["sort"]!!
    val sortEntry = sort.arrayValue.getValues(0).mapValue.fieldsMap
    assertThat(sortEntry["direction"]?.stringValue).isEqualTo("ascending")
    assertThat(sortEntry["expression"]?.fieldReferenceValue).isEqualTo("foo")

    // add_fields
    val addFields = options["add_fields"]!!
    assertThat(addFields.mapValue.fieldsMap["bar"]?.booleanValue).isTrue()
  }
}
