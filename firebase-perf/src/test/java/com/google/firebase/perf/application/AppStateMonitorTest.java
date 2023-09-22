// Copyright 2021 Google LLC
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
import static com.google.firebase.perf.application.AppStateMonitor.AppStateCallback;
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
import com.google.firebase.perf.FirebasePerformanceInitializer;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.DeviceCacheManager;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.session.gauges.GaugeManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.ImmutableBundle;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import com.google.testing.timing.FakeDirectExecutorService;
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

/** Unit tests for {@link com.google.firebase.perf.application.AppStateMonitor}. */
@RunWith(RobolectricTestRunner.class)
public class AppStateMonitorTest extends FirebasePerformanceTestBase {

  @Mock private Clock clock;
  @Mock private TransportManager transportManager;

  @Captor private ArgumentCaptor<TraceMetric> argTraceMetric;

  private long currentTime = 0;

  private Activity activity1;
  private Activity activity2;

  @Before
  public void setUp() {
    currentTime = 0;
    initMocks(this);
    doAnswer((Answer<Timer>) invocationOnMock -> new Timer(currentTime)).when(clock).getTime();

    activity1 = createFakeActivity(/* isHardwareAccelerated= */ true);
    activity2 = createFakeActivity(/* isHardwareAccelerated= */ true);

    DeviceCacheManager.clearInstance();
    ConfigResolver.clearInstance();

    ConfigResolver configResolver = ConfigResolver.getInstance();
    configResolver.setDeviceCacheManager(new DeviceCacheManager(new FakeDirectExecutorService()));
  }

