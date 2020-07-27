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

import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.core.ListenSequence;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import java.util.Comparator;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

/** Implements the steps for LRU garbage collection. */
public class LruGarbageCollector {
  /** How long we wait to try running LRU GC after SDK initialization. */
  private static final long INITIAL_GC_DELAY_MS = TimeUnit.MINUTES.toMillis(1);
  /** Minimum amount of time between GC checks, after the first one. */
  private static final long REGULAR_GC_DELAY_MS = TimeUnit.MINUTES.toMillis(5);

  public static class Params {
    private static final long COLLECTION_DISABLED = FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED;
    private static final long DEFAULT_CACHE_SIZE_BYTES = 100 * 1024 * 1024; // 100mb
    /**
     * The following two constants are estimates for how we want to tune the garbage collector. If
     * we encounter a large cache, we don't want to spend a large chunk of time GCing all of it, we
     * would rather make some progress and then try again later. We also don't want to collect
     * everything that we possibly could, as our thesis is that recently used items are more likely
     * to be used again.
     */
    private static final int DEFAULT_COLLECTION_PERCENTILE = 10;

    private static final int DEFAULT_MAX_SEQUENCE_NUMBERS_TO_COLLECT = 1000;

    public static Params Default() {
      return new Params(
          DEFAULT_CACHE_SIZE_BYTES,
          DEFAULT_COLLECTION_PERCENTILE,
          DEFAULT_MAX_SEQUENCE_NUMBERS_TO_COLLECT);
    }

    public static Params Disabled() {
      return new Params(COLLECTION_DISABLED, 0, 0);
    }

    public static Params WithCacheSizeBytes(long cacheSizeBytes) {
      return new Params(cacheSizeBytes, 10, 1000);
    }

    final long minBytesThreshold;
    final int percentileToCollect;
    final int maximumSequenceNumbersToCollect;

    Params(long minBytesThreshold, int percentileToCollect, int maximumSequenceNumbersToCollect) {
      this.minBytesThreshold = minBytesThreshold;
      this.percentileToCollect = percentileToCollect;
      this.maximumSequenceNumbersToCollect = maximumSequenceNumbersToCollect;
    }
  }

  public static class Results {
    private final boolean hasRun;
    private final int sequenceNumbersCollected;
    private final int targetsRemoved;
    private final int documentsRemoved;

    static Results DidNotRun() {
      return new Results(/* hasRun= */ false, 0, 0, 0);
    }

    Results(
        boolean hasRun, int sequenceNumbersCollected, int targetsRemoved, int documentsRemoved) {
      this.hasRun = hasRun;
      this.sequenceNumbersCollected = sequenceNumbersCollected;
      this.targetsRemoved = targetsRemoved;
      this.documentsRemoved = documentsRemoved;
    }

    public boolean hasRun() {
      return hasRun;
    }

    public int getSequenceNumbersCollected() {
      return sequenceNumbersCollected;
    }

    public int getTargetsRemoved() {
      return targetsRemoved;
    }

    public int getDocumentsRemoved() {
      return documentsRemoved;
    }
  }

  /**
   * This class is responsible for the scheduling of LRU garbage collection. It handles checking
   * whether or not GC is enabled, as well as which delay to use before the next run.
   */
  public class Scheduler implements GarbageCollectionScheduler {
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
      if (params.minBytesThreshold != Params.COLLECTION_DISABLED) {
        scheduleGC();
      }
    }

    @Override
    public void stop() {
      if (gcTask != null) {
        gcTask.cancel();
      }
    }

