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

import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Application;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.Signal;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.Thread.Frame;
import com.google.firebase.crashlytics.internal.model.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CrashlyticsReportPersistenceTest {

  private CrashlyticsReportPersistence reportPersistence;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    reportPersistence = new CrashlyticsReportPersistence(folder.newFolder());
  }

  @Test
  public void testLoadFinalizeReports_noReports_returnsNothing() {
    assertTrue(reportPersistence.loadFinalizedReports().isEmpty());
  }

  @Test
  public void testLoadFinalizedReports_reportWithNoEvents_returnsNothing() {
    final String sessionId = "testSession";
    reportPersistence.persistReport(makeTestReport(sessionId));
    reportPersistence.finalizeReports(sessionId);
    assertTrue(reportPersistence.loadFinalizedReports().isEmpty());
  }

  @Test
  public void testLoadFinalizedReports_reportThenEvent_returnsReportWithEvent() {
    final String sessionId = "testSession";
    final CrashlyticsReport testReport = makeTestReport(sessionId);
    final CrashlyticsReport.Session.Event testEvent = makeTestEvent();

    reportPersistence.persistReport(testReport);
    reportPersistence.persistEvent(testEvent, sessionId);
    reportPersistence.finalizeReports("skippedSession");

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(1, finalizedReports.size());
    final CrashlyticsReport finalizedReport = finalizedReports.get(0);
    assertEquals(finalizedReport, testReport.withEvents(ImmutableList.from(testEvent)));
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

    reportPersistence.finalizeReports("skippedSession");

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(1, finalizedReports.size());
    final CrashlyticsReport finalizedReport = finalizedReports.get(0);
    assertEquals(finalizedReport, testReport.withEvents(ImmutableList.from(testEvent, testEvent2)));
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

    reportPersistence.finalizeReports("skippedSession");

    final List<CrashlyticsReport> finalizedReports = reportPersistence.loadFinalizedReports();
    assertEquals(2, finalizedReports.size());
    final CrashlyticsReport finalizedReport1 = finalizedReports.get(0);
    assertEquals(finalizedReport1, testReport1.withEvents(ImmutableList.from(testEvent1)));
    final CrashlyticsReport finalizedReport2 = finalizedReports.get(1);
    assertEquals(finalizedReport2, testReport2.withEvents(ImmutableList.from(testEvent2)));
  }

  @Test
  public void testDeleteFinalizedReport_removesReports() {
    final String sessionId = "testSession";
    final CrashlyticsReport testReport = makeTestReport(sessionId);
    final CrashlyticsReport.Session.Event testEvent = makeTestEvent();

    reportPersistence.persistReport(testReport);
    reportPersistence.persistEvent(testEvent, sessionId);
    reportPersistence.finalizeReports("skippedSession");

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
    reportPersistence.finalizeReports("skippedSession");

    assertEquals(1, reportPersistence.loadFinalizedReports().size());

    reportPersistence.deleteFinalizedReport("wrongSessionId");

    assertEquals(1, reportPersistence.loadFinalizedReports().size());
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

  private static CrashlyticsReport.Session makeTestSession(String sessionId) {
    return Session.builder()
        .setGenerator("generator")
        .setIdentifier(sessionId)
        .setStartedAt(0)
        .setApp(makeTestApplication())
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
                                .setReason("reason")
                                .setType("java.lang.Exception")
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
