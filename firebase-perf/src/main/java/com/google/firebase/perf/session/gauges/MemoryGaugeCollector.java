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

package com.google.firebase.perf.session.gauges;

import android.annotation.SuppressLint;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.StorageUnit;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.util.Utils;
import com.google.firebase.perf.v1.AndroidMemoryReading;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class collects Memory Gauge metrics and queues them up on its ConcurrentLinkedQueue. It is
 * the responsibility of the GaugeManager to drain this queue periodically.
 *
 * <p>The class methods are not generally thread safe, but it is thread safe to read and write to
 * the ConcurrentLinkedQueue.
 */
public class MemoryGaugeCollector {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  public static final long INVALID_MEMORY_COLLECTION_FREQUENCY = -1;
  // This value indicates that we do not know the frequency at which to collect Memory Metrics. If
  // this value is set for the memoryMetricCollectionRateMs, we do not collect Memory Metrics.
  private static final int UNSET_MEMORY_METRIC_COLLECTION_RATE = -1;

  private final ScheduledExecutorService memoryMetricCollectorExecutor;
  /* This is populated by MemoryGaugeCollector but it's drained by GaugeManager.*/
  public final ConcurrentLinkedQueue<AndroidMemoryReading> memoryMetricReadings;
  private final Runtime runtime;

  @Nullable private ScheduledFuture memoryMetricCollectorJob = null;
  private long memoryMetricCollectionRateMs = UNSET_MEMORY_METRIC_COLLECTION_RATE;

  // TODO(b/258263016): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  MemoryGaugeCollector() {
    this(Executors.newSingleThreadScheduledExecutor(), Runtime.getRuntime());
  }

  @VisibleForTesting
  MemoryGaugeCollector(ScheduledExecutorService memoryMetricCollectorExecutor, Runtime runtime) {
    this.memoryMetricCollectorExecutor = memoryMetricCollectorExecutor;
    memoryMetricReadings = new ConcurrentLinkedQueue<>();
    this.runtime = runtime;
  }

  /**
   * Starts collecting MemoryMetricReadings at the specified rate. If it is already collecting the
   * readings, it updates the rate at which they're being collected if a different
   * memoryMetricCollectionRateMs is specified, otherwise it does nothing.
   *
   * <p>This method is NOT thread safe.
   *
   * @param memoryMetricCollectionRateMs The rate at which to collect MemoryMetricReadings in
   *     milliseconds. Should be greater than 0.
   * @param referenceTime The reference timestamp based off of which the current timestamp is
   *     calculated when collecting cpu metrics. See go/fireperf-sessions-timestamps.
   */
  public void startCollecting(long memoryMetricCollectionRateMs, Timer referenceTime) {
    if (isInvalidCollectionFrequency(memoryMetricCollectionRateMs)) {
      return;
    }

    if (memoryMetricCollectorJob != null) {
      if (this.memoryMetricCollectionRateMs != memoryMetricCollectionRateMs) {
        stopCollecting();
        scheduleMemoryMetricCollectionWithRate(memoryMetricCollectionRateMs, referenceTime);
      }

      return;
    }

    scheduleMemoryMetricCollectionWithRate(memoryMetricCollectionRateMs, referenceTime);
  }

  /** Stops collecting MemoryMetricReadings. This method is NOT thread safe. */
  public void stopCollecting() {
    if (memoryMetricCollectorJob == null) {
      return;
    }

    memoryMetricCollectorJob.cancel(/*mayInterruptIfRunning =*/ false);
    memoryMetricCollectorJob = null;
    memoryMetricCollectionRateMs = UNSET_MEMORY_METRIC_COLLECTION_RATE;
  }

  /**
   * Collects only a single {@link AndroidMemoryReading}.
   *
   * @param referenceTime The reference timestamp based off of which the current timestamp is
   *     calculated when collecting cpu metric. See go/fireperf-sessions-timestamps.
   */
  public void collectOnce(Timer referenceTime) {
    scheduleMemoryMetricCollectionOnce(referenceTime);
  }

  // TODO(b/177945554): If it appears that startCollecting() and stopCollecting() can be called
  // from the same thread, then get rid of the synchronized.
  private synchronized void scheduleMemoryMetricCollectionWithRate(
      long memoryMetricCollectionRate, Timer referenceTime) {
    this.memoryMetricCollectionRateMs = memoryMetricCollectionRate;

    try {
      memoryMetricCollectorJob =
          memoryMetricCollectorExecutor.scheduleAtFixedRate(
              () -> {
                AndroidMemoryReading memoryReading = syncCollectMemoryMetric(referenceTime);
                if (memoryReading != null) {
                  memoryMetricReadings.add(memoryReading);
                }
              },
              /* initialDelay */ 0,
              /*period=*/ memoryMetricCollectionRate,
              TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      logger.warn("Unable to start collecting Memory Metrics: " + e.getMessage());
    }
  }

  private synchronized void scheduleMemoryMetricCollectionOnce(Timer referenceTime) {
    try {
      @SuppressWarnings("FutureReturnValueIgnored")
      ScheduledFuture unusedFuture =
          memoryMetricCollectorExecutor.schedule(
              () -> {
                AndroidMemoryReading memoryReading = syncCollectMemoryMetric(referenceTime);
                if (memoryReading != null) {
                  memoryMetricReadings.add(memoryReading);
                }
              },
              /* initialDelay */ 0,
              TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      logger.warn("Unable to collect Memory Metric: " + e.getMessage());
    }
  }

  @Nullable
  private AndroidMemoryReading syncCollectMemoryMetric(Timer referenceTime) {
    if (referenceTime == null) {
      return null;
    }

    long memoryMetricTimestampUs = referenceTime.getCurrentTimestampMicros();

    return AndroidMemoryReading.newBuilder()
        .setClientTimeUs(memoryMetricTimestampUs)
        .setUsedAppJavaHeapMemoryKb(getCurrentUsedAppJavaHeapMemoryKb())
        .build();
  }

  /**
   * Returns the amount of memory (in kilobytes) the app is using at any given point in time (the
   * returned value is subject to unpredictable variations depending on when Garbage Collection
   * runs).
   */
  private int getCurrentUsedAppJavaHeapMemoryKb() {
    return Utils.saturatedIntCast(
        StorageUnit.BYTES.toKilobytes(runtime.totalMemory() - runtime.freeMemory()));
  }

  /** Returns {@code true} if the {@code collectionFrequency} is invalid. */
  public static boolean isInvalidCollectionFrequency(long collectionFrequency) {
    return collectionFrequency <= 0;
  }
}
