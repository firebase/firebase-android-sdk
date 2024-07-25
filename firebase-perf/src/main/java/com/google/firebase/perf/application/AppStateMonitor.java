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

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Constants.CounterNames;
import com.google.firebase.perf.util.Optional;
import com.google.firebase.perf.util.ScreenTraceUtil;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Trace timer implementation to send foreground and background session log. */
public class AppStateMonitor implements ActivityLifecycleCallbacks {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private static volatile AppStateMonitor instance;

  private final WeakHashMap<Activity, Boolean> activityToResumedMap = new WeakHashMap<>();
  private final WeakHashMap<Activity, FrameMetricsRecorder> activityToRecorderMap =
      new WeakHashMap<>();

  // Map for holding the fragment state monitor to remove receiving the fragment state callbacks
  private final WeakHashMap<Activity, FragmentStateMonitor> activityToFragmentStateMonitorMap =
      new WeakHashMap<>();
  private final WeakHashMap<Activity, Trace> activityToScreenTraceMap = new WeakHashMap<>();
  private final Map<String, Long> metricToCountMap = new HashMap<>();
  private final Set<WeakReference<AppStateCallback>> appStateSubscribers = new HashSet<>();
  private Set<AppColdStartCallback> appColdStartSubscribers = new HashSet<>();

  /* Count for TRACE_STARTED_NOT_STOPPED */
  private final AtomicInteger tsnsCount = new AtomicInteger(0);

  private final TransportManager transportManager;
  private final ConfigResolver configResolver;
  private final Clock clock;
  private final boolean screenPerformanceRecordingSupported;

  private Timer resumeTime; // The time app comes to foreground
  private Timer stopTime; // The time app goes to background

  private ApplicationProcessState currentAppState = ApplicationProcessState.BACKGROUND;

  private boolean isRegisteredForLifecycleCallbacks = false;
  private boolean isColdStart = true;

  public static AppStateMonitor getInstance() {
    if (instance == null) {
      synchronized (AppStateMonitor.class) {
        if (instance == null) {
          instance = new AppStateMonitor(TransportManager.getInstance(), new Clock());
        }
      }
    }
    return instance;
  }

  AppStateMonitor(TransportManager transportManager, Clock clock) {
    this(
        transportManager,
        clock,
        ConfigResolver.getInstance(),
        isScreenPerformanceRecordingSupported());
  }

  @VisibleForTesting
  AppStateMonitor(
      TransportManager transportManager,
      Clock clock,
      ConfigResolver configResolver,
      boolean screenPerformanceRecordingSupported) {
    this.transportManager = transportManager;
    this.clock = clock;
    this.configResolver = configResolver;
    this.screenPerformanceRecordingSupported = screenPerformanceRecordingSupported;
  }

  public synchronized void registerActivityLifecycleCallbacks(Context context) {
    // Make sure the callback is registered only once.
    if (isRegisteredForLifecycleCallbacks) {
      return;
    }
    Context appContext = context.getApplicationContext();
    if (appContext instanceof Application) {
      ((Application) appContext).registerActivityLifecycleCallbacks(this);
      isRegisteredForLifecycleCallbacks = true;
    }
  }

  public synchronized void unregisterActivityLifecycleCallbacks(Context context) {
    if (!isRegisteredForLifecycleCallbacks) {
      return;
    }
    Context appContext = context.getApplicationContext();
    if (appContext instanceof Application) {
      ((Application) appContext).unregisterActivityLifecycleCallbacks(this);
      isRegisteredForLifecycleCallbacks = false;
    }
  }

  public void incrementCount(@NonNull String name, long value) {
    // This method is called by RateLimiter.java when a log exceeds rate limit and to be dropped
    // It can be on any thread. sendSessionLog() method is called in callback methods from main UI
    // thread, thus we need synchronized access on mMetrics.
    synchronized (metricToCountMap) {
      Long v = metricToCountMap.get(name);
      if (v == null) {
        metricToCountMap.put(name, value);
      } else {
        metricToCountMap.put(name, v + value);
      }
    }
  }

  public void incrementTsnsCount(int value) {
    tsnsCount.addAndGet(value);
  }

