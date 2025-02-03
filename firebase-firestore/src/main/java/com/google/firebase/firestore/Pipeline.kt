package com.google.firebase.firestore

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.common.collect.FluentIterable
import com.google.common.collect.ImmutableList
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.SnapshotVersion
import com.google.firebase.firestore.pipeline.CollectionGroupSource
import com.google.firebase.firestore.pipeline.CollectionSource
import com.google.firebase.firestore.pipeline.DatabaseSource
import com.google.firebase.firestore.pipeline.DocumentsSource
import com.google.firebase.firestore.pipeline.Stage
import com.google.firestore.v1.ExecutePipelineRequest
import com.google.firestore.v1.StructuredPipeline
import com.google.firestore.v1.Value

class Pipeline
internal constructor(
  internal val firestore: FirebaseFirestore,
  internal val stages: FluentIterable<Stage>
) {
  internal constructor(
    firestore: FirebaseFirestore,
    stage: Stage
  ) : this(firestore, FluentIterable.of(stage))

  private fun append(stage: Stage): Pipeline {
    return Pipeline(firestore, stages.append(stage))
  }

  fun execute(): Task<PipelineSnapshot> {
    val observerTask = ObserverSnapshotTask()
    execute(observerTask)
    return observerTask.task
  }

  private fun execute(observer: PipelineResultObserver) {
    firestore.callClient { call -> call!!.executePipeline(toProto(), observer) }
  }

  internal fun documentReference(key: DocumentKey): DocumentReference {
    return DocumentReference(key, firestore)
  }

  fun toProto(): ExecutePipelineRequest {
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

  private fun toPipelineProto(): com.google.firestore.v1.Pipeline =
    com.google.firestore.v1.Pipeline.newBuilder()
      .addAllStages(stages.map(Stage::toProtoStage))
      .build()

  private inner class ObserverSnapshotTask : PipelineResultObserver {
    private val taskCompletionSource = TaskCompletionSource<PipelineSnapshot>()
    private val results: ImmutableList.Builder<PipelineResult> = ImmutableList.builder()
    override fun onDocument(key: DocumentKey?, data: Map<String, Value>, version: SnapshotVersion) {
      results.add(
        PipelineResult(if (key == null) null else DocumentReference(key, firestore), data, version)
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

class PipelineSource(private val firestore: FirebaseFirestore) {
  fun collection(path: String): Pipeline {
    return Pipeline(firestore, CollectionSource(path))
  }

  fun collectionGroup(collectionId: String): Pipeline {
    return Pipeline(firestore, CollectionGroupSource(collectionId))
  }

  fun database(): Pipeline {
    return Pipeline(firestore, DatabaseSource())
  }

  fun documents(vararg documents: DocumentReference): Pipeline {
    return Pipeline(firestore, DocumentsSource(documents))
  }
}

class PipelineSnapshot
internal constructor(
  private val executionTime: SnapshotVersion,
  private val results: List<PipelineResult>
)

class PipelineResult
internal constructor(
  private val key: DocumentReference?,
  private val fields: Map<String, Value>,
  private val version: SnapshotVersion,
)

internal interface PipelineResultObserver {
  fun onDocument(key: DocumentKey?, data: Map<String, Value>, version: SnapshotVersion)
  fun onComplete(executionTime: SnapshotVersion)
  fun onError(exception: FirebaseFirestoreException)
}
