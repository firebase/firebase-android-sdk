// Copyright 2021 Google LLC
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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Implements the steps for backfilling indexes. */
public class IndexBackfiller {
  private static final String LOG_TAG = "IndexBackfiller";

  /** How long we wait to try running index backfill after SDK initialization. */
  private static final long INITIAL_BACKFILL_DELAY_MS = TimeUnit.SECONDS.toMillis(15);
  /** Minimum amount of time between backfill checks, after the first one. */
  private static final long REGULAR_BACKFILL_DELAY_MS = TimeUnit.MINUTES.toMillis(1);
  /** The maximum number of documents to process each time backfill() is called. */
  private static final int MAX_DOCUMENTS_TO_PROCESS = 50;

  private final Scheduler scheduler;
  private final Persistence persistence;
  private final Supplier<IndexManager> indexManagerOfCurrentUser;
  private final Supplier<LocalDocumentsView> localDocumentsViewOfCurrentUser;
  private int maxDocumentsToProcess = MAX_DOCUMENTS_TO_PROCESS;

  public IndexBackfiller(Persistence persistence, AsyncQueue asyncQueue, LocalStore localStore) {
    this(
        persistence,
        asyncQueue,
        localStore::getIndexManagerForCurrentUser,
        localStore::getLocalDocumentsForCurrentUser);
  }

  public IndexBackfiller(
      Persistence persistence,
      AsyncQueue asyncQueue,
      Supplier<IndexManager> indexManagerOfCurrentUser,
      Supplier<LocalDocumentsView> localDocumentsViewOfCurrentUser) {
    this.persistence = persistence;
    this.scheduler = new Scheduler(asyncQueue);
    this.indexManagerOfCurrentUser = indexManagerOfCurrentUser;
    this.localDocumentsViewOfCurrentUser = localDocumentsViewOfCurrentUser;
  }

  public class Scheduler implements com.google.firebase.firestore.local.Scheduler {
    @Nullable private AsyncQueue.DelayedTask backfillTask;
    private final AsyncQueue asyncQueue;

    public Scheduler(AsyncQueue asyncQueue) {
      this.asyncQueue = asyncQueue;
    }

    @Override
    public void start() {
      scheduleBackfill(INITIAL_BACKFILL_DELAY_MS);
    }

    @Override
    public void stop() {
      if (backfillTask != null) {
        backfillTask.cancel();
      }
    }

    private void scheduleBackfill(long delay) {
      backfillTask =
          asyncQueue.enqueueAfterDelay(
              AsyncQueue.TimerId.INDEX_BACKFILL,
              delay,
              () -> {
                int documentsProcessed = backfill();
                Logger.debug(LOG_TAG, "Documents written: %s", documentsProcessed);
                scheduleBackfill(REGULAR_BACKFILL_DELAY_MS);
              });
    }
  }

  public Scheduler getScheduler() {
    return scheduler;
  }

  /** Runs a single backfill operation and returns the number of documents processed. */
  public int backfill() {
    return persistence.runTransaction("Backfill Indexes", () -> this.writeIndexEntries());
  }

  /** Writes index entries until the cap is reached. Returns the number of documents processed. */
  private int writeIndexEntries() {
    IndexManager indexManager = indexManagerOfCurrentUser.get();
    Set<String> processedCollectionGroups = new HashSet<>();
    int documentsRemaining = maxDocumentsToProcess;
    while (documentsRemaining > 0) {
      String collectionGroup = indexManager.getNextCollectionGroupToUpdate();
      if (collectionGroup == null || processedCollectionGroups.contains(collectionGroup)) {
        break;
      }
      Logger.debug(LOG_TAG, "Processing collection: %s", collectionGroup);
      documentsRemaining -= writeEntriesForCollectionGroup(collectionGroup, documentsRemaining);
      processedCollectionGroups.add(collectionGroup);
    }
    return maxDocumentsToProcess - documentsRemaining;
  }

  /**
   * Writes entries for the provided collection group. Returns the number of documents processed.
   */
  private int writeEntriesForCollectionGroup(
      String collectionGroup, int documentsRemainingUnderCap) {
    IndexManager indexManager = indexManagerOfCurrentUser.get();
    LocalDocumentsView localDocumentsView = localDocumentsViewOfCurrentUser.get();
    // Use the earliest offset of all field indexes to query the local cache.
    IndexOffset existingOffset = indexManager.getMinOffset(collectionGroup);

    LocalDocumentsResult nextBatch =
        localDocumentsView.getNextDocuments(
            collectionGroup, existingOffset, documentsRemainingUnderCap);
    indexManager.updateIndexEntries(nextBatch.getDocuments());

    IndexOffset newOffset = getNewOffset(existingOffset, nextBatch);
    Logger.debug(LOG_TAG, "Updating offset: %s", newOffset);
    indexManager.updateCollectionGroup(collectionGroup, newOffset);

    return nextBatch.getDocuments().size();
  }

  /** Returns the next offset based on the provided documents. */
  private IndexOffset getNewOffset(IndexOffset existingOffset, LocalDocumentsResult lookupResult) {
    IndexOffset maxOffset = existingOffset;
    for (Map.Entry<DocumentKey, Document> entry : lookupResult.getDocuments()) {
      IndexOffset newOffset = IndexOffset.fromDocument(entry.getValue());
      if (newOffset.compareTo(maxOffset) > 0) {
        maxOffset = newOffset;
      }
    }
    return IndexOffset.create(
        maxOffset.getReadTime(),
        maxOffset.getDocumentKey(),
        Math.max(lookupResult.getBatchId(), existingOffset.getLargestBatchId()));
  }

  @VisibleForTesting
  void setMaxDocumentsToProcess(int newMax) {
    maxDocumentsToProcess = newMax;
  }
}
