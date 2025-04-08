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

package com.google.firebase.firestore

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.common.collect.FluentIterable
import com.google.common.collect.ImmutableList
import com.google.firebase.Timestamp
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.pipeline.AddFieldsStage
import com.google.firebase.firestore.pipeline.AggregateStage
import com.google.firebase.firestore.pipeline.AggregateWithAlias
import com.google.firebase.firestore.pipeline.BooleanExpr
import com.google.firebase.firestore.pipeline.CollectionGroupSource
import com.google.firebase.firestore.pipeline.CollectionSource
import com.google.firebase.firestore.pipeline.DatabaseSource
import com.google.firebase.firestore.pipeline.DistinctStage
import com.google.firebase.firestore.pipeline.DocumentsSource
import com.google.firebase.firestore.pipeline.Expr
import com.google.firebase.firestore.pipeline.Field
import com.google.firebase.firestore.pipeline.FindNearestStage
import com.google.firebase.firestore.pipeline.GenericArg
import com.google.firebase.firestore.pipeline.GenericStage
import com.google.firebase.firestore.pipeline.LimitStage
import com.google.firebase.firestore.pipeline.OffsetStage
import com.google.firebase.firestore.pipeline.Ordering
import com.google.firebase.firestore.pipeline.RemoveFieldsStage
import com.google.firebase.firestore.pipeline.ReplaceStage
import com.google.firebase.firestore.pipeline.SampleStage
import com.google.firebase.firestore.pipeline.SelectStage
import com.google.firebase.firestore.pipeline.Selectable
import com.google.firebase.firestore.pipeline.SortStage
import com.google.firebase.firestore.pipeline.Stage
import com.google.firebase.firestore.pipeline.UnionStage
import com.google.firebase.firestore.pipeline.UnnestStage
import com.google.firebase.firestore.pipeline.WhereStage
import com.google.firebase.firestore.util.Preconditions
import com.google.firestore.v1.ExecutePipelineRequest
import com.google.firestore.v1.StructuredPipeline
import com.google.firestore.v1.Value

