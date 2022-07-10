// Copyright 2018 Google LLC
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

import android.app.Activity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.core.ActivityScope
import com.google.firebase.firestore.core.AsyncEventListener
import com.google.firebase.firestore.core.EventManager.ListenOptions
import com.google.firebase.firestore.core.ListenerRegistrationImpl
import com.google.firebase.firestore.core.Query
import com.google.firebase.firestore.core.UserData.ParsedUpdateData
import com.google.firebase.firestore.core.ViewSnapshot
import com.google.firebase.firestore.encoding.JavaDocumentReferenceSerializer
import com.google.firebase.firestore.model.Document
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.ResourcePath
import com.google.firebase.firestore.model.mutation.DeleteMutation
import com.google.firebase.firestore.model.mutation.Precondition
import com.google.firebase.firestore.util.Assert
import com.google.firebase.firestore.util.Executors
import com.google.firebase.firestore.util.Preconditions
import com.google.firebase.firestore.util.Util
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlinx.serialization.Serializable

/**
 * A `DocumentReference` refers to a document location in a Cloud Firestore database and can be used
 * to write, read, or listen to the location. There may or may not exist a document at the
 * referenced location. A `DocumentReference` can also be used to create a [ ] to a subcollection.
 *
 * **Subclassing Note**: Cloud Firestore classes are not meant to be subclassed except for use in
 * test mocks. Subclassing is not supported in production code and new SDK releases may break code
 * that does so.
 */
@Serializable(with = JavaDocumentReferenceSerializer::class)
class JavaDocumentReference constructor(val key: DocumentKey, val firestore: FirebaseFirestore) {
    //    val key: DocumentKey
    /** Gets the Cloud Firestore instance associated with this document reference. */
    //    val firestore: FirebaseFirestore
    val id: String
        get() = key.documentId

    /**
     * Gets a `CollectionReference` to the collection that contains this document.
     *
     * @return The `CollectionReference` that contains this document.
     */
    val parent: CollectionReference
        get() = CollectionReference(key.collectionPath, firestore)

    /**
     * Gets the path of this document (relative to the root of the database) as a slash-separated
     * string.
     *
     * @return The path of this document.
     */
    val path: String
        get() = key.path.canonicalString()

    /**
     * Gets a `CollectionReference` instance that refers to the subcollection at the specified path
     * relative to this document.
     *
     * @param collectionPath A slash-separated relative path to a subcollection.
     * @return The `CollectionReference` instance.
     */
    fun collection(collectionPath: String): CollectionReference {
        Preconditions.checkNotNull(collectionPath, "Provided collection path must not be null.")
        return CollectionReference(
            key.path.append(ResourcePath.fromString(collectionPath)),
            firestore
        )
    }
    /**
     * Writes to the document referred to by this `DocumentReference`. If the document does not yet
     * exist, it will be created. If you pass `SetOptions`, the provided data can be merged into an
     * existing document.
     *
     * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
     * document contents).
     * @param options An object to configure the set behavior.
     * @return A Task that will be resolved when the write finishes.
     */
    /**
     * Overwrites the document referred to by this `DocumentReference`. If the document does not yet
     * exist, it will be created. If a document already exists, it will be overwritten.
     *
     * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
     * document contents).
     * @return A Task that will be resolved when the write finishes.
     */
    @JvmOverloads
    fun set(data: Any, options: SetOptions = SetOptions.OVERWRITE): Task<Void> {
        var data = data
        Preconditions.checkNotNull(data, "Provided data must not be null.")
        Preconditions.checkNotNull(options, "Provided options must not be null.")
        // TODO: Support other encoders in the future.
        val availableEncoders = firestore.mapEncoders
        for (encoder in availableEncoders) {
            if (encoder.supports(data.javaClass)) {
                data = encoder.encode(data)
            }
        }
        return setParsedData(data, options)
    }

    private fun setParsedData(data: Any, options: SetOptions): Task<Void> {
        val parsed =
            if (options.isMerge) firestore.userDataReader.parseMergeData(data, options.fieldMask)
            else firestore.userDataReader.parseSetData(data)
        return firestore.client
            .write(listOf(parsed.toMutation(key, Precondition.NONE)))
            .continueWith(Executors.DIRECT_EXECUTOR, Util.voidErrorTransformer())
    }

