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

package com.google.firebase.perf.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.perf.v1.ApplicationProcessState.FOREGROUND_BACKGROUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.WindowManager.LayoutParams;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.DeviceCacheManager;
import com.google.firebase.perf.impl.NetworkRequestMetricBuilder;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.ImmutableBundle;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
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

/** Unit tests for {@link AppStateMonitor}. */
@RunWith(RobolectricTestRunner.class)
public class AppStateMonitorTest extends FirebasePerformanceTestBase {

  @Mock private Clock mClock;
  @Mock private TransportManager transportManager;

  @Captor private ArgumentCaptor<TraceMetric> mArgTraceMetric;
  @Captor private ArgumentCaptor<ApplicationProcessState> mArgRate;

  private long mCurrentTime = 0;

  private Activity activity1;
  private Activity activity2;

  @Before
  public void setUp() {
    mCurrentTime = 0;
    initMocks(this);
    doAnswer((Answer<Timer>) invocationOnMock -> new Timer(mCurrentTime)).when(mClock).getTime();

    activity1 = createFakeActivity(/* isHardwareAccelerated= */ true);
    activity2 = createFakeActivity(/* isHardwareAccelerated= */ true);

    DeviceCacheManager.clearInstance();
    ConfigResolver.clearInstance();
  }

  @Test
  public void foregroundBackgroundEvent_activityStateChanges_fgBgEventsCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    // activity1 comes to foreground.
    mCurrentTime = 1;
    monitor.incrementCount("counter1", 10);
    monitor.onActivityResumed(activity1);
    Assert.assertEquals(mCurrentTime, monitor.getResumeTime().getMicros());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(0))
        .log(mArgTraceMetric.capture(), nullable(ApplicationProcessState.class));

    // activity1 goes to background.
    mCurrentTime = 2;
    monitor.incrementCount("counter2", 20);
    monitor.onActivityStopped(activity1);
    Assert.assertEquals(mCurrentTime, monitor.getPauseTime().getMicros());
    Assert.assertEquals(0, monitor.getResumed().size());
    Assert.assertFalse(monitor.isForeground());
    verify(transportManager, times(1)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    TraceMetric metric = mArgTraceMetric.getValue();
    Assert.assertEquals(Constants.TraceNames.FOREGROUND_TRACE_NAME.toString(), metric.getName());
    Assert.assertEquals(2, metric.getCountersCount());
    Assert.assertEquals(1, metric.getPerfSessionsCount());
    Map<String, Long> counters = metric.getCountersMap();
    Assert.assertEquals(10, (long) counters.get("counter1"));
    Assert.assertEquals(20, (long) counters.get("counter2"));
    Assert.assertEquals(monitor.getResumeTime().getMicros(), metric.getClientStartTimeUs());
    Assert.assertEquals(
        monitor.getResumeTime().getDurationMicros(monitor.getPauseTime()), metric.getDurationUs());

    // Verify bug 36457047 fix, onActivityStopped() is called twice.
    monitor.onActivityStopped(activity1);
    Assert.assertEquals(2, monitor.getPauseTime().getMicros());
    Assert.assertEquals(0, monitor.getResumed().size());
    Assert.assertFalse(monitor.isForeground());
    // log() should NOT be called again on second onActivityStopped() call.
    verify(transportManager, times(1)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    // activity1 goes to foreground.
    mCurrentTime = 3;
    monitor.incrementCount("counter3", 30);
    monitor.onActivityResumed(activity1);
    Assert.assertEquals(mCurrentTime, monitor.getResumeTime().getMicros());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(2)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    metric = mArgTraceMetric.getValue();
    Assert.assertEquals(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString(), metric.getName());
    Assert.assertEquals(1, metric.getCountersCount());
    assertThat(metric.getCountersMap()).containsEntry("counter3", 30L);
    Assert.assertEquals(monitor.getPauseTime().getMicros(), metric.getClientStartTimeUs());
    Assert.assertEquals(
        monitor.getPauseTime().getDurationMicros(monitor.getResumeTime()), metric.getDurationUs());
    Assert.assertEquals(1, metric.getPerfSessionsCount());
  }

  @Test
  public void testIncrementCount() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);

    monitor.incrementCount("counter1", 10);
    monitor.incrementCount("counter2", 20);
    monitor.incrementCount("counter2", 2);

    // Resume and then stop activity to trigger logging of the trace metric
    monitor.onActivityResumed(activity1);
    monitor.onActivityStopped(activity1);

    verify(transportManager)
        .log(mArgTraceMetric.capture(), nullable(ApplicationProcessState.class));
    TraceMetric metric = mArgTraceMetric.getValue();

    Map<String, Long> counters = metric.getCountersMap();
    Assert.assertEquals(10, (long) counters.get("counter1"));
    Assert.assertEquals(22, (long) counters.get("counter2"));
  }

  @Test
  public void testTwoActivities() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    // activity1 comes to foreground.
    mCurrentTime = 1;
    monitor.onActivityResumed(activity1);
    Assert.assertEquals(mCurrentTime, monitor.getResumeTime().getMicros());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(0))
        .log(mArgTraceMetric.capture(), nullable(ApplicationProcessState.class));

    mCurrentTime = 2;
    monitor.onActivityResumed(activity2);
    // second activity becomes visible does not change resumeTime.
    Assert.assertEquals(1, monitor.getResumeTime().getMicros());
    // two activities visible.
    Assert.assertEquals(2, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(0)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    // activity1 goes to background.
    mCurrentTime = 3;
    monitor.onActivityStopped(activity1);
    Assert.assertNull(monitor.getPauseTime());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(0)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    // activity2 goes to background.
    mCurrentTime = 4;
    monitor.onActivityStopped(activity2);
    // pauseTime updated.
    Assert.assertEquals(4, monitor.getPauseTime().getMicros());
    // no activity visible.
    Assert.assertEquals(0, monitor.getResumed().size());
    Assert.assertFalse(monitor.isForeground());
    // send foreground trace log.
    verify(transportManager, times(1)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    TraceMetric metric = mArgTraceMetric.getValue();
    Assert.assertEquals(Constants.TraceNames.FOREGROUND_TRACE_NAME.toString(), metric.getName());
    Assert.assertEquals(monitor.getResumeTime().getMicros(), metric.getClientStartTimeUs());
    Assert.assertEquals(
        monitor.getResumeTime().getDurationMicros(monitor.getPauseTime()), metric.getDurationUs());

    // activity1 goes to foreground.
    mCurrentTime = 5;
    monitor.onActivityResumed(activity1);
    // resumeTime updated.
    Assert.assertEquals(mCurrentTime, monitor.getResumeTime().getMicros());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    // send background trace.
    verify(transportManager, times(2)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    metric = mArgTraceMetric.getValue();
    Assert.assertEquals(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString(), metric.getName());
    Assert.assertEquals(monitor.getPauseTime().getMicros(), metric.getClientStartTimeUs());
    Assert.assertEquals(
        monitor.getPauseTime().getDurationMicros(monitor.getResumeTime()), metric.getDurationUs());

    // activity2 goes to foreground.
    mCurrentTime = 6;
    monitor.onActivityResumed(activity2);
    // resumeTime does not change because this is second activity becomes visible.
    Assert.assertEquals(5, monitor.getResumeTime().getMicros());
    // two activities are visible.
    Assert.assertEquals(2, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    // no new event log.
    verify(transportManager, times(2)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void testAppStateCallbackWithTrace() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Trace trace = new Trace("TRACE_1", transportManager, mClock, monitor);
    // Trace is not started yet, default state is APPLICATION_PROCESS_STATE_UNKNOWN
    Assert.assertEquals(
        ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN, trace.getAppState());
    // activity1 comes to foreground.
    mCurrentTime = 1;
    // registerForAppState() is called by Trace.start().
    trace.start();
    // Trace started, get state from AppStateMonitor.
    Assert.assertEquals(ApplicationProcessState.BACKGROUND, trace.getAppState());
    monitor.onActivityResumed(activity1);
    Assert.assertTrue(monitor.isForeground());
    Assert.assertEquals(FOREGROUND_BACKGROUND, trace.getAppState());
    verify(transportManager, times(0))
        .log(mArgTraceMetric.capture(), nullable(ApplicationProcessState.class));

    // activity1 goes to background.
    mCurrentTime = 2;
    monitor.onActivityStopped(activity1);
    Assert.assertFalse(monitor.isForeground());
    // Foreground session trace.
    verify(transportManager, times(1)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));
    // Trace is updated through AppStatCallback.
    Assert.assertEquals(FOREGROUND_BACKGROUND, trace.getAppState());

    // unregisterForAppState() is called by Trace.stop()
    trace.stop();
    // trace has been through FOREGROUND_BACKGROUND
    Assert.assertEquals(FOREGROUND_BACKGROUND, trace.getAppState());
    // a TraceMetric is sent for this trace object.
    verify(transportManager, times(2)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    TraceMetric metric = mArgTraceMetric.getValue();
    Assert.assertEquals("TRACE_1", metric.getName());
  }

  @Test
  public void testAppStateCallbackWithNetworkRequestMetricBuilder() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    // registerForAppState() is called by NetworkRequestMetricBuilder's constructor.
    NetworkRequestMetricBuilder builder =
        new NetworkRequestMetricBuilder(
            mock(TransportManager.class), monitor, mock(GaugeManager.class));
    Assert.assertEquals(ApplicationProcessState.BACKGROUND, builder.getAppState());
    // activity1 comes to foreground.
    mCurrentTime = 1;
    monitor.onActivityResumed(activity1);
    Assert.assertTrue(monitor.isForeground());
    // builder is updated through AppStateCallback.
    Assert.assertEquals(FOREGROUND_BACKGROUND, builder.getAppState());
    verify(transportManager, times(0))
        .log(mArgTraceMetric.capture(), nullable(ApplicationProcessState.class));

    // activity1 goes to background.
    mCurrentTime = 2;
    monitor.onActivityStopped(activity1);
    Assert.assertFalse(monitor.isForeground());
    // Foreground session trace.
    verify(transportManager, times(1)).log(mArgTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));
    // builder is updated again.
    Assert.assertEquals(FOREGROUND_BACKGROUND, builder.getAppState());

    // unregisterForAppState() is called by NetworkRequestMetricBuilder.build().
    builder.build();
    Assert.assertEquals(FOREGROUND_BACKGROUND, builder.getAppState());
  }

  @Test
  public void testRegisterActivityLifecycleCallbacks() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Context context = mock(Context.class);
    Application application = mock(Application.class);
    when(context.getApplicationContext()).thenReturn(application);

    monitor.registerActivityLifecycleCallbacks(context);
    // Call registerActivityLifecycleCallbacks on the monitor again, and ensure that
    // registerActivityLifecycleCallbacks is not invoked again on the application object.
    monitor.registerActivityLifecycleCallbacks(context);
    verify(application, times(1)).registerActivityLifecycleCallbacks(monitor);
  }

  @Test
  public void testUnregisterActivityLifecycleCallbacks() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Context context = mock(Context.class);
    Application application = mock(Application.class);
    when(context.getApplicationContext()).thenReturn(application);

    monitor.registerActivityLifecycleCallbacks(context);
    monitor.unregisterActivityLifecycleCallbacks(context);
    verify(application, times(1)).unregisterActivityLifecycleCallbacks(monitor);
  }

  @Test
  public void testUnregisterActivityLifecycleCallbacksBeforeItWasRegistered() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Context context = mock(Context.class);
    Application application = mock(Application.class);
    when(context.getApplicationContext()).thenReturn(application);

    monitor.unregisterActivityLifecycleCallbacks(context);
    verify(application, never()).unregisterActivityLifecycleCallbacks(monitor);
  }

  @Test
  public void screenTrace_twoActivities_traceStartedAndStoppedWithActivityLifecycle() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);

    Activity[] arr = {activity1, activity2};
    for (int i = 0; i < arr.length; ++i) {
      Activity activity = arr[i];
      // Start and then stop activity to trigger logging of the screen trace
      int startTime = i * 100;
      int endTime = startTime + 10;
      mCurrentTime = startTime;
      monitor.onActivityStarted(activity);
      assertThat(monitor.getActivity2ScreenTrace()).hasSize(1);
      mCurrentTime = endTime;
      monitor.onActivityStopped(activity);
      Assert.assertEquals(0, monitor.getActivity2ScreenTrace().size());
    }
  }

  @Test
  public void screenTrace_noHardwareAccelerated_traceNotCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Activity activityWithNonHardwareAcceleratedView =
        createFakeActivity(/* isHardwareAccelerated =*/ false);

    monitor.onActivityStarted(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).isEmpty();

    // Confirm that this doesn't throw an exception.
    monitor.onActivityStopped(activityWithNonHardwareAcceleratedView);
  }

  @Test
  public void screenTrace_perfMonDisabledAtBuildTime_traceNotCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Activity activityWithNonHardwareAcceleratedView =
        createFakeActivity(/* isHardwareAccelerated =*/ true);
    ConfigResolver configResolver = ConfigResolver.getInstance();

    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    monitor.onActivityStarted(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).isEmpty();
    // Confirm that this doesn't throw an exception.
    monitor.onActivityStopped(activityWithNonHardwareAcceleratedView);
  }

  @Test
  public void screenTrace_perfMonEnabledSwitchAtRuntime_traceCreationDependsOnRuntime() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Activity activityWithNonHardwareAcceleratedView =
        createFakeActivity(/* isHardwareAccelerated =*/ true);
    ConfigResolver configResolver = ConfigResolver.getInstance();
    // Developer disables Performance Monitoring during build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    // Case #1: developer has enabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(true);

    // Assert that screen trace has been created.
    mCurrentTime = 100;
    monitor.onActivityStarted(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).hasSize(1);
    mCurrentTime = 200;
    monitor.onActivityStopped(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).isEmpty();

    // Case #2: developer has disabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(false);

    // Assert that screen trace has not been created.
    monitor.onActivityStarted(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).isEmpty();
    // Confirm that this doesn't throw an exception.
    monitor.onActivityStopped(activityWithNonHardwareAcceleratedView);
  }

  @Test
  public void screenTrace_perfMonDeactivated_traceNotCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Activity activityWithNonHardwareAcceleratedView =
        createFakeActivity(/* isHardwareAccelerated =*/ true);
    ConfigResolver configResolver = ConfigResolver.getInstance();
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_deactivated", true);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    // Developer has enabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(true);

    // Assert that screen trace has not been created.
    monitor.onActivityStarted(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).isEmpty();
    // Confirm that this doesn't throw an exception.
    monitor.onActivityStopped(activityWithNonHardwareAcceleratedView);
  }

  @Test
  public void foregroundTrace_perfMonDisabledAtRuntime_traceNotCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);

    // activity1 comes to foreground.
    mCurrentTime = 1;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    mCurrentTime = 2;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground traces has been created because Performance Monitoring is enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer disabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);

    // activity1 comes to foreground.
    mCurrentTime = 3;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    mCurrentTime = 4;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace is not created because Performance Monitoring is disabled at runtime.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void foregroundTrace_perfMonEnabledAtRuntime_traceCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    ConfigResolver configResolver = ConfigResolver.getInstance();
    // Firebase Performance is disabled at build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    // activity1 comes to foreground.
    mCurrentTime = 1;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    mCurrentTime = 2;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace is not created because Performance Monitoring is disabled at build time.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer enabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(true);

    // activity1 comes to foreground.
    mCurrentTime = 3;
    monitor.onActivityResumed(activity1);
    // Background trace has been created because Performance Monitoring is enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // activity1 goes to background.
    mCurrentTime = 4;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace has been created because Performance Monitoring is enabled.
    verify(transportManager, times(2)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void foreGroundTrace_perfMonDeactivated_traceCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    ConfigResolver configResolver = ConfigResolver.getInstance();
    // Firebase Performance is deactivated at build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_deactivated", true);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    // activity1 comes to foreground.
    mCurrentTime = 1;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    mCurrentTime = 2;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace is not created because Performance Monitoring is deactivated at build time.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer enabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(true);

    // activity1 comes to foreground.
    mCurrentTime = 3;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    mCurrentTime = 4;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace is not created because deactivation takes higher priority.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void backgroundTrace_perfMonDisabledAtRuntime_traceNotCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);

    // activity1 comes to background.
    mCurrentTime = 1;
    monitor.onActivityResumed(activity1);
    monitor.onActivityStopped(activity1);
    // Foreground trace has been created because Performance Monitoring is enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer disabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);

    // activity1 goes to foreground.
    mCurrentTime = 2;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is not created because previously Performance Monitoring was enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // activity1 comes to background.
    mCurrentTime = 3;
    monitor.onActivityStopped(activity1);

    // activity1 goes to foreground.
    mCurrentTime = 4;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // New foreground trace is not created because Performance Monitoring is disabled at runtime.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void backgroundTrace_perfMonEnabledAtRuntime_traceCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    ConfigResolver configResolver = ConfigResolver.getInstance();
    // Firebase Performance is disabled at build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    // activity1 comes to background.
    mCurrentTime = 1;
    monitor.onActivityResumed(activity1);
    monitor.onActivityStopped(activity1);

    // activity1 goes to foreground.
    mCurrentTime = 2;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is not created because Performance Monitoring is disabled at build time.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer enabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(true);

    // activity1 comes to background.
    mCurrentTime = 3;
    monitor.onActivityStopped(activity1);
    // Foreground trace is created because Performance Monitoring is enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // activity1 goes to foreground.
    mCurrentTime = 4;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is created because Performance Monitoring is enabled.
    verify(transportManager, times(2)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void backgroundTrace_perfMonDeactivated_traceCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    ConfigResolver configResolver = ConfigResolver.getInstance();
    // Firebase Performance is deactivated at build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_deactivated", true);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    // activity1 comes to background.
    mCurrentTime = 1;
    monitor.onActivityResumed(activity1);
    monitor.onActivityStopped(activity1);

    // activity1 goes to foreground.
    mCurrentTime = 2;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is not created because Performance Monitoring is deactivated at build time.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer enabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(true);

    // activity1 comes to background.
    mCurrentTime = 3;
    monitor.onActivityStopped(activity1);

    // activity1 goes to foreground.
    mCurrentTime = 4;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is not created because deactivation takes higher priority.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void activityStateChanges_singleClient_callbackIsCalled() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Map<Integer, ApplicationProcessState> clientState = new HashMap<>();

    final int client1 = 1;
    monitor.registerForAppState(
        new WeakReference<>(newState -> clientState.put(client1, newState)));

    // Activity comes to Foreground
    monitor.onActivityResumed(activity1);
    assertThat(clientState.get(client1)).isEqualTo(ApplicationProcessState.FOREGROUND);

    // Activity goes to Background
    monitor.onActivityStopped(activity1);
    assertThat(clientState.get(client1)).isEqualTo(ApplicationProcessState.BACKGROUND);
  }

  @Test
  public void activityStateChanges_multipleClients_callbackCalledOnEachClient() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, mClock);
    Map<Integer, ApplicationProcessState> clientState = new HashMap<>();

    final int client1 = 1;
    monitor.registerForAppState(
        new WeakReference<>(newState -> clientState.put(client1, newState)));

    final int client2 = 2;
    monitor.registerForAppState(
        new WeakReference<>(newState -> clientState.put(client2, newState)));

    final int client3 = 3;
    monitor.registerForAppState(
        new WeakReference<>(newState -> clientState.put(client3, newState)));

    // Activity comes to Foreground
    monitor.onActivityResumed(activity1);
    assertThat(clientState.get(client1)).isEqualTo(ApplicationProcessState.FOREGROUND);
    assertThat(clientState.get(client2)).isEqualTo(ApplicationProcessState.FOREGROUND);
    assertThat(clientState.get(client3)).isEqualTo(ApplicationProcessState.FOREGROUND);

    // Activity goes to Background
    monitor.onActivityStopped(activity1);
    assertThat(clientState.get(client1)).isEqualTo(ApplicationProcessState.BACKGROUND);
    assertThat(clientState.get(client2)).isEqualTo(ApplicationProcessState.BACKGROUND);
    assertThat(clientState.get(client3)).isEqualTo(ApplicationProcessState.BACKGROUND);
  }

  private static Activity createFakeActivity(boolean isHardwareAccelerated) {
    ActivityController<Activity> fakeActivityController = Robolectric.buildActivity(Activity.class);

    if (isHardwareAccelerated) {
      fakeActivityController.get().getWindow().addFlags(LayoutParams.FLAG_HARDWARE_ACCELERATED);
    } else {
      fakeActivityController.get().getWindow().clearFlags(LayoutParams.FLAG_HARDWARE_ACCELERATED);
    }

    return fakeActivityController.start().get();
  }
}
