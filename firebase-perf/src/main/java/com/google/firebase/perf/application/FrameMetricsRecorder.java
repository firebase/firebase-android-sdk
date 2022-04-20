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

package com.google.firebase.perf.application;

import android.app.Activity;
import android.util.SparseIntArray;
import androidx.annotation.NonNull;
import androidx.core.app.FrameMetricsAggregator;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;
import com.google.firebase.perf.util.Constants;
import java.util.WeakHashMap;

/**
 * Provides FrameMetrics data from an Activity's Window. Encapsulates FrameMetricsAggregator.
 *
 * <p>Note: each recorder holds a reference to an Activity, so it is very important to dereference
 * each recorder before the associated Activity is destroyed
 */
public class FrameMetricsRecorder {
  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private final Activity activity;
  private final FrameMetricsAggregator fma;
  private final WeakHashMap<Object, PerfFrameMetrics> subTraceMap;
  private boolean isRecording = false;

  /**
   * Creates a recorder for a specific activity
   *
   * @param activity the activity that the recorder is collecting data from
   */
  public FrameMetricsRecorder(Activity activity) {
    this(activity, new FrameMetricsAggregator(), new WeakHashMap<>());
  }

  @VisibleForTesting
  FrameMetricsRecorder(
      Activity activity,
      FrameMetricsAggregator frameMetricsAggregator,
      WeakHashMap<Object, PerfFrameMetrics> subTraceMap) {
    this.activity = activity;
    this.fma = frameMetricsAggregator;
    this.subTraceMap = subTraceMap;
  }

  /** Starts recording FrameMetrics for the activity window */
  public void start() {
    if (isRecording) {
      logger.error(
          "FrameMetricsAggregator is already recording %s", activity.getClass().getSimpleName());
      return;
    }
    fma.add(activity);
    isRecording = true;
  }

  /**
   * Stops recording FrameMetrics for the activity window
   *
   * @return FrameMetrics accumulated during the current recording
   */
  public PerfFrameMetrics stop() {
    if (!isRecording) {
      logger.error("Cannot stop because no recording was started");
      return null;
    }
    PerfFrameMetrics data = this.snapshot();
    try {
      fma.remove(activity);
    } catch (IllegalArgumentException ignored) {
      logger.error("View not hardware accelerated. Unable to collect FrameMetrics.");
    }
    fma.reset();
    isRecording = false;
    return data;
  }

  /**
   * Starts a sub-trace in the current recording
   *
   * @param key a UI state to associate this sub-trace with. e.g.) fragment
   */
  public void startSubTrace(Object key) {
    if (!isRecording) {
      logger.error("Cannot start sub-trace because FrameMetricsAggregator is not recording");
      return;
    }
    if (subTraceMap.containsKey(key)) {
      logger.error(
          "Cannot start sub-trace because one is already ongoing with the key %s",
          key.getClass().getSimpleName());
      return;
    }
    subTraceMap.put(key, this.snapshot());
  }

  /**
   * Stops the sub-trace associated with the given UI state
   *
   * @param key the UI state associated with the sub-trace to be stopped
   * @return FrameMetrics accumulated during this sub-trace
   */
  public PerfFrameMetrics stopSubTrace(Object key) {
    if (!isRecording) {
      logger.error("Cannot stop sub-trace because FrameMetricsAggregator is not recording");
      return null;
    }
    if (!subTraceMap.containsKey(key)) {
      logger.error(
          "Sub-trace associated with key %s was not started or does not exist",
          key.getClass().getSimpleName());
      return null;
    }
    PerfFrameMetrics snapshotStart = subTraceMap.remove(key);
    PerfFrameMetrics snapshotEnd = this.snapshot();
    return snapshotEnd.subtract(snapshotStart);
  }

  /**
   * Calculate total frames, slow frames, and frozen frames from SparseIntArray[] recorded by {@link
   * FrameMetricsAggregator}.
   *
   * @return the frame metrics
   */
  protected @NonNull PerfFrameMetrics snapshot() {
    if (!isRecording) {
      throw new IllegalStateException("No recording has been started.");
    }
    SparseIntArray[] arr = this.fma.getMetrics();
    if (arr == null) {
      throw new IllegalStateException("FrameMetricsAggregator.mMetrics is uninitialized.");
    }
    SparseIntArray frameTimes = arr[FrameMetricsAggregator.TOTAL_INDEX];
    if (frameTimes == null) {
      throw new IllegalStateException(
          "FrameMetricsAggregator.mMetrics[TOTAL_INDEX] is uninitialized.");
    }
    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;

    for (int i = 0; i < frameTimes.size(); i++) {
      int frameTime = frameTimes.keyAt(i);
      int numFrames = frameTimes.valueAt(i);
      totalFrames += numFrames;
      if (frameTime > Constants.FROZEN_FRAME_TIME) {
        // Frozen frames mean the app appear frozen.  The recommended thresholds is 700ms
        frozenFrames += numFrames;
      }
      if (frameTime > Constants.SLOW_FRAME_TIME) {
        // Slow frames are anything above 16ms (i.e. 60 frames/second)
        slowFrames += numFrames;
      }
    }
    // Only incrementMetric if corresponding metric is non-zero.
    return new PerfFrameMetrics(totalFrames, slowFrames, frozenFrames);
  }
}
