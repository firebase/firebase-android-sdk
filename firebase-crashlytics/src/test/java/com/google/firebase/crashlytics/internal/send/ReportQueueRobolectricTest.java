// Copyright 2026 Google LLC
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

package com.google.firebase.crashlytics.internal.send;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.android.datatransport.Transport;
import com.google.android.gms.tasks.Task;
import com.google.firebase.crashlytics.internal.common.CrashlyticsReportWithSessionId;
import com.google.firebase.crashlytics.internal.common.OnDemandCounter;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.settings.Settings;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ReportQueueRobolectricTest {
  @Mock private Transport<CrashlyticsReport> mockTransport;

  private final OnDemandCounter onDemandCounter = new OnDemandCounter();
  // Unblocks the queue's worker thread at the end of each test.
  private final CountDownLatch releaseTransport = new CountDownLatch(1);
  private AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    releaseTransport.countDown();
    mocks.close();
  }

  @Test
  public void zeroRate_dropsOnDemandReports() {
    ReportQueue reportQueue = new ReportQueue(mockTransport, settingsWithRate(0), onDemandCounter);

    Task<CrashlyticsReportWithSessionId> task =
        reportQueue.enqueueReport(mockReportWithSessionId(), /* isOnDemand= */ true).getTask();

    assertThat(task.isSuccessful()).isTrue();
    assertThat(onDemandCounter.getRecordedOnDemandExceptions()).isEqualTo(1);
    assertThat(onDemandCounter.getDroppedOnDemandExceptions()).isEqualTo(1);
    verify(mockTransport, never()).schedule(any(), any());
  }

  @Test
  public void zeroRate_stillSendsReportsThatAreNotOnDemand() {
    ReportQueue reportQueue = new ReportQueue(mockTransport, settingsWithRate(0), onDemandCounter);

    reportQueue.enqueueReport(mockReportWithSessionId(), /* isOnDemand= */ false);

    verify(mockTransport).schedule(any(), any());
  }

  @Test
  public void fractionalRate_roundsQueueCapacityUp() throws InterruptedException {
    assertOnDemandQueueCapacity(settingsWithRate(0.5), /* expectedCapacity= */ 1);
  }

  @Test
  public void integerRate_keepsQueueCapacity() throws InterruptedException {
    assertOnDemandQueueCapacity(settingsWithRate(10), /* expectedCapacity= */ 10);
  }

  @Test
  public void validatedRateRange_doesNotThrow() {
    // Settings parsing limits the rate to the range [0, 1000].
    for (double rate : new double[] {0, Double.MIN_VALUE, 0.5, 1000}) {
      new ReportQueue(mockTransport, settingsWithRate(rate), onDemandCounter);
    }
  }

  /**
   * Blocks the queue's worker thread while it sends the first on-demand report, then checks that
   * exactly {@code expectedCapacity} more reports are queued before reports start being dropped.
   */
  private void assertOnDemandQueueCapacity(Settings settings, int expectedCapacity)
      throws InterruptedException {
    CountDownLatch sending = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              sending.countDown();
              releaseTransport.await();
              return null;
            })
        .when(mockTransport)
        .schedule(any(), any());
    ReportQueue reportQueue = new ReportQueue(mockTransport, settings, onDemandCounter);
    CrashlyticsReportWithSessionId report = mockReportWithSessionId();

    reportQueue.enqueueReport(report, /* isOnDemand= */ true);
    assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
    for (int i = 0; i <= expectedCapacity; i++) {
      reportQueue.enqueueReport(report, /* isOnDemand= */ true);
    }

    assertThat(onDemandCounter.getRecordedOnDemandExceptions()).isEqualTo(expectedCapacity + 2);
    assertThat(onDemandCounter.getDroppedOnDemandExceptions()).isEqualTo(1);
    verify(mockTransport, times(1)).schedule(any(), any());
  }

  private static Settings settingsWithRate(double onDemandUploadRatePerMinute) {
    return new Settings(
        /* expiresAtMillis= */ 0,
        new Settings.SessionData(
            /* maxCustomExceptionEvents= */ 8, /* maxCompleteSessionsCount= */ 4),
        new Settings.FeatureFlagData(
            /* collectReports= */ true, /* collectAnrs= */ false, /* collectBuildIds= */ false),
        /* settingsVersion= */ 3,
        /* cacheDuration= */ 3600,
        onDemandUploadRatePerMinute,
        /* onDemandBackoffBase= */ 1.2,
        /* onDemandBackoffStepDurationSeconds= */ 60);
  }

  private static CrashlyticsReportWithSessionId mockReportWithSessionId() {
    return CrashlyticsReportWithSessionId.create(
        mock(CrashlyticsReport.class), "sessionId", new File("fake"));
  }
}
