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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers;
import com.google.firebase.crashlytics.internal.metadata.EventMetadata;
import com.google.firebase.crashlytics.internal.metadata.LogFileManager;
import com.google.firebase.crashlytics.internal.metadata.RolloutAssignment;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.CustomAttribute;
import com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SessionReportingCoordinatorTest extends CrashlyticsTestCase {

  private static final String TEST_SESSION_ID = "testSessionId";

  @Mock private CrashlyticsReportDataCapture dataCapture;
  @Mock private CrashlyticsReportPersistence reportPersistence;
  @Mock private DataTransportCrashlyticsReportSender reportSender;
  @Mock private LogFileManager logFileManager;
  @Mock private IdManager idManager;
  @Mock private CrashlyticsReport mockReport;
  @Mock private CrashlyticsReport.Session.Event mockEvent;
  @Mock private CrashlyticsReport.Session.Event.Builder mockEventBuilder;
  @Mock private CrashlyticsReport.Session.Event.Application mockEventApp;
  @Mock private CrashlyticsReport.Session.Event.Application.Builder mockEventAppBuilder;
  @Mock private Exception mockException;
  @Mock private Thread mockThread;

  private UserMetadata reportMetadata;
  private SessionReportingCoordinator reportingCoordinator;
  private AutoCloseable mocks;

  private CrashlyticsWorkers crashlyticsWorkers =
      new CrashlyticsWorkers(TestOnlyExecutors.background(), TestOnlyExecutors.blocking());

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);

    FileStore testFileStore = new FileStore(getContext());
    reportMetadata = new UserMetadata(TEST_SESSION_ID, testFileStore, crashlyticsWorkers);

    reportingCoordinator =
        new SessionReportingCoordinator(
            dataCapture,
            reportPersistence,
            reportSender,
            logFileManager,
            reportMetadata,
            idManager,
            crashlyticsWorkers);
  }

  @After
  public void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  public void testOnSessionBegin_persistsReportForSessionId() {
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    when(dataCapture.captureReportData(anyString(), anyLong())).thenReturn(mockReport);

    reportingCoordinator.onBeginSession(sessionId, timestamp);

    verify(dataCapture).captureReportData(sessionId, timestamp);
    verify(reportPersistence).persistReport(mockReport);
  }

  @Test
  public void testFatalEvent_persistsHighPriorityEventWithAllThreadsForSessionId() {
    final String eventType = "crash";
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    final boolean expectedAllThreads = true;
    final boolean expectedHighPriority = true;

    verify(dataCapture)
        .captureEventData(
            mockException, mockThread, eventType, timestamp, 4, 8, expectedAllThreads);
    verify(reportPersistence).persistEvent(mockEvent, sessionId, expectedHighPriority);
  }

  @Test
  public void testNonFatalEvent_persistsNormalPriorityEventWithoutAllThreadsForSessionId()
      throws Exception {
    final String eventType = "error";
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    final boolean expectedAllThreads = false;
    final boolean expectedHighPriority = false;

    verify(dataCapture)
        .captureEventData(
            mockException, mockThread, eventType, timestamp, 4, 8, expectedAllThreads);
    verify(reportPersistence).persistEvent(mockEvent, sessionId, expectedHighPriority);
  }

  @Test
  public void testNonFatalEvent_addsLogsToEvent() throws Exception {
    long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";
    final String testLog = "test\nlog";

    when(logFileManager.getLogString()).thenReturn(testLog);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    verify(mockEventBuilder)
        .setLog(CrashlyticsReport.Session.Event.Log.builder().setContent(testLog).build());
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsNoLogsToEventWhenNoneAvailable() throws Exception {
    long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";

    when(logFileManager.getLogString()).thenReturn(null);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    verify(mockEventBuilder, never()).setLog(any(CrashlyticsReport.Session.Event.Log.class));
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsLogsToEvent() {
    long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";
    final String testLog = "test\nlog";

    when(logFileManager.getLogString()).thenReturn(testLog);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    verify(mockEventBuilder)
        .setLog(CrashlyticsReport.Session.Event.Log.builder().setContent(testLog).build());
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsNoLogsToEventWhenNoneAvailable() {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";

    when(logFileManager.getLogString()).thenReturn(null);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    verify(mockEventBuilder, never()).setLog(any(CrashlyticsReport.Session.Event.Log.class));
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsSortedKeysToEvent() throws Exception {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";

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

    final List<CustomAttribute> expectedCustomAttributes = new ArrayList<>();
    expectedCustomAttributes.add(customAttribute1);
    expectedCustomAttributes.add(customAttribute2);

    addCustomKeysToUserMetadata(attributes);
    addInternalKeysToUserMetadata(attributes);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    verify(mockEventAppBuilder).setCustomAttributes(expectedCustomAttributes);
    verify(mockEventAppBuilder).setInternalKeys(expectedCustomAttributes);
    verify(mockEventAppBuilder).build();
    verify(mockEventBuilder).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsNoKeysToEventWhenNoneAvailable() throws Exception {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    verify(mockEventAppBuilder, never()).setCustomAttributes(anyList());
    verify(mockEventAppBuilder, never()).build();
    verify(mockEventBuilder, never()).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsUserInfoKeysToEventWhenAvailable() throws Exception {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";

    final String testKey1 = "testKey1";
    final String testValue1 = "testValue1";

    final Map<String, String> userInfo = new HashMap<>();
    userInfo.put(testKey1, testValue1);

    final CustomAttribute customAttribute1 =
        CustomAttribute.builder().setKey(testKey1).setValue(testValue1).build();

    final List<CustomAttribute> expectedCustomAttributes = new ArrayList<>();
    expectedCustomAttributes.add(customAttribute1);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp, userInfo));

    crashlyticsWorkers.diskWrite.await();

    verify(mockEventAppBuilder).setCustomAttributes(expectedCustomAttributes);
    verify(mockEventAppBuilder).build();
    verify(mockEventBuilder).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_mergesUserInfoKeysWithCustomKeys() throws Exception {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";

    final String testKey1 = "testKey1";
    final String testValue1 = "testValue1";

    final String testKey2 = "testKey2";
    final String testValue2 = "testValue2";

    final Map<String, String> attributes = new HashMap<>();
    attributes.put(testKey1, testValue1);
    attributes.put(testKey2, testValue2);

    addCustomKeysToUserMetadata(attributes);

    final String testValue1UserInfo = "testValue1";
    final String testKey3 = "testKey3";
    final String testValue3 = "testValue3";

    final Map<String, String> userInfo = new HashMap<>();
    userInfo.put(testKey1, testValue1UserInfo);
    userInfo.put(testKey3, testValue3);

    final CustomAttribute customAttribute1 =
        CustomAttribute.builder().setKey(testKey1).setValue(testValue1UserInfo).build();
    final CustomAttribute customAttribute2 =
        CustomAttribute.builder().setKey(testKey2).setValue(testValue2).build();
    final CustomAttribute customAttribute3 =
        CustomAttribute.builder().setKey(testKey3).setValue(testValue3).build();

    final List<CustomAttribute> expectedCustomAttributes =
        List.of(customAttribute1, customAttribute2, customAttribute3);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp, userInfo));

    crashlyticsWorkers.diskWrite.await();

    verify(mockEventAppBuilder).setCustomAttributes(expectedCustomAttributes);
    verify(mockEventAppBuilder).build();
    verify(mockEventBuilder).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addRolloutsEvent() throws Exception {
    long timestamp = System.currentTimeMillis();
    String sessionId = "testSessionId";
    mockEventInteractions();

    List<RolloutAssignment> rolloutsState = new ArrayList<>();
    rolloutsState.add(fakeRolloutAssignment());
    reportMetadata.updateRolloutsState(rolloutsState);
    crashlyticsWorkers.diskWrite.await();

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    verify(mockEventAppBuilder, never()).setCustomAttributes(anyList());
    verify(mockEventAppBuilder, never()).build();
    verify(mockEventBuilder, never()).setApp(mockEventApp);
    // first build for custom keys
    // second build for rollouts
    verify(mockEventBuilder, times(2)).build();
  }

  @Test
  public void testFatalEvent_addsSortedCustomKeysToEvent() throws Exception {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";

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

    final List<CustomAttribute> expectedCustomAttributes = new ArrayList<>();
    expectedCustomAttributes.add(customAttribute1);
    expectedCustomAttributes.add(customAttribute2);

    addCustomKeysToUserMetadata(attributes);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    verify(mockEventAppBuilder).setCustomAttributes(expectedCustomAttributes);
    verify(mockEventAppBuilder).build();
    verify(mockEventBuilder).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsSortedInternalKeysToEvent() throws Exception {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";

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

    final List<CustomAttribute> expectedCustomAttributes = new ArrayList<>();
    expectedCustomAttributes.add(customAttribute1);
    expectedCustomAttributes.add(customAttribute2);

    addInternalKeysToUserMetadata(attributes);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    verify(mockEventAppBuilder).setInternalKeys(expectedCustomAttributes);
    verify(mockEventAppBuilder).build();
    verify(mockEventBuilder).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsNoKeysToEventWhenNoneAvailable() {
    final long timestamp = System.currentTimeMillis();

    mockEventInteractions();

    final String sessionId = "testSessionId";

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    verify(mockEventAppBuilder, never()).setCustomAttributes(anyList());
    verify(mockEventAppBuilder, never()).build();
    verify(mockEventBuilder, never()).setApp(mockEventApp);
    verify(mockEventBuilder).build();
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addRolloutsToEvent() throws Exception {
    long timestamp = System.currentTimeMillis();
    String sessionId = "testSessionId";
    mockEventInteractions();

    List<RolloutAssignment> rolloutsState = new ArrayList<>();
    rolloutsState.add(fakeRolloutAssignment());

    reportMetadata.updateRolloutsState(rolloutsState);
    crashlyticsWorkers.diskWrite.await();

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    verify(mockEventAppBuilder, never()).setCustomAttributes(anyList());
    verify(mockEventAppBuilder, never()).build();
    verify(mockEventBuilder, never()).setApp(mockEventApp);
    // first build for custom keys
    // second build for rollouts
    verify(mockEventBuilder, times(2)).build();
  }

  @Test
  public void testFinalizeSessionWithNativeEvent_createsCrashlyticsReportWithNativePayload() {
    byte[] testBytes = {0, 2, 20, 10};
    String byteBackedSessionName = "byte";
    BytesBackedNativeSessionFile byteSession =
        new BytesBackedNativeSessionFile(byteBackedSessionName, "not_applicable", testBytes);
    reportingCoordinator.finalizeSessionWithNativeEvent(
        "id", Collections.singletonList(byteSession), null);

    ArgumentCaptor<CrashlyticsReport.FilesPayload> filesPayload =
        ArgumentCaptor.forClass(CrashlyticsReport.FilesPayload.class);
    verify(reportPersistence)
        .finalizeSessionWithNativeEvent(eq("id"), filesPayload.capture(), any());
    CrashlyticsReport.FilesPayload ndkPayloadFinalized = filesPayload.getValue();
    assertEquals(1, ndkPayloadFinalized.getFiles().size());

    assertArrayEquals(
        testBytes,
        TestUtils.inflateGzipToRawBytes(ndkPayloadFinalized.getFiles().get(0).getContents()));
    assertEquals(byteBackedSessionName, ndkPayloadFinalized.getFiles().get(0).getFilename());
  }

  @Test
  public void onSessionsFinalize_finalizesReports() {
    final String sessionId = "testSessionId";
    reportingCoordinator.onBeginSession(sessionId, System.currentTimeMillis());
    final long endedAt = System.currentTimeMillis();
    reportingCoordinator.finalizeSessions(endedAt, sessionId);

    verify(reportPersistence).finalizeReports(sessionId, endedAt);
  }

  @Test
  public void onReportSend_successfulReportsAreDeleted() {
    final String sessionId1 = "sessionId1";
    final String sessionId2 = "sessionId2";

    final List<CrashlyticsReportWithSessionId> finalizedReports = new ArrayList<>();
    final CrashlyticsReportWithSessionId mockReport1 = mockReportWithSessionId(sessionId1);
    final CrashlyticsReportWithSessionId mockReport2 = mockReportWithSessionId(sessionId2);
    finalizedReports.add(mockReport1);
    finalizedReports.add(mockReport2);

    when(reportPersistence.loadFinalizedReports()).thenReturn(finalizedReports);

    final Task<CrashlyticsReportWithSessionId> successfulTask = Tasks.forResult(mockReport1);
    final Task<CrashlyticsReportWithSessionId> failedTask =
        Tasks.forException(new Exception("fail"));

    when(reportSender.enqueueReport(mockReport1, false)).thenReturn(successfulTask);
    when(reportSender.enqueueReport(mockReport2, false)).thenReturn(failedTask);

    when(idManager.fetchTrueFid(anyBoolean()))
        .thenReturn(new FirebaseInstallationId("fid", "authToken"));
    reportingCoordinator.sendReports(Runnable::run);

    verify(reportSender).enqueueReport(mockReport1, false);
    verify(reportSender).enqueueReport(mockReport2, false);
  }

  @Test
  public void testHasReportsToSend() {
    when(reportPersistence.hasFinalizedReports()).thenReturn(true);
    assertTrue(reportingCoordinator.hasReportsToSend());
  }

  @Test
  public void testHasReportsToSend_noReports() {
    when(reportPersistence.hasFinalizedReports()).thenReturn(false);
    assertFalse(reportingCoordinator.hasReportsToSend());
  }

  @Test
  public void testRemoveAllReports_deletesPersistedReports() {
    reportingCoordinator.removeAllReports();

    verify(reportPersistence).deleteAllReports();
  }

  private void addCustomKeysToUserMetadata(Map<String, String> customKeys) throws Exception {
    reportMetadata.setCustomKeys(customKeys);
    for (Map.Entry<String, String> entry : customKeys.entrySet()) {
      reportMetadata.setInternalKey(entry.getKey(), entry.getValue());
    }
    crashlyticsWorkers.diskWrite.await();
  }

  private void addInternalKeysToUserMetadata(Map<String, String> internalKeys) throws Exception {
    for (Map.Entry<String, String> entry : internalKeys.entrySet()) {
      reportMetadata.setInternalKey(entry.getKey(), entry.getValue());
    }
    crashlyticsWorkers.diskWrite.await();
  }

  private void mockEventInteractions() {
    when(mockEvent.toBuilder()).thenReturn(mockEventBuilder);
    when(mockEventBuilder.build()).thenReturn(mockEvent);
    when(mockEvent.getApp()).thenReturn(mockEventApp);
    when(mockEventApp.toBuilder()).thenReturn(mockEventAppBuilder);
    when(mockEventAppBuilder.setCustomAttributes(anyList())).thenReturn(mockEventAppBuilder);
    when(mockEventAppBuilder.setInternalKeys(anyList())).thenReturn(mockEventAppBuilder);
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

  private static CrashlyticsReport mockReport(String sessionId) {
    final CrashlyticsReport mockReport = mock(CrashlyticsReport.class);
    final CrashlyticsReport.Session mockSession = mock(CrashlyticsReport.Session.class);
    when(mockSession.getIdentifier()).thenReturn(sessionId);
    when(mockReport.getSession()).thenReturn(mockSession);
    when(mockReport.withFirebaseInstallationId(anyString())).thenReturn(mockReport);
    when(mockReport.withFirebaseAuthenticationToken(anyString())).thenReturn(mockReport);
    return mockReport;
  }

  private static CrashlyticsReportWithSessionId mockReportWithSessionId(String sessionId) {
    return CrashlyticsReportWithSessionId.create(
        mockReport(sessionId), sessionId, new File("fake"));
  }

  private static RolloutAssignment fakeRolloutAssignment() {
    return RolloutAssignment.create("rollout_1", "my_feature", "false", "enabled", 2);
  }
}
