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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Preconditions.checkNotNull;
import static com.google.firebase.firestore.util.Util.voidErrorTransformer;
import static java.util.Collections.singletonList;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.core.ActivityScope;
import com.google.firebase.firestore.core.AsyncEventListener;
import com.google.firebase.firestore.core.EventManager.ListenOptions;
import com.google.firebase.firestore.core.ListenerRegistrationImpl;
import com.google.firebase.firestore.core.QueryListener;
import com.google.firebase.firestore.core.UserData.ParsedSetData;
import com.google.firebase.firestore.core.UserData.ParsedUpdateData;
import com.google.firebase.firestore.core.ViewSnapshot;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.util.Assert;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Util;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * A {@code DocumentReference} refers to a document location in a Cloud Firestore database and can
 * be used to write, read, or listen to the location. There may or may not exist a document at the
 * referenced location. A {@code DocumentReference} can also be used to create a {@link
 * CollectionReference} to a subcollection.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class DocumentReference {

  private final DocumentKey key;

  private final FirebaseFirestore firestore;

  DocumentReference(DocumentKey key, FirebaseFirestore firestore) {
    this.key = checkNotNull(key);
    // TODO: We should checkNotNull(firestore), but tests are currently cheating
    // and setting it to null.
    this.firestore = firestore;
  }

  /** @hide */
  static DocumentReference forPath(ResourcePath path, FirebaseFirestore firestore) {
    if (path.length() % 2 != 0) {
      throw new IllegalArgumentException(
          "Invalid document reference. Document references must have an even number "
              + "of segments, but "
              + path.canonicalString()
              + " has "
              + path.length());
    }

    return new DocumentReference(DocumentKey.fromPath(path), firestore);
  }

  DocumentKey getKey() {
    return key;
  }

  /** Gets the Cloud Firestore instance associated with this document reference. */
  @NonNull
  public FirebaseFirestore getFirestore() {
    return firestore;
  }

  @NonNull
  public String getId() {
    return key.getPath().getLastSegment();
  }

  /**
   * Gets a {@code CollectionReference} to the collection that contains this document.
   *
   * @return The {@code CollectionReference} that contains this document.
   */
  @NonNull
  public CollectionReference getParent() {
    return new CollectionReference(key.getPath().popLast(), firestore);
  }

  /**
   * Gets the path of this document (relative to the root of the database) as a slash-separated
   * string.
   *
   * @return The path of this document.
   */
  @NonNull
  public String getPath() {
    return key.getPath().canonicalString();
  }

  /**
   * Gets a {@code CollectionReference} instance that refers to the subcollection at the specified
   * path relative to this document.
   *
   * @param collectionPath A slash-separated relative path to a subcollection.
   * @return The {@code CollectionReference} instance.
   */
  @NonNull
  public CollectionReference collection(@NonNull String collectionPath) {
    checkNotNull(collectionPath, "Provided collection path must not be null.");
    return new CollectionReference(
        key.getPath().append(ResourcePath.fromString(collectionPath)), firestore);
  }

  /**
   * Overwrites the document referred to by this {@code DocumentReference}. If the document does not
   * yet exist, it will be created. If a document already exists, it will be overwritten.
   *
   * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
   *     document contents).
   * @return A Task that will be resolved when the write finishes.
   */
  @NonNull
  public Task<Void> set(@NonNull Object data) {
    return set(data, SetOptions.OVERWRITE);
  }

  /**
   * Writes to the document referred to by this {@code DocumentReference}. If the document does not
   * yet exist, it will be created. If you pass {@code SetOptions}, the provided data can be merged
   * into an existing document.
   *
   * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
   *     document contents).
   * @param options An object to configure the set behavior.
   * @return A Task that will be resolved when the write finishes.
   */
  @NonNull
  public Task<Void> set(@NonNull Object data, @NonNull SetOptions options) {
    checkNotNull(data, "Provided data must not be null.");
    checkNotNull(options, "Provided options must not be null.");
    ParsedSetData parsed =
        options.isMerge()
            ? firestore.getUserDataReader().parseMergeData(data, options.getFieldMask())
            : firestore.getUserDataReader().parseSetData(data);
    return firestore
        .getClient()
        .write(parsed.toMutationList(key, Precondition.NONE))
        .continueWith(Executors.DIRECT_EXECUTOR, voidErrorTransformer());
  }

  /**
   * Updates fields in the document referred to by this {@code DocumentReference}. If no document
   * exists yet, the update will fail.
   *
   * @param data A map of field / value pairs to update. Fields can contain dots to reference nested
   *     fields within the document.
   * @return A Task that will be resolved when the write finishes.
   */
  @NonNull
  public Task<Void> update(@NonNull Map<String, Object> data) {
    ParsedUpdateData parsedData = firestore.getUserDataReader().parseUpdateData(data);
    return update(parsedData);
  }

  /**
   * Updates fields in the document referred to by this {@code DocumentReference}. If no document
   * exists yet, the update will fail.
   *
   * @param field The first field to update. Fields can contain dots to reference a nested field
   *     within the document.
   * @param value The first value
   * @param moreFieldsAndValues Additional field/value pairs.
   * @return A Task that will be resolved when the write finishes.
   */
  @NonNull
  public Task<Void> update(
      @NonNull String field, @Nullable Object value, Object... moreFieldsAndValues) {
    ParsedUpdateData parsedData =
        firestore
            .getUserDataReader()
            .parseUpdateData(
                Util.collectUpdateArguments(
                    /* fieldPathOffset= */ 1, field, value, moreFieldsAndValues));
    return update(parsedData);
  }

  /**
   * Updates fields in the document referred to by this {@code DocumentReference}. If no document
   * exists yet, the update will fail.
   *
   * @param fieldPath The first field to update.
   * @param value The first value
   * @param moreFieldsAndValues Additional field/value pairs.
   * @return A Task that will be resolved when the write finishes.
   */
  @NonNull
  public Task<Void> update(
      @NonNull FieldPath fieldPath, @Nullable Object value, Object... moreFieldsAndValues) {
    ParsedUpdateData parsedData =
        firestore
            .getUserDataReader()
            .parseUpdateData(
                Util.collectUpdateArguments(
                    /* fieldPathOffset= */ 1, fieldPath, value, moreFieldsAndValues));
    return update(parsedData);
  }

  private Task<Void> update(@NonNull ParsedUpdateData parsedData) {
    return firestore
        .getClient()
        .write(parsedData.toMutationList(key, Precondition.exists(true)))
        .continueWith(Executors.DIRECT_EXECUTOR, voidErrorTransformer());
  }

  /**
   * Deletes the document referred to by this {@code DocumentReference}.
   *
   * @return A Task that will be resolved when the delete completes.
   */
  @NonNull
  public Task<Void> delete() {
    return firestore
        .getClient()
        .write(singletonList(new DeleteMutation(key, Precondition.NONE)))
        .continueWith(Executors.DIRECT_EXECUTOR, voidErrorTransformer());
  }

  /**
   * Reads the document referenced by this {@code DocumentReference}.
   *
   * @return A Task that will be resolved with the contents of the Document at this {@code
   *     DocumentReference}.
   */
  @NonNull
  public Task<DocumentSnapshot> get() {
    return get(Source.DEFAULT);
  }

  /**
   * Reads the document referenced by this {@code DocumentReference}.
   *
   * <p>By default, {@code get()} attempts to provide up-to-date data when possible by waiting for
   * data from the server, but it may return cached data or fail if you are offline and the server
   * cannot be reached. This behavior can be altered via the {@code Source} parameter.
   *
   * @param source A value to configure the get behavior.
   * @return A Task that will be resolved with the contents of the Document at this {@code
   *     DocumentReference}.
   */
  @NonNull
  public Task<DocumentSnapshot> get(@NonNull Source source) {
    if (source == Source.CACHE) {
      return firestore
          .getClient()
          .getDocumentFromLocalCache(key)
          .continueWith(
              Executors.DIRECT_EXECUTOR,
              (Task<Document> task) -> {
                Document doc = task.getResult();
                boolean hasPendingWrites = doc != null && doc.hasLocalMutations();
                return new DocumentSnapshot(
                    firestore, key, doc, /*isFromCache=*/ true, hasPendingWrites);
              });
    } else {
      return getViaSnapshotListener(source);
    }
  }

  @NonNull
  private Task<DocumentSnapshot> getViaSnapshotListener(Source source) {
    final TaskCompletionSource<DocumentSnapshot> res = new TaskCompletionSource<>();
    final TaskCompletionSource<ListenerRegistration> registration = new TaskCompletionSource<>();

    ListenOptions options = new ListenOptions();
    options.includeDocumentMetadataChanges = true;
    options.includeQueryMetadataChanges = true;
    options.waitForSyncWhenOnline = true;

    ListenerRegistration listenerRegistration =
        addSnapshotListenerInternal(
            // No need to schedule, we just set the task result directly
            Executors.DIRECT_EXECUTOR,
            options,
            null,
            (snapshot, error) -> {
              if (error != null) {
                res.setException(error);
                return;
              }

              try {
                ListenerRegistration actualRegistration = Tasks.await(registration.getTask());

                // Remove query first before passing event to user to avoid user actions affecting
                // the now stale query.
                actualRegistration.remove();

                if (!snapshot.exists() && snapshot.getMetadata().isFromCache()) {
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
                      new FirebaseFirestoreException(
                          "Failed to get document because the client is offline.",
                          Code.UNAVAILABLE));
                } else if (snapshot.exists()
                    && snapshot.getMetadata().isFromCache()
                    && source == Source.SERVER) {
                  res.setException(
                      new FirebaseFirestoreException(
                          "Failed to get document from server. (However, this document does exist "
                              + "in the local cache. Run again without setting source to SERVER to "
                              + "retrieve the cached document.)",
                          Code.UNAVAILABLE));
                } else {
                  res.setResult(snapshot);
                }
              } catch (ExecutionException e) {
                throw fail(e, "Failed to register a listener for a single document");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw fail(e, "Failed to register a listener for a single document");
              }
            });

    registration.setResult(listenerRegistration);

    return res.getTask();
  }

  /**
   * Starts listening to the document referenced by this {@code DocumentReference}.
   *
   * @param listener The event listener that will be called with the snapshots.
   * @return A registration object that can be used to remove the listener.
   */
  @NonNull
  public ListenerRegistration addSnapshotListener(
      @NonNull EventListener<DocumentSnapshot> listener) {
    return addSnapshotListener(MetadataChanges.EXCLUDE, listener);
  }

  /**
   * Starts listening to the document referenced by this {@code DocumentReference}.
   *
   * @param executor The executor to use to call the listener.
   * @param listener The event listener that will be called with the snapshots.
   * @return A registration object that can be used to remove the listener.
   */
  @NonNull
  public ListenerRegistration addSnapshotListener(
      @NonNull Executor executor, @NonNull EventListener<DocumentSnapshot> listener) {
    return addSnapshotListener(executor, MetadataChanges.EXCLUDE, listener);
  }

  /**
   * Starts listening to the document referenced by this {@code DocumentReference} using an
   * Activity-scoped listener.
   *
   * <p>The listener will be automatically removed during {@link Activity#onStop}.
   *
   * @param activity The activity to scope the listener to.
   * @param listener The event listener that will be called with the snapshots.
   * @return A registration object that can be used to remove the listener.
   */
  @NonNull
  public ListenerRegistration addSnapshotListener(
      @NonNull Activity activity, @NonNull EventListener<DocumentSnapshot> listener) {
    return addSnapshotListener(activity, MetadataChanges.EXCLUDE, listener);
  }

  /**
   * Starts listening to the document referenced by this {@code DocumentReference} with the given
   * options.
   *
   * @param metadataChanges Indicates whether metadata-only changes (i.e. only {@code
   *     DocumentSnapshot.getMetadata()} changed) should trigger snapshot events.
   * @param listener The event listener that will be called with the snapshots.
   * @return A registration object that can be used to remove the listener.
   */
  @NonNull
  public ListenerRegistration addSnapshotListener(
      @NonNull MetadataChanges metadataChanges, @NonNull EventListener<DocumentSnapshot> listener) {
    return addSnapshotListener(Executors.DEFAULT_CALLBACK_EXECUTOR, metadataChanges, listener);
  }

  /**
   * Starts listening to the document referenced by this {@code DocumentReference} with the given
   * options.
   *
   * @param executor The executor to use to call the listener.
   * @param metadataChanges Indicates whether metadata-only changes (i.e. only {@code
   *     DocumentSnapshot.getMetadata()} changed) should trigger snapshot events.
   * @param listener The event listener that will be called with the snapshots.
   * @return A registration object that can be used to remove the listener.
   */
  @NonNull
  public ListenerRegistration addSnapshotListener(
      @NonNull Executor executor,
      @NonNull MetadataChanges metadataChanges,
      @NonNull EventListener<DocumentSnapshot> listener) {
    checkNotNull(executor, "Provided executor must not be null.");
    checkNotNull(metadataChanges, "Provided MetadataChanges value must not be null.");
    checkNotNull(listener, "Provided EventListener must not be null.");
    return addSnapshotListenerInternal(executor, internalOptions(metadataChanges), null, listener);
  }

  /**
   * Starts listening to the document referenced by this {@code DocumentReference} with the given
   * options using an Activity-scoped listener.
   *
   * <p>The listener will be automatically removed during {@link Activity#onStop}.
   *
   * @param activity The activity to scope the listener to.
   * @param metadataChanges Indicates whether metadata-only changes (i.e. only {@code
   *     DocumentSnapshot.getMetadata()} changed) should trigger snapshot events.
   * @param listener The event listener that will be called with the snapshots.
   * @return A registration object that can be used to remove the listener.
   */
  @NonNull
  public ListenerRegistration addSnapshotListener(
      @NonNull Activity activity,
      @NonNull MetadataChanges metadataChanges,
      @NonNull EventListener<DocumentSnapshot> listener) {
    checkNotNull(activity, "Provided activity must not be null.");
    checkNotNull(metadataChanges, "Provided MetadataChanges value must not be null.");
    checkNotNull(listener, "Provided EventListener must not be null.");
    return addSnapshotListenerInternal(
        Executors.DEFAULT_CALLBACK_EXECUTOR, internalOptions(metadataChanges), activity, listener);
  }

  /**
   * Internal helper method to create add a snapshot listener.
   *
   * <p>Will be Activity scoped if the activity parameter is non-{@code null}.
   *
   * @param userExecutor The executor to use to call the listener.
   * @param options The options to use for this listen.
   * @param activity Optional activity this listener is scoped to.
   * @param userListener The user-supplied event listener that will be called with document
   *     snapshots.
   * @return A registration object that can be used to remove the listener.
   */
  private ListenerRegistration addSnapshotListenerInternal(
      Executor userExecutor,
      ListenOptions options,
      @Nullable Activity activity,
      EventListener<DocumentSnapshot> userListener) {

    // Convert from ViewSnapshots to DocumentSnapshots.
    EventListener<ViewSnapshot> viewListener =
        (snapshot, error) -> {
          if (error != null) {
            userListener.onEvent(null, error);
            return;
          }

          Assert.hardAssert(snapshot != null, "Got event without value or error set");
          Assert.hardAssert(
              snapshot.getDocuments().size() <= 1,
              "Too many documents returned on a document query");

          Document document = snapshot.getDocuments().getDocument(key);
          DocumentSnapshot documentSnapshot;
          if (document != null) {
            boolean hasPendingWrites = snapshot.getMutatedKeys().contains(document.getKey());
            documentSnapshot =
                DocumentSnapshot.fromDocument(
                    firestore, document, snapshot.isFromCache(), hasPendingWrites);
          } else {
            // We don't raise `hasPendingWrites` for deleted documents.
            documentSnapshot =
                DocumentSnapshot.fromNoDocument(
                    firestore, key, snapshot.isFromCache(), /* hasPendingWrites= */ false);
          }
          userListener.onEvent(documentSnapshot, null);
        };

    // Call the viewListener on the userExecutor.
    AsyncEventListener<ViewSnapshot> asyncListener =
        new AsyncEventListener<>(userExecutor, viewListener);

    com.google.firebase.firestore.core.Query query = asQuery();
    QueryListener queryListener = firestore.getClient().listen(query, options, asyncListener);

    return ActivityScope.bind(
        activity,
        new ListenerRegistrationImpl(firestore.getClient(), queryListener, asyncListener));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DocumentReference)) {
      return false;
    }

    DocumentReference that = (DocumentReference) o;

    return key.equals(that.key) && firestore.equals(that.firestore);
  }

  @Override
  public int hashCode() {
    int result = key.hashCode();
    result = 31 * result + firestore.hashCode();
    return result;
  }

  private com.google.firebase.firestore.core.Query asQuery() {
    return com.google.firebase.firestore.core.Query.atPath(key.getPath());
  }

  /** Converts the public API MetadataChanges object to the internal options object. */
  private static ListenOptions internalOptions(MetadataChanges metadataChanges) {
    ListenOptions internalOptions = new ListenOptions();
    internalOptions.includeDocumentMetadataChanges = (metadataChanges == MetadataChanges.INCLUDE);
    internalOptions.includeQueryMetadataChanges = (metadataChanges == MetadataChanges.INCLUDE);
    internalOptions.waitForSyncWhenOnline = false;
    return internalOptions;
  }
}
