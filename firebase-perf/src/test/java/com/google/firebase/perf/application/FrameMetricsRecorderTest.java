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

import static androidx.core.app.FrameMetricsAggregator.TOTAL_INDEX;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.util.SparseIntArray;
import android.view.WindowManager;
import androidx.core.app.FrameMetricsAggregator;
import androidx.fragment.app.Fragment;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;
import com.google.firebase.perf.util.Optional;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/** Unit tests for {@link com.google.firebase.perf.application.FrameMetricsRecorder}. */
@RunWith(RobolectricTestRunner.class)
public class FrameMetricsRecorderTest extends FirebasePerformanceTestBase {
  private final int[][] frameTimesDefault = {{1, 1}, {17, 1}, {800, 1}};
  private final int[][] frameTimes1 = {{1, 1}, {17, 3}, {800, 1}};
  private final int[][] frameTimes2 = {{1, 5}, {18, 5}, {800, 4}};

  private Activity activity;
  private FrameMetricsRecorder recorder;

  @Mock private FrameMetricsAggregator fma;

  @Before
  public void setUp() {
    initMocks(this);
    activity = createFakeActivity(true);
    recorder = new FrameMetricsRecorder(activity, fma, new HashMap<>());
    stubFrameMetricsAggregatorData(fma, frameTimesDefault);
  }

  @Test
  public void stop_whileNotStarted_returnsEmptyResult() {
    // nothing ever started
    Optional<PerfFrameMetrics> result = recorder.stop();
    assertThat(result.isAvailable()).isFalse();
    // previous recording ended but no one has not started
    recorder.start();
    recorder.stop();
    Optional<PerfFrameMetrics> result2 = recorder.stop();
    assertThat(result2.isAvailable()).isFalse();
  }

