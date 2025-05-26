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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.perf.session.FirebaseSessionsTestHelperKt.createTestSession;
import static com.google.firebase.perf.session.FirebaseSessionsTestHelperKt.testSessionId;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.components.Lazy;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.AndroidMemoryReading;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.CpuMetricReading;
import com.google.firebase.perf.v1.GaugeMetadata;
import com.google.firebase.perf.v1.GaugeMetric;
import com.google.testing.timing.FakeScheduledExecutorService;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.session.gauges.GaugeManager} */
@RunWith(RobolectricTestRunner.class)
public final class GaugeManagerTest extends FirebasePerformanceTestBase {

  // This is a guesstimate of the max amount of time to wait before any pending metrics' collection
  // might take.
  private static final long TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS = 20;
  private static final long APPROX_NUMBER_OF_DATA_POINTS_PER_GAUGE_METRIC = 20;
  private static final long DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_BG_MS = 100;
  private static final long DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_FG_MS = 50;
  private static final long DEFAULT_MEMORY_GAUGE_COLLECTION_FREQUENCY_BG_MS = 120;
  private static final long DEFAULT_MEMORY_GAUGE_COLLECTION_FREQUENCY_FG_MS = 60;

  private GaugeManager testGaugeManager = null;
  private FakeScheduledExecutorService fakeScheduledExecutorService = null;
  private TransportManager mockTransportManager = null;
  private ConfigResolver mockConfigResolver = null;
  private GaugeMetadataManager fakeGaugeMetadataManager = null;
  private CpuGaugeCollector fakeCpuGaugeCollector = null;
  private MemoryGaugeCollector fakeMemoryGaugeCollector = null;

  @Before
  public void setUp() {
    fakeScheduledExecutorService = new FakeScheduledExecutorService();
    mockTransportManager = mock(TransportManager.class);
    mockConfigResolver = mock(ConfigResolver.class);
    fakeGaugeMetadataManager =
        spy(
            new GaugeMetadataManager(
                Runtime.getRuntime(), ApplicationProvider.getApplicationContext()));
    fakeCpuGaugeCollector = spy(new CpuGaugeCollector());
    fakeMemoryGaugeCollector = spy(new MemoryGaugeCollector());

    doNothing()
        .when(fakeCpuGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));
    doNothing().when(fakeCpuGaugeCollector).stopCollecting();

