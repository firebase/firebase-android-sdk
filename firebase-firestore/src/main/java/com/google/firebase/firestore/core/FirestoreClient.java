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
import android.support.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.common.base.Function;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.EventManager.ListenOptions;
import com.google.firebase.firestore.local.LocalSerializer;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.LruDelegate;
import com.google.firebase.firestore.local.LruGarbageCollector;
import com.google.firebase.firestore.local.MemoryPersistence;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.SQLitePersistence;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatchResult;
import com.google.firebase.firestore.remote.AndroidConnectivityMonitor;
import com.google.firebase.firestore.remote.ConnectivityMonitor;
import com.google.firebase.firestore.remote.Datastore;
import com.google.firebase.firestore.remote.RemoteEvent;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import io.grpc.Status;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FirestoreClient is a top-level class that constructs and owns all of the pieces of the client SDK
 * architecture.
 */
public final class FirestoreClient implements RemoteStore.RemoteStoreCallback {

  private static final String LOG_TAG = "FirestoreClient";

  private final DatabaseInfo databaseInfo;
  private final CredentialsProvider credentialsProvider;
  private final AsyncQueue asyncQueue;

  private Persistence persistence;
  private LocalStore localStore;
  private RemoteStore remoteStore;
  private SyncEngine syncEngine;
  private EventManager eventManager;

  // LRU-related
  @Nullable private LruGarbageCollector.Scheduler lruScheduler;

