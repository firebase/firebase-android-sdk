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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.StartupTime;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.FirstDrawDoneListener;
import com.google.firebase.perf.util.PreDrawListener;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
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
public class AppStartTrace implements ActivityLifecycleCallbacks {

  private static final long MAX_LATENCY_BEFORE_UI_INIT = TimeUnit.MINUTES.toMicros(1);

  // Core pool size 0 allows threads to shut down if they're idle
  private static final int CORE_POOL_SIZE = 0;
  private static final int MAX_POOL_SIZE = 1; // Only need single thread

  private static volatile AppStartTrace instance;
  private static ExecutorService executorService;

  private boolean isRegisteredForLifecycleCallbacks = false;
  private final TransportManager transportManager;
  private final Clock clock;
  private final ConfigResolver configResolver;
  private final TraceMetric.Builder experimentTtid;
  private Context appContext;
  /**
   * The first time onCreate() of any activity is called, the activity is saved as launchActivity.
   */
  private WeakReference<Activity> launchActivity;
  /**
   * The first time onResume() of any activity is called, the activity is saved as appStartActivity
   */
  private WeakReference<Activity> appStartActivity;

  /**
   * If the time difference between app starts and creation of any Activity is larger than
   * MAX_LATENCY_BEFORE_UI_INIT, set mTooLateToInitUI to true and we don't send AppStart Trace.
   */
  private boolean isTooLateToInitUI = false;

  private static Timer firebaseStartupTime = null;

  private Timer appStartTime = null;
  private Timer onCreateTime = null;
  private Timer onStartTime = null;
  private Timer onResumeTime = null;
  private Timer firstDrawDone = null;
  private Timer preDraw = null;

