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

import com.google.firebase.firestore.core.AsyncEventListener
import com.google.firebase.firestore.core.DocumentViewChange
import com.google.firebase.firestore.core.EventManager
import com.google.firebase.firestore.core.QueryListener
import com.google.firebase.firestore.core.QueryOrPipeline
import com.google.firebase.firestore.core.ViewSnapshot
import com.google.firebase.firestore.model.Document
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.ResourcePath
import com.google.firebase.firestore.pipeline.BooleanExpression
import com.google.firebase.firestore.pipeline.CollectionGroupOptions
import com.google.firebase.firestore.pipeline.CollectionGroupSource
import com.google.firebase.firestore.pipeline.CollectionSource
import com.google.firebase.firestore.pipeline.CollectionSourceOptions
import com.google.firebase.firestore.pipeline.EvaluationContext
import com.google.firebase.firestore.pipeline.Field
import com.google.firebase.firestore.pipeline.FunctionExpression
import com.google.firebase.firestore.pipeline.InternalOptions
import com.google.firebase.firestore.pipeline.LimitStage
import com.google.firebase.firestore.pipeline.OffsetStage
import com.google.firebase.firestore.pipeline.Ordering
import com.google.firebase.firestore.pipeline.SortStage
import com.google.firebase.firestore.pipeline.Stage
import com.google.firebase.firestore.pipeline.WhereStage
import com.google.firebase.firestore.remote.RemoteSerializer
import com.google.firebase.firestore.util.Assert
import com.google.firebase.firestore.util.Assert.fail
import com.google.firebase.firestore.util.Executors
import com.google.firestore.v1.StructuredPipeline
import java.util.concurrent.Executor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class RealtimePipelineSource internal constructor(private val firestore: FirebaseFirestore) {
  /**
   * Convert the given Query into an equivalent Pipeline.
   *
   * @param query A Query to be converted into a Pipeline.
   * @return A new [Pipeline] object that is equivalent to [query]
   * @throws [IllegalArgumentException] Thrown if the [query] provided targets a different project
   * or database than the pipeline.
   */
  fun convertFrom(query: Query): RealtimePipeline {
    if (query.firestore.databaseId != firestore.databaseId) {
      throw IllegalArgumentException("Provided query is from a different Firestore instance.")
    }
    return query.query.toRealtimePipeline(firestore, firestore.userDataReader)
  }

  /**
   * Set the pipeline's source to the collection specified by the given path.
   *
   * @param path A path to a collection that will be the source of this pipeline.
   * @return A new [RealtimePipeline] object with documents from target collection.
   */
  fun collection(path: String): RealtimePipeline = collection(firestore.collection(path))

  /**
   * Set the pipeline's source to the collection specified by the given [CollectionReference].
   *
   * @param ref A [CollectionReference] for a collection that will be the source of this pipeline.
   * @return A new [RealtimePipeline] object with documents from target collection.
   * @throws [IllegalArgumentException] Thrown if the [ref] provided targets a different project or
   * database than the pipeline.
   */
  fun collection(ref: CollectionReference): RealtimePipeline =
    collection(
      CollectionSource(
        ResourcePath.fromString(ref.path),
        RemoteSerializer(firestore.databaseId),
        CollectionSourceOptions()
      )
    )

  /**
   * Set the pipeline's source to the collection specified by CollectionSource.
   *
   * @param stage A [CollectionSource] that will be the source of this pipeline.
   * @return A new [RealtimePipeline] object with documents from target collection.
   * @throws [IllegalArgumentException] Thrown if the [stage] provided targets a different project
   * or database than the pipeline.
   */
  internal fun collection(stage: CollectionSource): RealtimePipeline {
    if (stage.serializer.databaseId() != firestore.databaseId) {
      throw IllegalArgumentException("Provided collection is from a different Firestore instance.")
    }
    return RealtimePipeline(
      firestore,
      RemoteSerializer(firestore.databaseId),
      firestore.userDataReader,
      stage
    )
  }

  /**
   * Set the pipeline's source to the collection group with the given id.
   *
   * @param collectionId The id of a collection group that will be the source of this pipeline.
   * @return A new [RealtimePipeline] object with documents from target collection group.
   */
  fun collectionGroup(collectionId: String): RealtimePipeline =
    collectionGroup(CollectionGroupSource(collectionId, CollectionGroupOptions()))

  internal fun collectionGroup(stage: CollectionGroupSource): RealtimePipeline =
    RealtimePipeline(
      firestore,
      RemoteSerializer(firestore.databaseId),
      firestore.userDataReader,
      stage
    )
}

