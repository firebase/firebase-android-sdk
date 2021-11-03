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
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Preconditions;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Implements the steps for backfilling indexes. */
public class IndexBackfiller {
  /** How long we wait to try running index backfill after SDK initialization. */
  private static final long INITIAL_BACKFILL_DELAY_MS = TimeUnit.SECONDS.toMillis(15);
  /** Minimum amount of time between backfill checks, after the first one. */
  private static final long REGULAR_BACKFILL_DELAY_MS = TimeUnit.MINUTES.toMillis(1);
  /** The maximum number of entries to write each time backfill() is called. */
  private static final int MAX_INDEX_ENTRIES_TO_PROCESS = 1000;

  private final Scheduler scheduler;
  private final Persistence persistence;
  private LocalDocumentsView localDocumentsView;
  private IndexManager indexManager;
  private int maxIndexEntriesToProcess = MAX_INDEX_ENTRIES_TO_PROCESS;

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

    private final int entriesAdded;
    private final int entriesRemoved;

    static IndexBackfiller.Results DidNotRun() {
      return new IndexBackfiller.Results(/* hasRun= */ false, 0, 0);
    }

    Results(boolean hasRun, int entriesAdded, int entriesRemoved) {
      this.hasRun = hasRun;
      this.entriesAdded = entriesAdded;
      this.entriesRemoved = entriesRemoved;
    }

    public boolean hasRun() {
      return hasRun;
    }

    public int getEntriesAdded() {
      return entriesAdded;
    }

    public int getEntriesRemoved() {
      return entriesRemoved;
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
          int entriesAdded = writeIndexEntries(localDocumentsView);
          return new Results(
              /* hasRun= */ true,
              /* numIndexesWritten= */ entriesAdded,
              /* numIndexesRemoved= */ 0);
        });
  }

  /** Writes index entries until the cap is reached. Returns the number of entries written. */
  private int writeIndexEntries(LocalDocumentsView localDocumentsView) {
    int totalEntriesWrittenCount = 0;
    Timestamp startingTimestamp = Timestamp.now();

    while (totalEntriesWrittenCount < maxIndexEntriesToProcess) {
      int entriesRemainingUnderCap = maxIndexEntriesToProcess - totalEntriesWrittenCount;
      String collectionGroup = indexManager.getNextCollectionGroupToUpdate(startingTimestamp);
      if (collectionGroup == null) {
        break;
      }
      totalEntriesWrittenCount +=
          writeEntriesForCollectionGroup(
              localDocumentsView, collectionGroup, entriesRemainingUnderCap);
    }

    return totalEntriesWrittenCount;
  }

  /** Writes entries for the fetched field indexes. */
  private int writeEntriesForCollectionGroup(
      LocalDocumentsView localDocumentsView, String collectionGroup, int entriesRemainingUnderCap) {
    Query query = new Query(ResourcePath.EMPTY, collectionGroup);

    // Use the earliest updateTime of all field indexes as the base updateTime.
    SnapshotVersion earliestUpdateTime =
        getEarliestUpdateTime(indexManager.getFieldIndexes(collectionGroup));

    // TODO(indexing): Make sure the docs matching the query are sorted by read time.
    // TODO(indexing): Use limit queries to allow incremental progress.
    // TODO(indexing): Support mutation batch Ids when sorting and writing indexes.
    ImmutableSortedMap<DocumentKey, Document> matchingDocuments =
        localDocumentsView.getDocumentsMatchingQuery(query, earliestUpdateTime);

    return indexManager.updateIndexEntries(
        collectionGroup, matchingDocuments, entriesRemainingUnderCap);
  }

  private SnapshotVersion getEarliestUpdateTime(List<FieldIndex> fieldIndexes) {
    Preconditions.checkState(!fieldIndexes.isEmpty(), "List of field indexes cannot be empty");
    SnapshotVersion lowestVersion = fieldIndexes.get(0).getUpdateTime();
    for (FieldIndex fieldIndex : fieldIndexes) {
      lowestVersion =
          fieldIndex.getUpdateTime().compareTo(lowestVersion) < 0
              ? fieldIndex.getUpdateTime()
              : lowestVersion;
    }

    return lowestVersion;
  }

  @VisibleForTesting
  void setMaxIndexEntriesToProcess(int newMax) {
    maxIndexEntriesToProcess = newMax;
  }
}
