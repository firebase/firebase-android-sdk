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

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.perf.internal.SessionManager;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.provider.FirebasePerfProvider;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A class to capture the Android AppStart Trace information. The first time activity goes through
 * onCreate()->onStart()->onResume() sequence is captured as app start timer and a TraceMetric log
 * is sent to server.
 *
 * <p>The first time any activity (activityC) enters onCreate() method we record an onCreateTime.
 * The first time any activity (activityS) enters onStart() method we record an onStartTime. The
 * first time any activity (activityR) enters onResume() method we record an onResumeTime and this
 * activity is recorded as AppStartActivity, this is end of AppStart trace..
 *
 * <p>In reality activityC, activityS and activityR do not need to be the same activity.
 *
 * @hide
 */
/** @hide */
public class AppStartTrace implements ActivityLifecycleCallbacks {
  private static final long MAX_LATENCY_BEFORE_UI_INIT = TimeUnit.MINUTES.toMicros(1);
  private static volatile AppStartTrace sInstance;

  /**
   * Called from onCreate() method of an activity by instrumented byte code.
   *
   * @param activity Activity class name.
   */
  @Keep
  public static void setLauncherActivityOnCreateTime(String activity) {
    // no-op, for backward compatibility with old version plugin.
  }

  /**
   * Called from onStart() method of an activity by instrumented byte code.
   *
   * @param activity Activity class name.
   */
  @Keep
  public static void setLauncherActivityOnStartTime(String activity) {
    // no-op, for backward compatibility with old version plugin.
  }
  /**
   * Called from onResume() method of an activity by instrumented byte code.
   *
   * @param activity Activity class name.
   */
  @Keep
  public static void setLauncherActivityOnResumeTime(String activity) {
    // no-op, for backward compatibility with old version plugin.
  }

  public static AppStartTrace getInstance() {
    return sInstance != null ? sInstance : getInstance(TransportManager.getInstance(), new Clock());
  }

  static AppStartTrace getInstance(TransportManager transportManager, Clock clock) {
    if (sInstance == null) {
      synchronized (AppStartTrace.class) {
        if (sInstance == null) {
          sInstance = new AppStartTrace(transportManager, clock);
        }
      }
    }
    return sInstance;
  }

  private boolean mRegistered = false;
  private final TransportManager transportManager;
  private final Clock mClock;
  private Context mAppContext;
  /**
   * The first time onCreate() of any activity is called, the activity is saved as mLaunchActivity.
   */
  private WeakReference<Activity> mLaunchActivity;
  /**
   * The first time onResume() of any activity is called, the activity is saved as mAppStartActivity
   */
  private WeakReference<Activity> mAppStartActivity;

  /**
   * If the time difference between app starts and creation of any Activity is larger than
   * MAX_LATENCY_BEFORE_UI_INIT, set mTooLateToInitUI to true and we don't send AppStart Trace.
   */
  private boolean mTooLateToInitUI = false;

  private Timer mOnCreateTime = null;
  private Timer mOnStartTime = null;
  private Timer mOnResumeTime = null;

  private boolean mIsStartFromBackground = false;

  AppStartTrace(@NonNull TransportManager transportManager, @NonNull Clock clock) {
    this.transportManager = transportManager;
    mClock = clock;
  }

  /** Called from FirebasePerfProvider to register this callback. */
  public synchronized void registerActivityLifecycleCallbacks(@NonNull Context context) {
    // Make sure the callback is registered only once.
    if (mRegistered) {
      return;
    }
    Context appContext = context.getApplicationContext();
    if (appContext instanceof Application) {
      ((Application) appContext).registerActivityLifecycleCallbacks(this);
      mRegistered = true;
      mAppContext = appContext;
    }
  }

  /** Unregister this callback after AppStart trace is logged. */
  public synchronized void unregisterActivityLifecycleCallbacks() {
    if (!mRegistered) {
      return;
    }
    ((Application) mAppContext).unregisterActivityLifecycleCallbacks(this);
    mRegistered = false;
  }

  @Override
  public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    if (mIsStartFromBackground || mOnCreateTime != null // An activity already called onCreate()
    ) {
      return;
    }

    mLaunchActivity = new WeakReference<Activity>(activity);
    mOnCreateTime = mClock.getTime();