class RealtimePipeline
internal constructor(
  // This is nullable because RealtimePipeline is also created from deserialization from persistent
  // cache. In that case, it is only used to facilitate remote store requests, and this field is
  // never used in that scenario.
  internal val firestore: FirebaseFirestore?,
  internal val serializer: RemoteSerializer,
  internal val userDataReader: UserDataReader,
  internal val stages: List<Stage<*>>,
  internal val internalOptions: EventManager.ListenOptions? = null
) {
  internal constructor(
    firestore: FirebaseFirestore,
    serializer: RemoteSerializer,
    userDataReader: UserDataReader,
    stage: Stage<*>
  ) : this(firestore, serializer, userDataReader, listOf(stage))

  private fun with(stages: List<Stage<*>>): RealtimePipeline =
    RealtimePipeline(firestore, serializer, userDataReader, stages)

  private fun append(stage: Stage<*>): RealtimePipeline = with(stages.plus(stage))

  /**
   * Limits the maximum number of documents returned by previous stages to `limit`.
   *
   * This stage is particularly useful when you want to retrieve a controlled subset of data from a
   * potentially large result set. It's often used for:
   * - **Pagination:** In combination with [where] to retrieve specific pages of results.
   * - **Limiting Data Retrieval:** To prevent excessive data transfer and improve performance,
   * especially when dealing with large collections.
   *
   * Example:
   * ```
   * // Limit the results to the top 10 highest-rated books
   * firestore.pipeline().collection("books")
   *     .sort(field("rating").descending())
   *     .limit(10);
   * ```
   *
   * @param limit The maximum number of documents to return.
   * @return A new [RealtimePipeline] object with this stage appended to the stage list.
   */
  fun limit(limit: Int): RealtimePipeline = append(LimitStage(limit))

  /**
   * Sorts the documents from previous stages based on one or more [Ordering] criteria.
   *
   * This stage allows you to order the results of your pipeline. You can specify multiple
   * [Ordering] instances to sort by multiple fields in ascending or descending order. If documents
   * have the same value for a field used for sorting, the next specified ordering will be used. If
   * all orderings result in equal comparison, the documents are considered equal and the order is
   * unspecified.
   *
   * Example:
   * ```
   * // Sort books by rating in descending order, and then by title in ascending order for books with the same rating
   * firestore.pipeline().collection("books")
   *     .sort(
   *         Ordering.of("rating").descending(),
   *         Ordering.of("title")  // Ascending order is the default
   *     );
   * ```
   *
   * @param order The first [Ordering] instance specifying the sorting criteria.
   * @param additionalOrders Optional additional [Ordering] instances specifying the sorting
   * criteria.
   * @return A new [RealtimePipeline] object with this stage appended to the stage list.
   */
  fun sort(order: Ordering, vararg additionalOrders: Ordering): RealtimePipeline =
    append(SortStage(arrayOf(order, *additionalOrders)))

  /**
   * Filters the documents from previous stages to only include those matching the specified
   * [BooleanExpression].
   *
   * This stage allows you to apply conditions to the data, similar to a "WHERE" clause in SQL.
   *
   * You can filter documents based on their field values, using implementations of
   * [BooleanExpression], typically including but not limited to:
   * - field comparators: [Expr.eq], [Expr.lt] (less than), [Expr.gt] (greater than), etc.
   * - logical operators: [Expr.and], [Expr.or], [Expr.not], etc.
   * - advanced functions: [Expr.arrayContains], [Expr.eqAny]etc.
   *
   * Example:
   * ```
   * firestore.pipeline().collection("books")
   *   .where(
   *     and(
   *         gt("rating", 4.0),   // Filter for ratings greater than 4.0
   *         field("genre").eq("Science Fiction") // Equivalent to eq("genre", "Science Fiction")
   *     )
   *   );
   * ```
   *
   * @param condition The [BooleanExpression] to apply.
   * @return A new [RealtimePipeline] object with this stage appended to the stage list.
   */
  fun where(condition: BooleanExpression): RealtimePipeline = append(WhereStage(condition))

  /**
   * Starts listening to this pipeline and emits a [RealtimePipelineSnapshot] every time the results
   * change.
   *
   * @return A [Flow] of [RealtimePipelineSnapshot] that emits new snapshots on every change.
   */
  fun snapshots(): Flow<RealtimePipelineSnapshot> = snapshots(RealtimePipelineOptions.DEFAULT)

  /**
   * Starts listening to this pipeline and emits a [RealtimePipelineSnapshot] every time the results
   * change.
   *
   * @param options The [RealtimePipelineOptions] to use for this listen.
   * @return A [Flow] of [RealtimePipelineSnapshot] that emits new snapshots on every change.
   */
  fun snapshots(options: RealtimePipelineOptions): Flow<RealtimePipelineSnapshot> = callbackFlow {
    val listener =
      addSnapshotListener(options) { snapshot, error ->
        if (snapshot != null) {
          trySend(snapshot)
        } else {
          close(error)
        }
      }
    awaitClose { listener.remove() }
  }

  /**
   * Starts listening to this pipeline using an [EventListener].
   *
   * @param listener The event listener to receive the results.
   * @return A [ListenerRegistration] that can be used to stop listening.
   */
  fun addSnapshotListener(listener: EventListener<RealtimePipelineSnapshot>): ListenerRegistration =
    addSnapshotListener(RealtimePipelineOptions.DEFAULT, listener)

  /**
   * Starts listening to this pipeline using an [EventListener].
   *
   * @param options The [RealtimePipelineOptions] to use for this listen.
   * @param listener The event listener to receive the results.
   * @return A [ListenerRegistration] that can be used to stop listening.
   */
  fun addSnapshotListener(
    options: RealtimePipelineOptions,
    listener: EventListener<RealtimePipelineSnapshot>
  ): ListenerRegistration =
    addSnapshotListener(Executors.DEFAULT_CALLBACK_EXECUTOR, options, listener)

  /**
   * Starts listening to this pipeline using an [EventListener].
   *
   * @param executor The executor to use for the listener.
   * @param listener The event listener to receive the results.
   * @return A [ListenerRegistration] that can be used to stop listening.
   */
  fun addSnapshotListener(
    executor: Executor,
    listener: EventListener<RealtimePipelineSnapshot>
  ): ListenerRegistration = addSnapshotListener(executor, RealtimePipelineOptions.DEFAULT, listener)

  /**
   * Starts listening to this pipeline using an [EventListener].
   *
   * @param executor The executor to use for the listener.
   * @param options The [RealtimePipelineOptions] to use for this listen.
   * @param listener The event listener to receive the results.
   * @return A [ListenerRegistration] that can be used to stop listening.
   */
  fun addSnapshotListener(
    executor: Executor,
    options: RealtimePipelineOptions,
    listener: EventListener<RealtimePipelineSnapshot>
  ): ListenerRegistration {
    val userListener =
      EventListener<ViewSnapshot> { snapshot, error ->
        val realtimeSnapshot = snapshot?.let { RealtimePipelineSnapshot(it, firestore!!, options) }
        listener.onEvent(realtimeSnapshot, error)
      }

    val asyncListener = AsyncEventListener(executor, userListener)

    return firestore!!.callClient { client ->
      val listener: QueryListener =
        client!!.listen(
          QueryOrPipeline.PipelineWrapper(this),
          options.toListenOptions(),
          asyncListener
        )
      ListenerRegistration {
        asyncListener.mute()
        client!!.stopListening(listener)
      }
    }
  }

  internal fun withListenOptions(options: EventManager.ListenOptions): RealtimePipeline =
    RealtimePipeline(firestore, serializer, userDataReader, stages, options)

  internal val rewrittenStages: List<Stage<*>> by lazy {
    var hasOrder = false
    buildList {
      for (stage in stages) when (stage) {
        // Stages whose semantics depend on ordering
        is LimitStage,
        is OffsetStage -> {
          if (!hasOrder) {
            hasOrder = true
            add(SortStage.BY_DOCUMENT_ID)
          }
          add(stage)
        }
        is SortStage -> {
          hasOrder = true
          add(stage.withStableOrdering())
        }
        else -> add(stage)
      }
      if (!hasOrder) {
        add(SortStage.BY_DOCUMENT_ID)
      }
    }
  }

  internal fun canonicalId(): String {
    return rewrittenStages.joinToString("|") { stage -> stage.canonicalId() }
  }

  override fun toString(): String = canonicalId()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RealtimePipeline) return false
    if (serializer.databaseId() != other.serializer.databaseId()) return false
    return rewrittenStages == other.rewrittenStages
  }

  override fun hashCode(): Int {
    return serializer.databaseId().hashCode() * 31 + stages.hashCode()
  }

  internal fun evaluate(inputs: List<MutableDocument>): List<MutableDocument> {
    val context = EvaluationContext(this)
    return rewrittenStages.fold(inputs) { documents, stage -> stage.evaluate(context, documents) }
  }

  internal fun matchesAllDocuments(): Boolean {
    for (stage in rewrittenStages) {
      // Check for LimitStage
      if (stage.name == "limit") {
        return false
      }

      // Check for Where stage
      if (stage is WhereStage) {
        // Check if it's the special 'exists(__name__)' case
        val funcExpr = stage.condition as? FunctionExpression
        if (funcExpr?.name == "exists" && funcExpr.params.size == 1) {
          val fieldExpr = funcExpr.params[0] as? Field
          if (fieldExpr?.fieldPath?.isKeyField == true) {
            continue // This specific 'exists(__name__)' filter doesn't count
          }
        }
        return false
      }
      // TODO(pipeline) : Add checks for other filtering stages like Aggregate,
      // Distinct, FindNearest once they are implemented.
    }
    return true
  }

  internal fun hasLimit(): Boolean {
    for (stage in rewrittenStages) {
      if (stage.name == "limit") {
        return true
      }
      // TODO(pipeline): need to check for other stages that could have a limit,
      // like findNearest
    }
    return false
  }

  internal fun matches(doc: Document): Boolean {
    val result = evaluate(listOf(doc as MutableDocument))
    return result.isNotEmpty()
  }

  private fun evaluateContext(): EvaluationContext {
    return EvaluationContext(this)
  }

  internal fun comparator(): Comparator<Document> =
    getLastEffectiveSortStage().comparator(evaluateContext())

  internal fun toStructurePipelineProto(): StructuredPipeline {
    val builder = StructuredPipeline.newBuilder()
    builder.pipeline =
      com.google.firestore.v1.Pipeline.newBuilder()
        .addAllStages(rewrittenStages.map { it.toProtoStage(userDataReader) })
        .build()
    return builder.build()
  }

  private fun getLastEffectiveSortStage(): SortStage {
    for (stage in rewrittenStages.asReversed()) {
      if (stage is SortStage) {
        return stage
      }
      // TODO(pipeline): Consider stages that might invalidate ordering later,
      // like fineNearest
    }
    throw fail("RealtimePipeline must contain at least one Sort stage (ensured by RewriteStages).")
  }
}

