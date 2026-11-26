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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Parcel;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
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
import com.google.firebase.crashlytics.internal.persistence.ResumableUploadMetadataStore;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
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
  @Mock private Exception mockException;
  @Mock private Thread mockThread;

  private final CrashlyticsReport testReport = makeTestReport(TEST_SESSION_ID);
  private final CrashlyticsReport.Session.Event testEvent = makeTestEvent();
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

    ResumableUploadMetadataStore resumableUploadMetadataStore =
        new ResumableUploadMetadataStore(getContext(), testFileStore);

    reportingCoordinator =
        new SessionReportingCoordinator(
            dataCapture,
            reportPersistence,
            reportSender,
            logFileManager,
            reportMetadata,
            idManager,
            crashlyticsWorkers,
            resumableUploadMetadataStore);
  }

  @After
  public void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  public void testOnSessionBegin_persistsReportForSessionId() {
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();
    when(dataCapture.captureReportData(anyString(), anyLong())).thenReturn(testReport);

    reportingCoordinator.onBeginSession(sessionId, timestamp);

    verify(dataCapture).captureReportData(sessionId, timestamp);
    verify(reportPersistence).persistReport(testReport);
  }

  @Test
  public void testFatalEvent_persistsHighPriorityEventWithAllThreadsForSessionId() {
    final String eventType = "crash";
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    final boolean expectedAllThreads = true;
    final boolean expectedHighPriority = true;

    verify(dataCapture)
        .captureEventData(
            mockException, mockThread, eventType, timestamp, 4, 8, expectedAllThreads);
    verify(reportPersistence).persistEvent(testEvent, sessionId, expectedHighPriority);
  }

  @Test
  public void testNonFatalEvent_persistsNormalPriorityEventWithoutAllThreadsForSessionId()
      throws Exception {
    final String eventType = "error";
    final String sessionId = "testSessionId";
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    final boolean expectedAllThreads = false;
    final boolean expectedHighPriority = false;

    verify(dataCapture)
        .captureEventData(
            mockException, mockThread, eventType, timestamp, 4, 8, expectedAllThreads);
    verify(reportPersistence).persistEvent(testEvent, sessionId, expectedHighPriority);
  }

  @Test
  public void testNonFatalEvent_addsLogsToEvent() throws Exception {
    long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

    final String sessionId = "testSessionId";
    final String testLog = "test\nlog";

    when(logFileManager.getLogString()).thenReturn(testLog);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ false);
    assertEquals(
        CrashlyticsReport.Session.Event.Log.builder().setContent(testLog).build(),
        persistedEvent.getLog());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsNoLogsToEventWhenNoneAvailable() throws Exception {
    long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

    final String sessionId = "testSessionId";

    when(logFileManager.getLogString()).thenReturn(null);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ false);
    assertNull(persistedEvent.getLog());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsLogsToEvent() {
    long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

    final String sessionId = "testSessionId";
    final String testLog = "test\nlog";

    when(logFileManager.getLogString()).thenReturn(testLog);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ true);
    assertEquals(
        CrashlyticsReport.Session.Event.Log.builder().setContent(testLog).build(),
        persistedEvent.getLog());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsNoLogsToEventWhenNoneAvailable() {
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

    final String sessionId = "testSessionId";

    when(logFileManager.getLogString()).thenReturn(null);

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ true);
    assertNull(persistedEvent.getLog());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsSortedKeysToEvent() throws Exception {
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

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

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ false);
    assertEquals(expectedCustomAttributes, persistedEvent.getApp().getCustomAttributes());
    assertEquals(expectedCustomAttributes, persistedEvent.getApp().getInternalKeys());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsNoKeysToEventWhenNoneAvailable() throws Exception {
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

    final String sessionId = "testSessionId";

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ false);
    assertNull(persistedEvent.getApp().getCustomAttributes());
    assertNull(persistedEvent.getApp().getInternalKeys());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addsUserInfoKeysToEventWhenAvailable() throws Exception {
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

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

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ false);
    assertEquals(expectedCustomAttributes, persistedEvent.getApp().getCustomAttributes());
    assertEquals(Collections.emptyList(), persistedEvent.getApp().getInternalKeys());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_mergesUserInfoKeysWithCustomKeys() throws Exception {
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

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

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ false);
    assertEquals(expectedCustomAttributes, persistedEvent.getApp().getCustomAttributes());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testNonFatalEvent_addRolloutsEvent() throws Exception {
    long timestamp = System.currentTimeMillis();
    String sessionId = "testSessionId";
    stubCaptureEventData();

    List<RolloutAssignment> rolloutsState = new ArrayList<>();
    rolloutsState.add(fakeRolloutAssignment());
    reportMetadata.updateRolloutsState(rolloutsState);
    crashlyticsWorkers.diskWrite.await();

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistNonFatalEvent(
        mockException, mockThread, new EventMetadata(sessionId, timestamp));

    crashlyticsWorkers.diskWrite.await();

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ false);
    assertNull(persistedEvent.getApp().getCustomAttributes());
    assertNull(persistedEvent.getApp().getInternalKeys());
    assertEquals(
        CrashlyticsReport.Session.Event.RolloutsState.builder()
            .setRolloutAssignments(
                Collections.singletonList(fakeRolloutAssignment().toReportProto()))
            .build(),
        persistedEvent.getRollouts());
  }

  @Test
  public void testFatalEvent_addsSortedCustomKeysToEvent() throws Exception {
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

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

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ true);
    assertEquals(expectedCustomAttributes, persistedEvent.getApp().getCustomAttributes());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsSortedInternalKeysToEvent() throws Exception {
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

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

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ true);
    assertEquals(expectedCustomAttributes, persistedEvent.getApp().getInternalKeys());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addsNoKeysToEventWhenNoneAvailable() {
    final long timestamp = System.currentTimeMillis();

    stubCaptureEventData();

    final String sessionId = "testSessionId";

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ true);
    assertNull(persistedEvent.getApp().getCustomAttributes());
    assertNull(persistedEvent.getApp().getInternalKeys());
    verify(logFileManager, never()).clearLog();
  }

  @Test
  public void testFatalEvent_addRolloutsToEvent() throws Exception {
    long timestamp = System.currentTimeMillis();
    String sessionId = "testSessionId";
    stubCaptureEventData();

    List<RolloutAssignment> rolloutsState = new ArrayList<>();
    rolloutsState.add(fakeRolloutAssignment());

    reportMetadata.updateRolloutsState(rolloutsState);
    crashlyticsWorkers.diskWrite.await();

    reportingCoordinator.onBeginSession(sessionId, timestamp);
    reportingCoordinator.persistFatalEvent(mockException, mockThread, sessionId, timestamp);

    CrashlyticsReport.Session.Event persistedEvent =
        capturePersistedEvent(sessionId, /* isHighPriority= */ true);
    assertNull(persistedEvent.getApp().getCustomAttributes());
    assertNull(persistedEvent.getApp().getInternalKeys());
    assertEquals(
        CrashlyticsReport.Session.Event.RolloutsState.builder()
            .setRolloutAssignments(
                Collections.singletonList(fakeRolloutAssignment().toReportProto()))
            .build(),
        persistedEvent.getRollouts());
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
    final CrashlyticsReportWithSessionId report1 = createTestReportWithSessionId(sessionId1);
    final CrashlyticsReportWithSessionId report2 = createTestReportWithSessionId(sessionId2);
    finalizedReports.add(report1);
    finalizedReports.add(report2);

    when(reportPersistence.loadFinalizedReports()).thenReturn(finalizedReports);

    final CrashlyticsReportWithSessionId expectedReport1 =
        CrashlyticsReportWithSessionId.create(
            report1
                .getReport()
                .withFirebaseInstallationId("fid")
                .withFirebaseAuthenticationToken("authToken"),
            sessionId1,
            report1.getReportFile());
    final CrashlyticsReportWithSessionId expectedReport2 =
        CrashlyticsReportWithSessionId.create(
            report2
                .getReport()
                .withFirebaseInstallationId("fid")
                .withFirebaseAuthenticationToken("authToken"),
            sessionId2,
            report2.getReportFile());

    final Task<CrashlyticsReportWithSessionId> successfulTask = Tasks.forResult(expectedReport1);
    final Task<CrashlyticsReportWithSessionId> failedTask =
        Tasks.forException(new Exception("fail"));

    when(reportSender.enqueueReport(expectedReport1, false)).thenReturn(successfulTask);
    when(reportSender.enqueueReport(expectedReport2, false)).thenReturn(failedTask);

    when(idManager.fetchTrueFid(anyBoolean()))
        .thenReturn(new FirebaseInstallationId("fid", "authToken"));
    reportingCoordinator.sendReports(Runnable::run);

    verify(reportSender).enqueueReport(expectedReport1, false);
    verify(reportSender).enqueueReport(expectedReport2, false);
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

  @SdkSuppress(minSdkVersion = 37)
  @Test
  public void testIsOom_returnsFalseWhenMarkerAbsent() {
    when(reportPersistence.hasOomMarker("sessionId")).thenReturn(false);

    assertFalse(reportingCoordinator.isOom("sessionId"));
  }

  @SdkSuppress(minSdkVersion = 37)
  @Test
  public void testIsOom_returnsTrueWhenMarkerPresent() {
    when(reportPersistence.hasOomMarker("sessionId")).thenReturn(true);

    assertTrue(reportingCoordinator.isOom("sessionId"));
  }

  @SdkSuppress(minSdkVersion = 37)
  @Test
  public void testPersistOomMarker_delegatesToPersistence() {
    reportingCoordinator.persistOomMarker("sessionId");

    verify(reportPersistence).persistOomMarker("sessionId");
  }

  @SdkSuppress(minSdkVersion = 37)
  @Test
  public void testIsMemoryLimiterKill() {
    ApplicationExitInfo exitInfo =
        makeApplicationExitInfo(ApplicationExitInfo.REASON_OTHER, 0, 0, "MemoryLimiter:AnonSwap");

    assertNotNull(reportingCoordinator.isMemoryLimiterKill("sessionId", List.of(exitInfo)));
  }

  @SdkSuppress(minSdkVersion = 37)
  @Test
  public void testWasHeapDumpGeneratedViaFile_returnsTrueForCorrectSessionTime()
      throws IOException {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    File profilingDir = new File(context.getFilesDir(), "profiling");
    assertTrue(profilingDir.mkdir());

    File heapDump = new File(context.getFilesDir(), "profiling/profile_trigger-type-7.hprof");
    assertTrue(heapDump.createNewFile());

    Instant sessionTime = Instant.ofEpochMilli(heapDump.lastModified());
    boolean generated =
        SessionReportingCoordinator.wasHeapDumpGeneratedViaFile(context.getFilesDir(), sessionTime);

    assertTrue(generated);
  }

  @SdkSuppress(minSdkVersion = 37)
  @Test
  public void testWasHeapDumpGeneratedViaFile_returnsFalseForIncorrectSessionTime()
      throws IOException {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    File profilingDir = new File(context.getFilesDir(), "profiling");
    assertTrue(profilingDir.mkdir());

    File heapDump =
        new File(context.getFilesDir(), "profiling/com.google.firebase.crashlytics.test.hprof");
    assertTrue(heapDump.createNewFile());

    Instant sessionTime = Instant.ofEpochMilli(heapDump.lastModified()).plusMillis(100);

    boolean generated =
        SessionReportingCoordinator.wasHeapDumpGeneratedViaFile(context.getFilesDir(), sessionTime);

    assertFalse(generated);
  }

  @SdkSuppress(minSdkVersion = 37)
  @Test
  public void testCorrectFilenames() {
    assertTrue(
        SessionReportingCoordinator.isProfilingTriggerHeapdump(
            "profile_trigger-anomaly_memorylimit_2026-11-03-15-19-47-390_uid-10531.perfetto-java-heap-dump-metadata.zip"));
    assertTrue(
        SessionReportingCoordinator.isProfilingTriggerHeapdump(
            "profile_trigger-type-7_2026-10-21-16-30-31-270_uid-10531.perfetto-java-heap-dump"));
    assertFalse(SessionReportingCoordinator.isProfilingTriggerHeapdump("some-other-filename"));
  }

  private ApplicationExitInfo makeApplicationExitInfo(int reason, int subreason, int status) {
    return makeApplicationExitInfo(reason, subreason, status, "");
  }

  private ApplicationExitInfo makeApplicationExitInfo(
      int reason, int subreason, int status, String description) {
    Parcel dest = Parcel.obtain();

    dest.writeInt(1);
    dest.writeInt(1);
    dest.writeInt(1);
    dest.writeInt(1);
    dest.writeString("process");
    dest.writeString("com.test");
    dest.writeInt(1);
    dest.writeInt(reason);
    dest.writeInt(subreason);
    dest.writeInt(status);
    dest.writeInt(1);
    dest.writeLong(1L);
    dest.writeLong(1L);
    dest.writeLong(1L);
    dest.writeString(description);
    dest.writeByteArray(new byte[] {});
    dest.setDataPosition(0);

    return ApplicationExitInfo.CREATOR.createFromParcel(dest);
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

  private void stubCaptureEventData() {
    when(dataCapture.captureEventData(
            any(Throwable.class),
            any(Thread.class),
            anyString(),
            anyLong(),
            anyInt(),
            anyInt(),
            anyBoolean()))
        .thenReturn(testEvent);
  }

  private CrashlyticsReport.Session.Event capturePersistedEvent(
      String sessionId, boolean isHighPriority) {
    ArgumentCaptor<CrashlyticsReport.Session.Event> eventCaptor =
        ArgumentCaptor.forClass(CrashlyticsReport.Session.Event.class);
    verify(reportPersistence)
        .persistEvent(eventCaptor.capture(), eq(sessionId), eq(isHighPriority));
    return eventCaptor.getValue();
  }

  private static CrashlyticsReport makeTestReport(String sessionId) {
    return CrashlyticsReport.builder()
        .setSdkVersion("sdkVersion")
        .setGmpAppId("gmpAppId")
        .setPlatform(1)
        .setInstallationUuid("installationId")
        .setBuildVersion("1")
        .setDisplayVersion("1.0.0")
        .setSession(
            CrashlyticsReport.Session.builder()
                .setGenerator("generator")
                .setIdentifier(sessionId)
                .setStartedAt(0)
                .setApp(
                    CrashlyticsReport.Session.Application.builder()
                        .setIdentifier("applicationId")
                        .setVersion("version")
                        .setDisplayVersion("displayVersion")
                        .build())
                .setGeneratorType(3)
                .build())
        .build();
  }

  private static CrashlyticsReport.Session.Event makeTestEvent() {
    return CrashlyticsReport.Session.Event.builder()
        .setType("error")
        .setTimestamp(1000L)
        .setApp(
            CrashlyticsReport.Session.Event.Application.builder()
                .setExecution(
                    CrashlyticsReport.Session.Event.Application.Execution.builder()
                        .setSignal(
                            CrashlyticsReport.Session.Event.Application.Execution.Signal.builder()
                                .setName("0")
                                .setCode("0")
                                .setAddress(0)
                                .build())
                        .setBinaries(Collections.emptyList())
                        .build())
                .setUiOrientation(1)
                .build())
        .setDevice(
            CrashlyticsReport.Session.Event.Device.builder()
                .setBatteryVelocity(3)
                .setProximityOn(false)
                .setOrientation(1)
                .setRamUsed(10000L)
                .setDiskUsed(10000L)
                .build())
        .build();
  }

  private static CrashlyticsReportWithSessionId createTestReportWithSessionId(String sessionId) {
    return CrashlyticsReportWithSessionId.create(
        makeTestReport(sessionId), sessionId, new File("fake"));
  }

  private static RolloutAssignment fakeRolloutAssignment() {
    return RolloutAssignment.create("rollout_1", "my_feature", "false", "enabled", 2);
  }
}
