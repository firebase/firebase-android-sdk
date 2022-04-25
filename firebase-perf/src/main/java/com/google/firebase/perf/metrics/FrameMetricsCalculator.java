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

package com.google.firebase.perf.metrics;

import android.util.SparseIntArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.FrameMetricsAggregator;
import com.google.firebase.perf.util.Constants;

/**
 * FrameMetricsCalculator helps calculate total frames, slow frames, and frozen frames from metrics
 * collected by {@link FrameMetricsAggregator}
 *
 * @hide
 */
public class FrameMetricsCalculator {
  public static class PerfFrameMetrics {
    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;

    public PerfFrameMetrics(int totalFrames, int slowFrames, int frozenFrames) {
      this.totalFrames = totalFrames;
      this.slowFrames = slowFrames;
      this.frozenFrames = frozenFrames;
    }

    public int getFrozenFrames() {
      return frozenFrames;
    }

    public int getSlowFrames() {
      return slowFrames;
    }

    public int getTotalFrames() {
      return totalFrames;
    }

    /**
     * Subtracts frame-time counts of the argument object from the current object.
     *
     * @param b the subtrahend PerfFrameMetrics object.
     * @return difference of this and the argument.
     */
    public PerfFrameMetrics subtract(PerfFrameMetrics b) {
      int newTotalFrames = this.totalFrames - b.getTotalFrames();
      int newSlowFrames = this.slowFrames - b.getSlowFrames();
      int newFrozenFrames = this.frozenFrames - b.getFrozenFrames();
      return new PerfFrameMetrics(newTotalFrames, newSlowFrames, newFrozenFrames);
    }
  }

  /**
   * Calculate total frames, slow frames, and frozen frames from SparseIntArray[] recorded by {@link
   * FrameMetricsAggregator}.
   *
   * @param arr the metrics data collected by {@link FrameMetricsAggregator#getMetrics()}
   * @return the frame metrics
   */
  public static @NonNull PerfFrameMetrics calculateFrameMetrics(@Nullable SparseIntArray[] arr) {
    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;

    if (arr != null) {
      SparseIntArray frameTimes = arr[FrameMetricsAggregator.TOTAL_INDEX];
      if (frameTimes != null) {
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
      }
    }
    // Only incrementMetric if corresponding metric is non-zero.
    return new PerfFrameMetrics(totalFrames, slowFrames, frozenFrames);
  }
}
