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
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
      LocalDocumentsView localDocumentsView,
      String collectionGroup,
      int documentsRemainingUnderCap) {
    int documentsProcessed = 0;
    Query query = new Query(ResourcePath.EMPTY, collectionGroup);

    // Use the earliest offset of all field indexes to query the local cache.
    Collection<FieldIndex> fieldIndexes = indexManager.getFieldIndexes(collectionGroup);
    IndexOffset existingOffset = getExistingOffset(fieldIndexes);

    // TODO(indexing): Use limit queries to only fetch the required number of entries.
    ImmutableSortedMap<DocumentKey, Document> documents =
        localDocumentsView.getDocumentsMatchingQuery(query, existingOffset);

    List<Document> oldestDocuments = getOldestDocuments(documents, documentsRemainingUnderCap);
    indexManager.updateIndexEntries(oldestDocuments);
    documentsProcessed += oldestDocuments.size();

    IndexOffset newOffset = getNewOffset(oldestDocuments, existingOffset);

    // Start indexing mutations only after catching up for read time. This must be done separately
    // since the backfill's first pass is in read time order rather than mutation batch id order.
    int documentsRemaining = documentsRemainingUnderCap - oldestDocuments.size();
    if (documentsRemaining > 0) {
      int earliestBatchId = getEarliestBatchId(fieldIndexes);
      newOffset =
          IndexOffset.create(newOffset.getReadTime(), newOffset.getDocumentKey(), earliestBatchId);
      Map<Document, Integer> documentToBatchId =
          localDocumentsView.getDocumentsBatchIdsMatchingCollectionGroupQuery(query, newOffset);

      // Write index entries for documents touched by local mutations and update the offset to the
      // new largest batch id.
      if (!documentToBatchId.isEmpty()) {
        List<Document> documentsByBatchId =
            getDocumentsByBatchId(documentToBatchId, documentsRemaining);

        // Largest batch id is the last element in documentsByBatchId since it's sorted in ascending
        // order by batch id.
        int newLargestBatchId =
            documentToBatchId.get(documentsByBatchId.get(documentsByBatchId.size() - 1));
        newOffset =
            IndexOffset.create(
                newOffset.getReadTime(), newOffset.getDocumentKey(), newLargestBatchId);
        indexManager.updateIndexEntries(documentsByBatchId);
        documentsProcessed += documentsByBatchId.size();
      }
    }

    indexManager.updateCollectionGroup(collectionGroup, newOffset);
    return documentsProcessed;
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

  /**
   * Returns the read time and document key offset for the index based on the newly indexed
   * documents.
   *
   * @param documents a list of documents sorted by read time and key (ascending)
   * @param currentOffset the current offset of the index group
   */
  private IndexOffset getNewOffset(List<Document> documents, IndexOffset currentOffset) {
    IndexOffset latestOffset =
        documents.isEmpty()
            ? IndexOffset.create(remoteDocumentCache.getLatestReadTime())
            : IndexOffset.create(
                documents.get(documents.size() - 1).getReadTime(),
                documents.get(documents.size() - 1).getKey());
    // Make sure the index does not go back in time
    latestOffset = latestOffset.compareTo(currentOffset) > 0 ? latestOffset : currentOffset;
    return latestOffset;
  }

  /** Returns up to {@code count} documents sorted by read time and key. */
  private List<Document> getOldestDocuments(
      ImmutableSortedMap<DocumentKey, Document> documents, int count) {
    List<Document> oldestDocuments = new ArrayList<>();
    for (Map.Entry<DocumentKey, Document> entry : documents) {
      oldestDocuments.add(entry.getValue());
    }
    Collections.sort(
        oldestDocuments,
        (l, r) ->
            IndexOffset.create(l.getReadTime(), l.getKey())
                .compareTo(IndexOffset.create(r.getReadTime(), r.getKey())));
    return oldestDocuments.subList(0, Math.min(count, oldestDocuments.size()));
  }

  /**
   * Returns up to {@code count} documents sorted by batch id ascending, except in cases where the
   * mutation batch id contains more documents. This method will always return all documents in a
   * mutation batch, even if it goes over the limit.
   *
   * <p>For example, if count = 2, and we have: (docA, batch1), (docB, batch2), and (docC, batch2),
   * all three documents will be returned since docC is also part of batch2.
   */
  private List<Document> getDocumentsByBatchId(
      Map<Document, Integer> documentToBatchId, int count) {
    List<Document> oldestDocuments = new ArrayList<>(documentToBatchId.keySet());
    Collections.sort(
        oldestDocuments, (l, r) -> documentToBatchId.get(l).compareTo(documentToBatchId.get(r)));
    int i = Math.min(count, oldestDocuments.size()) - 1;

    // Include all documents that match the last document's batch id.
    int lastBatchId = documentToBatchId.get(oldestDocuments.get(i));
    while (i < oldestDocuments.size()
        && lastBatchId == documentToBatchId.get(oldestDocuments.get(i))) {
      ++i;
    }
    return oldestDocuments.subList(0, i);
  }

  /** Returns the earliest batch id from the specified field indexes. */
  private int getEarliestBatchId(Collection<FieldIndex> fieldIndexes) {
    int lowestBatchId = (int) Double.POSITIVE_INFINITY;
    for (FieldIndex fieldIndex : fieldIndexes) {
      lowestBatchId =
          Math.min(fieldIndex.getIndexState().getOffset().getLargestBatchId(), lowestBatchId);
    }
    return lowestBatchId;
  }

  @VisibleForTesting
  void setMaxDocumentsToProcess(int newMax) {
    maxDocumentsToProcess = newMax;
  }
}
