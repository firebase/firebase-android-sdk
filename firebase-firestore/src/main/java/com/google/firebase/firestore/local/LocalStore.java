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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.hardAssert;
import static java.util.Arrays.asList;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.core.TargetIdGenerator;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.MutationBatchResult;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.remote.RemoteEvent;
import com.google.firebase.firestore.remote.TargetChange;
import com.google.firebase.firestore.util.Logger;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Local storage in the Firestore client. Coordinates persistence components like the mutation queue
 * and remote document cache to present a latency compensated view of stored data.
 *
 * <p>The LocalStore is responsible for accepting mutations from the Sync Engine. Writes from the
 * client are put into a queue as provisional Mutations until they are processed by the RemoteStore
 * and confirmed as having been written to the server.
 *
 * <p>The local store provides the local version of documents that have been modified locally. It
 * maintains the constraint:
 *
 * <p>LocalDocument = RemoteDocument + Active(LocalMutations)
 *
 * <p>(Active mutations are those that are enqueued and have not been previously acknowledged or
 * rejected).
 *
 * <p>The RemoteDocument ("ground truth") state is provided via the applyChangeBatch method. It will
 * be some version of a server-provided document OR will be a server-provided document PLUS
 * acknowledged mutations:
 *
 * <p>RemoteDocument' = RemoteDocument + Acknowledged(LocalMutations)
 *
 * <p>Note that this "dirty" version of a RemoteDocument will not be identical to a server base
 * version, since it has LocalMutations added to it pending getting an authoritative copy from the
 * server.
 *
 * <p>Since LocalMutations can be rejected by the server, we have to be able to revert a
 * LocalMutation that has already been applied to the LocalDocument (typically done by replaying all
 * remaining LocalMutations to the RemoteDocument to re-apply).
 *
 * <p>The LocalStore is responsible for the garbage collection of the documents it contains. For
 * now, it every doc referenced by a view, the mutation queue, or the RemoteStore.
 *
 * <p>It also maintains the persistence of mapping queries to resume tokens and target ids. It needs
 * to know this data about queries to properly know what docs it would be allowed to garbage
 * collect.
 *
 * <p>The LocalStore must be able to efficiently execute queries against its local cache of the
 * documents, to provide the initial set of results before any remote changes have been received.
 */
public final class LocalStore {
  /**
   * The maximum time to leave a resume token buffered without writing it out. This value is
   * arbitrary: it's long enough to avoid several writes (possibly indefinitely if updates come more
   * frequently than this) but short enough that restarting after crashing will still have a pretty
   * recent resume token.
   */
  private static final long RESUME_TOKEN_MAX_AGE_SECONDS = TimeUnit.MINUTES.toSeconds(5);

  /** Manages our in-memory or durable persistence. */
  private final Persistence persistence;

  /** The set of all mutations that have been sent but not yet been applied to the backend. */
  private MutationQueue mutationQueue;

  /** The last known state of all referenced documents according to the backend. */
  private final RemoteDocumentCache remoteDocuments;

  /** The current state of all referenced documents, reflecting local changes. */
  private LocalDocumentsView localDocuments;

  /** Performs queries over the localDocuments (and potentially maintains indexes). */
  private QueryEngine queryEngine;

  /** The set of document references maintained by any local views. */
  private final ReferenceSet localViewReferences;

  /** Maps a query to the data about that query. */
  private final TargetCache targetCache;

  /** Maps a targetId to data about its query. */
  private final SparseArray<TargetData> queryDataByTarget;

  /** Maps a target to its targetID. */
  private final Map<Target, Integer> targetIdByTarget;

  /** Used to generate targetIds for queries tracked locally. */
  private final TargetIdGenerator targetIdGenerator;

  public LocalStore(Persistence persistence, QueryEngine queryEngine, User initialUser) {
    hardAssert(
        persistence.isStarted(), "LocalStore was passed an unstarted persistence implementation");
    this.persistence = persistence;
    targetCache = persistence.getTargetCache();
    targetIdGenerator = TargetIdGenerator.forTargetCache(targetCache.getHighestTargetId());
    mutationQueue = persistence.getMutationQueue(initialUser);
    remoteDocuments = persistence.getRemoteDocumentCache();
    localDocuments =
        new LocalDocumentsView(remoteDocuments, mutationQueue, persistence.getIndexManager());

    this.queryEngine = queryEngine;
    queryEngine.setLocalDocumentsView(localDocuments);

    localViewReferences = new ReferenceSet();
    persistence.getReferenceDelegate().setInMemoryPins(localViewReferences);

    queryDataByTarget = new SparseArray<>();
    targetIdByTarget = new HashMap<>();
  }

