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

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.base.Function;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.PublicApi;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.auth.EmptyCredentialsProvider;
import com.google.firebase.firestore.auth.FirebaseAuthCredentialsProvider;
import com.google.firebase.firestore.core.DatabaseInfo;
import com.google.firebase.firestore.core.FirestoreClient;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.firestore.util.Logger.Level;
import java.util.concurrent.Executor;

/**
 * Represents a Firestore Database and is the entry point for all Firestore operations
 *
 * <p><b>Subclassing Note</b>: Firestore classes are not meant to be subclassed except for use in
 * test mocks. Subclassing is not supported in production code and new SDK releases may break code
 * that does so.
 */
@PublicApi
public class FirebaseFirestore {
  private static final String TAG = "FirebaseFirestore";
  private final Context context;
  // This is also used as private lock object for this instance. There is nothing inherent about
  // databaseId itself that needs locking; it just saves us creating a separate lock object.
  private final DatabaseId databaseId;
  private final String persistenceKey;
  private final CredentialsProvider credentialsProvider;
  private final AsyncQueue asyncQueue;
  private final FirebaseApp firebaseApp;

  private FirebaseFirestoreSettings settings;
  private volatile FirestoreClient client;
  private final UserDataConverter dataConverter;

  @NonNull
  @PublicApi
  public static FirebaseFirestore getInstance() {
    FirebaseApp app = FirebaseApp.getInstance();
    if (app == null) {
      throw new IllegalStateException("You must call FirebaseApp.initializeApp first.");
    }
    return getInstance(app, DatabaseId.DEFAULT_DATABASE_ID);
  }

  @NonNull
  @PublicApi
  public static FirebaseFirestore getInstance(@NonNull FirebaseApp app) {
    return getInstance(app, DatabaseId.DEFAULT_DATABASE_ID);
  }

  // TODO: make this public
  @NonNull
  private static FirebaseFirestore getInstance(@NonNull FirebaseApp app, @NonNull String database) {
    checkNotNull(app, "Provided FirebaseApp must not be null.");
    FirestoreMultiDbComponent component = app.get(FirestoreMultiDbComponent.class);
    checkNotNull(component, "Firestore component is not present.");
    return component.get(database);
  }

  @NonNull
  static FirebaseFirestore newInstance(
      @NonNull Context context,
      @NonNull FirebaseApp app,
      @Nullable InternalAuthProvider authProvider,
      @NonNull String database) {
    String projectId = app.getOptions().getProjectId();
    if (projectId == null) {
      throw new IllegalArgumentException("FirebaseOptions.getProjectId() cannot be null");
    }
    DatabaseId databaseId = DatabaseId.forDatabase(projectId, database);

    AsyncQueue queue = new AsyncQueue();

    CredentialsProvider provider;
    if (authProvider == null) {
      Logger.debug(TAG, "Firebase Auth not available, falling back to unauthenticated usage.");
      provider = new EmptyCredentialsProvider();
    } else {
      provider = new FirebaseAuthCredentialsProvider(authProvider);
    }

    queue.enqueueAndForget(
        () -> {
          try {
            ProviderInstaller.installIfNeeded(context);
          } catch (GooglePlayServicesNotAvailableException
              | GooglePlayServicesRepairableException e) {
            Logger.warn("Firestore", "Failed to update ssl context");
          }
        });

    // Firestore uses a different database for each app name. Note that we don't use
    // app.getPersistenceKey() here because it includes the application ID which is related
    // to the project ID. We already include the project ID when resolving the database,
    // so there is no need to include it in the persistence key.
    String persistenceKey = app.getName();

    return new FirebaseFirestore(context, databaseId, persistenceKey, provider, queue, app);
  }

