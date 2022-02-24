package com.google.firebase.crashlytics.internal.send;

import static com.google.firebase.components.Preconditions.checkState;

import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CrashlyticsReportWithSessionId;
import com.google.firebase.crashlytics.internal.common.OnDemandCounter;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.settings.model.Settings;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Represents a rate limited bounded queue for sending Crashlytics reports. */
final class ReportQueue {
  private static final int MS_PER_SECOND = 1_000;
  private static final int MS_PER_MINUTE = 60_000;
  private static final int MAX_DELAY_MS = 3_600_000; // 1 hour.

  private final double ratePerMinute;
  private final double base;
  private final long stepDurationMs;

  private final int queueCapacity;
  private final BlockingQueue<Runnable> queue;
  private final ThreadPoolExecutor singleThreadExecutor;
  private final Transport<CrashlyticsReport> transport;
  private final OnDemandCounter onDemandCounter;

  private int step;
  private long lastUpdatedMs;

  ReportQueue(Transport<CrashlyticsReport> transport, Settings settings) {
    this(
        settings.onDemandUploadRatePerMinute(),
        settings.onDemandBackoffBase(),
        (long) settings.onDemandBackoffStepDurationSeconds() * MS_PER_SECOND,
        transport);
  }

  ReportQueue(
      double ratePerMinute,
      double base,
      long stepDurationMs,
      Transport<CrashlyticsReport> transport) {
    this.ratePerMinute = ratePerMinute;
    this.base = base;
    this.stepDurationMs = stepDurationMs;
    this.transport = transport;

    // The queue capacity is the per-minute rate number. // TODO(mrober): Round up to next int?
    queueCapacity = (int) ratePerMinute;
    queue = new ArrayBlockingQueue<>(queueCapacity);
    singleThreadExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue);

    step = 0;
    lastUpdatedMs = 0;

    onDemandCounter = OnDemandCounter.getInstance();
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

  /** Send the report to Crashlytics through Google DataTransport. */
  private void sendReport(
      CrashlyticsReportWithSessionId reportWithSessionId,
      TaskCompletionSource<CrashlyticsReportWithSessionId> tcs) {
    Logger.getLogger()
        .d("Sending report through Google DataTransport: " + reportWithSessionId.getSessionId());
    transport.schedule(
        Event.ofUrgent(reportWithSessionId.getReport()),
        error -> {
          if (error != null) {
            tcs.trySetException(error);
            return;
          }
          tcs.trySetResult(reportWithSessionId);
        });
  }

  private boolean isQueueAvailable() {
    return queue.size() < queueCapacity;
  }

  private boolean isQueueFull() {
    // TODO(mrober): Remove this after testing.
    checkState(queue.size() <= queueCapacity, "Queue went over capacity. Concurrency issue?");
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
