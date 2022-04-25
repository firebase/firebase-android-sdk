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
import androidx.core.app.FrameMetricsAggregator;
import androidx.fragment.app.Fragment;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Optional;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides FrameMetrics data from an Activity's Window. Encapsulates FrameMetricsAggregator.
 *
 * <p>IMPORTANT: each recorder holds a reference to an Activity, so it is very important to
 * dereference each recorder at or before its Activity's onDestroy. Similar for Fragments.
 */
public class FrameMetricsRecorder {
  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private final Activity activity;
  private final FrameMetricsAggregator frameMetricsAggregator;
  private final Map<Fragment, PerfFrameMetrics> subTraceMap;

  private boolean isRecording = false;

  /**
   * Creates a recorder for a specific activity.
   *
   * @param activity the activity that the recorder is collecting data from.
   */
  public FrameMetricsRecorder(Activity activity) {
    this(activity, new FrameMetricsAggregator(), new HashMap<>());
  }

  @VisibleForTesting
  FrameMetricsRecorder(
      Activity activity,
      FrameMetricsAggregator frameMetricsAggregator,
      Map<Fragment, PerfFrameMetrics> subTraceMap) {
    this.activity = activity;
    this.frameMetricsAggregator = frameMetricsAggregator;
    this.subTraceMap = subTraceMap;
  }

  /** Starts recording FrameMetrics for the activity window. */
  public void start() {
    if (isRecording) {
      logger.warn(
          "FrameMetricsAggregator is already recording %s", activity.getClass().getSimpleName());
      return;
    }
    frameMetricsAggregator.add(activity);
    isRecording = true;
  }

  /**
   * Stops recording FrameMetrics for the activity window.
   *
   * @return FrameMetrics accumulated during the current recording.
   */
  public Optional<PerfFrameMetrics> stop() {
    if (!isRecording) {
      logger.warn("Cannot stop because no recording was started");
      return Optional.absent();
    }
    Optional<PerfFrameMetrics> data = this.snapshot();
    try {
      frameMetricsAggregator.remove(activity);
    } catch (IllegalArgumentException err) {
      logger.debug(
          "View not hardware accelerated. Unable to collect FrameMetrics. %s", err.toString());
      return Optional.absent();
    }
    frameMetricsAggregator.reset();
    isRecording = false;
    return data;
  }

  /**
   * Starts a sub-trace in the current recording.
   *
   * @param key a UI state to associate this sub-trace with. e.g.) fragment
   */
  public void startSubTrace(Fragment key) {
    if (!isRecording) {
      logger.warn("Cannot start sub-trace because FrameMetricsAggregator is not recording");
      return;
    }
    if (subTraceMap.containsKey(key)) {
      logger.warn(
          "Cannot start sub-trace because one is already ongoing with the key %s",
          key.getClass().getSimpleName());
      return;
    }
    Optional<PerfFrameMetrics> snapshot = this.snapshot();
    if (!snapshot.isAvailable()) {
      logger.warn("startSubTrace(%s): snapshot() failed", key.getClass().getSimpleName());
      return;
    }
    subTraceMap.put(key, snapshot.get());
  }

  /**
   * Stops the sub-trace associated with the given UI state.
   *
   * @param key the UI state associated with the sub-trace to be stopped.
   * @return FrameMetrics accumulated during this sub-trace.
   */
  public Optional<PerfFrameMetrics> stopSubTrace(Fragment key) {
    if (!isRecording) {
      logger.warn("Cannot stop sub-trace because FrameMetricsAggregator is not recording");
      return Optional.absent();
    }
    if (!subTraceMap.containsKey(key)) {
      logger.warn(
          "Sub-trace associated with key %s was not started or does not exist",
          key.getClass().getSimpleName());
      return Optional.absent();
    }
    PerfFrameMetrics snapshotStart = subTraceMap.remove(key);
    Optional<PerfFrameMetrics> snapshotEnd = this.snapshot();
    if (!snapshotEnd.isAvailable()) {
      logger.warn("stopSubTrace(%s): snapshot() failed", key.getClass().getSimpleName());
      return Optional.absent();
    }
    return Optional.of(snapshotEnd.get().subtract(snapshotStart));
  }

  /**
   * Calculate total frames, slow frames, and frozen frames from SparseIntArray[] recorded by {@link
   * FrameMetricsAggregator}.
   *
   * @return {@link PerfFrameMetrics} at the time of snapshot.
   */
  protected Optional<PerfFrameMetrics> snapshot() {
    if (!isRecording) {
      logger.warn("No recording has been started.");
      return Optional.absent();
    }
    SparseIntArray[] arr = this.frameMetricsAggregator.getMetrics();
    if (arr == null) {
      logger.warn("FrameMetricsAggregator.mMetrics is uninitialized.");
      return Optional.absent();
    }
    SparseIntArray frameTimes = arr[FrameMetricsAggregator.TOTAL_INDEX];
    if (frameTimes == null) {
      logger.warn("FrameMetricsAggregator.mMetrics[TOTAL_INDEX] is uninitialized.");
      return Optional.absent();
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
    return Optional.of(new PerfFrameMetrics(totalFrames, slowFrames, frozenFrames));
  }
}