  public void start() {
    startMutationQueue();
  }

  private void startMutationQueue() {
    persistence.runTransaction(
        "Start MutationQueue",
        () -> {
          mutationQueue.start();
        });
  }

  // PORTING NOTE: no shutdown for LocalStore or persistence components on Android.

  public ImmutableSortedMap<DocumentKey, MaybeDocument> handleUserChange(User user) {
    // Swap out the mutation queue, grabbing the pending mutation batches before and after.
    List<MutationBatch> oldBatches = mutationQueue.getAllMutationBatches();

    mutationQueue = persistence.getMutationQueue(user);
    startMutationQueue();

    List<MutationBatch> newBatches = mutationQueue.getAllMutationBatches();

    // Recreate our LocalDocumentsView using the new MutationQueue.
    localDocuments =
        new LocalDocumentsView(remoteDocuments, mutationQueue, persistence.getIndexManager());
    queryEngine.setLocalDocumentsView(localDocuments);

    // Union the old/new changed keys.
    ImmutableSortedSet<DocumentKey> changedKeys = DocumentKey.emptyKeySet();
    for (List<MutationBatch> batches : asList(oldBatches, newBatches)) {
      for (MutationBatch batch : batches) {
        for (Mutation mutation : batch.getMutations()) {
          changedKeys = changedKeys.insert(mutation.getKey());
        }
      }
    }

    // Return the set of all (potentially) changed documents as the result of the user change.
    return localDocuments.getDocuments(changedKeys);
  }

  /** Accepts locally generated Mutations and commits them to storage. */
  public LocalWriteResult writeLocally(List<Mutation> mutations) {
    Timestamp localWriteTime = Timestamp.now();

    // TODO: Call queryEngine.handleDocumentChange() appropriately.

    Set<DocumentKey> keys = new HashSet<>();
    for (Mutation mutation : mutations) {
      keys.add(mutation.getKey());
    }

    return persistence.runTransaction(
        "Locally write mutations",
        () -> {
          // Load and apply all existing mutations. This lets us compute the current base state for
          // all non-idempotent transforms before applying any additional user-provided writes.
          ImmutableSortedMap<DocumentKey, MaybeDocument> existingDocuments =
              localDocuments.getDocuments(keys);

          // For non-idempotent mutations (such as `FieldValue.increment()`), we record the base
          // state in a separate patch mutation. This is later used to guarantee consistent values
          // and prevents flicker even if the backend sends us an update that already includes our
          // transform.
          List<Mutation> baseMutations = new ArrayList<>();
          for (Mutation mutation : mutations) {
            ObjectValue baseValue =
                mutation.extractBaseValue(existingDocuments.get(mutation.getKey()));
            if (baseValue != null) {
              // NOTE: The base state should only be applied if there's some existing
              // document to override, so use a Precondition of exists=true
              baseMutations.add(
                  new PatchMutation(
                      mutation.getKey(),
                      baseValue,
                      baseValue.getFieldMask(),
                      Precondition.exists(true)));
            }
          }

          MutationBatch batch =
              mutationQueue.addMutationBatch(localWriteTime, baseMutations, mutations);
          ImmutableSortedMap<DocumentKey, MaybeDocument> changedDocuments =
              batch.applyToLocalDocumentSet(existingDocuments);
          return new LocalWriteResult(batch.getBatchId(), changedDocuments);
        });
  }

