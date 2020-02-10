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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import androidx.test.runner.AndroidJUnit4;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DataTransportCrashlyticsReportSenderTest {

  @Mock private Transport<CrashlyticsReport> mockTransport;

  @Mock private SendCallback<CrashlyticsReport> mockCallback;

  private DataTransportCrashlyticsReportSender reportSender;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    reportSender = new DataTransportCrashlyticsReportSender(mockTransport, mockCallback);
  }

  @Test
  public void testSendReportsSuccessful() {
    doAnswer(
            (i) -> {
              final Event<CrashlyticsReport> event = i.getArgument(0);
              mockCallback.onSendComplete(event.getPayload(), null);
              return null;
            })
        .when(mockTransport)
        .schedule(any(), any());

    final CrashlyticsReport report1 = mock(CrashlyticsReport.class);
    final CrashlyticsReport report2 = mock(CrashlyticsReport.class);

    final List<CrashlyticsReport> reports = new ArrayList<>();
    reports.add(report1);
    reports.add(report2);

    reportSender.sendReports(reports);
    verify(mockCallback).onSendComplete(report1, null);
    verify(mockCallback).onSendComplete(report2, null);
  }

  @Test
  public void testSendReportsFailure() {
    final IllegalStateException error = new IllegalStateException();
    doAnswer(
            (i) -> {
              final Event<CrashlyticsReport> event = i.getArgument(0);
              mockCallback.onSendComplete(event.getPayload(), error);
              return null;
            })
        .when(mockTransport)
        .schedule(any(), any());

    final CrashlyticsReport report1 = mock(CrashlyticsReport.class);
    final CrashlyticsReport report2 = mock(CrashlyticsReport.class);

    final List<CrashlyticsReport> reports = new ArrayList<>();
    reports.add(report1);
    reports.add(report2);

    reportSender.sendReports(reports);
    verify(mockCallback).onSendComplete(report1, error);
    verify(mockCallback).onSendComplete(report2, error);
  }
}
