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

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.ViewSnapshot.SyncState;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.LocalViewChanges;
import com.google.firebase.firestore.local.LocalWriteResult;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.local.QueryResult;
import com.google.firebase.firestore.local.ReferenceSet;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.MutationBatchResult;
import com.google.firebase.firestore.remote.RemoteEvent;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.remote.TargetChange;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Function;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.firestore.util.Util;
import io.grpc.Status;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * SyncEngine is the central controller in the client SDK architecture. It is the glue code between
 * the EventManager, LocalStore, and RemoteStore. Some of SyncEngine's responsibilities include:
 *
 * <ol>
 *   <li>Coordinating client requests and remote events between the EventManager and the local and
 *       remote data stores.
 *   <li>Managing a View object for each query, providing the unified view between the local and
 *       remote data stores.
 *   <li>Notifying the RemoteStore when the LocalStore has new mutations in its queue that need
 *       sending to the backend.
 * </ol>
 *
 * <p>The SyncEngine’s methods should only ever be called by methods running on our own worker
 * dispatch queue.
 */
public class SyncEngine implements RemoteStore.RemoteStoreCallback {

  /** Tracks a limbo resolution. */
  private static class LimboResolution {
    private final DocumentKey key;

    /**
     * Set to true once we've received a document. This is used in getRemoteKeysForTarget() and
     * ultimately used by WatchChangeAggregator to decide whether it needs to manufacture a delete
     * event for the target once the target is CURRENT.
     */
    private boolean receivedDocument;

    LimboResolution(DocumentKey key) {
      this.key = key;
    }
  }

  private static final String TAG = SyncEngine.class.getSimpleName();

  /** Interface implemented by EventManager to handle notifications from SyncEngine. */
  interface SyncEngineCallback {
    /** Handles new view snapshots. */
    void onViewSnapshots(List<ViewSnapshot> snapshotList);

    /** Handles the failure of a query. */
    void onError(Query query, Status error);

    /** Handles a change in online state. */
    void handleOnlineStateChange(OnlineState onlineState);
  }

  /** The local store, used to persist mutations and cached documents. */
  private final LocalStore localStore;

  /** The remote store for sending writes, watches, etc. to the backend. */
  private final RemoteStore remoteStore;

  /** QueryViews for all active queries, indexed by query. */
  private final Map<Query, QueryView> queryViewsByQuery;

  /** Queries mapped to active targets, indexed by target id. */
  private final Map<Integer, List<Query>> queriesByTarget;

  private final int maxConcurrentLimboResolutions;

  /**
   * The keys of documents that are in limbo for which we haven't yet started a limbo resolution
   * query.
   */
  private final Queue<DocumentKey> enqueuedLimboResolutions;

  /** Keeps track of the target ID for each document that is in limbo with an active target. */
  private final Map<DocumentKey, Integer> activeLimboTargetsByKey;

  /**
   * Keeps track of the information about an active limbo resolution for each active target ID that
   * was started for the purpose of limbo resolution.
   */
  private final Map<Integer, LimboResolution> activeLimboResolutionsByTarget;

  /** Used to track any documents that are currently in limbo. */
  private final ReferenceSet limboDocumentRefs;

  /** Stores user completion blocks, indexed by user and batch ID. */
  private final Map<User, Map<Integer, TaskCompletionSource<Void>>> mutationUserCallbacks;

  /** Stores user callbacks waiting for all pending writes to be acknowledged. */
  private final Map<Integer, List<TaskCompletionSource<Void>>> pendingWritesCallbacks;

  /** Used for creating the target IDs for the listens used to resolve limbo documents. */
  private final TargetIdGenerator targetIdGenerator;

  private User currentUser;

  private SyncEngineCallback syncEngineListener;

  public SyncEngine(
      LocalStore localStore,
      RemoteStore remoteStore,
      User initialUser,
      int maxConcurrentLimboResolutions) {
    this.localStore = localStore;
    this.remoteStore = remoteStore;
    this.maxConcurrentLimboResolutions = maxConcurrentLimboResolutions;

    queryViewsByQuery = new HashMap<>();
    queriesByTarget = new HashMap<>();

    enqueuedLimboResolutions = new ArrayDeque<>();
    activeLimboTargetsByKey = new HashMap<>();
    activeLimboResolutionsByTarget = new HashMap<>();
    limboDocumentRefs = new ReferenceSet();

    mutationUserCallbacks = new HashMap<>();
    targetIdGenerator = TargetIdGenerator.forSyncEngine();
    currentUser = initialUser;

    pendingWritesCallbacks = new HashMap<>();
  }

