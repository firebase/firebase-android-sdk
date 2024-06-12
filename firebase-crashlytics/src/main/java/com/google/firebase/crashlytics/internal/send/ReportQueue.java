// Copyright 2022 Google LLC
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

package com.google.firebase.crashlytics.internal.send;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.runtime.ForcedSender;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CrashlyticsReportWithSessionId;
import com.google.firebase.crashlytics.internal.common.OnDemandCounter;
import com.google.firebase.crashlytics.internal.common.Utils;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.settings.Settings;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Represents a rate limited bounded queue for sending Crashlytics reports. */
final class ReportQueue {
  private static final int MS_PER_SECOND = 1_000;
  private static final int MS_PER_MINUTE = 60_000;
  private static final int MAX_DELAY_MS = 3_600_000; // 1 hour.
  private static final int STARTUP_DURATION_MS = 2_000; // 2 seconds.

  private final double ratePerMinute;
  private final double base;
  private final long stepDurationMs;
  private final long startTimeMs;

  private final int queueCapacity;
  private final BlockingQueue<Runnable> queue;
  private final ThreadPoolExecutor singleThreadExecutor;
  private final Transport<CrashlyticsReport> transport;
  private final OnDemandCounter onDemandCounter;

  private int step;
  private long lastUpdatedMs;

  ReportQueue(
      Transport<CrashlyticsReport> transport, Settings settings, OnDemandCounter onDemandCounter) {
    this(
        settings.onDemandUploadRatePerMinute,
        settings.onDemandBackoffBase,
        (long) settings.onDemandBackoffStepDurationSeconds * MS_PER_SECOND,
        transport,
        onDemandCounter);
  }

  // TODO(b/258263226): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  ReportQueue(
      double ratePerMinute,
      double base,
      long stepDurationMs,
      Transport<CrashlyticsReport> transport,
      OnDemandCounter onDemandCounter) {
    this.ratePerMinute = ratePerMinute;
    this.base = base;
    this.stepDurationMs = stepDurationMs;
    this.transport = transport;
    this.onDemandCounter = onDemandCounter;

    startTimeMs = SystemClock.elapsedRealtime();

    // The queue capacity is the per-minute rate number. // TODO(mrober): Round up to next int?
    queueCapacity = (int) ratePerMinute;
    queue = new ArrayBlockingQueue<>(queueCapacity);
    singleThreadExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue);

    step = 0;
    lastUpdatedMs = 0;
  }

  /**
   * Enqueue a report to send through Google DataTransport. If the queue is full, drop the report.
   *
   * <p>The report will be sent according to the per-minute rate. avoiding bursts.
   */
  TaskCompletionSource<CrashlyticsReportWithSessionId> enqueueReport(
      CrashlyticsReportWithSessionId reportWithSessionId, boolean isOnDemand) {
    synchronized (queue) {
      TaskCompletionSource<CrashlyticsReportWithSessionId> tcs = new TaskCompletionSource<>();
      if (isOnDemand) {
        onDemandCounter.incrementRecordedOnDemandExceptions();
        if (isQueueAvailable()) {
          Logger.getLogger().d("Enqueueing report: " + reportWithSessionId.getSessionId());
          Logger.getLogger().d("Queue size: " + queue.size());
          singleThreadExecutor.execute(new ReportRunnable(reportWithSessionId, tcs));

          // TODO(mrober): Avoid this, so queued tasks can still fail properly.
          // Complete the task right away to not block on-demand callers.
          Logger.getLogger().d("Closing task for report: " + reportWithSessionId.getSessionId());
          tcs.trySetResult(reportWithSessionId);

          return tcs;
        }

        calcStep();
        Logger.getLogger()
            .d("Dropping report due to queue being full: " + reportWithSessionId.getSessionId());
        onDemandCounter.incrementDroppedOnDemandExceptions();
        tcs.trySetResult(reportWithSessionId); // Complete the task right away.
        return tcs;
      }

      sendReport(reportWithSessionId, tcs);
      return tcs;
    }
  }

  // TODO(b/258263226): Migrate to go/firebase-android-executors
  @SuppressLint({"DiscouragedApi", "ThreadPoolCreation"}) // best effort only
  public void flushScheduledReportsIfAble() {
    CountDownLatch latch = new CountDownLatch(1);
    new Thread(
            () -> {
              try {
                ForcedSender.sendBlocking(transport, Priority.HIGHEST);
              } catch (Exception ignored) {
                // best effort only.
              }
              latch.countDown();
            })
        .start();
    Utils.awaitUninterruptibly(latch, 2, TimeUnit.SECONDS);
  }

  /** Send the report to Crashlytics through Google DataTransport. */
  private void sendReport(
      CrashlyticsReportWithSessionId reportWithSessionId,
      TaskCompletionSource<CrashlyticsReportWithSessionId> tcs) {
    Logger.getLogger()
        .d("Sending report through Google DataTransport: " + reportWithSessionId.getSessionId());
    boolean isStartup = (SystemClock.elapsedRealtime() - startTimeMs) < STARTUP_DURATION_MS;
    transport.schedule(
        Event.ofUrgent(reportWithSessionId.getReport()),
        error -> {
          if (error != null) {
            tcs.trySetException(error);
            return;
          }
          if (isStartup) {
            flushScheduledReportsIfAble();
          }
          tcs.trySetResult(reportWithSessionId);
        });
  }

  private boolean isQueueAvailable() {
    return queue.size() < queueCapacity;
  }

  private boolean isQueueFull() {
    return queue.size() == queueCapacity;
  }

  /** Calculate the time to delay after sending a fatal event. */
  private double calcDelay() {
    return Math.min(MAX_DELAY_MS, MS_PER_MINUTE / ratePerMinute * Math.pow(base, calcStep()));
  }

  /** Calculate the current step value, based on the stored value and current time. */
  private int calcStep() {
    if (lastUpdatedMs == 0) {
      lastUpdatedMs = now();
    }

    int delta = (int) ((now() - lastUpdatedMs) / stepDurationMs);
    int calcStep =
        isQueueFull()
            ? Math.min(100, step + delta)
            : Math.max(0, step - delta); // Step cannot go below 0.

    // Update the stored step value and last updated time if changed.
    if (step != calcStep) {
      step = calcStep;
      lastUpdatedMs = now();
    }

    return calcStep;
  }

  // TODO(mrober): Use some time provider that can be mocked in tests. SDK already has one?
  private long now() {
    return System.currentTimeMillis();
  }

  private final class ReportRunnable implements Runnable {
    private final CrashlyticsReportWithSessionId reportWithSessionId;
    private final TaskCompletionSource<CrashlyticsReportWithSessionId> tcs;

    private ReportRunnable(
        CrashlyticsReportWithSessionId reportWithSessionId,
        TaskCompletionSource<CrashlyticsReportWithSessionId> tcs) {
      this.reportWithSessionId = reportWithSessionId;
      this.tcs = tcs;
    }

    @Override
    public void run() {
      sendReport(reportWithSessionId, tcs);
      onDemandCounter.resetDroppedOnDemandExceptions();

      // Block the single thread executor to enforce the rate, with or without backoff.
      double delay = calcDelay();
      Logger.getLogger()
          .d(
              "Delay for: "
                  + String.format(Locale.US, "%.2f", delay / 1000)
                  + " s for report: "
                  + reportWithSessionId.getSessionId());
      sleep(delay);
    }
  }

  private static void sleep(double millis) {
    try {
      Thread.sleep((long) millis);
    } catch (InterruptedException ignored) {
      // nop
    }
  }
}
