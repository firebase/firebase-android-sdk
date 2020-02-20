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
import com.google.firebase.crashlytics.core.UserMetadata;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.CustomAttribute;
import com.google.firebase.crashlytics.internal.model.ImmutableList;
import com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FirebaseCrashlyticsReportManagerTest {

  @Mock private CrashlyticsReportDataCapture dataCapture;
  @Mock private CrashlyticsReportPersistence reportPersistence;
  @Mock private DataTransportCrashlyticsReportSender reportSender;
  @Mock private LogFileManager logFileManager;
  @Mock private UserMetadata reportMetadata;
  @Mock private CurrentTimeProvider mockCurrentTimeProvider;
  @Mock private CrashlyticsReport mockReport;
  @Mock private CrashlyticsReport.Session.Event mockEvent;
  @Mock private CrashlyticsReport.Session.Event.Builder mockEventBuilder;
  @Mock private CrashlyticsReport.Session.Event.Application mockEventApp;
  @Mock private CrashlyticsReport.Session.Event.Application.Builder mockEventAppBuilder;
  @Mock private Exception mockException;
  @Mock private Thread mockThread;

  private FirebaseCrashlyticsReportManager reportManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    reportManager =
        new FirebaseCrashlyticsReportManager(
            dataCapture,
            reportPersistence,
            reportSender,
            logFileManager,
            reportMetadata,
            mockCurrentTimeProvider,
            Runnable::run);
  }

  @Test
  public void testOnSessionBegin_persistsReportForSessionId() {
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(timestamp);
    final long timestampSeconds = timestamp / 1000;
    when(dataCapture.captureReportData(anyString(), anyLong())).thenReturn(mockReport);

    reportManager.onBeginSession(sessionId);

    verify(dataCapture).captureReportData(sessionId, timestampSeconds);
    verify(reportPersistence).persistReport(mockReport);
  }

  @Test
  public void testOnFatalEvent_persistsHighPriorityEventForSessionId() {
    final String eventType = "crash";
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    final long timestampSeconds = timestamp / 1000;

    mockEventInteractions(timestamp);

    reportManager.onBeginSession(sessionId);
    reportManager.onFatalEvent(mockException, mockThread);

    verify(dataCapture)
        .captureEventData(mockException, mockThread, eventType, timestampSeconds, 4, 8, true);
    verify(reportPersistence).persistEvent(mockEvent, sessionId, true);
  }

  @Test
  public void testOnNonFatalEvent_persistsNormalPriorityEventForSessionId() {
    final String eventType = "error";
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    final long timestampSeconds = timestamp / 1000;

    mockEventInteractions(timestamp);

    reportManager.onBeginSession(sessionId);
    reportManager.onNonFatalEvent(mockException, mockThread);

    verify(dataCapture)
        .captureEventData(mockException, mockThread, eventType, timestampSeconds, 4, 8, false);
    verify(reportPersistence).persistEvent(mockEvent, sessionId, false);
  }

  @Test
  public void testOnNonFatalEvent_addsLogsToEvent() {
    mockEventInteractions(System.currentTimeMillis());

    final String testLog = "test\nlog";

    when(logFileManager.getLogString()).thenReturn(testLog);

    reportManager.onBeginSession("testSessionId");
    reportManager.onNonFatalEvent(mockException, mockThread);

    verify(mockEventBuilder)
        .setLog(CrashlyticsReport.Session.Event.Log.builder().setContent(testLog).build());
    verify(mockEventBuilder).build();
    verify(logFileManager).clearLog();
  }

  @Test
  public void testOnNonFatalEvent_addsNoLogsToEventWhenNoneAvailable() {
    mockEventInteractions(System.currentTimeMillis());
    when(logFileManager.getLogString()).thenReturn(null);

    reportManager.onBeginSession("testSessionId");
    reportManager.onNonFatalEvent(mockException, mockThread);

    verify(mockEventBuilder, never()).setLog(any(CrashlyticsReport.Session.Event.Log.class));
    verify(mockEventBuilder).build();
    verify(logFileManager).clearLog();
  }

  @Test
  public void testOnFatalEvent_addsLogsToEvent() {
    mockEventInteractions(System.currentTimeMillis());

    final String testLog = "test\nlog";

    when(logFileManager.getLogString()).thenReturn(testLog);

    reportManager.onBeginSession("testSessionId");
    reportManager.onFatalEvent(mockException, mockThread);

    verify(mockEventBuilder)
        .setLog(CrashlyticsReport.Session.Event.Log.builder().setContent(testLog).build());
    verify(mockEventBuilder).build();
    verify(logFileManager).clearLog();
  }

  @Test
  public void testOnFatalEvent_addsNoLogsToEventWhenNoneAvailable() {
    mockEventInteractions(System.currentTimeMillis());

    when(logFileManager.getLogString()).thenReturn(null);

    reportManager.onBeginSession("testSessionId");
    reportManager.onFatalEvent(mockException, mockThread);

    verify(mockEventBuilder, never()).setLog(any(CrashlyticsReport.Session.Event.Log.class));
    verify(mockEventBuilder).build();
    verify(logFileManager).clearLog();
  }

  @Test
  public void testOnNonFatalEvent_addsSortedKeysToEvent() {
    mockEventInteractions(System.currentTimeMillis());

    final String testKey1 = "testKey1";
    final String testValue1 = "testValue1";
    final String testKey2 = "testKey2";
    final String testValue2 = "testValue2";

    final Map<String, String> attributes = new HashMap<>();
    attributes.put(testKey1, testValue1);
    attributes.put(testKey2, testValue2);

    final CustomAttribute customAttribute1 =
        CustomAttribute.builder().setKey(testKey1).setValue(testValue1).build();
    final CustomAttribute customAttribute2 =
        CustomAttribute.builder().setKey(testKey2).setValue(testValue2).build();

    final ImmutableList<CustomAttribute> expectedCustomAttributes =
        ImmutableList.from(customAttribute1, customAttribute2);

    when(reportMetadata.getCustomKeys()).thenReturn(attributes);

    reportManager.onBeginSession("testSessionId");
    reportManager.onNonFatalEvent(mockException, mockThread);

    verify(mockEventAppBuilder).setCustomAttributes(expectedCustomAttributes);
    verify(mockEventAppBuilder).build();
    verify(mockEventBuilder).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager).clearLog();
  }

  @Test
  public void testOnNonFatalEvent_addsNoKeysToEventWhenNoneAvailable() {
    mockEventInteractions(System.currentTimeMillis());

    final Map<String, String> attributes = Collections.emptyMap();

    when(reportMetadata.getCustomKeys()).thenReturn(attributes);

    reportManager.onBeginSession("testSessionId");
    reportManager.onNonFatalEvent(mockException, mockThread);

    verify(mockEventAppBuilder, never()).setCustomAttributes(any());
    verify(mockEventAppBuilder, never()).build();
    verify(mockEventBuilder, never()).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager).clearLog();
  }

  @Test
  public void testOnFatalEvent_addsSortedKeysToEvent() {
    mockEventInteractions(System.currentTimeMillis());

    final String testKey1 = "testKey1";
    final String testValue1 = "testValue1";
    final String testKey2 = "testKey2";
    final String testValue2 = "testValue2";

    final Map<String, String> attributes = new HashMap<>();
    attributes.put(testKey1, testValue1);
    attributes.put(testKey2, testValue2);

    final CustomAttribute customAttribute1 =
        CustomAttribute.builder().setKey(testKey1).setValue(testValue1).build();
    final CustomAttribute customAttribute2 =
        CustomAttribute.builder().setKey(testKey2).setValue(testValue2).build();

    final ImmutableList<CustomAttribute> expectedCustomAttributes =
        ImmutableList.from(customAttribute1, customAttribute2);

    when(reportMetadata.getCustomKeys()).thenReturn(attributes);

    reportManager.onBeginSession("testSessionId");
    reportManager.onFatalEvent(mockException, mockThread);

    verify(mockEventAppBuilder).setCustomAttributes(expectedCustomAttributes);
    verify(mockEventAppBuilder).build();
    verify(mockEventBuilder).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager).clearLog();
  }

  @Test
  public void testOnFatalEvent_addsNoKeysToEventWhenNoneAvailable() {
    mockEventInteractions(System.currentTimeMillis());

    final Map<String, String> attributes = Collections.emptyMap();

    when(reportMetadata.getCustomKeys()).thenReturn(attributes);

    reportManager.onBeginSession("testSessionId");
    reportManager.onFatalEvent(mockException, mockThread);

    verify(mockEventAppBuilder, never()).setCustomAttributes(any());
    verify(mockEventAppBuilder, never()).build();
    verify(mockEventBuilder, never()).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager).clearLog();
  }

  @Test
  public void testOnEndSession_addsUserIdToReport() {
    final String userId = "testUser";
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(timestamp);
    when(dataCapture.captureReportData(anyString(), anyLong())).thenReturn(mockReport);
    when(reportMetadata.getUserId()).thenReturn(userId);

    reportManager.onBeginSession(sessionId);
    reportManager.onEndSession();

    verify(reportPersistence).persistUserIdForSession(userId, sessionId);
  }

  @Test
  public void onLog_writesToLogFileManager() {
    long timestamp = System.currentTimeMillis();
    String log = "this is a log";

    reportManager.onLog(timestamp, log);

    verify(logFileManager).writeToLog(timestamp, log);
  }

  @Test
  public void onCustomKey_writesToReportMetadata() {
    final String key = "key";
    final String value = "value";

    reportManager.onCustomKey(key, value);

    verify(reportMetadata).setCustomKey(key, value);
  }

  @Test
  public void onUserId_writesUserToReportMetadata() {
    final String userId = "testUser";

    reportManager.onUserId(userId);

    verify(reportMetadata).setUserId(userId);
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
  public void onReportSend_successfulReportsAreDeleted() {
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

  private void mockEventInteractions(long timestamp) {
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(timestamp);
    when(mockEvent.toBuilder()).thenReturn(mockEventBuilder);
    when(mockEventBuilder.build()).thenReturn(mockEvent);
    when(mockEvent.getApp()).thenReturn(mockEventApp);
    when(mockEventApp.toBuilder()).thenReturn(mockEventAppBuilder);
    when(mockEventAppBuilder.setCustomAttributes(any())).thenReturn(mockEventAppBuilder);
    when(mockEventAppBuilder.build()).thenReturn(mockEventApp);
    when(dataCapture.captureEventData(
            any(Throwable.class),
            any(Thread.class),
            anyString(),
            anyLong(),
            anyInt(),
            anyInt(),
            anyBoolean()))
        .thenReturn(mockEvent);
  }

  private static CrashlyticsReport mockReport(String sessionId, String orgId) {
    final CrashlyticsReport mockReport = mock(CrashlyticsReport.class);
    final CrashlyticsReport.Session mockSession = mock(CrashlyticsReport.Session.class);
    when(mockSession.getIdentifier()).thenReturn(sessionId);
    when(mockReport.getSession()).thenReturn(mockSession);
    when(mockReport.withOrganizationId(orgId)).thenReturn(mockReport);
    return mockReport;
  }
}
