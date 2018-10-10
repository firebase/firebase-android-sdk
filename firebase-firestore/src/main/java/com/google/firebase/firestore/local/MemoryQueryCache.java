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

import android.util.SparseArray;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.Consumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * An implementation of the QueryCache protocol that merely keeps queries in memory, suitable for
 * online only clients with persistence disabled.
 */
final class MemoryQueryCache implements QueryCache {

  /** Maps a query to the data about that query. */
  private final Map<Query, QueryData> queries = new HashMap<>();

  /** A ordered bidirectional mapping between documents and the remote target IDs. */
  private final ReferenceSet references = new ReferenceSet();

  /** The highest numbered target ID encountered. */
  private int highestTargetId;

  /** The last received snapshot version. */
  private SnapshotVersion lastRemoteSnapshotVersion = SnapshotVersion.NONE;

  private long highestSequenceNumber = 0;

  private final MemoryPersistence persistence;

  MemoryQueryCache(MemoryPersistence persistence) {
    this.persistence = persistence;
  }

  @Override
  public int getHighestTargetId() {
    return highestTargetId;
  }

  @Override
  public long getTargetCount() {
    return queries.size();
  }

  @Override
  public void forEachTarget(Consumer<QueryData> consumer) {
    for (QueryData queryData : queries.values()) {
      consumer.accept(queryData);
    }
  }

  @Override
  public long getHighestListenSequenceNumber() {
    return highestSequenceNumber;
  }

  @Override
  public SnapshotVersion getLastRemoteSnapshotVersion() {
    return lastRemoteSnapshotVersion;
  }

  @Override
  public void setLastRemoteSnapshotVersion(SnapshotVersion snapshotVersion) {
    lastRemoteSnapshotVersion = snapshotVersion;
  }

  // Query tracking

  @Override
  public void addQueryData(QueryData queryData) {
    queries.put(queryData.getQuery(), queryData);
    int targetId = queryData.getTargetId();
    if (targetId > highestTargetId) {
      highestTargetId = targetId;
    }
    if (queryData.getSequenceNumber() > highestSequenceNumber) {
      highestSequenceNumber = queryData.getSequenceNumber();
    }
  }

  @Override
  public void updateQueryData(QueryData queryData) {
    // Memory persistence doesn't need to do anything different between add and remove.
    addQueryData(queryData);
  }

  @Override
  public void removeQueryData(QueryData queryData) {
    queries.remove(queryData.getQuery());
    references.removeReferencesForId(queryData.getTargetId());
  }

  /**
   * Drops any targets with sequence number less than or equal to the upper bound, excepting those
   * present in `activeTargetIds`. Document associations for the removed targets are also removed.
   *
   * @return the number of targets removed
   */
  int removeQueries(long upperBound, SparseArray<?> activeTargetIds) {
    int removed = 0;
    for (Iterator<Map.Entry<Query, QueryData>> it = queries.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Query, QueryData> entry = it.next();
      int targetId = entry.getValue().getTargetId();
      long sequenceNumber = entry.getValue().getSequenceNumber();
      if (sequenceNumber <= upperBound && activeTargetIds.get(targetId) == null) {
        it.remove();
        removeMatchingKeysForTargetId(targetId);
        removed++;
      }
    }
    return removed;
  }

  @Nullable
  @Override
  public QueryData getQueryData(Query query) {
    return queries.get(query);
  }

  // Reference tracking

  @Override
  public void addMatchingKeys(ImmutableSortedSet<DocumentKey> keys, int targetId) {
    references.addReferences(keys, targetId);
    ReferenceDelegate referenceDelegate = persistence.getReferenceDelegate();
    for (DocumentKey key : keys) {
      referenceDelegate.addReference(key);
    }
  }

  @Override
  public void removeMatchingKeys(ImmutableSortedSet<DocumentKey> keys, int targetId) {
    references.removeReferences(keys, targetId);
    ReferenceDelegate referenceDelegate = persistence.getReferenceDelegate();
    for (DocumentKey key : keys) {
      referenceDelegate.removeReference(key);
    }
  }

  private void removeMatchingKeysForTargetId(int targetId) {
    references.removeReferencesForId(targetId);
  }

  @Override
  public ImmutableSortedSet<DocumentKey> getMatchingKeysForTargetId(int targetId) {
    return references.referencesForId(targetId);
  }

  @Override
  public boolean containsKey(DocumentKey key) {
    return references.containsKey(key);
  }
}
