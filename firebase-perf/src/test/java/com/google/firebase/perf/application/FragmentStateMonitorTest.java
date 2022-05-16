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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.DeviceCacheManager;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Constants.CounterNames;
import com.google.firebase.perf.util.Optional;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import java.util.WeakHashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

class FragmentMonitorMockFragment extends Fragment {}

/** Unit tests for {@link com.google.firebase.perf.application.FragmentStateMonitor}. */
@RunWith(RobolectricTestRunner.class)
public class FragmentStateMonitorTest extends FirebasePerformanceTestBase {

  @Mock private Clock clock;
  @Mock private Fragment mockFragment;
  @Mock private FragmentManager mockFragmentManager;
  @Mock private TransportManager mockTransportManager;
  @Mock private AppCompatActivity mockActivity;
  @Mock private AppStateMonitor appStateMonitor;
  @Mock private FrameMetricsRecorder recorder;
  @Mock private PerfFrameMetrics frameCounts1;
  @Mock private PerfFrameMetrics frameCounts2;
  @Mock private ConfigResolver configResolver;
  @Mock private Bundle savedInstanceState;
  @Mock private Fragment mockFragment1;
  @Mock private Fragment mockFragment2;

  @Captor private ArgumentCaptor<TraceMetric> argTraceMetric;

  private Activity activity;
  private long currentTime = 0;
  private static final String longFragmentName =
      "_st_NeverGonnaGiveYouUpNeverGonnaLetYouDownNeverGonnaRunAroundAndDesertYouNeverGonnaMakeYouCryNeverGonnaSayGoodbyeNeverGonnaTellALieAndHurtYou";

  @Before
  public void setUp() {
    currentTime = 0;
    initMocks(this);
    doAnswer((Answer<Timer>) invocationOnMock -> new Timer(currentTime)).when(clock).getTime();

    DeviceCacheManager.clearInstance();
    when(configResolver.isPerformanceMonitoringEnabled()).thenReturn(true);

    doNothing().when(recorder).start();
    doNothing().when(recorder).startFragment(any());

    activity = createFakeActivity(true);

    // Sample frame counts.
    frameCounts1 = new PerfFrameMetrics(9, 5, 3);
    frameCounts2 = new PerfFrameMetrics(14, 9, 4);

    savedInstanceState = mock(Bundle.class);
    appStateMonitor = mock(AppStateMonitor.class);
    mockFragment1 = mock(Fragment.class);
    mockFragment2 = mock(Fragment.class);
  }

  /************ Trace Creation Tests ****************/