  private PerfSession startSession;
  private boolean isStartedFromBackground = false;

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
    return instance != null ? instance : getInstance(TransportManager.getInstance(), new Clock());
  }

  // TODO(b/258263016): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  static AppStartTrace getInstance(TransportManager transportManager, Clock clock) {
    if (instance == null) {
      synchronized (AppStartTrace.class) {
        if (instance == null) {
          instance =
              new AppStartTrace(
                  transportManager,
                  clock,
                  ConfigResolver.getInstance(),
                  new ThreadPoolExecutor(
                      CORE_POOL_SIZE,
                      MAX_POOL_SIZE,
                      /* keepAliveTime= */ MAX_LATENCY_BEFORE_UI_INIT + 10,
                      TimeUnit.SECONDS,
                      new LinkedBlockingQueue<>()));
        }
      }
    }
    return instance;
  }

  @SuppressWarnings("FirebaseUseExplicitDependencies")
  AppStartTrace(
      @NonNull TransportManager transportManager,
      @NonNull Clock clock,
      @NonNull ConfigResolver configResolver,
      @NonNull ExecutorService executorService) {
    this.transportManager = transportManager;
    this.clock = clock;
    this.configResolver = configResolver;
    this.executorService = executorService;

    StartupTime startupTime = FirebaseApp.getInstance().get(StartupTime.class);
    if (startupTime == null) {
      firebaseStartupTime = new Timer();
    } else {
      firebaseStartupTime =
          Timer.ofElapsedRealtime(startupTime.getElapsedRealtime(), startupTime.getUptimeMillis());
    }
    this.experimentTtid = TraceMetric.newBuilder().setName("_experiment_app_start_ttid");
  }

  /** Called from FirebasePerfEarly to register this callback. */
  public synchronized void registerActivityLifecycleCallbacks(@NonNull Context context) {
    // Make sure the callback is registered only once.
    if (isRegisteredForLifecycleCallbacks) {
      return;
    }
    Context appContext = context.getApplicationContext();
    if (appContext instanceof Application) {
      ((Application) appContext).registerActivityLifecycleCallbacks(this);
      isRegisteredForLifecycleCallbacks = true;
      this.appContext = appContext;
    }
  }

  /** Unregister this callback after AppStart trace is logged. */
  public synchronized void unregisterActivityLifecycleCallbacks() {
    if (!isRegisteredForLifecycleCallbacks) {
      return;
    }
    ((Application) appContext).unregisterActivityLifecycleCallbacks(this);
    isRegisteredForLifecycleCallbacks = false;
  }

  /**
   * Gets the timetamp that marks the beginning of app start, currently defined as the beginning of
   * BIND_APPLICATION. Fallback to class-load time of {@link StartupTime} when API < 24.
   *
   * @return {@link Timer} at the beginning of app start by Fireperf definition.
   */
  private static Timer getStartTimer() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return Timer.ofElapsedRealtime(
          Process.getStartElapsedRealtime(), Process.getStartUptimeMillis());
    }
    return firebaseStartupTime;
  }

  private void recordFirstDrawDone() {
    if (firstDrawDone != null) {
      return;
    }
    Timer start = getStartTimer();
    this.firstDrawDone = clock.getTime();
    this.experimentTtid
        .setClientStartTimeUs(start.getMicros())
        .setDurationUs(start.getDurationMicros(this.firstDrawDone));

    TraceMetric.Builder subtrace =
        TraceMetric.newBuilder()
            .setName("_experiment_classLoadTime")
            .setClientStartTimeUs(firebaseStartupTime.getMicros())
            .setDurationUs(firebaseStartupTime.getDurationMicros(this.firstDrawDone));
    this.experimentTtid.addSubtraces(subtrace.build());

    subtrace = TraceMetric.newBuilder();
    subtrace
        .setName("_experiment_uptimeMillis")
        .setClientStartTimeUs(start.getMicros())
        .setDurationUs(start.getDurationUptimeMicros(this.firstDrawDone));
    this.experimentTtid.addSubtraces(subtrace.build());

    this.experimentTtid.addPerfSessions(this.startSession.build());

    if (isExperimentTraceDone()) {
      executorService.execute(() -> this.logExperimentTtid(this.experimentTtid));

      if (isRegisteredForLifecycleCallbacks) {
        // After AppStart trace is queued to be logged, we can unregister this callback.
        unregisterActivityLifecycleCallbacks();
      }
    }
  }

  private void recordFirstDrawDonePreDraw() {
    if (preDraw != null) {
      return;
    }
    Timer start = getStartTimer();
    this.preDraw = clock.getTime();
    TraceMetric.Builder subtrace =
        TraceMetric.newBuilder()
            .setName("_experiment_preDraw")
            .setClientStartTimeUs(start.getMicros())
            .setDurationUs(start.getDurationMicros(this.preDraw));
    this.experimentTtid.addSubtraces(subtrace.build());

    subtrace = TraceMetric.newBuilder();
    subtrace
        .setName("_experiment_preDraw_uptimeMillis")
        .setClientStartTimeUs(start.getMicros())
        .setDurationUs(start.getDurationUptimeMicros(this.preDraw));
    this.experimentTtid.addSubtraces(subtrace.build());

    if (isExperimentTraceDone()) {
      executorService.execute(() -> this.logExperimentTtid(this.experimentTtid));

      if (isRegisteredForLifecycleCallbacks) {
        // After AppStart trace is queued to be logged, we can unregister this callback.
        unregisterActivityLifecycleCallbacks();
      }
    }
  }

  private boolean isExperimentTraceDone() {
    return this.preDraw != null && this.firstDrawDone != null;
  }

  @Override
  public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    if (isStartedFromBackground || onCreateTime != null // An activity already called onCreate()
    ) {
      return;
    }

    launchActivity = new WeakReference<Activity>(activity);
    onCreateTime = clock.getTime();

    if (firebaseStartupTime.getDurationMicros(onCreateTime) > MAX_LATENCY_BEFORE_UI_INIT) {
      isTooLateToInitUI = true;
    }
  }

  @Override
  public synchronized void onActivityStarted(Activity activity) {
    if (isStartedFromBackground
        || onStartTime != null // An activity already called onStart()
        || isTooLateToInitUI) {
      return;
    }
    onStartTime = clock.getTime();
  }

  @Override
  public synchronized void onActivityResumed(Activity activity) {
    if (isStartedFromBackground || isTooLateToInitUI) {
      return;
    }

    // Shadow-launch experiment of new app start time
    final boolean isExperimentTTIDEnabled = configResolver.getIsExperimentTTIDEnabled();
    if (isExperimentTTIDEnabled) {
      View rootView = activity.findViewById(android.R.id.content);
      FirstDrawDoneListener.registerForNextDraw(rootView, this::recordFirstDrawDone);
      PreDrawListener.registerForNextDraw(rootView, this::recordFirstDrawDonePreDraw);
    }

    if (onResumeTime != null) { // An activity already called onResume()
      return;
    }

    appStartActivity = new WeakReference<Activity>(activity);

    onResumeTime = clock.getTime();
    this.appStartTime = firebaseStartupTime;
    this.startSession = SessionManager.getInstance().perfSession();
    AndroidLogger.getInstance()
        .debug(
            "onResume(): "
                + activity.getClass().getName()
                + ": "
                + this.appStartTime.getDurationMicros(onResumeTime)
                + " microseconds");

    // Log the app start trace in a non-main thread.
    executorService.execute(this::logAppStartTrace);

    if (!isExperimentTTIDEnabled && isRegisteredForLifecycleCallbacks) {
      // After AppStart trace is logged, we can unregister this callback.
      unregisterActivityLifecycleCallbacks();
    }
  }

  private void logExperimentTtid(TraceMetric.Builder metric) {
    transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);
  }

  private void logAppStartTrace() {
    TraceMetric.Builder metric =
        TraceMetric.newBuilder()
            .setName(Constants.TraceNames.APP_START_TRACE_NAME.toString())
            .setClientStartTimeUs(getappStartTime().getMicros())
            .setDurationUs(getappStartTime().getDurationMicros(onResumeTime));
    List<TraceMetric> subtraces = new ArrayList<>(/* initialCapacity= */ 3);

    TraceMetric.Builder traceMetricBuilder =
        TraceMetric.newBuilder()
            .setName(Constants.TraceNames.ON_CREATE_TRACE_NAME.toString())
            .setClientStartTimeUs(getappStartTime().getMicros())
            .setDurationUs(getappStartTime().getDurationMicros(onCreateTime));
    subtraces.add(traceMetricBuilder.build());

    traceMetricBuilder = TraceMetric.newBuilder();
    traceMetricBuilder
        .setName(Constants.TraceNames.ON_START_TRACE_NAME.toString())
        .setClientStartTimeUs(onCreateTime.getMicros())
        .setDurationUs(onCreateTime.getDurationMicros(onStartTime));
    subtraces.add(traceMetricBuilder.build());

    traceMetricBuilder = TraceMetric.newBuilder();
    traceMetricBuilder
        .setName(Constants.TraceNames.ON_RESUME_TRACE_NAME.toString())
        .setClientStartTimeUs(onStartTime.getMicros())
        .setDurationUs(onStartTime.getDurationMicros(onResumeTime));
    subtraces.add(traceMetricBuilder.build());

    metric.addAllSubtraces(subtraces).addPerfSessions(this.startSession.build());

    transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);
  }

  @Override
  public void onActivityPaused(Activity activity) {
    if (isExperimentTraceDone()) {
      return;
    }
    Timer onPauseTime = clock.getTime();
    TraceMetric.Builder subtrace =
        TraceMetric.newBuilder()
            .setName("_experiment_onPause")
            .setClientStartTimeUs(onPauseTime.getMicros())
            .setDurationUs(getStartTimer().getDurationMicros(onPauseTime));
    this.experimentTtid.addSubtraces(subtrace.build());
  }

  @Override
  public void onActivityStopped(Activity activity) {
    if (isExperimentTraceDone()) {
      return;
    }
    Timer onStopTime = clock.getTime();
    TraceMetric.Builder subtrace =
        TraceMetric.newBuilder()
            .setName("_experiment_onStop")
            .setClientStartTimeUs(onStopTime.getMicros())
            .setDurationUs(getStartTimer().getDurationMicros(onStopTime));
    this.experimentTtid.addSubtraces(subtrace.build());
  }

  @Override
  public void onActivityDestroyed(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  /**
   * We use StartFromBackgroundRunnable to detect if app is started from background or foreground.
   * If app is started from background, we do not generate AppStart trace. This runnable is posted
   * to main UI thread from FirebasePerfEarly. If app is started from background, this runnable will
   * be executed before any activity's onCreate() method. If app is started from foreground,
   * activity's onCreate() method is executed before this runnable.
   */
  public static class StartFromBackgroundRunnable implements Runnable {
    private final AppStartTrace trace;

    public StartFromBackgroundRunnable(final AppStartTrace trace) {
      this.trace = trace;
    }

    @Override
    public void run() {
      // if no activity has ever been created.
      if (trace.onCreateTime == null) {
        trace.isStartedFromBackground = true;
      }
    }
  }

  @VisibleForTesting
  @Nullable
  Activity getLaunchActivity() {
    return launchActivity.get();
  }

  @VisibleForTesting
  @Nullable
  Activity getAppStartActivity() {
    return appStartActivity.get();
  }

  @VisibleForTesting
  Timer getappStartTime() {
    return appStartTime;
  }

  @VisibleForTesting
  Timer getOnCreateTime() {
    return onCreateTime;
  }

  @VisibleForTesting
  Timer getOnStartTime() {
    return onStartTime;
  }

  @VisibleForTesting
  Timer getOnResumeTime() {
    return onResumeTime;
  }

  @VisibleForTesting
  void setIsStartFromBackground() {
    isStartedFromBackground = true;
  }
}