class Pipeline
internal constructor(
  internal val firestore: FirebaseFirestore,
  internal val userDataReader: UserDataReader,
  private val stages: FluentIterable<Stage<*>>
) {
  internal constructor(
    firestore: FirebaseFirestore,
    userDataReader: UserDataReader,
    stage: Stage<*>
  ) : this(firestore, userDataReader, FluentIterable.of(stage))

  private fun append(stage: Stage<*>): Pipeline {
    return Pipeline(firestore, userDataReader, stages.append(stage))
  }

  fun execute(): Task<PipelineSnapshot> {
    val observerTask = ObserverSnapshotTask()
    firestore.callClient { call -> call!!.executePipeline(toProto(), observerTask) }
    return observerTask.task
  }

  internal fun documentReference(key: DocumentKey): DocumentReference {
    return DocumentReference(key, firestore)
  }

  private fun toProto(): ExecutePipelineRequest {
    val database = firestore.databaseId
    val builder = ExecutePipelineRequest.newBuilder()
    builder.database = "projects/${database.projectId}/databases/${database.databaseId}"
    builder.structuredPipeline = toStructuredPipelineProto()
    return builder.build()
  }

  private fun toStructuredPipelineProto(): StructuredPipeline {
    val builder = StructuredPipeline.newBuilder()
    builder.pipeline = toPipelineProto()
    return builder.build()
  }

  internal fun toPipelineProto(): com.google.firestore.v1.Pipeline =
    com.google.firestore.v1.Pipeline.newBuilder()
      .addAllStages(stages.map { it.toProtoStage(userDataReader) })
      .build()

  fun genericStage(name: String, vararg arguments: Any): Pipeline =
    append(GenericStage(name, arguments.map(GenericArg::from)))

  fun genericStage(stage: GenericStage): Pipeline = append(stage)

  fun addFields(vararg fields: Selectable): Pipeline = append(AddFieldsStage(fields))

  fun removeFields(vararg fields: Field): Pipeline = append(RemoveFieldsStage(fields))

  fun removeFields(vararg fields: String): Pipeline =
    append(RemoveFieldsStage(fields.map(Field::of).toTypedArray()))

  fun select(vararg fields: Selectable): Pipeline = append(SelectStage(fields))

  fun select(vararg fields: String): Pipeline =
    append(SelectStage(fields.map(Field::of).toTypedArray()))

  fun select(vararg fields: Any): Pipeline =
    append(SelectStage(fields.map(Selectable::toSelectable).toTypedArray()))

  fun sort(vararg orders: Ordering): Pipeline = append(SortStage(orders))

  fun where(condition: BooleanExpr): Pipeline = append(WhereStage(condition))

  fun offset(offset: Int): Pipeline = append(OffsetStage(offset))

  fun limit(limit: Int): Pipeline = append(LimitStage(limit))

  fun distinct(vararg groups: Selectable): Pipeline = append(DistinctStage(groups))

  fun distinct(vararg groups: String): Pipeline =
    append(DistinctStage(groups.map(Field::of).toTypedArray()))

  fun distinct(vararg groups: Any): Pipeline =
    append(DistinctStage(groups.map(Selectable::toSelectable).toTypedArray()))

  fun aggregate(vararg accumulators: AggregateWithAlias): Pipeline =
    append(AggregateStage.withAccumulators(*accumulators))

  fun aggregate(aggregateStage: AggregateStage): Pipeline = append(aggregateStage)

  fun findNearest(
    property: Expr,
    vector: DoubleArray,
    distanceMeasure: FindNearestStage.DistanceMeasure,
  ) = append(FindNearestStage.of(property, vector, distanceMeasure))

  fun findNearest(
    propertyField: String,
    vector: DoubleArray,
    distanceMeasure: FindNearestStage.DistanceMeasure,
  ) = append(FindNearestStage.of(propertyField, vector, distanceMeasure))

  fun findNearest(
    property: Expr,
    vector: Expr,
    distanceMeasure: FindNearestStage.DistanceMeasure,
  ) = append(FindNearestStage.of(property, vector, distanceMeasure))

  fun findNearest(
    propertyField: String,
    vector: Expr,
    distanceMeasure: FindNearestStage.DistanceMeasure,
  ) = append(FindNearestStage.of(propertyField, vector, distanceMeasure))

  fun findNearest(stage: FindNearestStage) = append(stage)

  fun replace(field: String): Pipeline = replace(Field.of(field))

  fun replace(field: Selectable): Pipeline =
    append(ReplaceStage(field, ReplaceStage.Mode.FULL_REPLACE))

  fun sample(documents: Int): Pipeline = append(SampleStage.withDocLimit(documents))

  fun sample(sample: SampleStage): Pipeline = append(sample)

  fun union(other: Pipeline): Pipeline = append(UnionStage(other))

  fun unnest(field: String, alias: String): Pipeline = unnest(UnnestStage.withField(field, alias))

  fun unnest(selectable: Selectable): Pipeline = append(UnnestStage(selectable))

  fun unnest(stage: UnnestStage): Pipeline = append(stage)

  private inner class ObserverSnapshotTask : PipelineResultObserver {
    private val userDataWriter =
      UserDataWriter(firestore, DocumentSnapshot.ServerTimestampBehavior.DEFAULT)
    private val taskCompletionSource = TaskCompletionSource<PipelineSnapshot>()
    private val results: ImmutableList.Builder<PipelineResult> = ImmutableList.builder()
    override fun onDocument(
      key: DocumentKey?,
      data: Map<String, Value>,
      createTime: Timestamp?,
      updateTime: Timestamp?
    ) {
      results.add(
        PipelineResult(
          firestore,
          userDataWriter,
          if (key == null) null else DocumentReference(key, firestore),
          data,
          createTime,
          updateTime
        )
      )
    }

    override fun onComplete(executionTime: Timestamp) {
      taskCompletionSource.setResult(PipelineSnapshot(executionTime, results.build()))
    }

    override fun onError(exception: FirebaseFirestoreException) {
      taskCompletionSource.setException(exception)
    }

    val task: Task<PipelineSnapshot>
      get() = taskCompletionSource.task
  }
}

