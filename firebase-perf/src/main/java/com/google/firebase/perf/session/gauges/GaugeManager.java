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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for orchestrating different Gauges like CPU and Memory, collating that
 * information and logging it to the Transport.
 */
@Keep // Needed because of b/117526359.
public class GaugeManager {

  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private static final GaugeManager instance = new GaugeManager();

  // This is a guesstimate of the max amount of time to wait before any pending metrics' collection
  // might take.
  private static final long TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS = 20;
  private static final long APPROX_NUMBER_OF_DATA_POINTS_PER_GAUGE_METRIC = 20;
  private static final long INVALID_GAUGE_COLLECTION_FREQUENCY = -1;

  private final Lazy<ScheduledExecutorService> gaugeManagerExecutor;
  private final ConfigResolver configResolver;
  private final Lazy<CpuGaugeCollector> cpuGaugeCollector;
  private final Lazy<MemoryGaugeCollector> memoryGaugeCollector;
  private final TransportManager transportManager;

  @Nullable private GaugeMetadataManager gaugeMetadataManager;
  @Nullable private ScheduledFuture<?> gaugeManagerDataCollectionJob = null;
  @Nullable private PerfSession session = null;

  // The default value for application process state is Foreground. This will be updated based on
  // app state changes.
  private ApplicationProcessState applicationProcessState = ApplicationProcessState.FOREGROUND;

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
  }

  /** Initializes GaugeMetadataManager which requires application context. */
  public void initializeGaugeMetadataManager(Context appContext) {
    this.gaugeMetadataManager = new GaugeMetadataManager(appContext);
  }

  /** Returns the singleton instance of this class. */
  public static synchronized GaugeManager getInstance() {
    return instance;
  }

  public void updateGaugeCollection(ApplicationProcessState applicationProcessState) {
    if (gaugeManagerDataCollectionJob != null) {
      gaugeManagerDataCollectionJob.cancel(false);
    }

    if (session == null
        || !session.isGaugeAndEventCollectionEnabled()
        || session.aqsSessionId() == null) {
      logger.warn("Not starting gauge collection.");
      stopCollectingGauges();
      return;
    }

    long collectionFrequency =
        Math.min(
            getCpuGaugeCollectionFrequencyMs(applicationProcessState),
            getMemoryGaugeCollectionFrequencyMs(applicationProcessState));
    String sessionIdForScheduledTask = session.aqsSessionId();

    try {
      gaugeManagerDataCollectionJob =
          gaugeManagerExecutor
              .get()
              .scheduleWithFixedDelay(
                  () -> {
                    syncFlush(sessionIdForScheduledTask, applicationProcessState);
                  },
                  /* initialDelay= */ collectionFrequency
                      * APPROX_NUMBER_OF_DATA_POINTS_PER_GAUGE_METRIC,
                  /* period= */ collectionFrequency * APPROX_NUMBER_OF_DATA_POINTS_PER_GAUGE_METRIC,
                  TimeUnit.MILLISECONDS);

    } catch (RejectedExecutionException e) {
      logger.warn("Unable to start collecting Gauges: " + e.getMessage());
    }

    if (this.applicationProcessState != applicationProcessState) {
      this.applicationProcessState = applicationProcessState;

      // If there's a change in App State - update the gauge collection frequency as well.
      stopCollectingGauges();
      startCollectingGauges(applicationProcessState, this.session.getTimer());
    }
  }

  /**
   * Starts the collection of available gauges for the given {@code session}.
   *
   * <p>GaugeManager can only collect gauges for one session at a time, and if this method is called
   * again with the same or new sessionId while it's already collecting gauges, all future gauges
   * will then be associated with the same or new sessionId and applicationProcessState.
   *
   * @param session The {@link PerfSession} to which the collected gauges will be associated with.
   * @note: This method is NOT thread safe - {@link this.startCollectingGauges()} and {@link
   *     this.stopCollectingGauges()} should always be called from the same thread.
   */
  public void startCollectingGauges(PerfSession session) {
    if (this.session != null) {
      stopCollectingGauges();
    }

    // Updates the session associated w/ Gauge Manager when starting collecting gauges.
    this.session = session;

    if (!session.isGaugeAndEventCollectionEnabled()) {
      return;
    }

    long collectionFrequency = startCollectingGauges(applicationProcessState, session.getTimer());
    if (collectionFrequency == INVALID_GAUGE_COLLECTION_FREQUENCY) {
      logger.warn("Invalid gauge collection frequency. Unable to start collecting Gauges.");
    }
  }

  /**
   * Starts the collection of available Gauges for the given {@code appState}.
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
    if (this.session == null) {
      return;
    }

    // This is needed, otherwise the Runnable might use a stale value.

    // AQS is guaranteed to be available when stopping gauge collection.
    final String sessionIdForScheduledTask = session.aqsSessionId();
    final ApplicationProcessState applicationProcessStateForScheduledTask = applicationProcessState;

    cpuGaugeCollector.get().stopCollecting();
    memoryGaugeCollector.get().stopCollecting();

    if (gaugeManagerDataCollectionJob != null) {
      gaugeManagerDataCollectionJob.cancel(false);
    }

    // Flush any data that was collected for this session one last time.
    @SuppressWarnings("FutureReturnValueIgnored")
    ScheduledFuture<?> unusedFuture =
        gaugeManagerExecutor
            .get()
            .schedule(
                () -> {
                  syncFlush(sessionIdForScheduledTask, applicationProcessStateForScheduledTask);
                },
                TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS,
                TimeUnit.MILLISECONDS);

    this.session = null;
  }

  /**
   * This method reads any pending data points from all the Gauge's queues, assembles a GaugeMetric
   * proto and logs it to transport.
   *
   * @param sessionId The sessionId to which the collected GaugeMetrics should be associated with.
   * @param appState The app state for which these gauges are collected.
   */
  private void syncFlush(String sessionId, ApplicationProcessState appState) {
    GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();

    // Adding CPU metric readings.
    while (!cpuGaugeCollector.get().cpuMetricReadings.isEmpty()) {
      gaugeMetricBuilder.addCpuMetricReadings(cpuGaugeCollector.get().cpuMetricReadings.poll());
    }

    // Adding Memory metric readings.
    while (!memoryGaugeCollector.get().memoryMetricReadings.isEmpty()) {
      gaugeMetricBuilder.addAndroidMemoryReadings(
          memoryGaugeCollector.get().memoryMetricReadings.poll());
    }

    // Adding Session ID info.
    // TODO(b/394127311): Switch to using AQS.
    gaugeMetricBuilder.setSessionId(sessionId);

    transportManager.log(gaugeMetricBuilder.build(), appState);
  }

  /**
   * Log the Gauge Metadata information to the transport.
   *
   * @param aqsSessionId The {@link PerfSession#aqsSessionId()} ()} to which the collected Gauge Metrics
   *     should be associated with.
   * @param appState The {@link ApplicationProcessState} for which these gauges are collected.
   * @return true if GaugeMetadata was logged, false otherwise.
   */
  public boolean logGaugeMetadata(String aqsSessionId, ApplicationProcessState appState) {
    if (gaugeMetadataManager != null) {
      GaugeMetric gaugeMetric =
          GaugeMetric.newBuilder()
              .setSessionId(aqsSessionId)
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
