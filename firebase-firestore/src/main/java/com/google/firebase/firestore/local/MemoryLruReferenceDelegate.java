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

import android.util.SparseArray;
import com.google.firebase.firestore.core.ListenSequence;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.util.Consumer;
import java.util.HashMap;
import java.util.Map;

/** Provides LRU garbage collection functionality for MemoryPersistence. */
class MemoryLruReferenceDelegate implements ReferenceDelegate, LruDelegate {
  private final MemoryPersistence persistence;
  private final LocalSerializer serializer;
  private final Map<DocumentKey, Long> orphanedSequenceNumbers;
  private ReferenceSet inMemoryPins;
  private final LruGarbageCollector garbageCollector;
  private final ListenSequence listenSequence;
  private long currentSequenceNumber;

  MemoryLruReferenceDelegate(
      MemoryPersistence persistence,
      LruGarbageCollector.Params params,
      LocalSerializer serializer) {
    this.persistence = persistence;
    this.serializer = serializer;
    this.orphanedSequenceNumbers = new HashMap<>();
    this.listenSequence =
        new ListenSequence(persistence.getTargetCache().getHighestListenSequenceNumber());
    this.currentSequenceNumber = ListenSequence.INVALID;
    this.garbageCollector = new LruGarbageCollector(this, params);
  }

  @Override
  public LruGarbageCollector getGarbageCollector() {
    return garbageCollector;
  }

  @Override
  public void onTransactionStarted() {
    hardAssert(
        currentSequenceNumber == ListenSequence.INVALID,
        "Starting a transaction without committing the previous one");
    currentSequenceNumber = listenSequence.next();
  }

  @Override
  public void onTransactionCommitted() {
    hardAssert(
        currentSequenceNumber != ListenSequence.INVALID,
        "Committing a transaction without having started one");
    currentSequenceNumber = ListenSequence.INVALID;
  }

  @Override
  public long getCurrentSequenceNumber() {
    hardAssert(
        currentSequenceNumber != ListenSequence.INVALID,
        "Attempting to get a sequence number outside of a transaction");
    return currentSequenceNumber;
  }

  @Override
  public void forEachTarget(Consumer<TargetData> consumer) {
    persistence.getTargetCache().forEachTarget(consumer);
  }

  @Override
  public long getSequenceNumberCount() {
    long targetCount = persistence.getTargetCache().getTargetCount();
    long orphanedCount[] = new long[1];
    forEachOrphanedDocumentSequenceNumber(
        sequenceNumber -> {
          orphanedCount[0]++;
        });
    return targetCount + orphanedCount[0];
  }

  @Override
  public void forEachOrphanedDocumentSequenceNumber(Consumer<Long> consumer) {
    for (Map.Entry<DocumentKey, Long> entry : orphanedSequenceNumbers.entrySet()) {
      // Pass in the exact sequence number as the upper bound so we know it won't be pinned by being
      // too recent.
      if (!isPinned(entry.getKey(), entry.getValue())) {
        consumer.accept(entry.getValue());
      }
    }
  }

  @Override
  public void setInMemoryPins(ReferenceSet inMemoryPins) {
    this.inMemoryPins = inMemoryPins;
  }

  @Override
  public int removeTargets(long upperBound, SparseArray<?> activeTargetIds) {
    return persistence.getTargetCache().removeQueries(upperBound, activeTargetIds);
  }

  @Override
  public int removeOrphanedDocuments(long upperBound) {
    int count = 0;
    MemoryRemoteDocumentCache cache = persistence.getRemoteDocumentCache();
    for (MaybeDocument doc : cache.getDocuments()) {
      DocumentKey key = doc.getKey();
      if (!isPinned(key, upperBound)) {
        cache.remove(key);
        orphanedSequenceNumbers.remove(key);
        count++;
      }
    }
    return count;
  }

  @Override
  public void removeMutationReference(DocumentKey key) {
    orphanedSequenceNumbers.put(key, getCurrentSequenceNumber());
  }

  @Override
  public void removeTarget(TargetData targetData) {
    TargetData updated = targetData.withSequenceNumber(getCurrentSequenceNumber());
    persistence.getTargetCache().updateTargetData(updated);
  }

  @Override
  public void addReference(DocumentKey key) {
    orphanedSequenceNumbers.put(key, getCurrentSequenceNumber());
  }

  @Override
  public void removeReference(DocumentKey key) {
    orphanedSequenceNumbers.put(key, getCurrentSequenceNumber());
  }

  @Override
  public void updateLimboDocument(DocumentKey key) {
    orphanedSequenceNumbers.put(key, getCurrentSequenceNumber());
  }

  private boolean mutationQueuesContainsKey(DocumentKey key) {
    for (MemoryMutationQueue mutationQueue : persistence.getMutationQueues()) {
      if (mutationQueue.containsKey(key)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return true if there is anything that would keep the given document alive or if the document's
   *     sequence number is greater than the provided upper bound.
   */
  private boolean isPinned(DocumentKey key, long upperBound) {
    if (mutationQueuesContainsKey(key)) {
      return true;
    }

    if (inMemoryPins.containsKey(key)) {
      return true;
    }

    if (persistence.getTargetCache().containsKey(key)) {
      return true;
    }

    Long sequenceNumber = orphanedSequenceNumbers.get(key);
    return sequenceNumber != null && sequenceNumber > upperBound;
  }

  @Override
  public long getByteSize() {
    // Note that this method is only used for testing because this delegate is only
    // used for testing. The algorithm here (loop through everything, serialize it
    // and count bytes) is inefficient and inexact, but won't run in production.
    long count = 0;
    count += persistence.getTargetCache().getByteSize(serializer);
    count += persistence.getRemoteDocumentCache().getByteSize(serializer);
    for (MemoryMutationQueue queue : persistence.getMutationQueues()) {
      count += queue.getByteSize(serializer);
    }
    return count;
  }
}
