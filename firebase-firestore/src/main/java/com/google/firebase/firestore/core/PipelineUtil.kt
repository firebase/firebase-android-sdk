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

package com.google.firebase.firestore.core

import com.google.firebase.firestore.RealtimePipeline
import com.google.firebase.firestore.model.Document
import com.google.firebase.firestore.model.ResourcePath
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.pipeline.CollectionGroupSource
import com.google.firebase.firestore.pipeline.CollectionSource
import com.google.firebase.firestore.pipeline.DatabaseSource
import com.google.firebase.firestore.pipeline.DocumentsSource
import com.google.firebase.firestore.pipeline.InternalOptions
import com.google.firebase.firestore.pipeline.LimitStage
import com.google.firebase.firestore.util.Assert.hardAssert

/** A class that wraps either a Query or a RealtimePipeline. */
sealed class QueryOrPipeline {
  data class QueryWrapper(val query: Query) : QueryOrPipeline()
  data class PipelineWrapper(val pipeline: RealtimePipeline) : QueryOrPipeline()

  val isQuery: Boolean
    get() = this is QueryWrapper

  val isPipeline: Boolean
    get() = this is PipelineWrapper

  fun query(): Query {
    return (this as QueryWrapper).query
  }

  fun pipeline(): RealtimePipeline {
    return (this as PipelineWrapper).pipeline
  }

  fun canonicalId(): String {
    return when (this) {
      is PipelineWrapper -> pipeline.canonicalId()
      is QueryWrapper -> query.canonicalId
    }
  }

  override fun toString(): String {
    return when (this) {
      is PipelineWrapper -> pipeline.canonicalId()
      is QueryWrapper -> query.toString()
    }
  }

  fun toTargetOrPipeline(): TargetOrPipeline {
    return when (this) {
      is PipelineWrapper -> TargetOrPipeline.PipelineWrapper(pipeline)
      is QueryWrapper -> TargetOrPipeline.TargetWrapper(query.toTarget())
    }
  }

  fun matchesAllDocuments(): Boolean {
    return when (this) {
      is PipelineWrapper -> pipeline.matchesAllDocuments()
      is QueryWrapper -> query.matchesAllDocuments()
    }
  }

  fun hasLimit(): Boolean {
    return when (this) {
      is PipelineWrapper -> pipeline.hasLimit()
      is QueryWrapper -> query.hasLimit()
    }
  }

  fun matches(doc: Document): Boolean {
    return when (this) {
      is PipelineWrapper -> pipeline.matches(doc)
      is QueryWrapper -> query.matches(doc)
    }
  }

  fun comparator(): Comparator<Document> {
    return when (this) {
      is PipelineWrapper -> pipeline.comparator()
      is QueryWrapper -> query.comparator()
    }
  }
}

/** A class that wraps either a Target or a RealtimePipeline. */
sealed class TargetOrPipeline {
  data class TargetWrapper(val target: Target) : TargetOrPipeline()
  data class PipelineWrapper(val pipeline: RealtimePipeline) : TargetOrPipeline()

  val isTarget: Boolean
    get() = this is TargetWrapper

  val isPipeline: Boolean
    get() = this is PipelineWrapper

  fun target(): Target {
    return (this as TargetWrapper).target
  }

  fun pipeline(): RealtimePipeline {
    return (this as PipelineWrapper).pipeline
  }

  val singleDocPath: ResourcePath?
    get() {
      return when (this) {
        is PipelineWrapper -> {
          if (getPipelineSourceType(pipeline) == PipelineSourceType.DOCUMENTS) {
            val docs = getPipelineDocuments(pipeline)
            if (docs != null && docs.size == 1) {
              return ResourcePath.fromString(docs[0])
            }
          }
          return null
        }
        is TargetWrapper -> {
          if (target.isDocumentQuery) {
            return target.path
          }

          return null
        }
      }
    }

  fun canonicalId(): String {
    return when (this) {
      is PipelineWrapper -> pipeline.canonicalId()
      is TargetWrapper -> target.canonicalId
    }
  }

  override fun toString(): String {
    return when (this) {
      is PipelineWrapper -> pipeline.canonicalId()
      is TargetWrapper -> target.toString()
    }
  }
}

enum class PipelineFlavor {
  // The pipeline exactly represents the query.
  EXACT,

  // The pipeline has additional fields projected (e.g., __key__,
  // __create_time__).
  AUGMENTED,

  // The pipeline has stages that remove document keys (e.g., aggregate,
  // distinct).
  KEYLESS,
}