  public void setCallback(SyncEngineCallback callback) {
    this.syncEngineListener = callback;
  }

  private void assertCallback(String method) {
    hardAssert(syncEngineListener != null, "Trying to call %s before setting callback", method);
  }

  /**
   * Initiates a new listen. The LocalStore will be queried for initial data and the listen will be
   * sent to the RemoteStore to get remote data. The registered SyncEngineCallback will be notified
   * of resulting view snapshots and/or listen errors.
   *
   * @return the target ID assigned to the query.
   */
  public int listen(Query query) {
    assertCallback("listen");
    hardAssert(!queryViewsByQuery.containsKey(query), "We already listen to query: %s", query);

    TargetData targetData = localStore.allocateTarget(query.toTarget());
    ViewSnapshot viewSnapshot = initializeViewAndComputeSnapshot(query, targetData.getTargetId());
    syncEngineListener.onViewSnapshots(Collections.singletonList(viewSnapshot));

    remoteStore.listen(targetData);
    return targetData.getTargetId();
  }

  private ViewSnapshot initializeViewAndComputeSnapshot(Query query, int targetId) {
    QueryResult queryResult = localStore.executeQuery(query, /* usePreviousResults= */ true);

    SyncState currentTargetSyncState = SyncState.NONE;
    TargetChange synthesizedCurrentChange = null;

    // If there are already queries mapped to the target id, create a synthesized target change to
    // apply the sync state from those queries to the new query.
    if (this.queriesByTarget.get(targetId) != null) {
      Query mirrorQuery = this.queriesByTarget.get(targetId).get(0);
      currentTargetSyncState = this.queryViewsByQuery.get(mirrorQuery).getView().getSyncState();
      synthesizedCurrentChange =
          TargetChange.createSynthesizedTargetChangeForCurrentChange(
              currentTargetSyncState == SyncState.SYNCED);
    }

    // TODO(wuandy): Investigate if we can extract the logic of view change computation and
    // update tracked limbo in one place, and have both emitNewSnapsAndNotifyLocalStore
    // and here to call that.
    View view = new View(query, queryResult.getRemoteKeys());
    View.DocumentChanges viewDocChanges = view.computeDocChanges(queryResult.getDocuments());
    ViewChange viewChange = view.applyChanges(viewDocChanges, synthesizedCurrentChange);
    updateTrackedLimboDocuments(viewChange.getLimboChanges(), targetId);

    QueryView queryView = new QueryView(query, targetId, view);
    queryViewsByQuery.put(query, queryView);

    if (!queriesByTarget.containsKey(targetId)) {
      // Most likely there will only be one query mapping to a target, so construct the
      // query list with capacity 1.
      queriesByTarget.put(targetId, new ArrayList<>(1));
    }
    queriesByTarget.get(targetId).add(query);

    return viewChange.getSnapshot();
  }

  /** Stops listening to a query previously listened to via listen. */
  void stopListening(Query query) {
    assertCallback("stopListening");

    QueryView queryView = queryViewsByQuery.get(query);
    hardAssert(queryView != null, "Trying to stop listening to a query not found");

    queryViewsByQuery.remove(query);

    int targetId = queryView.getTargetId();
    List<Query> targetQueries = queriesByTarget.get(targetId);
    targetQueries.remove(query);

    if (targetQueries.isEmpty()) {
      localStore.releaseTarget(targetId);
      remoteStore.stopListening(targetId);
      removeAndCleanupTarget(targetId, Status.OK);
    }
  }

  /**
   * Initiates the write of local mutation batch which involves adding the writes to the mutation
   * queue, notifying the remote store about new mutations, and raising events for any changes this
   * write caused. The provided task will be resolved once the write has been acked/rejected by the
   * backend (or failed locally for any other reason).
   */
  public void writeMutations(List<Mutation> mutations, TaskCompletionSource<Void> userTask) {
    assertCallback("writeMutations");

    LocalWriteResult result = localStore.writeLocally(mutations);
    addUserCallback(result.getBatchId(), userTask);

    emitNewSnapsAndNotifyLocalStore(result.getChanges(), /*remoteEvent=*/ null);
    remoteStore.fillWritePipeline();
  }

