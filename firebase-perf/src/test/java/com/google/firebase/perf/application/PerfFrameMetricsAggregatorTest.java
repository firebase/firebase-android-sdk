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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import android.view.FrameMetrics;
import android.view.Window;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link PerfFrameMetricsAggregator}. */
@RunWith(RobolectricTestRunner.class)
public class PerfFrameMetricsAggregatorTest {

  @Before
  public void setUp() {
    PerfFrameMetricsAggregator.resetForTesting();
  }

  @After
  public void tearDown() {
    PerfFrameMetricsAggregator.resetForTesting();
  }

  @Test
  public void recordFrameDuration_roundsToNearestMsAndBuckets() {
    PerfFrameMetricsAggregator aggregator = new PerfFrameMetricsAggregator();
    aggregator.recordFrameDurationNanos(0L); // 0ms
    aggregator.recordFrameDurationNanos(16_400_000L); // 16ms
    aggregator.recordFrameDurationNanos(16_500_000L); // 17ms (rounds half up)
    aggregator.recordFrameDurationNanos(16_600_000L); // 17ms

    SparseIntArray total = aggregator.getMetrics()[TOTAL_INDEX];
    assertThat(total.get(0)).isEqualTo(1);
    assertThat(total.get(16)).isEqualTo(1);
    assertThat(total.get(17)).isEqualTo(2);
  }

  @Test
  public void recordFrameDuration_negativeOrOverflowDuration_isIgnored() {
    PerfFrameMetricsAggregator aggregator = new PerfFrameMetricsAggregator();
    aggregator.recordFrameDurationNanos(-1L);
    aggregator.recordFrameDurationNanos(-100_000L);
    aggregator.recordFrameDurationNanos(-5_000_000L);
    aggregator.recordFrameDurationNanos(Long.MAX_VALUE);

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
  public void getHandlerNonBlocking_beforePrewarm_fallsBackToMainLooper() {
    Handler handler = PerfFrameMetricsAggregator.getHandlerNonBlocking();

    assertThat(handler).isNotNull();
    assertThat(handler.getLooper()).isEqualTo(Looper.getMainLooper());
  }

  @Test
  public void prewarm_runsOnProvidedExecutorAndIsIdempotent() throws Exception {
    AtomicInteger executions = new AtomicInteger(0);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Executor countingExecutor =
        command -> {
          executions.incrementAndGet();
          executor.execute(command);
        };

    try {
      PerfFrameMetricsAggregator.prewarm(countingExecutor);
      PerfFrameMetricsAggregator.prewarm(countingExecutor);

      assertThat(executions.get()).isEqualTo(1);

      long deadlineMs = System.currentTimeMillis() + 5_000L;
      while (PerfFrameMetricsAggregator.getHandlerNonBlocking().getLooper()
              == Looper.getMainLooper()
          && System.currentTimeMillis() < deadlineMs) {
        Thread.sleep(10L);
      }
      assertThat(PerfFrameMetricsAggregator.getHandlerNonBlocking().getLooper())
          .isNotEqualTo(Looper.getMainLooper());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void prewarm_whenExecutorRejects_allowsSubsequentRetry() {
    Executor rejectingExecutor =
        command -> {
          throw new RejectedExecutionException("rejected");
        };
    AtomicInteger executions = new AtomicInteger(0);
    Executor recordingExecutor = command -> executions.incrementAndGet();

    PerfFrameMetricsAggregator.prewarm(rejectingExecutor);
    PerfFrameMetricsAggregator.prewarm(recordingExecutor);

    assertThat(executions.get()).isEqualTo(1);
  }

  @Test
  public void addAndRemove_attachesAndDetachesWindowListener() {
    Activity activity = mock(Activity.class);
    Window window = mock(Window.class);
    when(activity.getWindow()).thenReturn(window);

    PerfFrameMetricsAggregator aggregator = new PerfFrameMetricsAggregator();
    aggregator.add(activity);

    ArgumentCaptor<Window.OnFrameMetricsAvailableListener> listenerCaptor =
        ArgumentCaptor.forClass(Window.OnFrameMetricsAvailableListener.class);
    ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
    verify(window)
        .addOnFrameMetricsAvailableListener(listenerCaptor.capture(), handlerCaptor.capture());

    assertThat(handlerCaptor.getValue().getLooper()).isEqualTo(Looper.getMainLooper());

    FrameMetrics frameMetrics = mock(FrameMetrics.class);
    when(frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)).thenReturn(17_000_000L);
    listenerCaptor.getValue().onFrameMetricsAvailable(window, frameMetrics, 0);

    assertThat(aggregator.getMetrics()[TOTAL_INDEX].get(17)).isEqualTo(1);

    aggregator.remove(activity);
    verify(window).removeOnFrameMetricsAvailableListener(listenerCaptor.getValue());
  }
}