/**
 * An options object that configures the behavior of `snapshots()` calls. By default, `snapshots()`
 * attempts to provide up-to-date data when possible, but falls back to cached data if the device is
 * offline and the server cannot be reached.
 */
class RealtimePipelineOptions
private constructor(
  internal val source: ListenSource,
  internal val serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior,
  internal val metadataChanges: MetadataChanges,
  options: InternalOptions
) {

  constructor() :
    this(
      ListenSource.DEFAULT,
      DocumentSnapshot.ServerTimestampBehavior.NONE,
      MetadataChanges.EXCLUDE,
      InternalOptions.EMPTY
    )

  companion object {
    /** A `RealtimePipelineOptions` object with default options. */
    @JvmField
    val DEFAULT: RealtimePipelineOptions =
      RealtimePipelineOptions(
        ListenSource.DEFAULT,
        DocumentSnapshot.ServerTimestampBehavior.NONE,
        MetadataChanges.EXCLUDE,
        InternalOptions.EMPTY
      )
  }

  /**
   * Returns a new `RealtimePipelineOptions` object with the specified `ListenSource`.
   *
   * @param source The `ListenSource` to use.
   * @return A new `RealtimePipelineOptions` object.
   */
  fun withSource(source: ListenSource): RealtimePipelineOptions {
    return RealtimePipelineOptions(
      source,
      serverTimestampBehavior,
      metadataChanges,
      InternalOptions.EMPTY
    )
  }

  /**
   * Returns a new `RealtimePipelineOptions` object with the specified `ServerTimestampBehavior`.
   *
   * @param serverTimestampBehavior The `ServerTimestampBehavior` to use.
   * @return A new `RealtimePipelineOptions` object.
   */
  fun withServerTimestampBehavior(
    serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior
  ): RealtimePipelineOptions {
    return RealtimePipelineOptions(
      source,
      serverTimestampBehavior,
      metadataChanges,
      InternalOptions.EMPTY
    )
  }

  /**
   * Returns a new `RealtimePipelineOptions` object with the specified `MetadataChanges` option.
   *
   * @param metadataChanges The `MetadataChanges` option to use.
   * @return A new `RealtimePipelineOptions` object.
   */
  fun withMetadataChanges(metadataChanges: MetadataChanges): RealtimePipelineOptions {
    return RealtimePipelineOptions(
      source,
      serverTimestampBehavior,
      metadataChanges,
      InternalOptions.EMPTY
    )
  }

  internal fun toListenOptions(): EventManager.ListenOptions {
    val result = EventManager.ListenOptions()
    result.source = source
    result.includeQueryMetadataChanges = metadataChanges == MetadataChanges.INCLUDE
    result.includeDocumentMetadataChanges = metadataChanges == MetadataChanges.INCLUDE
    result.waitForSyncWhenOnline = false
    result.serverTimestampBehavior = serverTimestampBehavior
    return result
  }
}

