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

import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.index.IndexEntry;
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
  private static int MAX_INDEX_ENTRIES_TO_PROCESS = 1000;

  private final SQLitePersistence persistence;
  private final SQLiteIndexManager indexManager;

  private int maxIndexEntriesToProcess = MAX_INDEX_ENTRIES_TO_PROCESS;

  public IndexBackfiller(SQLitePersistence sqLitePersistence) {
    this.persistence = sqLitePersistence;
    this.indexManager = (SQLiteIndexManager) sqLitePersistence.getIndexManager();
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

  public class BackfillScheduler implements Scheduler {
    private final AsyncQueue asyncQueue;
    private final LocalStore localStore;
    private boolean hasRun = false;
    @Nullable private AsyncQueue.DelayedTask backfillTask;

    public BackfillScheduler(AsyncQueue asyncQueue, LocalStore localStore) {
      this.asyncQueue = asyncQueue;
      this.localStore = localStore;
    }

    @Override
    public void start() {
      scheduleBackfill();
    }

    @Override
    public void stop() {
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
                localStore.backfillIndexes(IndexBackfiller.this);
                hasRun = true;
                scheduleBackfill();
              });
    }
  }

  public BackfillScheduler newScheduler(AsyncQueue asyncQueue, LocalStore localStore) {
    return new BackfillScheduler(asyncQueue, localStore);
  }

  public Results backfill(LocalStore localStore) {
    // TODO(indexing): Handle field indexes that are removed by the user.
    return new Results(
        /* hasRun= */ true,
        /* numIndexesWritten= */ writeIndexEntries(localStore),
        /* numIndexesRemoved= */ 0);
  }

  /** Writes index entries based on the FieldIndexQueue. Returns the number of entries written. */
  private int writeIndexEntries(LocalStore localStore) {
    int totalEntriesWrittenCount = 0;
    indexManager.loadFieldIndexes();
    Timestamp startingTimestamp = Timestamp.now();

    while (totalEntriesWrittenCount < maxIndexEntriesToProcess) {
      int entriesRemainingUnderCap = maxIndexEntriesToProcess - totalEntriesWrittenCount;
      Pair<String, List<FieldIndex>> collectionGroupPair =
          indexManager.getNextCollectionGroupToUpdate(startingTimestamp);
      if (collectionGroupPair == null) {
        break;
      }

      String collectionGroup = collectionGroupPair.first;
      List<FieldIndex> fieldIndexes = collectionGroupPair.second;
      totalEntriesWrittenCount +=
          writeEntriesForCollectionGroup(
              localStore, collectionGroup, fieldIndexes, entriesRemainingUnderCap);
    }

    return totalEntriesWrittenCount;
  }

  /**
   * Writes entries for the fetched field indexes. Requires field indexes to be loaded into memory
   * first, via {@link SQLiteIndexManager#loadFieldIndexes()}.
   */
  private int writeEntriesForCollectionGroup(
      LocalStore localStore,
      String collectionGroup,
      List<FieldIndex> fieldIndexes,
      int entriesRemainingUnderCap) {
    int entriesWrittenCount = 0;
    Query query = new Query(ResourcePath.EMPTY, collectionGroup);

    // Use the earliest updateTime of all field indexes as the base updateTime.
    SnapshotVersion earliestUpdateTime = getEarliestUpdateTime(fieldIndexes);

    // TODO(indexing): Make sure the docs matching the query are sorted by read time.
    // TODO(indexing): Use limit queries to allow incremental progress.
    // TODO(indexing): Support mutation batch Ids when sorting and writing indexes.
    ImmutableSortedMap<DocumentKey, Document> matchingDocuments =
        localStore.getDocumentsMatchingQuery(query, earliestUpdateTime);

    entriesWrittenCount +=
        indexManager.updateIndexEntries(matchingDocuments, fieldIndexes, entriesRemainingUnderCap);
    return entriesWrittenCount;
  }

  private SnapshotVersion getEarliestUpdateTime(List<FieldIndex> fieldIndexes) {
    Preconditions.checkState(!fieldIndexes.isEmpty(), "List of field indexes cannot be empty");
    SnapshotVersion lowestVersion = fieldIndexes.get(0).getVersion();
    for (FieldIndex fieldIndex : fieldIndexes) {
      lowestVersion =
          fieldIndex.getVersion().compareTo(lowestVersion) < 0
              ? fieldIndex.getVersion()
              : lowestVersion;
    }
    return lowestVersion;
  }

  @VisibleForTesting
  void setMaxIndexEntriesToProcess(int newMax) {
    maxIndexEntriesToProcess = newMax;
  }

  @VisibleForTesting
  void addIndexEntry(IndexEntry entry) {
    persistence.execute(
        "INSERT OR IGNORE INTO index_entries ("
            + "index_id, "
            + "array_value, "
            + "directional_value, "
            + "uid, "
            + "document_name) VALUES(?, ?, ?, ?, ?)",
        entry.getIndexId(),
        entry.getArrayValue(),
        entry.getDirectionalValue(),
        entry.getUid(),
        entry.getDocumentName());
  }

  @VisibleForTesting
  void removeIndexEntry(int indexId, String uid, String documentName) {
    persistence.execute(
        "DELETE FROM index_entries "
            + "WHERE index_id = ? "
            + "AND uid = ?"
            + "AND document_name = ?",
        indexId,
        uid,
        documentName);
    ;
  }

  @Nullable
  @VisibleForTesting
  IndexEntry getIndexEntry(int indexId) {
    return persistence
        .query(
            "SELECT array_value, directional_value, uid, document_name FROM index_entries WHERE index_id = ?")
        .binding(indexId)
        .firstValue(
            row ->
                row == null
                    ? null
                    : new IndexEntry(
                        indexId,
                        row.getBlob(0),
                        row.getBlob(1),
                        row.getString(2),
                        row.getString(3)));
  }
}
