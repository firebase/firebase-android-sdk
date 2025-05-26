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

package com.google.firebase.perf.session.gauges;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.components.Lazy;
import com.google.firebase.perf.application.AppStateUpdateHandler;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.AndroidMemoryReading;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.CpuMetricReading;
import com.google.firebase.perf.v1.GaugeMetadata;
import com.google.firebase.perf.v1.GaugeMetric;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for orchestrating different Gauges like CPU and Memory, collating that
 * information and logging it to the Transport.
 */
@Keep // Needed because of b/117526359.
public class GaugeManager extends AppStateUpdateHandler {

  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private static final GaugeManager instance = new GaugeManager();

  // This is a guesstimate of the max amount of time to wait before any pending metrics' collection
  // might take.
  private static final long TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS = 20;
  private static final long INVALID_GAUGE_COLLECTION_FREQUENCY = -1;

  private final Lazy<ScheduledExecutorService> gaugeManagerExecutor;
  private final ConfigResolver configResolver;
  private final Lazy<CpuGaugeCollector> cpuGaugeCollector;
  private final Lazy<MemoryGaugeCollector> memoryGaugeCollector;
  private final TransportManager transportManager;

  @Nullable private GaugeMetadataManager gaugeMetadataManager;
  @Nullable private ScheduledFuture<?> gaugeManagerDataCollectionJob = null;
  @Nullable private PerfSession session = null;
  private ApplicationProcessState applicationProcessState =
      ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN;