/**
 * A `RealtimePipelineSnapshot` contains the results of a realtime pipeline listen. It can be used
 * to retrieve the full list of results, or the incremental changes since the last snapshot.
 */
class RealtimePipelineSnapshot
internal constructor(
  private val viewSnapshot: ViewSnapshot,
  private val firestore: FirebaseFirestore,
  private val options: RealtimePipelineOptions
) {
  /** Returns the metadata for this snapshot. */
  val metadata: PipelineSnapshotMetadata
    get() = PipelineSnapshotMetadata(viewSnapshot.hasPendingWrites(), !viewSnapshot.isFromCache)

  /** Returns the results of the pipeline for this snapshot. */
  val results: List<PipelineResult>
    get() =
      viewSnapshot.documents.map { PipelineResult(it, options.serverTimestampBehavior, firestore) }

  /**
   * Returns the incremental changes since the last snapshot.
   *
   * @param metadataChanges Whether to include metadata-only changes.
   * @return A list of [PipelineResultChange] objects.
   */
  fun getChanges(metadataChanges: MetadataChanges? = null): List<PipelineResultChange> =
    changesFromSnapshot(metadataChanges ?: MetadataChanges.EXCLUDE, viewSnapshot) {
      doc,
      type,
      oldIndex,
      newIndex ->
      PipelineResultChange(
        firestore,
        doc,
        options.serverTimestampBehavior,
        type,
        oldIndex,
        newIndex
      )
    }
}