  @Test
  public void fragmentTraceName_validFragment_validFragmentScreenTraceNameGenerated() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder);
    Fragment testFragment = new FragmentMonitorMockFragment();
    assertThat(monitor.getFragmentScreenTraceName(testFragment))
        .isEqualTo("_st_FragmentMonitorMockFragment");
  }

  @Test
  public void
      lifecycleCallbacks_differentFrameMetricsCapturedByFrameRecorder_logFragmentScreenTrace() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder);
    when(recorder.stopFragment(any())).thenReturn(Optional.of(frameCounts1));
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());

    when(recorder.stopFragment(any())).thenReturn(Optional.of(frameCounts2));
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(2)).log(any(TraceMetric.class), any());
  }

  @Test
  public void lifecycleCallbacks_onPausedCalledTwice_logFragmentScreenTraceOnce() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder);
    when(recorder.stopFragment(any())).thenReturn(Optional.of(frameCounts1));
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());

    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());
  }

  @Test
  public void lifecycleCallbacks_onPausedCalledBeforeOnResume_doesNotLogFragmentScreenTrace() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder);
    when(recorder.stopFragment(any())).thenReturn(Optional.of(frameCounts1));

    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());
  }

  @Test
  public void
      lifecycleCallbacks_differentFrameMetricsCapturedByFrameRecorder_logFragmentScreenTraceWithCorrectFrames() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder);
    when(recorder.stopFragment(any())).thenReturn(Optional.of(frameCounts1));
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    monitor.onFragmentPaused(mockFragmentManager, mockFragment);

    verify(mockTransportManager, times(1))
        .log(argTraceMetric.capture(), nullable(ApplicationProcessState.class));
    TraceMetric metric = argTraceMetric.getValue();
    assertThat((long) metric.getCountersMap().get(CounterNames.FRAMES_TOTAL.toString()))
        .isEqualTo(frameCounts1.getTotalFrames());
    assertThat((long) metric.getCountersMap().get(CounterNames.FRAMES_SLOW.toString()))
        .isEqualTo(frameCounts1.getSlowFrames());
    assertThat((long) metric.getCountersMap().get(CounterNames.FRAMES_FROZEN.toString()))
        .isEqualTo(frameCounts1.getFrozenFrames());
  }

  /** Simulate call order of activity + fragment lifecycle events */
  @Test
  public void lifecycleCallbacks_cleansUpMap_duringFragmentTransitions() {
    doNothing().when(recorder).startFragment(any());
    when(recorder.stopFragment(any())).thenReturn(Optional.of(frameCounts1));
    when(mockActivity.getSupportFragmentManager()).thenReturn(mockFragmentManager);

    FragmentStateMonitor fragmentMonitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder);
    WeakHashMap<Fragment, Trace> fragmentToTraceMap = fragmentMonitor.getFragmentToTraceMap();

    // Activity_A starts
    fragmentMonitor.onFragmentCreated(mockFragmentManager, mockFragment1, savedInstanceState);
    fragmentMonitor.onFragmentStarted(mockFragmentManager, mockFragment1);
    assertThat(fragmentToTraceMap.size()).isEqualTo(0);
    fragmentMonitor.onFragmentResumed(mockFragmentManager, mockFragment1);
    assertThat(fragmentToTraceMap.size()).isEqualTo(1);
    // Activity A is starting Activity B
    fragmentMonitor.onFragmentPaused(mockFragmentManager, mockFragment1);
    assertThat(fragmentToTraceMap.size()).isEqualTo(0);
    fragmentMonitor.onFragmentCreated(mockFragmentManager, mockFragment2, savedInstanceState);
    fragmentMonitor.onFragmentStarted(mockFragmentManager, mockFragment2);
    fragmentMonitor.onFragmentResumed(mockFragmentManager, mockFragment2);
    assertThat(fragmentToTraceMap.size()).isEqualTo(1);
  }

  @Test
  public void fragmentTraceCreation_whenFrameMetricsIsAbsent_dropsTrace() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder);
    when(recorder.stopFragment(any())).thenReturn(Optional.absent());
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());
  }

  @Test
  public void fragmentTraceCreation_dropsTrace_whenFragmentNameTooLong() {
    AppStateMonitor appStateMonitor =
        spy(new AppStateMonitor(mockTransportManager, clock, configResolver, true));
    FragmentStateMonitor fragmentMonitor =
        spy(new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder));
    when(appStateMonitor.isScreenTraceSupported()).thenReturn(true);
    doReturn(longFragmentName)
        .when(fragmentMonitor)
        .getFragmentScreenTraceName(nullable(Fragment.class));
    when(recorder.stopFragment(any())).thenReturn(Optional.of(frameCounts1));

    fragmentMonitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());
    fragmentMonitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());
  }

  @Test
  public void fragmentTraceCreation_hasParentFragment_addsParentFragmentAttribute() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder);
    when(recorder.stopFragment(any())).thenReturn(Optional.of(frameCounts1));
    Fragment mockParent = mock(Fragment.class);
    when(mockFragment.getParentFragment()).thenReturn(mockParent);

    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);

    verify(mockTransportManager, times(1)).log(argTraceMetric.capture(), any());
    TraceMetric metric = argTraceMetric.getValue();
    assertThat(metric.getCustomAttributesMap().get(Constants.PARENT_FRAGMENT_ATTRIBUTE_KEY))
        .isEqualTo("Fragment");
  }

  @Test
  public void fragmentTraceCreation_noParentFragment_addsSpecialAttributeValue() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, recorder);
    when(recorder.stopFragment(any())).thenReturn(Optional.of(frameCounts1));

    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);

    verify(mockTransportManager, times(1)).log(argTraceMetric.capture(), any());
    TraceMetric metric = argTraceMetric.getValue();
    assertThat(metric.getCustomAttributesMap().get(Constants.PARENT_FRAGMENT_ATTRIBUTE_KEY))
        .isEqualTo(Constants.PARENT_FRAGMENT_ATTRIBUTE_VALUE_NONE);
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
}
