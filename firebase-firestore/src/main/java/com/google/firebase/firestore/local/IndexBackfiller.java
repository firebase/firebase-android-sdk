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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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
  private final RemoteDocumentCache remoteDocumentCache;
  private LocalDocumentsView localDocumentsView;
  private IndexManager indexManager;
  private int maxDocumentsToProcess = MAX_DOCUMENTS_TO_PROCESS;

  public IndexBackfiller(Persistence persistence, AsyncQueue asyncQueue) {
    this.persistence = persistence;
    this.scheduler = new Scheduler(asyncQueue);
    this.remoteDocumentCache = persistence.getRemoteDocumentCache();
  }

  public void setLocalDocumentsView(LocalDocumentsView localDocumentsView) {
    this.localDocumentsView = localDocumentsView;
  }

  public void setIndexManager(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  public class Scheduler implements com.google.firebase.firestore.local.Scheduler {
    private boolean hasRun = false;
    @Nullable private AsyncQueue.DelayedTask backfillTask;
    private final AsyncQueue asyncQueue;

    public Scheduler(AsyncQueue asyncQueue) {
      this.asyncQueue = asyncQueue;
    }

    @Override
    public void start() {
      hardAssert(Persistence.INDEXING_SUPPORT_ENABLED, "Indexing support not enabled");
      scheduleBackfill();
    }

    @Override
    public void stop() {
      hardAssert(Persistence.INDEXING_SUPPORT_ENABLED, "Indexing support not enabled");
      if (backfillTask != null) {
        backfillTask.cancel();
      }
    }

    private void scheduleBackfill() {
      long delay = hasRun ? REGULAR_BACKFILL_DELAY_MS : INITIAL_BACKFILL_DELAY_MS;
      backfillTask =
          asyncQueue.enqueueAfterDelay(
              AsyncQueue.TimerId.INDEX_BACKFILL,
              delay,
              () -> {
                int documentsProcessed = backfill();
                Logger.debug(LOG_TAG, "Documents written: %s", documentsProcessed);
                hasRun = true;
                scheduleBackfill();
              });
    }
  }

  public Scheduler getScheduler() {
    return scheduler;
  }

  /** Runs a single backfill operation and returns the number of documents processed. */
  public int backfill() {
    hardAssert(localDocumentsView != null, "setLocalDocumentsView() not called");
    hardAssert(indexManager != null, "setIndexManager() not called");
    return persistence.runTransaction(
        "Backfill Indexes", () -> writeIndexEntries(localDocumentsView));
  }

  /** Writes index entries until the cap is reached. Returns the number of documents processed. */
  private int writeIndexEntries(LocalDocumentsView localDocumentsView) {
    Set<String> processedCollectionGroups = new HashSet<>();
    int documentsRemaining = maxDocumentsToProcess;
    while (documentsRemaining > 0) {
      String collectionGroup = indexManager.getNextCollectionGroupToUpdate();
      if (collectionGroup == null || processedCollectionGroups.contains(collectionGroup)) {
        break;
      }
      Logger.debug(LOG_TAG, "Processing collection: %s", collectionGroup);
      documentsRemaining -=
          writeEntriesForCollectionGroup(localDocumentsView, collectionGroup, documentsRemaining);
      processedCollectionGroups.add(collectionGroup);
    }
    return maxDocumentsToProcess - documentsRemaining;
  }

  /** Writes entries for the fetched field indexes. */
  private int writeEntriesForCollectionGroup(
      LocalDocumentsView localDocumentsView, String collectionGroup, int entriesRemainingUnderCap) {
    // TODO(indexing): Support mutation batch Ids when sorting and writing indexes.

    // Use the earliest offset of all field indexes to query the local cache.
    IndexOffset existingOffset = getExistingOffset(indexManager.getFieldIndexes(collectionGroup));
    ImmutableSortedMap<DocumentKey, Document> documents =
        localDocumentsView.getDocuments(collectionGroup, existingOffset, entriesRemainingUnderCap);
    indexManager.updateIndexEntries(documents);

    IndexOffset newOffset = getNewOffset(documents, existingOffset);
    indexManager.updateCollectionGroup(collectionGroup, newOffset);

    return documents.size();
  }

  /** Returns the lowest offset for the provided index group. */
  private IndexOffset getExistingOffset(Collection<FieldIndex> fieldIndexes) {
    IndexOffset lowestOffset = null;
    for (FieldIndex fieldIndex : fieldIndexes) {
      if (lowestOffset == null
          || fieldIndex.getIndexState().getOffset().compareTo(lowestOffset) < 0) {
        lowestOffset = fieldIndex.getIndexState().getOffset();
      }
    }
    return lowestOffset == null ? IndexOffset.NONE : lowestOffset;
  }

  /** Returns the offset for the index based on the newly indexed documents. */
  private IndexOffset getNewOffset(
      ImmutableSortedMap<DocumentKey, Document> documents, IndexOffset currentOffset) {
    if (documents.isEmpty()) {
      return IndexOffset.create(remoteDocumentCache.getLatestReadTime());
    } else {
      IndexOffset latestOffset = currentOffset;
      Iterator<Map.Entry<DocumentKey, Document>> it = documents.iterator();
      while (it.hasNext()) {
        IndexOffset newOffset = IndexOffset.fromDocument(it.next().getValue());
        if (newOffset.compareTo(latestOffset) > 0) {
          latestOffset = newOffset;
        }
      }
      return latestOffset;
    }
  }

  @VisibleForTesting
  void setMaxDocumentsToProcess(int newMax) {
    maxDocumentsToProcess = newMax;
  }
}