  private void addUserCallback(int batchId, TaskCompletionSource<Void> userTask) {
    Map<Integer, TaskCompletionSource<Void>> userTasks = mutationUserCallbacks.get(currentUser);
    if (userTasks == null) {
      userTasks = new HashMap<>();
      mutationUserCallbacks.put(currentUser, userTasks);
    }
    userTasks.put(batchId, userTask);
  }

  /**
   * Takes an updateFunction in which a set of reads and writes can be performed atomically. In the
   * updateFunction, the client can read and write values using the supplied transaction object.
   * After the updateFunction, all changes will be committed. If a retryable error occurs (ex: some
   * other client has changed any of the data referenced), then the updateFunction will be called
   * again after a backoff. If the updateFunction still fails after all retries, then the
   * transaction will be rejected.
   *
   * <p>The transaction object passed to the updateFunction contains methods for accessing documents
   * and collections. Unlike other datastore access, data accessed with the transaction will not
   * reflect local changes that have not been committed. For this reason, it is required that all
   * reads are performed before any writes. Transactions must be performed while online.
   *
   * <p>The Task returned is resolved when the transaction is fully committed.
   */
  public <TResult> Task<TResult> transaction(
      AsyncQueue asyncQueue, Function<Transaction, Task<TResult>> updateFunction) {
    return new TransactionRunner<TResult>(asyncQueue, remoteStore, updateFunction).run();
  }

  /** Called by FirestoreClient to notify us of a new remote event. */
  @Override
  public void handleRemoteEvent(RemoteEvent event) {
    assertCallback("handleRemoteEvent");

    // Update `receivedDocument` as appropriate for any limbo targets.
    for (Map.Entry<Integer, TargetChange> entry : event.getTargetChanges().entrySet()) {
      Integer targetId = entry.getKey();
      TargetChange targetChange = entry.getValue();
      LimboResolution limboResolution = activeLimboResolutionsByTarget.get(targetId);
      if (limboResolution != null) {
        // Since this is a limbo resolution lookup, it's for a single document and it could be
        // added, modified, or removed, but not a combination.
        hardAssert(
            targetChange.getAddedDocuments().size()
                    + targetChange.getModifiedDocuments().size()
                    + targetChange.getRemovedDocuments().size()
                <= 1,
            "Limbo resolution for single document contains multiple changes.");
        if (targetChange.getAddedDocuments().size() > 0) {
          limboResolution.receivedDocument = true;
        } else if (targetChange.getModifiedDocuments().size() > 0) {
          hardAssert(
              limboResolution.receivedDocument,
              "Received change for limbo target document without add.");
        } else if (targetChange.getRemovedDocuments().size() > 0) {
          hardAssert(
              limboResolution.receivedDocument,
              "Received remove for limbo target document without add.");
          limboResolution.receivedDocument = false;
        } else {
          // This was probably just a CURRENT targetChange or similar.
        }
      }
    }

    ImmutableSortedMap<DocumentKey, MaybeDocument> changes = localStore.applyRemoteEvent(event);
    emitNewSnapsAndNotifyLocalStore(changes, event);
  }

  /** Applies an OnlineState change to the sync engine and notifies any views of the change. */
  @Override
  public void handleOnlineStateChange(OnlineState onlineState) {
    assertCallback("handleOnlineStateChange");
    ArrayList<ViewSnapshot> newViewSnapshots = new ArrayList<>();
    for (Map.Entry<Query, QueryView> entry : queryViewsByQuery.entrySet()) {
      View view = entry.getValue().getView();
      ViewChange viewChange = view.applyOnlineStateChange(onlineState);
      hardAssert(
          viewChange.getLimboChanges().isEmpty(), "OnlineState should not affect limbo documents.");
      if (viewChange.getSnapshot() != null) {
        newViewSnapshots.add(viewChange.getSnapshot());
      }
    }
    syncEngineListener.onViewSnapshots(newViewSnapshots);
    syncEngineListener.handleOnlineStateChange(onlineState);
  }