  /**
   * Acknowledges the given batch.
   *
   * <p>On the happy path when a batch is acknowledged, the local store will
   *
   * <ul>
   *   <li>remove the batch from the mutation queue;
   *   <li>apply the changes to the remote document cache;
   *   <li>recalculate the latency compensated view implied by those changes (there may be mutations
   *       in the queue that affect the documents but haven't been acknowledged yet); and
   *   <li>give the changed documents back the sync engine
   * </ul>
   *
   * @return The resulting (modified) documents.
   */
  public ImmutableSortedMap<DocumentKey, MaybeDocument> acknowledgeBatch(
      MutationBatchResult batchResult) {
    return persistence.runTransaction(
        "Acknowledge batch",
        () -> {
          MutationBatch batch = batchResult.getBatch();
          mutationQueue.acknowledgeBatch(batch, batchResult.getStreamToken());
          applyWriteToRemoteDocuments(batchResult);
          mutationQueue.performConsistencyCheck();
          return localDocuments.getDocuments(batch.getKeys());
        });
  }

  /**
   * Removes mutations from the MutationQueue for the specified batch. LocalDocuments will be
   * recalculated.
   *
   * @return The resulting (modified) documents.
   */
  public ImmutableSortedMap<DocumentKey, MaybeDocument> rejectBatch(int batchId) {
    // TODO: Call queryEngine.handleDocumentChange() appropriately.

    return persistence.runTransaction(
        "Reject batch",
        () -> {
          MutationBatch toReject = mutationQueue.lookupMutationBatch(batchId);
          hardAssert(toReject != null, "Attempt to reject nonexistent batch!");

          mutationQueue.removeMutationBatch(toReject);
          mutationQueue.performConsistencyCheck();
          return localDocuments.getDocuments(toReject.getKeys());
        });
  }

  /**
   * Returns the largest (latest) batch id in mutation queue that is pending server response.
   * Returns {@link MutationBatch#UNKNOWN} if the queue is empty.
   */
  public int getHighestUnacknowledgedBatchId() {
    return mutationQueue.getHighestUnacknowledgedBatchId();
  }

  /** Returns the last recorded stream token for the current user. */
  public ByteString getLastStreamToken() {
    return mutationQueue.getLastStreamToken();
  }

  /**
   * Sets the stream token for the current user without acknowledging any mutation batch. This is
   * usually only useful after a stream handshake or in response to an error that requires clearing
   * the stream token.
   *
   * @param streamToken The streamToken to record. Use {@code WriteStream.EMPTY_STREAM_TOKEN} to
   *     clear the current value.
   */
  public void setLastStreamToken(ByteString streamToken) {
    persistence.runTransaction(
        "Set stream token", () -> mutationQueue.setLastStreamToken(streamToken));
  }

  /**
   * Returns the last consistent snapshot processed (used by the RemoteStore to determine whether to
   * buffer incoming snapshots from the backend).
   */
  public SnapshotVersion getLastRemoteSnapshotVersion() {
    return targetCache.getLastRemoteSnapshotVersion();
  }

