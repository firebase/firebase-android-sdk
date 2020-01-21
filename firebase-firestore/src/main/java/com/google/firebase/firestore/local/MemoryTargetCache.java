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
import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.Consumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An implementation of the TargetCache protocol that merely keeps targets in memory, suitable for
 * online only clients with persistence disabled.
 */
final class MemoryTargetCache implements TargetCache {

  /** Maps a target to the data about that target. */
  private final Map<Target, TargetData> targets = new HashMap<>();

  /** A ordered bidirectional mapping between documents and the remote target IDs. */
  private final ReferenceSet references = new ReferenceSet();

  /** The highest numbered target ID encountered. */
  private int highestTargetId;

  /** The last received snapshot version. */
  private SnapshotVersion lastRemoteSnapshotVersion = SnapshotVersion.NONE;

  private long highestSequenceNumber = 0;

  private final MemoryPersistence persistence;

  MemoryTargetCache(MemoryPersistence persistence) {
    this.persistence = persistence;
  }

  @Override
  public int getHighestTargetId() {
    return highestTargetId;
  }

  @Override
  public long getTargetCount() {
    return targets.size();
  }

  @Override
  public void forEachTarget(Consumer<TargetData> consumer) {
    for (TargetData targetData : targets.values()) {
      consumer.accept(targetData);
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
  public void addTargetData(TargetData targetData) {
    targets.put(targetData.getTarget(), targetData);
    int targetId = targetData.getTargetId();
    if (targetId > highestTargetId) {
      highestTargetId = targetId;
    }
    if (targetData.getSequenceNumber() > highestSequenceNumber) {
      highestSequenceNumber = targetData.getSequenceNumber();
    }
  }

  @Override
  public void updateTargetData(TargetData targetData) {
    // Memory persistence doesn't need to do anything different between add and remove.
    addTargetData(targetData);
  }

  @Override
  public void removeTargetData(TargetData targetData) {
    targets.remove(targetData.getTarget());
    references.removeReferencesForId(targetData.getTargetId());
  }

  /**
   * Drops any targets with sequence number less than or equal to the upper bound, excepting those
   * present in `activeTargetIds`. Document associations for the removed targets are also removed.
   *
   * @return the number of targets removed
   */
  int removeQueries(long upperBound, SparseArray<?> activeTargetIds) {
    int removed = 0;
    for (Iterator<Map.Entry<Target, TargetData>> it = targets.entrySet().iterator();
        it.hasNext(); ) {
      Map.Entry<Target, TargetData> entry = it.next();
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
  public TargetData getTargetData(Target target) {
    return targets.get(target);
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

  long getByteSize(LocalSerializer serializer) {
    long count = 0;
    for (Map.Entry<Target, TargetData> entry : targets.entrySet()) {
      count += serializer.encodeTargetData(entry.getValue()).getSerializedSize();
    }
    return count;
  }
}
