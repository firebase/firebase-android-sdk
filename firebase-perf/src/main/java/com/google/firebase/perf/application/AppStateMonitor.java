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

public class AppStateMonitor implements ActivityLifecycleCallbacks {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private static volatile AppStateMonitor instance;

  private final WeakHashMap<Activity, Boolean> activityToResumedMap = new WeakHashMap<>();
  private final WeakHashMap<Activity, FrameMetricsRecorder> activityToRecorderMap =
      new WeakHashMap<>();

  private final WeakHashMap<Activity, FragmentStateMonitor> activityToFragmentStateMonitorMap =
      new WeakHashMap<>();
  private final WeakHashMap<Activity, Trace> activityToScreenTraceMap = new WeakHashMap<>();
  private final Map<String, Long> metricToCountMap = new HashMap<>();
  private final Set<WeakReference<AppStateCallback>> appStateSubscribers = new HashSet<>();
  private Set<AppColdStartCallback> appColdStartSubscribers = new HashSet<>();

  private final AtomicInteger tsnsCount = new AtomicInteger(0);

  private final TransportManager transportManager;
  private final ConfigResolver configResolver;
  private final Clock clock;
  private final boolean screenPerformanceRecordingSupported;

  private Timer resumeTime;
  private Timer stopTime;

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
    activityToRecorderMap.remove(activity);
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
        startFrameMonitoring(activity);
      }
      activityToRecorderMap.get(activity).start();
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

    if (activityToResumedMap.containsKey(activity)) {
      activityToResumedMap.remove(activity);
      if (activityToResumedMap.isEmpty()) {
        stopTime = clock.getTime();
        sendSessionLog(Constants.TraceNames.FOREGROUND_TRACE_NAME.toString(), resumeTime, stopTime);
        updateAppState(ApplicationProcessState.BACKGROUND);
      }
    }
  }

  @Override
  public synchronized void onActivityResumed(Activity activity) {
    if (activityToResumedMap.isEmpty()) {
      resumeTime = clock.getTime();
      activityToResumedMap.put(activity, true);
      if (isColdStart) {
        updateAppState(ApplicationProcessState.FOREGROUND);
        sendAppColdStartUpdate();
        isColdStart = false;
      } else {
        sendSessionLog(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString(), stopTime, resumeTime);
        updateAppState(ApplicationProcessState.FOREGROUND);
      }
    } else {
      activityToResumedMap.put(activity, true);
    }
  }

  public boolean isColdStart() {
    return isColdStart;
  }

  public ApplicationProcessState getAppState() {
    return currentAppState;
  }

  public void registerForAppState(WeakReference<AppStateCallback> subscriber) {
    synchronized (appStateSubscribers) {
      appStateSubscribers.add(subscriber);
    }
  }

  public void unregisterForAppState(WeakReference<AppStateCallback> subscriber) {
    synchronized (appStateSubscribers) {
      appStateSubscribers.remove(subscriber);
    }
  }

  public void registerForAppColdStart(AppColdStartCallback subscriber) {
    synchronized (appColdStartSubscribers) {
      appColdStartSubscribers.add(subscriber);
    }
  }

  private void updateAppState(ApplicationProcessState newState) {
    currentAppState = newState;
    synchronized (appStateSubscribers) {
      for (Iterator<WeakReference<AppStateCallback>> i = appStateSubscribers.iterator();
          i.hasNext(); ) {
        AppStateCallback callback = i.next().get();
        if (callback != null) {
          callback.onUpdateAppState(currentAppState);
        } else {
          i.remove();
        }
      }
    }
  }

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

  public boolean isForeground() {
    return currentAppState == ApplicationProcessState.FOREGROUND;
  }

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  @Override
  public void onActivityPaused(Activity activity) {}

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
    screenTrace.stop();
  }

  private void sendSessionLog(String name, Timer startTime, Timer endTime) {
    if (!configResolver.isPerformanceMonitoringEnabled()) {
      return;
    }
    TraceMetric.Builder metric =
        TraceMetric.newBuilder()
            .setName(name)
            .setClientStartTimeUs(startTime.getMicros())
            .setDurationUs(startTime.getDurationMicros(endTime))
            .addPerfSessions(SessionManager.getInstance().perfSession().build());
    int tsnsCount = this.tsnsCount.getAndSet(0);
    synchronized (metricToCountMap) {
      metric.putAllCounters(metricToCountMap);
      if (tsnsCount != 0) {
        metric.putCounters(CounterNames.TRACE_STARTED_NOT_STOPPED.toString(), tsnsCount);
      }
      metricToCountMap.clear();
    }
    transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);
  }

  protected boolean isScreenTraceSupported() {
    return screenPerformanceRecordingSupported;
  }

  private static boolean isScreenPerformanceRecordingSupported() {
    return FrameMetricsRecorder.isFrameMetricsRecordingSupported();
  }

  public static interface AppStateCallback {
    public void onUpdateAppState(ApplicationProcessState newState);
  }

  public static interface AppColdStartCallback {
    public void onAppColdStart();
  }

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
