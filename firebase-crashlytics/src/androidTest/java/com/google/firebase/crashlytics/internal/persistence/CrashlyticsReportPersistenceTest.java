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

package com.google.firebase.crashlytics.internal.persistence;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Application;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.Signal;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.Thread.Frame;
import com.google.firebase.crashlytics.internal.model.ImmutableList;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
import com.google.firebase.crashlytics.internal.settings.model.SessionSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.Settings;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CrashlyticsReportPersistenceTest {

  private static final int VERY_LARGE_UPPER_LIMIT = 9999;

  private CrashlyticsReportPersistence reportPersistence;
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static SettingsDataProvider getSettingsMock(
      int maxCompleteSessionsCount, int maxCustomExceptionEvents) {
    SettingsDataProvider settingsDataProvider = mock(SettingsDataProvider.class);
    Settings settingsMock = mock(Settings.class);
    SessionSettingsData sessionSettingsDataMock =
        new SessionSettingsData(maxCustomExceptionEvents, maxCompleteSessionsCount);
    when(settingsMock.getSessionData()).thenReturn(sessionSettingsDataMock);
    when(settingsDataProvider.getSettings()).thenReturn(settingsMock);
    return settingsDataProvider;
  }

  @Before
  public void setUp() throws Exception {
    reportPersistence =
        new CrashlyticsReportPersistence(
            folder.newFolder(), getSettingsMock(VERY_LARGE_UPPER_LIMIT, VERY_LARGE_UPPER_LIMIT));
  }

  @Test
  public void testLoadFinalizeReports_noReports_returnsNothing() {
    assertTrue(reportPersistence.loadFinalizedReports().isEmpty());
  }

  @Test
  public void testLoadFinalizedReports_reportWithNoEvents_returnsNothing() {
    final String sessionId = "testSession";
    final long timestamp = System.currentTimeMillis();
    reportPersistence.persistReport(makeTestReport(sessionId));
    reportPersistence.finalizeReports(sessionId, timestamp);
    assertTrue(reportPersistence.loadFinalizedReports().isEmpty());
  }

  @Test
  public void testLoadFinalizedReports_reportThenEvent_returnsReportWithEvent() {
    final String sessionId = "testSession";
    final CrashlyticsReport testReport = makeTestReport(sessionId);
    final CrashlyticsReport.Session.Event testEvent = makeTestEvent();

    reportPersistence.persistReport(testReport);
    reportPersistence.persistEvent(testEvent, sessionId);

    final long endedAt = System.currentTimeMillis();

    reportPersistence.finalizeReports("skippedSession", endedAt);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(1, finalizedReports.size());
    final CrashlyticsReport finalizedReport = finalizedReports.get(0);
    assertEquals(
        testReport
            .withSessionEndFields(endedAt, false, null)
            .withEvents(ImmutableList.from(testEvent)),
        finalizedReport);
  }

  @Test
  public void testLoadFinalizedReports_reportThenMultipleEvents_returnsReportWithMultipleEvents() {
    final String sessionId = "testSession";
    final CrashlyticsReport testReport = makeTestReport(sessionId);
    final CrashlyticsReport.Session.Event testEvent = makeTestEvent();
    final CrashlyticsReport.Session.Event testEvent2 = makeTestEvent();

    reportPersistence.persistReport(testReport);
    reportPersistence.persistEvent(testEvent, sessionId);
    reportPersistence.persistEvent(testEvent2, sessionId);

    final long endedAt = System.currentTimeMillis();

    reportPersistence.finalizeReports("skippedSession", endedAt);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(1, finalizedReports.size());
    final CrashlyticsReport finalizedReport = finalizedReports.get(0);
    assertEquals(
        testReport
            .withSessionEndFields(endedAt, false, null)
            .withEvents(ImmutableList.from(testEvent, testEvent2)),
        finalizedReport);
  }

  @Test
  public void
      testLoadFinalizedReports_reportsWithEventsInMultipleSessions_returnsReportsWithProperEvents() {
    final String sessionId1 = "testSession1";
    final CrashlyticsReport testReport1 = makeTestReport(sessionId1);
    final String sessionId2 = "testSession2";
    final CrashlyticsReport testReport2 = makeTestReport(sessionId2);
    final CrashlyticsReport.Session.Event testEvent1 = makeTestEvent();
    final CrashlyticsReport.Session.Event testEvent2 = makeTestEvent();

    reportPersistence.persistReport(testReport1);
    reportPersistence.persistReport(testReport2);
    reportPersistence.persistEvent(testEvent1, sessionId1);
    reportPersistence.persistEvent(testEvent2, sessionId2);

    final long endedAt = System.currentTimeMillis();

    reportPersistence.finalizeReports("skippedSession", endedAt);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(2, finalizedReports.size());
    final CrashlyticsReport finalizedReport1 = finalizedReports.get(1);
    assertEquals(
        testReport1
            .withSessionEndFields(endedAt, false, null)
            .withEvents(ImmutableList.from(testEvent1)),
        finalizedReport1);
    final CrashlyticsReport finalizedReport2 = finalizedReports.get(0);
    assertEquals(
        testReport2
            .withSessionEndFields(endedAt, false, null)
            .withEvents(ImmutableList.from(testEvent2)),
        finalizedReport2);
  }

  @Test
  public void testFinalizeReports_capsOpenSessions() throws IOException {
    for (int i = 0; i < 10; i++) {
      persistReportWithEvent(reportPersistence, "testSession" + i, true);
    }

    final long endedAt = System.currentTimeMillis();

    reportPersistence.finalizeReports("skippedSession", endedAt);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(8, finalizedReports.size());
  }

  @Test
  public void testFinalizeReports_capsOldestSessionsFirst() throws IOException {
    DecimalFormat format = new DecimalFormat("00");
    for (int i = 0; i < 16; i++) {
      persistReportWithEvent(reportPersistence, "testSession" + format.format(i), true);
    }

    final long endedAt = System.currentTimeMillis();

    reportPersistence.finalizeReports("skippedSession", endedAt);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(8, finalizedReports.size());

    List<String> reportIdentifiers = new ArrayList<>();
    for (CrashlyticsReport finalizedReport : finalizedReports) {
      reportIdentifiers.add(finalizedReport.getSession().getIdentifier());
    }
    List<String> expectedSessions =
        Arrays.asList("testSession12", "testSession13", "testSession14", "testSession15");
    List<String> unexpectedSessions =
        Arrays.asList("testSession4", "testSession5", "testSession6", "testSession7");

    assertTrue(reportIdentifiers.containsAll(expectedSessions));
    for (String unexpectedSession : unexpectedSessions) {
      assertFalse(reportIdentifiers.contains(unexpectedSession));
    }
  }

  @Test
  public void testFinalizeReports_skipsCappingCurrentSession() throws IOException {
    for (int i = 0; i < 16; i++) {
      persistReportWithEvent(reportPersistence, "testSession" + i, true);
    }

    final long endedAt = System.currentTimeMillis();

    reportPersistence.finalizeReports("testSession5", endedAt);
    List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(8, finalizedReports.size());
    persistReportWithEvent(reportPersistence, "testSession11", true);
    reportPersistence.finalizeReports("testSession11", endedAt);
    finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(9, finalizedReports.size());
  }

  @Test
  public void testFinalizeReports_capsReports() throws IOException {
    reportPersistence =
        new CrashlyticsReportPersistence(
            folder.newFolder(), getSettingsMock(4, VERY_LARGE_UPPER_LIMIT));
    for (int i = 0; i < 10; i++) {
      persistReportWithEvent(reportPersistence, "testSession" + i, true);
    }

    reportPersistence.finalizeReports("skippedSession", 0L);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(4, finalizedReports.size());
  }

  @Test
  public void testFinalizeReports_whenSettingsChanges_capsReports() throws IOException {
    SettingsDataProvider settingsDataProvider = mock(SettingsDataProvider.class);
    Settings settingsMock = mock(Settings.class);
    SessionSettingsData sessionSettingsDataMock =
        new SessionSettingsData(VERY_LARGE_UPPER_LIMIT, 4);
    SessionSettingsData sessionSettingsDataMockDifferentValues =
        new SessionSettingsData(VERY_LARGE_UPPER_LIMIT, 8);
    when(settingsMock.getSessionData()).thenReturn(sessionSettingsDataMock);
    when(settingsDataProvider.getSettings()).thenReturn(settingsMock);
    reportPersistence = new CrashlyticsReportPersistence(folder.newFolder(), settingsDataProvider);

    DecimalFormat format = new DecimalFormat("00");
    for (int i = 0; i < 16; i++) {
      persistReportWithEvent(reportPersistence, "testSession" + format.format(i), true);
    }

    reportPersistence.finalizeReports("skippedSession", 0L);
    List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(4, finalizedReports.size());
    when(settingsMock.getSessionData()).thenReturn(sessionSettingsDataMockDifferentValues);

    for (int i = 16; i < 32; i++) {
      persistReportWithEvent(reportPersistence, "testSession" + i, true);
    }

    reportPersistence.finalizeReports("skippedSession", 0L);

    finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(8, finalizedReports.size());
  }

  @Test
  public void testFinalizeReports_removesLowPriorityReportsFirst() throws IOException {
    reportPersistence =
        new CrashlyticsReportPersistence(
            folder.newFolder(), getSettingsMock(4, VERY_LARGE_UPPER_LIMIT));

    for (int i = 0; i < 10; i++) {
      boolean priority = i >= 3 && i <= 8;
      String sessionId = "testSession" + i + (priority ? "high" : "low");
      persistReportWithEvent(reportPersistence, sessionId, priority);
    }

    reportPersistence.finalizeReports("skippedSession", 0L);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(4, finalizedReports.size());
    for (CrashlyticsReport finalizedReport : finalizedReports) {
      assertTrue(finalizedReport.getSession().getIdentifier().contains("high"));
    }
  }

  @Test
  public void testFinalizeReports_removesOldestReportsFirst() throws IOException {
    reportPersistence =
        new CrashlyticsReportPersistence(
            folder.newFolder(), getSettingsMock(4, VERY_LARGE_UPPER_LIMIT));
    for (int i = 0; i < 8; i++) {
      String sessionId = "testSession" + i;
      persistReportWithEvent(reportPersistence, sessionId, true);
    }

    reportPersistence.finalizeReports("skippedSession", 0L);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(4, finalizedReports.size());
    List<String> reportIdentifiers = new ArrayList<>();
    for (CrashlyticsReport finalizedReport : finalizedReports) {
      reportIdentifiers.add(finalizedReport.getSession().getIdentifier());
    }

    List<String> expectedSessions =
        Arrays.asList("testSession4", "testSession5", "testSession6", "testSession7");
    List<String> unexpectedSessions =
        Arrays.asList("testSession0", "testSession1", "testSession2", "testSession3");

    assertTrue(reportIdentifiers.containsAll(expectedSessions));
    for (String unexpectedSession : unexpectedSessions) {
      assertFalse(reportIdentifiers.contains(unexpectedSession));
    }
  }

  @Test
  public void testLoadFinalizedReports_reportWithUserId_returnsReportWithProperUserId() {
    final String sessionId = "testSession";
    final String userId = "testUser";
    final CrashlyticsReport testReport = makeTestReport(sessionId);
    final CrashlyticsReport.Session.Event testEvent = makeTestEvent();

    reportPersistence.persistReport(testReport);
    reportPersistence.persistEvent(testEvent, sessionId);
    reportPersistence.persistUserIdForSession(userId, sessionId);

    reportPersistence.finalizeReports("skippedSession", 0L);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(1, finalizedReports.size());
    final CrashlyticsReport finalizedReport = finalizedReports.get(0);
    assertNotNull(finalizedReport.getSession().getUser());
    assertEquals(userId, finalizedReport.getSession().getUser().getIdentifier());
  }

  @Test
  public void
      testLoadFinalizedReports_reportsWithUserIdInMultipleSessions_returnsReportsWithProperUserIds() {
    final String sessionId1 = "testSession1";
    final CrashlyticsReport testReport1 = makeTestReport(sessionId1);
    final String sessionId2 = "testSession2";
    final CrashlyticsReport testReport2 = makeTestReport(sessionId2);
    final CrashlyticsReport.Session.Event testEvent1 = makeTestEvent();
    final CrashlyticsReport.Session.Event testEvent2 = makeTestEvent();
    final String userId1 = "testUser1";
    final String userId2 = "testUser2";

    reportPersistence.persistReport(testReport1);
    reportPersistence.persistReport(testReport2);
    reportPersistence.persistEvent(testEvent1, sessionId1);
    reportPersistence.persistEvent(testEvent2, sessionId2);
    reportPersistence.persistUserIdForSession(userId1, sessionId1);
    reportPersistence.persistUserIdForSession(userId2, sessionId2);

    reportPersistence.finalizeReports("skippedSession", 0L);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(2, finalizedReports.size());
    final CrashlyticsReport finalizedReport1 = finalizedReports.get(1);
    assertNotNull(finalizedReport1.getSession().getUser());
    assertEquals(userId1, finalizedReport1.getSession().getUser().getIdentifier());
    final CrashlyticsReport finalizedReport2 = finalizedReports.get(0);
    assertNotNull(finalizedReport2.getSession().getUser());
    assertEquals(userId2, finalizedReport2.getSession().getUser().getIdentifier());
  }

  @Test
  public void testFinalizeSessionWithNativeEvent_writesNativeSessions() {
    byte[] testContents = {0, 2, 20, 10};
    CrashlyticsReport.FilesPayload filesPayload =
        CrashlyticsReport.FilesPayload.builder()
            .setOrgId("orgId")
            .setFiles(
                ImmutableList.from(
                    CrashlyticsReport.FilesPayload.File.builder()
                        .setContents(testContents)
                        .setFilename("bytes")
                        .build()))
            .build();

    CrashlyticsReport report = makeTestNativeReport();
    List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();

    assertEquals(0, finalizedReports.size());

    reportPersistence.finalizeSessionWithNativeEvent("sessionId", report, filesPayload);

    finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(1, finalizedReports.size());
    assertEquals(
        report.withNdkPayload(filesPayload).withOrganizationId("orgId"), finalizedReports.get(0));
  }

  @Test
  public void testDeleteFinalizedReport_removesReports() {
    final String sessionId = "testSession";
    final CrashlyticsReport testReport = makeTestReport(sessionId);
    final CrashlyticsReport.Session.Event testEvent = makeTestEvent();

    reportPersistence.persistReport(testReport);
    reportPersistence.persistEvent(testEvent, sessionId);

    reportPersistence.finalizeReports("skippedSession", 0L);

    assertEquals(1, reportPersistence.loadFinalizedReports().size());

    reportPersistence.deleteFinalizedReport(sessionId);

    assertEquals(0, reportPersistence.loadFinalizedReports().size());
  }

  @Test
  public void testDeleteFinalizedReport_withWrongSessionId_doesNotRemoveReports() {
    final String sessionId = "testSession";
    final CrashlyticsReport testReport = makeTestReport(sessionId);
    final CrashlyticsReport.Session.Event testEvent = makeTestEvent();

    reportPersistence.persistReport(testReport);
    reportPersistence.persistEvent(testEvent, sessionId);

    reportPersistence.finalizeReports("skippedSession", 0L);

    assertEquals(1, reportPersistence.loadFinalizedReports().size());

    reportPersistence.deleteFinalizedReport("wrongSessionId");

    assertEquals(1, reportPersistence.loadFinalizedReports().size());
  }

  @Test
  public void testDeleteAllReports_removesAllReports() {
    final String sessionId1 = "testSession1";
    final CrashlyticsReport testReport1 = makeTestReport(sessionId1);
    final String sessionId2 = "testSession2";
    final CrashlyticsReport testReport2 = makeTestReport(sessionId2);
    final CrashlyticsReport.Session.Event testEvent1 = makeTestEvent();
    final CrashlyticsReport.Session.Event testEvent2 = makeTestEvent();

    reportPersistence.persistReport(testReport1);
    reportPersistence.persistReport(testReport2);
    reportPersistence.persistEvent(testEvent1, sessionId1);
    reportPersistence.persistEvent(testEvent2, sessionId2);

    reportPersistence.finalizeReports("skippedSession", 0L);

    assertEquals(2, reportPersistence.loadFinalizedReports().size());

    reportPersistence.deleteAllReports();

    assertEquals(0, reportPersistence.loadFinalizedReports().size());
  }

  @Test
  public void testPersistEvent_keepsAppropriateNumberOfMostRecentEvents() throws IOException {
    reportPersistence =
        new CrashlyticsReportPersistence(
            folder.newFolder(), getSettingsMock(VERY_LARGE_UPPER_LIMIT, 4));
    final String sessionId = "testSession";
    final CrashlyticsReport testReport = makeTestReport(sessionId);
    final CrashlyticsReport.Session.Event testEvent1 = makeTestEvent("type1", "reason1");
    final CrashlyticsReport.Session.Event testEvent2 = makeTestEvent("type2", "reason2");
    final CrashlyticsReport.Session.Event testEvent3 = makeTestEvent("type3", "reason3");
    final CrashlyticsReport.Session.Event testEvent4 = makeTestEvent("type4", "reason4");
    final CrashlyticsReport.Session.Event testEvent5 = makeTestEvent("type5", "reason5");

    reportPersistence.persistReport(testReport);
    reportPersistence.persistEvent(testEvent1, sessionId);
    reportPersistence.persistEvent(testEvent2, sessionId);
    reportPersistence.persistEvent(testEvent3, sessionId);
    reportPersistence.persistEvent(testEvent4, sessionId);
    reportPersistence.persistEvent(testEvent5, sessionId);

    final long endedAt = System.currentTimeMillis();

    reportPersistence.finalizeReports("skippedSession", endedAt);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(1, finalizedReports.size());
    final CrashlyticsReport finalizedReport = finalizedReports.get(0);
    assertEquals(4, finalizedReport.getSession().getEvents().size());
    assertEquals(
        testReport
            .withSessionEndFields(endedAt, false, null)
            .withEvents(ImmutableList.from(testEvent2, testEvent3, testEvent4, testEvent5)),
        finalizedReport);
  }

  @Test
  public void testPersistEvent_whenSettingsChanges_keepsAppropriateNumberOfMostRecentEvents()
      throws IOException {
    SettingsDataProvider settingsDataProvider = mock(SettingsDataProvider.class);
    Settings settingsMock = mock(Settings.class);
    SessionSettingsData sessionSettingsDataMock =
        new SessionSettingsData(4, VERY_LARGE_UPPER_LIMIT);
    SessionSettingsData sessionSettingsDataMockDifferentValues =
        new SessionSettingsData(8, VERY_LARGE_UPPER_LIMIT);
    when(settingsMock.getSessionData()).thenReturn(sessionSettingsDataMock);
    when(settingsDataProvider.getSettings()).thenReturn(settingsMock);
    reportPersistence = new CrashlyticsReportPersistence(folder.newFolder(), settingsDataProvider);

    final String sessionId = "testSession";
    final CrashlyticsReport testReport = makeTestReport(sessionId);
    final CrashlyticsReport.Session.Event testEvent1 = makeTestEvent("type1", "reason1");
    final CrashlyticsReport.Session.Event testEvent2 = makeTestEvent("type2", "reason2");
    final CrashlyticsReport.Session.Event testEvent3 = makeTestEvent("type3", "reason3");
    final CrashlyticsReport.Session.Event testEvent4 = makeTestEvent("type4", "reason4");
    final CrashlyticsReport.Session.Event testEvent5 = makeTestEvent("type5", "reason5");

    reportPersistence.persistReport(testReport);
    reportPersistence.persistEvent(testEvent1, sessionId);
    reportPersistence.persistEvent(testEvent2, sessionId);
    reportPersistence.persistEvent(testEvent3, sessionId);
    reportPersistence.persistEvent(testEvent4, sessionId);
    reportPersistence.persistEvent(testEvent5, sessionId);

    long endedAt = System.currentTimeMillis();

    reportPersistence.finalizeReports("skippedSession", endedAt);

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(1, finalizedReports.size());
    final CrashlyticsReport finalizedReport = finalizedReports.get(0);
    assertEquals(4, finalizedReport.getSession().getEvents().size());
    assertEquals(
        testReport
            .withSessionEndFields(endedAt, false, null)
            .withEvents(ImmutableList.from(testEvent2, testEvent3, testEvent4, testEvent5)),
        finalizedReport);

    when(settingsMock.getSessionData()).thenReturn(sessionSettingsDataMockDifferentValues);

    final CrashlyticsReport.Session.Event testEvent6 = makeTestEvent("type6", "reason6");
    final CrashlyticsReport.Session.Event testEvent7 = makeTestEvent("type7", "reason7");
    final CrashlyticsReport.Session.Event testEvent8 = makeTestEvent("type8", "reason8");
    final CrashlyticsReport.Session.Event testEvent9 = makeTestEvent("type9", "reason9");
    final CrashlyticsReport.Session.Event testEvent10 = makeTestEvent("type10", "reason10");

    final String sessionId2 = "testSession2";
    final CrashlyticsReport testReport2 = makeTestReport(sessionId2);
    reportPersistence.persistReport(testReport2);
    reportPersistence.persistEvent(testEvent1, sessionId2);
    reportPersistence.persistEvent(testEvent2, sessionId2);
    reportPersistence.persistEvent(testEvent3, sessionId2);
    reportPersistence.persistEvent(testEvent4, sessionId2);
    reportPersistence.persistEvent(testEvent5, sessionId2);
    reportPersistence.persistEvent(testEvent6, sessionId2);
    reportPersistence.persistEvent(testEvent7, sessionId2);
    reportPersistence.persistEvent(testEvent8, sessionId2);
    reportPersistence.persistEvent(testEvent9, sessionId2);
    reportPersistence.persistEvent(testEvent10, sessionId2);

    endedAt = System.currentTimeMillis();

    reportPersistence.finalizeReports("skippedSession", endedAt);

    final List<CrashlyticsReport> finalizedReports2 = reportPersistence.loadFinalizedReports();
    assertEquals(2, finalizedReports2.size());
    final CrashlyticsReport finalizedReport2 = finalizedReports2.get(0);
    assertEquals(8, finalizedReport2.getSession().getEvents().size());
    assertEquals(
        testReport2
            .withSessionEndFields(endedAt, false, null)
            .withEvents(
                ImmutableList.from(
                    testEvent3,
                    testEvent4,
                    testEvent5,
                    testEvent6,
                    testEvent7,
                    testEvent8,
                    testEvent9,
                    testEvent10)),
        finalizedReport2);
  }

  private static void persistReportWithEvent(
      CrashlyticsReportPersistence reportPersistence, String sessionId, boolean isHighPriority) {
    CrashlyticsReport testReport = makeTestReport(sessionId);
    reportPersistence.persistReport(testReport);
    final CrashlyticsReport.Session.Event testEvent = makeTestEvent();
    reportPersistence.persistEvent(testEvent, sessionId, isHighPriority);
  }

  private static CrashlyticsReport makeTestReport(String sessionId) {
    return CrashlyticsReport.builder()
        .setSdkVersion("sdkVersion")
        .setGmpAppId("gmpAppId")
        .setPlatform(1)
        .setInstallationUuid("installationId")
        .setBuildVersion("1")
        .setDisplayVersion("1.0.0")
        .setSession(makeTestSession(sessionId))
        .build();
  }

  private static CrashlyticsReport makeTestNativeReport() {
    return CrashlyticsReport.builder()
        .setSdkVersion("sdkVersion")
        .setGmpAppId("gmpAppId")
        .setPlatform(1)
        .setInstallationUuid("installationId")
        .setBuildVersion("1")
        .setDisplayVersion("1.0.0")
        .build();
  }

  private static CrashlyticsReport.Session makeTestSession(String sessionId) {
    return Session.builder()
        .setGenerator("generator")
        .setIdentifier(sessionId)
        .setStartedAt(0)
        .setApp(makeTestApplication())
        .setGeneratorType(3)
        .build();
  }

  private static Application makeTestApplication() {
    return Application.builder()
        .setIdentifier("applicationId")
        .setVersion("version")
        .setDisplayVersion("displayVersion")
        .build();
  }

  private static Event makeTestEvent() {
    return makeTestEvent("java.lang.Exception", "reason");
  }

  private static Event makeTestEvent(String type, String reason) {
    return Event.builder()
        .setType("type")
        .setTimestamp(1000)
        .setApp(
            Session.Event.Application.builder()
                .setBackground(false)
                .setExecution(
                    Execution.builder()
                        .setBinaries(
                            ImmutableList.from(
                                Execution.BinaryImage.builder()
                                    .setBaseAddress(0)
                                    .setName("name")
                                    .setSize(100000)
                                    .setUuid("uuid")
                                    .build()))
                        .setException(
                            Execution.Exception.builder()
                                .setFrames(makeTestFrames())
                                .setOverflowCount(0)
                                .setReason(reason)
                                .setType(type)
                                .build())
                        .setSignal(Signal.builder().setCode("0").setName("0").setAddress(0).build())
                        .setThreads(
                            ImmutableList.from(
                                Session.Event.Application.Execution.Thread.builder()
                                    .setName("name")
                                    .setImportance(4)
                                    .setFrames(makeTestFrames())
                                    .build()))
                        .build())
                .setUiOrientation(1)
                .build())
        .setDevice(
            Session.Event.Device.builder()
                .setBatteryLevel(0.5)
                .setBatteryVelocity(3)
                .setDiskUsed(10000000)
                .setOrientation(1)
                .setProximityOn(true)
                .setRamUsed(10000000)
                .build())
        .build();
  }

  private static ImmutableList<Frame> makeTestFrames() {
    return ImmutableList.from(
        Frame.builder()
            .setPc(0)
            .setSymbol("func1")
            .setFile("Test.java")
            .setOffset(36)
            .setImportance(4)
            .build(),
        Frame.builder()
            .setPc(1)
            .setSymbol("func2")
            .setFile("Test.java")
            .setOffset(5637)
            .setImportance(4)
            .build(),
        Frame.builder()
            .setPc(2)
            .setSymbol("func3")
            .setFile("Test.java")
            .setOffset(22429)
            .setImportance(4)
            .build(),
        Frame.builder()
            .setPc(3)
            .setSymbol("func4")
            .setFile("Test.java")
            .setOffset(751)
            .setImportance(4)
            .build());
  }
}
