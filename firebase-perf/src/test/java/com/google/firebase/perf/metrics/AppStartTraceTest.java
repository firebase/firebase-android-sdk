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
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.internal.SessionManager;
import com.google.firebase.perf.provider.FirebasePerfProvider;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import java.util.concurrent.TimeUnit;
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
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link AppStartTrace}. */
@RunWith(RobolectricTestRunner.class)
public class AppStartTraceTest extends FirebasePerformanceTestBase {

  @Mock private Clock mClock;
  @Mock private TransportManager transportManager;
  @Mock private Activity mActivity1;
  @Mock private Activity mActivity2;
  @Mock private Bundle mBundle;

  private ArgumentCaptor<TraceMetric> mArguments;

  // a mocked current wall-clock time in microseconds.
  private long mCurrentTime = 0;

  // wall-clock time in microseconds
  private long mAppStartTime;

  // high resolution time in microseconds
  private long mAppStartHRT;

  @Before
  public void setUp() {
    initMocks(this);
    doAnswer(
            new Answer<Timer>() {
              @Override
              public Timer answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new Timer(mCurrentTime);
              }
            })
        .when(mClock)
        .getTime();
    transportManager = mock(TransportManager.class);
    mArguments = ArgumentCaptor.forClass(TraceMetric.class);
    mAppStartTime = FirebasePerfProvider.getAppStartTime().getMicros();
    mAppStartHRT = FirebasePerfProvider.getAppStartTime().getHighResTime();
  }

  /** Test activity sequentially goes through onCreate()->onStart()->onResume() state change. */
  @Test
  public void testLaunchActivity() {
    AppStartTrace trace = new AppStartTrace(transportManager, mClock);
    // first activity goes through onCreate()->onStart()->onResume() state change.
    mCurrentTime = 1;
    trace.onActivityCreated(mActivity1, mBundle);
    mCurrentTime = 2;
    trace.onActivityStarted(mActivity1);
    mCurrentTime = 3;
    trace.onActivityResumed(mActivity1);
    verifyFinalState(mActivity1, trace, 1, 2, 3);
    // same activity goes through onCreate()->onStart()->onResume() state change again.
    // should have no effect on AppStartTrace.
    mCurrentTime = 4;
    trace.onActivityCreated(mActivity1, mBundle);
    mCurrentTime = 5;
    trace.onActivityStarted(mActivity1);
    mCurrentTime = 6;
    trace.onActivityResumed(mActivity1);
    verifyFinalState(mActivity1, trace, 1, 2, 3);

    // a different activity goes through onCreate()->onStart()->onResume() state change.
    // should have no effect on AppStartTrace.
    mCurrentTime = 7;
    trace.onActivityCreated(mActivity2, mBundle);
    mCurrentTime = 8;
    trace.onActivityStarted(mActivity2);
    mCurrentTime = 9;
    trace.onActivityResumed(mActivity2);
    verifyFinalState(mActivity1, trace, 1, 2, 3);
  }

  private void verifyFinalState(
      Activity activity, AppStartTrace trace, long createTime, long startTime, long resumeTime) {
    Assert.assertEquals(activity, trace.getAppStartActivity());
    Assert.assertEquals(createTime, trace.getOnCreateTime().getMicros());
    Assert.assertEquals(startTime, trace.getOnStartTime().getMicros());
    Assert.assertEquals(resumeTime, trace.getOnResumeTime().getMicros());
    verify(transportManager, times(1))
        .log(mArguments.capture(), ArgumentMatchers.nullable(ApplicationProcessState.class));
    TraceMetric metric = mArguments.getValue();

    Assert.assertEquals(Constants.TraceNames.APP_START_TRACE_NAME.toString(), metric.getName());
    Assert.assertEquals(mAppStartTime, metric.getClientStartTimeUs());
    Assert.assertEquals(resumeTime - mAppStartHRT, metric.getDurationUs());

    Assert.assertEquals(3, metric.getSubtracesCount());
    Assert.assertEquals(
        Constants.TraceNames.ON_CREATE_TRACE_NAME.toString(), metric.getSubtraces(0).getName());
    Assert.assertEquals(mAppStartTime, metric.getSubtraces(0).getClientStartTimeUs());
    Assert.assertEquals(createTime - mAppStartHRT, metric.getSubtraces(0).getDurationUs());

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
    AppStartTrace trace = new AppStartTrace(transportManager, mClock);
    // first activity onCreate()
    mCurrentTime = 1;
    trace.onActivityCreated(mActivity1, mBundle);
    Assert.assertEquals(mActivity1, trace.getLaunchActivity());
    Assert.assertEquals(1, trace.getOnCreateTime().getMicros());
    // second activity onCreate(), should not change onCreate time.
    mCurrentTime = 2;
    trace.onActivityCreated(mActivity2, mBundle);
    Assert.assertEquals(1, trace.getOnCreateTime().getMicros());
    // second activity onStart() time is recorded as onStartTime.
    mCurrentTime = 3;
    trace.onActivityStarted(mActivity2);
    Assert.assertEquals(3, trace.getOnStartTime().getMicros());
    // second activity onResume() time is recorded as onResumeTime.
    // and second activity is recorded as AppStartActivity.
    mCurrentTime = 4;
    trace.onActivityResumed(mActivity2);
    Assert.assertEquals(mActivity1, trace.getLaunchActivity());
    Assert.assertEquals(mActivity2, trace.getAppStartActivity());
    verifyFinalState(mActivity2, trace, 1, 3, 4);

    // first activity continues.
    mCurrentTime = 5;
    trace.onActivityStarted(mActivity1);
    mCurrentTime = 6;
    trace.onActivityResumed(mActivity1);
    verifyFinalState(mActivity2, trace, 1, 3, 4);
  }

  @Test
  public void testDelayedAppStart() {
    AppStartTrace trace = new AppStartTrace(transportManager, mClock);
    // Delays activity creation after 1 minute from app start time.
    mCurrentTime = mAppStartTime + TimeUnit.MINUTES.toMicros(1) + 1;
    trace.onActivityCreated(mActivity1, mBundle);
    Assert.assertEquals(mCurrentTime, trace.getOnCreateTime().getMicros());
    ++mCurrentTime;
    trace.onActivityStarted(mActivity1);
    ++mCurrentTime;
    trace.onActivityResumed(mActivity1);
    Assert.assertNull(trace.getOnStartTime());
    Assert.assertNull(trace.getOnResumeTime());
    // There should be no trace sent.
    verify(transportManager, times(0))
        .log(mArguments.capture(), ArgumentMatchers.nullable(ApplicationProcessState.class));
  }

  @Test
  public void testStartFromBackground() {
    AppStartTrace trace = new AppStartTrace(transportManager, mClock);
    trace.setIsStartFromBackground();
    trace.onActivityCreated(mActivity1, mBundle);
    Assert.assertNull(trace.getOnCreateTime());
    ++mCurrentTime;
    trace.onActivityStarted(mActivity1);
    Assert.assertNull(trace.getOnStartTime());
    ++mCurrentTime;
    trace.onActivityResumed(mActivity1);
    Assert.assertNull(trace.getOnResumeTime());
    // There should be no trace sent.
    verify(transportManager, times(0))
        .log(mArguments.capture(), ArgumentMatchers.nullable(ApplicationProcessState.class));
  }

  @Test
  public void testFirebasePerfProviderOnAttachInfoUpdatesPerfSession() {
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();
    Assert.assertNotNull(oldSessionId);
    Assert.assertEquals(oldSessionId, SessionManager.getInstance().perfSession().sessionId());

    FirebasePerfProvider provider = new FirebasePerfProvider();
    provider.attachInfo(RuntimeEnvironment.systemContext, new ProviderInfo());

    Assert.assertNotEquals(oldSessionId, SessionManager.getInstance().perfSession().sessionId());
  }
}
