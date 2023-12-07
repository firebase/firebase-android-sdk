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

import static com.google.firebase.firestore.util.Assert.hardAssert;
import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.PreviewApi;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.emulators.EmulatedServiceSettings;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.auth.FirebaseAppCheckTokenProvider;
import com.google.firebase.firestore.auth.FirebaseAuthCredentialsProvider;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.ActivityScope;
import com.google.firebase.firestore.core.AsyncEventListener;
import com.google.firebase.firestore.core.DatabaseInfo;
import com.google.firebase.firestore.core.FirestoreClient;
import com.google.firebase.firestore.local.SQLitePersistence;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.remote.FirestoreChannel;
import com.google.firebase.firestore.remote.GrpcMetadataProvider;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.ByteBufferInputStream;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Function;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.firestore.util.Logger.Level;
import com.google.firebase.firestore.util.Preconditions;
import com.google.firebase.inject.Deferred;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a Cloud Firestore database and is the entry point for all Cloud Firestore operations.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class FirebaseFirestore {

  /**
   * Provides a registry management interface for {@code FirebaseFirestore} instances.
   *
   * @hide
   */
  public interface InstanceRegistry {
    /** Removes the Cloud Firestore instance with given name from registry. */
    void remove(@NonNull String databaseId);
  }

  private static final String TAG = "FirebaseFirestore";
  private final Context context;
  // This is also used as private lock object for this instance. There is nothing inherent about
  // databaseId itself that needs locking; it just saves us creating a separate lock object.
  private final DatabaseId databaseId;
  private final String persistenceKey;
  private final CredentialsProvider<User> authProvider;
  private final CredentialsProvider<String> appCheckProvider;
  private final AsyncQueue asyncQueue;
  private final FirebaseApp firebaseApp;
  private final UserDataReader userDataReader;
  // When user requests to terminate, use this to notify `FirestoreMultiDbComponent` to deregister
  // this instance.
  private final InstanceRegistry instanceRegistry;
  @Nullable private EmulatedServiceSettings emulatorSettings;
  private FirebaseFirestoreSettings settings;
  private volatile FirestoreClient client;
  private final GrpcMetadataProvider metadataProvider;

  @Nullable private PersistentCacheIndexManager persistentCacheIndexManager;

  @NonNull
  private static FirebaseApp getDefaultFirebaseApp() {
    FirebaseApp app = FirebaseApp.getInstance();
    if (app == null) {
      throw new IllegalStateException("You must call FirebaseApp.initializeApp first.");
    }
    return app;
  }

  /**
   * Returns the default {@link FirebaseFirestore} instance for the default {@link FirebaseApp}.
   *
   * <p>Returns the same instance for all invocations. If no instance exists, initializes a new
   * instance.
   *
   * @returns The {@link FirebaseFirestore} instance.
   */
  @NonNull
  public static FirebaseFirestore getInstance() {
    return getInstance(getDefaultFirebaseApp(), DatabaseId.DEFAULT_DATABASE_ID);
  }

  /**
   * Returns the default {@link FirebaseFirestore} instance for the provided {@link FirebaseApp}.
   *
   * <p>For a given {@link FirebaseApp}, invocation always returns the same instance. If no instance
   * exists, initializes a new instance.
   *
   * @param app The {@link FirebaseApp} instance that the returned {@link FirebaseFirestore}
   *     instance is associated with.
   * @returns The {@link FirebaseFirestore} instance.
   */
  @NonNull
  public static FirebaseFirestore getInstance(@NonNull FirebaseApp app) {
    return getInstance(app, DatabaseId.DEFAULT_DATABASE_ID);
  }

  /**
   * Returns the {@link FirebaseFirestore} instance for the default {@link FirebaseApp}.
   *
   * <p>Returns the same instance for all invocations given the same database parameter. If no
   * instance exists, initializes a new instance.
   *
   * @param database The database ID.
   * @returns The {@link FirebaseFirestore} instance.
   */
  @NonNull
  public static FirebaseFirestore getInstance(@NonNull String database) {
    return getInstance(getDefaultFirebaseApp(), database);
  }

  /**
   * Returns the {@link FirebaseFirestore} instance for the provided {@link FirebaseApp}.
   *
   * <p>Returns the same instance for all invocations given the same {@link FirebaseApp} and
   * database parameter. If no instance exists, initializes a new instance.
   *
   * @param app The {@link FirebaseApp} instance that the returned {@link FirebaseFirestore}
   *     instance is associated with.
   * @param database The database ID.
   * @returns The {@link FirebaseFirestore} instance.
   */
  @NonNull
  public static FirebaseFirestore getInstance(@NonNull FirebaseApp app, @NonNull String database) {
    checkNotNull(app, "Provided FirebaseApp must not be null.");
    checkNotNull(database, "Provided database name must not be null.");
    FirestoreMultiDbComponent component = app.get(FirestoreMultiDbComponent.class);
    checkNotNull(component, "Firestore component is not present.");
    return component.get(database);
  }

  @NonNull
  static FirebaseFirestore newInstance(
      @NonNull Context context,
      @NonNull FirebaseApp app,
      @NonNull Deferred<InternalAuthProvider> deferredAuthProvider,
      @NonNull Deferred<InteropAppCheckTokenProvider> deferredAppCheckTokenProvider,
      @NonNull String database,
      @NonNull InstanceRegistry instanceRegistry,
      @Nullable GrpcMetadataProvider metadataProvider) {
    String projectId = app.getOptions().getProjectId();
    if (projectId == null) {
      throw new IllegalArgumentException("FirebaseOptions.getProjectId() cannot be null");
    }
    DatabaseId databaseId = DatabaseId.forDatabase(projectId, database);

    AsyncQueue queue = new AsyncQueue();

    CredentialsProvider<User> authProvider =
        new FirebaseAuthCredentialsProvider(deferredAuthProvider);
    CredentialsProvider<String> appCheckProvider =
        new FirebaseAppCheckTokenProvider(deferredAppCheckTokenProvider);

    // Firestore uses a different database for each app name. Note that we don't use
    // app.getPersistenceKey() here because it includes the application ID which is related
    // to the project ID. We already include the project ID when resolving the database,
    // so there is no need to include it in the persistence key.
    String persistenceKey = app.getName();

    FirebaseFirestore firestore =
        new FirebaseFirestore(
            context,
            databaseId,
            persistenceKey,
            authProvider,
            appCheckProvider,
            queue,
            app,
            instanceRegistry,
            metadataProvider);
    return firestore;
  }

  @VisibleForTesting
  FirebaseFirestore(
      Context context,
      DatabaseId databaseId,
      String persistenceKey,
      CredentialsProvider<User> authProvider,
      CredentialsProvider<String> appCheckProvider,
      AsyncQueue asyncQueue,
      @Nullable FirebaseApp firebaseApp,
      InstanceRegistry instanceRegistry,
      @Nullable GrpcMetadataProvider metadataProvider) {
    this.context = checkNotNull(context);
    this.databaseId = checkNotNull(checkNotNull(databaseId));
    this.userDataReader = new UserDataReader(databaseId);
    this.persistenceKey = checkNotNull(persistenceKey);
    this.authProvider = checkNotNull(authProvider);
    this.appCheckProvider = checkNotNull(appCheckProvider);
    this.asyncQueue = checkNotNull(asyncQueue);
    // NOTE: We allow firebaseApp to be null in tests only.
    this.firebaseApp = firebaseApp;
    this.instanceRegistry = instanceRegistry;
    this.metadataProvider = metadataProvider;

    this.settings = new FirebaseFirestoreSettings.Builder().build();
  }

  /** Returns the settings used by this {@code FirebaseFirestore} object. */
  @NonNull
  public FirebaseFirestoreSettings getFirestoreSettings() {
    return settings;
  }

  /**
   * Sets any custom settings used to configure this {@code FirebaseFirestore} object. This method
   * can only be called before calling any other methods on this object.
   */
  public void setFirestoreSettings(@NonNull FirebaseFirestoreSettings settings) {
    settings = mergeEmulatorSettings(settings, this.emulatorSettings);

    synchronized (databaseId) {
      checkNotNull(settings, "Provided settings must not be null.");

      // As a special exception, don't throw if the same settings are passed repeatedly. This
      // should make it simpler to get a Firestore instance in an activity.
      if (client != null && !this.settings.equals(settings)) {
        throw new IllegalStateException(
            "FirebaseFirestore has already been started and its settings can no longer be changed. "
                + "You can only call setFirestoreSettings() before calling any other methods on a "
                + "FirebaseFirestore object.");
      }

      this.settings = settings;
    }
  }

  /**
   * Modifies this FirebaseDatabase instance to communicate with the Cloud Firestore emulator.
   *
   * <p>Note: Call this method before using the instance to do any database operations.
   *
   * @param host the emulator host (for example, 10.0.2.2)
   * @param port the emulator port (for example, 8080)
   */
  public void useEmulator(@NonNull String host, int port) {
    if (this.client != null) {
      throw new IllegalStateException(
          "Cannot call useEmulator() after instance has already been initialized.");
    }

    this.emulatorSettings = new EmulatedServiceSettings(host, port);
    this.settings = mergeEmulatorSettings(this.settings, this.emulatorSettings);
  }

  private void ensureClientConfigured() {
    if (client != null) {
      return;
    }

    synchronized (databaseId) {
      if (client != null) {
        return;
      }
      DatabaseInfo databaseInfo =
          new DatabaseInfo(databaseId, persistenceKey, settings.getHost(), settings.isSslEnabled());

      client =
          new FirestoreClient(
              context,
              databaseInfo,
              settings,
              authProvider,
              appCheckProvider,
              asyncQueue,
              metadataProvider);
    }
  }

  private FirebaseFirestoreSettings mergeEmulatorSettings(
      @NonNull FirebaseFirestoreSettings settings,
      @Nullable EmulatedServiceSettings emulatorSettings) {
    if (emulatorSettings == null) {
      return settings;
    }

    if (!FirebaseFirestoreSettings.DEFAULT_HOST.equals(settings.getHost())) {
      Logger.warn(
          TAG,
          "Host has been set in FirebaseFirestoreSettings and useEmulator, emulator host will be used.");
    }

    return new FirebaseFirestoreSettings.Builder(settings)
        .setHost(emulatorSettings.getHost() + ":" + emulatorSettings.getPort())
        .setSslEnabled(false)
        .build();
  }

  /** Returns the FirebaseApp instance to which this {@code FirebaseFirestore} belongs. */
  @NonNull
  public FirebaseApp getApp() {
    return firebaseApp;
  }

  /**
   * Configures indexing for local query execution. Any previous index configuration is overridden.
   * The Task resolves once the index configuration has been persisted.
   *
   * <p>The index entries themselves are created asynchronously. You can continue to use queries
   * that require indexing even if the indices are not yet available. Query execution will
   * automatically start using the index once the index entries have been written.
   *
   * <p>The method accepts the JSON format exported by the Firebase CLI (`firebase
   * firestore:indexes`). If the JSON format is invalid, this method throws an exception.
   *
   * @param json The JSON format exported by the Firebase CLI.
   * @return A task that resolves once all indices are successfully configured.
   * @throws IllegalArgumentException if the JSON format is invalid
   * @deprecated Instead of creating cache indexes manually, consider using {@link
   *     PersistentCacheIndexManager#enableIndexAutoCreation()} to let the SDK decide whether to
   *     create cache indexes for queries running locally.
   */
  @Deprecated
  @PreviewApi
  @NonNull
  public Task<Void> setIndexConfiguration(@NonNull String json) {
    ensureClientConfigured();
    Preconditions.checkState(
        settings.isPersistenceEnabled(), "Cannot enable indexes when persistence is disabled");

    List<FieldIndex> parsedIndexes = new ArrayList<>();

    // See https://firebase.google.com/docs/reference/firestore/indexes/#json_format for the
    // format of the index definition. Unlike the backend, the SDK does not distinguish between
    // collection ID and collection group indices and hence the queryScope field is ignored.

    try {
      JSONObject jsonObject = new JSONObject(json);

      if (jsonObject.has("indexes")) {
        JSONArray indexes = jsonObject.getJSONArray("indexes");
        for (int i = 0; i < indexes.length(); ++i) {
          JSONObject definition = indexes.getJSONObject(i);
          String collectionGroup = definition.getString("collectionGroup");
          List<FieldIndex.Segment> segments = new ArrayList<>();

          JSONArray fields = definition.optJSONArray("fields");
          for (int f = 0; fields != null && f < fields.length(); ++f) {
            JSONObject field = fields.getJSONObject(f);
            FieldPath fieldPath = FieldPath.fromServerFormat(field.getString("fieldPath"));
            if ("CONTAINS".equals(field.optString("arrayConfig"))) {
              segments.add(FieldIndex.Segment.create(fieldPath, FieldIndex.Segment.Kind.CONTAINS));
            } else if ("ASCENDING".equals(field.optString("order"))) {
              segments.add(FieldIndex.Segment.create(fieldPath, FieldIndex.Segment.Kind.ASCENDING));
            } else {
              segments.add(
                  FieldIndex.Segment.create(fieldPath, FieldIndex.Segment.Kind.DESCENDING));
            }
          }

          parsedIndexes.add(
              FieldIndex.create(
                  FieldIndex.UNKNOWN_ID, collectionGroup, segments, FieldIndex.INITIAL_STATE));
        }
      }
    } catch (JSONException e) {
      throw new IllegalArgumentException("Failed to parse index configuration", e);
    }

    return client.configureFieldIndexes(parsedIndexes);
  }

  /**
   * Gets the {@code PersistentCacheIndexManager} instance used by this {@code FirebaseFirestore}
   * object.
   *
   * <p>This is not the same as Cloud Firestore Indexes. Persistent cache indexes are optional
   * indexes that only exist within the SDK to assist in local query execution.
   *
   * @return The {@code PersistentCacheIndexManager} instance or null if local persistent storage is
   *     not in use.
   */
  @Nullable
  public synchronized PersistentCacheIndexManager getPersistentCacheIndexManager() {
    ensureClientConfigured();
    if (persistentCacheIndexManager == null
        && (settings.isPersistenceEnabled()
            || settings.getCacheSettings() instanceof PersistentCacheSettings)) {
      persistentCacheIndexManager = new PersistentCacheIndexManager(client);
    }
    return persistentCacheIndexManager;
  }

  /**
   * Gets a {@code CollectionReference} instance that refers to the collection at the specified path
   * within the database.
   *
   * @param collectionPath A slash-separated path to a collection.
   * @return The {@code CollectionReference} instance.
   */
  @NonNull
  public CollectionReference collection(@NonNull String collectionPath) {
    checkNotNull(collectionPath, "Provided collection path must not be null.");
    ensureClientConfigured();
    return new CollectionReference(ResourcePath.fromString(collectionPath), this);
  }

  /**
   * Gets a `DocumentReference` instance that refers to the document at the specified path within
   * the database.
   *
   * @param documentPath A slash-separated path to a document.
   * @return The DocumentReference instance.
   */
  @NonNull
  public DocumentReference document(@NonNull String documentPath) {
    checkNotNull(documentPath, "Provided document path must not be null.");
    ensureClientConfigured();
    return DocumentReference.forPath(ResourcePath.fromString(documentPath), this);
  }

  /**
   * Creates and returns a new {@code Query} that includes all documents in the database that are
   * contained in a collection or subcollection with the given {@code collectionId}.
   *
   * @param collectionId Identifies the collections to query over. Every collection or subcollection
   *     with this ID as the last segment of its path will be included. Cannot contain a slash.
   * @return The created Query.
   */
  @NonNull
  public Query collectionGroup(@NonNull String collectionId) {
    checkNotNull(collectionId, "Provided collection ID must not be null.");
    if (collectionId.contains("/")) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid collectionId '%s'. Collection IDs must not contain '/'.", collectionId));
    }

    ensureClientConfigured();
    return new Query(
        new com.google.firebase.firestore.core.Query(ResourcePath.EMPTY, collectionId), this);
  }

  /**
   * Executes the given {@code updateFunction} and then attempts to commit the changes applied
   * within the transaction. If any document read within the transaction has changed, the
   * updateFunction will be retried. If it fails to commit after 5 attempts (the default failure
   * limit), the transaction will fail.
   *
   * <p>The maximum number of writes allowed in a single transaction is 500, but note that each
   * usage of {@link FieldValue#serverTimestamp()}, {@link FieldValue#arrayUnion(Object...)}, {@link
   * FieldValue#arrayRemove(Object...)}, or {@link FieldValue#increment(long)} inside a transaction
   * counts as an additional write.
   *
   * @param updateFunction The function to execute within the transaction context.
   * @param executor The executor to run the transaction callback on.
   * @return The task returned from the updateFunction.
   */
  private <ResultT> Task<ResultT> runTransaction(
      TransactionOptions options, Transaction.Function<ResultT> updateFunction, Executor executor) {
    ensureClientConfigured();

    // We wrap the function they provide in order to
    // 1. Use internal implementation classes for Transaction,
    // 2. Convert exceptions they throw into Tasks, and
    // 3. Run the user callback on the user queue.
    Function<com.google.firebase.firestore.core.Transaction, Task<ResultT>> wrappedUpdateFunction =
        internalTransaction ->
            Tasks.call(
                executor,
                () ->
                    updateFunction.apply(
                        new Transaction(internalTransaction, FirebaseFirestore.this)));

    return client.transaction(options, wrappedUpdateFunction);
  }

  /**
   * Executes the given {@code updateFunction} and then attempts to commit the changes applied
   * within the transaction. If any document read within the transaction has changed, the
   * updateFunction will be retried. If it fails to commit after 5 attempts (the default failure
   * limit), the transaction will fail. To have a different number of retries, use the {@link
   * FirebaseFirestore#runTransaction(TransactionOptions, Transaction.Function)} method instead.
   *
   * @param updateFunction The function to execute within the transaction context.
   * @return The task returned from the updateFunction.
   */
  @NonNull
  public <TResult> Task<TResult> runTransaction(
      @NonNull Transaction.Function<TResult> updateFunction) {
    return runTransaction(TransactionOptions.DEFAULT, updateFunction);
  }

  /**
   * Executes the given {@code updateFunction} and then attempts to commit the changes applied
   * within the transaction. If any document read within the transaction has changed, the
   * updateFunction will be retried. If it fails to commit after the maxmimum number of attempts
   * specified in transactionOptions, the transaction will fail.
   *
   * @param options The transaction options for controlling execution.
   * @param updateFunction The function to execute within the transaction context.
   * @return The task returned from the updateFunction.
   */
  @NonNull
  public <TResult> Task<TResult> runTransaction(
      @NonNull TransactionOptions options, @NonNull Transaction.Function<TResult> updateFunction) {
    checkNotNull(updateFunction, "Provided transaction update function must not be null.");
    return runTransaction(
        options,
        updateFunction,
        com.google.firebase.firestore.core.Transaction.getDefaultExecutor());
  }

  /**
   * Creates a write batch, used for performing multiple writes as a single atomic operation.
   *
   * <p>The maximum number of writes allowed in a single batch is 500, but note that each usage of
   * {@link FieldValue#serverTimestamp()}, {@link FieldValue#arrayUnion(Object...)}, {@link
   * FieldValue#arrayRemove(Object...)}, or {@link FieldValue#increment(long)} inside a transaction
   * counts as an additional write.
   *
   * @return The created WriteBatch object.
   */
  @NonNull
  public WriteBatch batch() {
    ensureClientConfigured();

    return new WriteBatch(this);
  }

  /**
   * Executes a batchFunction on a newly created {@link WriteBatch} and then commits all of the
   * writes made by the batchFunction as a single atomic unit.
   *
   * @param batchFunction The function to execute within the batch context.
   * @return A Task that will be resolved when the batch has been committed.
   */
  @NonNull
  public Task<Void> runBatch(@NonNull WriteBatch.Function batchFunction) {
    WriteBatch batch = batch();
    batchFunction.apply(batch);
    return batch.commit();
  }

  /**
   * Terminates this {@code FirebaseFirestore} instance.
   *
   * <p>After calling {@code terminate()} only the {@link #clearPersistence()} method may be used.
   * Any other method will throw an {@link IllegalStateException}.
   *
   * <p>To restart after termination, simply create a new instance of {@code FirebaseFirestore} with
   * {@link #getInstance()} or {@link #getInstance(FirebaseApp)}.
   *
   * <p>{@code terminate()} does not cancel any pending writes and any tasks that are awaiting a
   * response from the server will not be resolved. The next time you start this instance, it will
   * resume attempting to send these writes to the server.
   *
   * <p>Note: Under normal circumstances, calling {@code terminate()} is not required. This method
   * is useful only when you want to force this instance to release all of its resources or in
   * combination with {@link #clearPersistence} to ensure that all local state is destroyed between
   * test runs.
   *
   * @return A {@code Task} that is resolved when the instance has been successfully terminated.
   */
  @NonNull
  public Task<Void> terminate() {
    instanceRegistry.remove(this.getDatabaseId().getDatabaseId());

    // The client must be initialized to ensure that all subsequent API usage throws an exception.
    this.ensureClientConfigured();
    return client.terminate();
  }

  /**
   * Waits until all currently pending writes for the active user have been acknowledged by the
   * backend.
   *
   * <p>The returned Task completes immediately if there are no outstanding writes. Otherwise, the
   * Task waits for all previously issued writes (including those written in a previous app
   * session), but it does not wait for writes that were added after the method is called. If you
   * wish to wait for additional writes, you have to call {@code waitForPendingWrites()} again.
   *
   * <p>Any outstanding {@code waitForPendingWrites()} Tasks are cancelled during user changes.
   *
   * @return A {@code Task} which resolves when all currently pending writes have been acknowledged
   *     by the backend.
   */
  @NonNull
  public Task<Void> waitForPendingWrites() {
    ensureClientConfigured();
    return client.waitForPendingWrites();
  }

  @VisibleForTesting
  AsyncQueue getAsyncQueue() {
    return asyncQueue;
  }

  /**
   * Re-enables network usage for this instance after a prior call to {@link #disableNetwork()}.
   *
   * @return A Task that will be completed once networking is enabled.
   */
  @NonNull
  public Task<Void> enableNetwork() {
    ensureClientConfigured();
    return client.enableNetwork();
  }

  /**
   * Disables network access for this instance. While the network is disabled, any snapshot
   * listeners or {@code get()} calls will return results from cache, and any write operations will
   * be queued until network usage is re-enabled via a call to {@link #enableNetwork()}.
   *
   * @return A Task that will be completed once networking is disabled.
   */
  @NonNull
  public Task<Void> disableNetwork() {
    ensureClientConfigured();
    return client.disableNetwork();
  }

  /** Globally enables / disables Cloud Firestore logging for the SDK. */
  public static void setLoggingEnabled(boolean loggingEnabled) {
    if (loggingEnabled) {
      Logger.setLogLevel(Level.DEBUG);
    } else {
      Logger.setLogLevel(Level.WARN);
    }
  }

  /**
   * Clears the persistent storage, including pending writes and cached documents.
   *
   * <p>Must be called while the {@code FirebaseFirestore} instance is not started (after the app is
   * shutdown or when the app is first initialized). On startup, this method must be called before
   * other methods (other than {@link #setFirestoreSettings(FirebaseFirestoreSettings)}). If the
   * {@code FirebaseFirestore} instance is still running, the {@code Task} will fail with an error
   * code of {@code FAILED_PRECONDITION}.
   *
   * <p>Note: {@code clearPersistence()} is primarily intended to help write reliable tests that use
   * Cloud Firestore. It uses an efficient mechanism for dropping existing data but does not attempt
   * to securely overwrite or otherwise make cached data unrecoverable. For applications that are
   * sensitive to the disclosure of cached data in between user sessions, we strongly recommend not
   * enabling persistence at all.
   *
   * @return A {@code Task} that is resolved when the persistent storage is cleared. Otherwise, the
   *     {@code Task} is rejected with an error.
   */
  @NonNull
  public Task<Void> clearPersistence() {
    final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    asyncQueue.enqueueAndForgetEvenAfterShutdown(
        () -> {
          try {
            if (client != null && !client.isTerminated()) {
              throw new FirebaseFirestoreException(
                  "Persistence cannot be cleared while the firestore instance is running.",
                  Code.FAILED_PRECONDITION);
            }
            SQLitePersistence.clearPersistence(context, databaseId, persistenceKey);
            source.setResult(null);
          } catch (FirebaseFirestoreException e) {
            source.setException(e);
          }
        });
    return source.getTask();
  }

  /**
   * Attaches a listener for a snapshots-in-sync event. The snapshots-in-sync event indicates that
   * all listeners affected by a given change have fired, even if a single server-generated change
   * affects multiple listeners.
   *
   * <p>NOTE: The snapshots-in-sync event only indicates that listeners are in sync with each other,
   * but does not relate to whether those snapshots are in sync with the server. Use
   * SnapshotMetadata in the individual listeners to determine if a snapshot is from the cache or
   * the server.
   *
   * @param runnable A callback to be called every time all snapshot listeners are in sync with each
   *     other.
   * @return A registration object that can be used to remove the listener.
   */
  @NonNull
  public ListenerRegistration addSnapshotsInSyncListener(@NonNull Runnable runnable) {
    return addSnapshotsInSyncListener(Executors.DEFAULT_CALLBACK_EXECUTOR, runnable);
  }

  /**
   * Attaches a listener for a snapshots-in-sync event. The snapshots-in-sync event indicates that
   * all listeners affected by a given change have fired, even if a single server-generated change
   * affects multiple listeners.
   *
   * <p>NOTE: The snapshots-in-sync event only indicates that listeners are in sync with each other,
   * but does not relate to whether those snapshots are in sync with the server. Use
   * SnapshotMetadata in the individual listeners to determine if a snapshot is from the cache or
   * the server.
   *
   * @param activity The activity to scope the listener to.
   * @param runnable A callback to be called every time all snapshot listeners are in sync with each
   *     other.
   * @return A registration object that can be used to remove the listener.
   */
  @NonNull
  public ListenerRegistration addSnapshotsInSyncListener(
      @NonNull Activity activity, @NonNull Runnable runnable) {
    return addSnapshotsInSyncListener(Executors.DEFAULT_CALLBACK_EXECUTOR, activity, runnable);
  }

  /**
   * Attaches a listener for a snapshots-in-sync event. The snapshots-in-sync event indicates that
   * all listeners affected by a given change have fired, even if a single server-generated change
   * affects multiple listeners.
   *
   * <p>NOTE: The snapshots-in-sync event only indicates that listeners are in sync with each other,
   * but does not relate to whether those snapshots are in sync with the server. Use
   * SnapshotMetadata in the individual listeners to determine if a snapshot is from the cache or
   * the server.
   *
   * @param executor The executor to use to call the listener.
   * @param runnable A callback to be called every time all snapshot listeners are in sync with each
   *     other.
   * @return A registration object that can be used to remove the listener.
   */
  @NonNull
  public ListenerRegistration addSnapshotsInSyncListener(
      @NonNull Executor executor, @NonNull Runnable runnable) {
    return addSnapshotsInSyncListener(executor, null, runnable);
  }

  /**
   * Loads a Firestore bundle into the local cache.
   *
   * @param bundleData A stream representing the bundle to be loaded.
   * @return A {@link LoadBundleTask}, which notifies callers with progress updates, and completion
   *     or error events.
   */
  @NonNull
  public LoadBundleTask loadBundle(@NonNull InputStream bundleData) {
    ensureClientConfigured();
    LoadBundleTask resultTask = new LoadBundleTask();
    client.loadBundle(bundleData, resultTask);
    return resultTask;
  }

  /**
   * Loads a Firestore bundle into the local cache.
   *
   * @param bundleData A byte array representing the bundle to be loaded.
   * @return A {@link LoadBundleTask}, which notifies callers with progress updates, and completion
   *     or error events.
   */
  @NonNull
  public LoadBundleTask loadBundle(@NonNull byte[] bundleData) {
    return loadBundle(new ByteArrayInputStream(bundleData));
  }

  /**
   * Loads a Firestore bundle into the local cache.
   *
   * @param bundleData A ByteBuffer representing the bundle to be loaded.
   * @return A {@link LoadBundleTask}, which notifies callers with progress updates, and completion
   *     or error events.
   */
  @NonNull
  public LoadBundleTask loadBundle(@NonNull ByteBuffer bundleData) {
    return loadBundle(new ByteBufferInputStream(bundleData));
  }

  /**
   * Reads a Firestore {@link Query} from local cache, identified by the given name.
   *
   * <p>The named queries are packaged into bundles on the server side (along with resulting
   * documents) and loaded to local cache using {@link #loadBundle(byte[])}. Once in local cache,
   * you can use this method to extract a query by name.
   */
  // TODO(b/261013682): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  public @NonNull Task<Query> getNamedQuery(@NonNull String name) {
    ensureClientConfigured();
    return client
        .getNamedQuery(name)
        .continueWith(
            task -> {
              com.google.firebase.firestore.core.Query query = task.getResult();
              if (query != null) {
                return new Query(query, FirebaseFirestore.this);
              } else {
                return null;
              }
            });
  }

  /**
   * Internal helper method to add a snapshotsInSync listener.
   *
   * <p>Will be Activity scoped if the activity parameter is non-{@code null}.
   *
   * @param userExecutor The executor to use to call the listener.
   * @param activity Optional activity this listener is scoped to.
   * @param runnable A callback to be called every time all snapshot listeners are in sync with each
   *     other.
   * @return A registration object that can be used to remove the listener.
   */
  private ListenerRegistration addSnapshotsInSyncListener(
      Executor userExecutor, @Nullable Activity activity, @NonNull Runnable runnable) {
    ensureClientConfigured();
    EventListener<Void> eventListener =
        (Void v, FirebaseFirestoreException error) -> {
          hardAssert(error == null, "snapshots-in-sync listeners should never get errors.");
          runnable.run();
        };
    AsyncEventListener<Void> asyncListener =
        new AsyncEventListener<Void>(userExecutor, eventListener);
    client.addSnapshotsInSyncListener(asyncListener);
    return ActivityScope.bind(
        activity,
        () -> {
          asyncListener.mute();
          client.removeSnapshotsInSyncListener(asyncListener);
        });
  }

  FirestoreClient getClient() {
    return client;
  }

  DatabaseId getDatabaseId() {
    return databaseId;
  }

  UserDataReader getUserDataReader() {
    return userDataReader;
  }

  /**
   * Helper to validate a {@code DocumentReference}. Used by {@link WriteBatch} and {@link
   * Transaction}.
   */
  void validateReference(DocumentReference docRef) {
    checkNotNull(docRef, "Provided DocumentReference must not be null.");
    if (docRef.getFirestore() != this) {
      throw new IllegalArgumentException(
          "Provided document reference is from a different Cloud Firestore instance.");
    }
  }

  /**
   * Sets the language of the public API in the format of "gl-<language>/<version>" where version
   * might be blank, for example `gl-cpp/`. The provided string is used as is.
   *
   * <p>Note: this method is package-private because it is expected to only be called via JNI (which
   * ignores access modifiers).
   */
  @Keep
  static void setClientLanguage(@NonNull String languageToken) {
    FirestoreChannel.setClientLanguage(languageToken);
  }
}
