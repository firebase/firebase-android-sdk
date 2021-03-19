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

/**
 * Trace timer implementation to send foreground and background session log.
 *
 * @hide
 */

/** @hide */
public class AppStateMonitor implements ActivityLifecycleCallbacks {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private static volatile AppStateMonitor sInstance;

  public static AppStateMonitor getInstance() {
    if (sInstance == null) {
      synchronized (AppStateMonitor.class) {
        if (sInstance == null) {
          sInstance = new AppStateMonitor(TransportManager.getInstance(), new Clock());
        }
      }
    }
    return sInstance;
  }

  private static final String FRAME_METRICS_AGGREGATOR_CLASSNAME =
      "androidx.core.app.FrameMetricsAggregator";
  private boolean mRegistered = false;
  private final TransportManager transportManager;
  private ConfigResolver mConfigResolver;
  private final Clock mClock;
  private boolean mIsColdStart = true;
  private final WeakHashMap<Activity, Boolean> mResumed = new WeakHashMap<>();
  private Timer mStopTime; // The time app goes to background
  private Timer mResumeTime; // The time app comes to foreground
  private final Map<String, Long> mMetrics = new HashMap<>();
  /* Count for TRACE_STARTED_NOT_STOPPED */
  private AtomicInteger mTsnsCount = new AtomicInteger(0);

  private ApplicationProcessState mCurrentState = ApplicationProcessState.BACKGROUND;

  private Set<WeakReference<AppStateCallback>> mClients =
      new HashSet<WeakReference<AppStateCallback>>();

  private boolean hasFrameMetricsAggregator = false;
  private FrameMetricsAggregator mFrameMetricsAggregator;
  private final WeakHashMap<Activity, Trace> mActivity2ScreenTrace = new WeakHashMap<>();

  AppStateMonitor(TransportManager transportManager, Clock clock) {
    this.transportManager = transportManager;
    mClock = clock;
    mConfigResolver = ConfigResolver.getInstance();
    hasFrameMetricsAggregator = hasFrameMetricsAggregatorClass();
    if (hasFrameMetricsAggregator) {
      mFrameMetricsAggregator = new FrameMetricsAggregator();
    }
  }

  public synchronized void registerActivityLifecycleCallbacks(Context context) {
    // Make sure the callback is registered only once.
    if (mRegistered) {
      return;
    }
    Context appContext = context.getApplicationContext();
    if (appContext instanceof Application) {
      ((Application) appContext).registerActivityLifecycleCallbacks(this);
      mRegistered = true;
    }
  }

  public synchronized void unregisterActivityLifecycleCallbacks(Context context) {
    if (!mRegistered) {
      return;
    }
    Context appContext = context.getApplicationContext();
    if (appContext instanceof Application) {
      ((Application) appContext).unregisterActivityLifecycleCallbacks(this);
      mRegistered = false;
    }
  }

  /** @hide */
  /** @hide */
  public void incrementCount(@NonNull String name, long value) {
    // This method is called by RateLimiter.java when a log exceeds rate limit and to be dropped
    // It can be on any thread. sendSessionLog() method is called in callback methods from main UI
    // thread, thus we need synchronized access on mMetrics.
    synchronized (mMetrics) {
      Long v = mMetrics.get(name);
      if (v == null) {
        mMetrics.put(name, value);
      } else {
        mMetrics.put(name, v + value);
      }
    }
  }

  /** @hide */
  /** @hide */
  public void incrementTsnsCount(int value) {
    mTsnsCount.addAndGet(value);
  }

  /** @hide */
  /** @hide */
  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

  /** @hide */
  /** @hide */
  @Override
  public void onActivityDestroyed(Activity activity) {}

  /** @hide */
  /** @hide */
  @Override
  public synchronized void onActivityStarted(Activity activity) {
    if (isScreenTraceSupported(activity) && mConfigResolver.isPerformanceMonitoringEnabled()) {
      // Starts recording frame metrics for this activity.
      mFrameMetricsAggregator.add(activity);
      // Start the Trace
      Trace screenTrace = new Trace(getScreenTraceName(activity), transportManager, mClock, this);
      screenTrace.start();
      mActivity2ScreenTrace.put(activity, screenTrace);
    }
  }

  /** @hide */
  /** @hide */
  @Override
  public synchronized void onActivityStopped(Activity activity) {
    if (isScreenTraceSupported(activity)) {
      sendScreenTrace(activity);
    }

    // Last activity has its onActivityStopped called, the app goes to background.
    if (mResumed.containsKey(activity)) {
      mResumed.remove(activity);
      if (mResumed.isEmpty()) {
        // no more activity in foreground, app goes to background.
        mStopTime = mClock.getTime();
        updateAppState(ApplicationProcessState.BACKGROUND);
        sendSessionLog(
            Constants.TraceNames.FOREGROUND_TRACE_NAME.toString(), mResumeTime, mStopTime);
      }
    }
  }