  public FirestoreClient(
      final Context context,
      DatabaseInfo databaseInfo,
      FirebaseFirestoreSettings settings,
      CredentialsProvider credentialsProvider,
      final AsyncQueue asyncQueue) {
    this.databaseInfo = databaseInfo;
    this.credentialsProvider = credentialsProvider;
    this.asyncQueue = asyncQueue;

    TaskCompletionSource<User> firstUser = new TaskCompletionSource<>();
    final AtomicBoolean initialized = new AtomicBoolean(false);
    credentialsProvider.setChangeListener(
        (User user) -> {
          if (initialized.compareAndSet(false, true)) {
            hardAssert(!firstUser.getTask().isComplete(), "Already fulfilled first user task");
            firstUser.setResult(user);
          } else {
            asyncQueue.enqueueAndForget(
                () -> {
                  Logger.debug(LOG_TAG, "Credential changed. Current user: %s", user.getUid());
                  syncEngine.handleCredentialChange(user);
                });
          }
        });

    // Defer initialization until we get the current user from the changeListener. This is
    // guaranteed to be synchronously dispatched onto our worker queue, so we will be initialized
    // before any subsequently queued work runs.
    asyncQueue.enqueueAndForget(
        () -> {
          try {
            // Block on initial user being available
            User initialUser = Tasks.await(firstUser.getTask());
            initialize(
                context,
                initialUser,
                settings.isPersistenceEnabled(),
                settings.getCacheSizeBytes());
          } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
          }
        });
  }

  public Task<Void> disableNetwork() {
    return asyncQueue.enqueue(() -> remoteStore.disableNetwork());
  }

  public Task<Void> enableNetwork() {
    return asyncQueue.enqueue(() -> remoteStore.enableNetwork());
  }

  /** Shuts down this client, cancels all writes / listeners, and releases all resources. */
  public Task<Void> shutdown() {
    credentialsProvider.removeChangeListener();
    return asyncQueue.enqueue(
        () -> {
          remoteStore.shutdown();
          persistence.shutdown();
          if (lruScheduler != null) {
            lruScheduler.stop();
          }
        });
  }

  /** Starts listening to a query. */
  public QueryListener listen(
      Query query, ListenOptions options, EventListener<ViewSnapshot> listener) {
    QueryListener queryListener = new QueryListener(query, options, listener);
    asyncQueue.enqueueAndForget(() -> eventManager.addQueryListener(queryListener));
    return queryListener;
  }

  /** Stops listening to a query previously listened to. */
  public void stopListening(QueryListener listener) {
    asyncQueue.enqueueAndForget(() -> eventManager.removeQueryListener(listener));
  }

  public Task<Document> getDocumentFromLocalCache(DocumentKey docKey) {
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
    return asyncQueue.enqueue(
        () -> {
          ImmutableSortedMap<DocumentKey, Document> docs = localStore.executeQuery(query);

          View view =
              new View(
                  query,
                  new ImmutableSortedSet<DocumentKey>(
                      Collections.emptyList(), DocumentKey::compareTo));
          View.DocumentChanges viewDocChanges = view.computeDocChanges(docs);
          return view.applyChanges(viewDocChanges).getSnapshot();
        });
  }

  /** Writes mutations. The returned task will be notified when it's written to the backend. */
  public Task<Void> write(final List<Mutation> mutations) {
    final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    asyncQueue.enqueueAndForget(() -> syncEngine.writeMutations(mutations, source));
    return source.getTask();
  }

  /** Tries to execute the transaction in updateFunction up to retries times. */
  public <TResult> Task<TResult> transaction(
      Function<Transaction, Task<TResult>> updateFunction, int retries) {
    return AsyncQueue.callTask(
        asyncQueue.getExecutor(),
        () -> syncEngine.transaction(asyncQueue, updateFunction, retries));
  }

  private void initialize(Context context, User user, boolean usePersistence, long cacheSizeBytes) {
    // Note: The initialization work must all be synchronous (we can't dispatch more work) since
    // external write/listen operations could get queued to run before that subsequent work
    // completes.
    Logger.debug(LOG_TAG, "Initializing. user=%s", user.getUid());

    LruGarbageCollector gc = null;
    if (usePersistence) {
      LocalSerializer serializer =
          new LocalSerializer(new RemoteSerializer(databaseInfo.getDatabaseId()));
      LruGarbageCollector.Params params =
          LruGarbageCollector.Params.WithCacheSizeBytes(cacheSizeBytes);
      SQLitePersistence sqlitePersistence =
          new SQLitePersistence(
              context,
              databaseInfo.getPersistenceKey(),
              databaseInfo.getDatabaseId(),
              serializer,
              params);
      LruDelegate lruDelegate = sqlitePersistence.getReferenceDelegate();
      gc = lruDelegate.getGarbageCollector();
      persistence = sqlitePersistence;
    } else {
      persistence = MemoryPersistence.createEagerGcMemoryPersistence();
    }

    persistence.start();
    localStore = new LocalStore(persistence, user);
    if (gc != null) {
      lruScheduler = gc.newScheduler(asyncQueue, localStore);
      lruScheduler.start();
    }

    Datastore datastore = new Datastore(databaseInfo, asyncQueue, credentialsProvider, context);
    ConnectivityMonitor connectivityMonitor = new AndroidConnectivityMonitor(context);
    remoteStore = new RemoteStore(this, localStore, datastore, asyncQueue, connectivityMonitor);

    syncEngine = new SyncEngine(localStore, remoteStore, user);
    eventManager = new EventManager(syncEngine);

    // NOTE: RemoteStore depends on LocalStore (for persisting stream tokens, refilling mutation
    // queue, etc.) so must be started after LocalStore.
    localStore.start();
    remoteStore.start();
  }

  @Override
  public void handleRemoteEvent(RemoteEvent remoteEvent) {
    syncEngine.handleRemoteEvent(remoteEvent);
  }

  @Override
  public void handleRejectedListen(int targetId, Status error) {
    syncEngine.handleRejectedListen(targetId, error);
  }

  @Override
  public void handleSuccessfulWrite(MutationBatchResult mutationBatchResult) {
    syncEngine.handleSuccessfulWrite(mutationBatchResult);
  }

  @Override
  public void handleRejectedWrite(int batchId, Status error) {
    syncEngine.handleRejectedWrite(batchId, error);
  }

  @Override
  public void handleOnlineStateChange(OnlineState onlineState) {
    syncEngine.handleOnlineStateChange(onlineState);
  }

  @Override
  public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
    return syncEngine.getRemoteKeysForTarget(targetId);
  }
}
