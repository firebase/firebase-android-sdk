package com.google.firebase.perf.util;

import android.util.SparseIntArray;
import androidx.core.app.FrameMetricsAggregator;

/**
 *  This class is responsible for calculating the total frames, slow frames, and frozen frames from a
 *  SparseIntArray recorded by {@link FrameMetricsAggregator}.
 */
public class FrameMetricsCalculator {

  public static FrameMetrics calculateFrameMetrics(SparseIntArray[] arr) {
    if (arr == null) {
      System.out.println("*** arr is null");
      return new FrameMetrics(0, 0, 0);
    }
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
    } else {
      System.out.println("*** frameTimes is null");
    }

    // Only incrementMetric if corresponding metric is non-zero.
    return new FrameMetrics(totalFrames, slowFrames, frozenFrames);
  }

  public static class FrameMetrics {
    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;

    FrameMetrics(int totalFrames, int slowFrames, int frozenFrames) {
      this.totalFrames = totalFrames;
      this.slowFrames = slowFrames;
      this.frozenFrames = frozenFrames;
    }

    public int getTotalFrames() {
      return totalFrames;
    }

    public int getSlowFrames() {
      return slowFrames;
    }

    public int getFrozenFrames() {
      return frozenFrames;
    }
  }
}