  /** @hide */
  /** @hide */
  @Override
  public synchronized void onActivityResumed(Activity activity) {
    // cases:
    // 1. At app startup, first activity comes to foreground.
    // 2. app switch from background to foreground.
    // 3. app already in foreground, current activity is replaced by another activity.
    if (mResumed.isEmpty()) {
      // The first resumed activity means app comes to foreground.
      mResumeTime = mClock.getTime();
      mResumed.put(activity, true);
      updateAppState(ApplicationProcessState.FOREGROUND);
      if (mIsColdStart) {
        // case 1: app startup.
        mIsColdStart = false;
      } else {
        // case 2: app switch from background to foreground.
        sendSessionLog(
            Constants.TraceNames.BACKGROUND_TRACE_NAME.toString(), mStopTime, mResumeTime);
      }
    } else {
      // case 3: app already in foreground, current activity is replaced by another activity.
      mResumed.put(activity, true);
    }
  }

  /** Returns if this is the cold start of the app. */
  public boolean isColdStart() {
    return mIsColdStart;
  }

  /**
   * @return current app state.
   * @hide
   */
  /** @hide */
  public ApplicationProcessState getAppState() {
    return mCurrentState;
  }

  /**
   * Register a client to receive app state update.
   *
   * @param client an AppStateCallback instance.
   * @hide
   */
  /** @hide */
  public void registerForAppState(WeakReference<AppStateCallback> client) {
    synchronized (mClients) {
      mClients.add(client);
    }
  }

  /**
   * Unregister the client to stop receiving app state update.
   *
   * @param client an AppStateCallback instance.
   * @hide
   */
  /** @hide */
  public void unregisterForAppState(WeakReference<AppStateCallback> client) {
    synchronized (mClients) {
      mClients.remove(client);
    }
  }

  /** Send update state update to registered clients. */
  private void updateAppState(ApplicationProcessState newState) {
    mCurrentState = newState;
    synchronized (mClients) {
      for (Iterator<WeakReference<AppStateCallback>> i = mClients.iterator(); i.hasNext(); ) {
        AppStateCallback callback = i.next().get();
        if (callback != null) {
          callback.onUpdateAppState(mCurrentState);
        } else {
          // The object pointing by WeakReference has already been garbage collected.
          // Remove it from the Set.
          i.remove();
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
    return mCurrentState == ApplicationProcessState.FOREGROUND;
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
    if (!mActivity2ScreenTrace.containsKey(activity)) {
      return;
    }
    Trace screenTrace = mActivity2ScreenTrace.get(activity);
    if (screenTrace == null) {
      return;
    }
    mActivity2ScreenTrace.remove(activity);

    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;
    // Stops recording metrics for this Activity and returns the currently-collected metrics
    SparseIntArray[] arr = mFrameMetricsAggregator.remove(activity);
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
    if (!mConfigResolver.isPerformanceMonitoringEnabled()) {
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
    int tsnsCount = mTsnsCount.getAndSet(0);
    synchronized (mMetrics) {
      metric.putAllCounters(mMetrics);
      if (tsnsCount != 0) {
        metric.putCounters(CounterNames.TRACE_STARTED_NOT_STOPPED.toString(), tsnsCount);
      }

      // reset metrics.
      mMetrics.clear();
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

  /**
   * An interface to be implemented by clients which needs to receive app state update.
   *
   * @hide
   */
  /** @hide */
  public static interface AppStateCallback {
    /** @hide */
    /** @hide */
    public void onUpdateAppState(ApplicationProcessState newState);
  }

  /**
   * Screen trace name is prefix "_st_" concatenates with Activity's class name.
   *
   * @param activity activity object.
   * @return screen trace name.
   * @hide
   */
  /** @hide */
  public static String getScreenTraceName(Activity activity) {
    return Constants.SCREEN_TRACE_PREFIX + activity.getClass().getSimpleName();
  }

  @VisibleForTesting
  WeakHashMap<Activity, Boolean> getResumed() {
    return mResumed;
  }

  @VisibleForTesting
  WeakHashMap<Activity, Trace> getActivity2ScreenTrace() {
    return mActivity2ScreenTrace;
  }

  @VisibleForTesting
  Timer getPauseTime() {
    return mStopTime;
  }

  @VisibleForTesting
  Timer getResumeTime() {
    return mResumeTime;
  }

  @VisibleForTesting
  void setIsColdStart(boolean isColdStart) {
    mIsColdStart = isColdStart;
  }
}