  /**
   * Updates the "ground-state" (remote) documents. We assume that the remote event reflects any
   * write batches that have been acknowledged or rejected (i.e. we do not re-apply local mutations
   * to updates from this event).
   *
   * <p>LocalDocuments are re-calculated if there are remaining mutations in the queue.
   */
  public ImmutableSortedMap<DocumentKey, MaybeDocument> applyRemoteEvent(RemoteEvent remoteEvent) {
    SnapshotVersion remoteVersion = remoteEvent.getSnapshotVersion();

    // TODO: Call queryEngine.handleDocumentChange() appropriately.
    return persistence.runTransaction(
        "Apply remote event",
        () -> {
          Map<Integer, TargetChange> targetChanges = remoteEvent.getTargetChanges();
          long sequenceNumber = persistence.getReferenceDelegate().getCurrentSequenceNumber();

          for (Map.Entry<Integer, TargetChange> entry : targetChanges.entrySet()) {
            Integer boxedTargetId = entry.getKey();
            int targetId = boxedTargetId;
            TargetChange change = entry.getValue();

            TargetData oldTargetData = queryDataByTarget.get(targetId);
            if (oldTargetData == null) {
              // We don't update the remote keys if the query is not active. This ensures that
              // we persist the updated query data along with the updated assignment.
              continue;
            }

            targetCache.removeMatchingKeys(change.getRemovedDocuments(), targetId);
            targetCache.addMatchingKeys(change.getAddedDocuments(), targetId);

            ByteString resumeToken = change.getResumeToken();
            // Update the resume token if the change includes one.
            if (!resumeToken.isEmpty()) {
              TargetData newTargetData =
                  oldTargetData
                      .withResumeToken(resumeToken, remoteEvent.getSnapshotVersion())
                      .withSequenceNumber(sequenceNumber);
              queryDataByTarget.put(targetId, newTargetData);

              // Update the query data if there are target changes (or if sufficient time has
              // passed since the last update).
              if (shouldPersistTargetData(oldTargetData, newTargetData, change)) {
                targetCache.updateTargetData(newTargetData);
              }
            }
          }

          Map<DocumentKey, MaybeDocument> changedDocs = new HashMap<>();
          Map<DocumentKey, MaybeDocument> documentUpdates = remoteEvent.getDocumentUpdates();
          Set<DocumentKey> limboDocuments = remoteEvent.getResolvedLimboDocuments();
          // Each loop iteration only affects its "own" doc, so it's safe to get all the remote
          // documents in advance in a single call.
          Map<DocumentKey, MaybeDocument> existingDocs =
              remoteDocuments.getAll(documentUpdates.keySet());

          for (Entry<DocumentKey, MaybeDocument> entry : documentUpdates.entrySet()) {
            DocumentKey key = entry.getKey();
            MaybeDocument doc = entry.getValue();
            MaybeDocument existingDoc = existingDocs.get(key);

            // Note: The order of the steps below is important, since we want to ensure that
            // rejected limbo resolutions (which fabricate NoDocuments with SnapshotVersion.NONE)
            // never add documents to cache.
            if (doc instanceof NoDocument && doc.getVersion().equals(SnapshotVersion.NONE)) {
              // NoDocuments with SnapshotVersion.NONE are used in manufactured events. We remove
              // these documents from cache since we lost access.
              remoteDocuments.remove(doc.getKey());
              changedDocs.put(key, doc);
            } else if (existingDoc == null
                || doc.getVersion().compareTo(existingDoc.getVersion()) > 0
                || (doc.getVersion().compareTo(existingDoc.getVersion()) == 0
                    && existingDoc.hasPendingWrites())) {
              hardAssert(
                  !SnapshotVersion.NONE.equals(remoteEvent.getSnapshotVersion()),
                  "Cannot add a document when the remote version is zero");
              remoteDocuments.add(doc, remoteEvent.getSnapshotVersion());
              changedDocs.put(key, doc);
            } else {
              Logger.debug(
                  "LocalStore",
                  "Ignoring outdated watch update for %s."
                      + "Current version: %s  Watch version: %s",
                  key,
                  existingDoc.getVersion(),
                  doc.getVersion());
            }

            if (limboDocuments.contains(key)) {
              persistence.getReferenceDelegate().updateLimboDocument(key);
            }
          }

          // HACK: The only reason we allow snapshot version NONE is so that we can synthesize
          // remote events when we get permission denied errors while trying to resolve the
          // state of a locally cached document that is in limbo.
          SnapshotVersion lastRemoteVersion = targetCache.getLastRemoteSnapshotVersion();
          if (!remoteVersion.equals(SnapshotVersion.NONE)) {
            hardAssert(
                remoteVersion.compareTo(lastRemoteVersion) >= 0,
                "Watch stream reverted to previous snapshot?? (%s < %s)",
                remoteVersion,
                lastRemoteVersion);
            targetCache.setLastRemoteSnapshotVersion(remoteVersion);
          }

          return localDocuments.getLocalViewOfDocuments(changedDocs);
        });
  }

