// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.metrics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import com.google.testing.timing.FakeScheduledExecutorService;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSystemClock;

/** Unit tests for {@link AppStartTrace}. */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class AppStartTraceTest extends FirebasePerformanceTestBase {

  @Mock private Clock clock;
  @Mock private TransportManager transportManager;
  @Mock private ConfigResolver configResolver;
  @Mock private Activity activity1;
  @Mock private Activity activity2;
  @Mock private Bundle bundle;

  private final Context appContext = ApplicationProvider.getApplicationContext();
  private ArgumentCaptor<TraceMetric> traceArgumentCaptor;

  // a mocked current wall-clock time in microseconds.
  private long currentTime = 0;

  @Before
  public void setUp() {
    initMocks(this);
    doAnswer(
            new Answer<Timer>() {
              @Override
              public Timer answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new Timer(currentTime);
              }
            })
        .when(clock)
        .getTime();
    transportManager = mock(TransportManager.class);
    traceArgumentCaptor = ArgumentCaptor.forClass(TraceMetric.class);
  }

  @After
  public void reset() {
    SessionManager.getInstance().updatePerfSession(PerfSession.createWithId("randomSessionId"));
  }

  /** Test activity sequentially goes through onCreate()->onStart()->onResume() state change. */
  @Test
  public void testLaunchActivity() {
    FakeScheduledExecutorService fakeExecutorService = new FakeScheduledExecutorService();
    AppStartTrace trace =
        new AppStartTrace(transportManager, clock, configResolver, fakeExecutorService);
    trace.registerActivityLifecycleCallbacks(appContext);
    // first activity goes through onCreate()->onStart()->onResume() state change.
    currentTime = 1;
    trace.onActivityCreated(activity1, bundle);
    currentTime = 2;
    trace.onActivityStarted(activity1);
    currentTime = 3;
    trace.onActivityResumed(activity1);
    fakeExecutorService.runAll();
    verifyFinalState(activity1, trace, 1, 2, 3);

    // same activity goes through onCreate()->onStart()->onResume() state change again.
    // should have no effect on AppStartTrace.
    currentTime = 4;
    trace.onActivityCreated(activity1, bundle);
    currentTime = 5;
    trace.onActivityStarted(activity1);
    currentTime = 6;
    trace.onActivityResumed(activity1);
    fakeExecutorService.runAll();
    verifyFinalState(activity1, trace, 1, 2, 3);

    // a different activity goes through onCreate()->onStart()->onResume() state change.
    // should have no effect on AppStartTrace.
    currentTime = 7;
    trace.onActivityCreated(activity2, bundle);
    currentTime = 8;
    trace.onActivityStarted(activity2);
    currentTime = 9;
    trace.onActivityResumed(activity2);
    fakeExecutorService.runAll();
    verifyFinalState(activity1, trace, 1, 2, 3);
  }

  private void verifyFinalState(
      Activity activity, AppStartTrace trace, long createTime, long startTime, long resumeTime) {
    Assert.assertEquals(activity, trace.getAppStartActivity());
    Assert.assertEquals(createTime, trace.getOnCreateTime().getMicros());
    Assert.assertEquals(startTime, trace.getOnStartTime().getMicros());
    Assert.assertEquals(resumeTime, trace.getOnResumeTime().getMicros());
    verify(transportManager, times(1))
        .log(
            traceArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    TraceMetric metric = traceArgumentCaptor.getValue();

    Assert.assertEquals(Constants.TraceNames.APP_START_TRACE_NAME.toString(), metric.getName());

    Assert.assertEquals(3, metric.getSubtracesCount());
    Assert.assertEquals(
        Constants.TraceNames.ON_CREATE_TRACE_NAME.toString(), metric.getSubtraces(0).getName());

    Assert.assertEquals(
        Constants.TraceNames.ON_START_TRACE_NAME.toString(), metric.getSubtraces(1).getName());
    Assert.assertEquals(createTime, metric.getSubtraces(1).getClientStartTimeUs());
    Assert.assertEquals(startTime - createTime, metric.getSubtraces(1).getDurationUs());

    Assert.assertEquals(
        Constants.TraceNames.ON_RESUME_TRACE_NAME.toString(), metric.getSubtraces(2).getName());
    Assert.assertEquals(startTime, metric.getSubtraces(2).getClientStartTimeUs());
    Assert.assertEquals(resumeTime - startTime, metric.getSubtraces(2).getDurationUs());

    Assert.assertEquals(1, metric.getPerfSessionsCount());
  }

  /**
   * Test second activity kicking in before the first activity onResume(). Second activity has no
   * effect on AppStartTrace object.
   */
  @Test
  public void testInterleavedActivity() {
    FakeScheduledExecutorService fakeExecutorService = new FakeScheduledExecutorService();
    AppStartTrace trace =
        new AppStartTrace(transportManager, clock, configResolver, fakeExecutorService);
    trace.registerActivityLifecycleCallbacks(appContext);
    // first activity onCreate()
    currentTime = 1;
    trace.onActivityCreated(activity1, bundle);
    Assert.assertEquals(activity1, trace.getLaunchActivity());
    Assert.assertEquals(1, trace.getOnCreateTime().getMicros());
    // second activity onCreate(), should not change onCreate time.
    currentTime = 2;
    trace.onActivityCreated(activity2, bundle);
    Assert.assertEquals(1, trace.getOnCreateTime().getMicros());
    // second activity onStart() time is recorded as onStartTime.
    currentTime = 3;
    trace.onActivityStarted(activity2);
    Assert.assertEquals(3, trace.getOnStartTime().getMicros());
    // second activity onResume() time is recorded as onResumeTime.
    // and second activity is recorded as AppStartActivity.
    currentTime = 4;
    trace.onActivityResumed(activity2);
    Assert.assertEquals(activity1, trace.getLaunchActivity());
    Assert.assertEquals(activity2, trace.getAppStartActivity());
    fakeExecutorService.runAll();
    verifyFinalState(activity2, trace, 1, 3, 4);

    // first activity continues.
    currentTime = 5;
    trace.onActivityStarted(activity1);
    currentTime = 6;
    trace.onActivityResumed(activity1);
    fakeExecutorService.runAll();
    verifyFinalState(activity2, trace, 1, 3, 4);
  }

  @Test
  public void testDelayedAppStart() {
    FakeScheduledExecutorService fakeExecutorService = new FakeScheduledExecutorService();
    AppStartTrace trace =
        new AppStartTrace(transportManager, clock, configResolver, fakeExecutorService);
    trace.registerActivityLifecycleCallbacks(appContext);
    // Delays activity creation after 1 minute from app start time.
    currentTime =
        TimeUnit.MILLISECONDS.toMicros(SystemClock.elapsedRealtime())
            + TimeUnit.MINUTES.toMicros(1)
            + 1;
    trace.onActivityCreated(activity1, bundle);
    Assert.assertEquals(currentTime, trace.getOnCreateTime().getMicros());
    ++currentTime;
    trace.onActivityStarted(activity1);
    ++currentTime;
    trace.onActivityResumed(activity1);
    Assert.assertNull(trace.getOnStartTime());
    Assert.assertNull(trace.getOnResumeTime());
    // There should be no trace sent.
    verify(transportManager, times(0))
        .log(
            traceArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
  }

  @Test
  public void testStartFromBackground() {
    FakeScheduledExecutorService fakeExecutorService = new FakeScheduledExecutorService();
    AppStartTrace trace =
        new AppStartTrace(transportManager, clock, configResolver, fakeExecutorService);
    trace.setIsStartFromBackground();
    trace.onActivityCreated(activity1, bundle);
    Assert.assertNull(trace.getOnCreateTime());
    ++currentTime;
    trace.onActivityStarted(activity1);
    Assert.assertNull(trace.getOnStartTime());
    ++currentTime;
    trace.onActivityResumed(activity1);
    Assert.assertNull(trace.getOnResumeTime());
    // There should be no trace sent.
    verify(transportManager, times(0))
        .log(
            traceArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
  }

  @Test
  @Config(sdk = 26)
  public void timeToInitialDisplay_isLogged() {
    // Test setup
    when(clock.getTime()).thenCallRealMethod(); // Use robolectric shadows to manipulate time
    View testView = new View(appContext);
    when(activity1.findViewById(android.R.id.content)).thenReturn(testView);
    when(activity2.findViewById(android.R.id.content)).thenReturn(testView);
    when(configResolver.getIsExperimentTTIDEnabled()).thenReturn(true);
    FakeScheduledExecutorService fakeExecutorService = new FakeScheduledExecutorService();
    AppStartTrace trace =
        new AppStartTrace(transportManager, clock, configResolver, fakeExecutorService);
    trace.registerActivityLifecycleCallbacks(appContext);
    // Simulate resume and manually stepping time forward
    ShadowSystemClock.advanceBy(Duration.ofMillis(1000));
    long resumeTime = TimeUnit.NANOSECONDS.toMicros(SystemClock.elapsedRealtimeNanos());
    trace.onActivityCreated(activity1, bundle);
    trace.onActivityStarted(activity1);
    trace.onActivityResumed(activity1);
    // Experiment: simulate new activity before draw
    trace.onActivityStarted(activity2);
    trace.onActivityResumed(activity2);
    trace.onActivityPaused(activity1);
    trace.onActivityStopped(activity1);
    trace.onActivityDestroyed(activity1);
    fakeExecutorService.runAll();
    // only the old original _as should have been sent
    verify(transportManager, times(1))
        .log(isA(TraceMetric.class), isA(ApplicationProcessState.class));

    // Simulate draw and manually stepping time forward
    ShadowSystemClock.advanceBy(Duration.ofMillis(1000));
    long drawTime = TimeUnit.NANOSECONDS.toMicros(SystemClock.elapsedRealtimeNanos());
    testView.getViewTreeObserver().dispatchOnPreDraw();
    testView.getViewTreeObserver().dispatchOnDraw();
    shadowOf(Looper.getMainLooper()).idle();
    fakeExecutorService.runNext();
    verify(transportManager, times(2))
        .log(traceArgumentCaptor.capture(), isA(ApplicationProcessState.class));

    // Verify ttid trace is being logged
    TraceMetric ttid = traceArgumentCaptor.getValue();
    long appStartTime = TimeUnit.MILLISECONDS.toMicros(Process.getStartElapsedRealtime());
    assertThat(ttid.getName()).isEqualTo("_experiment_app_start_ttid");
    assertThat(ttid.getDurationUs()).isNotEqualTo(resumeTime - appStartTime);
    assertThat(ttid.getDurationUs()).isEqualTo(drawTime - appStartTime);
    assertThat(ttid.getSubtracesCount()).isEqualTo(3);
  }
}
