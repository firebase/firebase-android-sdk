// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.util;

import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;
import com.google.firebase.perf.metrics.Trace;

/** Utility class for screen traces. */
public class ScreenTraceUtil {
  private static final AndroidLogger logger = AndroidLogger.getInstance();

  /**
   * Set the metrics of total frames, slow frames, and frozen frames for the given screen trace.
   *
   * @param screenTrace a screen trace
   * @param perfFrameMetrics frame metrics calculated by {@link
   *     com.google.firebase.perf.metrics.FrameMetricsCalculator#calculateFrameMetrics}
   * @return the screen trace with frame metrics added.
   */
  public static Trace addFrameCounters(Trace screenTrace, PerfFrameMetrics perfFrameMetrics) {
    // Only putMetric if corresponding metric is greater than zero.
    if (perfFrameMetrics.getTotalFrames() > 0) {
      screenTrace.putMetric(
          Constants.CounterNames.FRAMES_TOTAL.toString(), perfFrameMetrics.getTotalFrames());
    }
    if (perfFrameMetrics.getSlowFrames() > 0) {
      screenTrace.putMetric(
          Constants.CounterNames.FRAMES_SLOW.toString(), perfFrameMetrics.getSlowFrames());
    }
    if (perfFrameMetrics.getFrozenFrames() > 0) {
      screenTrace.putMetric(
          Constants.CounterNames.FRAMES_FROZEN.toString(), perfFrameMetrics.getFrozenFrames());
    }
    logger.debug(
        "Screen trace: "
            + screenTrace.getName()
            + " _fr_tot:"
            + perfFrameMetrics.getTotalFrames()
            + " _fr_slo:"
            + perfFrameMetrics.getSlowFrames()
            + " _fr_fzn:"
            + perfFrameMetrics.getFrozenFrames());
    return screenTrace;
  }
}
