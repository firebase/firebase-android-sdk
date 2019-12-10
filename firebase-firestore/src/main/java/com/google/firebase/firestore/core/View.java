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
import static com.google.firebase.firestore.util.Util.compareIntegers;

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.DocumentViewChange.Type;
import com.google.firebase.firestore.core.ViewSnapshot.SyncState;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.remote.TargetChange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * View is responsible for computing the final merged truth of what docs are in a query. It gets
 * notified of local and remote changes to docs, and applies the query filters and limits to
 * determine the most correct possible results.
 */
public class View {
  /** The result of applying a set of doc changes to a view. */
  public static class DocumentChanges {
    private DocumentChanges(
        DocumentSet newDocuments,
        DocumentViewChangeSet changes,
        ImmutableSortedSet<DocumentKey> mutatedKeys,
        boolean needsRefill) {
      this.documentSet = newDocuments;
      this.changeSet = changes;
      this.mutatedKeys = mutatedKeys;
      this.needsRefill = needsRefill;
    }

    /** The new set of docs that should be in the view. */
    final DocumentSet documentSet;

    /** The diff of these docs with the previous set of docs. */
    final DocumentViewChangeSet changeSet;

    private final boolean needsRefill;

    final ImmutableSortedSet<DocumentKey> mutatedKeys;

    /**
     * Whether the set of documents passed in was not sufficient to calculate the new state of the
     * view and there needs to be another pass based on the local cache.
     */
    public boolean needsRefill() {
      return needsRefill;
    }
  }

  private final Query query;

  private SyncState syncState;

  /**
   * A flag whether the view is current with the backend. A view is considered current after it has
   * seen the current flag from the backend and did not lose consistency within the watch stream
   * (e.g. because of an existence filter mismatch).
   */
  private boolean current;

  private DocumentSet documentSet;

  /** Documents included in the remote target */
  private ImmutableSortedSet<DocumentKey> syncedDocuments;

  /** Documents in the view but not in the remote target */
  private ImmutableSortedSet<DocumentKey> limboDocuments;

  /** Documents that have local changes */
  private ImmutableSortedSet<DocumentKey> mutatedKeys;

  public View(Query query, ImmutableSortedSet<DocumentKey> remoteDocuments) {
    this.query = query;
    syncState = SyncState.NONE;
    documentSet = DocumentSet.emptySet(query.comparator());
    syncedDocuments = remoteDocuments;
    limboDocuments = DocumentKey.emptyKeySet();
    mutatedKeys = DocumentKey.emptyKeySet();
  }

  public SyncState getSyncState() {
    return this.syncState;
  }

  /**
   * Iterates over a set of doc changes, applies the query limit, and computes what the new results
   * should be, what the changes were, and whether we may need to go back to the local cache for
   * more results. Does not make any changes to the view.
   *
   * @param docChanges The doc changes to apply to this view.
   * @return a new set of docs, changes, and refill flag.
   */
  public <D extends MaybeDocument> DocumentChanges computeDocChanges(
      ImmutableSortedMap<DocumentKey, D> docChanges) {
    return computeDocChanges(docChanges, null);
  }

