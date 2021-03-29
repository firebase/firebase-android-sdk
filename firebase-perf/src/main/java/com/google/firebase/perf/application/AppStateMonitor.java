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
import android.util.SparseIntArray;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.core.app.FrameMetricsAggregator;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Constants.CounterNames;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.util.Utils;
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
  private static final String FRAME_METRICS_AGGREGATOR_CLASSNAME =
      "androidx.core.app.FrameMetricsAggregator";

  private static volatile AppStateMonitor instance;

  private final WeakHashMap<Activity, Boolean> activityToResumedMap = new WeakHashMap<>();
  private final WeakHashMap<Activity, Trace> activityToScreenTraceMap = new WeakHashMap<>();
  private final Map<String, Long> metricToCountMap = new HashMap<>();
  private final Set<WeakReference<AppStateCallback>> appStateSubscribers = new HashSet<>();
  private Set<AppColdStartCallback> appColdStartSubscribers = new HashSet<>();

  /* Count for TRACE_STARTED_NOT_STOPPED */
  private final AtomicInteger tsnsCount = new AtomicInteger(0);

  private final TransportManager transportManager;
  private final ConfigResolver configResolver;
  private final Clock clock;

  private FrameMetricsAggregator frameMetricsAggregator;

  private Timer resumeTime; // The time app comes to foreground
  private Timer stopTime; // The time app goes to background

  private ApplicationProcessState currentAppState = ApplicationProcessState.BACKGROUND;

  private boolean isRegisteredForLifecycleCallbacks = false;
  private boolean isColdStart = true;
  private boolean hasFrameMetricsAggregator = false;

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
    this.transportManager = transportManager;
    this.clock = clock;
    configResolver = ConfigResolver.getInstance();
    hasFrameMetricsAggregator = hasFrameMetricsAggregatorClass();
    if (hasFrameMetricsAggregator) {
      frameMetricsAggregator = new FrameMetricsAggregator();
    }
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

  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

  @Override
  public void onActivityDestroyed(Activity activity) {}

  @Override
  public synchronized void onActivityStarted(Activity activity) {
    if (isScreenTraceSupported(activity) && configResolver.isPerformanceMonitoringEnabled()) {
      // Starts recording frame metrics for this activity.
      frameMetricsAggregator.add(activity);
      // Start the Trace
      Trace screenTrace = new Trace(getScreenTraceName(activity), transportManager, clock, this);
      screenTrace.start();
      activityToScreenTraceMap.put(activity, screenTrace);
    }
  }

  @Override
  public synchronized void onActivityStopped(Activity activity) {
    if (isScreenTraceSupported(activity)) {
      sendScreenTrace(activity);
    }

    // Last activity has its onActivityStopped called, the app goes to background.
    if (activityToResumedMap.containsKey(activity)) {
      activityToResumedMap.remove(activity);
      if (activityToResumedMap.isEmpty()) {
        // no more activity in foreground, app goes to background.
        stopTime = clock.getTime();
        updateAppState(ApplicationProcessState.BACKGROUND);
        sendSessionLog(Constants.TraceNames.FOREGROUND_TRACE_NAME.toString(), resumeTime, stopTime);
      }
    }
  }

  @Override
  public synchronized void onActivityResumed(Activity activity) {
    // cases:
    // 1. At app startup, first activity comes to foreground.
    // 2. app switch from background to foreground.
    // 3. app already in foreground, current activity is replaced by another activity.
    if (activityToResumedMap.isEmpty()) {
      // The first resumed activity means app comes to foreground.
      resumeTime = clock.getTime();
      activityToResumedMap.put(activity, true);
      updateAppState(ApplicationProcessState.FOREGROUND);
      if (isColdStart) {
        // case 1: app startup.
        sendAppColdStartUpdate();
        isColdStart = false;
      } else {
        // case 2: app switch from background to foreground.
        sendSessionLog(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString(), stopTime, resumeTime);
      }
    } else {
      // case 3: app already in foreground, current activity is replaced by another activity.
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
    synchronized (appStateSubscribers) {
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
    synchronized (appStateSubscribers) {
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
   * Send screen trace.
   *
   * @param activity activity object.
   */
  private void sendScreenTrace(Activity activity) {
    if (!activityToScreenTraceMap.containsKey(activity)) {
      return;
    }
    Trace screenTrace = activityToScreenTraceMap.get(activity);
    if (screenTrace == null) {
      return;
    }
    activityToScreenTraceMap.remove(activity);

    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;
    // Stops recording metrics for this Activity and returns the currently-collected metrics
    SparseIntArray[] arr = frameMetricsAggregator.remove(activity);
    if (arr != null) {
      SparseIntArray frameTimes = arr[FrameMetricsAggregator.TOTAL_INDEX];
      if (frameTimes != null) {
        for (int i = 0; i < frameTimes.size(); i++) {
          int frameTime = frameTimes.keyAt(i);
          int numFrames = frameTimes.valueAt(i);
          totalFrames += numFrames;
          if (frameTime > Constants.FROZEN_FRAME_TIME) {
            // Frozen frames mean the app appear frozen.  The recommended thresholds is 700ms
            frozenFrames += numFrames;
          }
          if (frameTime > Constants.SLOW_FRAME_TIME) {
            // Slow frames are anything above 16ms (i.e. 60 frames/second)
            slowFrames += numFrames;
          }
        }
      }
    }
    if (totalFrames == 0 && slowFrames == 0 && frozenFrames == 0) {
      // All metrics are zero, no need to send screen trace.
      // return;
    }
    // Only incrementMetric if corresponding metric is non-zero.
    if (totalFrames > 0) {
      screenTrace.putMetric(Constants.CounterNames.FRAMES_TOTAL.toString(), totalFrames);
    }
    if (slowFrames > 0) {
      screenTrace.putMetric(Constants.CounterNames.FRAMES_SLOW.toString(), slowFrames);
    }
    if (frozenFrames > 0) {
      screenTrace.putMetric(Constants.CounterNames.FRAMES_FROZEN.toString(), frozenFrames);
    }
    if (Utils.isDebugLoggingEnabled(activity.getApplicationContext())) {
      logger.debug(
          "sendScreenTrace name:"
              + getScreenTraceName(activity)
              + " _fr_tot:"
              + totalFrames
              + " _fr_slo:"
              + slowFrames
              + " _fr_fzn:"
              + frozenFrames);
    }
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
   * Only send screen trace if FrameMetricsAggregator exists and the activity is hardware
   * accelerated.
   *
   * @param activity The Activity for which we're monitoring the screen rendering performance.
   * @return true if supported, false if not.
   */
  private boolean isScreenTraceSupported(Activity activity) {
    return hasFrameMetricsAggregator
        // This check is needed because we can't observe frame rates for a non hardware accelerated
        // view.
        // See b/133827763.
        && activity.getWindow() != null
        && ((activity.getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            != 0);
  }

  /**
   * FrameMetricsAggregator first appears in Android Support Library 26.1.0. Before GMSCore SDK is
   * updated to 26.1.0 (b/69954793), there will be ClassNotFoundException. This method is to check
   * if FrameMetricsAggregator exists to avoid ClassNotFoundException.
   */
  private boolean hasFrameMetricsAggregatorClass() {
    try {
      Class<?> initializerClass = Class.forName(FRAME_METRICS_AGGREGATOR_CLASSNAME);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
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
  Timer getResumeTime() {
    return resumeTime;
  }

  @VisibleForTesting
  public void setIsColdStart(boolean isColdStart) {
    this.isColdStart = isColdStart;
  }
}
