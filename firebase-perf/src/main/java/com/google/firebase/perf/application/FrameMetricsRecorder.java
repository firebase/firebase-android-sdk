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
import android.os.Build;
import android.util.SparseIntArray;

import androidx.annotation.VisibleForTesting;
import androidx.core.app.FrameMetricsAggregator;
import androidx.fragment.app.Fragment;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.FrameMetricsCalculator;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;
import com.google.firebase.perf.util.Optional;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides FrameMetrics data from an Activity's Window. Encapsulates FrameMetricsAggregator.
 *
 * <p>IMPORTANT: each recorder holds a reference to an Activity, so it is very important to
 * dereference each recorder at or before its Activity's onDestroy. Similar for Fragments.
 *
 * <p>Relationship of Fragment recording to Activity recording is like a stopwatch. The stopwatch
 * itself is for the Activity, Fragments traces are laps in the stopwatch. Stopwatch can only ever
 * be for the Activity by-definition because "frames" refer to a frame for a window, and
 * FrameMetrics can only come from an Activity's Window. Fragment traces are laps in the stopwatch,
 * because the frame metrics data is still for the Activity window, fragment traces are just
 * intervals in the Activity frames recording that has the name "fragment X" attached to them.
 */
public class FrameMetricsRecorder {
  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private static final String FRAME_METRICS_AGGREGATOR_CLASSNAME =
      "androidx.core.app.FrameMetricsAggregator";

  private final Activity activity;
  private final FrameMetricsAggregator frameMetricsAggregator;
  private final Map<Fragment, PerfFrameMetrics> fragmentSnapshotMap;

  private boolean isRecording = false;

  static boolean isFrameMetricsRecordingSupported() {
    try {
      Class<?> initializerClass = Class.forName(FRAME_METRICS_AGGREGATOR_CLASSNAME);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

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
    this.fragmentSnapshotMap = subTraceMap;
  }

  /** Starts recording FrameMetrics for the activity window. */
  public void start() {
    if (isRecording) {
      logger.debug(
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
      logger.debug("Cannot stop because no recording was started");
      return Optional.absent();
    }
    if (!fragmentSnapshotMap.isEmpty()) {
      // Instrumentation stops and still return activity data, but invalidate all dangling fragments
      logger.debug(
          "Sub-recordings are still ongoing! Sub-recordings should be stopped first before stopping Activity screen trace.");
      fragmentSnapshotMap.clear();
    }
    Optional<PerfFrameMetrics> data = this.snapshot();
    try {
      // No reliable way to check for hardware-acceleration, so we must catch retroactively (#2736).
      frameMetricsAggregator.remove(activity);
    } catch (IllegalArgumentException | NullPointerException ex) {
      // Both of these exceptions result from android.view.View.addFrameMetricsListener silently
      // failing when the view is not hardware-accelerated. Successful addFrameMetricsListener
      // stores an observer in a list, and initializes the list if it was uninitialized. Invoking
      // View.removeFrameMetricsListener(listener) throws IAE if it doesn't exist in the list, or
      // throws NPE if the list itself was never initialized (#4184).
      if (ex instanceof NullPointerException && Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        // Re-throw above API 28, since the NPE is fixed in API 29:
        // https://android.googlesource.com/platform/frameworks/base/+/140ff5ea8e2d99edc3fbe63a43239e459334c76b
        throw ex;
      }
      logger.warn(
          "View not hardware accelerated. Unable to collect FrameMetrics. %s", ex.toString());
      data = Optional.absent();
    }
    frameMetricsAggregator.reset();
    isRecording = false;
    return data;
  }

  /**
   * Starts a Fragment sub-recording in the current Activity recording. Fragments are sub-recordings
   * due to the way frame metrics work: frame metrics can only comes from an activity's window. An
   * analogy for sub-recording is a lap in a stopwatch.
   *
   * @param fragment a Fragment to associate this sub-trace with.
   */
  public void startFragment(Fragment fragment) {
    if (!isRecording) {
      logger.debug("Cannot start sub-recording because FrameMetricsAggregator is not recording");
      return;
    }
    if (fragmentSnapshotMap.containsKey(fragment)) {
      logger.debug(
          "Cannot start sub-recording because one is already ongoing with the key %s",
          fragment.getClass().getSimpleName());
      return;
    }
    Optional<PerfFrameMetrics> snapshot = this.snapshot();
    if (!snapshot.isAvailable()) {
      logger.debug("startFragment(%s): snapshot() failed", fragment.getClass().getSimpleName());
      return;
    }
    fragmentSnapshotMap.put(fragment, snapshot.get());
  }

  /**
   * Stops the sub-recording associated with the given Fragment. Fragments are sub-recordings due to
   * the way frame metrics work: frame metrics can only comes from an activity's window. An analogy
   * for sub-recording is a lap in a stopwatch.
   *
   * @param fragment the Fragment associated with the sub-recording to be stopped.
   * @return FrameMetrics accumulated during this sub-recording.
   */
  public Optional<PerfFrameMetrics> stopFragment(Fragment fragment) {
    if (!isRecording) {
      logger.debug("Cannot stop sub-recording because FrameMetricsAggregator is not recording");
      return Optional.absent();
    }
    if (!fragmentSnapshotMap.containsKey(fragment)) {
      logger.debug(
          "Sub-recording associated with key %s was not started or does not exist",
          fragment.getClass().getSimpleName());
      return Optional.absent();
    }
    PerfFrameMetrics snapshotStart = fragmentSnapshotMap.remove(fragment);
    Optional<PerfFrameMetrics> snapshotEnd = this.snapshot();
    if (!snapshotEnd.isAvailable()) {
      logger.debug("stopFragment(%s): snapshot() failed", fragment.getClass().getSimpleName());
      return Optional.absent();
    }
    return Optional.of(snapshotEnd.get().deltaFrameMetricsFromSnapshot(snapshotStart));
  }

  /**
   * Snapshots total frames, slow frames, and frozen frames from SparseIntArray[] recorded by {@link
   * FrameMetricsAggregator}.
   *
   * @return {@link PerfFrameMetrics} at the time of snapshot.
   */
  private Optional<PerfFrameMetrics> snapshot() {
    if (!isRecording) {
      logger.debug("No recording has been started.");
      return Optional.absent();
    }
    SparseIntArray[] arr = this.frameMetricsAggregator.getMetrics();
    if (arr == null) {
      logger.debug("FrameMetricsAggregator.mMetrics is uninitialized.");
      return Optional.absent();
    }
    SparseIntArray frameTimes = arr[FrameMetricsAggregator.TOTAL_INDEX];
    if (frameTimes == null) {
      logger.debug("FrameMetricsAggregator.mMetrics[TOTAL_INDEX] is uninitialized.");
      return Optional.absent();
    }
    return Optional.of(FrameMetricsCalculator.calculateFrameMetrics(arr));
  }
}
