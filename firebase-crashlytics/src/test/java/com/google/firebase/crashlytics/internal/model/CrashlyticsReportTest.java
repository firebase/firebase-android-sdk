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

package com.google.firebase.crashlytics.internal.model;

import static org.junit.Assert.*;

import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Application;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.BinaryImage;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.Signal;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.Thread.Frame;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class CrashlyticsReportTest {

  @Test
  public void testWithEvents_returnsNewReportWithEvents() {
    final CrashlyticsReport testReport = makeTestReport();

    assertNull(testReport.getSession().getEvents());

    final CrashlyticsReport withEventsReport = testReport.withEvents(makeTestEvents(2));

    assertNotEquals(testReport, withEventsReport);
    assertNotNull(withEventsReport.getSession().getEvents());
    assertEquals(2, withEventsReport.getSession().getEvents().size());
    // no rollouts feature so we don't expect any rollouts state info within a event
    assertNull(withEventsReport.getSession().getEvents().get(0).getRollouts());
  }

  @Test
  public void testWithEvents_returnsNewReportWithAnr() {
    final CrashlyticsReport testReport = makeTestReport();

    assertNull(testReport.getSession().getEvents());
    final CrashlyticsReport withAnrEventsReport =
        testReport.withEvents(Collections.singletonList(makeAnrEvent()));

    assertNotEquals(testReport, withAnrEventsReport);
    assertNotNull(withAnrEventsReport.getSession().getEvents());
    assertEquals(1, withAnrEventsReport.getSession().getEvents().size());
    assertNotNull(
        withAnrEventsReport
            .getSession()
            .getEvents()
            .get(0)
            .getApp()
            .getExecution()
            .getAppExitInfo());
  }

  @Test
  public void testWithOrganizationId_returnsNewReportWithOrganizationId() {
    final CrashlyticsReport testReport = makeTestReport();

    assertNull(testReport.getSession().getApp().getOrganization());

    final CrashlyticsReport withOrganizationIdReport =
        testReport.withOrganizationId("organizationId");

    assertNotEquals(testReport, withOrganizationIdReport);
    assertNotNull(withOrganizationIdReport.getSession().getApp().getOrganization());
    assertEquals(
        "organizationId",
        withOrganizationIdReport.getSession().getApp().getOrganization().getClsId());
  }

  @Test
  public void testWithSessionEndFields_returnsNewReportWithSessionEndFields() {
    final CrashlyticsReport testReport = makeTestReport();

    assertNull(testReport.getSession().getEndedAt());
    assertFalse(testReport.getSession().isCrashed());
    assertNull(testReport.getSession().getUser());

    final long endedAt = System.currentTimeMillis();
    final boolean isCrashed = true;
    final String userId = "userId";

    final CrashlyticsReport withSessionEndFieldsReport =
        testReport.withSessionEndFields(endedAt, isCrashed, userId);

    assertNotEquals(testReport, withSessionEndFieldsReport);
    assertNotNull(withSessionEndFieldsReport.getSession().getUser());
    assertNotNull(withSessionEndFieldsReport.getSession().getEndedAt());
    assertEquals(endedAt, withSessionEndFieldsReport.getSession().getEndedAt().longValue());
    assertEquals(isCrashed, withSessionEndFieldsReport.getSession().isCrashed());
    assertEquals(userId, withSessionEndFieldsReport.getSession().getUser().getIdentifier());
  }

  @Test
  public void testWithSessionEndFieldsNullUser_returnsNewReportWithSessionEndFieldsNullUser() {
    final CrashlyticsReport testReport = makeTestReport();

    assertNull(testReport.getSession().getEndedAt());
    assertFalse(testReport.getSession().isCrashed());
    assertNull(testReport.getSession().getUser());

    final long endedAt = System.currentTimeMillis();
    final boolean isCrashed = true;

    final CrashlyticsReport withSessionEndFieldsReport =
        testReport.withSessionEndFields(endedAt, isCrashed, null);

    assertNotEquals(testReport, withSessionEndFieldsReport);
    assertNotNull(withSessionEndFieldsReport.getSession().getEndedAt());
    assertEquals(endedAt, withSessionEndFieldsReport.getSession().getEndedAt().longValue());
    assertEquals(isCrashed, withSessionEndFieldsReport.getSession().isCrashed());
    assertNull(withSessionEndFieldsReport.getSession().getUser());
  }

  @Test
  public void testGetSessionIdUtf8Bytes_returnsProperBytes() {
    final CrashlyticsReport testReport = makeTestReport();
    final byte[] expectedBytes = "identifier".getBytes(Charset.forName("UTF-8"));

    assertArrayEquals(expectedBytes, testReport.getSession().getIdentifierUtf8Bytes());
  }

  @Test
  public void testGetBinaryImageUuidUtf8Bytes_returnsProperBytes() {
    final String expectedUuid = "expectedUuid";
    final byte[] expectedBytes = expectedUuid.getBytes(Charset.forName("UTF-8"));
    final BinaryImage binaryImage =
        BinaryImage.builder()
            .setName("binary")
            .setBaseAddress(0)
            .setSize(100000)
            .setUuid(expectedUuid)
            .build();
    assertArrayEquals(expectedBytes, binaryImage.getUuidUtf8Bytes());
  }

  @Test
  public void testGetBinaryImageUuidUtf8Bytes_returnsNullWhenUuidIsNull() {
    final String expectedUuid = null;
    final byte[] expectedBytes = null;
    final BinaryImage binaryImage =
        BinaryImage.builder()
            .setName("binary")
            .setBaseAddress(0)
            .setSize(100000)
            .setUuid(expectedUuid)
            .build();
    assertArrayEquals(expectedBytes, binaryImage.getUuidUtf8Bytes());
  }

  @Test
  public void testSetSessionIdUtf8Bytes_returnsProperSessionId() {
    final CrashlyticsReport testReport = makeTestReport();
    final String testSessionId = "testSessionId";
    final byte[] utf8Bytes = testSessionId.getBytes(Charset.forName("UTF-8"));

    assertNotEquals(testSessionId, testReport.getSession().getIdentifier());

    final CrashlyticsReport updatedReport =
        testReport.toBuilder()
            .setSession(
                testReport.getSession().toBuilder().setIdentifierFromUtf8Bytes(utf8Bytes).build())
            .build();

    assertEquals(testSessionId, updatedReport.getSession().getIdentifier());
  }

  @Test
  public void testSetBinaryImageUuidUtf8Bytes_returnsProperUuid() {
    final String expectedUuid = "expectedUuid";
    final byte[] expectedBytes = expectedUuid.getBytes(Charset.forName("UTF-8"));
    final BinaryImage binaryImage =
        BinaryImage.builder()
            .setName("binary")
            .setBaseAddress(0)
            .setSize(100000)
            .setUuidFromUtf8Bytes(expectedBytes)
            .build();
    assertEquals(expectedUuid, binaryImage.getUuid());
  }

  @Test
  public void testWithEvents_returnsNewReportWithRolloutsState() {
    final CrashlyticsReport testReport = makeTestReport();

    List<Event> eventList = new ArrayList<Event>();
    eventList.add(makeEventWithRolloutsState());
    final CrashlyticsReport withEventsReport = testReport.withEvents(eventList);

    assertNotEquals(testReport, withEventsReport);
    assertNotNull(withEventsReport.getSession().getEvents());
    assertEquals(1, withEventsReport.getSession().getEvents().size());

    assertNotNull(withEventsReport.getSession().getEvents().get(0).getRollouts());
  }

  private static CrashlyticsReport makeTestReport() {
    return CrashlyticsReport.builder()
        .setSdkVersion("sdkVersion")
        .setGmpAppId("gmpAppId")
        .setPlatform(1)
        .setInstallationUuid("installationId")
        .setBuildVersion("1")
        .setDisplayVersion("1.0.0")
        .setSession(makeTestSession())
        .build();
  }

  private static CrashlyticsReport.Session makeTestSession() {
    return Session.builder()
        .setGenerator("generator")
        .setIdentifier("identifier")
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

  private static List<Event> makeTestEvents(int numEvents) {
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < numEvents; i++) {
      events.add(makeTestEvent());
    }
    return events;
  }

  private static Event makeAnrEvent() {
    return Event.builder()
        .setType("anr")
        .setTimestamp(1000)
        .setApp(
            Session.Event.Application.builder()
                .setBackground(false)
                .setExecution(
                    Execution.builder()
                        .setBinaries(
                            Collections.singletonList(
                                Execution.BinaryImage.builder()
                                    .setBaseAddress(0)
                                    .setName("name")
                                    .setSize(100000)
                                    .setUuid("uuid")
                                    .build()))
                        .setSignal(Signal.builder().setCode("0").setName("0").setAddress(0).build())
                        .setAppExitInfo(makeAppExitInfo())
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

  private static Event makeTestEvent() {
    return Event.builder()
        .setType("test")
        .setTimestamp(1000)
        .setApp(
            Session.Event.Application.builder()
                .setBackground(false)
                .setExecution(
                    Execution.builder()
                        .setBinaries(
                            Collections.singletonList(
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
                            Collections.singletonList(
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

  private static Event makeEventWithRolloutsState() {
    final Event event = makeTestEvent();

    List<Event.RolloutAssignment> rolloutAssignmentList = new ArrayList<Event.RolloutAssignment>();
    rolloutAssignmentList.add(
        Event.RolloutAssignment.builder()
            .setRolloutVariant(
                Event.RolloutAssignment.RolloutVariant.builder()
                    .setRolloutId("rollout_100")
                    .setVariantId("enabled")
                    .build())
            .setParameterValue("true")
            .setParameterKey("my_test_feature")
            .setTemplateVersion(4)
            .build());

    Event.RolloutsState rolloutsState =
        Event.RolloutsState.builder().setRolloutAssignments(rolloutAssignmentList).build();
    final Event eventWithRolloutsState = event.toBuilder().setRollouts(rolloutsState).build();
    return eventWithRolloutsState;
  }

  private static CrashlyticsReport.ApplicationExitInfo makeAppExitInfo() {
    return CrashlyticsReport.ApplicationExitInfo.builder()
        .setTraceFile("trace")
        .setTimestamp(1L)
        .setImportance(1)
        .setReasonCode(1)
        .setProcessName("test")
        .setPid(1)
        .setPss(1)
        .setRss(1)
        .build();
  }

  private static List<Frame> makeTestFrames() {
    ArrayList<Frame> frames = new ArrayList<>();
    frames.add(
        Frame.builder()
            .setPc(0)
            .setSymbol("func1")
            .setFile("Test.java")
            .setOffset(36)
            .setImportance(4)
            .build());
    frames.add(
        Frame.builder()
            .setPc(0)
            .setSymbol("func2")
            .setFile("Test.java")
            .setOffset(5637)
            .setImportance(4)
            .build());
    frames.add(
        Frame.builder()
            .setPc(0)
            .setSymbol("func3")
            .setFile("Test.java")
            .setOffset(22429)
            .setImportance(4)
            .build());
    frames.add(
        Frame.builder()
            .setPc(0)
            .setSymbol("func4")
            .setFile("Test.java")
            .setOffset(751)
            .setImportance(4)
            .build());
    return frames;
  }
}
