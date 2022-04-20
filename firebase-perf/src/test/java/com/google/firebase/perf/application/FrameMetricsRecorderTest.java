package com.google.firebase.perf.application;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.view.WindowManager;

import androidx.core.app.FrameMetricsAggregator;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;

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

import java.util.List;
import java.util.WeakHashMap;

/** Unit tests for {@link com.google.firebase.perf.application.FrameMetricsRecorder}. */
@RunWith(RobolectricTestRunner.class)
public class FrameMetricsRecorderTest extends FirebasePerformanceTestBase {
  private Activity activity;
  private FrameMetricsRecorder recorder;

  @Mock private FrameMetricsAggregator fma;
  @Mock private PerfFrameMetrics frameMetrics1;
  @Mock private PerfFrameMetrics frameMetrics2;
  @Mock private PerfFrameMetrics frameMetrics3;

  @Spy
  private final WeakHashMap<Object, PerfFrameMetrics> subTraceMap = new WeakHashMap<>();

  @Before
  public void setUp() {
    initMocks(this);
    activity = createFakeActivity(true);
    recorder = spy(new FrameMetricsRecorder(activity, fma, subTraceMap, false));
    // stop() depends on snapshot(), so we stub snapshot() to test stop() independently
    doReturn(null).when(recorder).snapshot();
  }

  @Test
  public void eachInstance_usesOneAndTheSameActivityInFrameMetricsAggregator_always() {
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
    recorder.start();
    recorder.stop();
    recorder.start();
    verify(fma, times(2)).add(nullable(Activity.class));
  }

  @Test
  public void stop_callsFrameMetricsAggregator() {
    recorder.start();
    recorder.stop();
    verify(fma, times(1)).remove(nullable(Activity.class));
    verify(fma, times(1)).reset();
  }

  @Test
  public void stop_whileNotStarted_fails() {
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
    FrameMetricsRecorder recorder = new FrameMetricsRecorder(activity, fma, subTraceMap, true);
    FrameMetricsRecorder spyRecorder = spy(recorder);
    // stop() depends on snapshot(), so we stub snapshot() to test stop() independently
    doReturn(null).when(spyRecorder).snapshot();
    spyRecorder.stop();
    InOrder orderVerifier = inOrder(spyRecorder, fma);
    orderVerifier.verify(spyRecorder).snapshot();
    orderVerifier.verify(fma).reset();
  }

  @Test
  public void startSubTrace_whenNotRecording_fails() {
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
    Assert.assertSame(subTraceMap.get(uiState1), frameMetrics1);
    Assert.assertSame(objectCaptor.getValue(), uiState1);
    Assert.assertNotSame(objectCaptor.getValue(), uiState2);

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
    Assert.assertSame(subTraceMap.get(uiState1), frameMetrics1);

    doReturn(frameMetrics2).when(recorder).snapshot();
    recorder.startSubTrace(uiState2);
    Assert.assertSame(subTraceMap.get(uiState2), frameMetrics2);
  }

  @Test
  public void stopSubTrace_whenNotRecording_fails() {
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

  private static Activity createFakeActivity(boolean isHardwareAccelerated) {
    ActivityController<Activity> fakeActivityController = Robolectric.buildActivity(Activity.class);

    if (isHardwareAccelerated) {
      fakeActivityController.get().getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    } else {
      fakeActivityController.get().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    }

    return fakeActivityController.start().get();
  }
}