    private void scheduleGC() {
      long delay = hasRun ? REGULAR_GC_DELAY_MS : INITIAL_GC_DELAY_MS;
      gcTask =
          asyncQueue.enqueueAfterDelay(
              AsyncQueue.TimerId.GARBAGE_COLLECTION,
              delay,
              () -> {
                localStore.collectGarbage(LruGarbageCollector.this);
                hasRun = true;
                scheduleGC();
              });
    }
  }

  private final LruDelegate delegate;
  private final Params params;

  LruGarbageCollector(LruDelegate delegate, Params params) {
    this.delegate = delegate;
    this.params = params;
  }

  /** A helper method to create a new scheduler. */
  public Scheduler newScheduler(AsyncQueue asyncQueue, LocalStore localStore) {
    return new Scheduler(asyncQueue, localStore);
  }

  /** Given a percentile of target to collect, returns the number of targets to collect. */
  int calculateQueryCount(int percentile) {
    long targetCount = delegate.getSequenceNumberCount();
    return (int) ((percentile / 100.0f) * targetCount);
  }

  /**
   * Used to calculate the nth sequence number. Keeps a rolling buffer of the lowest n values passed
   * to addElement, and finally reports the largest of them in getMaxValue().
   */
  private static class RollingSequenceNumberBuffer {
    // Invert the comparison because we want to keep the smallest values.
    private static final Comparator<Long> COMPARATOR = (Long a, Long b) -> b.compareTo(a);
    private final PriorityQueue<Long> queue;
    private final int maxElements;

    RollingSequenceNumberBuffer(int count) {
      this.maxElements = count;
      this.queue = new PriorityQueue<>(count, COMPARATOR);
    }

    void addElement(Long sequenceNumber) {
      if (queue.size() < maxElements) {
        queue.add(sequenceNumber);
      } else {
        Long highestValue = queue.peek();
        if (sequenceNumber < highestValue) {
          queue.poll();
          queue.add(sequenceNumber);
        }
      }
    }

    long getMaxValue() {
      return queue.peek();
    }
  }

  /** Returns the nth sequence number, counting in order from the smallest. */
  long getNthSequenceNumber(int count) {
    if (count == 0) {
      return ListenSequence.INVALID;
    }
    RollingSequenceNumberBuffer buffer = new RollingSequenceNumberBuffer(count);
    delegate.forEachTarget((targetData) -> buffer.addElement(targetData.getSequenceNumber()));
    delegate.forEachOrphanedDocumentSequenceNumber(buffer::addElement);
    return buffer.getMaxValue();
  }

  /**
   * Removes targets with a sequence number equal to or less than the given upper bound, and removes
   * document associations with those targets.
   */
  int removeTargets(long upperBound, SparseArray<?> activeTargetIds) {
    return delegate.removeTargets(upperBound, activeTargetIds);
  }

  /**
   * Removes documents that have a sequence number equal to or less than the upper bound and are not
   * otherwise pinned.
   */
  int removeOrphanedDocuments(long upperBound) {
    return delegate.removeOrphanedDocuments(upperBound);
  }

  Results collect(SparseArray<?> activeTargetIds) {
    if (params.minBytesThreshold == Params.COLLECTION_DISABLED) {
      Logger.debug("LruGarbageCollector", "Garbage collection skipped; disabled");
      return Results.DidNotRun();
    }

    long cacheSize = getByteSize();
    if (cacheSize < params.minBytesThreshold) {
      Logger.debug(
          "LruGarbageCollector",
          "Garbage collection skipped; Cache size "
              + cacheSize
              + " is lower than threshold "
              + params.minBytesThreshold);
      return Results.DidNotRun();
    } else {
      return runGarbageCollection(activeTargetIds);
    }
  }

  private Results runGarbageCollection(SparseArray<?> liveTargetIds) {
    long startTs = System.currentTimeMillis();
    int sequenceNumbers = calculateQueryCount(params.percentileToCollect);
    // Cap at the configured max
    if (sequenceNumbers > params.maximumSequenceNumbersToCollect) {
      Logger.debug(
          "LruGarbageCollector",
          "Capping sequence numbers to collect down to the maximum of "
              + params.maximumSequenceNumbersToCollect
              + " from "
              + sequenceNumbers);
      sequenceNumbers = params.maximumSequenceNumbersToCollect;
    }
    long countedTargetsTs = System.currentTimeMillis();

    long upperBound = getNthSequenceNumber(sequenceNumbers);
    long foundUpperBoundTs = System.currentTimeMillis();

    int numTargetsRemoved = removeTargets(upperBound, liveTargetIds);
    long removedTargetsTs = System.currentTimeMillis();

    int numDocumentsRemoved = removeOrphanedDocuments(upperBound);
    long removedDocumentsTs = System.currentTimeMillis();

    if (Logger.isDebugEnabled()) {
      String desc = "LRU Garbage Collection:\n";
      desc += "\tCounted targets in " + (countedTargetsTs - startTs) + "ms\n";
      desc +=
          String.format(
              Locale.ROOT,
              "\tDetermined least recently used %d sequence numbers in %dms\n",
              sequenceNumbers,
              (foundUpperBoundTs - countedTargetsTs));
      desc +=
          String.format(
              Locale.ROOT,
              "\tRemoved %d targets in %dms\n",
              numTargetsRemoved,
              (removedTargetsTs - foundUpperBoundTs));
      desc +=
          String.format(
              Locale.ROOT,
              "\tRemoved %d documents in %dms\n",
              numDocumentsRemoved,
              (removedDocumentsTs - removedTargetsTs));
      desc += String.format(Locale.ROOT, "Total Duration: %dms", (removedDocumentsTs - startTs));
      Logger.debug("LruGarbageCollector", desc);
    }
    return new Results(/* hasRun= */ true, sequenceNumbers, numTargetsRemoved, numDocumentsRemoved);
  }

  long getByteSize() {
    return delegate.getByteSize();
  }
}
