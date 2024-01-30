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

package com.google.firebase.perf.transport;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.datatransport.TransportFactory;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.perf.BuildConfig;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.application.AppStateMonitor.AppStateCallback;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.logging.ConsoleUrlGenerator;
import com.google.firebase.perf.metrics.validator.PerfMetricValidator;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Constants.CounterNames;
import com.google.firebase.perf.util.Rate;
import com.google.firebase.perf.v1.AndroidApplicationInfo;
import com.google.firebase.perf.v1.ApplicationInfo;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.GaugeMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.PerfMetric;
import com.google.firebase.perf.v1.PerfMetricOrBuilder;
import com.google.firebase.perf.v1.TraceMetric;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the transport of events (performance logs) to the Firebase Performance backend. This
 * class exposes below APIs for logging.
 *
 * <pre>
 *   1. TransportManager.getInstance().log(TraceMetric)
 *   2. TransportManager.getInstance().log(TraceMetric, ApplicationProcessState)
 *
 *   3. TransportManager.getInstance().log(NetworkRequestMetric)
 *   4. TransportManager.getInstance().log(NetworkRequestMetric, ApplicationProcessState)
 *
 *   5. TransportManager.getInstance().log(GaugeMetric)
 *   6. TransportManager.getInstance().log(GaugeMetric, ApplicationProcessState)
 * </pre>
 *
 * <p>TODO(b/172008005): Implement a Callback functionality for the caller/subscriber to know
 * whether the log was actually dispatched or not.
 */
public class TransportManager implements AppStateCallback {

  private static final AndroidLogger logger = AndroidLogger.getInstance();
  // TODO(b/171986777): Consider migrating to DependencyInjection framework to avoid extensive use
  //  of Singletons.
  private static final TransportManager instance = new TransportManager();

  // Core pool size 0 allows threads to shut down if they're idle
  private static final int CORE_POOL_SIZE = 0;
  private static final int MAX_POOL_SIZE = 1; // Only need single thread

  // Allows for in-memory caching of events while the TransportManager is not initialized
  private static final String KEY_AVAILABLE_TRACES_FOR_CACHING = "KEY_AVAILABLE_TRACES_FOR_CACHING";
  private static final String KEY_AVAILABLE_NETWORK_REQUESTS_FOR_CACHING =
      "KEY_AVAILABLE_NETWORK_REQUESTS_FOR_CACHING";
  private static final String KEY_AVAILABLE_GAUGES_FOR_CACHING = "KEY_AVAILABLE_GAUGES_FOR_CACHING";
  // The cache size limit (50) for each PerfMetric type is tentative and is subject to change
  private static final int MAX_TRACE_METRICS_CACHE_SIZE = 50;
  private static final int MAX_NETWORK_REQUEST_METRICS_CACHE_SIZE = 50;
  private static final int MAX_GAUGE_METRICS_CACHE_SIZE = 50;
  private final Map<String, Integer> cacheMap;
  private final ConcurrentLinkedQueue<PendingPerfEvent> pendingEventsQueue =
      new ConcurrentLinkedQueue<>();

  private final AtomicBoolean isTransportInitialized = new AtomicBoolean(false);

  private FirebaseApp firebaseApp;
  @Nullable private FirebasePerformance firebasePerformance;
  private FirebaseInstallationsApi firebaseInstallationsApi;
  private Provider<TransportFactory> flgTransportFactoryProvider;
  private FlgTransport flgTransport;
  private ExecutorService executorService;
  private Context appContext;
  private ConfigResolver configResolver;
  private RateLimiter rateLimiter;
  private AppStateMonitor appStateMonitor;
  private ApplicationInfo.Builder applicationInfoBuilder;
  private String packageName;
  private String projectId;

  private boolean isForegroundState = false;

