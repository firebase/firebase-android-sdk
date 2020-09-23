// Copyright 2020 Google LLC
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.runner.AndroidJUnit4;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportScheduleCallback;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.common.CrashlyticsReportWithSessionId;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

@RunWith(AndroidJUnit4.class)
public class DataTransportCrashlyticsReportSenderTest {

  @Mock private Transport<CrashlyticsReport> mockTransport;
  @Mock private Transformer<CrashlyticsReport, byte[]> mockTransform;

  private DataTransportCrashlyticsReportSender reportSender;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(mockTransform.apply(any())).thenReturn(new byte[0]);
    reportSender = new DataTransportCrashlyticsReportSender(mockTransport, mockTransform);
  }

  @Test
  public void testSendReportsSuccessful() throws Exception {
    doAnswer(callbackAnswer(null)).when(mockTransport).schedule(any(), any());

    final CrashlyticsReportWithSessionId report1 = mockReportWithSessionId();
    final CrashlyticsReportWithSessionId report2 = mockReportWithSessionId();

    final Task<CrashlyticsReportWithSessionId> send1 = reportSender.sendReport(report1);
    final Task<CrashlyticsReportWithSessionId> send2 = reportSender.sendReport(report2);

    try {
      Tasks.await(send1);
      Tasks.await(send2);
    } catch (ExecutionException e) {
      // Allow this to fall through
    }

    assertTrue(send1.isSuccessful());
    assertEquals(report1, send1.getResult());
    assertTrue(send2.isSuccessful());
    assertEquals(report2, send2.getResult());
  }

  @Test
  public void testSendReportsFailure() throws Exception {
    final Exception ex = new Exception("fail");
    doAnswer(callbackAnswer(ex)).when(mockTransport).schedule(any(), any());

    final CrashlyticsReportWithSessionId report1 = mockReportWithSessionId();
    final CrashlyticsReportWithSessionId report2 = mockReportWithSessionId();

    final Task<CrashlyticsReportWithSessionId> send1 = reportSender.sendReport(report1);
    final Task<CrashlyticsReportWithSessionId> send2 = reportSender.sendReport(report2);

    try {
      Tasks.await(send1);
      Tasks.await(send2);
    } catch (ExecutionException e) {
      // Allow this to fall through
    }

    assertFalse(send1.isSuccessful());
    assertEquals(ex, send1.getException());
    assertFalse(send2.isSuccessful());
    assertEquals(ex, send2.getException());
  }

  @Test
  public void testSendReports_oneSuccessOneFail() throws Exception {
    final Exception ex = new Exception("fail");
    doAnswer(callbackAnswer(null))
        .doAnswer(callbackAnswer(ex))
        .when(mockTransport)
        .schedule(any(), any());

    final CrashlyticsReportWithSessionId report1 = mockReportWithSessionId();
    final CrashlyticsReportWithSessionId report2 = mockReportWithSessionId();

    final Task<CrashlyticsReportWithSessionId> send1 = reportSender.sendReport(report1);
    final Task<CrashlyticsReportWithSessionId> send2 = reportSender.sendReport(report2);

    try {
      Tasks.await(send1);
      Tasks.await(send2);
    } catch (ExecutionException e) {
      // Allow this to fall through
    }

    assertTrue(send1.isSuccessful());
    assertEquals(report1, send1.getResult());
    assertFalse(send2.isSuccessful());
    assertEquals(ex, send2.getException());
  }

  private static Answer<Void> callbackAnswer(Exception failure) {
    return (i) -> {
      final TransportScheduleCallback callback = i.getArgument(1);
      callback.onSchedule(failure);
      return null;
    };
  }

  private static CrashlyticsReportWithSessionId mockReportWithSessionId() {
    return CrashlyticsReportWithSessionId.create(mock(CrashlyticsReport.class), "sessionId");
  }
}