  @Test
  public void foregroundBackgroundEvent_activityStateChanges_fgBgEventsCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    // activity1 comes to foreground.
    currentTime = 1;
    monitor.incrementCount("counter1", 10);
    monitor.onActivityResumed(activity1);
    Assert.assertEquals(currentTime, monitor.getResumeTime().getMicros());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(0))
        .log(argTraceMetric.capture(), nullable(ApplicationProcessState.class));

    // activity1 goes to background.
    currentTime = 2;
    monitor.incrementCount("counter2", 20);
    monitor.onActivityStopped(activity1);
    Assert.assertEquals(currentTime, monitor.getPauseTime().getMicros());
    Assert.assertEquals(0, monitor.getResumed().size());
    Assert.assertFalse(monitor.isForeground());
    verify(transportManager, times(1)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    TraceMetric metric = argTraceMetric.getValue();
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
    verify(transportManager, times(1)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    // activity1 goes to foreground.
    currentTime = 3;
    monitor.incrementCount("counter3", 30);
    monitor.onActivityResumed(activity1);
    Assert.assertEquals(currentTime, monitor.getResumeTime().getMicros());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(2)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    metric = argTraceMetric.getValue();
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
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);

    monitor.incrementCount("counter1", 10);
    monitor.incrementCount("counter2", 20);
    monitor.incrementCount("counter2", 2);

    // Resume and then stop activity to trigger logging of the trace metric
    monitor.onActivityResumed(activity1);
    monitor.onActivityStopped(activity1);

    verify(transportManager).log(argTraceMetric.capture(), nullable(ApplicationProcessState.class));
    TraceMetric metric = argTraceMetric.getValue();

    Map<String, Long> counters = metric.getCountersMap();
    Assert.assertEquals(10, (long) counters.get("counter1"));
    Assert.assertEquals(22, (long) counters.get("counter2"));
  }

  @Test
  public void testTwoActivities() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    // activity1 comes to foreground.
    currentTime = 1;
    monitor.onActivityResumed(activity1);
    Assert.assertEquals(currentTime, monitor.getResumeTime().getMicros());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(0))
        .log(argTraceMetric.capture(), nullable(ApplicationProcessState.class));

    currentTime = 2;
    monitor.onActivityResumed(activity2);
    // second activity becomes visible does not change resumeTime.
    Assert.assertEquals(1, monitor.getResumeTime().getMicros());
    // two activities visible.
    Assert.assertEquals(2, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(0)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    // activity1 goes to background.
    currentTime = 3;
    monitor.onActivityStopped(activity1);
    Assert.assertNull(monitor.getPauseTime());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    verify(transportManager, times(0)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    // activity2 goes to background.
    currentTime = 4;
    monitor.onActivityStopped(activity2);
    // pauseTime updated.
    Assert.assertEquals(4, monitor.getPauseTime().getMicros());
    // no activity visible.
    Assert.assertEquals(0, monitor.getResumed().size());
    Assert.assertFalse(monitor.isForeground());
    // send foreground trace log.
    verify(transportManager, times(1)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    TraceMetric metric = argTraceMetric.getValue();
    Assert.assertEquals(Constants.TraceNames.FOREGROUND_TRACE_NAME.toString(), metric.getName());
    Assert.assertEquals(monitor.getResumeTime().getMicros(), metric.getClientStartTimeUs());
    Assert.assertEquals(
        monitor.getResumeTime().getDurationMicros(monitor.getPauseTime()), metric.getDurationUs());

    // activity1 goes to foreground.
    currentTime = 5;
    monitor.onActivityResumed(activity1);
    // resumeTime updated.
    Assert.assertEquals(currentTime, monitor.getResumeTime().getMicros());
    Assert.assertEquals(1, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    // send background trace.
    verify(transportManager, times(2)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    metric = argTraceMetric.getValue();
    Assert.assertEquals(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString(), metric.getName());
    Assert.assertEquals(monitor.getPauseTime().getMicros(), metric.getClientStartTimeUs());
    Assert.assertEquals(
        monitor.getPauseTime().getDurationMicros(monitor.getResumeTime()), metric.getDurationUs());

    // activity2 goes to foreground.
    currentTime = 6;
    monitor.onActivityResumed(activity2);
    // resumeTime does not change because this is second activity becomes visible.
    Assert.assertEquals(5, monitor.getResumeTime().getMicros());
    // two activities are visible.
    Assert.assertEquals(2, monitor.getResumed().size());
    Assert.assertTrue(monitor.isForeground());
    // no new event log.
    verify(transportManager, times(2)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void testAppStateCallbackWithTrace() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    Trace trace = new Trace("TRACE_1", transportManager, clock, monitor);
    // Trace is not started yet, default state is APPLICATION_PROCESS_STATE_UNKNOWN
    Assert.assertEquals(
        ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN, trace.getAppState());
    // activity1 comes to foreground.
    currentTime = 1;
    // registerForAppState() is called by Trace.start().
    trace.start();
    // Trace started, get state from AppStateMonitor.
    Assert.assertEquals(ApplicationProcessState.BACKGROUND, trace.getAppState());
    monitor.onActivityResumed(activity1);
    Assert.assertTrue(monitor.isForeground());
    Assert.assertEquals(FOREGROUND_BACKGROUND, trace.getAppState());
    verify(transportManager, times(0))
        .log(argTraceMetric.capture(), nullable(ApplicationProcessState.class));

    // activity1 goes to background.
    currentTime = 2;
    monitor.onActivityStopped(activity1);
    Assert.assertFalse(monitor.isForeground());
    // Foreground session trace.
    verify(transportManager, times(1)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));
    // Trace is updated through AppStatCallback.
    Assert.assertEquals(FOREGROUND_BACKGROUND, trace.getAppState());

    // unregisterForAppState() is called by Trace.stop()
    trace.stop();
    // trace has been through FOREGROUND_BACKGROUND
    Assert.assertEquals(FOREGROUND_BACKGROUND, trace.getAppState());
    // a TraceMetric is sent for this trace object.
    verify(transportManager, times(2)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));

    TraceMetric metric = argTraceMetric.getValue();
    Assert.assertEquals("TRACE_1", metric.getName());
  }

  @Test
  public void testAppStateCallbackWithNetworkRequestMetricBuilder() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    // registerForAppState() is called by NetworkRequestMetricBuilder's constructor.
    NetworkRequestMetricBuilder builder =
        new NetworkRequestMetricBuilder(
            mock(TransportManager.class), monitor, mock(GaugeManager.class));
    Assert.assertEquals(ApplicationProcessState.BACKGROUND, builder.getAppState());
    // activity1 comes to foreground.
    currentTime = 1;
    monitor.onActivityResumed(activity1);
    Assert.assertTrue(monitor.isForeground());
    // builder is updated through AppStateCallback.
    Assert.assertEquals(FOREGROUND_BACKGROUND, builder.getAppState());
    verify(transportManager, times(0))
        .log(argTraceMetric.capture(), nullable(ApplicationProcessState.class));

    // activity1 goes to background.
    currentTime = 2;
    monitor.onActivityStopped(activity1);
    Assert.assertFalse(monitor.isForeground());
    // Foreground session trace.
    verify(transportManager, times(1)).log(argTraceMetric.capture(), eq(FOREGROUND_BACKGROUND));
    // builder is updated again.
    Assert.assertEquals(FOREGROUND_BACKGROUND, builder.getAppState());

    // unregisterForAppState() is called by NetworkRequestMetricBuilder.build().
    builder.build();
    Assert.assertEquals(FOREGROUND_BACKGROUND, builder.getAppState());
  }

  @Test
  public void testRegisterActivityLifecycleCallbacks() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
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
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    Context context = mock(Context.class);
    Application application = mock(Application.class);
    when(context.getApplicationContext()).thenReturn(application);

    monitor.registerActivityLifecycleCallbacks(context);
    monitor.unregisterActivityLifecycleCallbacks(context);
    verify(application, times(1)).unregisterActivityLifecycleCallbacks(monitor);
  }

  @Test
  public void testUnregisterActivityLifecycleCallbacksBeforeItWasRegistered() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    Context context = mock(Context.class);
    Application application = mock(Application.class);
    when(context.getApplicationContext()).thenReturn(application);

    monitor.unregisterActivityLifecycleCallbacks(context);
    verify(application, never()).unregisterActivityLifecycleCallbacks(monitor);
  }

  @Test
  public void screenTrace_twoActivities_traceStartedAndStoppedWithActivityLifecycle() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);

    Activity[] arr = {activity1, activity2};
    for (int i = 0; i < arr.length; ++i) {
      Activity activity = arr[i];
      // Start and then stop activity to trigger logging of the screen trace
      int startTime = i * 100;
      int endTime = startTime + 10;
      currentTime = startTime;
      monitor.onActivityStarted(activity);
      assertThat(monitor.getActivity2ScreenTrace()).hasSize(1);
      currentTime = endTime;
      monitor.onActivityStopped(activity);
      Assert.assertEquals(0, monitor.getActivity2ScreenTrace().size());
    }
  }

  @Test
  public void screenTrace_noHardwareAccelerated_noExceptionThrown() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    Activity activityWithNonHardwareAcceleratedView =
        createFakeActivity(/* isHardwareAccelerated =*/ false);

    monitor.onActivityStarted(activityWithNonHardwareAcceleratedView);

    // Confirm that this doesn't throw an exception.
    monitor.onActivityStopped(activityWithNonHardwareAcceleratedView);
  }

  @Test
  public void screenTrace_perfMonDisabledAtBuildTime_traceNotCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    Activity activityWithNonHardwareAcceleratedView =
        createFakeActivity(/* isHardwareAccelerated =*/ true);

    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    ConfigResolver.getInstance().setMetadataBundle(new ImmutableBundle(bundle));

    monitor.onActivityStarted(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).isEmpty();
    // Confirm that this doesn't throw an exception.
    monitor.onActivityStopped(activityWithNonHardwareAcceleratedView);
  }

  @Test
  public void screenTrace_perfMonEnabledSwitchAtRuntime_traceCreationDependsOnRuntime() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    Activity activityWithNonHardwareAcceleratedView =
        createFakeActivity(/* isHardwareAccelerated =*/ true);

    // Developer disables Performance Monitoring during build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    ConfigResolver.getInstance().setMetadataBundle(new ImmutableBundle(bundle));

    // Case #1: developer has enabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(true);

    // Assert that screen trace has been created.
    currentTime = 100;
    monitor.onActivityStarted(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).hasSize(1);
    currentTime = 200;
    monitor.onActivityStopped(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).isEmpty();

    // Case #2: developer has disabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);

    // Assert that screen trace has not been created.
    monitor.onActivityStarted(activityWithNonHardwareAcceleratedView);
    assertThat(monitor.getActivity2ScreenTrace()).isEmpty();
    // Confirm that this doesn't throw an exception.
    monitor.onActivityStopped(activityWithNonHardwareAcceleratedView);
  }

  @Test
  public void screenTrace_perfMonDeactivated_traceNotCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    Activity activityWithNonHardwareAcceleratedView =
        createFakeActivity(/* isHardwareAccelerated =*/ true);
    ConfigResolver configResolver = ConfigResolver.getInstance();
    configResolver.setDeviceCacheManager(new DeviceCacheManager(new FakeDirectExecutorService()));

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
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);

    // activity1 comes to foreground.
    currentTime = 1;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    currentTime = 2;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground traces has been created because Performance Monitoring is enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer disabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);

    // activity1 comes to foreground.
    currentTime = 3;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    currentTime = 4;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace is not created because Performance Monitoring is disabled at runtime.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void foregroundTrace_perfMonEnabledAtRuntime_traceCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);

    // Firebase Performance is disabled at build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    ConfigResolver.getInstance().setMetadataBundle(new ImmutableBundle(bundle));

    // activity1 comes to foreground.
    currentTime = 1;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    currentTime = 2;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace is not created because Performance Monitoring is disabled at build time.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer enabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(true);

    // activity1 comes to foreground.
    currentTime = 3;
    monitor.onActivityResumed(activity1);
    // Background trace has been created because Performance Monitoring is enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // activity1 goes to background.
    currentTime = 4;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace has been created because Performance Monitoring is enabled.
    verify(transportManager, times(2)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void foregroundTrace_perfMonDeactivated_traceCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);

    // Firebase Performance is deactivated at build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_deactivated", true);
    ConfigResolver.getInstance().setMetadataBundle(new ImmutableBundle(bundle));

    // activity1 comes to foreground.
    currentTime = 1;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    currentTime = 2;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace is not created because Performance Monitoring is deactivated at build time.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer enabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(true);

    // activity1 comes to foreground.
    currentTime = 3;
    monitor.onActivityResumed(activity1);

    // activity1 goes to background.
    currentTime = 4;
    monitor.onActivityStopped(activity1);
    assertThat(monitor.isForeground()).isFalse();
    // Foreground trace is not created because deactivation takes higher priority.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void backgroundTrace_perfMonDisabledAtRuntime_traceNotCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);

    // activity1 comes to background.
    currentTime = 1;
    monitor.onActivityResumed(activity1);
    monitor.onActivityStopped(activity1);
    // Foreground trace has been created because Performance Monitoring is enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer disabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);

    // activity1 goes to foreground.
    currentTime = 2;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is not created because previously Performance Monitoring was enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // activity1 comes to background.
    currentTime = 3;
    monitor.onActivityStopped(activity1);

    // activity1 goes to foreground.
    currentTime = 4;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // New foreground trace is not created because Performance Monitoring is disabled at runtime.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void backgroundTrace_perfMonEnabledAtRuntime_traceCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);

    // Firebase Performance is disabled at build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    ConfigResolver.getInstance().setMetadataBundle(new ImmutableBundle(bundle));

    // activity1 comes to background.
    currentTime = 1;
    monitor.onActivityResumed(activity1);
    monitor.onActivityStopped(activity1);

    // activity1 goes to foreground.
    currentTime = 2;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is not created because Performance Monitoring is disabled at build time.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer enabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(true);

    // activity1 comes to background.
    currentTime = 3;
    monitor.onActivityStopped(activity1);
    // Foreground trace is created because Performance Monitoring is enabled.
    verify(transportManager, times(1)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // activity1 goes to foreground.
    currentTime = 4;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is created because Performance Monitoring is enabled.
    verify(transportManager, times(2)).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void backgroundTrace_perfMonDeactivated_traceCreated() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);

    // Firebase Performance is deactivated at build time.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_deactivated", true);
    ConfigResolver.getInstance().setMetadataBundle(new ImmutableBundle(bundle));

    // activity1 comes to background.
    currentTime = 1;
    monitor.onActivityResumed(activity1);
    monitor.onActivityStopped(activity1);

    // activity1 goes to foreground.
    currentTime = 2;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is not created because Performance Monitoring is deactivated at build time.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));

    // Developer enabled Performance Monitoring during runtime.
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(true);

    // activity1 comes to background.
    currentTime = 3;
    monitor.onActivityStopped(activity1);

    // activity1 goes to foreground.
    currentTime = 4;
    monitor.onActivityResumed(activity1);
    assertThat(monitor.isForeground()).isTrue();
    // Background trace is not created because deactivation takes higher priority.
    verify(transportManager, never()).log(any(TraceMetric.class), eq(FOREGROUND_BACKGROUND));
  }

  @Test
  public void activityStateChanges_singleSubscriber_callbackIsCalled() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    Map<Integer, ApplicationProcessState> subscriberState = new HashMap<>();

    // Register callbacks, but note that each callback is saved in a local variable. Otherwise
    // WeakReference can get garbage collected, making this test flaky.
    final int subscriber1 = 1;
    AppStateCallback callback1 = newState -> subscriberState.put(subscriber1, newState);
    monitor.registerForAppState(new WeakReference<>(callback1));

    // Activity comes to Foreground
    monitor.onActivityResumed(activity1);
    assertThat(subscriberState.get(subscriber1)).isEqualTo(ApplicationProcessState.FOREGROUND);

    // Activity goes to Background
    monitor.onActivityStopped(activity1);
    assertThat(subscriberState.get(subscriber1)).isEqualTo(ApplicationProcessState.BACKGROUND);
  }

  @Test
  public void activityStateChanges_multipleSubscribers_callbackCalledOnEachSubscriber() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    Map<Integer, ApplicationProcessState> subscriberState = new HashMap<>();

    // Register callbacks, but note that each callback is saved in a local variable. Otherwise
    // WeakReference can get garbage collected, making this test flaky.
    final int subscriber1 = 1;
    AppStateCallback callback1 = newState -> subscriberState.put(subscriber1, newState);
    monitor.registerForAppState(new WeakReference<>(callback1));

    final int subscriber2 = 2;
    AppStateCallback callback2 = newState -> subscriberState.put(subscriber2, newState);
    monitor.registerForAppState(new WeakReference<>(callback2));

    final int subscriber3 = 3;
    AppStateCallback callback3 = newState -> subscriberState.put(subscriber3, newState);
    monitor.registerForAppState(new WeakReference<>(callback3));

    // Activity comes to Foreground
    monitor.onActivityResumed(activity1);
    assertThat(subscriberState.get(subscriber1)).isEqualTo(ApplicationProcessState.FOREGROUND);
    assertThat(subscriberState.get(subscriber2)).isEqualTo(ApplicationProcessState.FOREGROUND);
    assertThat(subscriberState.get(subscriber3)).isEqualTo(ApplicationProcessState.FOREGROUND);

    // Activity goes to Background
    monitor.onActivityStopped(activity1);
    assertThat(subscriberState.get(subscriber1)).isEqualTo(ApplicationProcessState.BACKGROUND);
    assertThat(subscriberState.get(subscriber2)).isEqualTo(ApplicationProcessState.BACKGROUND);
    assertThat(subscriberState.get(subscriber3)).isEqualTo(ApplicationProcessState.BACKGROUND);
  }

  @Test
  public void appColdStart_singleSubscriber_callbackIsCalled() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    FirebasePerformanceInitializer mockInitializer = mock(FirebasePerformanceInitializer.class);
    monitor.registerForAppColdStart(mockInitializer);

    // Activity comes to Foreground
    monitor.onActivityResumed(activity1);
    verify(mockInitializer, times(1)).onAppColdStart();
  }

  @Test
  public void appHotStart_singleSubscriber_callbackIsNotCalled() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    FirebasePerformanceInitializer mockInitializer = mock(FirebasePerformanceInitializer.class);
    monitor.registerForAppColdStart(mockInitializer);

    // Activity comes to Foreground
    monitor.onActivityResumed(activity1);
    verify(mockInitializer, times(1)).onAppColdStart();

    // Activity goes to Background
    monitor.onActivityStopped(activity1);

    // Activity comes to Foreground
    monitor.onActivityResumed(activity1);
    verify(mockInitializer, times(1)).onAppColdStart();
  }

  @Test
  public void appColdStart_multipleSubscriber_callbackIsCalled() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    FirebasePerformanceInitializer mockInitializer1 = mock(FirebasePerformanceInitializer.class);
    FirebasePerformanceInitializer mockInitializer2 = mock(FirebasePerformanceInitializer.class);
    monitor.registerForAppColdStart(mockInitializer1);
    monitor.registerForAppColdStart(mockInitializer2);

    // Activity comes to Foreground
    monitor.onActivityResumed(activity1);
    verify(mockInitializer1, times(1)).onAppColdStart();
    verify(mockInitializer2, times(1)).onAppColdStart();
  }

  @Test
  public void appColdStart_singleSubscriberRegistersForMultipleTimes_oneCallbackIsCalled() {
    AppStateMonitor monitor = new AppStateMonitor(transportManager, clock);
    FirebasePerformanceInitializer mockInitializer1 = mock(FirebasePerformanceInitializer.class);
    monitor.registerForAppColdStart(mockInitializer1);
    monitor.registerForAppColdStart(mockInitializer1);

    // Activity comes to Foreground
    monitor.onActivityResumed(activity1);
    verify(mockInitializer1, times(1)).onAppColdStart();
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
