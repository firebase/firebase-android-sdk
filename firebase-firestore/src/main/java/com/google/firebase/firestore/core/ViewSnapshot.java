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

import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import java.util.ArrayList;
import java.util.List;

/** A view snapshot is an immutable capture of the results of a query and the changes to them. */
public class ViewSnapshot {

  /** The possibly states a document can be in w.r.t syncing from local storage to the backend. */
  public enum SyncState {
    NONE,
    LOCAL,
    SYNCED
  }

  private final Query query;
  private final DocumentSet documents;
  private final DocumentSet oldDocuments;
  private final List<DocumentViewChange> changes;
  private final boolean isFromCache;
  private final ImmutableSortedSet<DocumentKey> mutatedKeys;
  private final boolean didSyncStateChange;
  private boolean excludesMetadataChanges;

  public ViewSnapshot(
      Query query,
      DocumentSet documents,
      DocumentSet oldDocuments,
      List<DocumentViewChange> changes,
      boolean isFromCache,
      ImmutableSortedSet<DocumentKey> mutatedKeys,
      boolean didSyncStateChange,
      boolean excludesMetadataChanges) {
    this.query = query;
    this.documents = documents;
    this.oldDocuments = oldDocuments;
    this.changes = changes;
    this.isFromCache = isFromCache;
    this.mutatedKeys = mutatedKeys;
    this.didSyncStateChange = didSyncStateChange;
    this.excludesMetadataChanges = excludesMetadataChanges;
  }

  /** Returns a view snapshot as if all documents in the snapshot were added. */
  public static ViewSnapshot fromInitialDocuments(
      Query query,
      DocumentSet documents,
      ImmutableSortedSet<DocumentKey> mutatedKeys,
      boolean fromCache,
      boolean excludesMetadataChanges) {
    List<DocumentViewChange> viewChanges = new ArrayList<>();
    for (Document doc : documents) {
      viewChanges.add(DocumentViewChange.create(DocumentViewChange.Type.ADDED, doc));
    }
    return new ViewSnapshot(
        query,
        documents,
        DocumentSet.emptySet(query.comparator()),
        viewChanges,
        fromCache,
        mutatedKeys,
        /* didSyncStateChange= */ true,
        excludesMetadataChanges);
  }

  public Query getQuery() {
    return query;
  }

  public DocumentSet getDocuments() {
    return documents;
  }

  public DocumentSet getOldDocuments() {
    return oldDocuments;
  }

  public List<DocumentViewChange> getChanges() {
    return changes;
  }

  public boolean isFromCache() {
    return isFromCache;
  }

  public boolean hasPendingWrites() {
    return !mutatedKeys.isEmpty();
  }

  public ImmutableSortedSet<DocumentKey> getMutatedKeys() {
    return mutatedKeys;
  }

  public boolean didSyncStateChange() {
    return didSyncStateChange;
  }

  public boolean excludesMetadataChanges() {
    return excludesMetadataChanges;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ViewSnapshot)) {
      return false;
    }

    ViewSnapshot that = (ViewSnapshot) o;

    if (isFromCache != that.isFromCache) {
      return false;
    }
    if (didSyncStateChange != that.didSyncStateChange) {
      return false;
    }
    if (excludesMetadataChanges != that.excludesMetadataChanges) {
      return false;
    }
    if (!query.equals(that.query)) {
      return false;
    }
    if (!mutatedKeys.equals(that.mutatedKeys)) {
      return false;
    }
    if (!documents.equals(that.documents)) {
      return false;
    }
    if (!oldDocuments.equals(that.oldDocuments)) {
      return false;
    }
    return changes.equals(that.changes);
  }

  @Override
  public int hashCode() {
    int result = query.hashCode();
    result = 31 * result + documents.hashCode();
    result = 31 * result + oldDocuments.hashCode();
    result = 31 * result + changes.hashCode();
    result = 31 * result + mutatedKeys.hashCode();
    result = 31 * result + (isFromCache ? 1 : 0);
    result = 31 * result + (didSyncStateChange ? 1 : 0);
    result = 31 * result + (excludesMetadataChanges ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ViewSnapshot("
        + query
        + ", "
        + documents
        + ", "
        + oldDocuments
        + ", "
        + changes
        + ", isFromCache="
        + isFromCache
        + ", mutatedKeys="
        + mutatedKeys.size()
        + ", didSyncStateChange="
        + didSyncStateChange
        + ", excludesMetadataChanges="
        + excludesMetadataChanges
        + ")";
  }
}