/**
 * Metadata about a [RealtimePipelineSnapshot], including information about the source of the data
 * and whether the snapshot has pending writes.
 *
 * @property hasPendingWrites True if the snapshot contains results that have not yet been written
 * to the backend.
 * @property isConsistentBetweenListeners True if the snapshot is guaranteed to be consistent with
 * other active listeners on the same Firestore instance.
 */
data class PipelineSnapshotMetadata
internal constructor(val hasPendingWrites: Boolean, val isConsistentBetweenListeners: Boolean)

/**
 * A `PipelineResultChange` represents a change to a single result in a `RealtimePipelineSnapshot`.
 *
 * @property result The `PipelineResult` that changed.
 * @property type The type of change.
 * @property oldIndex The index of the result in the previous snapshot, or -1 if it's a new result.
 * @property newIndex The index of the result in the new snapshot, or -1 if it was removed.
 */
data class PipelineResultChange
internal constructor(
  val result: PipelineResult,
  val type: ChangeType,
  val oldIndex: Int?,
  val newIndex: Int?
) {
  /** An enumeration of the different types of changes that can occur. */
  enum class ChangeType {
    ADDED,
    MODIFIED,
    REMOVED
  }

  internal constructor(
    firestore: FirebaseFirestore,
    doc: Document,
    serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior,
    type: DocumentChange.Type,
    oldIndex: Int,
    newIndex: Int
  ) : this(
    PipelineResult(doc, serverTimestampBehavior, firestore),
    getChangeType(type),
    oldIndex,
    newIndex
  )

  companion object {
    private fun getChangeType(type: DocumentChange.Type): ChangeType =
      when (type) {
        DocumentChange.Type.ADDED -> ChangeType.ADDED
        DocumentChange.Type.MODIFIED -> ChangeType.MODIFIED
        DocumentChange.Type.REMOVED -> ChangeType.REMOVED
      }
  }
}