  /**
   * Returns true if the newTargetData should be persisted during an update of an active target.
   * TargetData should always be persisted when a target is being released and should not call this
   * function.
   *
   * <p>While the target is active, TargetData updates can be omitted when nothing about the target
   * has changed except metadata like the resume token or snapshot version. Occasionally it's worth
   * the extra write to prevent these values from getting too stale after a crash, but this doesn't
   * have to be too frequent.
   */
  private static boolean shouldPersistTargetData(
      TargetData oldTargetData, TargetData newTargetData, TargetChange change) {
    hardAssert(
        !newTargetData.getResumeToken().isEmpty(),
        "Attempted to persist query data with empty resume token");

    // Always persist query data if we don't already have a resume token.
    if (oldTargetData.getResumeToken().isEmpty()) return true;

    // Don't allow resume token changes to be buffered indefinitely. This allows us to be reasonably
    // up-to-date after a crash and avoids needing to loop over all active queries on shutdown.
    // Especially in the browser we may not get time to do anything interesting while the current
    // tab is closing.
    long newSeconds = newTargetData.getSnapshotVersion().getTimestamp().getSeconds();
    long oldSeconds = oldTargetData.getSnapshotVersion().getTimestamp().getSeconds();
    long timeDelta = newSeconds - oldSeconds;
    if (timeDelta >= RESUME_TOKEN_MAX_AGE_SECONDS) return true;

    // Otherwise if the only thing that has changed about a target is its resume token it's not
    // worth persisting. Note that the RemoteStore keeps an in-memory view of the currently active
    // targets which includes the current resume token, so stream failure or user changes will still
    // use an up-to-date resume token regardless of what we do here.
    int changes =
        change.getAddedDocuments().size()
            + change.getModifiedDocuments().size()
            + change.getRemovedDocuments().size();
    return changes > 0;
  }

  /** Notify the local store of the changed views to locally pin / unpin documents. */
  public void notifyLocalViewChanges(List<LocalViewChanges> viewChanges) {
    persistence.runTransaction(
        "notifyLocalViewChanges",
        () -> {
          for (LocalViewChanges viewChange : viewChanges) {
            int targetId = viewChange.getTargetId();

            localViewReferences.addReferences(viewChange.getAdded(), targetId);
            ImmutableSortedSet<DocumentKey> removed = viewChange.getRemoved();
            for (DocumentKey key : removed) {
              persistence.getReferenceDelegate().removeReference(key);
            }
            localViewReferences.removeReferences(removed, targetId);

            if (!viewChange.isFromCache()) {
              TargetData targetData = queryDataByTarget.get(targetId);
              hardAssert(
                  targetData != null,
                  "Can't set limbo-free snapshot version for unknown target: %s",
                  targetId);

              // Advance the last limbo free snapshot version
              SnapshotVersion lastLimboFreeSnapshotVersion = targetData.getSnapshotVersion();
              TargetData updatedTargetData =
                  targetData.withLastLimboFreeSnapshotVersion(lastLimboFreeSnapshotVersion);
              queryDataByTarget.put(targetId, updatedTargetData);
            }
          }
        });
  }

  /**
   * Returns the mutation batch after the passed in batchId in the mutation queue or null if empty.
   *
   * @param afterBatchId The batch to search after, or -1 for the first mutation in the queue.
   * @return The next mutation or null if there wasn't one.
   */
  public @Nullable MutationBatch getNextMutationBatch(int afterBatchId) {
    return mutationQueue.getNextMutationBatchAfterBatchId(afterBatchId);
  }

  /** Returns the current value of a document with a given key, or null if not found. */
  @Nullable
  public MaybeDocument readDocument(DocumentKey key) {
    return localDocuments.getDocument(key);
  }

  /**
   * Assigns the given target an internal ID so that its results can be pinned so they don't get
   * GC'd. A query must be allocated in the local store before the store can be used to manage its
   * view.
   *
   * <p>Allocating an already allocated target will return the existing @{code TargetData} for that
   * target.
   */
  public TargetData allocateTarget(Target target) {
    int targetId;
    TargetData cached = targetCache.getTargetData(target);
    if (cached != null) {
      // This query has been listened to previously, so reuse the previous targetID.
      // TODO: freshen last accessed date?
      targetId = cached.getTargetId();
    } else {
      final AllocateQueryHolder holder = new AllocateQueryHolder();
      persistence.runTransaction(
          "Allocate target",
          () -> {
            holder.targetId = targetIdGenerator.nextId();
            holder.cached =
                new TargetData(
                    target,
                    holder.targetId,
                    persistence.getReferenceDelegate().getCurrentSequenceNumber(),
                    QueryPurpose.LISTEN);
            targetCache.addTargetData(holder.cached);
          });
      targetId = holder.targetId;
      cached = holder.cached;
    }

    if (queryDataByTarget.get(targetId) == null) {
      queryDataByTarget.put(targetId, cached);
      targetIdByTarget.put(target, targetId);
    }
    return cached;
  }

