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
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.FrameMetricsAggregator;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.DeviceCacheManager;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Timer;
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

  private Activity activity1;
  private ConfigResolver configResolver;

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
  }

  /************ Trace Creation Tests ****************/

  @Test
  public void lifecycleCallbacks_logFragmentScreenTrace() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    monitor.onFragmentPaused(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());

    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());

    monitor.onFragmentResumed(mockFragmentManager, mockFragment);
    verify(mockTransportManager, times(2)).log(any(TraceMetric.class), any());
  }

  @Test
  public void lifecycleCallbacks_cleansUpMap_duringActivityTransitions() {
    // Simulate call order of activity + fragment lifecycle events
    AppStateMonitor appStateMonitor =
        spy(new AppStateMonitor(mockTransportManager, clock, configResolver, fma));
    FragmentStateMonitor fragmentMonitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    doReturn(true).when(appStateMonitor).isScreenTraceSupported();
    WeakHashMap<Fragment, Trace> map = fragmentMonitor.getFragmentToTraceMap();
    // Activity_A onCreate registers FragmentStateMonitor, then:
    appStateMonitor.onActivityStarted(mockActivity);
    Assert.assertEquals(0, map.size());
    appStateMonitor.onActivityResumed(mockActivity);
    fragmentMonitor.onFragmentResumed(mockFragmentManager, mockFragment);
    Assert.assertEquals(1, map.size());
    appStateMonitor.onActivityPaused(mockActivity);
    fragmentMonitor.onFragmentPaused(mockFragmentManager, mockFragment);
    Assert.assertEquals(0, map.size());
    appStateMonitor.onActivityPostPaused(mockActivity);
    // Activity_B onCreate registers FragmentStateMonitor, then:
    appStateMonitor.onActivityStarted(mockActivityB);
    appStateMonitor.onActivityResumed(mockActivityB);
    fragmentMonitor.onFragmentResumed(mockFragmentManager, mockFragment);
    appStateMonitor.onActivityStopped(mockActivity);
    Assert.assertEquals(1, map.size());
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
