// Copyright 2026 Google LLC
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

package com.google.firebase.perf.application;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.SparseIntArray;
import android.view.FrameMetrics;
import android.view.Window;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.FrameMetricsAggregator;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal replacement for the subset of {@link FrameMetricsAggregator} used by Firebase
 * Performance that never blocks or spawns threads on the calling (main) thread.
 *
 * <p>{@link FrameMetricsAggregator} lazily creates a {@link HandlerThread} on the first {@code
 * add()} and then calls {@link HandlerThread#getLooper()}, which waits until the new thread has
 * been scheduled. During cold start on heavily loaded devices, either {@link Thread#start()} or
 * {@link HandlerThread#getLooper()} on the main thread can exceed the input dispatch timeout and
 * cause an ANR.
 *
 * <p>Here the background thread is created and started asynchronously via {@link
 * #prewarm(Executor)} and {@link #add(Activity)} never waits for it: if its looper is not ready
 * yet, the listener is attached to the main looper instead. The listener only increments a
 * histogram bucket, so running it on the main thread during early startup is cheap.
 *
 * <p>Only {@link FrameMetrics#TOTAL_DURATION} is tracked, which is the only metric consumed by
 * {@link com.google.firebase.perf.metrics.FrameMetricsCalculator}.
 */
class PerfFrameMetricsAggregator {
  private static final int NANOS_PER_MS = 1_000_000;
  // Round to the nearest millisecond, same as FrameMetricsAggregator.
  private static final int NANOS_ROUNDING_VALUE = NANOS_PER_MS / 2;
  private static final String THREAD_NAME = "FirebasePerfFrameMetrics";
  private static final int METRICS_ARRAY_SIZE = FrameMetricsAggregator.TOTAL_INDEX + 1;

  private static final Object threadLock = new Object();

  @GuardedBy("threadLock")
  private static HandlerThread handlerThread;

  private static final AtomicBoolean prewarmSubmitted = new AtomicBoolean(false);

  // Published by the background thread itself once its Looper is prepared.
  private static volatile Handler backgroundHandler;

  // Lazily created fallback handler on the main looper.
  private static volatile Handler mainHandler;

  private final Object metricsLock = new Object();

  @GuardedBy("metricsLock")
  private SparseIntArray totalDurations = new SparseIntArray();

  private final Window.OnFrameMetricsAvailableListener listener =
      (window, frameMetrics, dropCountSinceLastInvocation) ->
          recordFrameDurationNanos(frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION));

  /**
   * Schedules creation and startup of the shared background thread on {@code executor} without
   * waiting for it or calling {@link Thread#start()} on the calling thread. Idempotent and safe to
   * call from any thread; call as early as possible (for example, when registering activity
   * lifecycle callbacks).
   */
  static void prewarm(@NonNull Executor executor) {
    if (backgroundHandler != null || !prewarmSubmitted.compareAndSet(false, true)) {
      return;
    }
    try {
      executor.execute(PerfFrameMetricsAggregator::startBackgroundThread);
    } catch (RuntimeException e) {
      // Allow a future call to retry if the executor rejected the task.
      prewarmSubmitted.set(false);
    }
  }

  private static void startBackgroundThread() {
    synchronized (threadLock) {
      if (handlerThread != null) {
        return;
      }
      handlerThread =
          new HandlerThread(THREAD_NAME) {
            @Override
            protected void onLooperPrepared() {
              // Runs on the new thread after its Looper is set, so getLooper() does not block.
              backgroundHandler = new Handler(getLooper());
            }
          };
      handlerThread.start();
    }
  }

  /**
   * Returns the background handler if its looper is ready, otherwise a main-looper handler. Never
   * blocks and never spawns a thread on the calling thread.
   */
  // Window.addOnFrameMetricsAvailableListener requires an android.os.Handler rather than an
  // Executor.
  @SuppressLint("ThreadPoolCreation")
  @VisibleForTesting
  @NonNull
  static Handler getHandlerNonBlocking() {
    Handler handler = backgroundHandler;
    if (handler != null) {
      return handler;
    }
    Handler fallback = mainHandler;
    if (fallback == null) {
      fallback = new Handler(Looper.getMainLooper());
      mainHandler = fallback;
    }
    return fallback;
  }

  @VisibleForTesting
  static void resetForTesting() {
    synchronized (threadLock) {
      if (handlerThread != null) {
        handlerThread.quit();
        handlerThread = null;
      }
      backgroundHandler = null;
      mainHandler = null;
      prewarmSubmitted.set(false);
    }
  }

  /** Starts collecting frame metrics for the activity's window. */
  void add(@NonNull Activity activity) {
    activity.getWindow().addOnFrameMetricsAvailableListener(listener, getHandlerNonBlocking());
  }

  /**
   * Stops collecting frame metrics for the activity's window.
   *
   * <p>May throw {@link IllegalArgumentException} or {@link NullPointerException} for windows that
   * are not hardware-accelerated, exactly like {@link FrameMetricsAggregator#remove(Activity)};
   * callers already handle this.
   */
  void remove(@NonNull Activity activity) {
    activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
  }

  /**
   * Returns a snapshot of the collected metrics in the same layout as {@link
   * FrameMetricsAggregator#getMetrics()}. Only {@link FrameMetricsAggregator#TOTAL_INDEX} is
   * populated.
   */
  @NonNull
  SparseIntArray[] getMetrics() {
    SparseIntArray[] metrics = new SparseIntArray[METRICS_ARRAY_SIZE];
    synchronized (metricsLock) {
      metrics[FrameMetricsAggregator.TOTAL_INDEX] = totalDurations.clone();
    }
    return metrics;
  }

  /** Clears the collected metrics. */
  void reset() {
    synchronized (metricsLock) {
      totalDurations = new SparseIntArray();
    }
  }

  @VisibleForTesting
  void recordFrameDurationNanos(long durationNanos) {
    if (durationNanos < 0) {
      return;
    }
    long durationMsLong = (durationNanos + NANOS_ROUNDING_VALUE) / NANOS_PER_MS;
    if (durationMsLong < 0 || durationMsLong > Integer.MAX_VALUE) {
      return;
    }
    int durationMs = (int) durationMsLong;
    synchronized (metricsLock) {
      totalDurations.put(durationMs, totalDurations.get(durationMs) + 1);
    }
  }
}
