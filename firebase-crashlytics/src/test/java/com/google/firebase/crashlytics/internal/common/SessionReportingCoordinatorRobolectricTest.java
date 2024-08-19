// Copyright 2021 Google LLC
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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.crashlytics.internal.metadata.LogFileManager;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SessionReportingCoordinatorRobolectricTest {
  @Mock private CrashlyticsReportDataCapture dataCapture;
  @Mock private CrashlyticsReportPersistence reportPersistence;
  @Mock private DataTransportCrashlyticsReportSender reportSender;
  @Mock private LogFileManager logFileManager;
  @Mock private UserMetadata reportMetadata;
  @Mock private IdManager idManager;
  @Mock private CrashlyticsReport.Session.Event mockEvent;
  @Mock private CrashlyticsReport.Session.Event.Builder mockEventBuilder;
  @Mock private CrashlyticsReport.Session.Event.Application mockEventApp;
  @Mock private CrashlyticsReport.Session.Event.Application.Builder mockEventAppBuilder;
  @Mock private LogFileManager mockLogFileManager;
  @Mock UserMetadata mockUserMetadata;

  private SessionReportingCoordinator reportingCoordinator;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    reportingCoordinator =
        new SessionReportingCoordinator(
            dataCapture,
            reportPersistence,
            reportSender,
            logFileManager,
            reportMetadata,
            idManager);
    mockEventInteractions();
  }

  @Test
  public void testAppExitInfoEvent_persistIfAnrWithinSession() {
    // The timestamp of the applicationExitInfo is 0, and so in this case, we want the session
    // timestamp to be less than or equal to 0.
    final long sessionStartTimestamp = 0;
    final String sessionId = "testSessionId";
    when(reportPersistence.getStartTimestampMillis(sessionId)).thenReturn(sessionStartTimestamp);

    mockEventInteractions();
    addAppExitInfo(ApplicationExitInfo.REASON_ANR);
    List<ApplicationExitInfo> testApplicationExitInfoList = getAppExitInfoList();

    reportingCoordinator.onBeginSession(sessionId, sessionStartTimestamp);
    reportingCoordinator.persistRelevantAppExitInfoEvent(
        sessionId, testApplicationExitInfoList, mockLogFileManager, mockUserMetadata);

    verify(dataCapture)
        .captureAnrEventData(convertApplicationExitInfo(testApplicationExitInfoList.get(0)));
    verify(reportPersistence).persistEvent(any(), eq(sessionId), eq(true));
  }

  @Test
  public void testAppExitInfoEvent_persistIfAnrWithinSession_multipleAppExitInfo() {
    // The timestamp of the applicationExitInfo is 0, and so in this case, we want the session
    // timestamp to be less than or equal to 0.
    final long sessionStartTimestamp = 0;
    final String sessionId = "testSessionId";
    when(reportPersistence.getStartTimestampMillis(sessionId)).thenReturn(sessionStartTimestamp);

    mockEventInteractions();
    addAppExitInfo(ApplicationExitInfo.REASON_ANR);
    addAppExitInfo(ApplicationExitInfo.REASON_EXIT_SELF);
    List<ApplicationExitInfo> testApplicationExitInfoList = getAppExitInfoList();

    reportingCoordinator.onBeginSession(sessionId, sessionStartTimestamp);
    reportingCoordinator.persistRelevantAppExitInfoEvent(
        sessionId, testApplicationExitInfoList, mockLogFileManager, mockUserMetadata);

    verify(dataCapture)
        .captureAnrEventData(convertApplicationExitInfo(testApplicationExitInfoList.get(1)));
    verify(reportPersistence).persistEvent(any(), eq(sessionId), eq(true));
  }

  @Test
  public void testAppExitInfoEvent_notPersistIfAnrBeforeSession() {
    // The timestamp of the applicationExitInfo is 0, and so in this case, we want the session
    // timestamp to be greater than 0.
    final long sessionStartTimestamp = 10;
    final String sessionId = "testSessionId";
    when(reportPersistence.getStartTimestampMillis(sessionId)).thenReturn(sessionStartTimestamp);

    addAppExitInfo(ApplicationExitInfo.REASON_ANR);
    List<ApplicationExitInfo> testApplicationExitInfoList = getAppExitInfoList();

    reportingCoordinator.onBeginSession(sessionId, sessionStartTimestamp);
    reportingCoordinator.persistRelevantAppExitInfoEvent(
        sessionId, testApplicationExitInfoList, mockLogFileManager, mockUserMetadata);

    verify(dataCapture, never())
        .captureAnrEventData(convertApplicationExitInfo(testApplicationExitInfoList.get(0)));
    verify(reportPersistence, never()).persistEvent(any(), eq(sessionId), eq(true));
  }

  @Test
  public void testAppExitInfoEvent_notPersistIfAppExitInfoNotAnrButWithinSession() {
    // The timestamp of the applicationExitInfo is 0, and so in this case, we want the session
    // timestamp to be less than or equal to 0.
    final long sessionStartTimestamp = 0;
    final String sessionId = "testSessionId";
    when(reportPersistence.getStartTimestampMillis(sessionId)).thenReturn(sessionStartTimestamp);
    addAppExitInfo(ApplicationExitInfo.REASON_DEPENDENCY_DIED);
    List<ApplicationExitInfo> testApplicationExitInfoList = getAppExitInfoList();

    reportingCoordinator.onBeginSession(sessionId, sessionStartTimestamp);
    reportingCoordinator.persistRelevantAppExitInfoEvent(
        sessionId, testApplicationExitInfoList, mockLogFileManager, mockUserMetadata);

    verify(dataCapture, never())
        .captureAnrEventData(convertApplicationExitInfo(testApplicationExitInfoList.get(0)));
    verify(reportPersistence, never()).persistEvent(any(), eq(sessionId), eq(true));
  }

  @Test
  public void testconvertInputStreamToString_worksSuccessfully() throws IOException {
    String stackTrace = "-----stacktrace---------";
    InputStream inputStream = new ByteArrayInputStream(stackTrace.getBytes());
    assertEquals(stackTrace, SessionReportingCoordinator.convertInputStreamToString(inputStream));
  }

  private void mockEventInteractions() {
    when(mockEvent.toBuilder()).thenReturn(mockEventBuilder);
    when(mockEventBuilder.build()).thenReturn(mockEvent);
    when(mockEvent.getApp()).thenReturn(mockEventApp);
    when(mockEventApp.toBuilder()).thenReturn(mockEventAppBuilder);
    when(mockEventAppBuilder.setCustomAttributes(any())).thenReturn(mockEventAppBuilder);
    when(mockEventAppBuilder.build()).thenReturn(mockEventApp);
    when(dataCapture.captureAnrEventData(any(CrashlyticsReport.ApplicationExitInfo.class)))
        .thenReturn(mockEvent);
  }

  private void addAppExitInfo(int reason) {
    ActivityManager activityManager =
        (ActivityManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.RunningAppProcessInfo runningAppProcessInfo =
        activityManager.getRunningAppProcesses().get(0);
    shadowOf(activityManager)
        .addApplicationExitInfo(
            runningAppProcessInfo.processName, runningAppProcessInfo.pid, reason, 1);
    return;
  }

  private List<ApplicationExitInfo> getAppExitInfoList() {
    ActivityManager activityManager =
        (ActivityManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.RunningAppProcessInfo runningAppProcessInfo =
        activityManager.getRunningAppProcesses().get(0);
    return activityManager.getHistoricalProcessExitReasons(null, 0, 0);
  }

  @RequiresApi(api = Build.VERSION_CODES.R)
  private static CrashlyticsReport.ApplicationExitInfo convertApplicationExitInfo(
      ApplicationExitInfo applicationExitInfo) {
    // The ApplicationExitInfo inserted by ShadowApplicationManager does not contain an input trace,
    // and so it will be null.
    // Thus, these tests verify that an ApplicationExitInfo is successfully converted even when the
    // input trace fails to be parsed as a string.
    return CrashlyticsReport.ApplicationExitInfo.builder()
        .setImportance(applicationExitInfo.getImportance())
        .setProcessName(applicationExitInfo.getProcessName())
        .setReasonCode(applicationExitInfo.getReason())
        .setTimestamp(applicationExitInfo.getTimestamp())
        .setPid(applicationExitInfo.getPid())
        .setPss(applicationExitInfo.getPss())
        .setRss(applicationExitInfo.getRss())
        .setTraceFile(null)
        .build();
  }
}