    doNothing()
        .when(fakeMemoryGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));
    doNothing().when(fakeMemoryGaugeCollector).stopCollecting();

    doNothing().when(fakeCpuGaugeCollector).collectOnce(ArgumentMatchers.nullable(Timer.class));
    doNothing().when(fakeMemoryGaugeCollector).collectOnce(ArgumentMatchers.nullable(Timer.class));

    forceVerboseSession();

    doReturn(DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_BG_MS)
        .when(mockConfigResolver)
        .getSessionsCpuCaptureFrequencyBackgroundMs();
    doReturn(DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_FG_MS)
        .when(mockConfigResolver)
        .getSessionsCpuCaptureFrequencyForegroundMs();

    doReturn(DEFAULT_MEMORY_GAUGE_COLLECTION_FREQUENCY_BG_MS)
        .when(mockConfigResolver)
        .getSessionsMemoryCaptureFrequencyBackgroundMs();
    doReturn(DEFAULT_MEMORY_GAUGE_COLLECTION_FREQUENCY_FG_MS)
        .when(mockConfigResolver)
        .getSessionsMemoryCaptureFrequencyForegroundMs();

    when(mockTransportManager.isInitialized()).thenReturn(true);

    testGaugeManager =
        new GaugeManager(
            new Lazy<>(() -> fakeScheduledExecutorService),
            mockTransportManager,
            mockConfigResolver,
            fakeGaugeMetadataManager,
            new Lazy<>(() -> fakeCpuGaugeCollector),
            new Lazy<>(() -> fakeMemoryGaugeCollector));
  }

  @After
  public void tearDown() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void testStartCollectingGaugesStartsCollectingMetricsDefault() {
    PerfSession fakeSession = createTestSession(1);
    testGaugeManager.setApplicationProcessState(
        ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
    testGaugeManager.startCollectingGauges(fakeSession);
    verify(fakeCpuGaugeCollector)
        .startCollecting(
            eq(DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_FG_MS),
            ArgumentMatchers.nullable(Timer.class));
    verify(fakeMemoryGaugeCollector)
        .startCollecting(
            eq(DEFAULT_MEMORY_GAUGE_COLLECTION_FREQUENCY_FG_MS),
            ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void testStartCollectingGaugesStartsCollectingMetricsInForegroundState() {
    PerfSession fakeSession = createTestSession(1);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.FOREGROUND);
    testGaugeManager.startCollectingGauges(fakeSession);
    verify(fakeCpuGaugeCollector)
        .startCollecting(
            eq(DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_FG_MS),
            ArgumentMatchers.nullable(Timer.class));
    verify(fakeMemoryGaugeCollector)
        .startCollecting(
            eq(DEFAULT_MEMORY_GAUGE_COLLECTION_FREQUENCY_FG_MS),
            ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void
      stopCollectingCPUMetric_invalidCPUCaptureFrequency_OtherMetricsWithValidFrequencyInBackground() {
    // PASS 1: Test with 0
    doReturn(0L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyBackgroundMs();
    PerfSession fakeSession1 = createTestSession(1);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.BACKGROUND);
    testGaugeManager.startCollectingGauges(fakeSession1);

    // Verify that Cpu metric collection is not started
    verify(fakeCpuGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // Verify that Memory metric collection is started
    verify(fakeMemoryGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyBackgroundMs();
    PerfSession fakeSession2 = createTestSession(1);
    testGaugeManager.startCollectingGauges(fakeSession2);

    // Verify that Cpu metric collection is not started
    verify(fakeCpuGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // Verify that Memory metric collection is started
    verify(fakeMemoryGaugeCollector, times(2))
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));
  }

  @Test
  public void
      startCollectingGaugesOnBackground_invalidMemoryCaptureMs_onlyDisableMemoryCollection() {
    // PASS 1: Test with 0
    doReturn(0L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyBackgroundMs();
    PerfSession fakeSession1 = createTestSession(1);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.BACKGROUND);
    testGaugeManager.startCollectingGauges(fakeSession1);

    // Verify that Memory metric collection is not started
    verify(fakeMemoryGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // Verify that Cpu metric collection is started
    verify(fakeCpuGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyBackgroundMs();
    PerfSession fakeSession2 = createTestSession(2);
    testGaugeManager.startCollectingGauges(fakeSession2);

    // Verify that Memory metric collection is not started
    verify(fakeMemoryGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // Verify that Cpu metric collection is started
    verify(fakeCpuGaugeCollector, times(2))
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));
  }

  @Test
  public void stopCollectingCPUMetric_invalidCPUCaptureFrequency_OtherMetricsWithValidFrequency() {
    // PASS 1: Test with 0
    doReturn(0L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();
    PerfSession fakeSession1 = createTestSession(1);
    testGaugeManager.startCollectingGauges(fakeSession1);

    // Verify that Cpu metric collection is not started
    verify(fakeCpuGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));

    // Verify that Memory metric collection is started
    verify(fakeMemoryGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();
    PerfSession fakeSession2 = createTestSession(2);
    testGaugeManager.startCollectingGauges(fakeSession2);

    // Verify that Cpu metric collection is not started
    verify(fakeCpuGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // Verify that Memory metric collection is started
    verify(fakeMemoryGaugeCollector, times(2))
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));
  }

  @Test
  public void
      startCollectingGaugesOnForeground_invalidMemoryCaptureMs_onlyDisableMemoryCollection() {
    // PASS 1: Test with 0
    doReturn(0L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();
    PerfSession fakeSession1 = createTestSession(1);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.FOREGROUND);
    testGaugeManager.startCollectingGauges(fakeSession1);

    // Verify that Memory metric collection is not started
    verify(fakeMemoryGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));

    // Verify that Cpu metric collection is started
    verify(fakeCpuGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();
    PerfSession fakeSession2 = createTestSession(2);
    testGaugeManager.startCollectingGauges(fakeSession2);

    // Verify that Memory metric collection is not started
    verify(fakeMemoryGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // Verify that Cpu metric collection is started
    verify(fakeCpuGaugeCollector, times(2))
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));
  }

  @Test
  // TODO(b/394127311): Explore parametrized tests.
  public void testStartCollectingGaugesDoesNotStartLogging_default() {
    PerfSession fakeSession = createTestSession(1);
    testGaugeManager.setApplicationProcessState(
        ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
    testGaugeManager.startCollectingGauges(fakeSession);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void testStartCollectingGaugesDoesNotStartLogging_appInForeground() {
    PerfSession fakeSession = createTestSession(1);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.FOREGROUND);
    testGaugeManager.startCollectingGauges(fakeSession);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void testStartCollectingGaugesDoesNotStartLogging_appInBackground() {
    PerfSession fakeSession = createTestSession(1);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.BACKGROUND);
    testGaugeManager.startCollectingGauges(fakeSession);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void stopCollectingGauges_invalidGaugeCollectionFrequency_appInForeground() {
    // PASS 1: Test with 0
    doReturn(0L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();
    doReturn(0L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();

    PerfSession fakeSession1 = createTestSession(1);
    testGaugeManager.startCollectingGauges(fakeSession1);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();
    doReturn(-25L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();

    PerfSession fakeSession2 = createTestSession(2);
    testGaugeManager.startCollectingGauges(fakeSession2);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void testGaugeCounterStartsAJobToConsumeTheGeneratedMetrics() {
    PerfSession fakeSession = createTestSession(1);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.FOREGROUND);
    testGaugeManager.startCollectingGauges(fakeSession);
    GaugeCounter.setGaugeManager(testGaugeManager);

    // There's no job to log the gauges.
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    // Generate metrics that don't exceed the GaugeCounter.MAX_COUNT.
    generateMetricsAndIncrementCounter(20);

    // There's still no job to log the gauges.
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    generateMetricsAndIncrementCounter(10);

    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();

    // Generate additional metrics, but doesn't start logging them as it hasn't met the threshold.
    generateMetricsAndIncrementCounter(5);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND);

    // It flushes all the original metrics in the ConcurrentLinkedQueues, but not the new ones
    // added after the task completed.
    int recordedGaugeMetricsCount =
        recordedGaugeMetric.getAndroidMemoryReadingsCount()
            + recordedGaugeMetric.getCpuMetricReadingsCount();
    assertThat(recordedGaugeMetricsCount).isEqualTo(30);

    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(testSessionId(1));
  }

  @Test
  public void testGaugeCounterIsDecrementedWhenLogged() {
    int priorGaugeCounter = GaugeCounter.count();

    PerfSession fakeSession = createTestSession(1);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.FOREGROUND);
    testGaugeManager.startCollectingGauges(fakeSession);
    GaugeCounter.setGaugeManager(testGaugeManager);

    // There's no job to log the gauges.
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    // Generate metrics that don't exceed the GaugeCounter.MAX_COUNT.
    generateMetricsAndIncrementCounter(20);

    // There's still no job to log the gauges.
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    generateMetricsAndIncrementCounter(10);

    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS);

    assertThat(GaugeCounter.count()).isEqualTo(priorGaugeCounter + 30);
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();

    assertThat(GaugeCounter.count()).isEqualTo(priorGaugeCounter);
  }

  @Test
  public void testUpdateAppStateHandlesMultipleAppStates() {
    PerfSession fakeSession = createTestSession(1);
    fakeSession.setGaugeAndEventCollectionEnabled(true);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.FOREGROUND);
    testGaugeManager.startCollectingGauges(fakeSession);
    GaugeCounter.setGaugeManager(testGaugeManager);

    // Generate metrics that don't exceed the GaugeCounter.MAX_COUNT.
    generateMetricsAndIncrementCounter(10);

    // There's no job to log the gauges.
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    testGaugeManager.onUpdateAppState(ApplicationProcessState.BACKGROUND);

    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    shadowOf(Looper.getMainLooper()).idle();

    // Generate additional metrics in the new app state.
    generateMetricsAndIncrementCounter(26);

    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND);

    // It flushes all metrics in the ConcurrentLinkedQueues.
    int recordedGaugeMetricsCount =
        recordedGaugeMetric.getAndroidMemoryReadingsCount()
            + recordedGaugeMetric.getCpuMetricReadingsCount();
    assertThat(recordedGaugeMetricsCount).isEqualTo(10);

    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(testSessionId(1));

    // Simulate gauges collected in the new app state.
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    shadowOf(Looper.getMainLooper()).idle();

    recordedGaugeMetric = getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND);

    // Verify the metrics in the new app state.
    recordedGaugeMetricsCount =
        recordedGaugeMetric.getAndroidMemoryReadingsCount()
            + recordedGaugeMetric.getCpuMetricReadingsCount();
    assertThat(recordedGaugeMetricsCount).isEqualTo(26);

    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(testSessionId(1));
  }

  @Test
  public void testGaugeManagerHandlesMultipleSessionIds() {
    PerfSession fakeSession = createTestSession(1);
    fakeSession.setGaugeAndEventCollectionEnabled(true);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.BACKGROUND);
    testGaugeManager.startCollectingGauges(fakeSession);
    GaugeCounter.setGaugeManager(testGaugeManager);

    // Generate metrics that don't exceed the GaugeCounter.MAX_COUNT.
    generateMetricsAndIncrementCounter(10);

    PerfSession updatedPerfSession = createTestSession(2);
    updatedPerfSession.setGaugeAndEventCollectionEnabled(true);

    // A new session and updated app state.
    testGaugeManager.startCollectingGauges(updatedPerfSession);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.FOREGROUND);

    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    shadowOf(Looper.getMainLooper()).idle();

    // Generate metrics for the new session.
    generateMetricsAndIncrementCounter(26);

    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND);

    // It flushes all metrics in the ConcurrentLinkedQueues.
    int recordedGaugeMetricsCount =
        recordedGaugeMetric.getAndroidMemoryReadingsCount()
            + recordedGaugeMetric.getCpuMetricReadingsCount();
    assertThat(recordedGaugeMetricsCount).isEqualTo(10);

    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(testSessionId(1));

    // Simulate gauges collected in the new app state.
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    shadowOf(Looper.getMainLooper()).idle();

    recordedGaugeMetric = getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND);

    // Verify the metrics in the new app state.
    recordedGaugeMetricsCount =
        recordedGaugeMetric.getAndroidMemoryReadingsCount()
            + recordedGaugeMetric.getCpuMetricReadingsCount();
    assertThat(recordedGaugeMetricsCount).isEqualTo(26);

    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(testSessionId(2));
  }

  @Test
  public void testStopCollectingGaugesStopsCollectingAllGaugeMetrics() {
    PerfSession fakeSession = createTestSession(1);

    testGaugeManager.startCollectingGauges(fakeSession);
    verify(fakeCpuGaugeCollector)
        .startCollecting(eq(DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_FG_MS), ArgumentMatchers.any());
    verify(fakeMemoryGaugeCollector)
        .startCollecting(
            eq(DEFAULT_MEMORY_GAUGE_COLLECTION_FREQUENCY_FG_MS), ArgumentMatchers.any());

    testGaugeManager.stopCollectingGauges();

    verify(fakeCpuGaugeCollector).stopCollecting();
    verify(fakeMemoryGaugeCollector).stopCollecting();
  }

  @Test
  public void testStopCollectingGaugesCreatesOneLastJobToConsumeAnyPendingMetrics() {
    PerfSession fakeSession = createTestSession(1);
    testGaugeManager.setApplicationProcessState(ApplicationProcessState.FOREGROUND);
    testGaugeManager.startCollectingGauges(fakeSession);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    generateMetricsAndIncrementCounter(2);

    testGaugeManager.stopCollectingGauges();
    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();

    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();

    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND);
    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(testSessionId(1));
    int recordedGaugeMetricsCount =
        recordedGaugeMetric.getAndroidMemoryReadingsCount()
            + recordedGaugeMetric.getCpuMetricReadingsCount();
    assertThat(recordedGaugeMetricsCount).isEqualTo(2);

    // TODO(b/394127311): Investigate why this isn't 0 on local runs.
    //    assertThat(GaugeCounter.INSTANCE.count()).isEqualTo(0);
  }

  @Test
  public void testLogGaugeMetadataSendDataToTransport() {
    when(fakeGaugeMetadataManager.getDeviceRamSizeKb()).thenReturn(2000);
    when(fakeGaugeMetadataManager.getMaxAppJavaHeapMemoryKb()).thenReturn(1000);
    when(fakeGaugeMetadataManager.getMaxEncouragedAppJavaHeapMemoryKb()).thenReturn(800);

    testGaugeManager.logGaugeMetadata(testSessionId(1), ApplicationProcessState.FOREGROUND);

    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND);
    GaugeMetadata recordedGaugeMetadata = recordedGaugeMetric.getGaugeMetadata();

    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(testSessionId(1));

    assertThat(recordedGaugeMetadata.getDeviceRamSizeKb())
        .isEqualTo(fakeGaugeMetadataManager.getDeviceRamSizeKb());
    assertThat(recordedGaugeMetadata.getMaxAppJavaHeapMemoryKb())
        .isEqualTo(fakeGaugeMetadataManager.getMaxAppJavaHeapMemoryKb());
    assertThat(recordedGaugeMetadata.getMaxEncouragedAppJavaHeapMemoryKb())
        .isEqualTo(fakeGaugeMetadataManager.getMaxEncouragedAppJavaHeapMemoryKb());
  }

  @Test
  public void testLogGaugeMetadataDoesNotLogWhenGaugeMetadataManagerNotAvailable() {
    testGaugeManager =
        new GaugeManager(
            new Lazy<>(() -> fakeScheduledExecutorService),
            mockTransportManager,
            mockConfigResolver,
            /* gaugeMetadataManager= */ null,
            new Lazy<>(() -> fakeCpuGaugeCollector),
            new Lazy<>(() -> fakeMemoryGaugeCollector));

    assertThat(
            testGaugeManager.logGaugeMetadata(testSessionId(1), ApplicationProcessState.FOREGROUND))
        .isFalse();
  }

  @Test
  public void testLogGaugeMetadataLogsAfterApplicationContextIsSet() {

    testGaugeManager =
        new GaugeManager(
            new Lazy<>(() -> fakeScheduledExecutorService),
            mockTransportManager,
            mockConfigResolver,
            /* gaugeMetadataManager= */ null,
            new Lazy<>(() -> fakeCpuGaugeCollector),
            new Lazy<>(() -> fakeMemoryGaugeCollector));

    assertThat(
            testGaugeManager.logGaugeMetadata(testSessionId(1), ApplicationProcessState.FOREGROUND))
        .isFalse();

    testGaugeManager.initializeGaugeMetadataManager(ApplicationProvider.getApplicationContext());
    assertThat(
            testGaugeManager.logGaugeMetadata(testSessionId(1), ApplicationProcessState.FOREGROUND))
        .isTrue();

    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND);
    GaugeMetadata recordedGaugeMetadata = recordedGaugeMetric.getGaugeMetadata();

    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(testSessionId(1));
    assertThat(recordedGaugeMetadata).isNotEqualTo(GaugeMetadata.getDefaultInstance());
  }

  @Test
  public void testCollectGaugeMetricOnceCollectAllMetricsWhenCollectionIsEnabled() {
    // Default Cpu and Memory metrics collection is enabled
    testGaugeManager.collectGaugeMetricOnce(new Timer());

    // Verify that Cpu metric collection is started
    verify(fakeCpuGaugeCollector).collectOnce(ArgumentMatchers.any());

    // Verify that Memory metric collection is started
    verify(fakeMemoryGaugeCollector).collectOnce(ArgumentMatchers.any());
  }

  /** @return The minimum background collection frequency of all the Gauges. */
  private long getMinimumBackgroundCollectionFrequency() {
    return DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_BG_MS;
  }

  // Simulates the behavior of Cpu and Memory Gauge collector.
  private void generateMetricsAndIncrementCounter(int count) {
    // TODO(b/394127311): Explore actually collecting metrics using the fake Cpu and Memory
    //  metric collectors.
    Random random = new Random();
    for (int i = 0; i < count; ++i) {
      if (random.nextInt(2) == 0) {
        fakeCpuGaugeCollector.cpuMetricReadings.add(createFakeCpuMetricReading(100, 200));
        GaugeCounter.incrementCounter();
      } else {
        fakeMemoryGaugeCollector.memoryMetricReadings.add(createFakeAndroidMetricReading(100));
        GaugeCounter.incrementCounter();
      }
    }
  }

  private CpuMetricReading createFakeCpuMetricReading(long userTimeUs, long systemTimeUs) {
    CpuMetricReading.Builder fakeMetricReadingBuilder = CpuMetricReading.newBuilder();
    fakeMetricReadingBuilder.setClientTimeUs(System.currentTimeMillis());
    fakeMetricReadingBuilder.setUserTimeUs(userTimeUs);
    fakeMetricReadingBuilder.setSystemTimeUs(systemTimeUs);
    return fakeMetricReadingBuilder.build();
  }

  private AndroidMemoryReading createFakeAndroidMetricReading(int currentUsedAppJavaHeapMemoryKb) {
    AndroidMemoryReading.Builder fakeMetricReadingBuilder = AndroidMemoryReading.newBuilder();
    fakeMetricReadingBuilder.setClientTimeUs(System.currentTimeMillis());
    fakeMetricReadingBuilder.setUsedAppJavaHeapMemoryKb(currentUsedAppJavaHeapMemoryKb);
    return fakeMetricReadingBuilder.build();
  }

  /**
   * Gets the last recorded GaugeMetric, and verifies that they were logged for the right {@link
   * ApplicationProcessState}.
   *
   * @param expectedApplicationProcessState The expected {@link ApplicationProcessState} that it was logged
   *                                        to.
   * @return The last logged {@link GaugeMetric}.
   */
  private GaugeMetric getLastRecordedGaugeMetric(
      ApplicationProcessState expectedApplicationProcessState) {
    ArgumentCaptor<GaugeMetric> argMetric = ArgumentCaptor.forClass(GaugeMetric.class);
    verify(mockTransportManager, times(1))
        .log(argMetric.capture(), eq(expectedApplicationProcessState));
    reset(mockTransportManager);
    // Required after resetting the mock. By default we assume that Transport is initialized.
    when(mockTransportManager.isInitialized()).thenReturn(true);
    return argMetric.getValue();
  }
}