  // Starts tracking the frame metrics for an activity.
  private void startFrameMonitoring(Activity activity) {
    if (isScreenTraceSupported() && configResolver.isPerformanceMonitoringEnabled()) {
      FrameMetricsRecorder recorder = new FrameMetricsRecorder(activity);
      activityToRecorderMap.put(activity, recorder);
      if (activity instanceof FragmentActivity) {
        FragmentStateMonitor fragmentStateMonitor =
            new FragmentStateMonitor(clock, transportManager, this, recorder);
        activityToFragmentStateMonitorMap.put(activity, fragmentStateMonitor);
        FragmentActivity fragmentActivity = (FragmentActivity) activity;
        fragmentActivity
            .getSupportFragmentManager()
            .registerFragmentLifecycleCallbacks(fragmentStateMonitor, true);
      }
    }
  }

  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    startFrameMonitoring(activity);
  }

  @Override
  public void onActivityDestroyed(Activity activity) {
    // Dereference FrameMetricsRecorder from the map because it holds an Activity reference
    activityToRecorderMap.remove(activity);
    // Dereference FragmentStateMonitor because it holds a FrameMetricsRecorder reference
    if (activityToFragmentStateMonitorMap.containsKey(activity)) {
      FragmentActivity fragmentActivity = (FragmentActivity) activity;
      fragmentActivity
          .getSupportFragmentManager()
          .unregisterFragmentLifecycleCallbacks(activityToFragmentStateMonitorMap.remove(activity));
    }
  }

  @Override
  public synchronized void onActivityStarted(Activity activity) {
    if (isScreenTraceSupported() && configResolver.isPerformanceMonitoringEnabled()) {
      if (!activityToRecorderMap.containsKey(activity)) {
        // If performance monitoring is disabled at start and enabled at runtime, start monitoring
        // the activity as the app comes to foreground.
        startFrameMonitoring(activity);
      }
      // Starts recording frame metrics for this activity.
      activityToRecorderMap.get(activity).start();
      // Start the Trace
      Trace screenTrace = new Trace(getScreenTraceName(activity), transportManager, clock, this);
      screenTrace.start();
      activityToScreenTraceMap.put(activity, screenTrace);
    }
  }

  @Override
  public synchronized void onActivityStopped(Activity activity) {
    if (isScreenTraceSupported()) {
      sendScreenTrace(activity);
    }

    // Last activity has its onActivityStopped called, the app goes to background.
    if (activityToResumedMap.containsKey(activity)) {
      activityToResumedMap.remove(activity);
      if (activityToResumedMap.isEmpty()) {
        // no more activity in foreground, app goes to background.
        stopTime = clock.getTime();
        sendSessionLog(Constants.TraceNames.FOREGROUND_TRACE_NAME.toString(), resumeTime, stopTime);
        // order is important to complete _fs before triggering a sessionId change b/204362742
        updateAppState(ApplicationProcessState.BACKGROUND);
      }
    }
  }

  @Override
  public synchronized void onActivityResumed(Activity activity) {
    // cases:
    // 1. At app startup, first activity comes to foreground.
    // 2. app switch from background to foreground.
    // 3. app already in foreground, current activity is replaced by another activity, or the
    // current activity was paused then resumed without onStop, for example by an AlertDialog
    if (activityToResumedMap.isEmpty()) {
      // The first resumed activity means app comes to foreground.
      resumeTime = clock.getTime();
      activityToResumedMap.put(activity, true);
      if (isColdStart) {
        // case 1: app startup.
        updateAppState(ApplicationProcessState.FOREGROUND);
        sendAppColdStartUpdate();
        isColdStart = false;
      } else {
        // case 2: app switch from background to foreground.
        sendSessionLog(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString(), stopTime, resumeTime);
        // order is important to complete _bs before triggering a sessionId change b/204362742
        updateAppState(ApplicationProcessState.FOREGROUND);
      }
    } else {
      // case 3: app already in foreground, current activity is replaced by another activity, or the
      // current activity was paused then resumed without onStop, for example by an AlertDialog
      activityToResumedMap.put(activity, true);
    }
  }

  /** Returns if this is the cold start of the app. */
  public boolean isColdStart() {
    return isColdStart;
  }

  /** @return current app state. */
  public ApplicationProcessState getAppState() {
    return currentAppState;
  }

  /**
   * Register a subscriber to receive app state update.
   *
   * @param subscriber an AppStateCallback instance.
   */
  public void registerForAppState(WeakReference<AppStateCallback> subscriber) {
    synchronized (appStateSubscribers) {
      appStateSubscribers.add(subscriber);
    }
  }

  /**
   * Unregister the subscriber to stop receiving app state update.
   *
   * @param subscriber an AppStateCallback instance.
   */
  public void unregisterForAppState(WeakReference<AppStateCallback> subscriber) {
    synchronized (appStateSubscribers) {
      appStateSubscribers.remove(subscriber);
    }
  }

  /**
   * Register a subscriber to receive app cold start update.
   *
   * @param subscriber the {@link AppColdStartCallback} instance.
   */
  public void registerForAppColdStart(AppColdStartCallback subscriber) {
    synchronized (appColdStartSubscribers) {
      appColdStartSubscribers.add(subscriber);
    }
  }

  /** Send update state update to registered subscribers. */
  private void updateAppState(ApplicationProcessState newState) {
    currentAppState = newState;
    synchronized (appStateSubscribers) {
      for (Iterator<WeakReference<AppStateCallback>> i = appStateSubscribers.iterator();
          i.hasNext(); ) {
        AppStateCallback callback = i.next().get();
        if (callback != null) {
          callback.onUpdateAppState(currentAppState);
        } else {
          // The object pointing by WeakReference has already been garbage collected.
          // Remove it from the Set.
          i.remove();
        }
      }
    }
  }

  /** Send cold start update to registered subscribers. */
  private void sendAppColdStartUpdate() {
    synchronized (appColdStartSubscribers) {
      for (Iterator<AppColdStartCallback> i = appColdStartSubscribers.iterator(); i.hasNext(); ) {
        AppColdStartCallback callback = i.next();
        if (callback != null) {
          callback.onAppColdStart();
        }
      }
    }
  }

  /**
   * Return app is in foreground or not.
   *
   * @return true if app is in foreground, false if in background.
   */
  public boolean isForeground() {
    return currentAppState == ApplicationProcessState.FOREGROUND;
  }

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  @Override
  public void onActivityPaused(Activity activity) {}

  /**
   * Sends the screen trace for the provided activity.
   *
   * @param activity activity object.
   */
  private void sendScreenTrace(Activity activity) {
    Trace screenTrace = activityToScreenTraceMap.get(activity);
    if (screenTrace == null) {
      return;
    }
    activityToScreenTraceMap.remove(activity);

    Optional<PerfFrameMetrics> perfFrameMetrics = activityToRecorderMap.get(activity).stop();
    if (!perfFrameMetrics.isAvailable()) {
      logger.warn("Failed to record frame data for %s.", activity.getClass().getSimpleName());
      return;
    }
    ScreenTraceUtil.addFrameCounters(screenTrace, perfFrameMetrics.get());
    // Stop and record trace
    screenTrace.stop();
  }

  /**
   * Send foreground/background session trace.
   *
   * @param name trace name.
   * @param startTime session trace start time.
   * @param endTime session trace end time.
   */
  private void sendSessionLog(String name, Timer startTime, Timer endTime) {
    if (!configResolver.isPerformanceMonitoringEnabled()) {
      return;
    }
    // TODO(b/117776450): We should also capture changes in the Session ID.
    TraceMetric.Builder metric =
        TraceMetric.newBuilder()
            .setName(name)
            .setClientStartTimeUs(startTime.getMicros())
            .setDurationUs(startTime.getDurationMicros(endTime))
            .addPerfSessions(SessionManager.getInstance().perfSession().build());
    // Atomically get mTsnsCount and set it to zero.
    int tsnsCount = this.tsnsCount.getAndSet(0);
    synchronized (metricToCountMap) {
      metric.putAllCounters(metricToCountMap);
      if (tsnsCount != 0) {
        metric.putCounters(CounterNames.TRACE_STARTED_NOT_STOPPED.toString(), tsnsCount);
      }

      // reset metrics.
      metricToCountMap.clear();
    }
    // The Foreground and Background trace marks the transition between the two states,
    // so we always specify the state to be ApplicationProcessState.FOREGROUND_BACKGROUND.
    transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);
  }

  /**
   * Only send screen trace if FrameMetricsAggregator exists.
   *
   * @return true if supported, false if not.
   */
  protected boolean isScreenTraceSupported() {
    return screenPerformanceRecordingSupported;
  }

  /**
   * FrameMetricsAggregator first appears in Android Support Library 26.1.0. Before GMSCore SDK is
   * updated to 26.1.0 (b/69954793), there will be ClassNotFoundException. This method is to check
   * if FrameMetricsAggregator exists to avoid ClassNotFoundException.
   */
  private static boolean isScreenPerformanceRecordingSupported() {
    return FrameMetricsRecorder.isFrameMetricsRecordingSupported();
  }

  /** An interface to be implemented by subscribers which needs to receive app state update. */
  public static interface AppStateCallback {
    public void onUpdateAppState(ApplicationProcessState newState);
  }

  /** An interface to be implemented by subscribers which needs to receive app cold start update. */
  public static interface AppColdStartCallback {
    public void onAppColdStart();
  }

  /**
   * Screen trace name is prefix "_st_" concatenates with Activity's class name.
   *
   * @param activity activity object.
   * @return screen trace name.
   */
  public static String getScreenTraceName(Activity activity) {
    return Constants.SCREEN_TRACE_PREFIX + activity.getClass().getSimpleName();
  }

  @VisibleForTesting
  WeakHashMap<Activity, Boolean> getResumed() {
    return activityToResumedMap;
  }

  @VisibleForTesting
  WeakHashMap<Activity, Trace> getActivity2ScreenTrace() {
    return activityToScreenTraceMap;
  }

  @VisibleForTesting
  Timer getPauseTime() {
    return stopTime;
  }

  @VisibleForTesting
  void setStopTime(Timer timer) {
    stopTime = timer;
  }

  @VisibleForTesting
  Timer getResumeTime() {
    return resumeTime;
  }

  @VisibleForTesting
  public void setIsColdStart(boolean isColdStart) {
    this.isColdStart = isColdStart;
  }
}
