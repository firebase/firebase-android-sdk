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
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.util.Consumer;

/** Provides LRU functionality for SQLite persistence. */
class SQLiteLruReferenceDelegate implements ReferenceDelegate, LruDelegate {
  /**
   * The batch size for orphaned document GC in `removeOrphanedDocuments()`.
   *
   * <p>This addresses https://github.com/firebase/firebase-android-sdk/issues/706, where a customer
   * reported that LRU GC hit a CursorWindow size limit during orphaned document removal.
   */
  static final int REMOVE_ORPHANED_DOCUMENTS_BATCH_SIZE = 100;

  private final SQLitePersistence persistence;
  private ListenSequence listenSequence;
  private long currentSequenceNumber;
  private final LruGarbageCollector garbageCollector;
  private ReferenceSet inMemoryPins;

  SQLiteLruReferenceDelegate(SQLitePersistence persistence, LruGarbageCollector.Params params) {
    this.currentSequenceNumber = ListenSequence.INVALID;
    this.persistence = persistence;
    this.garbageCollector = new LruGarbageCollector(this, params);
  }

  void start(long highestSequenceNumber) {
    listenSequence = new ListenSequence(highestSequenceNumber);
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
  public LruGarbageCollector getGarbageCollector() {
    return garbageCollector;
  }

  @Override
  public long getSequenceNumberCount() {
    long targetCount = persistence.getTargetCache().getTargetCount();
    long orphanedDocumentCount =
        persistence
            .query(
                "SELECT COUNT(*) FROM (SELECT sequence_number FROM target_documents GROUP BY path HAVING COUNT(*) = 1 AND target_id = 0)")
            .firstValue(row -> row.getLong(0));
    return targetCount + orphanedDocumentCount;
  }

  @Override
  public void forEachTarget(Consumer<TargetData> consumer) {
    persistence.getTargetCache().forEachTarget(consumer);
  }

  @Override
  public void forEachOrphanedDocumentSequenceNumber(Consumer<Long> consumer) {
    persistence
        .query(
            "select sequence_number from target_documents group by path having COUNT(*) = 1 AND target_id = 0")
        .forEach(row -> consumer.accept(row.getLong(0)));
  }

  @Override
  public void setInMemoryPins(ReferenceSet inMemoryPins) {
    this.inMemoryPins = inMemoryPins;
  }

  @Override
  public void addReference(DocumentKey key) {
    writeSentinel(key);
  }

  @Override
  public void removeReference(DocumentKey key) {
    writeSentinel(key);
  }

  @Override
  public int removeTargets(long upperBound, SparseArray<?> activeTargetIds) {
    return persistence.getTargetCache().removeQueries(upperBound, activeTargetIds);
  }

  @Override
  public void removeMutationReference(DocumentKey key) {
    writeSentinel(key);
  }

  /** Returns true if any mutation queue contains the given document. */
  private boolean mutationQueuesContainKey(DocumentKey key) {
    return !persistence
        .query("SELECT 1 FROM document_mutations WHERE path = ?")
        .binding(EncodedPath.encode(key.getPath()))
        .isEmpty();
  }

  /**
   * Returns true if anything would prevent this document from being garbage collected, given that
   * the document in question is not present in any targets and has a sequence number less than or
   * equal to the upper bound for the collection run.
   */
  private boolean isPinned(DocumentKey key) {
    if (inMemoryPins.containsKey(key)) {
      return true;
    }

    return mutationQueuesContainKey(key);
  }

  private void removeSentinel(DocumentKey key) {
    persistence.execute(
        "DELETE FROM target_documents WHERE path = ? AND target_id = 0",
        EncodedPath.encode(key.getPath()));
  }

  @Override
  public int removeOrphanedDocuments(long upperBound) {
    int[] count = new int[1];

    boolean resultsRemaining = true;

    while (resultsRemaining) {
      int rowsProccessed =
          persistence
              .query(
                  "select path from target_documents group by path having COUNT(*) = 1 AND target_id = 0 AND sequence_number <= ? LIMIT ?")
              .binding(upperBound, REMOVE_ORPHANED_DOCUMENTS_BATCH_SIZE)
              .forEach(
                  row -> {
                    ResourcePath path = EncodedPath.decodeResourcePath(row.getString(0));
                    DocumentKey key = DocumentKey.fromPath(path);
                    if (!isPinned(key)) {
                      count[0]++;
                      persistence.getRemoteDocumentCache().remove(key);
                      removeSentinel(key);
                    }
                  });

      resultsRemaining = (rowsProccessed == REMOVE_ORPHANED_DOCUMENTS_BATCH_SIZE);
    }

    return count[0];
  }

  @Override
  public void removeTarget(TargetData targetData) {
    TargetData updated = targetData.withSequenceNumber(getCurrentSequenceNumber());
    persistence.getTargetCache().updateTargetData(updated);
  }

  @Override
  public void updateLimboDocument(DocumentKey key) {
    writeSentinel(key);
  }

  private void writeSentinel(DocumentKey key) {
    String path = EncodedPath.encode(key.getPath());
    persistence.execute(
        "INSERT OR REPLACE INTO target_documents (target_id, path, sequence_number) VALUES (0, ?, ?)",
        path,
        getCurrentSequenceNumber());
  }

  @Override
  public long getByteSize() {
    return persistence.getByteSize();
  }
}