/** Start of a Firestore Pipeline */
class PipelineSource internal constructor(private val firestore: FirebaseFirestore) {

  /**
   * Convert the given Query into an equivalent Pipeline.
   *
   * @param query A Query to be converted into a Pipeline.
   * @return Pipeline that is equivalent to [query]
   * @throws [IllegalArgumentException] Thrown if the [query] provided targets a different project
   * or database than the pipeline.
   */
  fun createFrom(query: Query): Pipeline {
    if (query.firestore.databaseId != firestore.databaseId) {
      throw IllegalArgumentException("Provided query is from a different Firestore instance.")
    }
    return query.query.toPipeline(firestore, firestore.userDataReader)
  }

  /**
   * Convert the given Aggregate Query into an equivalent Pipeline.
   *
   * @param aggregateQuery An Aggregate Query to be converted into a Pipeline.
   * @return Pipeline that is equivalent to [aggregateQuery]
   * @throws [IllegalArgumentException] Thrown if the [aggregateQuery] provided targets a different
   * project or database than the pipeline.
   */
  fun createFrom(aggregateQuery: AggregateQuery): Pipeline =
    createFrom(aggregateQuery.query)
      .aggregate(
        *aggregateQuery.aggregateFields
          .map(AggregateField::toPipeline)
          .toTypedArray<AggregateWithAlias>()
      )

  /**
   * Set the pipeline's source to the collection specified by the given path.
   *
   * @param path A path to a collection that will be the source of this pipeline.
   * @return Pipeline with documents from target collection.
   */
  fun collection(path: String): Pipeline =
    // Validate path by converting to CollectionReference
    collection(firestore.collection(path))

  /**
   * Set the pipeline's source to the collection specified by the given CollectionReference.
   *
   * @param ref A CollectionReference for a collection that will be the source of this pipeline.
   * @return Pipeline with documents from target collection.
   * @throws [IllegalArgumentException] Thrown if the [ref] provided targets a different project or
   * database than the pipeline.
   */
  fun collection(ref: CollectionReference): Pipeline {
    if (ref.firestore.databaseId != firestore.databaseId) {
      throw IllegalArgumentException(
        "Provided collection reference is from a different Firestore instance."
      )
    }
    return Pipeline(firestore, firestore.userDataReader, CollectionSource(ref.path))
  }

  /**
   * Set the pipeline's source to the collection group with the given id.
   *
   * @param collectionid The id of a collection group that will be the source of this pipeline.
   */
  fun collectionGroup(collectionId: String): Pipeline {
    Preconditions.checkNotNull(collectionId, "Provided collection ID must not be null.")
    require(!collectionId.contains("/")) {
      "Invalid collectionId '$collectionId'. Collection IDs must not contain '/'."
    }
    return Pipeline(firestore, firestore.userDataReader, CollectionGroupSource(collectionId))
  }

  /**
   * Set the pipeline's source to be all documents in this database.
   *
   * @return Pipeline with all documents in this database.
   */
  fun database(): Pipeline = Pipeline(firestore, firestore.userDataReader, DatabaseSource())

  /**
   * Set the pipeline's source to the documents specified by the given paths.
   *
   * @param documents Paths specifying the individual documents that will be the source of this
   * pipeline.
   * @return Pipeline with [documents].
   */
  fun documents(vararg documents: String): Pipeline =
    // Validate document path by converting to DocumentReference
    documents(*documents.map(firestore::document).toTypedArray())

