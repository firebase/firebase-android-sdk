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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.EventManager.ListenOptions;
import com.google.firebase.firestore.local.GarbageCollectionScheduler;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.QueryResult;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.remote.Datastore;
import com.google.firebase.firestore.remote.GrpcMetadataProvider;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Function;
import com.google.firebase.firestore.util.Logger;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FirestoreClient is a top-level class that constructs and owns all of the pieces of the client SDK
 * architecture.
 */
public final class FirestoreClient {

  private static final String LOG_TAG = "FirestoreClient";
  private static final int MAX_CONCURRENT_LIMBO_RESOLUTIONS = 100;

  private final DatabaseInfo databaseInfo;
  private final CredentialsProvider credentialsProvider;
  private final AsyncQueue asyncQueue;

  private Persistence persistence;
  private LocalStore localStore;
  private RemoteStore remoteStore;
  private SyncEngine syncEngine;
  private EventManager eventManager;
  private final GrpcMetadataProvider metadataProvider;

  // LRU-related
  @Nullable private GarbageCollectionScheduler gcScheduler;

  public FirestoreClient(
      final Context context,
      DatabaseInfo databaseInfo,
      FirebaseFirestoreSettings settings,
      CredentialsProvider credentialsProvider,
      final AsyncQueue asyncQueue,
      @Nullable GrpcMetadataProvider metadataProvider) {
    this.databaseInfo = databaseInfo;
    this.credentialsProvider = credentialsProvider;
    this.asyncQueue = asyncQueue;
    this.metadataProvider = metadataProvider;

    TaskCompletionSource<User> firstUser = new TaskCompletionSource<>();
    final AtomicBoolean initialized = new AtomicBoolean(false);

    // Defer initialization until we get the current user from the changeListener. This is
    // guaranteed to be synchronously dispatched onto our worker queue, so we will be initialized
    // before any subsequently queued work runs.
    asyncQueue.enqueueAndForget(
        () -> {
          try {
            // Block on initial user being available
            User initialUser = Tasks.await(firstUser.getTask());
            initialize(context, initialUser, settings);
          } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
          }
        });

