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
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.common.SessionReportingCoordinator.SendReportPredicate;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.CustomAttribute;
import com.google.firebase.crashlytics.internal.model.ImmutableList;
import com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SessionReportingCoordinatorTest {

  @Mock private CrashlyticsReportDataCapture dataCapture;
  @Mock private CrashlyticsReportPersistence reportPersistence;
  @Mock private DataTransportCrashlyticsReportSender reportSender;
  @Mock private LogFileManager logFileManager;
  @Mock private UserMetadata reportMetadata;
  @Mock private SendReportPredicate mockSendReportPredicate;
  @Mock private CrashlyticsReport mockReport;
  @Mock private CrashlyticsReport.Session.Event mockEvent;
  @Mock private CrashlyticsReport.Session.Event.Builder mockEventBuilder;
  @Mock private CrashlyticsReport.Session.Event.Application mockEventApp;
  @Mock private CrashlyticsReport.Session.Event.Application.Builder mockEventAppBuilder;
  @Mock private Exception mockException;
  @Mock private Thread mockThread;

  private SessionReportingCoordinator reportManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    reportManager =
        new SessionReportingCoordinator(
            dataCapture, reportPersistence, reportSender, logFileManager, reportMetadata);
  }

  @Test
  public void testOnSessionBegin_persistsReportForSessionId() {
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    when(dataCapture.captureReportData(anyString(), anyLong())).thenReturn(mockReport);

    reportManager.onBeginSession(sessionId, timestamp);

    verify(dataCapture).captureReportData(sessionId, timestamp);
    verify(reportPersistence).persistReport(mockReport);
  }

  @Test
  public void testFatalEvent_persistsHighPriorityEventForSessionId() {
    final String eventType = "crash";
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    reportManager.onBeginSession(sessionId, timestamp);
    reportManager.persistFatalEvent(mockException, mockThread, timestamp);

    verify(dataCapture)
        .captureEventData(mockException, mockThread, eventType, timestamp, 4, 8, true);
    verify(reportPersistence).persistEvent(mockEvent, sessionId, true);
  }

  @Test
  public void testNonFatalEvent_persistsNormalPriorityEventForSessionId() {
    final String eventType = "error";
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    reportManager.onBeginSession(sessionId, timestamp);
    reportManager.persistNonFatalEvent(mockException, mockThread, timestamp);

    verify(dataCapture)
        .captureEventData(mockException, mockThread, eventType, timestamp, 4, 8, false);
    verify(reportPersistence).persistEvent(mockEvent, sessionId, false);
  }

  @Test
  public void testNonFatalEvent_addsLogsToEvent() {
    long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String testLog = "test\nlog";

    when(logFileManager.getLogString()).thenReturn(testLog);

    reportManager.onBeginSession("testSessionId", timestamp);
    reportManager.persistNonFatalEvent(mockException, mockThread, timestamp);

    verify(mockEventBuilder)
        .setLog(CrashlyticsReport.Session.Event.Log.builder().setContent(testLog).build());
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsNoLogsToEventWhenNoneAvailable() {
    long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    when(logFileManager.getLogString()).thenReturn(null);

    reportManager.onBeginSession("testSessionId", timestamp);
    reportManager.persistNonFatalEvent(mockException, mockThread, timestamp);

    verify(mockEventBuilder, never()).setLog(any(CrashlyticsReport.Session.Event.Log.class));
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsLogsToEvent() {
    long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String testLog = "test\nlog";

    when(logFileManager.getLogString()).thenReturn(testLog);

    reportManager.onBeginSession("testSessionId", timestamp);
    reportManager.persistFatalEvent(mockException, mockThread, timestamp);

    verify(mockEventBuilder)
        .setLog(CrashlyticsReport.Session.Event.Log.builder().setContent(testLog).build());
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsNoLogsToEventWhenNoneAvailable() {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    when(logFileManager.getLogString()).thenReturn(null);

    reportManager.onBeginSession("testSessionId", timestamp);
    reportManager.persistFatalEvent(mockException, mockThread, timestamp);

    verify(mockEventBuilder, never()).setLog(any(CrashlyticsReport.Session.Event.Log.class));
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsSortedKeysToEvent() {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

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

    reportManager.onBeginSession("testSessionId", timestamp);
    reportManager.persistNonFatalEvent(mockException, mockThread, timestamp);

    verify(mockEventAppBuilder).setCustomAttributes(expectedCustomAttributes);
    verify(mockEventAppBuilder).build();
    verify(mockEventBuilder).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsNoKeysToEventWhenNoneAvailable() {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final Map<String, String> attributes = Collections.emptyMap();

    when(reportMetadata.getCustomKeys()).thenReturn(attributes);

    reportManager.onBeginSession("testSessionId", timestamp);
    reportManager.persistNonFatalEvent(mockException, mockThread, timestamp);

    verify(mockEventAppBuilder, never()).setCustomAttributes(any());
    verify(mockEventAppBuilder, never()).build();
    verify(mockEventBuilder, never()).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsSortedKeysToEvent() {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

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

    reportManager.onBeginSession("testSessionId", timestamp);
    reportManager.persistFatalEvent(mockException, mockThread, timestamp);

    verify(mockEventAppBuilder).setCustomAttributes(expectedCustomAttributes);
    verify(mockEventAppBuilder).build();
    verify(mockEventBuilder).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsNoKeysToEventWhenNoneAvailable() {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final Map<String, String> attributes = Collections.emptyMap();

    when(reportMetadata.getCustomKeys()).thenReturn(attributes);

    reportManager.onBeginSession("testSessionId", timestamp);
    reportManager.persistFatalEvent(mockException, mockThread, timestamp);

    verify(mockEventAppBuilder, never()).setCustomAttributes(any());
    verify(mockEventAppBuilder, never()).build();
    verify(mockEventBuilder, never()).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
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
  public void testFinalizeSessionWithNativeEvent_finalizesSession() {
    // FIXME:
  }

  @Test
  public void onSessionsFinalize_finalizesReports() {
    final String sessionId = "testSessionId";
    reportManager.onBeginSession(sessionId, System.currentTimeMillis());
    final long endedAt = System.currentTimeMillis();
    reportManager.finalizeSessions(endedAt);

    verify(reportPersistence).finalizeReports(sessionId, endedAt);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void onReportSend_successfulReportsAreDeleted() {
    when(mockSendReportPredicate.shouldSendViaDataTransport()).thenReturn(true);
    final String orgId = "testOrgId";
    final String sessionId1 = "sessionId1";
    final String sessionId2 = "sessionId2";

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

    reportManager.sendReports(orgId, Runnable::run, mockSendReportPredicate);

    verify(reportSender).sendReport(mockReport1);
    verify(reportSender).sendReport(mockReport2);

    verify(reportPersistence).deleteFinalizedReport(sessionId1);
    verify(reportPersistence, never()).deleteFinalizedReport(sessionId2);
  }

  @Test
  public void onReportSend_reportsAreDeletedWithoutBeingSent_whenSendPredicateIsFalse() {
    when(mockSendReportPredicate.shouldSendViaDataTransport()).thenReturn(false);
    reportManager.sendReports("testOrgId", Runnable::run, mockSendReportPredicate);

    verify(reportPersistence).deleteAllReports();
    verify(reportPersistence, never()).loadFinalizedReports();
    verify(reportPersistence, never()).deleteFinalizedReport(anyString());
    verifyZeroInteractions(reportSender);
  }

  @Test
  public void testPersistUserIdForCurrentSession_persistsCurrentUserIdForCurrentSessionId() {
    final String currentSessionId = "currentSessionId";
    final String userId = "testUserId";
    final long timestamp = System.currentTimeMillis();
    when(dataCapture.captureReportData(anyString(), anyLong())).thenReturn(mockReport);
    when(reportMetadata.getUserId()).thenReturn(userId);

    reportManager.onBeginSession(currentSessionId, timestamp);
    reportManager.persistUserId();

    verify(reportPersistence).persistUserIdForSession(userId, currentSessionId);
  }

  @Test
  public void testRemoveAllReports_deletesPersistedReports() {
    reportManager.removeAllReports();

    verify(reportPersistence).deleteAllReports();
  }

  private void mockEventInteractions() {
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
