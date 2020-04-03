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

package com.google.firebase.firestore.remote;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.DocumentViewChange;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.remote.WatchChange.DocumentChange;
import com.google.firebase.firestore.remote.WatchChange.ExistenceFilterWatchChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChange;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A helper class to accumulate watch changes into a RemoteEvent and other target information. */
public class WatchChangeAggregator {
  /**
   * Interface implemented by RemoteStore to expose target metadata to the WatchChangeAggregator.
   */
  public interface TargetMetadataProvider {
    /**
     * Returns the set of remote document keys for the given target ID as of the last raised
     * snapshot or an empty set of document keys for unknown targets.
     */
    ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId);

    /**
     * Returns the TargetData for an active target ID or 'null' if this query is unknown or has
     * become inactive.
     */
    @Nullable
    TargetData getTargetDataForTarget(int targetId);
  }

  private final TargetMetadataProvider targetMetadataProvider;

  /** The internal state of all tracked targets. */
  private final Map<Integer, TargetState> targetStates = new HashMap<>();

  /** Keeps track of the documents to update since the last raised snapshot. */
  private Map<DocumentKey, MaybeDocument> pendingDocumentUpdates = new HashMap<>();

  /** A mapping of document keys to their set of target IDs. */
  private Map<DocumentKey, Set<Integer>> pendingDocumentTargetMapping = new HashMap<>();

  /**
   * A list of targets with existence filter mismatches. These targets are known to be inconsistent
   * and their listens needs to be re-established by RemoteStore.
   */
  private Set<Integer> pendingTargetResets = new HashSet<>();

  public WatchChangeAggregator(TargetMetadataProvider targetMetadataProvider) {
    this.targetMetadataProvider = targetMetadataProvider;
  }

  /** Processes and adds the DocumentWatchChange to the current set of changes. */
  public void handleDocumentChange(DocumentChange documentChange) {
    MaybeDocument document = documentChange.getNewDocument();
    DocumentKey documentKey = documentChange.getDocumentKey();

    for (int targetId : documentChange.getUpdatedTargetIds()) {
      if (document instanceof Document) {
        addDocumentToTarget(targetId, document);
      } else if (document instanceof NoDocument) {
        removeDocumentFromTarget(targetId, documentKey, document);
      }
    }

    for (int targetId : documentChange.getRemovedTargetIds()) {
      removeDocumentFromTarget(targetId, documentKey, documentChange.getNewDocument());
    }
  }

  /** Processes and adds the WatchTargetChange to the current set of changes. */
  public void handleTargetChange(WatchTargetChange targetChange) {
    for (int targetId : getTargetIds(targetChange)) {
      TargetState targetState = ensureTargetState(targetId);

      switch (targetChange.getChangeType()) {
        case NoChange:
          if (isActiveTarget(targetId)) {
            targetState.updateResumeToken(targetChange.getResumeToken());
          }
          break;
        case Added:
          // We need to decrement the number of pending acks needed from watch for this targetId.
          targetState.recordTargetResponse();
          if (!targetState.isPending()) {
            // We have a freshly added target, so we need to reset any state that we had previously.
            // This can happen e.g. when remove and add back a target for existence filter
            // mismatches.
            targetState.clearChanges();
          }
          targetState.updateResumeToken(targetChange.getResumeToken());
          break;
        case Removed:
          // We need to keep track of removed targets to we can post-filter and remove any target
          // changes.
          // We need to decrement the number of pending acks needed from watch for this targetId.
          targetState.recordTargetResponse();
          if (!targetState.isPending()) {
            removeTarget(targetId);
          }
          hardAssert(
              targetChange.getCause() == null,
              "WatchChangeAggregator does not handle errored targets");
          break;
        case Current:
          if (isActiveTarget(targetId)) {
            targetState.markCurrent();
            targetState.updateResumeToken(targetChange.getResumeToken());
          }
          break;
        case Reset:
          if (isActiveTarget(targetId)) {
            // Reset the target and synthesizes removes for all existing documents. The backend will
            // re-add any documents that still match the target before it sends the next global
            // snapshot.
            resetTarget(targetId);
            targetState.updateResumeToken(targetChange.getResumeToken());
          }
          break;
        default:
          throw fail("Unknown target watch change state: %s", targetChange.getChangeType());
      }
    }
  }

  /**
   * Returns all targetIds that the watch change applies to: either the targetIds explicitly listed
   * in the change or the targetIds of all currently active targets.
   */
  private Collection<Integer> getTargetIds(WatchTargetChange targetChange) {
    List<Integer> targetIds = targetChange.getTargetIds();
    if (!targetIds.isEmpty()) {
      return targetIds;
    } else {
      List<Integer> activeIds = new ArrayList<>();
      for (Integer id : targetStates.keySet()) {
        if (isActiveTarget(id)) {
          activeIds.add(id);
        }
      }
      return activeIds;
    }
  }

  /**
   * Handles existence filters and synthesizes deletes for filter mismatches. Targets that are
   * invalidated by filter mismatches are added to `pendingTargetResets`.
   */
  public void handleExistenceFilter(ExistenceFilterWatchChange watchChange) {
    int targetId = watchChange.getTargetId();
    int expectedCount = watchChange.getExistenceFilter().getCount();

    TargetData targetData = queryDataForActiveTarget(targetId);
    if (targetData != null) {
      Target target = targetData.getTarget();
      if (target.isDocumentQuery()) {
        if (expectedCount == 0) {
          // The existence filter told us the document does not exist. We deduce that this document
          // does not exist and apply a deleted document to our updates. Without applying this
          // deleted document there might be another query that will raise this document as part of
          // a snapshot  until it is resolved, essentially exposing inconsistency between queries.
          DocumentKey key = DocumentKey.fromPath(target.getPath());
          removeDocumentFromTarget(
              targetId,
              key,
              new NoDocument(key, SnapshotVersion.NONE, /*hasCommittedMutations=*/ false));
        } else {
          hardAssert(
              expectedCount == 1, "Single document existence filter with count: %d", expectedCount);
        }
      } else {
        long currentSize = getCurrentDocumentCountForTarget(targetId);
        if (currentSize != expectedCount) {
          // Existence filter mismatch: We reset the mapping and raise a new snapshot with
          // `isFromCache:true`.
          resetTarget(targetId);
          pendingTargetResets.add(targetId);
        }
      }
    }
  }

  /**
   * Converts the currently accumulated state into a remote event at the provided snapshot version.
   * Resets the accumulated changes before returning.
   */
  public RemoteEvent createRemoteEvent(SnapshotVersion snapshotVersion) {
    Map<Integer, TargetChange> targetChanges = new HashMap<>();

    for (Map.Entry<Integer, TargetState> entry : targetStates.entrySet()) {
      int targetId = entry.getKey();
      TargetState targetState = entry.getValue();

      TargetData targetData = queryDataForActiveTarget(targetId);
      if (targetData != null) {
        if (targetState.isCurrent() && targetData.getTarget().isDocumentQuery()) {
          // Document queries for document that don't exist can produce an empty result set. To
          // update our local cache, we synthesize a document delete if we have not previously
          // received the document. This resolves the limbo state of the document, removing it from
          // limboDocumentRefs.
          DocumentKey key = DocumentKey.fromPath(targetData.getTarget().getPath());
          if (pendingDocumentUpdates.get(key) == null && !targetContainsDocument(targetId, key)) {
            removeDocumentFromTarget(
                targetId,
                key,
                new NoDocument(key, snapshotVersion, /*hasCommittedMutations=*/ false));
          }
        }

        if (targetState.hasChanges()) {
          targetChanges.put(targetId, targetState.toTargetChange());
          targetState.clearChanges();
        }
      }
    }

    Set<DocumentKey> resolvedLimboDocuments = new HashSet<>();

    // We extract the set of limbo-only document updates as the GC logic special-cases documents
    // that do not appear in the query cache.
    //
    // TODO(gsoltis): Expand on this comment once GC is available in the Android client.
    for (Map.Entry<DocumentKey, Set<Integer>> entry : pendingDocumentTargetMapping.entrySet()) {
      DocumentKey key = entry.getKey();
      Set<Integer> targets = entry.getValue();

      boolean isOnlyLimboTarget = true;

      for (int targetId : targets) {
        TargetData targetData = queryDataForActiveTarget(targetId);
        if (targetData != null && !targetData.getPurpose().equals(QueryPurpose.LIMBO_RESOLUTION)) {
          isOnlyLimboTarget = false;
          break;
        }
      }

      if (isOnlyLimboTarget) {
        resolvedLimboDocuments.add(key);
      }
    }

    RemoteEvent remoteEvent =
        new RemoteEvent(
            snapshotVersion,
            Collections.unmodifiableMap(targetChanges),
            Collections.unmodifiableSet(pendingTargetResets),
            Collections.unmodifiableMap(pendingDocumentUpdates),
            Collections.unmodifiableSet(resolvedLimboDocuments));

    // Re-initialize the current state to ensure that we do not modify the generated RemoteEvent.
    pendingDocumentUpdates = new HashMap<>();
    pendingDocumentTargetMapping = new HashMap<>();
    pendingTargetResets = new HashSet<>();

    return remoteEvent;
  }

  /**
   * Adds the provided document to the internal list of document updates and its document key to the
   * given target's mapping.
   */
  private void addDocumentToTarget(int targetId, MaybeDocument document) {
    if (!isActiveTarget(targetId)) {
      return;
    }

    DocumentViewChange.Type changeType =
        targetContainsDocument(targetId, document.getKey())
            ? DocumentViewChange.Type.MODIFIED
            : DocumentViewChange.Type.ADDED;

    TargetState targetState = ensureTargetState(targetId);
    targetState.addDocumentChange(document.getKey(), changeType);

    pendingDocumentUpdates.put(document.getKey(), document);

    ensureDocumentTargetMapping(document.getKey()).add(targetId);
  }

  /**
   * Removes the provided document from the target mapping. If the document no longer matches the
   * target, but the document's state is still known (e.g. we know that the document was deleted or
   * we received the change that caused the filter mismatch), the new document can be provided to
   * update the remote document cache.
   */
  private void removeDocumentFromTarget(
      int targetId, DocumentKey key, @Nullable MaybeDocument updatedDocument) {
    if (!isActiveTarget(targetId)) {
      return;
    }

    TargetState targetState = ensureTargetState(targetId);
    if (targetContainsDocument(targetId, key)) {
      targetState.addDocumentChange(key, DocumentViewChange.Type.REMOVED);
    } else {
      // The document may have entered and left the target before we raised a snapshot, so we can
      // just ignore the change.
      targetState.removeDocumentChange(key);
    }

    ensureDocumentTargetMapping(key).add(targetId);

    if (updatedDocument != null) {
      pendingDocumentUpdates.put(key, updatedDocument);
    }
  }

  void removeTarget(int targetId) {
    targetStates.remove(targetId);
  }

  /**
   * Returns the current count of documents in the target. This includes both the number of
   * documents that the LocalStore considers to be part of the target as well as any accumulated
   * changes.
   */
  private int getCurrentDocumentCountForTarget(int targetId) {
    TargetState targetState = ensureTargetState(targetId);
    TargetChange targetChange = targetState.toTargetChange();
    return (targetMetadataProvider.getRemoteKeysForTarget(targetId).size()
        + targetChange.getAddedDocuments().size()
        - targetChange.getRemovedDocuments().size());
  }

  /**
   * Increment the number of acks needed from watch before we can consider the server to be
   * 'in-sync' with the client's active targets.
   */
  void recordPendingTargetRequest(int targetId) {
    // For each request we get we need to record we need a response for it.
    TargetState targetState = ensureTargetState(targetId);
    targetState.recordPendingTargetRequest();
  }

  private TargetState ensureTargetState(int targetId) {
    TargetState targetState = targetStates.get(targetId);
    if (targetState == null) {
      targetState = new TargetState();
      targetStates.put(targetId, targetState);
    }

    return targetState;
  }

  private Set<Integer> ensureDocumentTargetMapping(DocumentKey key) {
    Set<Integer> targetMapping = pendingDocumentTargetMapping.get(key);

    if (targetMapping == null) {
      targetMapping = new HashSet<>();
      pendingDocumentTargetMapping.put(key, targetMapping);
    }

    return targetMapping;
  }

  /**
   * Verifies that the user is still interested in this target (by calling
   * `getTargetDataForTarget()`) and that we are not waiting for pending ADDs from watch.
   */
  private boolean isActiveTarget(int targetId) {
    return queryDataForActiveTarget(targetId) != null;
  }

  /**
   * Returns the TargetData for an active target (i.e. a target that the user is still interested in
   * that has no outstanding target change requests).
   */
  @Nullable
  private TargetData queryDataForActiveTarget(int targetId) {
    TargetState targetState = targetStates.get(targetId);
    return targetState != null && targetState.isPending()
        ? null
        : targetMetadataProvider.getTargetDataForTarget(targetId);
  }

  /**
   * Resets the state of a Watch target to its initial state (e.g. sets 'current' to false, clears
   * the resume token and removes its target mapping from all documents).
   */
  private void resetTarget(int targetId) {
    hardAssert(
        targetStates.get(targetId) != null && !targetStates.get(targetId).isPending(),
        "Should only reset active targets");
    targetStates.put(targetId, new TargetState());

    // Trigger removal for any documents currently mapped to this target. These removals will be
    // part of the initial snapshot if Watch does not resend these documents.
    ImmutableSortedSet<DocumentKey> existingKeys =
        targetMetadataProvider.getRemoteKeysForTarget(targetId);
    for (DocumentKey key : existingKeys) {
      removeDocumentFromTarget(targetId, key, null);
    }
  }

  /** Returns whether the LocalStore considers the document to be part of the specified target. */
  private boolean targetContainsDocument(int targetId, DocumentKey key) {
    ImmutableSortedSet<DocumentKey> existingKeys =
        targetMetadataProvider.getRemoteKeysForTarget(targetId);
    return existingKeys.contains(key);
  }
}