  /**
   * Iterates over a set of doc changes, applies the query limit, and computes what the new results
   * should be, what the changes were, and whether we may need to go back to the local cache for
   * more results. Does not make any changes to the view.
   *
   * @param docChanges The doc changes to apply to this view.
   * @param previousChanges If this is being called with a refill, then start with this set of docs
   *     and changes instead of the current view.
   * @return a new set of docs, changes, and refill flag.
   */
  public <D extends MaybeDocument> DocumentChanges computeDocChanges(
      ImmutableSortedMap<DocumentKey, D> docChanges, @Nullable DocumentChanges previousChanges) {
    DocumentViewChangeSet changeSet =
        previousChanges != null ? previousChanges.changeSet : new DocumentViewChangeSet();
    DocumentSet oldDocumentSet =
        previousChanges != null ? previousChanges.documentSet : documentSet;
    ImmutableSortedSet<DocumentKey> newMutatedKeys =
        previousChanges != null ? previousChanges.mutatedKeys : mutatedKeys;
    DocumentSet newDocumentSet = oldDocumentSet;
    boolean needsRefill = false;

    // Track the last doc in a (full) limit. This is necessary, because some update (a delete, or an
    // update moving a doc past the old limit) might mean there is some other document in the local
    // cache that either should come (1) between the old last limit doc and the new last document,
    // in the case of updates, or (2) after the new last document, in the case of deletes. So we
    // keep this doc at the old limit to compare the updates to.
    //
    // Note that this should never get used in a refill (when previousChanges is set), because there
    // will only be adds -- no deletes or updates.
    Document lastDocInLimit =
        (query.hasLimitToFirst() && oldDocumentSet.size() == query.getLimitToFirst())
            ? oldDocumentSet.getLastDocument()
            : null;
    Document firstDocInLimit =
        (query.hasLimitToLast() && oldDocumentSet.size() == query.getLimitToLast())
            ? oldDocumentSet.getFirstDocument()
            : null;

    for (Map.Entry<DocumentKey, ? extends MaybeDocument> entry : docChanges) {
      DocumentKey key = entry.getKey();
      Document oldDoc = oldDocumentSet.getDocument(key);
      Document newDoc = null;
      MaybeDocument maybeDoc = entry.getValue();

      if (maybeDoc instanceof Document) {
        newDoc = (Document) maybeDoc;
      }

      if (newDoc != null) {
        hardAssert(
            key.equals(newDoc.getKey()),
            "Mismatching key in doc change %s != %s",
            key,
            newDoc.getKey());
        if (!query.matches(newDoc)) {
          newDoc = null;
        }
      }

      boolean oldDocHadPendingMutations =
          oldDoc != null && this.mutatedKeys.contains(oldDoc.getKey());

      // We only consider committed mutations for documents that were mutated during the lifetime of
      // the view.
      boolean newDocHasPendingMutations =
          newDoc != null
              && (newDoc.hasLocalMutations()
                  || (this.mutatedKeys.contains(newDoc.getKey())
                      && newDoc.hasCommittedMutations()));

      boolean changeApplied = false;

      // Calculate change
      if (oldDoc != null && newDoc != null) {
        boolean docsEqual = oldDoc.getData().equals(newDoc.getData());
        if (!docsEqual) {
          if (!shouldWaitForSyncedDocument(oldDoc, newDoc)) {
            changeSet.addChange(DocumentViewChange.create(Type.MODIFIED, newDoc));
            changeApplied = true;

            if ((lastDocInLimit != null && query.comparator().compare(newDoc, lastDocInLimit) > 0)
                || (firstDocInLimit != null
                    && query.comparator().compare(newDoc, firstDocInLimit) < 0)) {
              // This doc moved from inside the limit to outside the limit. That means there may be
              // some doc in the local cache that should be included instead.
              needsRefill = true;
            }
          }
        } else if (oldDocHadPendingMutations != newDocHasPendingMutations) {
          changeSet.addChange(DocumentViewChange.create(Type.METADATA, newDoc));
          changeApplied = true;
        }
      } else if (oldDoc == null && newDoc != null) {
        changeSet.addChange(DocumentViewChange.create(Type.ADDED, newDoc));
        changeApplied = true;
      } else if (oldDoc != null && newDoc == null) {
        changeSet.addChange(DocumentViewChange.create(Type.REMOVED, oldDoc));
        changeApplied = true;
        if (lastDocInLimit != null || firstDocInLimit != null) {
          // A doc was removed from a full limit query. We'll need to requery from the local cache
          // to see if we know about some other doc that should be in the results.
          needsRefill = true;
        }
      }

      if (changeApplied) {
        if (newDoc != null) {
          newDocumentSet = newDocumentSet.add(newDoc);
          if (newDoc.hasLocalMutations()) {
            newMutatedKeys = newMutatedKeys.insert(newDoc.getKey());
          } else {
            newMutatedKeys = newMutatedKeys.remove(newDoc.getKey());
          }
        } else {
          newDocumentSet = newDocumentSet.remove(key);
          newMutatedKeys = newMutatedKeys.remove(key);
        }
      }
    }

    // Drop documents out to meet limitToFirst/limitToLast requirement.
    if (query.hasLimitToFirst() || query.hasLimitToLast()) {
      long limit = query.hasLimitToFirst() ? query.getLimitToFirst() : query.getLimitToLast();
      for (long i = newDocumentSet.size() - limit; i > 0; --i) {
        Document oldDoc =
            query.hasLimitToFirst()
                ? newDocumentSet.getLastDocument()
                : newDocumentSet.getFirstDocument();
        newDocumentSet = newDocumentSet.remove(oldDoc.getKey());
        newMutatedKeys = newMutatedKeys.remove(oldDoc.getKey());
        changeSet.addChange(DocumentViewChange.create(Type.REMOVED, oldDoc));
      }
    }

    hardAssert(
        !needsRefill || previousChanges == null,
        "View was refilled using docs that themselves needed refilling.");

    return new DocumentChanges(newDocumentSet, changeSet, newMutatedKeys, needsRefill);
  }

