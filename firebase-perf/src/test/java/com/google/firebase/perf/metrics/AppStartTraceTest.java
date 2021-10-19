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

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.provider.FirebasePerfProvider;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import com.google.testing.timing.FakeScheduledExecutorService;
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

/** Unit tests for {@link AppStartTrace}. */
@RunWith(RobolectricTestRunner.class)
public class AppStartTraceTest extends FirebasePerformanceTestBase {

  @Mock private Clock clock;
  @Mock private TransportManager transportManager;
  @Mock private Activity activity1;
  @Mock private Activity activity2;
  @Mock private Bundle bundle;

  private ArgumentCaptor<TraceMetric> traceArgumentCaptor;

  // a mocked current wall-clock time in microseconds.
  private long currentTime = 0;

  // wall-clock time in microseconds
  private long appStartTime;

  // high resolution time in microseconds
  private long appStartHRT;

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
    appStartTime = FirebasePerfProvider.getAppStartTime().getMicros();
    appStartHRT = FirebasePerfProvider.getAppStartTime().getHighResTime();
  }

  @After
  public void reset() {
    SessionManager.getInstance()
        .setPerfSession(com.google.firebase.perf.session.PerfSession.create());
  }

  /** Test activity sequentially goes through onCreate()->onStart()->onResume() state change. */
  @Test
  public void testLaunchActivity() {
    FakeScheduledExecutorService fakeExecutorService = new FakeScheduledExecutorService();
    AppStartTrace trace = new AppStartTrace(transportManager, clock, fakeExecutorService);
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
    Assert.assertEquals(appStartTime, metric.getClientStartTimeUs());
    Assert.assertEquals(resumeTime - appStartHRT, metric.getDurationUs());

    Assert.assertEquals(3, metric.getSubtracesCount());
    Assert.assertEquals(
        Constants.TraceNames.ON_CREATE_TRACE_NAME.toString(), metric.getSubtraces(0).getName());
    Assert.assertEquals(appStartTime, metric.getSubtraces(0).getClientStartTimeUs());
    Assert.assertEquals(createTime - appStartHRT, metric.getSubtraces(0).getDurationUs());

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
    AppStartTrace trace = new AppStartTrace(transportManager, clock, fakeExecutorService);
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
    AppStartTrace trace = new AppStartTrace(transportManager, clock, fakeExecutorService);
    // Delays activity creation after 1 minute from app start time.
    currentTime = appStartTime + TimeUnit.MINUTES.toMicros(1) + 1;
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
    AppStartTrace trace = new AppStartTrace(transportManager, clock, fakeExecutorService);
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
  public void testFirebasePerfProviderOnAttachInfo_initializesGaugeCollection() {
    com.google.firebase.perf.session.PerfSession mockPerfSession =
        mock(com.google.firebase.perf.session.PerfSession.class);
    when(mockPerfSession.sessionId()).thenReturn("sessionId");
    when(mockPerfSession.isGaugeAndEventCollectionEnabled()).thenReturn(true);

    SessionManager.getInstance().setPerfSession(mockPerfSession);
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();
    Assert.assertEquals(oldSessionId, SessionManager.getInstance().perfSession().sessionId());

    FirebasePerfProvider provider = new FirebasePerfProvider();
    provider.attachInfo(ApplicationProvider.getApplicationContext(), new ProviderInfo());

    Assert.assertEquals(oldSessionId, SessionManager.getInstance().perfSession().sessionId());
    verify(mockPerfSession, times(2)).isGaugeAndEventCollectionEnabled();
  }
}
