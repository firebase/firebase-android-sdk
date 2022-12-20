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

import static android.system.Os.sysconf;

import android.annotation.SuppressLint;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.system.OsConstants;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.CpuMetricReading;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class collects CPU Gauge metrics and queues them up on its ConcurrentLinkedQueue. It is the
 * responsibility of the GaugeManager to drain this queue periodically.
 *
 * <p>The class methods are not generally thread safe, but it is thread safe to read and write to
 * the ConcurrentLinkedQueue.
 */
public class CpuGaugeCollector {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  public static final long INVALID_CPU_COLLECTION_FREQUENCY = -1;

  // The /proc/[pid]/stat file gives us values in clock ticks. These have to be converted to
  // seconds. To do that, we currently use an Android 20+ API to get seconds per clock tick, and for
  // API levels below that, we return this constant value to indicate that we do not have this
  // information available.
  private static final int INVALID_SC_PER_CPU_CLOCK_TICK = -1;

  // This value indicates that we do not know the frequency at which to collect CPU Metrics. If this
  // value is set for the cpuMetricCollectionRateMs, we do not collect CPU Metrics.
  private static final int UNSET_CPU_METRIC_COLLECTION_RATE = -1;

  // The format of the /proc/[pid]/stat file is documented at
  // http://man7.org/linux/man-pages/man5/proc.5.html. It is a string which contains numbers
  // delimited by a space. These constants below denote the position of the useful values based on
  // an index that starts at 0.
  private static final int UTIME_POSITION_IN_PROC_PID_STAT = 13;
  private static final int STIME_POSITION_IN_PROC_PID_STAT = 14;
  private static final int CUTIME_POSITION_IN_PROC_PID_STAT = 15;
  private static final int CSTIME_POSITION_IN_PROC_PID_STAT = 16;

  // We need this to convert a double seconds value to a long microseconds value without losing too
  // much precision through rounding.
  // This utility isn't provided by TimeUnits.SECONDS.toMicros() - it only accepts longs.
  private static final long MICROSECONDS_PER_SECOND = TimeUnit.SECONDS.toMicros(1);

  /* This is populated by CpuGaugeCollector but it's drained by GaugeManager.*/
  public final ConcurrentLinkedQueue<CpuMetricReading> cpuMetricReadings;
  private final ScheduledExecutorService cpuMetricCollectorExecutor;
  private final String procFileName;
  private final long clockTicksPerSecond;

  @Nullable private ScheduledFuture cpuMetricCollectorJob = null;
  private long cpuMetricCollectionRateMs = UNSET_CPU_METRIC_COLLECTION_RATE;

  // TODO(b/258263016): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  CpuGaugeCollector() {
    cpuMetricReadings = new ConcurrentLinkedQueue<>();
    cpuMetricCollectorExecutor = Executors.newSingleThreadScheduledExecutor();

    int pid = android.os.Process.myPid();
    procFileName = "/proc/" + Integer.toString(pid) + "/stat";