  private boolean shouldWaitForSyncedDocument(Document oldDoc, Document newDoc) {
    // We suppress the initial change event for documents that were modified as part of a write
    // acknowledgment (e.g. when the value of a server transform is applied) as Watch will send us
    // the same document again. By suppressing the event, we only raise two user visible events (one
    // with `hasPendingWrites` and the final state of the document) instead of three (one with
    // `hasPendingWrites`, the modified document with `hasPendingWrites` and the final state of the
    // document).
    return (oldDoc.hasLocalMutations()
        && newDoc.hasCommittedMutations()
        && !newDoc.hasLocalMutations());
  }

  /**
   * Updates the view with the given ViewDocumentChanges and updates limbo docs and sync state from
   * the given (optional) target change.
   *
   * @param docChanges The set of changes to make to the view's docs.
   * @return A new ViewChange with the given docs, changes, and sync state.
   */
  public ViewChange applyChanges(DocumentChanges docChanges) {
    return applyChanges(docChanges, null);
  }

  /**
   * Updates the view with the given ViewDocumentChanges and updates limbo docs and sync state from
   * the given (optional) target change.
   *
   * @param docChanges The set of changes to make to the view's docs.
   * @param targetChange A target change to apply for computing limbo docs and sync state.
   * @return A new ViewChange with the given docs, changes, and sync state.
   */
  public ViewChange applyChanges(DocumentChanges docChanges, TargetChange targetChange) {
    hardAssert(!docChanges.needsRefill, "Cannot apply changes that need a refill");

    DocumentSet oldDocumentSet = documentSet;
    documentSet = docChanges.documentSet;
    mutatedKeys = docChanges.mutatedKeys;

    // Sort changes based on type and query comparator.
    List<DocumentViewChange> viewChanges = docChanges.changeSet.getChanges();
    Collections.sort(
        viewChanges,
        (DocumentViewChange o1, DocumentViewChange o2) -> {
          int typeComp = compareIntegers(View.changeTypeOrder(o1), View.changeTypeOrder(o2));
          o1.getType().compareTo(o2.getType());
          if (typeComp != 0) {
            return typeComp;
          }
          return query.comparator().compare(o1.getDocument(), o2.getDocument());
        });
    applyTargetChange(targetChange);
    List<LimboDocumentChange> limboDocumentChanges = updateLimboDocuments();
    boolean synced = limboDocuments.size() == 0 && current;
    SyncState newSyncState = synced ? SyncState.SYNCED : SyncState.LOCAL;
    boolean syncStatedChanged = newSyncState != syncState;
    syncState = newSyncState;
    ViewSnapshot snapshot = null;
    if (viewChanges.size() != 0 || syncStatedChanged) {
      boolean fromCache = newSyncState == SyncState.LOCAL;
      snapshot =
          new ViewSnapshot(
              query,
              docChanges.documentSet,
              oldDocumentSet,
              viewChanges,
              fromCache,
              docChanges.mutatedKeys,
              syncStatedChanged,
              /* excludesMetadataChanges= */ false);
    }
    return new ViewChange(snapshot, limboDocumentChanges);
  }