    credentialsProvider.setChangeListener(
        (User user) -> {
          if (initialized.compareAndSet(false, true)) {
            hardAssert(!firstUser.getTask().isComplete(), "Already fulfilled first user task");
            firstUser.setResult(user);
          } else {
            asyncQueue.enqueueAndForget(
                () -> {
                  hardAssert(syncEngine != null, "SyncEngine not yet initialized");
                  Logger.debug(LOG_TAG, "Credential changed. Current user: %s", user.getUid());
                  syncEngine.handleCredentialChange(user);
                });
          }
        });
  }

  public Task<Void> disableNetwork() {
    this.verifyNotTerminated();
    return asyncQueue.enqueue(() -> remoteStore.disableNetwork());
  }

  public Task<Void> enableNetwork() {
    this.verifyNotTerminated();
    return asyncQueue.enqueue(() -> remoteStore.enableNetwork());
  }

  /** Terminates this client, cancels all writes / listeners, and releases all resources. */
  public Task<Void> terminate() {
    credentialsProvider.removeChangeListener();
    return asyncQueue.enqueueAndInitiateShutdown(
        () -> {
          remoteStore.shutdown();
          persistence.shutdown();
          if (gcScheduler != null) {
            gcScheduler.stop();
          }
        });
  }

  /** Returns true if this client has been terminated. */
  public boolean isTerminated() {
    // Technically, the asyncQueue is still running, but only accepting tasks related to terminating
    // or supposed to be run after terminate(). It is effectively terminated to the eyes of users.
    return this.asyncQueue.isShuttingDown();
  }

  /** Starts listening to a query. */
  public QueryListener listen(
      Query query, ListenOptions options, EventListener<ViewSnapshot> listener) {
    this.verifyNotTerminated();
    QueryListener queryListener = new QueryListener(query, options, listener);
    asyncQueue.enqueueAndForget(() -> eventManager.addQueryListener(queryListener));
    return queryListener;
  }

  /** Stops listening to a query previously listened to. */
  public void stopListening(QueryListener listener) {
    // Checks for terminate but does not raise error, allowing it to be a no-op if client is already
    // terminated.
    if (this.isTerminated()) {
      return;
    }
    asyncQueue.enqueueAndForget(() -> eventManager.removeQueryListener(listener));
  }

  public Task<Document> getDocumentFromLocalCache(DocumentKey docKey) {
    this.verifyNotTerminated();
    return asyncQueue
        .enqueue(() -> localStore.readDocument(docKey))
        .continueWith(
            (result) -> {
              @Nullable MaybeDocument maybeDoc = result.getResult();

              if (maybeDoc instanceof Document) {
                return (Document) maybeDoc;
              } else if (maybeDoc instanceof NoDocument) {
                return null;
              } else {
                throw new FirebaseFirestoreException(
                    "Failed to get document from cache. (However, this document may exist on the "
                        + "server. Run again without setting source to CACHE to attempt "
                        + "to retrieve the document from the server.)",
                    Code.UNAVAILABLE);
              }
            });
  }

  public Task<ViewSnapshot> getDocumentsFromLocalCache(Query query) {
    this.verifyNotTerminated();
    return asyncQueue.enqueue(
        () -> {
          QueryResult queryResult = localStore.executeQuery(query, /* usePreviousResults= */ true);
          View view = new View(query, queryResult.getRemoteKeys());
          View.DocumentChanges viewDocChanges = view.computeDocChanges(queryResult.getDocuments());
          return view.applyChanges(viewDocChanges).getSnapshot();
        });
  }

  /** Writes mutations. The returned task will be notified when it's written to the backend. */
  public Task<Void> write(final List<Mutation> mutations) {
    this.verifyNotTerminated();
    final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    asyncQueue.enqueueAndForget(() -> syncEngine.writeMutations(mutations, source));
    return source.getTask();
  }

  /** Tries to execute the transaction in updateFunction. */
  public <TResult> Task<TResult> transaction(Function<Transaction, Task<TResult>> updateFunction) {
    this.verifyNotTerminated();
    return AsyncQueue.callTask(
        asyncQueue.getExecutor(), () -> syncEngine.transaction(asyncQueue, updateFunction));
  }

  /**
   * Returns a task resolves when all the pending writes at the time when this method is called
   * received server acknowledgement. An acknowledgement can be either acceptance or rejections.
   */
  public Task<Void> waitForPendingWrites() {
    this.verifyNotTerminated();

    final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    asyncQueue.enqueueAndForget(() -> syncEngine.registerPendingWritesTask(source));
    return source.getTask();
  }

  private void initialize(Context context, User user, FirebaseFirestoreSettings settings) {
    // Note: The initialization work must all be synchronous (we can't dispatch more work) since
    // external write/listen operations could get queued to run before that subsequent work
    // completes.
    Logger.debug(LOG_TAG, "Initializing. user=%s", user.getUid());

    Datastore datastore =
        new Datastore(databaseInfo, asyncQueue, credentialsProvider, context, metadataProvider);
    ComponentProvider.Configuration configuration =
        new ComponentProvider.Configuration(
            context,
            asyncQueue,
            databaseInfo,
            datastore,
            user,
            MAX_CONCURRENT_LIMBO_RESOLUTIONS,
            settings);

    ComponentProvider provider =
        settings.isPersistenceEnabled()
            ? new SQLiteComponentProvider()
            : new MemoryComponentProvider();
    provider.initialize(configuration);
    persistence = provider.getPersistence();
    gcScheduler = provider.getGargabeCollectionScheduler();
    localStore = provider.getLocalStore();
    remoteStore = provider.getRemoteStore();
    syncEngine = provider.getSyncEngine();
    eventManager = provider.getEventManager();

    if (gcScheduler != null) {
      gcScheduler.start();
    }
  }

  public void addSnapshotsInSyncListener(EventListener<Void> listener) {
    verifyNotTerminated();
    asyncQueue.enqueueAndForget(() -> eventManager.addSnapshotsInSyncListener(listener));
  }

  public void removeSnapshotsInSyncListener(EventListener<Void> listener) {
    // Checks for shutdown but does not raise error, allowing remove after shutdown to be a no-op.
    if (isTerminated()) {
      return;
    }
    asyncQueue.enqueueAndForget(() -> eventManager.removeSnapshotsInSyncListener(listener));
  }

  private void verifyNotTerminated() {
    if (this.isTerminated()) {
      throw new IllegalStateException("The client has already been terminated");
    }
  }
}
