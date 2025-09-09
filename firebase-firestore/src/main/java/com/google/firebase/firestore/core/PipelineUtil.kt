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
import com.google.firebase.firestore.pipeline.Expr
import com.google.firebase.firestore.pipeline.Field
import com.google.firebase.firestore.pipeline.FunctionExpr
import com.google.firebase.firestore.pipeline.LimitStage
import com.google.firebase.firestore.pipeline.Ordering
import com.google.firebase.firestore.pipeline.SortStage
import com.google.firebase.firestore.pipeline.Stage
import com.google.firebase.firestore.pipeline.WhereStage
import com.google.firebase.firestore.util.Assert.fail
import com.google.firestore.v1.Value

private fun runPipeline(pipeline: RealtimePipeline, input: List<Document>): List<Document> {
  // This is a placeholder implementation. The actual pipeline execution logic is required.
  // For now, returning an empty list to ensure compilation.
  // A proper implementation would execute each stage of the pipeline on the input documents.
  return emptyList()
}

// Anonymous namespace for canonicalization helpers
private fun canonifyConstant(constant: Expr.Constant): String {
  return Values.canonicalId(constant.value)
}

private fun canonifyExpr(expr: Expr): String {
  return when (expr) {
    is Field -> "fld(${expr.fieldPath.canonicalString()})"
    is Expr.Constant -> "cst(${canonifyConstant(expr)})"
    is FunctionExpr -> {
      val paramStrings = expr.params.map { paramPtr -> canonifyExpr(paramPtr) }
      "fn(${expr.name}[${paramStrings.joinToString(",")}])"
    }
    else -> throw fail("Canonify a unrecognized expr")
  }
}

private fun canonifySortOrderings(orders: List<Ordering>): String {
  return orders
    .map { order ->
      val direction = if (order.dir == Ordering.Direction.ASCENDING) "asc" else "desc"
      "${canonifyExpr(order.expr)}$direction"
    }
    .joinToString(",")
}

private fun canonifyStage(stage: Stage<*>): String {
  return when (stage) {
    is CollectionSource -> "${stage.name}(${stage.path})"
    is CollectionGroupSource -> "${stage.name}(${stage.collectionId})"
    is DocumentsSource -> {
      val sortedDocuments = stage.documents.sorted()
      "${stage.name}(${sortedDocuments.joinToString(",")})"
    }
    is WhereStage -> "${stage.name}(${canonifyExpr(stage.expr)})"
    is SortStage -> "${stage.name}(${canonifySortOrderings(stage.orders)})"
    is LimitStage -> "${stage.name}(${stage.limit})"
    else -> throw fail("Trying to canonify an unrecognized stage type ${stage.name}")
  }
}

// Canonicalizes a RealtimePipeline by canonicalizing its stages.
private fun canonifyPipeline(pipeline: RealtimePipeline): String {
  return pipeline.rewriteStages().stages.map { stage -> canonifyStage(stage) }.joinToString("|")
}

