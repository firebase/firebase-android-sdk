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

package com.google.firebase.crashlytics.internal.model.serialization;

import static org.junit.Assert.*;

import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Application;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.Signal;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.Thread.Frame;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.User;
import com.google.firebase.crashlytics.internal.model.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class CrashlyticsReportJsonTransformTest {

  private CrashlyticsReportJsonTransform transform;

  @Before
  public void setUp() {
    transform = new CrashlyticsReportJsonTransform();
  }

  @Test
  public void testReportToJsonAndBack_equals() throws IOException {
    final CrashlyticsReport testReport = makeTestReport(false);
    final String testReportJson = transform.reportToJson(testReport);
    final CrashlyticsReport reifiedReport = transform.reportFromJson(testReportJson);
    assertNotSame(reifiedReport, testReport);
    assertEquals(reifiedReport, testReport);
  }

  @Test
  public void testReportToJsonAndBack_with_developmentPlatform_equals() throws IOException {
    final CrashlyticsReport testReport = makeTestReport(true);
    final String testReportJson = transform.reportToJson(testReport);
    final CrashlyticsReport reifiedReport = transform.reportFromJson(testReportJson);
    assertNotSame(reifiedReport, testReport);
    assertEquals(reifiedReport, testReport);
  }

  @Test
  public void testEventToJsonAndBack_equals() throws IOException {
    final CrashlyticsReport.Session.Event testEvent = makeTestEvent();
    final String testEventJson = transform.eventToJson(testEvent);
    final CrashlyticsReport.Session.Event reifiedEvent = transform.eventFromJson(testEventJson);
    assertNotSame(reifiedEvent, testEvent);
    assertEquals(reifiedEvent, testEvent);
  }

  private static CrashlyticsReport makeTestReport(boolean useDevelopmentPlatform) {
    return CrashlyticsReport.builder()
        .setSdkVersion("sdkVersion")
        .setGmpAppId("gmpAppId")
        .setPlatform(1)
        .setInstallationUuid("installationId")
        .setBuildVersion("1")
        .setDisplayVersion("1.0.0")
        .setSession(makeTestSession(useDevelopmentPlatform))
        .build();
  }

  private static CrashlyticsReport.Session makeTestSession(boolean useDevelopmentPlatform) {
    return Session.builder()
        .setGenerator("generator")
        .setIdentifier("identifier")
        .setStartedAt(1L)
        .setEndedAt(1L)
        .setCrashed(true)
        .setApp(makeTestApplication(useDevelopmentPlatform))
        .setUser(User.builder().setIdentifier("user").build())
        .setGeneratorType(3)
        .build();
  }

  private static Application makeTestApplication(boolean useDevelopmentPlatform) {
    final Application.Builder builder =
        Application.builder()
            .setIdentifier("applicationId")
            .setVersion("version")
            .setDisplayVersion("displayVersion");
    if (useDevelopmentPlatform) {
      builder
          .setDevelopmentPlatform("developmentPlatform")
          .setDevelopmentPlatformVersion("developmentPlatformVersion");
    }
    return builder.build();
  }

  private static ImmutableList<Event> makeTestEvents(int numEvents) {
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < numEvents; i++) {
      events.add(makeTestEvent());
    }
    return ImmutableList.from(events);
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
            .setPc(0)
            .setSymbol("func2")
            .setFile("Test.java")
            .setOffset(5637)
            .setImportance(4)
            .build(),
        Frame.builder()
            .setPc(0)
            .setSymbol("func3")
            .setFile("Test.java")
            .setOffset(22429)
            .setImportance(4)
            .build(),
        Frame.builder()
            .setPc(0)
            .setSymbol("func4")
            .setFile("Test.java")
            .setOffset(751)
            .setImportance(4)
            .build());
  }
}
