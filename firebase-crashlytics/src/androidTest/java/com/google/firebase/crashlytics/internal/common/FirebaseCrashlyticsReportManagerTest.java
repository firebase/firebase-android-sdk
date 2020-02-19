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

package com.google.firebase.crashlytics.internal.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FirebaseCrashlyticsReportManagerTest {

  @Mock private CrashlyticsReportDataCapture dataCapture;

  @Mock private CrashlyticsReportPersistence reportPersistence;

  @Mock private DataTransportCrashlyticsReportSender reportSender;

  @Mock private CurrentTimeProvider mockCurrentTimeProvider;

  private FirebaseCrashlyticsReportManager reportManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    reportManager =
        new FirebaseCrashlyticsReportManager(
            dataCapture, reportPersistence, reportSender, mockCurrentTimeProvider, Runnable::run);
  }

  @Test
  public void testOnSessionBegin_persistsReportForSessionId() {
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(timestamp);
    final long timestampSeconds = timestamp / 1000;
    final CrashlyticsReport mockReport = mock(CrashlyticsReport.class);
    when(dataCapture.captureReportData(anyString(), anyLong())).thenReturn(mockReport);

    reportManager.onBeginSession(sessionId);

    verify(dataCapture).captureReportData(sessionId, timestampSeconds);
    verify(reportPersistence).persistReport(mockReport);
  }

  @Test
  public void testOnFatalEvent_persistsFatalEventForSessionId() {
    final String eventType = "crash";
    final Exception exceptionEvent = new Exception("fatal");
    final Thread eventThread = Thread.currentThread();

    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(timestamp);
    final long timestampSeconds = timestamp / 1000;
    final CrashlyticsReport.Session.Event mockEvent = mock(CrashlyticsReport.Session.Event.class);
    when(dataCapture.captureEventData(
            any(Throwable.class),
            any(Thread.class),
            anyString(),
            anyLong(),
            anyInt(),
            anyInt(),
            anyBoolean()))
        .thenReturn(mockEvent);

    reportManager.onBeginSession(sessionId);
    reportManager.onFatalEvent(exceptionEvent, eventThread);

    verify(dataCapture)
        .captureEventData(exceptionEvent, eventThread, eventType, timestampSeconds, 4, 8, true);
    verify(reportPersistence).persistEvent(mockEvent, sessionId);
  }

  @Test
  public void testOnNonFatalEvent_persistsNonFatalEventForSessionId() {
    final String eventType = "error";
    final Exception exceptionEvent = new Exception("nonfatal");
    final Thread eventThread = Thread.currentThread();

    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(timestamp);
    final long timestampSeconds = timestamp / 1000;
    final CrashlyticsReport.Session.Event mockEvent = mock(CrashlyticsReport.Session.Event.class);
    when(dataCapture.captureEventData(
            any(Throwable.class),
            any(Thread.class),
            anyString(),
            anyLong(),
            anyInt(),
            anyInt(),
            anyBoolean()))
        .thenReturn(mockEvent);

    reportManager.onBeginSession(sessionId);
    reportManager.onNonFatalEvent(exceptionEvent, eventThread);

    verify(dataCapture)
        .captureEventData(exceptionEvent, eventThread, eventType, timestampSeconds, 4, 8, false);
    verify(reportPersistence).persistEvent(mockEvent, sessionId);
  }

  @Test
  public void onSessionsFinalize_finalizesReports() {
    final String sessionId = "testSessionId";
    reportManager.onBeginSession(sessionId);
    reportManager.onFinalizeSessions();

    verify(reportPersistence).finalizeReports(sessionId);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void onReportSend_successfulReportsAreDeleted() throws InterruptedException {
    final String orgId = "testOrgId";
    final String sessionId1 = "sessionId1";
    final String sessionId2 = "sessionId2";

    final AppSettingsData appSettings =
        new AppSettingsData(null, null, null, null, null, orgId, false);

    final List<CrashlyticsReport> finalizedReports = new ArrayList<>();
    final CrashlyticsReport mockReport1 = mockReport(sessionId1, orgId);
    final CrashlyticsReport mockReport2 = mockReport(sessionId2, orgId);
    finalizedReports.add(mockReport1);
    finalizedReports.add(mockReport2);

    when(reportPersistence.loadFinalizedReports()).thenReturn(finalizedReports);

    final Task<CrashlyticsReport> successfulTask = Tasks.forResult(mockReport1);
    final Task<CrashlyticsReport> failedTask = Tasks.forException(new Exception("fail"));

    when(reportSender.sendReport(mockReport1)).thenReturn(successfulTask);
    when(reportSender.sendReport(mockReport2)).thenReturn(failedTask);

    reportManager.onSendReports(appSettings);

    verify(reportSender).sendReport(mockReport1);
    verify(reportSender).sendReport(mockReport2);

    verify(reportPersistence).deleteFinalizedReport(sessionId1);
    verify(reportPersistence, never()).deleteFinalizedReport(sessionId2);
  }

  private CrashlyticsReport mockReport(String sessionId, String orgId) {
    final CrashlyticsReport mockReport = mock(CrashlyticsReport.class);
    final CrashlyticsReport.Session mockSession = mock(CrashlyticsReport.Session.class);
    when(mockSession.getIdentifier()).thenReturn(sessionId);
    when(mockReport.getSession()).thenReturn(mockSession);
    when(mockReport.withOrganizationId(orgId)).thenReturn(mockReport);
    return mockReport;
  }
}
