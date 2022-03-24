package com.google.firebase.perf.metrics;

import static com.google.common.truth.Truth.assertThat;

import android.util.SparseIntArray;
import androidx.core.app.FrameMetricsAggregator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link FrameMetricsCalculator}. */
@RunWith(RobolectricTestRunner.class)
public class FrameMetricsCalculatorTest {
  @Test
  public void calculateFrameMetrics_sparseIntArrayIsNull_returnsFrameMetricsWithAllZeros() {
    SparseIntArray[] arr = new SparseIntArray[1];
    arr[FrameMetricsAggregator.TOTAL_INDEX] = null;
    FrameMetricsCalculator.FrameMetrics metrics = FrameMetricsCalculator.calculateFrameMetrics(arr);
    assertThat(metrics.getTotalFrames()).isEqualTo(0);
    assertThat(metrics.getSlowFrames()).isEqualTo(0);
    assertThat(metrics.getFrozenFrames()).isEqualTo(0);
  }

  @Test
  public void calculateFrameMetrics_validSparseIntArray_returnsCorrectFrameMetrics() {
    // Slow frames have duration greater than 16ms and frozen frames have duration greater than
    // 700ms. The key value pair means (duration, num_of_samples).
    SparseIntArray sparseIntArray = new SparseIntArray();
    sparseIntArray.append(5, 3);
    sparseIntArray.append(20, 2);
    sparseIntArray.append(800, 5);
    SparseIntArray[] arr = new SparseIntArray[1];
    arr[FrameMetricsAggregator.TOTAL_INDEX] = sparseIntArray;

    FrameMetricsCalculator.FrameMetrics metrics = FrameMetricsCalculator.calculateFrameMetrics(arr);

    // we should expect 3+2+5=10 total frames, 2+5=7 slow frames, and 5 frozen frames.
    assertThat(metrics.getTotalFrames()).isEqualTo(10);
    assertThat(metrics.getSlowFrames()).isEqualTo(7);
    assertThat(metrics.getFrozenFrames()).isEqualTo(5);
  }

  @Test
  public void
      calculateFrameMetrics_validSparseIntArrayWithoutFrozenFrames_returnsCorrectFrameMetrics() {
    // Slow frames have duration greater than 16ms and frozen frames have duration greater than
    // 700ms. The key value pair means (duration, num_of_samples).
    SparseIntArray sparseIntArray = new SparseIntArray();
    sparseIntArray.append(5, 3);
    sparseIntArray.append(20, 2);
    SparseIntArray[] arr = new SparseIntArray[1];
    arr[FrameMetricsAggregator.TOTAL_INDEX] = sparseIntArray;

    FrameMetricsCalculator.FrameMetrics metrics = FrameMetricsCalculator.calculateFrameMetrics(arr);

    // we should expect 3+2=5 total frames, 2 slow frames, and 0 frozen frames.
    assertThat(metrics.getTotalFrames()).isEqualTo(5);
    assertThat(metrics.getSlowFrames()).isEqualTo(2);
    assertThat(metrics.getFrozenFrames()).isEqualTo(0);
  }

  @Test
  public void
      calculateFrameMetrics_validSparseIntArrayWithoutSlowFrames_returnsCorrectFrameMetrics() {
    // Slow frames have duration greater than 16ms and frozen frames have duration greater than
    // 700ms. The key value pair means (duration, num_of_samples).
    SparseIntArray sparseIntArray = new SparseIntArray();
    sparseIntArray.append(5, 3);
    sparseIntArray.append(701, 2);
    SparseIntArray[] arr = new SparseIntArray[1];
    arr[FrameMetricsAggregator.TOTAL_INDEX] = sparseIntArray;

    FrameMetricsCalculator.FrameMetrics metrics = FrameMetricsCalculator.calculateFrameMetrics(arr);

    // we should expect 3+2=5 total frames, 0+2=2 slow frames, and 2 frozen frames.
    assertThat(metrics.getTotalFrames()).isEqualTo(5);
    assertThat(metrics.getSlowFrames()).isEqualTo(2);
    assertThat(metrics.getFrozenFrames()).isEqualTo(2);
  }
}
