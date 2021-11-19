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
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.Collection;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/** Implements the steps for backfilling indexes. */
public class IndexBackfiller {
  /** How long we wait to try running index backfill after SDK initialization. */
  private static final long INITIAL_BACKFILL_DELAY_MS = TimeUnit.SECONDS.toMillis(15);
  /** Minimum amount of time between backfill checks, after the first one. */
  private static final long REGULAR_BACKFILL_DELAY_MS = TimeUnit.MINUTES.toMillis(1);
  /** The maximum number of documents to process each time backfill() is called. */
  private static final int MAX_DOCUMENTS_TO_PROCESS = 50;

  private final Scheduler scheduler;
  private final Persistence persistence;
  private LocalDocumentsView localDocumentsView;
  private IndexManager indexManager;
  private int maxDocumentsToProcess = MAX_DOCUMENTS_TO_PROCESS;

  public IndexBackfiller(Persistence persistence, AsyncQueue asyncQueue) {
    this.persistence = persistence;
    this.scheduler = new Scheduler(asyncQueue);
  }

  public void setLocalDocumentsView(LocalDocumentsView localDocumentsView) {
    this.localDocumentsView = localDocumentsView;
  }

  public void setIndexManager(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  public static class Results {
    private final boolean hasRun;

    private final int documentsProcessed;

    static IndexBackfiller.Results DidNotRun() {
      return new IndexBackfiller.Results(/* hasRun= */ false, 0);
    }

    Results(boolean hasRun, int documentsProcessed) {
      this.hasRun = hasRun;
      this.documentsProcessed = documentsProcessed;
    }

    public boolean hasRun() {
      return hasRun;
    }

    public int getDocumentsProcessed() {
      return documentsProcessed;
    }
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
                backfill();
                hasRun = true;
                scheduleBackfill();
              });
    }
  }

  public Scheduler getScheduler() {
    return scheduler;
  }

  public Results backfill() {
    hardAssert(localDocumentsView != null, "setLocalDocumentsView() not called");
    hardAssert(indexManager != null, "setIndexManager() not called");
    return persistence.runTransaction(
        "Backfill Indexes",
        () -> {
          // TODO(indexing): Handle field indexes that are removed by the user.
          int documentsProcessed = writeIndexEntries(localDocumentsView);
          return new Results(/* hasRun= */ true, documentsProcessed);
        });
  }

  /** Writes index entries until the cap is reached. Returns the number of documents processed. */
  private int writeIndexEntries(LocalDocumentsView localDocumentsView) {
    int documentsProcessed = 0;

    // Track the starting collection group to ensure that the backfill stops after looping through
    // all collections a single time.
    String startingCollectionGroup = indexManager.getNextCollectionGroupToUpdate();

    // TODO(indexing): Handle pausing and resuming from the correct document if backfilling hits the
    // max doc limit while processing docs for a certain read time.
    String collectionGroup = startingCollectionGroup;
    while (collectionGroup != null && documentsProcessed < maxDocumentsToProcess) {
      int documentsRemaining = maxDocumentsToProcess - documentsProcessed;
      documentsProcessed +=
          writeEntriesForCollectionGroup(localDocumentsView, collectionGroup, documentsRemaining);
      collectionGroup = indexManager.getNextCollectionGroupToUpdate();
      if (collectionGroup == null || collectionGroup.equals(startingCollectionGroup)) {
        break;
      }
    }

    return documentsProcessed;
  }

  /** Writes entries for the fetched field indexes. */
  private int writeEntriesForCollectionGroup(
      LocalDocumentsView localDocumentsView, String collectionGroup, int documentsRemaining) {
    Query query = new Query(ResourcePath.EMPTY, collectionGroup);

    // Use the earliest updateTime of all field indexes as the base updateTime.
    SnapshotVersion earliestUpdateTime =
        getEarliestUpdateTime(indexManager.getFieldIndexes(collectionGroup));

    // TODO(indexing): Use limit queries to only fetch the required number of entries.
    // TODO(indexing): Support mutation batch Ids when sorting and writing indexes.
    ImmutableSortedMap<DocumentKey, Document> documents =
        localDocumentsView.getDocumentsMatchingQuery(query, earliestUpdateTime);

    Queue<Document> oldestDocuments = getOldestDocuments(documents, documentsRemaining);
    indexManager.updateIndexEntries(oldestDocuments);

    // Mark the collection group as fully indexed if all documents in the collection have been
    // indexed during this backfill iteration.
    if (documentsRemaining >= documents.size()) {
      indexManager.markCollectionGroupIndexed(collectionGroup);
    }

    return oldestDocuments.size();
  }

  /** Returns up to {@code count} documents sorted by read time. */
  private Queue<Document> getOldestDocuments(
      ImmutableSortedMap<DocumentKey, Document> documents, int count) {
    Queue<Document> oldestDocuments =
        new PriorityQueue<>(count + 1, (l, r) -> r.getReadTime().compareTo(l.getReadTime()));
    for (Map.Entry<DocumentKey, Document> entry : documents) {
      oldestDocuments.add(entry.getValue());
      if (oldestDocuments.size() > count) {
        oldestDocuments.poll();
      }
    }
    return oldestDocuments;
  }

  private SnapshotVersion getEarliestUpdateTime(Collection<FieldIndex> fieldIndexes) {
    SnapshotVersion lowestVersion = null;
    for (FieldIndex fieldIndex : fieldIndexes) {
      lowestVersion =
          lowestVersion == null
              ? fieldIndex.getUpdateTime()
              : fieldIndex.getUpdateTime().compareTo(lowestVersion) < 0
                  ? fieldIndex.getUpdateTime()
                  : lowestVersion;
    }
    return lowestVersion;
  }

  @VisibleForTesting
  void setMaxDocumentsToProcess(int newMax) {
    maxDocumentsToProcess = newMax;
  }
}
