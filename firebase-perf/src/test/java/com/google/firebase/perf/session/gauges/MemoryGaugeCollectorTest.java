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
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.util.StorageUnit;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.AndroidMemoryReading;
import com.google.testing.timing.FakeScheduledExecutorService;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.session.gauges.MemoryGaugeCollector} */
@RunWith(RobolectricTestRunner.class)
public class MemoryGaugeCollectorTest {

  private MemoryGaugeCollector testGaugeCollector = null;
  private FakeScheduledExecutorService fakeScheduledExecutorService = null;

  private static final long RUNTIME_TOTAL_MEMORY_BYTES = StorageUnit.MEGABYTES.toBytes(100);
  private static final long RUNTIME_FREE_MEMORY_BYTES = StorageUnit.MEGABYTES.toBytes(75);

  @Mock private Runtime runtime;

  @Before
  public void setUp() {
    initMocks(this);
    mockMemory();

    fakeScheduledExecutorService = new FakeScheduledExecutorService();
    testGaugeCollector = new MemoryGaugeCollector(fakeScheduledExecutorService, runtime);
  }

  private void mockMemory() {
    // TODO(b/177317586): Unable to mock Runtime class after introduction of "mockito-inline" which
    //  is an incubating feature for mocking final classes.
    //    when(runtime.totalMemory()).thenReturn(RUNTIME_TOTAL_MEMORY_BYTES);
    //    when(runtime.freeMemory()).thenReturn(RUNTIME_FREE_MEMORY_BYTES);
  }

  @Test
  public void testStartCollecting_addsMemoryMetricReadingsToQueue() {
    testGaugeCollector.startCollecting(/* memoryMetricCollectionRateMs= */ 100, new Timer());
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(testGaugeCollector.memoryMetricReadings).hasSize(1);
  }

  @Test
  public void testStartCollecting_hasCorrectInterval() {
    testGaugeCollector.startCollecting(/* memoryMetricCollectionRateMs= */ 500, new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(500);

    testGaugeCollector.collectOnce(new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(500);

    testGaugeCollector.startCollecting(/* memoryMetricCollectionRateMs= */ 200, new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(200);

    testGaugeCollector.collectOnce(new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(200);
  }

  @Test
  public void testStopCollecting_cancelsFutureTasks() {
    testGaugeCollector.startCollecting(/* memoryMetricCollectionRateMs= */ 500, new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(500);

    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
    testGaugeCollector.stopCollecting();
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void testCollectedMemoryMetric_hasCorrectValues() throws IOException {
    testGaugeCollector.startCollecting(/* memoryMetricCollectionRateMs= */ 500, new Timer());
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();

    AndroidMemoryReading recordedReading = testGaugeCollector.memoryMetricReadings.poll();
    assertThat(recordedReading.getUsedAppJavaHeapMemoryKb()).isGreaterThan(0);
    //    assertThat(recordedReading.getUsedAppJavaHeapMemoryKb())
    //        .isEqualTo(
    //            StorageUnit.BYTES.toKilobytes(RUNTIME_TOTAL_MEMORY_BYTES -
    // RUNTIME_FREE_MEMORY_BYTES));
  }

  @Test
  public void testCollectedMemoryMetric_containsApproximatelyCorrectTimestamp() {
    Timer testTimer = new Timer();

    testGaugeCollector.startCollecting(/* memoryMetricCollectionRateMs= */ 100, testTimer);
    long beforeTimestampUs = testTimer.getCurrentTimestampMicros();
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    long afterTimestampUs = testTimer.getCurrentTimestampMicros();

    AndroidMemoryReading metricReading = testGaugeCollector.memoryMetricReadings.poll();
    assertThat(metricReading.getClientTimeUs()).isAtLeast(beforeTimestampUs);
    assertThat(metricReading.getClientTimeUs()).isAtMost(afterTimestampUs);
  }

  @Test
  public void
      testCollectMemoryMetric_doesNotStartCollecting_withInvalidMemoryMetricCollectionRate() {
    testGaugeCollector.startCollecting(/* memoryMetricCollectionRateMs= */ -1, new Timer());
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    testGaugeCollector.startCollecting(/* memoryMetricCollectionRateMs= */ 0, new Timer());
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void testCollectOnce_addOnlyOneMemoryMetricReadingToQueue() {
    assertThat(testGaugeCollector.memoryMetricReadings).isEmpty();
    testGaugeCollector.collectOnce(new Timer());

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(testGaugeCollector.memoryMetricReadings).hasSize(1);
  }

  @Test
  public void testInvalidFrequency() {
    // Verify with -ve value
    assertThat(MemoryGaugeCollector.isInvalidCollectionFrequency(-1)).isTrue();

    // Verify with 0
    assertThat(MemoryGaugeCollector.isInvalidCollectionFrequency(0)).isTrue();

    // Verify with +ve value
    assertThat(MemoryGaugeCollector.isInvalidCollectionFrequency(1)).isFalse();
  }
}