/** Creates the list of document changes from a `ViewSnapshot`. */
internal fun <T> changesFromSnapshot(
  metadataChanges: MetadataChanges,
  snapshot: ViewSnapshot,
  fromDocument: (Document, DocumentChange.Type, Int, Int) -> T
): List<T> {
  val documentChanges: MutableList<T> = ArrayList()
  if (snapshot.getOldDocuments().isEmpty()) {
    // Special case the first snapshot because index calculation is easy and fast. Also all
    // changes on the first snapshot are adds so there are also no metadata-only changes to filter
    // out.
    var index = 0
    var lastDoc: Document? = null
    for (change in snapshot.getChanges()) {
      val document = change.getDocument()
      Assert.hardAssert(
        change.getType() == DocumentViewChange.Type.ADDED,
        "Invalid added event for first snapshot"
      )
      Assert.hardAssert(
        lastDoc == null || snapshot.getQuery().comparator().compare(lastDoc, document) < 0,
        "Got added events in wrong order"
      )

      documentChanges.add(fromDocument(document, DocumentChange.Type.ADDED, -1, index++))
      lastDoc = document
    }
  } else {
    // A DocumentSet that is updated incrementally as changes are applied to use to lookup the
    // index of a document.
    var indexTracker = snapshot.getOldDocuments()
    for (change in snapshot.getChanges()) {
      if (
        metadataChanges == MetadataChanges.EXCLUDE &&
          change.getType() == DocumentViewChange.Type.METADATA
      ) {
        continue
      }
      val document = change.getDocument()
      val oldIndex: Int
      val newIndex: Int
      val type = getType(change)
      if (type != DocumentChange.Type.ADDED) {
        oldIndex = indexTracker.indexOf(document.getKey())
        Assert.hardAssert(oldIndex >= 0, "Index for document not found")
        indexTracker = indexTracker.remove(document.getKey())
      } else {
        oldIndex = -1
      }
      if (type != DocumentChange.Type.REMOVED) {
        indexTracker = indexTracker.add(document)
        newIndex = indexTracker.indexOf(document.getKey())
        Assert.hardAssert(newIndex >= 0, "Index for document not found")
      } else {
        newIndex = -1
      }

      documentChanges.add(fromDocument(document, type, oldIndex, newIndex))
    }
  }
  return documentChanges
}

private fun getType(change: DocumentViewChange): DocumentChange.Type {
  when (change.getType()) {
    DocumentViewChange.Type.ADDED -> return DocumentChange.Type.ADDED
    DocumentViewChange.Type.METADATA,
    DocumentViewChange.Type.MODIFIED -> return DocumentChange.Type.MODIFIED
    DocumentViewChange.Type.REMOVED -> return DocumentChange.Type.REMOVED
    else ->
      throw java.lang.IllegalArgumentException("Unknown view change type: " + change.getType())
  }
}
