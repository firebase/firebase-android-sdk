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
          int documentsProcessed = writeIndexEntries(localDocumentsView);
          return new Results(/* hasRun= */ true, documentsProcessed);
        });
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
      documentsRemaining -=
          writeEntriesForCollectionGroup(localDocumentsView, collectionGroup, documentsRemaining);
      processedCollectionGroups.add(collectionGroup);
    }
    return maxDocumentsToProcess - documentsRemaining;
  }

  /** Writes entries for the fetched field indexes. */
  private int writeEntriesForCollectionGroup(
      LocalDocumentsView localDocumentsView, String collectionGroup, int entriesRemainingUnderCap) {
    Query query = new Query(ResourcePath.EMPTY, collectionGroup);

    // Use the earliest readTime of all field indexes as the base readtime.
    SnapshotVersion earliestReadTime =
        getEarliestReadTime(indexManager.getFieldIndexes(collectionGroup));

    // TODO(indexing): Use limit queries to only fetch the required number of entries.
    // TODO(indexing): Support mutation batch Ids when sorting and writing indexes.
    ImmutableSortedMap<DocumentKey, Document> documents =
        localDocumentsView.getDocumentsMatchingQuery(query, earliestReadTime);

    List<Document> oldestDocuments = getOldestDocuments(documents, entriesRemainingUnderCap);
    indexManager.updateIndexEntries(oldestDocuments);

    SnapshotVersion latestReadTime = getPostUpdateReadTime(oldestDocuments, earliestReadTime);
    indexManager.updateCollectionGroup(collectionGroup, latestReadTime);
    return oldestDocuments.size();
  }

  /**
   * Returns the new read time for the index.
   *
   * @param documents a list of documents sorted by read time (ascending)
   * @param currentReadTime the current read time of the index
   */
  private SnapshotVersion getPostUpdateReadTime(
      List<Document> documents, SnapshotVersion currentReadTime) {
    SnapshotVersion latestReadTime =
        documents.isEmpty()
            ? remoteDocumentCache.getLatestReadTime()
            : documents.get(documents.size() - 1).getReadTime();
    // Make sure the index does not go back in time
    latestReadTime =
        latestReadTime.compareTo(currentReadTime) > 0 ? latestReadTime : currentReadTime;
    return latestReadTime;
  }

  /** Returns up to {@code count} documents sorted by read time. */
  private List<Document> getOldestDocuments(
      ImmutableSortedMap<DocumentKey, Document> documents, int count) {
    List<Document> oldestDocuments = new ArrayList<>();
    for (Map.Entry<DocumentKey, Document> entry : documents) {
      oldestDocuments.add(entry.getValue());
    }
    Collections.sort(
        oldestDocuments,
        (l, r) -> {
          int cmp = l.getReadTime().compareTo(r.getReadTime());
          if (cmp != 0) return cmp;
          return l.getKey().compareTo(r.getKey());
        });
    return oldestDocuments.subList(0, Math.min(count, oldestDocuments.size()));
  }

  private SnapshotVersion getEarliestReadTime(Collection<FieldIndex> fieldIndexes) {
    SnapshotVersion lowestVersion = null;
    for (FieldIndex fieldIndex : fieldIndexes) {
      lowestVersion =
          lowestVersion == null
              ? fieldIndex.getIndexState().getReadTime()
              : fieldIndex.getIndexState().getReadTime().compareTo(lowestVersion) < 0
                  ? fieldIndex.getIndexState().getReadTime()
                  : lowestVersion;
    }
    return lowestVersion;
  }

  @VisibleForTesting
  void setMaxDocumentsToProcess(int newMax) {
    maxDocumentsToProcess = newMax;
  }
}