/** A class that wraps either a Query or a RealtimePipeline. */
class QueryOrPipeline
private constructor(
  private val query: Query?,
  private val pipeline: RealtimePipeline?,
) {
  constructor(query: Query) : this(query, null)
  constructor(pipeline: RealtimePipeline) : this(null, pipeline)

  val isQuery: Boolean
    get() = query != null

  val isPipeline: Boolean
    get() = pipeline != null

  fun query(): Query {
    return query!!
  }

  fun pipeline(): RealtimePipeline {
    return pipeline!!
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is QueryOrPipeline) return false
    if (isPipeline != other.isPipeline) return false

    return if (isPipeline) {
      canonifyPipeline(pipeline()) == canonifyPipeline(other.pipeline())
    } else {
      query() == other.query()
    }
  }

  override fun hashCode(): Int {
    return if (isPipeline) {
      canonifyPipeline(pipeline()).hashCode()
    } else {
      query().hashCode()
    }
  }

  fun canonicalId(): String {
    return if (isPipeline) {
      canonifyPipeline(pipeline())
    } else {
      query().canonicalId()
    }
  }

  override fun toString(): String {
    return if (isPipeline) {
      canonicalId()
    } else {
      query().toString()
    }
  }

  fun toTargetOrPipeline(): TargetOrPipeline {
    return if (isPipeline) {
      TargetOrPipeline(pipeline())
    } else {
      TargetOrPipeline(query().toTarget())
    }
  }

  fun matchesAllDocuments(): Boolean {
    if (isPipeline) {
      for (stage in pipeline().rewrittenStages) {
        // Check for LimitStage
        if (stage.name == "limit") {
          return false
        }

        // Check for Where stage
        if (stage is Where) {
          // Check if it's the special 'exists(__name__)' case
          val funcExpr = stage.expr as? FunctionExpr
          if (funcExpr?.name == "exists" && funcExpr.params.size == 1) {
            val fieldExpr = funcExpr.params[0] as? Field
            if (fieldExpr?.fieldPath?.isKeyFieldPath == true) {
              continue // This specific 'exists(__name__)' filter doesn't count
            }
          }
          return false // Any other Where stage means it filters documents
        }
        // TODO(pipeline) : Add checks for other filtering stages like Aggregate,
        // Distinct, FindNearest once they are implemented.
      }
      return true // No filtering stages found (besides allowed ones)
    }

    return query().matchesAllDocuments()
  }

  fun hasLimit(): Boolean {
    if (isPipeline) {
      for (stage in pipeline().rewrittenStages) {
        // Check for LimitStage
        if (stage.name == "limit") {
          return true
        }
        // TODO(pipeline): need to check for other stages that could have a limit,
        // like findNearest
      }
      return false
    }

    return query().hasLimit()
  }

  fun matches(doc: Document): Boolean {
    if (isPipeline) {
      val result = runPipeline(pipeline(), listOf(doc))
      return result.isNotEmpty()
    }

    return query().matches(doc)
  }

  fun comparator(): DocumentComparator {
    if (isPipeline) {
      // Capture pipeline by reference. Orderings captured by value inside lambda.
      val p = pipeline()
      val orderings = getLastEffectiveSortOrderings(p)
      return DocumentComparator { d1, d2 ->
        val context = p.evaluateContext

        for (ordering in orderings) {
          val expr = ordering.expr
          // Evaluate expression for both documents using expr->Evaluate
          // (assuming this method exists) Pass const references to documents.
          val leftValue = expr.toEvaluable().evaluate(context, d1)
          val rightValue = expr.toEvaluable().evaluate(context, d2)

          // Compare results, using MinValue for error
          val comparison =
            Values.compare(
              if (leftValue.isErrorOrUnset) Value.getDefaultInstance() else leftValue.value!!,
              if (rightValue.isErrorOrUnset) Value.getDefaultInstance() else rightValue.value!!,
            )

          if (comparison != 0) {
            return@DocumentComparator if (ordering.direction == Ordering.Direction.ASCENDING) {
              comparison
            } else {
              -comparison
            }
          }
        }
        0
      }
    }

    return query().comparator()
  }
}

/** A class that wraps either a Target or a RealtimePipeline. */
class TargetOrPipeline
private constructor(
  private val target: Target?,
  private val pipeline: RealtimePipeline?,
) {
  constructor(target: Target) : this(target, null)
  constructor(pipeline: RealtimePipeline) : this(null, pipeline)

  val isTarget: Boolean
    get() = target != null

  val isPipeline: Boolean
    get() = pipeline != null

  fun target(): Target {
    return target!!
  }

  fun pipeline(): RealtimePipeline {
    return pipeline!!
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TargetOrPipeline) return false
    if (isPipeline != other.isPipeline) return false

    return if (isPipeline) {
      canonifyPipeline(pipeline()) == canonifyPipeline(other.pipeline())
    } else {
      target() == other.target()
    }
  }

  override fun hashCode(): Int {
    return if (isPipeline) {
      canonifyPipeline(pipeline()).hashCode()
    } else {
      target().hashCode()
    }
  }

  fun canonicalId(): String {
    return if (isPipeline) {
      canonifyPipeline(pipeline())
    } else {
      target().canonicalId()
    }
  }

  override fun toString(): String {
    return if (isPipeline) {
      canonicalId()
    } else {
      target().toString()
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
  HardAssert.hardAssert(
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
    HardAssert.hardAssert(
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
    HardAssert.hardAssert(
      !pipeline.stages.isEmpty(),
      "Pipeline source is Collection but stages are empty.",
    )
    val firstStage = pipeline.stages.first()
    if (firstStage is CollectionSource) {
      return firstStage.path
    }
  }
  return null
}

// Retrieves the document pathes if the pipeline's source is a document source.
fun getPipelineDocuments(pipeline: RealtimePipeline): List<String>? {
  if (getPipelineSourceType(pipeline) == PipelineSourceType.DOCUMENTS) {
    HardAssert.hardAssert(
      !pipeline.stages.isEmpty(),
      "Pipeline source is Documents but stages are empty.",
    )
    val firstStage = pipeline.stages.first()
    if (firstStage is DocumentsSource) {
      return firstStage.documents
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
        CollectionSource(path.canonicalString())
      } else {
        stagePtr
      }
    }

  // Construct a new RealtimePipeline with the (potentially) modified stages
  // and the original user_data_reader.
  return RealtimePipeline(
    newStages,
    Serializer(pipeline.evaluateContext.serializer.databaseId),
  )
}

fun getLastEffectiveLimit(pipeline: RealtimePipeline): Long? {
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
