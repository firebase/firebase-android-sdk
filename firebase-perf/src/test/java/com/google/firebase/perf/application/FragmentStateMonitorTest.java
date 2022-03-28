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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.util.SparseIntArray;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.FrameMetricsAggregator;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.DeviceCacheManager;
import com.google.firebase.perf.metrics.FrameMetricsCalculator;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import com.google.testing.timing.FakeDirectExecutorService;
import java.util.WeakHashMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.application.FragmentStateMonitor}. */
@RunWith(RobolectricTestRunner.class)
public class FragmentStateMonitorTest extends FirebasePerformanceTestBase {

  @Mock private Clock clock;
  @Mock private Fragment mockFragment;
  @Mock private FragmentManager mockFragmentManager;
  @Mock private TransportManager mockTransportManager;
  @Mock private AppCompatActivity mockActivity;
  @Mock private AppCompatActivity mockActivityB;
  @Mock private AppStateMonitor appStateMonitor;
  @Mock private FrameMetricsAggregator fma;

  @Captor private ArgumentCaptor<TraceMetric> argTraceMetric;

  private long currentTime = 0;
  private static final String longFragmentName =
      "_st_NeverGonnaGiveYouUpNeverGonnaLetYouDownNeverGonnaRunAroundAndDesertYouNeverGonnaMakeYouCryNeverGonnaSayGoodbyeNeverGonnaTellALieAndHurtYou";
  private ConfigResolver configResolver;

  /**
   * Array of SparseIntArray to mock the return value from {@link
   * FrameMetricsAggregator#getMetrics()}
   */
  private SparseIntArray[] fmaMetrics1 = new SparseIntArray[1];

  private SparseIntArray[] fmaMetrics2 = new SparseIntArray[1];

  @Before
  public void setUp() {
    currentTime = 0;
    initMocks(this);
    doAnswer((Answer<Timer>) invocationOnMock -> new Timer(currentTime)).when(clock).getTime();

    DeviceCacheManager.clearInstance();
    ConfigResolver.clearInstance();

    ConfigResolver configResolver = ConfigResolver.getInstance();
    configResolver.setDeviceCacheManager(new DeviceCacheManager(new FakeDirectExecutorService()));
    ConfigResolver spyConfigResolver = spy(configResolver);
    doReturn(true).when(spyConfigResolver).isPerformanceMonitoringEnabled();
    this.configResolver = spyConfigResolver;

    // fmaMetrics1 should have 1+3+1=5 total frames, 3+1=4 slow frames, and 1 frozen frames.
    SparseIntArray sparseIntArray = new SparseIntArray();
    sparseIntArray.append(1, 1);
    sparseIntArray.append(17, 3);
    sparseIntArray.append(800, 1);
    fmaMetrics1[FrameMetricsAggregator.TOTAL_INDEX] = sparseIntArray;

    // fmaMetrics2 should have 5+5+4=14 total frames, 5+4=9 slow frames, and 4 frozen frames.
    sparseIntArray = new SparseIntArray();
    sparseIntArray.append(1, 5);
    sparseIntArray.append(18, 5);
    sparseIntArray.append(800, 4);
    fmaMetrics2[FrameMetricsAggregator.TOTAL_INDEX] = sparseIntArray;
  }

  /************ Trace Creation Tests ****************/