    if (FirebasePerfProvider.getAppStartTime().getDurationMicros(mOnCreateTime)
        > MAX_LATENCY_BEFORE_UI_INIT) {
      mTooLateToInitUI = true;
    }
  }

  @Override
  public synchronized void onActivityStarted(Activity activity) {
    if (mIsStartFromBackground
        || mOnStartTime != null // An activity already called onStart()
        || mTooLateToInitUI) {
      return;
    }
    mOnStartTime = mClock.getTime();
  }

  @Override
  public synchronized void onActivityResumed(Activity activity) {
    if (mIsStartFromBackground
        || mOnResumeTime != null // An activity already called onResume()
        || mTooLateToInitUI) {
      return;
    }

    mAppStartActivity = new WeakReference<Activity>(activity);

    mOnResumeTime = mClock.getTime();
    final Timer startTime = FirebasePerfProvider.getAppStartTime();
    AndroidLogger.getInstance()
        .debug(
            "onResume(): "
                + activity.getClass().getName()
                + ": "
                + startTime.getDurationMicros(mOnResumeTime)
                + " microseconds");

    TraceMetric.Builder metric =
        TraceMetric.newBuilder()
            .setName(Constants.TraceNames.APP_START_TRACE_NAME.toString())
            .setClientStartTimeUs(startTime.getMicros())
            .setDurationUs(startTime.getDurationMicros(mOnResumeTime));
    List<TraceMetric> subtraces = new ArrayList<>(/* initialCapacity= */ 3);

    TraceMetric.Builder temp =
        TraceMetric.newBuilder()
            .setName(Constants.TraceNames.ON_CREATE_TRACE_NAME.toString())
            .setClientStartTimeUs(startTime.getMicros())
            .setDurationUs(startTime.getDurationMicros(mOnCreateTime));
    subtraces.add(temp.build());

    temp = TraceMetric.newBuilder();
    temp.setName(Constants.TraceNames.ON_START_TRACE_NAME.toString())
        .setClientStartTimeUs(mOnCreateTime.getMicros())
        .setDurationUs(mOnCreateTime.getDurationMicros(mOnStartTime));
    subtraces.add(temp.build());

    temp = TraceMetric.newBuilder();
    temp.setName(Constants.TraceNames.ON_RESUME_TRACE_NAME.toString())
        .setClientStartTimeUs(mOnStartTime.getMicros())
        .setDurationUs(mOnStartTime.getDurationMicros(mOnResumeTime));
    subtraces.add(temp.build());

    metric
        .addAllSubtraces(subtraces)
        .addPerfSessions(SessionManager.getInstance().perfSession().build());

    transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);

    if (mRegistered) {
      // After AppStart trace is logged, we can unregister this callback.
      unregisterActivityLifecycleCallbacks();
    }
  }

  @Override
  public void onActivityPaused(Activity activity) {}

  @Override
  public synchronized void onActivityStopped(Activity activity) {}

  @Override
  public void onActivityDestroyed(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  /**
   * We use StartFromBackgroundRunnable to detect if app is started from background or foreground.
   * If app is started from background, we do not generate AppStart trace. This runnable is posted
   * to main UI thread from FirebasePerfProvider. If app is started from background, this runnable
   * will be executed before any activity's onCreate() method. If app is started from foreground,
   * activity's onCreate() method is executed before this runnable.
   */
  public static class StartFromBackgroundRunnable implements Runnable {
    private final AppStartTrace mTrace;

    public StartFromBackgroundRunnable(final AppStartTrace trace) {
      mTrace = trace;
    }

    @Override
    public void run() {
      // if no activity has ever been created.
      if (mTrace.mOnCreateTime == null) {
        mTrace.mIsStartFromBackground = true;
      }
    }
  }

  @VisibleForTesting
  @Nullable
  Activity getLaunchActivity() {
    return mLaunchActivity.get();
  }

  @VisibleForTesting
  @Nullable
  Activity getAppStartActivity() {
    return mAppStartActivity.get();
  }

  @VisibleForTesting
  Timer getOnCreateTime() {
    return mOnCreateTime;
  }

  @VisibleForTesting
  Timer getOnStartTime() {
    return mOnStartTime;
  }

  @VisibleForTesting
  Timer getOnResumeTime() {
    return mOnResumeTime;
  }

  @VisibleForTesting
  void setIsStartFromBackground() {
    mIsStartFromBackground = true;
  }
}
