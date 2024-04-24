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
import android.app.ActivityManager;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
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
public class AppStartTrace implements ActivityLifecycleCallbacks, LifecycleObserver {

  private static final @NonNull Timer PERF_CLASS_LOAD_TIME = new Clock().getTime();
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

  // Critical timestamps during app-start, on the main-thread. IMPORTANT: these must all be captured
  // or modified on the main thread. Without this invariant, we cannot guarantee that null means "it
  // hasn't happened".
  private final @Nullable Timer processStartTime;
  private final @Nullable Timer firebaseClassLoadTime;
  private Timer onCreateTime = null;
  private Timer onStartTime = null;
  private Timer onResumeTime = null;
  private Timer firstForegroundTime = null;
  private @Nullable Timer firstBackgroundTime = null;
  private Timer preDrawPostTime = null;
  private Timer preDrawPostAtFrontOfQueueTime = null;
  private Timer onDrawPostAtFrontOfQueueTime = null;

  private PerfSession startSession;
  private boolean isStartedFromBackground = false;

  // TODO: remove after experiment
  private int onDrawCount = 0;
  private final DrawCounter onDrawCounterListener = new DrawCounter();
  private boolean systemForegroundCheck = false;

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
    this.experimentTtid = TraceMetric.newBuilder().setName("_experiment_app_start_ttid");
    // Set the timestamp for process-start (beginning of BIND_APPLICATION), if available
    this.processStartTime =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            ? Timer.ofElapsedRealtime(Process.getStartElapsedRealtime())
            : null;
    // Set the timestamp for Firebase's first class's class-loading (approx.), if available
    StartupTime firebaseStart = FirebaseApp.getInstance().get(StartupTime.class);
    this.firebaseClassLoadTime =
        firebaseStart != null ? Timer.ofElapsedRealtime(firebaseStart.getElapsedRealtime()) : null;
  }

  /** Called from FirebasePerfEarly to register this callback. */
  public synchronized void registerActivityLifecycleCallbacks(@NonNull Context context) {
    // Make sure the callback is registered only once.
    if (isRegisteredForLifecycleCallbacks) {
      return;
    }
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    Context appContext = context.getApplicationContext();
    if (appContext instanceof Application) {
      ((Application) appContext).registerActivityLifecycleCallbacks(this);
      systemForegroundCheck = systemForegroundCheck || isAnyAppProcessInForeground(appContext);
      isRegisteredForLifecycleCallbacks = true;
      this.appContext = appContext;
    }
  }

  /** Unregister this callback after AppStart trace is logged. */
  public synchronized void unregisterActivityLifecycleCallbacks() {
    if (!isRegisteredForLifecycleCallbacks) {
      return;
    }
    ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
    ((Application) appContext).unregisterActivityLifecycleCallbacks(this);
    isRegisteredForLifecycleCallbacks = false;
  }

  /**
   * Gets the timestamp that marks the beginning of app start, defined as the beginning of
   * BIND_APPLICATION, when the forked process is about to start loading the app's resources and
   * classes. Fallback to class-load time of a Firebase class for compatibility below API 24.
   *
   * @return {@link Timer} at the beginning of app start by Firebase-Performance definition.
   */
  private @NonNull Timer getStartTimerCompat() {
    // Preferred: Android system API provides BIND_APPLICATION time
    if (processStartTime != null) {
      return processStartTime;
    }
    // Fallback: static initializer time (during class-load) of a Firebase class
    return getClassLoadTimeCompat();
  }

  /**
   * Timestamp during class load. This timestamp is captured in a static initializer during class
   * loading of a particular Firebase class, the order of which CANNOT be guaranteed to be prior to
   * all other classes of an app, nor prior to resource-loading of an app. Thus this timestamp is
   * NOT PREFERRED to be used as starting-point of app-start.
   *
   * @return {@link Timer} captured by static-initializer during class-loading of a Firebase class.
   */
  private @NonNull Timer getClassLoadTimeCompat() {
    // Prefered: static-initializer time of the 1st Firebase class during init
    if (firebaseClassLoadTime != null) {
      return firebaseClassLoadTime;
    }
    // Fallback: static-initializer time of the current class
    return PERF_CLASS_LOAD_TIME;
  }

  private void recordPreDraw() {
    if (preDrawPostTime != null) {
      return;
    }
    this.preDrawPostTime = clock.getTime();
    this.experimentTtid
        .setClientStartTimeUs(getStartTimerCompat().getMicros())
        .setDurationUs(getStartTimerCompat().getDurationMicros(this.preDrawPostTime));
    logExperimentTrace(this.experimentTtid);
  }

  private void recordPreDrawFrontOfQueue() {
    if (preDrawPostAtFrontOfQueueTime != null) {
      return;
    }
    this.preDrawPostAtFrontOfQueueTime = clock.getTime();
    this.experimentTtid.addSubtraces(
        TraceMetric.newBuilder()
            .setName("_experiment_preDrawFoQ")
            .setClientStartTimeUs(getStartTimerCompat().getMicros())
            .setDurationUs(
                getStartTimerCompat().getDurationMicros(this.preDrawPostAtFrontOfQueueTime))
            .build());
    logExperimentTrace(this.experimentTtid);
  }

  private void recordOnDrawFrontOfQueue() {
    if (onDrawPostAtFrontOfQueueTime != null) {
      return;
    }
    this.onDrawPostAtFrontOfQueueTime = clock.getTime();

    this.experimentTtid.addSubtraces(
        TraceMetric.newBuilder()
            .setName("_experiment_onDrawFoQ")
            .setClientStartTimeUs(getStartTimerCompat().getMicros())
            .setDurationUs(
                getStartTimerCompat().getDurationMicros(this.onDrawPostAtFrontOfQueueTime))
            .build());
    if (processStartTime != null) {
      this.experimentTtid.addSubtraces(
          TraceMetric.newBuilder()
              .setName("_experiment_procStart_to_classLoad")
              .setClientStartTimeUs(getStartTimerCompat().getMicros())
              .setDurationUs(getStartTimerCompat().getDurationMicros(getClassLoadTimeCompat()))
              .build());
    }
    this.experimentTtid.putCustomAttributes(
        "systemDeterminedForeground", systemForegroundCheck ? "true" : "false");
    this.experimentTtid.putCounters("onDrawCount", onDrawCount);
    this.experimentTtid.addPerfSessions(this.startSession.build());
    logExperimentTrace(this.experimentTtid);
  }

  @Override
  public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    if (isStartedFromBackground || onCreateTime != null // An activity already called onCreate()
    ) {
      return;
    }

    systemForegroundCheck = systemForegroundCheck || isAnyAppProcessInForeground(appContext);
    launchActivity = new WeakReference<Activity>(activity);
    onCreateTime = clock.getTime();

    if (getStartTimerCompat().getDurationMicros(onCreateTime) > MAX_LATENCY_BEFORE_UI_INIT) {
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
      rootView.getViewTreeObserver().addOnDrawListener(onDrawCounterListener);
      FirstDrawDoneListener.registerForNextDraw(rootView, this::recordOnDrawFrontOfQueue);
      PreDrawListener.registerForNextDraw(
          rootView, this::recordPreDraw, this::recordPreDrawFrontOfQueue);
    }

    if (onResumeTime != null) { // An activity already called onResume()
      return;
    }

    appStartActivity = new WeakReference<Activity>(activity);

    onResumeTime = clock.getTime();
    this.startSession = SessionManager.getInstance().perfSession();
    AndroidLogger.getInstance()
        .debug(
            "onResume(): "
                + activity.getClass().getName()
                + ": "
                + getClassLoadTimeCompat().getDurationMicros(onResumeTime)
                + " microseconds");

    // Log the app start trace in a non-main thread.
    executorService.execute(this::logAppStartTrace);

    if (!isExperimentTTIDEnabled) {
      // After AppStart trace is logged, we can unregister this callback.
      unregisterActivityLifecycleCallbacks();
    }
  }

  /** Helper for logging all experiments in one trace. */
  private void logExperimentTrace(TraceMetric.Builder metric) {
    if (this.preDrawPostTime == null
        || this.preDrawPostAtFrontOfQueueTime == null
        || this.onDrawPostAtFrontOfQueueTime == null) {
      return;
    }
    executorService.execute(
        () -> transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND));
    // After logging the experiment trace, we can unregister ourself from lifecycle listeners.
    unregisterActivityLifecycleCallbacks();
  }

  private void logAppStartTrace() {
    TraceMetric.Builder metric =
        TraceMetric.newBuilder()
            .setName(Constants.TraceNames.APP_START_TRACE_NAME.toString())
            .setClientStartTimeUs(getClassLoadTimeCompat().getMicros())
            .setDurationUs(getClassLoadTimeCompat().getDurationMicros(onResumeTime));
    List<TraceMetric> subtraces = new ArrayList<>(/* initialCapacity= */ 3);

    TraceMetric.Builder traceMetricBuilder =
        TraceMetric.newBuilder()
            .setName(Constants.TraceNames.ON_CREATE_TRACE_NAME.toString())
            .setClientStartTimeUs(getClassLoadTimeCompat().getMicros())
            .setDurationUs(getClassLoadTimeCompat().getDurationMicros(onCreateTime));
    subtraces.add(traceMetricBuilder.build());

    // OnStartTime is not captured in all situations, so checking for valid value before using it.
    if (onStartTime != null) {
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
    }

    metric.addAllSubtraces(subtraces).addPerfSessions(this.startSession.build());

    transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);
  }

  @Override
  public void onActivityPaused(Activity activity) {
    if (isStartedFromBackground
        || isTooLateToInitUI
        || !configResolver.getIsExperimentTTIDEnabled()) {
      return;
    }
    View rootView = activity.findViewById(android.R.id.content);
    rootView.getViewTreeObserver().removeOnDrawListener(onDrawCounterListener);
  }

  @Override
  public void onActivityStopped(Activity activity) {}

  /** App is entering foreground. Keep annotation is required so R8 does not remove this method. */
  @Keep
  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  public void onAppEnteredForeground() {
    if (isStartedFromBackground || isTooLateToInitUI || firstForegroundTime != null) {
      return;
    }
    // firstForeground is equivalent to the first Activity onStart. This marks the beginning of
    // observable backgrounding. Prior to this point, backgrounding cannot be observed.
    firstForegroundTime = clock.getTime();
    this.experimentTtid.addSubtraces(
        TraceMetric.newBuilder()
            .setName("_experiment_firstForegrounding")
            .setClientStartTimeUs(getStartTimerCompat().getMicros())
            .setDurationUs(getStartTimerCompat().getDurationMicros(firstForegroundTime))
            .build());
  }

  /** App is entering background. Keep annotation is required so R8 does not remove this method. */
  @Keep
  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  public void onAppEnteredBackground() {
    if (isStartedFromBackground || isTooLateToInitUI || firstBackgroundTime != null) {
      return;
    }
    firstBackgroundTime = clock.getTime();
    // TODO: remove this subtrace after the experiment
    this.experimentTtid.addSubtraces(
        TraceMetric.newBuilder()
            .setName("_experiment_firstBackgrounding")
            .setClientStartTimeUs(getStartTimerCompat().getMicros())
            .setDurationUs(getStartTimerCompat().getDurationMicros(firstBackgroundTime))
            .build());
  }

  @Override
  public void onActivityDestroyed(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  /**
   * Returns whether any process corresponding to the package for the provided context is visible
   * (in other words, whether the app is currently in the foreground).
   *
   * @param appContext The application's context.
   */
  public static boolean isAnyAppProcessInForeground(Context appContext) {
    // Do not call ProcessStats.getActivityManger, caching will break tests that indirectly depend
    // on ProcessStats.
    ActivityManager activityManager =
        (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
    if (activityManager == null) {
      return true;
    }
    List<ActivityManager.RunningAppProcessInfo> appProcesses =
        activityManager.getRunningAppProcesses();
    if (appProcesses != null) {
      String appProcessName = appContext.getPackageName();
      String allowedAppProcessNamePrefix = appProcessName + ":";
      for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
        if (appProcess.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
          continue;
        }
        if (appProcess.processName.equals(appProcessName)
            || appProcess.processName.startsWith(allowedAppProcessNamePrefix)) {
          boolean isAppInForeground = true;

          // For the case when the app is in foreground and the device transitions to sleep mode,
          // the importance of the process is set to IMPORTANCE_TOP_SLEEPING. However, this
          // importance level was introduced in M. Pre M, the process importance is not changed to
          // IMPORTANCE_TOP_SLEEPING when the display turns off. So we need to rely also on the
          // state of the display to decide if any app process is really visible.
          if (Build.VERSION.SDK_INT < 23 /* M */) {
            isAppInForeground = isScreenOn(appContext);
          }

          if (isAppInForeground) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /**
   * Returns whether the device screen is on.
   *
   * @param appContext The application's context.
   */
  public static boolean isScreenOn(Context appContext) {
    PowerManager powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
    if (powerManager == null) {
      return true;
    }
    return (Build.VERSION.SDK_INT >= 20 /* KITKAT_WATCH */)
        ? powerManager.isInteractive()
        : powerManager.isScreenOn();
  }

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

  private final class DrawCounter implements ViewTreeObserver.OnDrawListener {
    @Override
    public void onDraw() {
      onDrawCount++;
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
