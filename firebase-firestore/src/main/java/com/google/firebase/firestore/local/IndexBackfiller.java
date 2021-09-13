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
import com.google.firebase.firestore.index.IndexEntry;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.concurrent.TimeUnit;

/** Implements the steps for backfilling indexes. */
public class IndexBackfiller {
  /** How long we wait to try running index backfill after SDK initialization. */
  private static final long INITIAL_BACKFILL_DELAY_MS = TimeUnit.SECONDS.toMillis(15);
  /** Minimum amount of time between backfill checks, after the first one. */
  private static final long REGULAR_BACKFILL_DELAY_MS = TimeUnit.MINUTES.toMillis(1);

  private final SQLitePersistence persistence;

  public IndexBackfiller(SQLitePersistence sqLitePersistence) {
    this.persistence = sqLitePersistence;
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

  // TODO(indexing): Figure out which index entries to backfill.
  public Results backfill() {
    int numIndexesWritten = 0;
    int numIndexesRemoved = 0;
    return new Results(/* hasRun= */ true, numIndexesWritten, numIndexesRemoved);
  }

  @VisibleForTesting
  void addIndexEntry(IndexEntry entry) {
    persistence.execute(
        "INSERT OR IGNORE INTO index_entries ("
            + "index_id, "
            + "index_value, "
            + "uid, "
            + "document_id) VALUES(?, ?, ?, ?)",
        entry.getIndexId(),
        entry.getIndexValue(),
        entry.getUid(),
        entry.getDocumentId());
  }

  @VisibleForTesting
  void removeIndexEntry(int indexId, String uid, String documentId) {
    persistence.execute(
        "DELETE FROM index_entries "
            + "WHERE index_id = ? "
            + "AND uid = ?"
            + "AND document_id = ?",
        indexId,
        uid,
        documentId);
    ;
  }

  @Nullable
  @VisibleForTesting
  IndexEntry getIndexEntry(int indexId) {
    return persistence
        .query("SELECT index_value, uid, document_id FROM index_entries WHERE index_id = ?")
        .binding(indexId)
        .firstValue(
            row ->
                row == null
                    ? null
                    : new IndexEntry(indexId, row.getBlob(0), row.getString(1), row.getString(2)));
  }
}
