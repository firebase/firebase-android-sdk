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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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

  /** FrameMetricsAggregator misuse prevention tests. Only 1 Activity per FMA. */
  @Test
  public void frameMetricsAggregator_shouldOnlyUseTheSameSingleActivity() {
    ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);
    recorder.start();
    recorder.stop();
    recorder.start();
    recorder.stop();
    verify(fma, times(2)).add(activityCaptor.capture());
    verify(fma, times(2)).remove(activityCaptor.capture());
    List<Activity> activities = activityCaptor.getAllValues();
    assertThat(activities.stream().allMatch(a -> a == activity)).isTrue();
  }

  @Test
  public void start_whileAlreadyStarted_doesNotCallFMATwice() {
    recorder.start();
    recorder.start();
    verify(fma, times(1)).add(any());
  }

  @Test
  public void start_afterPreviousEnded_doesCallFMASuccessfully() {
    recorder.start();
    recorder.stop();
    recorder.start();
    verify(fma, times(2)).add(any());
  }

  @Test
  public void stop_whileNotStarted_returnsEmptyResult() {
    Optional<PerfFrameMetrics> result;
    result = recorder.stop();
    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  public void stop_calledTwice_returnsEmptyResult() {
    Optional<PerfFrameMetrics> result;
    recorder.start();
    recorder.stop();
    result = recorder.stop();
    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  public void startAndStop_calledInCorrectOrder_returnsValidResult() {
    Optional<PerfFrameMetrics> result;
    recorder.start();
    result = recorder.stop();
    assertThat(result.isAvailable()).isTrue();
    assertThat(result.get().getTotalFrames()).isEqualTo(3);
    assertThat(result.get().getSlowFrames()).isEqualTo(2);
    assertThat(result.get().getFrozenFrames()).isEqualTo(1);
  }

  @Test
  public void startAndStopSubTrace_activityRecordingNeverStarted_returnsEmptyResult() {
    Fragment fragment = new Fragment();
    Optional<PerfFrameMetrics> result;

    stubFrameMetricsAggregatorData(fma, frameTimes1);
    recorder.startSubTrace(fragment);
    stubFrameMetricsAggregatorData(fma, frameTimes2);
    result = recorder.stopSubTrace(fragment);
    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  public void startAndStopSubTrace_activityRecordingHasEnded_returnsEmptyResult() {
    Fragment fragment = new Fragment();
    Optional<PerfFrameMetrics> result;

    recorder.start();
    recorder.stop();
    stubFrameMetricsAggregatorData(fma, frameTimes1);
    recorder.startSubTrace(fragment);
    stubFrameMetricsAggregatorData(fma, frameTimes2);
    result = recorder.stopSubTrace(fragment);
    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  public void startAndStopSubTrace_whenCalledTwice_ignoresSecondCall() {
    Fragment fragment = new Fragment();
    Optional<PerfFrameMetrics> result1;
    Optional<PerfFrameMetrics> result2;

    // Happens only when we hook on to the same instance of fragment twice. Very unlikely.
    recorder.start();
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 1}, {17, 1}, {800, 1}}); // 3, 2, 1
    recorder.startSubTrace(fragment);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 2}, {17, 3}, {800, 2}}); // 7, 5, 2
    recorder.startSubTrace(fragment);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 5}, {17, 4}, {800, 5}}); // 14, 9, 5
    result1 = recorder.stopSubTrace(fragment);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 6}, {17, 5}, {800, 5}}); // 16, 10, 5
    result2 = recorder.stopSubTrace(fragment);

    // total = 14 - 3 = 11, slow = 9 - 2 = 7, frozen = 5 - 1 = 4
    assertThat(result1.get().getTotalFrames()).isEqualTo(11);
    assertThat(result1.get().getSlowFrames()).isEqualTo(7);
    assertThat(result1.get().getFrozenFrames()).isEqualTo(4);
    assertThat(result2.isAvailable()).isFalse();
  }

  @Test
  public void stopAndStopSubTrace_whenNoSubTraceWithGivenKeyExists_returnsEmptyResult() {
    Fragment fragment1 = new Fragment();
    Fragment fragment2 = new Fragment();
    Optional<PerfFrameMetrics> result;

    recorder.start();

    recorder.startSubTrace(fragment1);
    result = recorder.stopSubTrace(fragment2);

    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  public void startAndStopSubTrace_duringActivityRecording_returnsValidResult() {
    Fragment fragment = new Fragment();
    Optional<PerfFrameMetrics> result;
    recorder.start();
    stubFrameMetricsAggregatorData(fma, frameTimes1);
    recorder.startSubTrace(fragment);
    stubFrameMetricsAggregatorData(fma, frameTimes2);
    result = recorder.stopSubTrace(fragment);
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
    Optional<PerfFrameMetrics> subTrace1;
    Optional<PerfFrameMetrics> subTrace2;
    recorder.start();
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 1}, {17, 1}, {800, 1}}); // 3, 2, 1
    recorder.startSubTrace(fragment1);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 2}, {17, 3}, {800, 2}}); // 7, 5, 2
    recorder.startSubTrace(fragment2);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 5}, {17, 4}, {800, 5}}); // 14, 9, 5
    subTrace1 = recorder.stopSubTrace(fragment1);
    stubFrameMetricsAggregatorData(fma, new int[][] {{1, 6}, {17, 5}, {800, 5}}); // 16, 10, 5
    subTrace2 = recorder.stopSubTrace(fragment2);

    // total = 14 - 3 = 11, slow = 9 - 2 = 7, frozen = 5 - 1 = 4
    assertThat(subTrace1.get().getTotalFrames()).isEqualTo(11);
    assertThat(subTrace1.get().getSlowFrames()).isEqualTo(7);
    assertThat(subTrace1.get().getFrozenFrames()).isEqualTo(4);
    // total = 16 - 7 = 9, slow = 10 - 5 = 5, frozen = 5 - 2 = 3
    assertThat(subTrace2.get().getTotalFrames()).isEqualTo(9);
    assertThat(subTrace2.get().getSlowFrames()).isEqualTo(5);
    assertThat(subTrace2.get().getFrozenFrames()).isEqualTo(3);
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