  /**
   * Returns the TargetData as seen by the LocalStore, including updates that may have not yet been
   * persisted to the TargetCache.
   */
  @VisibleForTesting
  @Nullable
  TargetData getTargetData(Target target) {
    Integer targetId = targetIdByTarget.get(target);
    if (targetId != null) {
      return queryDataByTarget.get(targetId);
    }
    return targetCache.getTargetData(target);
  }

  /** Mutable state for the transaction in allocateQuery. */
  private static class AllocateQueryHolder {
    TargetData cached;
    int targetId;
  }

  /**
   * Unpin all the documents associated with the given target.
   *
   * <p>Releasing a non-existing target is an error.
   */
  public void releaseTarget(int targetId) {
    persistence.runTransaction(
        "Release target",
        () -> {
          TargetData targetData = queryDataByTarget.get(targetId);
          hardAssert(targetData != null, "Tried to release nonexistent target: %s", targetId);

          // References for documents sent via Watch are automatically removed when we delete a
          // query's target data from the reference delegate. Since this does not remove references
          // for locally mutated documents, we have to remove the target associations for these
          // documents manually.
          ImmutableSortedSet<DocumentKey> removedReferences =
              localViewReferences.removeReferencesForId(targetId);
          for (DocumentKey key : removedReferences) {
            persistence.getReferenceDelegate().removeReference(key);
          }

          // Note: This also updates the query cache
          persistence.getReferenceDelegate().removeTarget(targetData);
          queryDataByTarget.remove(targetId);
          targetIdByTarget.remove(targetData.getTarget());
        });
  }

  /**
   * Runs the specified query against the local store and returns the results, potentially taking
   * advantage of query data from previous executions (such as the set of remote keys).
   *
   * @param usePreviousResults Whether results from previous executions can be used to optimize this
   *     query execution.
   */
  public QueryResult executeQuery(Query query, boolean usePreviousResults) {
    TargetData targetData = getTargetData(query.toTarget());
    SnapshotVersion lastLimboFreeSnapshotVersion = SnapshotVersion.NONE;
    ImmutableSortedSet<DocumentKey> remoteKeys = DocumentKey.emptyKeySet();

    if (targetData != null) {
      lastLimboFreeSnapshotVersion = targetData.getLastLimboFreeSnapshotVersion();
      remoteKeys = this.targetCache.getMatchingKeysForTargetId(targetData.getTargetId());
    }

    ImmutableSortedMap<DocumentKey, Document> documents =
        queryEngine.getDocumentsMatchingQuery(
            query,
            usePreviousResults ? lastLimboFreeSnapshotVersion : SnapshotVersion.NONE,
            usePreviousResults ? remoteKeys : DocumentKey.emptyKeySet());
    return new QueryResult(documents, remoteKeys);
  }

  /**
   * Returns the keys of the documents that are associated with the given target id in the remote
   * table.
   */
  public ImmutableSortedSet<DocumentKey> getRemoteDocumentKeys(int targetId) {
    return targetCache.getMatchingKeysForTargetId(targetId);
  }

  private void applyWriteToRemoteDocuments(MutationBatchResult batchResult) {
    MutationBatch batch = batchResult.getBatch();
    Set<DocumentKey> docKeys = batch.getKeys();
    for (DocumentKey docKey : docKeys) {
      MaybeDocument remoteDoc = remoteDocuments.get(docKey);
      MaybeDocument doc = remoteDoc;
      SnapshotVersion ackVersion = batchResult.getDocVersions().get(docKey);
      hardAssert(ackVersion != null, "docVersions should contain every doc in the write.");

      if (doc == null || doc.getVersion().compareTo(ackVersion) < 0) {
        doc = batch.applyToRemoteDocument(docKey, doc, batchResult);
        if (doc == null) {
          hardAssert(
              remoteDoc == null,
              "Mutation batch %s applied to document %s resulted in null.",
              batch,
              remoteDoc);
        } else {
          remoteDocuments.add(doc, batchResult.getCommitVersion());
        }
      }
    }

    mutationQueue.removeMutationBatch(batch);
  }

  public LruGarbageCollector.Results collectGarbage(LruGarbageCollector garbageCollector) {
    return persistence.runTransaction(
        "Collect garbage", () -> garbageCollector.collect(queryDataByTarget));
  }
}