    clockTicksPerSecond = getClockTicksPerSecond();
  }

  @VisibleForTesting
  CpuGaugeCollector(
      ScheduledExecutorService cpuMetricCollectorExecutor,
      String fakeProcFileName,
      long clockTicksPerSecond) {
    cpuMetricReadings = new ConcurrentLinkedQueue<>();
    this.cpuMetricCollectorExecutor = cpuMetricCollectorExecutor;
    procFileName = fakeProcFileName;
    this.clockTicksPerSecond = clockTicksPerSecond;
  }

  /**
   * Starts collecting CpuMetricReadings at the specified rate. If it is already collecting the
   * readings, it updates the rate at which they're being collected if a different
   * cpuMetricCollectionRateMs is specified, otherwise it does nothing.
   *
   * <p>This method is NOT thread safe.
   *
   * @param cpuMetricCollectionRateMs The rate at which to collect CpuMetricReadings in
   *     milliseconds. Should be greater than 0.
   * @param referenceTime The reference timestamp based off of which the current timestamp is
   *     calculated when collecting cpu metrics. See go/fireperf-sessions-timestamps.
   */
  public void startCollecting(long cpuMetricCollectionRateMs, Timer referenceTime) {
    if (clockTicksPerSecond == INVALID_SC_PER_CPU_CLOCK_TICK || clockTicksPerSecond == 0) {
      return;
    }

    if (isInvalidCollectionFrequency(cpuMetricCollectionRateMs)) {
      return;
    }

    if (cpuMetricCollectorJob != null) {
      if (this.cpuMetricCollectionRateMs != cpuMetricCollectionRateMs) {
        stopCollecting();
        scheduleCpuMetricCollectionWithRate(cpuMetricCollectionRateMs, referenceTime);
      }
      return;
    }
    scheduleCpuMetricCollectionWithRate(cpuMetricCollectionRateMs, referenceTime);
  }

  /** Stops collecting CPU Metric readings. This method is NOT thread safe. */
  public void stopCollecting() {
    if (cpuMetricCollectorJob == null) {
      return;
    }

    cpuMetricCollectorJob.cancel(false);
    cpuMetricCollectorJob = null;
    cpuMetricCollectionRateMs = UNSET_CPU_METRIC_COLLECTION_RATE;
  }

  /**
   * Collects only a single {@link CpuMetricReading}.
   *
   * @param referenceTime The reference timestamp based off of which the current timestamp is
   *     calculated when collecting cpu metric. See go/fireperf-sessions-timestamps.
   */
  public void collectOnce(Timer referenceTime) {
    scheduleCpuMetricCollectionOnce(referenceTime);
  }

  // TODO(b/177945554): If it appears that startCollecting() and stopCollecting() can be called
  // from the same thread, get rid of the synchronized.
  private synchronized void scheduleCpuMetricCollectionWithRate(
      long cpuMetricCollectionRate, Timer referenceTime) {
    this.cpuMetricCollectionRateMs = cpuMetricCollectionRate;
    try {
      cpuMetricCollectorJob =
          cpuMetricCollectorExecutor.scheduleAtFixedRate(
              () -> {
                CpuMetricReading currCpuReading = syncCollectCpuMetric(referenceTime);
                if (currCpuReading != null) {
                  cpuMetricReadings.add(currCpuReading);
                }
              },
              /* initialDelay */ 0,
              cpuMetricCollectionRate,
              TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      logger.warn("Unable to start collecting Cpu Metrics: " + e.getMessage());
    }
  }

  private synchronized void scheduleCpuMetricCollectionOnce(Timer referenceTime) {
    try {
      @SuppressWarnings("FutureReturnValueIgnored")
      ScheduledFuture unusedFuture =
          cpuMetricCollectorExecutor.schedule(
              () -> {
                CpuMetricReading currCpuReading = syncCollectCpuMetric(referenceTime);
                if (currCpuReading != null) {
                  cpuMetricReadings.add(currCpuReading);
                }
              },
              /* initialDelay */ 0,
              TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      logger.warn("Unable to collect Cpu Metric: " + e.getMessage());
    }
  }

  @Nullable
  private CpuMetricReading syncCollectCpuMetric(Timer referenceTime) {
    if (referenceTime == null) {
      return null;
    }

    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(procFileName))) {
      long cpuMetricTimestampUs = referenceTime.getCurrentTimestampMicros();
      String procPidStatFileContents = bufferedReader.readLine();
      String[] procFileTokens = procPidStatFileContents.split(" ");

      long utime = Long.parseLong(procFileTokens[UTIME_POSITION_IN_PROC_PID_STAT]);
      long cutime = Long.parseLong(procFileTokens[CUTIME_POSITION_IN_PROC_PID_STAT]);

      long stime = Long.parseLong(procFileTokens[STIME_POSITION_IN_PROC_PID_STAT]);
      long cstime = Long.parseLong(procFileTokens[CSTIME_POSITION_IN_PROC_PID_STAT]);

      return CpuMetricReading.newBuilder()
          .setClientTimeUs(cpuMetricTimestampUs)
          .setSystemTimeUs(convertClockTicksToMicroseconds(stime + cstime))
          .setUserTimeUs(convertClockTicksToMicroseconds(utime + cutime))
          .build();
    } catch (IOException e) {
      logger.warn("Unable to read 'proc/[pid]/stat' file: " + e.getMessage());
    } catch (ArrayIndexOutOfBoundsException | NumberFormatException | NullPointerException e) {
      logger.warn("Unexpected '/proc/[pid]/stat' file format encountered: " + e.getMessage());
    }
    return null;
  }

  private long getClockTicksPerSecond() {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      return sysconf(OsConstants._SC_CLK_TCK);
    } else {
      // TODO(b/110779408): Figure out how to collect this info for Android API 20 and below.
      return INVALID_SC_PER_CPU_CLOCK_TICK;
    }
  }

  private long convertClockTicksToMicroseconds(long clockTicks) {
    return Math.round(((double) clockTicks / clockTicksPerSecond) * MICROSECONDS_PER_SECOND);
  }

  /** Returns {@code true} if the {@code collectionFrequency} is invalid. */
  public static boolean isInvalidCollectionFrequency(long collectionFrequency) {
    return collectionFrequency <= 0;
  }
}
