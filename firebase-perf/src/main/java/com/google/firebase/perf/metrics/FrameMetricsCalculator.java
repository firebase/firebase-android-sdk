package com.google.firebase.perf.metrics;

import android.util.SparseIntArray;
import androidx.core.app.FrameMetricsAggregator;
import com.google.firebase.perf.util.Constants;

public class FrameMetricsCalculator {
  public static class FrameMetrics {
    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;

    FrameMetrics(int totalFrames, int slowFrames, int frozenFrames) {
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
  }

  /**
   * Calculate total frames, slow frames, and frozen frames from arr.
   *
   * @param arr the metrics data collected by {@link FrameMetricsAggregator}
   * @return the frame metrics
   */
  public static FrameMetrics calculateFrameMetrics(SparseIntArray[] arr) {
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
    return new FrameMetrics(totalFrames, slowFrames, frozenFrames);
  }
}