  @Test
  public void lifecycleCallbacks_differentFrameMetricsCapturedByFma_logFragmentScreenTrace() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    when(fma.getMetrics()).thenReturn(fmaMetrics1);
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    when(fma.getMetrics()).thenReturn(fmaMetrics2);
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());

    when(fma.getMetrics()).thenReturn(fmaMetrics1);
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());

    when(fma.getMetrics()).thenReturn(fmaMetrics2);
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(2)).log(any(TraceMetric.class), any());
  }

  @Test
  public void lifecycleCallbacks_onPausedCalledTwice_logFragmentScreenTraceOnce() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    when(fma.getMetrics()).thenReturn(fmaMetrics1);
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    when(fma.getMetrics()).thenReturn(fmaMetrics2);
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());

    when(fma.getMetrics()).thenReturn(fmaMetrics2);
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());
  }

  @Test
  public void lifecycleCallbacks_onPausedCalledBeforeOnResume_doesNotLogFragmentScreenTrace() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    when(fma.getMetrics()).thenReturn(fmaMetrics1);

    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    when(fma.getMetrics()).thenReturn(fmaMetrics2);
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());
  }

  @Test
  public void
      lifecycleCallbacks_differentFrameMetricsCapturedByFma_logFragmentScreenTraceWithCorrectFrames() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    when(fma.getMetrics()).thenReturn(fmaMetrics1);
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    when(fma.getMetrics()).thenReturn(fmaMetrics2);
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);

    // fmaMetrics1 has 1+3+1=5 total frames, 3+1=4 slow frames, and 1 frozen frames
    // fmaMetrics2 has 5+5+4=14 total frames, 5+4=9 slow frames, and 4 frozen frames
    // we expect the trace to have 14-5=9 total frames, 9-4=5 slow frames, and 4-1=3 frozen
    // frames.
    verify(mockTransportManager, times(1))
        .log(argTraceMetric.capture(), nullable(ApplicationProcessState.class));
    TraceMetric metric = argTraceMetric.getValue();
    Assert.assertEquals(
        9, (long) metric.getCountersMap().get(Constants.CounterNames.FRAMES_TOTAL.toString()));
    Assert.assertEquals(
        5, (long) metric.getCountersMap().get(Constants.CounterNames.FRAMES_SLOW.toString()));
    Assert.assertEquals(
        3, (long) metric.getCountersMap().get(Constants.CounterNames.FRAMES_FROZEN.toString()));
  }

  @Test
  public void lifecycleCallbacks_cleansUpMap_duringActivityTransitions() {
    // Simulate call order of activity + fragment lifecycle events
    AppStateMonitor appStateMonitor =
        spy(new AppStateMonitor(mockTransportManager, clock, configResolver, fma));
    FragmentStateMonitor fragmentMonitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    doReturn(true).when(appStateMonitor).isScreenTraceSupported();
    WeakHashMap<Fragment, Trace> fragmentToTraceMap = fragmentMonitor.getFragmentToTraceMap();
    WeakHashMap<Fragment, FrameMetricsCalculator.PerfFrameMetrics> fragmentToMetricsMap =
        fragmentMonitor.getFragmentToMetricsMap();
    // Activity_A onCreate registers FragmentStateMonitor, then:
    appStateMonitor.onActivityStarted(mockActivity);
    Assert.assertEquals(0, fragmentToTraceMap.size());
    Assert.assertEquals(0, fragmentToMetricsMap.size());
    appStateMonitor.onActivityResumed(mockActivity);
    fragmentMonitor.onFragmentResumed(mockFragmentManager, mockFragment);
    Assert.assertEquals(1, fragmentToTraceMap.size());
    Assert.assertEquals(1, fragmentToMetricsMap.size());
    appStateMonitor.onActivityPaused(mockActivity);
    fragmentMonitor.onFragmentPaused(mockFragmentManager, mockFragment);
    Assert.assertEquals(0, fragmentToTraceMap.size());
    Assert.assertEquals(0, fragmentToMetricsMap.size());
    appStateMonitor.onActivityPostPaused(mockActivity);
    // Activity_B onCreate registers FragmentStateMonitor, then:
    appStateMonitor.onActivityStarted(mockActivityB);
    appStateMonitor.onActivityResumed(mockActivityB);
    fragmentMonitor.onFragmentResumed(mockFragmentManager, mockFragment);
    appStateMonitor.onActivityStopped(mockActivity);
    Assert.assertEquals(1, fragmentToTraceMap.size());
    Assert.assertEquals(1, fragmentToMetricsMap.size());
  }

  @Test
  public void fragmentTraceCreation_whenFrameMetricsIsUnchanged_dropsTrace() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    when(fma.getMetrics()).thenReturn(fmaMetrics1);
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    when(fma.getMetrics()).thenReturn(fmaMetrics1);
    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());
  }

  @Test
  public void fragmentTraceCreation_dropsTrace_whenFragmentNameTooLong() {
    AppStateMonitor appStateMonitor =
        spy(new AppStateMonitor(mockTransportManager, clock, configResolver, fma));
    FragmentStateMonitor fragmentMonitor =
        spy(new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma));
    doReturn(true).when(appStateMonitor).isScreenTraceSupported();
    doReturn(longFragmentName)
        .when(fragmentMonitor)
        .getFragmentScreenTraceName(nullable(Fragment.class));

    fragmentMonitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());
    fragmentMonitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());
  }

  /************ FrameMetrics Collection Tests ****************/

  @Test
  public void onFragmentPaused_processFrameMetrics_beforeReset() {
    // Simulate call order of activity + fragment lifecycle events
    AppStateMonitor appStateMonitor =
        spy(new AppStateMonitor(mockTransportManager, clock, configResolver, fma));
    FragmentStateMonitor fragmentMonitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    doReturn(true).when(appStateMonitor).isScreenTraceSupported();
    // Activity_A onCreate registers FragmentStateMonitor, then:
    appStateMonitor.onActivityStarted(mockActivity);
    appStateMonitor.onActivityResumed(mockActivity);
    fragmentMonitor.onFragmentResumed(mockFragmentManager, mockFragment);
    appStateMonitor.onActivityPaused(mockActivity);
    // reset() was not called at the time of fragments collecting its frame metrics
    verify(fma, times(0)).reset();
    verify(fma, times(0)).remove(nullable(Activity.class));
    fragmentMonitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(fma, times(0)).reset();
    verify(fma, times(0)).remove(nullable(Activity.class));
    // reset() is only called after fragment is done collecting its metrics
    appStateMonitor.onActivityPostPaused(mockActivity);
    verify(fma, times(1)).reset();
    verify(fma, times(1)).remove(nullable(Activity.class));
  }
}
