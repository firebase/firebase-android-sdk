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

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.components.Lazy;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.AndroidMemoryReading;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.CpuMetricReading;
import com.google.firebase.perf.v1.GaugeMetadata;
import com.google.firebase.perf.v1.GaugeMetric;
import com.google.testing.timing.FakeScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

  @Test
  public void testStartCollectingGaugesStartsCollectingMetricsInBackgroundState() {
    PerfSession fakeSession = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession, ApplicationProcessState.BACKGROUND);
    verify(fakeCpuGaugeCollector)
        .startCollecting(
            eq(DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_BG_MS),
            ArgumentMatchers.nullable(Timer.class));
    verify(fakeMemoryGaugeCollector)
        .startCollecting(
            eq(DEFAULT_MEMORY_GAUGE_COLLECTION_FREQUENCY_BG_MS),
            ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void testStartCollectingGaugesStartsCollectingMetricsInForegroundState() {
    PerfSession fakeSession = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession, ApplicationProcessState.FOREGROUND);
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
      testStartCollectingGaugesDoesNotStartCollectingMetricsWithUnknownApplicationProcessState() {
    PerfSession fakeSession = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(
        fakeSession, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
    verify(fakeCpuGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));
    verify(fakeMemoryGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void
      stopCollectingCPUMetric_invalidCPUCaptureFrequency_OtherMetricsWithValidFrequencyInBackground() {
    // PASS 1: Test with 0
    doReturn(0L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyBackgroundMs();
    PerfSession fakeSession1 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession1, ApplicationProcessState.BACKGROUND);

    // Verify that Cpu metric collection is not started
    verify(fakeCpuGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // Verify that Memory metric collection is started
    verify(fakeMemoryGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyBackgroundMs();
    PerfSession fakeSession2 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession2, ApplicationProcessState.BACKGROUND);

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
    PerfSession fakeSession1 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession1, ApplicationProcessState.BACKGROUND);

    // Verify that Memory metric collection is not started
    verify(fakeMemoryGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // Verify that Cpu metric collection is started
    verify(fakeCpuGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyBackgroundMs();
    PerfSession fakeSession2 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession2, ApplicationProcessState.BACKGROUND);

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
    PerfSession fakeSession1 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession1, ApplicationProcessState.FOREGROUND);

    // Verify that Cpu metric collection is not started
    verify(fakeCpuGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));

    // Verify that Memory metric collection is started
    verify(fakeMemoryGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();
    PerfSession fakeSession2 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession2, ApplicationProcessState.FOREGROUND);

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
    PerfSession fakeSession1 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession1, ApplicationProcessState.FOREGROUND);

    // Verify that Memory metric collection is not started
    verify(fakeMemoryGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));

    // Verify that Cpu metric collection is started
    verify(fakeCpuGaugeCollector)
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();
    PerfSession fakeSession2 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession2, ApplicationProcessState.FOREGROUND);

    // Verify that Memory metric collection is not started
    verify(fakeMemoryGaugeCollector, never())
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(Timer.class));

    // Verify that Cpu metric collection is started
    verify(fakeCpuGaugeCollector, times(2))
        .startCollecting(ArgumentMatchers.anyLong(), ArgumentMatchers.any(Timer.class));
  }

  @Test
  public void testStartCollectingGaugesDoesNotStartAJobToConsumeMetricsWithUnknownAppState() {
    PerfSession fakeSession = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(
        fakeSession, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void stopCollectingCPUMetrics_invalidCPUCaptureFrequency_appInForegrounf() {
    // PASS 1: Test with 0
    doReturn(0L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();

    PerfSession fakeSession1 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession1, ApplicationProcessState.FOREGROUND);
    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();

    PerfSession fakeSession2 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession2, ApplicationProcessState.FOREGROUND);
    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
  }

  @Test
  public void stopCollectingGauges_invalidMemoryCollectionFrequency_appInForeground() {
    // PASS 1: Test with 0
    doReturn(0L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();

    PerfSession fakeSession1 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession1, ApplicationProcessState.FOREGROUND);
    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();

    PerfSession fakeSession2 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession2, ApplicationProcessState.FOREGROUND);
    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
  }

  @Test
  public void stopCollectingGauges_invalidGaugeCollectionFrequency_appInForeground() {
    // PASS 1: Test with 0
    doReturn(0L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();
    doReturn(0L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();

    PerfSession fakeSession1 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession1, ApplicationProcessState.FOREGROUND);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    // PASS 2: Test with -ve value
    doReturn(-25L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();
    doReturn(-25L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();

    PerfSession fakeSession2 = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession2, ApplicationProcessState.FOREGROUND);
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void startCollectingGauges_validGaugeCollectionFrequency_appInForeground() {
    doReturn(25L).when(mockConfigResolver).getSessionsCpuCaptureFrequencyForegroundMs();
    doReturn(15L).when(mockConfigResolver).getSessionsMemoryCaptureFrequencyForegroundMs();

    PerfSession fakeSession = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession, ApplicationProcessState.FOREGROUND);

    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(15L * APPROX_NUMBER_OF_DATA_POINTS_PER_GAUGE_METRIC);
  }

  @Test
  public void testStartCollectingGaugesStartsAJobToConsumeTheGeneratedMetrics() {
    PerfSession fakeSession = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession, ApplicationProcessState.BACKGROUND);

    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(
            getMinimumBackgroundCollectionFrequency()
                * APPROX_NUMBER_OF_DATA_POINTS_PER_GAUGE_METRIC);

    CpuMetricReading fakeCpuMetricReading1 = createFakeCpuMetricReading(200, 100);
    CpuMetricReading fakeCpuMetricReading2 = createFakeCpuMetricReading(300, 200);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading1);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading2);

    AndroidMemoryReading fakeMemoryMetricReading1 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 123456);
    AndroidMemoryReading fakeMemoryMetricReading2 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 23454678);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading1);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading2);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND, 1);

    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric, fakeCpuMetricReading1, fakeCpuMetricReading2);

    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric, fakeMemoryMetricReading1, fakeMemoryMetricReading2);
  }

  @Test
  public void testStopCollectingGaugesStopsCollectingAllGaugeMetrics() {
    PerfSession fakeSession = new PerfSession("sessionId", new Clock());

    testGaugeManager.startCollectingGauges(fakeSession, ApplicationProcessState.BACKGROUND);
    verify(fakeCpuGaugeCollector)
        .startCollecting(eq(DEFAULT_CPU_GAUGE_COLLECTION_FREQUENCY_BG_MS), ArgumentMatchers.any());

    testGaugeManager.stopCollectingGauges();

    verify(fakeCpuGaugeCollector).stopCollecting();
    verify(fakeMemoryGaugeCollector).stopCollecting();
  }

  @Test
  public void testStopCollectingGaugesCreatesOneLastJobToConsumeAnyPendingMetrics() {
    PerfSession fakeSession = new PerfSession("sessionId", new Clock());
    testGaugeManager.startCollectingGauges(fakeSession, ApplicationProcessState.BACKGROUND);
    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();

    testGaugeManager.stopCollectingGauges();
    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();

    CpuMetricReading fakeCpuMetricReading = createFakeCpuMetricReading(200, 100);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading);

    AndroidMemoryReading fakeMemoryMetricReading =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 23454678);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading);

    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(TIME_TO_WAIT_BEFORE_FLUSHING_GAUGES_QUEUE_MS);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric, fakeCpuMetricReading);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric, fakeMemoryMetricReading);
  }

  @Test
  public void testGaugeManagerClearsTheQueueEachRun() {
    PerfSession fakeSession = new PerfSession("sessionId", new Clock());

    testGaugeManager.startCollectingGauges(fakeSession, ApplicationProcessState.BACKGROUND);

    fakeCpuGaugeCollector.cpuMetricReadings.add(createFakeCpuMetricReading(200, 100));
    fakeCpuGaugeCollector.cpuMetricReadings.add(createFakeCpuMetricReading(300, 400));
    fakeMemoryGaugeCollector.memoryMetricReadings.add(
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234));

    assertThat(fakeCpuGaugeCollector.cpuMetricReadings).isNotEmpty();
    assertThat(fakeMemoryGaugeCollector.memoryMetricReadings).isNotEmpty();

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeCpuGaugeCollector.cpuMetricReadings).isEmpty();
    assertThat(fakeMemoryGaugeCollector.memoryMetricReadings).isEmpty();

    fakeCpuGaugeCollector.cpuMetricReadings.add(createFakeCpuMetricReading(200, 100));
    fakeMemoryGaugeCollector.memoryMetricReadings.add(
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234));
    fakeMemoryGaugeCollector.memoryMetricReadings.add(
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 2345));

    assertThat(fakeCpuGaugeCollector.cpuMetricReadings).isNotEmpty();
    assertThat(fakeMemoryGaugeCollector.memoryMetricReadings).isNotEmpty();

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeCpuGaugeCollector.cpuMetricReadings).isEmpty();
    assertThat(fakeMemoryGaugeCollector.memoryMetricReadings).isEmpty();
  }

  @Test
  public void testStartingGaugeManagerWithNewSessionIdButSameAppState() {
    PerfSession fakeSession1 = new PerfSession("sessionId", new Clock());

    // Start collecting Gauges.
    testGaugeManager.startCollectingGauges(fakeSession1, ApplicationProcessState.BACKGROUND);
    CpuMetricReading fakeCpuMetricReading1 = createFakeCpuMetricReading(200, 100);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading1);
    AndroidMemoryReading fakeMemoryMetricReading1 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading1);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric1 =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric1, fakeCpuMetricReading1);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric1, fakeMemoryMetricReading1);

    // One Cpu and Memory metric was added when the gauge was collecting for the previous sessionId.
    CpuMetricReading fakeCpuMetricReading2 = createFakeCpuMetricReading(400, 500);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading2);
    AndroidMemoryReading fakeMemoryMetricReading2 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 2345);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading2);

    PerfSession fakeSession2 = new PerfSession("sessionId2", new Clock());

    // Start collecting gauges for new session, but same app state.
    testGaugeManager.startCollectingGauges(fakeSession2, ApplicationProcessState.BACKGROUND);

    // The next sweep conducted by GaugeManager still associates metrics to old sessionId and state.
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric2 =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric2, fakeCpuMetricReading2);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric2, fakeMemoryMetricReading2);

    // Collect some more Cpu and Memory metrics and verify that they're associated with new
    // sessionId and state.
    CpuMetricReading fakeCpuMetricReading3 = createFakeCpuMetricReading(500, 600);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading3);
    AndroidMemoryReading fakeMemoryMetricReading3 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 3456);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading3);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric3 =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId2", recordedGaugeMetric3, fakeCpuMetricReading3);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId2", recordedGaugeMetric3, fakeMemoryMetricReading3);
  }

  @Test
  public void testStartGaugeManagerWithSameSessionIdButDifferentAppState() {
    PerfSession fakeSession = new PerfSession("sessionId", new Clock());

    // Start collecting Gauges.
    testGaugeManager.startCollectingGauges(fakeSession, ApplicationProcessState.BACKGROUND);
    CpuMetricReading fakeCpuMetricReading1 = createFakeCpuMetricReading(200, 100);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading1);
    AndroidMemoryReading fakeMemoryMetricReading1 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading1);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric1 =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric1, fakeCpuMetricReading1);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric1, fakeMemoryMetricReading1);

    // One Cpu and Memory metric was added when the gauge was collecting for the previous sessionId.
    CpuMetricReading fakeCpuMetricReading2 = createFakeCpuMetricReading(400, 500);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading2);
    AndroidMemoryReading fakeMemoryMetricReading2 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 2345);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading2);

    // Start collecting gauges for same session, but new app state
    testGaugeManager.startCollectingGauges(fakeSession, ApplicationProcessState.FOREGROUND);

    // The next sweep conducted by GaugeManager still associates metrics to old sessionId and state.
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric2 =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric2, fakeCpuMetricReading2);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric2, fakeMemoryMetricReading2);

    // Collect some more Cpu and Memory metrics and verify that they're associated with new
    // sessionId and state.
    CpuMetricReading fakeCpuMetricReading3 = createFakeCpuMetricReading(500, 600);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading3);
    AndroidMemoryReading fakeMemoryMetricReading3 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 3456);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading3);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric3 =
        getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric3, fakeCpuMetricReading3);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric3, fakeMemoryMetricReading3);
  }

  @Test
  public void testStartGaugeManagerWithNewSessionIdAndNewAppState() {
    PerfSession fakeSession1 = new PerfSession("sessionId", new Clock());

    // Start collecting Gauges.
    testGaugeManager.startCollectingGauges(fakeSession1, ApplicationProcessState.BACKGROUND);
    CpuMetricReading fakeCpuMetricReading1 = createFakeCpuMetricReading(200, 100);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading1);
    AndroidMemoryReading fakeMemoryMetricReading1 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading1);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric1 =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric1, fakeCpuMetricReading1);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric1, fakeMemoryMetricReading1);

    // One Cpu and Memory metric was added when the gauge was collecting for the previous sessionId.
    CpuMetricReading fakeCpuMetricReading2 = createFakeCpuMetricReading(400, 500);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading2);
    AndroidMemoryReading fakeMemoryMetricReading2 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 2345);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading2);

    PerfSession fakeSession2 = new PerfSession("sessionId2", new Clock());

    // Start collecting gauges for new session and new app state
    testGaugeManager.startCollectingGauges(fakeSession2, ApplicationProcessState.FOREGROUND);

    // The next sweep conducted by GaugeManager still associates metrics to old sessionId and state.
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric2 =
        getLastRecordedGaugeMetric(ApplicationProcessState.BACKGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric2, fakeCpuMetricReading2);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId", recordedGaugeMetric2, fakeMemoryMetricReading2);

    // Collect some more Cpu and Memory metrics and verify that they're associated with new
    // sessionId and state.
    CpuMetricReading fakeCpuMetricReading3 = createFakeCpuMetricReading(500, 600);
    fakeCpuGaugeCollector.cpuMetricReadings.add(fakeCpuMetricReading3);
    AndroidMemoryReading fakeMemoryMetricReading3 =
        createFakeAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 3456);
    fakeMemoryGaugeCollector.memoryMetricReadings.add(fakeMemoryMetricReading3);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    GaugeMetric recordedGaugeMetric3 =
        getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND, 1);
    assertThatCpuGaugeMetricWasSentToTransport(
        "sessionId2", recordedGaugeMetric3, fakeCpuMetricReading3);
    assertThatMemoryGaugeMetricWasSentToTransport(
        "sessionId2", recordedGaugeMetric3, fakeMemoryMetricReading3);
  }

  @Test
  public void testLogGaugeMetadataSendDataToTransport() {
    when(fakeGaugeMetadataManager.getDeviceRamSizeKb()).thenReturn(2000);
    when(fakeGaugeMetadataManager.getMaxAppJavaHeapMemoryKb()).thenReturn(1000);
    when(fakeGaugeMetadataManager.getMaxEncouragedAppJavaHeapMemoryKb()).thenReturn(800);

    testGaugeManager.logGaugeMetadata("sessionId", ApplicationProcessState.FOREGROUND);

    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND, 1);
    GaugeMetadata recordedGaugeMetadata = recordedGaugeMetric.getGaugeMetadata();

    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo("sessionId");

    assertThat(recordedGaugeMetadata.getDeviceRamSizeKb())
        .isEqualTo(fakeGaugeMetadataManager.getDeviceRamSizeKb());
    assertThat(recordedGaugeMetadata.getMaxAppJavaHeapMemoryKb())
        .isEqualTo(fakeGaugeMetadataManager.getMaxAppJavaHeapMemoryKb());
    assertThat(recordedGaugeMetadata.getMaxEncouragedAppJavaHeapMemoryKb())
        .isEqualTo(fakeGaugeMetadataManager.getMaxEncouragedAppJavaHeapMemoryKb());
  }

  @Test
  public void testLogGaugeMetadataDoesntLogWhenGaugeMetadataManagerNotAvailable() {

    testGaugeManager =
        new GaugeManager(
            new Lazy<>(() -> fakeScheduledExecutorService),
            mockTransportManager,
            mockConfigResolver,
            /* gaugeMetadataManager= */ null,
            new Lazy<>(() -> fakeCpuGaugeCollector),
            new Lazy<>(() -> fakeMemoryGaugeCollector));

    assertThat(testGaugeManager.logGaugeMetadata("sessionId", ApplicationProcessState.FOREGROUND))
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

    assertThat(testGaugeManager.logGaugeMetadata("sessionId", ApplicationProcessState.FOREGROUND))
        .isFalse();

    testGaugeManager.initializeGaugeMetadataManager(ApplicationProvider.getApplicationContext());
    assertThat(testGaugeManager.logGaugeMetadata("sessionId", ApplicationProcessState.FOREGROUND))
        .isTrue();

    GaugeMetric recordedGaugeMetric =
        getLastRecordedGaugeMetric(ApplicationProcessState.FOREGROUND, 1);
    GaugeMetadata recordedGaugeMetadata = recordedGaugeMetric.getGaugeMetadata();

    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo("sessionId");
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
   * @param applicationProcessState The expected {@link ApplicationProcessState} that it was logged
   *     to.
   * @param timesLogged Number of {@link GaugeMetric} that were expected to be logged to Transport.
   * @return The last logged {@link GaugeMetric}.
   */
  private GaugeMetric getLastRecordedGaugeMetric(
      ApplicationProcessState applicationProcessState, int timesLogged) {
    ArgumentCaptor<GaugeMetric> argMetric = ArgumentCaptor.forClass(GaugeMetric.class);
    verify(mockTransportManager, times(timesLogged))
        .log(argMetric.capture(), eq(applicationProcessState));
    reset(mockTransportManager);
    // Required after resetting the mock. By default we assume that Transport is initialized.
    when(mockTransportManager.isInitialized()).thenReturn(true);
    return argMetric.getValue();
  }

  private void assertThatCpuGaugeMetricWasSentToTransport(
      String sessionId, GaugeMetric recordedGaugeMetric, CpuMetricReading... cpuMetricReadings) {
    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(sessionId);
    assertThat(recordedGaugeMetric.getCpuMetricReadingsList())
        .containsAtLeastElementsIn(cpuMetricReadings);
  }

  private void assertThatMemoryGaugeMetricWasSentToTransport(
      String sessionId,
      GaugeMetric recordedGaugeMetric,
      AndroidMemoryReading... androidMetricReadings) {
    assertThat(recordedGaugeMetric.getSessionId()).isEqualTo(sessionId);
    assertThat(recordedGaugeMetric.getAndroidMemoryReadingsList())
        .containsAtLeastElementsIn(androidMetricReadings);
  }
}