  /**
   * Set the pipeline's source to the documents specified by the given DocumentReferences.
   *
   * @param documents DocumentReferences specifying the individual documents that will be the source
   * of this pipeline.
   * @return Pipeline with [documents].
   * @throws [IllegalArgumentException] Thrown if the [documents] provided targets a different
   * project or database than the pipeline.
   */
  fun documents(vararg documents: DocumentReference): Pipeline {
    val databaseId = firestore.databaseId
    for (document in documents) {
      if (document.firestore.databaseId != databaseId) {
        throw IllegalArgumentException(
          "Provided document reference is from a different Firestore instance."
        )
      }
    }
    return Pipeline(
      firestore,
      firestore.userDataReader,
      DocumentsSource(documents.map { docRef -> "/" + docRef.path }.toTypedArray())
    )
  }
}

/**
 */
class PipelineSnapshot
internal constructor(executionTime: Timestamp, results: List<PipelineResult>) :
  Iterable<PipelineResult> {

  /** The time at which the pipeline producing this result is executed. */
  val executionTime: Timestamp = executionTime

  /** List of all the results */
  val results: List<PipelineResult> = results

  override fun iterator() = results.iterator()
}

class PipelineResult
internal constructor(
  private val firestore: FirebaseFirestore,
  private val userDataWriter: UserDataWriter,
  ref: DocumentReference?,
  private val fields: Map<String, Value>,
  createTime: Timestamp?,
  updateTime: Timestamp?,
) {

  /** The time the document was created. Null if this result is not a document. */
  val createTime: Timestamp? = createTime

  /**
   * The time the document was last updated (at the time the snapshot was generated). Null if this
   * result is not a document.
   */
  val updateTime: Timestamp? = updateTime

  /**
   * The reference to the document, if the query returns the `__name__` field for a document. The
   * name field will be returned by default if querying a document.
   *
   * The `__name__` field will not be returned if the query projects away this field. For example:
   * ```
   *   // this query does not select the `__name__` field as part of the select stage,
   *   // so the __name__ field will not be in the output docs from this stage
   *   db.pipeline().collection("books").select("title", "desc")
   * ```
   *
   * The `__name__` field will not be returned from queries with aggregate or distinct stages.
   *
   * @return [DocumentReference] Reference to the document, if applicable.
   */
  val ref: DocumentReference? = ref

  /**
   * Returns the ID of the document represented by this result. Returns null if this result is not
   * corresponding to a Firestore document.
   *
   * @return ID of document, if applicable.
   */
  fun getId(): String? = ref?.id

  /**
   * Retrieves all fields in the result as an object map.
   *
   * @return Map of field names to objects.
   */
  fun getData(): Map<String, Any?> = userDataWriter.convertObject(fields)

  private fun extractNestedValue(fieldPath: FieldPath): Value? {
    val segments = fieldPath.internalPath.iterator()
    if (!segments.hasNext()) {
      return Values.encodeValue(fields)
    }
    val firstSegment = segments.next()
    if (!fields.containsKey(firstSegment)) {
      return null
    }
    var value: Value? = fields[firstSegment]
    for (segment in segments) {
      if (value == null || !value.hasMapValue()) {
        return null
      }
      value = value.mapValue.getFieldsOrDefault(segment, null)
    }
    return value
  }

  /**
   * Retrieves the field specified by [field].
   *
   * @param field The field path (e.g. "foo" or "foo.bar") to a specific field.
   * @return The data at the specified field location or null if no such field exists.
   */
  fun get(field: String): Any? = get(FieldPath.fromDotSeparatedPath(field))

  /**
   * Retrieves the field specified by [fieldPath].
   *
   * @param fieldPath The field path to a specific field.
   * @return The data at the specified field location or null if no such field exists.
   */
  fun get(fieldPath: FieldPath): Any? = userDataWriter.convertValue(extractNestedValue(fieldPath))

  override fun toString() = "PipelineResult{ref=$ref, updateTime=$updateTime}, data=${getData()}"
}

internal interface PipelineResultObserver {
  fun onDocument(
    key: DocumentKey?,
    data: Map<String, Value>,
    createTime: Timestamp?,
    updateTime: Timestamp?
  )
  fun onComplete(executionTime: Timestamp)
  fun onError(exception: FirebaseFirestoreException)
}