// Describes the source of a pipeline.
enum class PipelineSourceType {
  COLLECTION,
  COLLECTION_GROUP,
  DATABASE,
  DOCUMENTS,
  UNKNOWN,
}

// Determines the flavor of the given pipeline based on its stages.
fun getPipelineFlavor(pipeline: RealtimePipeline): PipelineFlavor {
  // For now, it is only possible to construct RealtimePipeline that is kExact.
  // PORTING NOTE: the typescript implementation support other flavors already,
  // despite not being used. We can port that later.
  return PipelineFlavor.EXACT
}

// Determines the source type of the given pipeline based on its first stage.
fun getPipelineSourceType(pipeline: RealtimePipeline): PipelineSourceType {
  hardAssert(
    !pipeline.stages.isEmpty(),
    "Pipeline must have at least one stage to determine its source.",
  )
  return when (pipeline.stages.first()) {
    is CollectionSource -> PipelineSourceType.COLLECTION
    is CollectionGroupSource -> PipelineSourceType.COLLECTION_GROUP
    is DatabaseSource -> PipelineSourceType.DATABASE
    is DocumentsSource -> PipelineSourceType.DOCUMENTS
    else -> PipelineSourceType.UNKNOWN
  }
}

// Retrieves the collection group ID if the pipeline's source is a collection
// group.
fun getPipelineCollectionGroup(pipeline: RealtimePipeline): String? {
  if (getPipelineSourceType(pipeline) == PipelineSourceType.COLLECTION_GROUP) {
    hardAssert(
      !pipeline.stages.isEmpty(),
      "Pipeline source is CollectionGroup but stages are empty.",
    )
    val firstStage = pipeline.stages.first()
    if (firstStage is CollectionGroupSource) {
      return firstStage.collectionId
    }
  }
  return null
}

// Retrieves the collection path if the pipeline's source is a collection.
fun getPipelineCollection(pipeline: RealtimePipeline): String? {
  if (getPipelineSourceType(pipeline) == PipelineSourceType.COLLECTION) {
    hardAssert(
      !pipeline.stages.isEmpty(),
      "Pipeline source is Collection but stages are empty.",
    )
    val firstStage = pipeline.stages.first()
    if (firstStage is CollectionSource) {
      return firstStage.path.canonicalString()
    }
  }
  return null
}

// Retrieves the document pathes if the pipeline's source is a document source.
fun getPipelineDocuments(pipeline: RealtimePipeline): Array<out String>? {
  if (getPipelineSourceType(pipeline) == PipelineSourceType.DOCUMENTS) {
    hardAssert(
      !pipeline.stages.isEmpty(),
      "Pipeline source is Documents but stages are empty.",
    )
    val firstStage = pipeline.stages.first()
    if (firstStage is DocumentsSource) {
      return firstStage.documents.map { it.canonicalString() }.toTypedArray()
    }
  }
  return null
}

// Creates a new pipeline by replacing CollectionGroupSource stages with
// CollectionSource stages using the provided path.
fun asCollectionPipelineAtPath(
  pipeline: RealtimePipeline,
  path: ResourcePath,
): RealtimePipeline {
  val newStages =
    pipeline.stages.map { stagePtr ->
      if (stagePtr is CollectionGroupSource) {
        CollectionSource(path, pipeline.serializer, InternalOptions.EMPTY)
      } else {
        stagePtr
      }
    }

  // Construct a new RealtimePipeline with the (potentially) modified stages
  // and the original user_data_reader.
  return RealtimePipeline(
    pipeline.serializer,
    pipeline.userDataReader,
    newStages,
  )
}

fun getLastEffectiveLimit(pipeline: RealtimePipeline): Int? {
  for (stagePtr in pipeline.rewrittenStages.asReversed()) {
    // Check if the stage is a LimitStage
    if (stagePtr is LimitStage) {
      return stagePtr.limit
    }
    // TODO(pipeline): Consider other stages that might imply a limit,
    // e.g., FindNearestStage, once they are implemented.
  }
  return null
}

private fun getLastEffectiveSortOrderings(pipeline: RealtimePipeline): List<Ordering> {
  for (stage in pipeline.rewrittenStages.asReversed()) {
    if (stage is SortStage) {
      return stage.orders
    }
    // TODO(pipeline): Consider stages that might invalidate ordering later,
    // like fineNearest
  }
  HardAssert.hardFail(
    "RealtimePipeline must contain at least one Sort stage (ensured by RewriteStages)."
  )
}