  @Test
  public void startAndStop_calledTwice_ignoresSecondCall() {
    recorder.start();
    recorder.start(); // 2nd call is ignored
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 1}, {17, 3}, {800, 1}});
    Optional<PerfFrameMetrics> result1 = recorder.stop();
    stubFrameMetricsAggregatorData(fma, frameTimes2); // 2nd call is ignored
    Optional<PerfFrameMetrics> result2 = recorder.stop();

    assertThat(result1.isAvailable()).isTrue();
    assertThat(result2.isAvailable()).isFalse();

    assertThat(result1.get().getTotalFrames()).isEqualTo(1 + 3 + 1);
    assertThat(result1.get().getSlowFrames()).isEqualTo(3 + 1);
    assertThat(result1.get().getFrozenFrames()).isEqualTo(1);
  }

  @Test
  public void startAndStop_calledInCorrectOrder_returnsValidResult() {
    recorder.start();
    Optional<PerfFrameMetrics> result = recorder.stop();
    assertThat(result.isAvailable()).isTrue();
    assertThat(result.get().getTotalFrames()).isEqualTo(3);
    assertThat(result.get().getSlowFrames()).isEqualTo(2);
    assertThat(result.get().getFrozenFrames()).isEqualTo(1);
  }

  @Test
  public void startAndStopSubTrace_activityRecordingNeverStarted_returnsEmptyResult() {
    Fragment fragment = new Fragment();

    stubFrameMetricsAggregatorData(fma, frameTimes1);
    recorder.startFragment(fragment);
    stubFrameMetricsAggregatorData(fma, frameTimes2);
    Optional<PerfFrameMetrics> result = recorder.stopFragment(fragment);
    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  public void startAndStopSubTrace_activityRecordingHasEnded_returnsEmptyResult() {
    Fragment fragment = new Fragment();

    recorder.start();
    recorder.stop();
    stubFrameMetricsAggregatorData(fma, frameTimes1);
    recorder.startFragment(fragment);
    stubFrameMetricsAggregatorData(fma, frameTimes2);
    Optional<PerfFrameMetrics> result = recorder.stopFragment(fragment);
    assertThat(result.isAvailable()).isFalse();
  }

  /**
   * This scenario happens only when we hook on to the same instance of fragment twice, which should
   * never happen
   */
  @Test
  public void startAndStopSubTrace_whenCalledTwice_ignoresSecondCall() {
    Fragment fragment = new Fragment();

    // comments are in this format: total frames, slow frames, frozen frames
    recorder.start();
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 1}, {17, 1}, {800, 1}}); // 3, 2, 1
    recorder.startFragment(fragment);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 2}, {17, 3}, {800, 2}}); // ignored
    recorder.startFragment(fragment); // this call is ignored
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 5}, {17, 4}, {800, 5}}); // 14, 9, 5
    Optional<PerfFrameMetrics> result1 = recorder.stopFragment(fragment);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 6}, {17, 5}, {800, 5}}); // ignored
    Optional<PerfFrameMetrics> result2 = recorder.stopFragment(fragment); // this call is ignored

    // total = 14 - 3 = 11, slow = 9 - 2 = 7, frozen = 5 - 1 = 4
    assertThat(result1.get().getTotalFrames()).isEqualTo(14 - 3);
    assertThat(result1.get().getSlowFrames()).isEqualTo(9 - 2);
    assertThat(result1.get().getFrozenFrames()).isEqualTo(5 - 1);
    assertThat(result2.isAvailable()).isFalse();
  }

  @Test
  public void stopAndStopSubTrace_whenNoSubTraceWithGivenKeyExists_returnsEmptyResult() {
    Fragment fragment1 = new Fragment();
    Fragment fragment2 = new Fragment();

    recorder.start();

    recorder.startFragment(fragment1);
    Optional<PerfFrameMetrics> result = recorder.stopFragment(fragment2);

    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  public void startAndStopSubTrace_duringActivityRecording_returnsValidResult() {
    Fragment fragment = new Fragment();
    recorder.start();
    stubFrameMetricsAggregatorData(fma, frameTimes1);
    recorder.startFragment(fragment);
    stubFrameMetricsAggregatorData(fma, frameTimes2);
    Optional<PerfFrameMetrics> result = recorder.stopFragment(fragment);
    assertThat(result.isAvailable()).isTrue();
    // frameTimes2 - frameTimes1
    assertThat(result.get().getTotalFrames()).isEqualTo(9);
    assertThat(result.get().getSlowFrames()).isEqualTo(5);
    assertThat(result.get().getFrozenFrames()).isEqualTo(3);
  }

  @Test
  public void startAndStopSubTrace_whenTwoSubTracesOverlap_returnsCorrectResults() {
    Fragment fragment1 = new Fragment();
    Fragment fragment2 = new Fragment();
    recorder.start();
    // comments are in this format: total frames, slow frames, frozen frames
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 1}, {17, 1}, {800, 1}}); // 3, 2, 1
    recorder.startFragment(fragment1);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 2}, {17, 3}, {800, 2}}); // 7, 5, 2
    recorder.startFragment(fragment2);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 5}, {17, 4}, {800, 5}}); // 14, 9, 5
    Optional<PerfFrameMetrics> subTrace1 = recorder.stopFragment(fragment1);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 6}, {17, 5}, {800, 5}}); // 16, 10, 5
    Optional<PerfFrameMetrics> subTrace2 = recorder.stopFragment(fragment2);

    // 3rd snapshot - 1st snapshot
    assertThat(subTrace1.get().getTotalFrames()).isEqualTo(14 - 3);
    assertThat(subTrace1.get().getSlowFrames()).isEqualTo(9 - 2);
    assertThat(subTrace1.get().getFrozenFrames()).isEqualTo(5 - 1);
    // 4th snapshot - 2nd snapshot
    assertThat(subTrace2.get().getTotalFrames()).isEqualTo(16 - 7);
    assertThat(subTrace2.get().getSlowFrames()).isEqualTo(10 - 5);
    assertThat(subTrace2.get().getFrozenFrames()).isEqualTo(5 - 2);
  }

  /**
   * This case happens when AppStateMonitor calls stop() before all Fragment traces are stopped by
   * stopFragment(fragment), leaving some dangling fragment traces that are invalid. Even if
   * activity recording starts again later, then stopFragment(fragment) is called, it should not
   * return a result which is incorrect.
   */
  @Test
  public void
      startAndStopSubTrace_notContainedWithinActivityRecordingStartAndStop_returnsEmptyResult() {
    Fragment fragment = new Fragment();
    recorder.start();
    stubFrameMetricsAggregatorData(fma, frameTimesDefault);
    recorder.startFragment(fragment);
    stubFrameMetricsAggregatorData(fma, frameTimes1);
    recorder.stop();
    recorder.start();
    stubFrameMetricsAggregatorData(fma, frameTimes2);
    Optional<PerfFrameMetrics> result = recorder.stopFragment(fragment); // invalid dangling trace
    assertThat(result.isAvailable()).isFalse();
  }

  private static Activity createFakeActivity(boolean isHardwareAccelerated) {
    ActivityController<Activity> fakeActivityController = Robolectric.buildActivity(Activity.class);

    if (isHardwareAccelerated) {
      fakeActivityController
          .get()
          .getWindow()
          .addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    } else {
      fakeActivityController
          .get()
          .getWindow()
          .clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    }

    return fakeActivityController.start().get();
  }

  private static SparseIntArray createFrameMetricsAggregatorData(int[][] frameTimes) {
    SparseIntArray sparseIntArray = new SparseIntArray();
    for (int[] bucket : frameTimes) {
      sparseIntArray.append(bucket[0], bucket[1]);
    }
    return sparseIntArray;
  }

  private static void stubFrameMetricsAggregatorData(
      FrameMetricsAggregator mockFMA, int[][] frameTimes) {
    SparseIntArray totalIndexValue = createFrameMetricsAggregatorData(frameTimes);
    SparseIntArray[] mMetrics = new SparseIntArray[9];
    mMetrics[TOTAL_INDEX] = totalIndexValue;
    doReturn(mMetrics).when(mockFMA).getMetrics();
    doReturn(mMetrics).when(mockFMA).remove(any());
    doReturn(mMetrics).when(mockFMA).reset();
  }
}