  @VisibleForTesting
  FirebaseFirestore(
      Context context,
      DatabaseId databaseId,
      String persistenceKey,
      CredentialsProvider credentialsProvider,
      AsyncQueue asyncQueue,
      @Nullable FirebaseApp firebaseApp) {
    this.context = checkNotNull(context);
    this.databaseId = checkNotNull(checkNotNull(databaseId));
    this.dataConverter = new UserDataConverter(databaseId);
    this.persistenceKey = checkNotNull(persistenceKey);
    this.credentialsProvider = checkNotNull(credentialsProvider);
    this.asyncQueue = checkNotNull(asyncQueue);
    // NOTE: We allow firebaseApp to be null in tests only.
    this.firebaseApp = firebaseApp;

    settings = new FirebaseFirestoreSettings.Builder().build();
  }

  /** Returns the settings used by this FirebaseFirestore object. */
  @NonNull
  @PublicApi
  public FirebaseFirestoreSettings getFirestoreSettings() {
    return settings;
  }

  /**
   * Sets any custom settings used to configure this FirebaseFirestore object. This method can only
   * be called before calling any other methods on this object.
   */
  @PublicApi
  public void setFirestoreSettings(@NonNull FirebaseFirestoreSettings settings) {
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

  private void ensureClientConfigured() {
    if (client != null) {
      return;
    }

    synchronized (databaseId) {
      if (client != null) {
        return;
      }
      if (!settings.areTimestampsInSnapshotsEnabled()) {
        Logger.warn(
            "Firestore",
            "The behavior for java.util.Date objects stored in Firestore is going to change "
                + "AND YOUR APP MAY BREAK.\n"
                + "To hide this warning and ensure your app does not break, you need to add "
                + "the following code to your app before calling any other Cloud Firestore "
                + "methods:\n"
                + "\n"
                + "FirebaseFirestore firestore = FirebaseFirestore.getInstance();\n"
                + "FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()\n"
                + "    .setTimestampsInSnapshotsEnabled(true)\n"
                + "    .build();\n"
                + "firestore.setFirestoreSettings(settings);\n"
                + "\n"
                + "With this change, timestamps stored in Cloud Firestore will be read back as "
                + "com.google.firebase.Timestamp objects instead of as system java.util.Date "
                + "objects. So you will also need to update code expecting a java.util.Date to "
                + "instead expect a Timestamp. For example:\n"
                + "\n"
                + "// Old:\n"
                + "java.util.Date date = snapshot.getDate(\"created_at\");\n"
                + "// New:\n"
                + "Timestamp timestamp = snapshot.getTimestamp(\"created_at\");\n"
                + "java.util.Date date = timestamp.toDate();\n"
                + "\n"
                + "Please audit all existing usages of java.util.Date when you enable the new "
                + "behavior. In a future release, the behavior will be changed to the new "
                + "behavior, so if you do not follow these steps, YOUR APP MAY BREAK.");
      }
      DatabaseInfo databaseInfo =
          new DatabaseInfo(databaseId, persistenceKey, settings.getHost(), settings.isSslEnabled());

      client =
          new FirestoreClient(
              context,
              databaseInfo,
              settings.isPersistenceEnabled(),
              credentialsProvider,
              asyncQueue);
    }
  }

  /**
   * Returns the FirebaseApp instance to which this FirebaseFirestore belongs.
   *
   * @return The FirebaseApp instance to which this FirebaseFirestore belongs.
   */
  @NonNull
  @PublicApi
  public FirebaseApp getApp() {
    return firebaseApp;
  }

  /**
   * Gets a CollectionReference instance that refers to the collection at the specified path within
   * the database.
   *
   * @param collectionPath A slash-separated path to a collection.
   * @return The CollectionReference instance.
   */
  @NonNull
  @PublicApi
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
  @PublicApi
  public DocumentReference document(@NonNull String documentPath) {
    checkNotNull(documentPath, "Provided document path must not be null.");
    ensureClientConfigured();
    return DocumentReference.forPath(ResourcePath.fromString(documentPath), this);
  }

  /**
   * Executes the given updateFunction and then attempts to commit the changes applied within the
   * transaction. If any document read within the transaction has changed, the updateFunction will
   * be retried. If it fails to commit after 5 attempts, the transaction will fail.
   *
   * @param updateFunction The function to execute within the transaction context.
   * @param executor The executor to run the transaction callback on.
   * @return The task returned from the updateFunction.
   */
  private <TResult> Task<TResult> runTransaction(
      Transaction.Function<TResult> updateFunction, Executor executor) {
    ensureClientConfigured();

    // We wrap the function they provide in order to
    // 1. Use internal implementation classes for Transaction,
    // 2. Convert exceptions they throw into Tasks, and
    // 3. Run the user callback on the user queue.
    Function<com.google.firebase.firestore.core.Transaction, Task<TResult>> wrappedUpdateFunction =
        internalTransaction ->
            Tasks.call(
                executor,
                () ->
                    updateFunction.apply(
                        new Transaction(internalTransaction, FirebaseFirestore.this)));

    return client.transaction(wrappedUpdateFunction, 5);
  }

  /**
   * Executes the given updateFunction and then attempts to commit the changes applied within the
   * transaction. If any document read within the transaction has changed, the updateFunction will
   * be retried. If it fails to commit after 5 attempts, the transaction will fail.
   *
   * @param updateFunction The function to execute within the transaction context.
   * @return The task returned from the updateFunction.
   */
  @NonNull
  @PublicApi
  public <TResult> Task<TResult> runTransaction(
      @NonNull Transaction.Function<TResult> updateFunction) {
    checkNotNull(updateFunction, "Provided transaction update function must not be null.");
    return runTransaction(
        updateFunction, com.google.firebase.firestore.core.Transaction.getDefaultExecutor());
  }

  /**
   * Creates a write batch, used for performing multiple writes as a single atomic operation.
   *
   * @return The created WriteBatch object.
   */
  @NonNull
  @PublicApi
  public WriteBatch batch() {
    ensureClientConfigured();

    return new WriteBatch(this);
  }

  @VisibleForTesting
  Task<Void> shutdown() {
    if (client == null) {
      return Tasks.forResult(null);
    } else {
      return client.shutdown();
    }
  }

  @VisibleForTesting
  AsyncQueue getAsyncQueue() {
    return asyncQueue;
  }

  /**
   * Re-enables network usage for this instance after a prior call to disableNetwork().
   *
   * @return A Task that will be completed once networking is enabled.
   */
  @PublicApi
  public Task<Void> enableNetwork() {
    ensureClientConfigured();
    return client.enableNetwork();
  }

  /**
   * Disables network access for this instance. While the network is disabled, any snapshot
   * listeners or get() calls will return results from cache, and any write operations will be
   * queued until network usage is re-enabled via a call to enableNetwork().
   *
   * @return A Task that will be completed once networking is disabled.
   */
  @PublicApi
  public Task<Void> disableNetwork() {
    ensureClientConfigured();
    return client.disableNetwork();
  }

  /** Globally enables / disables Firestore logging for the SDK. */
  @PublicApi
  public static void setLoggingEnabled(boolean loggingEnabled) {
    if (loggingEnabled) {
      Logger.setLogLevel(Level.DEBUG);
    } else {
      Logger.setLogLevel(Level.WARN);
    }
  }

  FirestoreClient getClient() {
    return client;
  }

  DatabaseId getDatabaseId() {
    return databaseId;
  }

  UserDataConverter getDataConverter() {
    return dataConverter;
  }

  /** Helper to validate a DocumentReference. Used by WriteBatch and Transaction. */
  void validateReference(DocumentReference docRef) {
    checkNotNull(docRef, "Provided DocumentReference must not be null.");
    if (docRef.getFirestore() != this) {
      throw new IllegalArgumentException(
          "Provided document reference is from a different Firestore instance.");
    }
  }
}
