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

import androidx.annotation.Nullable;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.concurrent.TimeUnit;

public class IndexBackfiller {
  /** How long we wait to try running LRU GC after SDK initialization. */
  private static final long INITIAL_GC_DELAY_MS = TimeUnit.MINUTES.toMillis(1);
  /** Minimum amount of time between GC checks, after the first one. */
  private static final long REGULAR_GC_DELAY_MS = TimeUnit.MINUTES.toMillis(5);

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

  public class Scheduler implements StartStopScheduler {
    private final AsyncQueue asyncQueue;
    private final LocalStore localStore;
    private boolean hasRun = false;
    @Nullable private AsyncQueue.DelayedTask gcTask;

    public Scheduler(AsyncQueue asyncQueue, LocalStore localStore) {
      this.asyncQueue = asyncQueue;
      this.localStore = localStore;
    }

    @Override
    public void start() {
      scheduleBackfill();
    }

    @Override
    public void stop() {
      if (gcTask != null) {
        gcTask.cancel();
      }
    }

    private void scheduleBackfill() {
      long delay = hasRun ? REGULAR_GC_DELAY_MS : INITIAL_GC_DELAY_MS;
      gcTask =
          asyncQueue.enqueueAfterDelay(
              AsyncQueue.TimerId.GARBAGE_COLLECTION,
              delay,
              () -> {
                localStore.backfillIndexes(IndexBackfiller.this);
                hasRun = true;
                scheduleBackfill();
              });
    }
  }

  private final IndexBackfillerDelegate delegate;

  IndexBackfiller(IndexBackfillerDelegate delegate) {
    this.delegate = delegate;
  }

  public Scheduler newScheduler(AsyncQueue asyncQueue, LocalStore localStore) {
    return new Scheduler(asyncQueue, localStore);
  }

  // TODO: Figure out which index entries to backfill.
  Results backfill() {
    int numIndexesWritten = 0;
    int numIndexesRemoved = 0;
    delegate.addIndexEntry();
    return new Results(/* hasRun= */ true, numIndexesWritten, numIndexesRemoved);
  }
}