    /**
     * Updates fields in the document referred to by this `DocumentReference`. If no document exists
     * yet, the update will fail.
     *
     * @param data A map of field / value pairs to update. Fields can contain dots to reference
     * nested fields within the document.
     * @return A Task that will be resolved when the write finishes.
     */
    fun update(data: Map<String?, Any?>): Task<Void> {
        val parsedData = firestore.userDataReader.parseUpdateData(data)
        return update(parsedData)
    }

    /**
     * Updates fields in the document referred to by this `DocumentReference`. If no document exists
     * yet, the update will fail.
     *
     * @param field The first field to update. Fields can contain dots to reference a nested field
     * within the document.
     * @param value The first value
     * @param moreFieldsAndValues Additional field/value pairs.
     * @return A Task that will be resolved when the write finishes.
     */
    fun update(field: String, value: Any?, vararg moreFieldsAndValues: Any?): Task<Void> {
        val parsedData =
            firestore.userDataReader.parseUpdateData(
                Util.collectUpdateArguments(
                    /* fieldPathOffset= */ 1,
                    field,
                    value,
                    *moreFieldsAndValues
                )
            )
        return update(parsedData)
    }

    /**
     * Updates fields in the document referred to by this `DocumentReference`. If no document exists
     * yet, the update will fail.
     *
     * @param fieldPath The first field to update.
     * @param value The first value
     * @param moreFieldsAndValues Additional field/value pairs.
     * @return A Task that will be resolved when the write finishes.
     */
    fun update(fieldPath: FieldPath, value: Any?, vararg moreFieldsAndValues: Any?): Task<Void> {
        val parsedData =
            firestore.userDataReader.parseUpdateData(
                Util.collectUpdateArguments(
                    /* fieldPathOffset= */ 1,
                    fieldPath,
                    value,
                    *moreFieldsAndValues
                )
            )
        return update(parsedData)
    }

    private fun update(parsedData: ParsedUpdateData): Task<Void> {
        return firestore.client
            .write(listOf(parsedData.toMutation(key, Precondition.exists(true))))
            .continueWith(Executors.DIRECT_EXECUTOR, Util.voidErrorTransformer())
    }

    /**
     * Deletes the document referred to by this `DocumentReference`.
     *
     * @return A Task that will be resolved when the delete completes.
     */
    fun delete(): Task<Void> {
        return firestore.client
            .write(listOf(DeleteMutation(key, Precondition.NONE)))
            .continueWith(Executors.DIRECT_EXECUTOR, Util.voidErrorTransformer())
    }
    /**
     * Reads the document referenced by this `DocumentReference`.
     *
     * By default, `get()` attempts to provide up-to-date data when possible by waiting for data
     * from the server, but it may return cached data or fail if you are offline and the server
     * cannot be reached. This behavior can be altered via the `Source` parameter.
     *
     * @param source A value to configure the get behavior.
     * @return A Task that will be resolved with the contents of the Document at this
     * `DocumentReference`.
     */
    /**
     * Reads the document referenced by this `DocumentReference`.
     *
     * @return A Task that will be resolved with the contents of the Document at this
     * `DocumentReference`.
     */
    @JvmOverloads
    operator fun get(source: Source = Source.DEFAULT): Task<DocumentSnapshot> {
        return if (source == Source.CACHE) {
            firestore.client.getDocumentFromLocalCache(key).continueWith(
                Executors.DIRECT_EXECUTOR
            ) { task: Task<Document?> ->
                val doc = task.result
                val hasPendingWrites = doc != null && doc.hasLocalMutations()
                DocumentSnapshot(firestore, key, doc, /*isFromCache=*/ true, hasPendingWrites)
            }
        } else {
            getViaSnapshotListener(source)
        }
    }

