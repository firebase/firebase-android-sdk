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

import static androidx.core.app.FrameMetricsAggregator.TOTAL_INDEX;
import static com.google.common.truth.Truth.assertThat;

import android.util.SparseIntArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link PerfFrameMetricsAggregator}. */
@RunWith(RobolectricTestRunner.class)
public class PerfFrameMetricsAggregatorTest {

  @Test
  public void recordFrameDuration_roundsToNearestMsAndBuckets() {
    PerfFrameMetricsAggregator aggregator = new PerfFrameMetricsAggregator();
    aggregator.recordFrameDurationNanos(16_400_000L); // 16ms
    aggregator.recordFrameDurationNanos(16_600_000L); // 17ms
    aggregator.recordFrameDurationNanos(16_700_000L); // 17ms

    SparseIntArray total = aggregator.getMetrics()[TOTAL_INDEX];
    assertThat(total.get(16)).isEqualTo(1);
    assertThat(total.get(17)).isEqualTo(2);
  }

  @Test
  public void recordFrameDuration_negativeDuration_isIgnored() {
    PerfFrameMetricsAggregator aggregator = new PerfFrameMetricsAggregator();
    aggregator.recordFrameDurationNanos(-5_000_000L);

    assertThat(aggregator.getMetrics()[TOTAL_INDEX].size()).isEqualTo(0);
  }

  @Test
  public void getMetrics_returnsDefensiveCopy() {
    PerfFrameMetricsAggregator aggregator = new PerfFrameMetricsAggregator();
    SparseIntArray before = aggregator.getMetrics()[TOTAL_INDEX];
    aggregator.recordFrameDurationNanos(5_000_000L);

    assertThat(before.size()).isEqualTo(0);
    assertThat(aggregator.getMetrics()[TOTAL_INDEX].get(5)).isEqualTo(1);
  }

  @Test
  public void reset_clearsMetrics() {
    PerfFrameMetricsAggregator aggregator = new PerfFrameMetricsAggregator();
    aggregator.recordFrameDurationNanos(5_000_000L);
    aggregator.reset();

    assertThat(aggregator.getMetrics()[TOTAL_INDEX].size()).isEqualTo(0);
  }

  @Test
  public void getHandlerNonBlocking_returnsHandlerWithoutWaiting() {
    // Must return immediately with a usable handler whether or not the background thread has
    // prepared its looper yet.
    assertThat(PerfFrameMetricsAggregator.getHandlerNonBlocking()).isNotNull();
  }
}