  @Override
  public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
    LimboResolution limboResolution = activeLimboResolutionsByTarget.get(targetId);
    if (limboResolution != null && limboResolution.receivedDocument) {
      return DocumentKey.emptyKeySet().insert(limboResolution.key);
    } else {
      ImmutableSortedSet<DocumentKey> remoteKeys = DocumentKey.emptyKeySet();
      if (queriesByTarget.containsKey(targetId)) {
        for (Query query : queriesByTarget.get(targetId)) {
          if (queryViewsByQuery.containsKey(query)) {
            remoteKeys =
                remoteKeys.unionWith(queryViewsByQuery.get(query).getView().getSyncedDocuments());
          }
        }
      }

      return remoteKeys;
    }
  }

  /** Called by FirestoreClient to notify us of a rejected listen. */
  @Override
  public void handleRejectedListen(int targetId, Status error) {
    assertCallback("handleRejectedListen");

    LimboResolution limboResolution = activeLimboResolutionsByTarget.get(targetId);
    DocumentKey limboKey = limboResolution != null ? limboResolution.key : null;
    if (limboKey != null) {
      // Since this query failed, we won't want to manually unlisten to it.
      // So go ahead and remove it from bookkeeping.
      activeLimboTargetsByKey.remove(limboKey);
      activeLimboResolutionsByTarget.remove(targetId);
      pumpEnqueuedLimboResolutions();

      // TODO: Retry on transient errors?

      // It's a limbo doc. Create a synthetic event saying it was deleted. This is kind of a hack.
      // Ideally, we would have a method in the local store to purge a document. However, it would
      // be tricky to keep all of the local store's invariants with another method.
      Map<DocumentKey, MaybeDocument> documentUpdates =
          Collections.singletonMap(
              limboKey,
              new NoDocument(limboKey, SnapshotVersion.NONE, /*hasCommittedMutations=*/ false));
      Set<DocumentKey> limboDocuments = Collections.singleton(limboKey);
      RemoteEvent event =
          new RemoteEvent(
              SnapshotVersion.NONE,
              /* targetChanges= */ Collections.emptyMap(),
              /* targetMismatches= */ Collections.emptySet(),
              documentUpdates,
              limboDocuments);
      handleRemoteEvent(event);
    } else {
      localStore.releaseTarget(targetId);
      removeAndCleanupTarget(targetId, error);
    }
  }

  @Override
  public void handleSuccessfulWrite(MutationBatchResult mutationBatchResult) {
    assertCallback("handleSuccessfulWrite");

    // The local store may or may not be able to apply the write result and raise events immediately
    // (depending on whether the watcher is caught up), so we raise user callbacks first so that
    // they consistently happen before listen events.
    notifyUser(mutationBatchResult.getBatch().getBatchId(), /*status=*/ null);

    resolvePendingWriteTasks(mutationBatchResult.getBatch().getBatchId());

    ImmutableSortedMap<DocumentKey, MaybeDocument> changes =
        localStore.acknowledgeBatch(mutationBatchResult);

    emitNewSnapsAndNotifyLocalStore(changes, /*remoteEvent=*/ null);
  }

  @Override
  public void handleRejectedWrite(int batchId, Status status) {
    assertCallback("handleRejectedWrite");
    ImmutableSortedMap<DocumentKey, MaybeDocument> changes = localStore.rejectBatch(batchId);

    if (!changes.isEmpty()) {
      logErrorIfInteresting(status, "Write failed at %s", changes.getMinKey().getPath());
    }

    // The local store may or may not be able to apply the write result and raise events immediately
    // (depending on whether the watcher is caught up), so we raise user callbacks first so that
    // they consistently happen before listen events.
    notifyUser(batchId, status);

    resolvePendingWriteTasks(batchId);

    emitNewSnapsAndNotifyLocalStore(changes, /*remoteEvent=*/ null);
  }

  /**
   * Takes a snapshot of current mutation queue, and register a user task which will resolve when
   * all those mutations are either accepted or rejected by the server.
   */
  public void registerPendingWritesTask(TaskCompletionSource<Void> userTask) {
    if (!remoteStore.canUseNetwork()) {
      Logger.debug(
          TAG,
          "The network is disabled. The task returned by 'awaitPendingWrites()' will not "
              + "complete until the network is enabled.");
    }

    int largestPendingBatchId = localStore.getHighestUnacknowledgedBatchId();

    if (largestPendingBatchId == MutationBatch.UNKNOWN) {
      // Complete the task right away if there is no pending writes at the moment.
      userTask.setResult(null);
      return;
    }

    if (!pendingWritesCallbacks.containsKey(largestPendingBatchId)) {
      pendingWritesCallbacks.put(largestPendingBatchId, new ArrayList());
    }

    pendingWritesCallbacks.get(largestPendingBatchId).add(userTask);
  }

  /** Resolves tasks waiting for this batch id to get acknowledged by server, if there are any. */
  private void resolvePendingWriteTasks(int batchId) {
    if (pendingWritesCallbacks.containsKey(batchId)) {
      for (TaskCompletionSource<Void> task : pendingWritesCallbacks.get(batchId)) {
        task.setResult(null);
      }

      pendingWritesCallbacks.remove(batchId);
    }
  }

  private void failOutstandingPendingWritesAwaitingTasks() {
    for (Map.Entry<Integer, List<TaskCompletionSource<Void>>> entry :
        pendingWritesCallbacks.entrySet()) {
      for (TaskCompletionSource<Void> task : entry.getValue()) {
        task.setException(
            new FirebaseFirestoreException(
                "'waitForPendingWrites' task is cancelled due to User change.",
                FirebaseFirestoreException.Code.CANCELLED));
      }
    }

    pendingWritesCallbacks.clear();
  }

  /** Resolves the task corresponding to this write result. */
  private void notifyUser(int batchId, @Nullable Status status) {
    Map<Integer, TaskCompletionSource<Void>> userTasks = mutationUserCallbacks.get(currentUser);

    // NOTE: Mutations restored from persistence won't have task completion sources, so it's okay
    // for this (or the task below) to be null.
    if (userTasks != null) {
      Integer boxedBatchId = batchId;
      TaskCompletionSource<Void> userTask = userTasks.get(boxedBatchId);
      if (userTask != null) {
        if (status != null) {
          userTask.setException(Util.exceptionFromStatus(status));
        } else {
          userTask.setResult(null);
        }
        userTasks.remove(boxedBatchId);
      }
    }
  }

  private void removeAndCleanupTarget(int targetId, Status status) {
    for (Query query : queriesByTarget.get(targetId)) {
      queryViewsByQuery.remove(query);
      if (!status.isOk()) {
        syncEngineListener.onError(query, status);
        logErrorIfInteresting(status, "Listen for %s failed", query);
      }
    }
    queriesByTarget.remove(targetId);

    ImmutableSortedSet<DocumentKey> limboKeys = limboDocumentRefs.referencesForId(targetId);
    limboDocumentRefs.removeReferencesForId(targetId);
    for (DocumentKey key : limboKeys) {
      if (!limboDocumentRefs.containsKey(key)) {
        // We removed the last reference for this key.
        removeLimboTarget(key);
      }
    }
  }

  private void removeLimboTarget(DocumentKey key) {
    // It's possible that the target already got removed because the query failed. In that case,
    // the key won't exist in `limboTargetsByKey`. Only do the cleanup if we still have the target.
    Integer targetId = activeLimboTargetsByKey.get(key);
    if (targetId != null) {
      remoteStore.stopListening(targetId);
      activeLimboTargetsByKey.remove(key);
      activeLimboResolutionsByTarget.remove(targetId);
      pumpEnqueuedLimboResolutions();
    }
  }

  /**
   * Computes a new snapshot from the changes and calls the registered callback with the new
   * snapshot.
   */
  private void emitNewSnapsAndNotifyLocalStore(
      ImmutableSortedMap<DocumentKey, MaybeDocument> changes, @Nullable RemoteEvent remoteEvent) {
    List<ViewSnapshot> newSnapshots = new ArrayList<>();
    List<LocalViewChanges> documentChangesInAllViews = new ArrayList<>();

    for (Map.Entry<Query, QueryView> entry : queryViewsByQuery.entrySet()) {
      QueryView queryView = entry.getValue();
      View view = queryView.getView();
      View.DocumentChanges viewDocChanges = view.computeDocChanges(changes);
      if (viewDocChanges.needsRefill()) {
        // The query has a limit and some docs were removed/updated, so we need to re-run the query
        // against the local store to make sure we didn't lose any good docs that had been past the
        // limit.
        QueryResult queryResult =
            localStore.executeQuery(queryView.getQuery(), /* usePreviousResults= */ false);
        viewDocChanges = view.computeDocChanges(queryResult.getDocuments(), viewDocChanges);
      }
      TargetChange targetChange =
          remoteEvent == null ? null : remoteEvent.getTargetChanges().get(queryView.getTargetId());
      ViewChange viewChange = queryView.getView().applyChanges(viewDocChanges, targetChange);
      updateTrackedLimboDocuments(viewChange.getLimboChanges(), queryView.getTargetId());

      if (viewChange.getSnapshot() != null) {
        newSnapshots.add(viewChange.getSnapshot());
        LocalViewChanges docChanges =
            LocalViewChanges.fromViewSnapshot(queryView.getTargetId(), viewChange.getSnapshot());
        documentChangesInAllViews.add(docChanges);
      }
    }
    syncEngineListener.onViewSnapshots(newSnapshots);
    localStore.notifyLocalViewChanges(documentChangesInAllViews);
  }

  /** Updates the limbo document state for the given targetId. */
  private void updateTrackedLimboDocuments(List<LimboDocumentChange> limboChanges, int targetId) {
    for (LimboDocumentChange limboChange : limboChanges) {
      switch (limboChange.getType()) {
        case ADDED:
          limboDocumentRefs.addReference(limboChange.getKey(), targetId);
          trackLimboChange(limboChange);
          break;
        case REMOVED:
          Logger.debug(TAG, "Document no longer in limbo: %s", limboChange.getKey());
          DocumentKey limboDocKey = limboChange.getKey();
          limboDocumentRefs.removeReference(limboDocKey, targetId);
          if (!limboDocumentRefs.containsKey(limboDocKey)) {
            // We removed the last reference for this key
            removeLimboTarget(limboDocKey);
          }
          break;
        default:
          throw fail("Unknown limbo change type: %s", limboChange.getType());
      }
    }
  }

  private void trackLimboChange(LimboDocumentChange change) {
    DocumentKey key = change.getKey();
    if (!activeLimboTargetsByKey.containsKey(key)) {
      Logger.debug(TAG, "New document in limbo: %s", key);
      enqueuedLimboResolutions.add(key);
      pumpEnqueuedLimboResolutions();
    }
  }

  /**
   * Starts listens for documents in limbo that are enqueued for resolution, subject to a maximum
   * number of concurrent resolutions.
   *
   * <p>Without bounding the number of concurrent resolutions, the server can fail with "resource
   * exhausted" errors which can lead to pathological client behavior as seen in
   * https://github.com/firebase/firebase-js-sdk/issues/2683.
   */
  private void pumpEnqueuedLimboResolutions() {
    while (!enqueuedLimboResolutions.isEmpty()
        && activeLimboTargetsByKey.size() < maxConcurrentLimboResolutions) {
      DocumentKey key = enqueuedLimboResolutions.remove();
      int limboTargetId = targetIdGenerator.nextId();
      activeLimboResolutionsByTarget.put(limboTargetId, new LimboResolution(key));
      activeLimboTargetsByKey.put(key, limboTargetId);
      remoteStore.listen(
          new TargetData(
              Query.atPath(key.getPath()).toTarget(),
              limboTargetId,
              ListenSequence.INVALID,
              QueryPurpose.LIMBO_RESOLUTION));
    }
  }

  @VisibleForTesting
  public Map<DocumentKey, Integer> getActiveLimboDocumentResolutions() {
    // Make a defensive copy as the Map continues to be modified.
    return new HashMap<>(activeLimboTargetsByKey);
  }

  @VisibleForTesting
  public Queue<DocumentKey> getEnqueuedLimboDocumentResolutions() {
    // Make a defensive copy as the Queue continues to be modified.
    return new ArrayDeque<>(enqueuedLimboResolutions);
  }

  public void handleCredentialChange(User user) {
    boolean userChanged = !currentUser.equals(user);
    currentUser = user;

    if (userChanged) {
      // Fails tasks waiting for pending writes requested by previous user.
      failOutstandingPendingWritesAwaitingTasks();
      // Notify local store and emit any resulting events from swapping out the mutation queue.
      ImmutableSortedMap<DocumentKey, MaybeDocument> changes = localStore.handleUserChange(user);
      emitNewSnapsAndNotifyLocalStore(changes, /*remoteEvent=*/ null);
    }

    // Notify remote store so it can restart its streams.
    remoteStore.handleCredentialChange();
  }

  /**
   * Logs the error as a warnings if it likely represents a developer mistake such as forgetting to
   * create an index or permission denied.
   */
  private void logErrorIfInteresting(Status error, String contextString, Object... contextArgs) {
    if (errorIsInteresting(error)) {
      String context = String.format(contextString, contextArgs);
      Logger.warn("Firestore", "%s: %s", context, error);
    }
  }

  private boolean errorIsInteresting(Status error) {
    Status.Code code = error.getCode();
    String description = error.getDescription() != null ? error.getDescription() : "";

    if (code == Status.Code.FAILED_PRECONDITION && description.contains("requires an index")) {
      return true;
    } else if (code == Status.Code.PERMISSION_DENIED) {
      return true;
    }

    return false;
  }
}
