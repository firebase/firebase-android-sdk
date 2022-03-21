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
import static org.mockito.Mockito.doAnswer;
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
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.TraceMetric;
import com.google.testing.timing.FakeDirectExecutorService;
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
  @Mock private FragmentManager mockfragmentManager;
  @Mock private TransportManager mockTransportManager;
  @Mock private AppCompatActivity mockActivity;
  @Mock private AppStateMonitor appStateMonitor;
  @Mock private FrameMetricsAggregator fma;

  @Captor private ArgumentCaptor<TraceMetric> argTraceMetric;

  private long currentTime = 0;

  private Activity activity1;

  @Before
  public void setUp() {
    currentTime = 0;
    initMocks(this);
    doAnswer((Answer<Timer>) invocationOnMock -> new Timer(currentTime)).when(clock).getTime();

    DeviceCacheManager.clearInstance();
    ConfigResolver.clearInstance();

    ConfigResolver configResolver = ConfigResolver.getInstance();
    configResolver.setDeviceCacheManager(new DeviceCacheManager(new FakeDirectExecutorService()));
  }

  @Test
  public void fragmentLifecycleCallbacks_logFragmentScreenTrace() {
    FragmentStateMonitor monitor =
        new FragmentStateMonitor(clock, mockTransportManager, appStateMonitor, fma);
    monitor.onFragmentResumed(mockfragmentManager, mockFragment);
    verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

    monitor.onFragmentPaused(mockfragmentManager, mockFragment);
    verify(mockTransportManager, times(1)).log(any(TraceMetric.class), any());
  }
}