  // TODO(b/258263016): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private GaugeManager() {
    this(
        new Lazy<>(Executors::newSingleThreadScheduledExecutor),
        TransportManager.getInstance(),
        ConfigResolver.getInstance(),
        null,
        new Lazy<>(CpuGaugeCollector::new),
        new Lazy<>(MemoryGaugeCollector::new));
  }

  @VisibleForTesting
  GaugeManager(
      Lazy<ScheduledExecutorService> gaugeManagerExecutor,
      TransportManager transportManager,
      ConfigResolver configResolver,
      @Nullable GaugeMetadataManager gaugeMetadataManager,
      Lazy<CpuGaugeCollector> cpuGaugeCollector,
      Lazy<MemoryGaugeCollector> memoryGaugeCollector) {

    this.gaugeManagerExecutor = gaugeManagerExecutor;
    this.transportManager = transportManager;
    this.configResolver = configResolver;
    this.gaugeMetadataManager = gaugeMetadataManager;
    this.cpuGaugeCollector = cpuGaugeCollector;
    this.memoryGaugeCollector = memoryGaugeCollector;
    registerForAppState();
  }

  /** Initializes GaugeMetadataManager which requires application context. */
  public void initializeGaugeMetadataManager(Context appContext) {
    this.gaugeMetadataManager = new GaugeMetadataManager(appContext);
  }

  @Override
  public void onUpdateAppState(ApplicationProcessState applicationProcessState) {
    // If it isn't a verbose session (or unset) update the app state and return.
    if (session == null || !session.isVerbose()) {
      this.applicationProcessState = applicationProcessState;
      return;
    }

    // Log existing gauges to the current app state.
    logGaugeMetrics();

    // Update App State.
    this.applicationProcessState = applicationProcessState;

    // Start collecting gauges for the new app state.
    startCollectingGauges(this.applicationProcessState, session.getTimer());
  }

  /** Returns the singleton instance of this class. */
  public static synchronized GaugeManager getInstance() {
    return instance;
  }

  /**
   * Starts the collection of available gauges for the given {@link PerfSession}.
   * The collected Gauge Metrics will be flushed by {@link GaugeCounter}
   *
   * <p>GaugeManager can only collect gauges for one session at a time, and if this method is called
   * again with the same or new sessionId while it's already collecting gauges, all future gauges
   * will then be associated with the same or new sessionId.
   *
   * @param session The {@link PerfSession} to which the collected gauges will be associated with.
   * @note: This method is NOT thread safe - {@link this.startCollectingGauges()} and {@link
   * this.stopCollectingGauges()} should always be called from the same thread.
   */
  public void startCollectingGauges(PerfSession session) {
    if (this.session != null) {
      stopCollectingGauges();
    }

    // TODO(b/394127311): Explore always setting the app state as FOREGROUND.
    ApplicationProcessState gaugeCollectionApplicationProcessState = applicationProcessState;
    if (gaugeCollectionApplicationProcessState
        == ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN) {
      logger.warn("Start collecting gauges with APPLICATION_PROCESS_STATE_UNKNOWN");
      // Since the application process state is unknown, collect gauges at the foreground frequency.
      gaugeCollectionApplicationProcessState = ApplicationProcessState.FOREGROUND;
    }

    long collectionFrequency =
        startCollectingGauges(gaugeCollectionApplicationProcessState, session.getTimer());
    if (collectionFrequency == INVALID_GAUGE_COLLECTION_FREQUENCY) {
      logger.warn("Invalid gauge collection frequency. Unable to start collecting Gauges.");
      return;
    }

    this.session = session;
  }

  /**
   * Starts the collection of available Gauges for the given {@code appState}. If it's being
   * collected for a different app state, it stops that prior to starting it for the given
   * {@code appState}.
   *
   * @param appState The app state to which the collected gauges are associated.
   * @param referenceTime The time off which the system time is calculated when collecting gauges.
   *     See go/fireperf-sessions-timestamps.
   * @return The minimum collection frequency at which the Gauge Metrics will be collected or {@link
   *     #INVALID_GAUGE_COLLECTION_FREQUENCY} if Gauge Metrics collection didn't start.
   */
  private long startCollectingGauges(ApplicationProcessState appState, Timer referenceTime) {
    long collectionFrequency = INVALID_GAUGE_COLLECTION_FREQUENCY;

    final long cpuMetricCollectionFrequency = getCpuGaugeCollectionFrequencyMs(appState);
    if (startCollectingCpuMetrics(cpuMetricCollectionFrequency, referenceTime)) {
      collectionFrequency = cpuMetricCollectionFrequency;
    }

    final long memoryMetricCollectionFrequency = getMemoryGaugeCollectionFrequencyMs(appState);
    if (startCollectingMemoryMetrics(memoryMetricCollectionFrequency, referenceTime)) {
      collectionFrequency =
          (collectionFrequency == INVALID_GAUGE_COLLECTION_FREQUENCY)
              ? memoryMetricCollectionFrequency
              : Math.min(collectionFrequency, memoryMetricCollectionFrequency);
    }

    return collectionFrequency;
  }

  /**
   * Stops the collection of gauges if it was currently collecting. Does nothing otherwise.
   *
   * @note: This method is NOT thread safe - {@link this.startCollectingGauges()} and {@link
   *     this.stopCollectingGauges()} should always be called from the same thread.
   */
  public void stopCollectingGauges() {
    if (session == null) {
      return;
    }

    cpuGaugeCollector.get().stopCollecting();
    memoryGaugeCollector.get().stopCollecting();

    logGaugeMetrics();
    this.session = null;
  }

  /**
   * Logs the existing GaugeMetrics to Firelog, associates it with the current {@link PerfSession}
   * and {@link ApplicationProcessState}.
   *
   * @return true if a new data collection job is started, false otherwise.
   */
  protected boolean logGaugeMetrics() {
    if (session == null) {
      logger.debug("Attempted to log Gauge Metrics when session was null.");
      return false;
    }

    // If there's an existing gaugeManagerDataCollectionJob, this is a no-op.
    if (gaugeManagerDataCollectionJob != null && !gaugeManagerDataCollectionJob.isDone()) {
      logger.debug("Attempted to start an additional gauge logging operation.");
      return false;
    }

    logExistingGaugeMetrics(session.sessionId(), applicationProcessState);
    return true;
  }

  private void logExistingGaugeMetrics(
      String sessionId, ApplicationProcessState applicationProcessState) {
    // Flush any data that was collected and attach it to the given session and app state.
    gaugeManagerDataCollectionJob =
        gaugeManagerExecutor
            .get()
            .schedule(
                () -> {
                  syncFlush(sessionId, applicationProcessState);
                },
                TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS,
                TimeUnit.MILLISECONDS);
  }

  /**
   * This method reads any pending data points from all the Gauge's queues, assembles a GaugeMetric
   * proto and logs it to transport.
   *
   * @param sessionId The sessionId to which the collected GaugeMetrics should be associated with.
   * @param appState The app state for which these gauges are attributed to.
   */
  private void syncFlush(String sessionId, ApplicationProcessState appState) {
    GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();

    // Adding CPU metric readings.
    while (!cpuGaugeCollector.get().cpuMetricReadings.isEmpty()) {
      gaugeMetricBuilder.addCpuMetricReadings(cpuGaugeCollector.get().cpuMetricReadings.poll());
      GaugeCounter.decrementCounter();
    }

    // Adding Memory metric readings.
    while (!memoryGaugeCollector.get().memoryMetricReadings.isEmpty()) {
      gaugeMetricBuilder.addAndroidMemoryReadings(
          memoryGaugeCollector.get().memoryMetricReadings.poll());
      GaugeCounter.decrementCounter();
    }

    // Adding Session ID info.
    gaugeMetricBuilder.setSessionId(sessionId);

    transportManager.log(gaugeMetricBuilder.build(), appState);
  }

  /**
   * Log the Gauge Metadata information to the transport.
   *
   * @param sessionId The {@link PerfSession#sessionId()} ()} to which the collected Gauge Metrics
   *     should be associated with.
   * @param appState The {@link ApplicationProcessState} for which these gauges are collected.
   * @return true if GaugeMetadata was logged, false otherwise.
   */
  public boolean logGaugeMetadata(String sessionId, ApplicationProcessState appState) {
    if (gaugeMetadataManager != null) {
      GaugeMetric gaugeMetric =
          GaugeMetric.newBuilder()
              .setSessionId(sessionId)
              .setGaugeMetadata(getGaugeMetadata())
              .build();
      transportManager.log(gaugeMetric, appState);
      return true;
    }
    return false;
  }

  private GaugeMetadata getGaugeMetadata() {
    return GaugeMetadata.newBuilder()
        .setDeviceRamSizeKb(gaugeMetadataManager.getDeviceRamSizeKb())
        .setMaxAppJavaHeapMemoryKb(gaugeMetadataManager.getMaxAppJavaHeapMemoryKb())
        .setMaxEncouragedAppJavaHeapMemoryKb(
            gaugeMetadataManager.getMaxEncouragedAppJavaHeapMemoryKb())
        .build();
  }

  /**
   * Starts collecting cpu metrics if {@code cpuMetricCollectionFrequency} is valid.
   *
   * @param cpuMetricCollectionFrequency The rate at which to collect CpuMetricReadings in
   *     milliseconds. Should be greater than 0.
   * @param referenceTime The reference timestamp from which current system time should be
   *     calculated when collecting CPU metrics. See go/fireperf-sessions-timestamps.
   * @return {@code true} If it started collecting the cpu metrics, {@code false} otherwise.
   */
  private boolean startCollectingCpuMetrics(
      long cpuMetricCollectionFrequency, Timer referenceTime) {
    if (cpuMetricCollectionFrequency == CpuGaugeCollector.INVALID_CPU_COLLECTION_FREQUENCY) {
      logger.debug("Invalid Cpu Metrics collection frequency. Did not collect Cpu Metrics.");
      return false;
    }

    cpuGaugeCollector.get().startCollecting(cpuMetricCollectionFrequency, referenceTime);
    return true;
  }

  /**
   * Starts collecting memory metrics if {@code memoryMetricCollectionFrequency} is valid.
   *
   * @param memoryMetricCollectionFrequency The rate at which to collect MemoryMetricReadings in
   *     milliseconds. Should be greater than 0.
   * @param referenceTime The reference timestamp from which current system time should be
   *     calculated when collecting CPU metrics. See go/fireperf-sessions-timestamps.
   * @return {@code true} If it started collecting the memory metrics, {@code false} otherwise.
   */
  private boolean startCollectingMemoryMetrics(
      long memoryMetricCollectionFrequency, Timer referenceTime) {
    if (memoryMetricCollectionFrequency
        == MemoryGaugeCollector.INVALID_MEMORY_COLLECTION_FREQUENCY) {
      logger.debug("Invalid Memory Metrics collection frequency. Did not collect Memory Metrics.");
      return false;
    }

    memoryGaugeCollector.get().startCollecting(memoryMetricCollectionFrequency, referenceTime);
    return true;
  }

  /**
   * Collects only a single Gauge Metric data point for {@link CpuMetricReading} and {@link
   * AndroidMemoryReading}. This will later be flushed to Transport whenever {@link
   * #syncFlush(String, ApplicationProcessState)} is called.
   *
   * @param referenceTime The reference timestamp from which current system time is calculated when
   *     collecting gauges. See go/fireperf-sessions-timestamps.
   *     <p>Note: Gauge Metrics will only be collected if the respective metric collection is
   *     enabled.
   */
  public void collectGaugeMetricOnce(Timer referenceTime) {
    collectGaugeMetricOnce(cpuGaugeCollector.get(), memoryGaugeCollector.get(), referenceTime);
  }

  private static void collectGaugeMetricOnce(
      CpuGaugeCollector cpuGaugeCollector,
      MemoryGaugeCollector memoryGaugeCollector,
      Timer referenceTime) {

    cpuGaugeCollector.collectOnce(referenceTime);
    memoryGaugeCollector.collectOnce(referenceTime);
  }

  /**
   * Gets and validates the cpu gauge collection frequency for the given app state from {@link
   * com.google.firebase.perf.config.ConfigResolver}.
   *
   * @param applicationProcessState The applicationProcessState for which the frequency is desired.
   * @return Valid (> 0) cpu gauges collection frequency or {@link
   *     CpuGaugeCollector#INVALID_CPU_COLLECTION_FREQUENCY} if an invalid value is returned by
   *     config resolver.
   */
  private long getCpuGaugeCollectionFrequencyMs(ApplicationProcessState applicationProcessState) {
    long cpuGaugeCollectionFrequency;
    switch (applicationProcessState) {
      case BACKGROUND:
        cpuGaugeCollectionFrequency = configResolver.getSessionsCpuCaptureFrequencyBackgroundMs();
        break;
      case FOREGROUND:
        cpuGaugeCollectionFrequency = configResolver.getSessionsCpuCaptureFrequencyForegroundMs();
        break;
      default:
        cpuGaugeCollectionFrequency = CpuGaugeCollector.INVALID_CPU_COLLECTION_FREQUENCY;
    }

    if (CpuGaugeCollector.isInvalidCollectionFrequency(cpuGaugeCollectionFrequency)) {
      return CpuGaugeCollector.INVALID_CPU_COLLECTION_FREQUENCY;
    } else {
      return cpuGaugeCollectionFrequency;
    }
  }

  /**
   * Gets and validates the Memory gauge collection frequency for the given app state from {@link
   * com.google.firebase.perf.config.ConfigResolver}.
   *
   * @param applicationProcessState The applicationProcessState for which the frequency is desired.
   * @return Valid (> 0) memory gauges collection frequency or {@link
   *     MemoryGaugeCollector#INVALID_MEMORY_COLLECTION_FREQUENCY} if an invalid value is returned
   *     by config resolver.
   */
  private long getMemoryGaugeCollectionFrequencyMs(
      ApplicationProcessState applicationProcessState) {
    long memoryGaugeCollectionFrequency;

    switch (applicationProcessState) {
      case BACKGROUND:
        memoryGaugeCollectionFrequency =
            configResolver.getSessionsMemoryCaptureFrequencyBackgroundMs();
        break;
      case FOREGROUND:
        memoryGaugeCollectionFrequency =
            configResolver.getSessionsMemoryCaptureFrequencyForegroundMs();
        break;
      default:
        memoryGaugeCollectionFrequency = MemoryGaugeCollector.INVALID_MEMORY_COLLECTION_FREQUENCY;
    }

    if (MemoryGaugeCollector.isInvalidCollectionFrequency(memoryGaugeCollectionFrequency)) {
      return MemoryGaugeCollector.INVALID_MEMORY_COLLECTION_FREQUENCY;
    } else {
      return memoryGaugeCollectionFrequency;
    }
  }

  @VisibleForTesting
  void setApplicationProcessState(ApplicationProcessState applicationProcessState) {
    this.applicationProcessState = applicationProcessState;
  }
}