  // TODO(b/258263016): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private TransportManager() {
    // MAX_POOL_SIZE must always be 1. We only allow one thread in this Executor. The reason
    // we specifically use a ThreadPoolExecutor rather than generating one from ExecutorService
    // because ThreadPoolExecutor provides the keepAliveTime timeout mechanism. If any threads
    // in excess of CORE_POOL_SIZE is idle for more than keepAliveTime, thread is shut down.
    this.executorService =
        new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            /* keepAliveTime= */ 10,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());

    cacheMap = new ConcurrentHashMap<>();
    cacheMap.put(KEY_AVAILABLE_TRACES_FOR_CACHING, MAX_TRACE_METRICS_CACHE_SIZE);
    cacheMap.put(
        KEY_AVAILABLE_NETWORK_REQUESTS_FOR_CACHING, MAX_NETWORK_REQUEST_METRICS_CACHE_SIZE);
    cacheMap.put(KEY_AVAILABLE_GAUGES_FOR_CACHING, MAX_GAUGE_METRICS_CACHE_SIZE);
  }

  public static TransportManager getInstance() {
    return instance;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  void initializeForTest(
      FirebaseApp firebaseApp,
      FirebasePerformance firebasePerformance,
      FirebaseInstallationsApi firebaseInstallationsApi,
      Provider<TransportFactory> flgTransportFactoryProvider,
      ConfigResolver configResolver,
      RateLimiter rateLimiter,
      AppStateMonitor appStateMonitor,
      FlgTransport flgTransport,
      ExecutorService executorService) {

    this.firebaseApp = firebaseApp;
    this.projectId = firebaseApp.getOptions().getProjectId();
    this.appContext = firebaseApp.getApplicationContext();
    this.firebasePerformance = firebasePerformance;
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    this.flgTransportFactoryProvider = flgTransportFactoryProvider;
    this.configResolver = configResolver;
    this.rateLimiter = rateLimiter;
    this.appStateMonitor = appStateMonitor;
    this.flgTransport = flgTransport;
    this.executorService = executorService;

    // Re-init the cache, otherwise the cache might get consumed/exhausted after a few tests
    cacheMap.put(KEY_AVAILABLE_TRACES_FOR_CACHING, MAX_TRACE_METRICS_CACHE_SIZE);
    cacheMap.put(
        KEY_AVAILABLE_NETWORK_REQUESTS_FOR_CACHING, MAX_NETWORK_REQUEST_METRICS_CACHE_SIZE);
    cacheMap.put(KEY_AVAILABLE_GAUGES_FOR_CACHING, MAX_GAUGE_METRICS_CACHE_SIZE);

    finishInitialization();
  }

  /**
   * Initializes the TransportManager so that it becomes ready for logging events. Should be called
   * during FirebasePerformance instance creation as it's guaranteed to have all input params
   * available by that time.
   *
   * @implNote The actual initialization will happen on a separate thread so the caller won't be
   *     blocked.
   * @see #isInitialized
   */
  public void initialize(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi,
      @NonNull Provider<TransportFactory> flgTransportFactoryProvider) {

    this.firebaseApp = firebaseApp;
    projectId = firebaseApp.getOptions().getProjectId();
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    this.flgTransportFactoryProvider = flgTransportFactoryProvider;

    // Run initialization in background thread
    this.executorService.execute(this::syncInit);
  }

  /** To avoid blocking user thread, initialization should be run from executorService thread. */
  @WorkerThread
  private void syncInit() {
    appContext = firebaseApp.getApplicationContext();
    packageName = appContext.getPackageName();
    configResolver = ConfigResolver.getInstance();
    rateLimiter =
        new RateLimiter(
            appContext, new Rate(Constants.RATE_PER_MINUTE, 1, MINUTES), Constants.BURST_CAPACITY);
    appStateMonitor = AppStateMonitor.getInstance();
    flgTransport =
        new FlgTransport(flgTransportFactoryProvider, configResolver.getAndCacheLogSourceName());

    finishInitialization();
  }

  private void finishInitialization() {
    appStateMonitor.registerForAppState(new WeakReference<>(instance));

    applicationInfoBuilder = ApplicationInfo.newBuilder();
    applicationInfoBuilder
        .setGoogleAppId(firebaseApp.getOptions().getApplicationId())
        .setAndroidAppInfo(
            AndroidApplicationInfo.newBuilder()
                .setPackageName(packageName)
                .setSdkVersion(BuildConfig.FIREPERF_VERSION_NAME)
                .setVersionName(getVersionName(appContext)));

    // Initialize before dispatching pending events
    isTransportInitialized.set(true);

    // Log any pending events which were queued and waiting for the Transport to initialize
    while (!pendingEventsQueue.isEmpty()) {
      PendingPerfEvent pendingPerfEvent = pendingEventsQueue.poll();
      if (pendingPerfEvent != null) {
        executorService.execute(
            () -> syncLog(pendingPerfEvent.perfMetricBuilder, pendingPerfEvent.appState));
      }
    }
  }

  @Override
  public void onUpdateAppState(ApplicationProcessState newAppState) {
    // TODO(b/172009242): Even though TransportManager is self aware of the Application State
    //  changes some callers needs to call log() with different ApplicationProcessState
    //  (like FOREGROUND_BACKGROUND) then the one provide by this callback.

    this.isForegroundState = newAppState == ApplicationProcessState.FOREGROUND;

    if (isInitialized()) {
      // Configures a new rate for the Token Bucket rate limiter.
      // TODO(b/172008563): RateLimiter should be self aware of the Application State changes.
      executorService.execute(() -> rateLimiter.changeRate(isForegroundState));
    }
  }

  // region Transport Public APIs

  /**
   * Returns whether the class has been initialized by specifically calling {@link
   * #initialize(FirebaseApp, FirebaseInstallationsApi, Provider)}.
   */
  public boolean isInitialized() {
    return isTransportInitialized.get();
  }

  /**
   * Logs the {@code traceMetric} event to be dispatched to the Firebase Performance backend. Event
   * will be queued (for to be dispatched later) if the Transport is not initialized yet (see {@link
   * #isTransportInitialized} and {@link #initialize(FirebaseApp, FirebaseInstallationsApi,
   * Provider)}).
   *
   * <p>The dispatch of the log depends on the validity of the {@code traceMetric} itself (see
   * {@link #isAllowedToDispatch(PerfMetric)}).
   */
  public void log(final TraceMetric traceMetric) {
    log(traceMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
  }

  /**
   * Logs the {@code traceMetric} event for the {@code appState} to be dispatched to the Firebase
   * Performance backend. Event will be queued (for to be dispatched later) if the Transport is not
   * initialized yet (see {@link #isTransportInitialized} and {@link #initialize(FirebaseApp,
   * FirebaseInstallationsApi, Provider)}).
   *
   * <p>The dispatch of the log depends on the validity of the {@code traceMetric} itself (see
   * {@link #isAllowedToDispatch(PerfMetric)}).
   */
  public void log(final TraceMetric traceMetric, final ApplicationProcessState appState) {
    executorService.execute(
        () -> syncLog(PerfMetric.newBuilder().setTraceMetric(traceMetric), appState));
  }

  /**
   * Logs the {@code networkRequestMetric} event to be dispatched to the Firebase Performance
   * backend. Event will be queued (for to be dispatched later) if the Transport is not initialized
   * yet (see {@link #isTransportInitialized} and {@link #initialize(FirebaseApp,
   * FirebaseInstallationsApi, Provider)}).
   *
   * <p>The dispatch of the log depends on the validity of the {@code networkRequestMetric} itself
   * (see {@link #isAllowedToDispatch(PerfMetric)}).
   */
  public void log(final NetworkRequestMetric networkRequestMetric) {
    log(networkRequestMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
  }

  /**
   * Logs the {@code networkRequestMetric} event for the {@code appState} to be dispatched to the
   * Firebase Performance backend. Event will be queued (for to be dispatched later) if the
   * Transport is not initialized yet (see {@link #isTransportInitialized} and {@link
   * #initialize(FirebaseApp, FirebaseInstallationsApi, Provider)}).
   *
   * <p>The dispatch of the log depends on the validity of the {@code networkRequestMetric} itself
   * (see {@link #isAllowedToDispatch(PerfMetric)}).
   */
  public void log(
      final NetworkRequestMetric networkRequestMetric, final ApplicationProcessState appState) {
    executorService.execute(
        () ->
            syncLog(
                PerfMetric.newBuilder().setNetworkRequestMetric(networkRequestMetric), appState));
  }

  /**
   * Logs the {@code gaugeMetric} event to be dispatched to the Firebase Performance backend. Event
   * will be queued (for to be dispatched later) if the Transport is not initialized yet (see {@link
   * #isTransportInitialized} and {@link #initialize(FirebaseApp, FirebaseInstallationsApi,
   * Provider)}).
   *
   * <p>The dispatch of the log depends on the validity of the {@code gaugeMetric} itself (see
   * {@link #isAllowedToDispatch(PerfMetric)}).
   */
  public void log(final GaugeMetric gaugeMetric) {
    log(gaugeMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
  }

  /**
   * Logs the {@code gaugeMetric} event for the {@code appState} to be dispatched to the Firebase
   * Performance backend. Event will be queued (for to be dispatched later) if the Transport is not
   * initialized yet (see {@link #isTransportInitialized} and {@link #initialize(FirebaseApp,
   * FirebaseInstallationsApi, Provider)}).
   *
   * <p>The dispatch of the log depends on the validity of the {@code gaugeMetric} itself (see
   * {@link #isAllowedToDispatch(PerfMetric)}).
   */
  public void log(final GaugeMetric gaugeMetric, final ApplicationProcessState appState) {
    executorService.execute(
        () -> syncLog(PerfMetric.newBuilder().setGaugeMetric(gaugeMetric), appState));
  }

  // endregion

  // region Transport Private APIs

  @WorkerThread
  private void syncLog(PerfMetric.Builder perfMetricBuilder, ApplicationProcessState appState) {
    if (!isInitialized()) {
      if (isAllowedToCache(perfMetricBuilder)) {
        logger.debug(
            "Transport is not initialized yet, %s will be queued for to be dispatched later",
            getLogcatMsg(perfMetricBuilder));

        pendingEventsQueue.add(new PendingPerfEvent(perfMetricBuilder, appState));
      }

      return;
    }

    PerfMetric perfMetric = setApplicationInfoAndBuild(perfMetricBuilder, appState);

    if (isAllowedToDispatch(perfMetric)) {
      dispatchLog(perfMetric);

      // Check if the session is expired. If so, stop gauge collection.
      SessionManager.getInstance().stopGaugeCollectionIfSessionRunningTooLong();
    }
  }

  @WorkerThread
  private boolean isAllowedToCache(PerfMetricOrBuilder perfMetricOrBuilder) {
    final int availableTracesForCaching = cacheMap.get(KEY_AVAILABLE_TRACES_FOR_CACHING);
    final int availableNetworkRequestsForCaching =
        cacheMap.get(KEY_AVAILABLE_NETWORK_REQUESTS_FOR_CACHING);
    final int availableGaugesForCaching = cacheMap.get(KEY_AVAILABLE_GAUGES_FOR_CACHING);

    if (perfMetricOrBuilder.hasTraceMetric() && availableTracesForCaching > 0) {
      cacheMap.put(KEY_AVAILABLE_TRACES_FOR_CACHING, availableTracesForCaching - 1);
      return true;

    } else if (perfMetricOrBuilder.hasNetworkRequestMetric()
        && availableNetworkRequestsForCaching > 0) {
      cacheMap.put(
          KEY_AVAILABLE_NETWORK_REQUESTS_FOR_CACHING, availableNetworkRequestsForCaching - 1);
      return true;

    } else if (perfMetricOrBuilder.hasGaugeMetric() && availableGaugesForCaching > 0) {
      cacheMap.put(KEY_AVAILABLE_GAUGES_FOR_CACHING, availableGaugesForCaching - 1);
      return true;
    }

    logger.debug(
        "%s is not allowed to cache. Cache exhausted the limit (availableTracesForCaching: %d,"
            + " availableNetworkRequestsForCaching: %d, availableGaugesForCaching: %d).",
        getLogcatMsg(perfMetricOrBuilder),
        availableTracesForCaching,
        availableNetworkRequestsForCaching,
        availableGaugesForCaching);

    return false;
  }

  /**
   * Checks if the {@code perfMetricBuilder} is allowed to be dispatched based on certain validation
   * criteria.
   */
  @WorkerThread
  private boolean isAllowedToDispatch(PerfMetric perfMetric) {
    if (!configResolver.isPerformanceMonitoringEnabled()) {
      logger.info("Performance collection is not enabled, dropping %s", getLogcatMsg(perfMetric));
      return false;
    }

    if (!perfMetric.getApplicationInfo().hasAppInstanceId()) {
      logger.warn("App Instance ID is null or empty, dropping %s", getLogcatMsg(perfMetric));
      return false;
    }

    if (!PerfMetricValidator.isValid(perfMetric, appContext)) {
      logger.warn(
          "Unable to process the PerfMetric (%s) due to missing or invalid values."
              + " See earlier log statements for additional information on the specific"
              + " missing/invalid values.",
          getLogcatMsg(perfMetric));
      return false;
    }

    if (!rateLimiter.isEventSampled(perfMetric)) {
      incrementDropCount(perfMetric);
      logger.info("Event dropped due to device sampling - %s", getLogcatMsg(perfMetric));
      return false;
    }

    if (rateLimiter.isEventRateLimited(perfMetric)) {
      incrementDropCount(perfMetric);
      logger.info("Rate limited (per device) - %s", getLogcatMsg(perfMetric));
      return false;
    }

    return true;
  }

  @WorkerThread
  private void dispatchLog(PerfMetric perfMetric) {

    // Logs the metrics to logcat plus console URL for every trace metric.
    if (perfMetric.hasTraceMetric()) {
      logger.info(
          "Logging %s. In a minute, visit the Firebase console to view your data: %s",
          getLogcatMsg(perfMetric), getConsoleUrl(perfMetric.getTraceMetric()));
    } else {
      logger.info("Logging %s", getLogcatMsg(perfMetric));
    }

    flgTransport.log(perfMetric);
  }

  // endregion

  // region Utility Methods

  /**
   * Returns the versionName (see
   * https://developer.android.com/guide/topics/manifest/manifest-element#vname) of the android
   * application.
   */
  private static String getVersionName(final Context appContext) {
    try {
      PackageInfo pi =
          appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
      return pi.versionName == null ? "" : pi.versionName;
    } catch (NameNotFoundException e) {
      return "";
    }
  }

  /**
   * Sets the {@link ApplicationInfo} to the {@code perfMetricBuilder} and generates the {@link
   * PerfMetric}.
   */
  private PerfMetric setApplicationInfoAndBuild(
      PerfMetric.Builder perfMetricBuilder, ApplicationProcessState appState) {
    updateFirebaseInstallationIdIfPossibleAndNeeded();

    ApplicationInfo.Builder appInfoBuilder =
        applicationInfoBuilder.setApplicationProcessState(appState);

    if (perfMetricBuilder.hasTraceMetric() || perfMetricBuilder.hasNetworkRequestMetric()) {
      appInfoBuilder =
          appInfoBuilder
              .clone() // Needed so that we don't add global custom attributes everywhere
              .putAllCustomAttributes(getGlobalCustomAttributes());
    }

    return perfMetricBuilder.setApplicationInfo(appInfoBuilder).build();
  }

  private Map<String, String> getGlobalCustomAttributes() {
    updateFirebasePerformanceIfPossibleAndNeeded();

    return firebasePerformance != null
        ? firebasePerformance.getAttributes()
        : Collections.emptyMap();
  }

  private void updateFirebasePerformanceIfPossibleAndNeeded() {
    if (firebasePerformance == null) {
      // Should only get FirebasePerformance instance once the Transport is initialized otherwise
      // it will create stack overflow with cyclic initialization.
      if (isInitialized()) {
        firebasePerformance = FirebasePerformance.getInstance();
      }
    }
  }

  /**
   * Populates the {@code AppInstanceId} field inside of {@link #applicationInfoBuilder} using
   * {@code installationId}. Firebase Installation Id is required for the performance event to be
   * accepted by Performance Monitoring backend.
   *
   * <p>This method handles the following situations:
   *
   * <ul>
   *   <li>Firebase Performance is not enabled, do not interact with {@link
   *       FirebaseInstallationsApi} for GDPR reason.
   *   <li>There's a possibility that {@link #firebaseInstallationsApi} returns a null or empty
   *       value, in which case we don't want to set it in our proto.
   * </ul>
   */
  @WorkerThread
  private void updateFirebaseInstallationIdIfPossibleAndNeeded() {
    if (configResolver.isPerformanceMonitoringEnabled()) {
      // Due to a potential cause of app crash during FIS interaction when app is on the background,
      // Fireperf keeps a cache of Firebase Installation Id for usage. Fireperf will request
      // Installation Id only when cache is empty or app is in the foreground.
      //
      // Bug reference: https://github.com/firebase/firebase-android-sdk/issues/1528
      //
      // Ideal solution is for FIS to support Pub/Sub model: b/160343157.
      if (applicationInfoBuilder.hasAppInstanceId() && !isForegroundState) {
        return;
      }

      String installationId = null;

      try {
        installationId = Tasks.await(firebaseInstallationsApi.getId(), 60000, MILLISECONDS);

      } catch (ExecutionException e) {
        logger.error("Unable to retrieve Installation Id: %s", e.getMessage());

      } catch (InterruptedException e) {
        logger.error("Task to retrieve Installation Id is interrupted: %s", e.getMessage());

      } catch (TimeoutException e) {
        logger.error("Task to retrieve Installation Id is timed out: %s", e.getMessage());
      }

      if (!TextUtils.isEmpty(installationId)) {
        applicationInfoBuilder.setAppInstanceId(installationId);

      } else {
        logger.warn("Firebase Installation Id is empty, contact Firebase Support for debugging.");
      }
    }
  }

  private void incrementDropCount(PerfMetric metric) {
    // TODO(b/172008005): We should instead have a callback from the TransportManager that should
    //  let the caller of the log (or anyone subscribed) know that whether the log was dispatched or
    //  not.
    if (metric.hasTraceMetric()) {
      appStateMonitor.incrementCount(Constants.CounterNames.TRACE_EVENT_RATE_LIMITED.toString(), 1);

    } else if (metric.hasNetworkRequestMetric()) {
      appStateMonitor.incrementCount(CounterNames.NETWORK_TRACE_EVENT_RATE_LIMITED.toString(), 1);
    }
  }

  // endregion

  // region Logcat/Console Logging Utility Methods

  private static String getLogcatMsg(PerfMetricOrBuilder perfMetric) {
    if (perfMetric.hasTraceMetric()) {
      return getLogcatMsg(perfMetric.getTraceMetric());
    }

    if (perfMetric.hasNetworkRequestMetric()) {
      return getLogcatMsg(perfMetric.getNetworkRequestMetric());
    }

    if (perfMetric.hasGaugeMetric()) {
      return getLogcatMsg(perfMetric.getGaugeMetric());
    }

    return "log";
  }

  private static String getLogcatMsg(TraceMetric traceMetric) {
    long durationInUs = traceMetric.getDurationUs();
    return String.format(
        Locale.ENGLISH,
        "trace metric: %s (duration: %sms)",
        traceMetric.getName(),
        new DecimalFormat("#.####").format(durationInUs / 1000.0));
  }

  private static String getLogcatMsg(NetworkRequestMetric networkRequestMetric) {
    long durationInUs =
        networkRequestMetric.hasTimeToResponseCompletedUs()
            ? networkRequestMetric.getTimeToResponseCompletedUs()
            : 0;

    String responseCode =
        networkRequestMetric.hasHttpResponseCode()
            ? String.valueOf(networkRequestMetric.getHttpResponseCode())
            : "UNKNOWN";

    return String.format(
        Locale.ENGLISH,
        "network request trace: %s (responseCode: %s, responseTime: %sms)",
        networkRequestMetric.getUrl(),
        responseCode,
        new DecimalFormat("#.####").format(durationInUs / 1000.0));
  }

  private static String getLogcatMsg(GaugeMetric gaugeMetric) {
    return String.format(
        Locale.ENGLISH,
        "gauges (hasMetadata: %b, cpuGaugeCount: %d, memoryGaugeCount: %d)",
        gaugeMetric.hasGaugeMetadata(),
        gaugeMetric.getCpuMetricReadingsCount(),
        gaugeMetric.getAndroidMemoryReadingsCount());
  }

  private String getConsoleUrl(TraceMetric traceMetric) {
    String traceName = traceMetric.getName();
    if (traceName.startsWith(Constants.SCREEN_TRACE_PREFIX)) {
      return ConsoleUrlGenerator.generateScreenTraceUrl(projectId, packageName, traceName);
    } else {
      return ConsoleUrlGenerator.generateCustomTraceUrl(projectId, packageName, traceName);
    }
  }

  // endregion

  // region Visible for Testing

  @VisibleForTesting
  protected void setInitialized(boolean initialized) {
    isTransportInitialized.set(initialized);
  }

  @VisibleForTesting
  protected void clearAppInstanceId() {
    applicationInfoBuilder.clearAppInstanceId();
  }

  @VisibleForTesting
  protected ConcurrentLinkedQueue<PendingPerfEvent> getPendingEventsQueue() {
    return new ConcurrentLinkedQueue<>(pendingEventsQueue);
  }

  // endregion
}
