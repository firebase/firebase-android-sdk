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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.util.SparseIntArray;
import android.view.WindowManager;
import androidx.core.app.FrameMetricsAggregator;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;
import java.util.List;
import java.util.WeakHashMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/** Unit tests for {@link com.google.firebase.perf.application.FrameMetricsRecorder}. */
@RunWith(RobolectricTestRunner.class)
public class FrameMetricsRecorderTest extends FirebasePerformanceTestBase {
  private Activity activity;
  private FrameMetricsRecorder recorder;

  @Mock private FrameMetricsAggregator fma;
  @Mock private PerfFrameMetrics frameMetrics1;
  @Mock private PerfFrameMetrics frameMetrics2;

  @Spy private final WeakHashMap<Object, PerfFrameMetrics> subTraceMap = new WeakHashMap<>();

  @Before
  public void setUp() {
    initMocks(this);
    activity = createFakeActivity(true);
    recorder = spy(new FrameMetricsRecorder(activity, fma, subTraceMap));
  }

  @Test
  public void eachInstance_usesOneAndTheSameActivityInFrameMetricsAggregator_always() {
    stubSnapshotToDoNothing();
    ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);
    recorder.start();
    recorder.stop();
    recorder.start();
    recorder.stop();
    verify(fma, times(2)).add(activityCaptor.capture());
    verify(fma, times(2)).remove(activityCaptor.capture());
    List<Activity> activities = activityCaptor.getAllValues();
    Assert.assertTrue(activities.stream().allMatch(a -> a == activity));
  }

  @Test
  public void start_callsFrameMetricsAggregator() {
    verify(fma, times(0)).add(nullable(Activity.class));
    recorder.start();
    verify(fma, times(1)).add(nullable(Activity.class));
  }

  @Test
  public void start_whileAlreadyStarted_fails() {
    recorder.start();
    recorder.start();
    verify(fma, times(1)).add(nullable(Activity.class));
  }

  @Test
  public void start_afterPreviousEnded_succeeds() {
    stubSnapshotToDoNothing();
    recorder.start();
    recorder.stop();
    recorder.start();
    verify(fma, times(2)).add(nullable(Activity.class));
  }

  @Test
  public void stop_callsFrameMetricsAggregator() {
    stubSnapshotToDoNothing();
    recorder.start();
    recorder.stop();
    verify(fma, times(1)).remove(nullable(Activity.class));
    verify(fma, times(1)).reset();
  }

  @Test
  public void stop_whileNotStarted_fails() {
    stubSnapshotToDoNothing();
    recorder.stop();
    verify(fma, times(0)).remove(nullable(Activity.class));
    verify(fma, times(0)).reset();
    recorder.start();
    recorder.stop();
    recorder.stop();
    verify(fma, times(1)).remove(nullable(Activity.class));
    verify(fma, times(1)).reset();
  }

  @Test
  public void stop_snapshotsFrameMetricsAggregator_beforeReset() {
    stubSnapshotToDoNothing();
    recorder.start();
    recorder.stop();
    InOrder orderVerifier = inOrder(recorder, fma);
    orderVerifier.verify(recorder).snapshot();
    orderVerifier.verify(fma).reset();
  }

  @Test
  public void startSubTrace_whenNotRecording_fails() {
    stubSnapshotToDoNothing();
    Object uiState = new Object();
    recorder.startSubTrace(uiState);
    verify(subTraceMap, times(0)).put(nullable(Object.class), nullable(PerfFrameMetrics.class));

    recorder.start();
    recorder.stop();
    recorder.startSubTrace(uiState);
    verify(subTraceMap, times(0)).put(nullable(Object.class), nullable(PerfFrameMetrics.class));
  }

  @Test
  public void startSubTrace_whenSameSubTraceWithGivenKeyIsAlreadyOngoing_fails() {
    doReturn(frameMetrics1).when(recorder).snapshot();
    ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);
    Object uiState1 = new Object();
    Object uiState2 = new Object();

    recorder.start();
    recorder.startSubTrace(uiState1);
    verify(subTraceMap, times(1)).put(objectCaptor.capture(), nullable(PerfFrameMetrics.class));
    Assert.assertSame(frameMetrics1, subTraceMap.get(uiState1));
    Assert.assertSame(uiState1, objectCaptor.getValue());
    Assert.assertNotSame(uiState2, objectCaptor.getValue());

    recorder.startSubTrace(uiState1);
    verify(subTraceMap, times(1)).put(nullable(Object.class), nullable(PerfFrameMetrics.class));
  }

  @Test
  public void startSubTrace_whenSucceeds_putsNewEntryInMap() {
    doReturn(frameMetrics1).when(recorder).snapshot();
    Object uiState1 = new Object();
    Object uiState2 = new Object();
    recorder.start();
    recorder.startSubTrace(uiState1);
    Assert.assertSame(frameMetrics1, subTraceMap.get(uiState1));

    doReturn(frameMetrics2).when(recorder).snapshot();
    recorder.startSubTrace(uiState2);
    Assert.assertSame(frameMetrics2, subTraceMap.get(uiState2));
  }

  @Test
  public void stopSubTrace_whenNotRecording_fails() {
    stubSnapshotToDoNothing();
    Object uiState = new Object();
    subTraceMap.put(uiState, frameMetrics1);
    Assert.assertTrue(subTraceMap.containsKey(uiState));

    recorder.stopSubTrace(uiState);
    verify(subTraceMap, times(0)).remove(nullable(Object.class));

    recorder.start();
    recorder.stop();
    recorder.startSubTrace(uiState);
    verify(subTraceMap, times(0)).remove(nullable(Object.class));
  }

  @Test
  public void stopSubTrace_whenNoSubTraceWithGivenKeyExists_fails() {
    doReturn(frameMetrics1).when(recorder).snapshot();
    Object uiState1 = new Object();
    Object uiState2 = new Object();
    subTraceMap.put(uiState2, frameMetrics2);

    recorder.start();
    recorder.stopSubTrace(uiState1);
    verify(subTraceMap, times(0)).remove(nullable(Object.class));
  }

  @Test
  public void stopSubTrace_whenSucceeds_removesEntryInMap() {
    doReturn(frameMetrics2).when(recorder).snapshot();
    Object uiState1 = new Object();
    recorder.start();
    subTraceMap.put(uiState1, frameMetrics1);
    Assert.assertEquals(1, subTraceMap.size());
    recorder.stopSubTrace(uiState1);

    Assert.assertEquals(0, subTraceMap.size());
  }

  @Test
  public void stopSubTrace_whenSucceeds_returnsDifferenceBetweenSnapshots() {
    recorder.start();
    Object uiState1 = new Object();
    doReturn(frameMetrics1).when(recorder).snapshot();
    recorder.startSubTrace(uiState1);

    doReturn(frameMetrics2).when(recorder).snapshot();
    PerfFrameMetrics difference = mock(PerfFrameMetrics.class);
    doReturn(difference).when(frameMetrics2).subtract(argThat(arg -> arg == frameMetrics1));

    PerfFrameMetrics result = recorder.stopSubTrace(uiState1);
    Assert.assertSame(difference, result);
  }

  @Test
  public void snapshot_invokedAfterStartBeforeStop_doesNotThrow() {
    FrameMetricsAggregator fma = new FrameMetricsAggregator();
    FrameMetricsRecorder recorder = new FrameMetricsRecorder(activity, fma, subTraceMap);
    recorder.start();
    recorder.snapshot();
  }

  @Test(expected = IllegalStateException.class)
  public void snapshot_invokedBeforeStart_throwsIllegalStateException() {
    FrameMetricsAggregator fma = new FrameMetricsAggregator();
    FrameMetricsRecorder recorder = new FrameMetricsRecorder(activity, fma, subTraceMap);
    recorder.snapshot();
  }

  @Test(expected = IllegalStateException.class)
  public void snapshot_invokedAfterStop_throwsIllegalStateException() {
    FrameMetricsAggregator fma = new FrameMetricsAggregator();
    FrameMetricsRecorder recorder = new FrameMetricsRecorder(activity, fma, subTraceMap);
    try {
      recorder.start();
      recorder.stop();
    } catch (Exception ignored) {
    }
    recorder.snapshot();
  }

  @Test(expected = IllegalStateException.class)
  public void snapshot_sparseIntArrayIsNull_throwsIllegalStateException() {
    doReturn(null).when(fma).getMetrics();
    doCallRealMethod().when(recorder).snapshot();
    try {
      recorder.start();
    } catch (Exception ignored) {
    }
    recorder.snapshot();
  }

  @Test(expected = IllegalStateException.class)
  public void snapshot_sparseIntArrayTotalIndexIsNull_throwsIllegalStateException() {
    SparseIntArray[] arr = new SparseIntArray[1];
    arr[FrameMetricsAggregator.TOTAL_INDEX] = null;
    doReturn(arr).when(fma).getMetrics();
    doCallRealMethod().when(recorder).snapshot();
    try {
      recorder.start();
    } catch (Exception ignored) {
    }
    recorder.snapshot();
  }

  @Test
  public void snapshot_validSparseIntArray_returnsCorrectFrameMetrics() {
    // Slow frames have duration greater than 16ms and frozen frames have duration greater than
    // 700ms. The key value pair means (duration, num_of_samples).
    SparseIntArray sparseIntArray = new SparseIntArray();
    sparseIntArray.append(5, 3);
    sparseIntArray.append(20, 2);
    sparseIntArray.append(800, 5);
    SparseIntArray[] arr = new SparseIntArray[1];
    arr[FrameMetricsAggregator.TOTAL_INDEX] = sparseIntArray;

    doReturn(arr).when(fma).getMetrics();
    doCallRealMethod().when(recorder).snapshot();
    recorder.start();
    PerfFrameMetrics metrics = recorder.snapshot();

    // we should expect 3+2+5=10 total frames, 2+5=7 slow frames, and 5 frozen frames.
    assertThat(metrics.getTotalFrames()).isEqualTo(10);
    assertThat(metrics.getSlowFrames()).isEqualTo(7);
    assertThat(metrics.getFrozenFrames()).isEqualTo(5);
  }

  @Test
  public void snapshot_validSparseIntArrayWithoutFrozenFrames_returnsCorrectFrameMetrics() {
    // Slow frames have duration greater than 16ms and frozen frames have duration greater than
    // 700ms. The key value pair means (duration, num_of_samples).
    SparseIntArray sparseIntArray = new SparseIntArray();
    sparseIntArray.append(5, 3);
    sparseIntArray.append(20, 2);
    SparseIntArray[] arr = new SparseIntArray[1];
    arr[FrameMetricsAggregator.TOTAL_INDEX] = sparseIntArray;

    doReturn(arr).when(fma).getMetrics();
    doCallRealMethod().when(recorder).snapshot();
    recorder.start();
    PerfFrameMetrics metrics = recorder.snapshot();

    // we should expect 3+2=5 total frames, 2 slow frames, and 0 frozen frames.
    assertThat(metrics.getTotalFrames()).isEqualTo(5);
    assertThat(metrics.getSlowFrames()).isEqualTo(2);
    assertThat(metrics.getFrozenFrames()).isEqualTo(0);
  }

  @Test
  public void snapshot_validSparseIntArrayWithoutSlowFrames_returnsCorrectFrameMetrics() {
    // Slow frames have duration greater than 16ms and frozen frames have duration greater than
    // 700ms. The key value pair means (duration, num_of_samples).
    SparseIntArray sparseIntArray = new SparseIntArray();
    sparseIntArray.append(5, 3);
    sparseIntArray.append(701, 2);
    SparseIntArray[] arr = new SparseIntArray[1];
    arr[FrameMetricsAggregator.TOTAL_INDEX] = sparseIntArray;

    doReturn(arr).when(fma).getMetrics();
    doCallRealMethod().when(recorder).snapshot();
    recorder.start();
    PerfFrameMetrics metrics = recorder.snapshot();

    // we should expect 3+2=5 total frames, 0+2=2 slow frames, and 2 frozen frames.
    assertThat(metrics.getTotalFrames()).isEqualTo(5);
    assertThat(metrics.getSlowFrames()).isEqualTo(2);
    assertThat(metrics.getFrozenFrames()).isEqualTo(2);
  }

  @Test
  public void
      snapshot_validSparseIntArrayWithoutSlowFramesOrFrozenFrames_returnsCorrectFrameMetrics() {
    // Slow frames have duration greater than 16ms and frozen frames have duration greater than
    // 700ms. The key value pair means (duration, num_of_samples).
    SparseIntArray sparseIntArray = new SparseIntArray();
    sparseIntArray.append(5, 3);
    SparseIntArray[] arr = new SparseIntArray[1];
    arr[FrameMetricsAggregator.TOTAL_INDEX] = sparseIntArray;

    doReturn(arr).when(fma).getMetrics();
    doCallRealMethod().when(recorder).snapshot();
    recorder.start();
    PerfFrameMetrics metrics = recorder.snapshot();

    // we should expect 3 total frames, 0 slow frames, and 0 frozen frames.
    assertThat(metrics.getTotalFrames()).isEqualTo(3);
    assertThat(metrics.getSlowFrames()).isEqualTo(0);
    assertThat(metrics.getFrozenFrames()).isEqualTo(0);
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

  /**
   * stop() and stopSubTrace() depends on snapshot(), so stub snapshot() to test them independently
   */
  private void stubSnapshotToDoNothing() {
    PerfFrameMetrics genericFrameMetricsMock = mock(PerfFrameMetrics.class);
    doReturn(genericFrameMetricsMock).when(recorder).snapshot();
  }
}
