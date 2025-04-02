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
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.SnapshotVersion
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
import com.google.firebase.firestore.pipeline.FindNearestOptions
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
import com.google.firebase.firestore.pipeline.UnnestOptions
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
  private val stages: FluentIterable<Stage>
) {
  internal constructor(
    firestore: FirebaseFirestore,
    userDataReader: UserDataReader,
    stage: Stage
  ) : this(firestore, userDataReader, FluentIterable.of(stage))

  private fun append(stage: Stage): Pipeline {
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
    distanceMeasure: FindNearestStage.DistanceMeasure
  ) = append(FindNearestStage(property, vector, distanceMeasure, FindNearestOptions.DEFAULT))

  fun findNearest(
    property: Expr,
    vector: DoubleArray,
    distanceMeasure: FindNearestStage.DistanceMeasure,
    options: FindNearestOptions
  ) = append(FindNearestStage(property, vector, distanceMeasure, options))

  fun replace(field: String): Pipeline = replace(Field.of(field))

  fun replace(field: Selectable): Pipeline =
    append(ReplaceStage(field, ReplaceStage.Mode.FULL_REPLACE))

  fun sample(documents: Int): Pipeline = append(SampleStage.withDocLimit(documents))

  fun sample(sample: SampleStage): Pipeline = append(sample)

  fun union(other: Pipeline): Pipeline = append(UnionStage(other))

  fun unnest(field: String, alias: String): Pipeline = unnest(Field.of(field).alias(alias))

  fun unnest(field: String, alias: String, options: UnnestOptions): Pipeline =
    unnest(Field.of(field).alias(alias), options)

  fun unnest(selectable: Selectable): Pipeline = append(UnnestStage(selectable))

  fun unnest(selectable: Selectable, options: UnnestOptions): Pipeline =
    append(UnnestStage(selectable))

  private inner class ObserverSnapshotTask : PipelineResultObserver {
    private val userDataWriter =
      UserDataWriter(firestore, DocumentSnapshot.ServerTimestampBehavior.DEFAULT)
    private val taskCompletionSource = TaskCompletionSource<PipelineSnapshot>()
    private val results: ImmutableList.Builder<PipelineResult> = ImmutableList.builder()
    override fun onDocument(key: DocumentKey?, data: Map<String, Value>, version: SnapshotVersion) {
      results.add(
        PipelineResult(
          firestore,
          userDataWriter,
          if (key == null) null else DocumentReference(key, firestore),
          data,
          version
        )
      )
    }

    override fun onComplete(executionTime: SnapshotVersion) {
      taskCompletionSource.setResult(PipelineSnapshot(executionTime, results.build()))
    }

    override fun onError(exception: FirebaseFirestoreException) {
      taskCompletionSource.setException(exception)
    }

    val task: Task<PipelineSnapshot>
      get() = taskCompletionSource.task
  }
}

class PipelineSource internal constructor(private val firestore: FirebaseFirestore) {
  fun createFrom(query: Query): Pipeline {
    if (query.firestore.databaseId != firestore.databaseId) {
      throw IllegalArgumentException("Provided query is from a different Firestore instance.")
    }
    return query.query.toPipeline(firestore, firestore.userDataReader)
  }

  fun createFrom(query: AggregateQuery): Pipeline =
    createFrom(query.query)
      .aggregate(
        *query.aggregateFields.map(AggregateField::toPipeline).toTypedArray<AggregateWithAlias>()
      )

  fun collection(path: String): Pipeline =
    // Validate path by converting to CollectionReference
    collection(firestore.collection(path))

  fun collection(ref: CollectionReference): Pipeline {
    if (ref.firestore.databaseId != firestore.databaseId) {
      throw IllegalArgumentException(
        "Provided collection reference is from a different Firestore instance."
      )
    }
    return Pipeline(firestore, firestore.userDataReader, CollectionSource(ref.path))
  }

  fun collectionGroup(collectionId: String): Pipeline {
    Preconditions.checkNotNull(collectionId, "Provided collection ID must not be null.")
    require(!collectionId.contains("/")) {
      "Invalid collectionId '$collectionId'. Collection IDs must not contain '/'."
    }
    return Pipeline(firestore, firestore.userDataReader, CollectionGroupSource(collectionId))
  }

  fun database(): Pipeline = Pipeline(firestore, firestore.userDataReader, DatabaseSource())

  fun documents(vararg documents: String): Pipeline =
    // Validate document path by converting to DocumentReference
    documents(*documents.map(firestore::document).toTypedArray())

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

class PipelineSnapshot
internal constructor(
  private val executionTime: SnapshotVersion,
  val results: List<PipelineResult>
) : Iterable<PipelineResult> {
  override fun iterator() = results.iterator()
}

class PipelineResult
internal constructor(
  private val firestore: FirebaseFirestore,
  private val userDataWriter: UserDataWriter,
  val ref: DocumentReference?,
  private val fields: Map<String, Value>,
  private val version: SnapshotVersion,
) {

  /**
   * Returns the ID of the document represented by this result. Returns null if this result is not
   * corresponding to a Firestore document.
   */
  fun getId(): String? = ref?.id

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

  fun get(field: String): Any? = get(FieldPath.fromDotSeparatedPath(field))

  fun get(fieldPath: FieldPath): Any? = userDataWriter.convertValue(extractNestedValue(fieldPath))

  override fun toString() = "PipelineResult{ref=$ref, version=$version}, data=${getData()}"
}

internal interface PipelineResultObserver {
  fun onDocument(key: DocumentKey?, data: Map<String, Value>, version: SnapshotVersion)
  fun onComplete(executionTime: SnapshotVersion)
  fun onError(exception: FirebaseFirestoreException)
}