  /**
   * Applies an OnlineState change to the view, potentially generating a ViewChange if the view's
   * syncState changes as a result.
   */
  public ViewChange applyOnlineStateChange(OnlineState onlineState) {
    if (current && onlineState == OnlineState.OFFLINE) {
      // If we're offline, set `current` to false and then call applyChanges() to refresh our
      // syncState and generate a ViewChange as appropriate. We are guaranteed to get a new
      // TargetChange that sets `current` back to true once the client is back online.
      this.current = false;
      return applyChanges(
          new DocumentChanges(
              documentSet, new DocumentViewChangeSet(), mutatedKeys, /*needsRefill=*/ false));
    } else {
      // No effect, just return a no-op ViewChange.
      return new ViewChange(null, Collections.emptyList());
    }
  }

  private void applyTargetChange(TargetChange targetChange) {
    if (targetChange != null) {
      for (DocumentKey documentKey : targetChange.getAddedDocuments()) {
        syncedDocuments = syncedDocuments.insert(documentKey);
      }
      for (DocumentKey documentKey : targetChange.getModifiedDocuments()) {
        hardAssert(
            syncedDocuments.contains(documentKey),
            "Modified document %s not found in view.",
            documentKey);
      }
      for (DocumentKey documentKey : targetChange.getRemovedDocuments()) {
        syncedDocuments = syncedDocuments.remove(documentKey);
      }
      current = targetChange.isCurrent();
    }
  }

  private List<LimboDocumentChange> updateLimboDocuments() {
    // We can only determine limbo documents when we're in-sync with the server.
    if (!current) {
      return Collections.emptyList();
    }

    // TODO: Do this incrementally so that it's not quadratic when updating many
    // documents.
    ImmutableSortedSet<DocumentKey> oldLimboDocs = limboDocuments;
    limboDocuments = DocumentKey.emptyKeySet();
    for (Document doc : documentSet) {
      if (shouldBeLimboDoc(doc.getKey())) {
        limboDocuments = limboDocuments.insert(doc.getKey());
      }
    }

    // Diff the new limbo docs with the old limbo docs.
    List<LimboDocumentChange> changes =
        new ArrayList<>(oldLimboDocs.size() + limboDocuments.size());
    for (DocumentKey key : oldLimboDocs) {
      if (!limboDocuments.contains(key)) {
        changes.add(new LimboDocumentChange(LimboDocumentChange.Type.REMOVED, key));
      }
    }

    for (DocumentKey key : limboDocuments) {
      if (!oldLimboDocs.contains(key)) {
        changes.add(new LimboDocumentChange(LimboDocumentChange.Type.ADDED, key));
      }
    }
    return changes;
  }

  private boolean shouldBeLimboDoc(DocumentKey key) {
    // If the remote end says it's part of this query, it's not in limbo.
    if (syncedDocuments.contains(key)) {
      return false;
    }
    // The local store doesn't think it's a result, so it shouldn't be in limbo.
    Document doc = documentSet.getDocument(key);
    if (doc == null) {
      return false;
    }

    // If there are local changes to the doc, they might explain why the server doesn't know
    // that it's part of the query. So don't put it in limbo.
    // TODO: Ideally, we would only consider changes that might actually affect this
    // specific query.
    if (doc.hasLocalMutations()) {
      return false;
    }

    // Everything else is in limbo
    return true;
  }

  ImmutableSortedSet<DocumentKey> getLimboDocuments() {
    return limboDocuments;
  }

  /**
   * @return The set of documents that the server has told us belongs to the target associated with
   *     this view.
   */
  ImmutableSortedSet<DocumentKey> getSyncedDocuments() {
    return syncedDocuments;
  }

  /** Helper function to determine order of changes */
  private static int changeTypeOrder(DocumentViewChange change) {
    switch (change.getType()) {
      case ADDED:
        return 1;
      case MODIFIED:
        return 2;
      case METADATA:
        // A metadata change is converted to a modified change at the public api layer.
        // Since we sort by document key and then change type, metadata and modified changes must
        // be sorted equivalently.
        return 2;
      case REMOVED:
        return 0;
    }
    throw new IllegalArgumentException("Unknown change type: " + change.getType());
  }
}