    private fun getViaSnapshotListener(source: Source): Task<DocumentSnapshot> {
        val res = TaskCompletionSource<DocumentSnapshot>()
        val registration = TaskCompletionSource<ListenerRegistration>()
        val options = ListenOptions()
        options.includeDocumentMetadataChanges = true
        options.includeQueryMetadataChanges = true
        options.waitForSyncWhenOnline = true
        val listenerRegistration =
            addSnapshotListenerInternal( // No need to schedule, we just set the task result
                // directly
                Executors.DIRECT_EXECUTOR,
                options,
                null
            ) { snapshot: DocumentSnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    res.setException(error)
                    return@addSnapshotListenerInternal
                }
                try {
                    val actualRegistration = Tasks.await(registration.task)

                    // Remove query first before passing event to user to avoid user actions
                    // affecting
                    // the now stale query.
                    actualRegistration.remove()
                    if (!snapshot!!.exists() && snapshot.metadata.isFromCache) {
                        // TODO: Reconsider how to raise missing documents when offline.
                        // If we're online and the document doesn't exist then we set the result
                        // of the Task with a document with document.exists set to false. If we're
                        // offline however, we set the Exception on the Task. Two options:
                        //
                        // 1)  Cache the negative response from the server so we can deliver that
                        //     even when you're offline.
                        // 2)  Actually set the Exception of the Task if the document doesn't
                        //     exist when you are offline.
                        res.setException(
                            FirebaseFirestoreException(
                                "Failed to get document because the client is offline.",
                                FirebaseFirestoreException.Code.UNAVAILABLE
                            )
                        )
                    } else if (
                        snapshot.exists() &&
                            snapshot.metadata.isFromCache &&
                            source == Source.SERVER
                    ) {
                        res.setException(
                            FirebaseFirestoreException(
                                "Failed to get document from server. (However, this document does exist " +
                                    "in the local cache. Run again without setting source to SERVER to " +
                                    "retrieve the cached document.)",
                                FirebaseFirestoreException.Code.UNAVAILABLE
                            )
                        )
                    } else {
                        res.setResult(snapshot)
                    }
                } catch (e: ExecutionException) {
                    throw Assert.fail(e, "Failed to register a listener for a single document")
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw Assert.fail(e, "Failed to register a listener for a single document")
                }
            }
        registration.setResult(listenerRegistration)
        return res.task
    }

    /**
     * Starts listening to the document referenced by this `DocumentReference`.
     *
     * @param listener The event listener that will be called with the snapshots.
     * @return A registration object that can be used to remove the listener.
     */
    fun addSnapshotListener(listener: EventListener<DocumentSnapshot>): ListenerRegistration {
        return addSnapshotListener(MetadataChanges.EXCLUDE, listener)
    }

    /**
     * Starts listening to the document referenced by this `DocumentReference`.
     *
     * @param executor The executor to use to call the listener.
     * @param listener The event listener that will be called with the snapshots.
     * @return A registration object that can be used to remove the listener.
     */
    fun addSnapshotListener(
        executor: Executor,
        listener: EventListener<DocumentSnapshot>
    ): ListenerRegistration {
        return addSnapshotListener(executor, MetadataChanges.EXCLUDE, listener)
    }

    /**
     * Starts listening to the document referenced by this `DocumentReference` using an
     * Activity-scoped listener.
     *
     * The listener will be automatically removed during [Activity.onStop].
     *
     * @param activity The activity to scope the listener to.
     * @param listener The event listener that will be called with the snapshots.
     * @return A registration object that can be used to remove the listener.
     */
    fun addSnapshotListener(
        activity: Activity,
        listener: EventListener<DocumentSnapshot>
    ): ListenerRegistration {
        return addSnapshotListener(activity, MetadataChanges.EXCLUDE, listener)
    }

    /**
     * Starts listening to the document referenced by this `DocumentReference` with the given
     * options.
     *
     * @param metadataChanges Indicates whether metadata-only changes (i.e. only
     * `DocumentSnapshot.getMetadata()` changed) should trigger snapshot events.
     * @param listener The event listener that will be called with the snapshots.
     * @return A registration object that can be used to remove the listener.
     */
    fun addSnapshotListener(
        metadataChanges: MetadataChanges,
        listener: EventListener<DocumentSnapshot>
    ): ListenerRegistration {
        return addSnapshotListener(Executors.DEFAULT_CALLBACK_EXECUTOR, metadataChanges, listener)
    }

    /**
     * Starts listening to the document referenced by this `DocumentReference` with the given
     * options.
     *
     * @param executor The executor to use to call the listener.
     * @param metadataChanges Indicates whether metadata-only changes (i.e. only
     * `DocumentSnapshot.getMetadata()` changed) should trigger snapshot events.
     * @param listener The event listener that will be called with the snapshots.
     * @return A registration object that can be used to remove the listener.
     */
    fun addSnapshotListener(
        executor: Executor,
        metadataChanges: MetadataChanges,
        listener: EventListener<DocumentSnapshot>
    ): ListenerRegistration {
        Preconditions.checkNotNull(executor, "Provided executor must not be null.")
        Preconditions.checkNotNull(
            metadataChanges,
            "Provided MetadataChanges value must not be null."
        )
        Preconditions.checkNotNull(listener, "Provided EventListener must not be null.")
        return addSnapshotListenerInternal(
            executor,
            internalOptions(metadataChanges),
            null,
            listener
        )
    }

    /**
     * Starts listening to the document referenced by this `DocumentReference` with the given
     * options using an Activity-scoped listener.
     *
     * The listener will be automatically removed during [Activity.onStop].
     *
     * @param activity The activity to scope the listener to.
     * @param metadataChanges Indicates whether metadata-only changes (i.e. only
     * `DocumentSnapshot.getMetadata()` changed) should trigger snapshot events.
     * @param listener The event listener that will be called with the snapshots.
     * @return A registration object that can be used to remove the listener.
     */
    fun addSnapshotListener(
        activity: Activity,
        metadataChanges: MetadataChanges,
        listener: EventListener<DocumentSnapshot>
    ): ListenerRegistration {
        Preconditions.checkNotNull(activity, "Provided activity must not be null.")
        Preconditions.checkNotNull(
            metadataChanges,
            "Provided MetadataChanges value must not be null."
        )
        Preconditions.checkNotNull(listener, "Provided EventListener must not be null.")
        return addSnapshotListenerInternal(
            Executors.DEFAULT_CALLBACK_EXECUTOR,
            internalOptions(metadataChanges),
            activity,
            listener
        )
    }

    /**
     * Internal helper method to create add a snapshot listener.
     *
     * Will be Activity scoped if the activity parameter is non-`null`.
     *
     * @param userExecutor The executor to use to call the listener.
     * @param options The options to use for this listen.
     * @param activity Optional activity this listener is scoped to.
     * @param userListener The user-supplied event listener that will be called with document
     * snapshots.
     * @return A registration object that can be used to remove the listener.
     */
    private fun addSnapshotListenerInternal(
        userExecutor: Executor,
        options: ListenOptions,
        activity: Activity?,
        userListener: EventListener<DocumentSnapshot>
    ): ListenerRegistration {

        // Convert from ViewSnapshots to DocumentSnapshots.
        val viewListener =
            label@ EventListener { snapshot: ViewSnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    userListener.onEvent(null, error)
                    // return@lable ???
                }
                Assert.hardAssert(snapshot != null, "Got event without value or error set")
                Assert.hardAssert(
                    snapshot!!.documents.size() <= 1,
                    "Too many documents returned on a document query"
                )
                val document = snapshot.documents.getDocument(key)
                val documentSnapshot: DocumentSnapshot
                documentSnapshot =
                    if (document != null) {
                        val hasPendingWrites = snapshot.mutatedKeys.contains(document.key)
                        DocumentSnapshot.fromDocument(
                            firestore,
                            document,
                            snapshot.isFromCache,
                            hasPendingWrites
                        )
                    } else {
                        // We don't raise `hasPendingWrites` for deleted documents.
                        DocumentSnapshot.fromNoDocument(firestore, key, snapshot.isFromCache)
                    }
                userListener.onEvent(documentSnapshot, null)
            }

        // Call the viewListener on the userExecutor.
        val asyncListener = AsyncEventListener(userExecutor, viewListener)
        val query = asQuery()
        val queryListener = firestore.client.listen(query, options, asyncListener)
        return ActivityScope.bind(
            activity,
            ListenerRegistrationImpl(firestore.client, queryListener, asyncListener)
        )
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is JavaDocumentReference) {
            return false
        }
        val that = o
        return key == that.key && firestore == that.firestore
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + firestore.hashCode()
        return result
    }

    private fun asQuery(): Query {
        return Query.atPath(key.path)
    }

    companion object {
        /** @hide */
        fun forPath(path: ResourcePath, firestore: FirebaseFirestore): JavaDocumentReference {
            require(path.length() % 2 == 0) {
                ("Invalid document reference. Document references must have an even number " +
                    "of segments, but " +
                    path.canonicalString() +
                    " has " +
                    path.length())
            }
            return JavaDocumentReference(DocumentKey.fromPath(path), firestore)
        }

        /** Converts the public API MetadataChanges object to the internal options object. */
        private fun internalOptions(metadataChanges: MetadataChanges): ListenOptions {
            val internalOptions = ListenOptions()
            internalOptions.includeDocumentMetadataChanges =
                metadataChanges == MetadataChanges.INCLUDE
            internalOptions.includeQueryMetadataChanges = metadataChanges == MetadataChanges.INCLUDE
            internalOptions.waitForSyncWhenOnline = false
            return internalOptions
        }
    }

    init {
        Preconditions.checkNotNull(key)
        // TODO: We should checkNotNull(firestore), but tests are currently cheating
        // and setting it to null.
//        firestore
    }
}
