package com.google.firebase.crashlytics.internal.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
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
  @Mock private CrashlyticsReport.Session.Event mockEvent;
  @Mock private CrashlyticsReport.Session.Event.Builder mockEventBuilder;
  @Mock private CrashlyticsReport.Session.Event.Application mockEventApp;
  @Mock private CrashlyticsReport.Session.Event.Application.Builder mockEventAppBuilder;

  private SessionReportingCoordinator reportingCoordinator;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    reportingCoordinator =
        new SessionReportingCoordinator(
            dataCapture, reportPersistence, reportSender, logFileManager, reportMetadata);
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
    ApplicationExitInfo testApplicationExitInfo = addAppExitInfo(ApplicationExitInfo.REASON_ANR);

    reportingCoordinator.onBeginSession(sessionId, sessionStartTimestamp);
    reportingCoordinator.persistAppExitInfoEvent(sessionId, testApplicationExitInfo);

    verify(dataCapture)
        .captureEventData(
            any(Throwable.class),
            any(Thread.class),
            eq("anr"),
            anyLong(),
            anyInt(),
            anyInt(),
            anyBoolean());
    verify(reportPersistence)
        .persistAppExitInfoEvent(any(), eq(sessionId), eq(testApplicationExitInfo));
  }

  @Test
  public void testAppExitInfoEvent_notPersistIfAnrBeforeSession() {
    // The timestamp of the applicationExitInfo is 0, and so in this case, we want the session
    // timestamp to be greater than 0.
    final long sessionStartTimestamp = 10;
    final String sessionId = "testSessionId";
    when(reportPersistence.getStartTimestampMillis(sessionId)).thenReturn(sessionStartTimestamp);

    ApplicationExitInfo testApplicationExitInfo = addAppExitInfo(ApplicationExitInfo.REASON_ANR);

    reportingCoordinator.onBeginSession(sessionId, sessionStartTimestamp);
    reportingCoordinator.persistAppExitInfoEvent(sessionId, testApplicationExitInfo);

    verify(dataCapture, never())
            .captureEventData(
                    any(Throwable.class),
                    any(Thread.class),
                    eq("anr"),
                    anyLong(),
                    anyInt(),
                    anyInt(),
                    anyBoolean());
    verify(reportPersistence, never())
        .persistAppExitInfoEvent(any(), eq(sessionId), eq(testApplicationExitInfo));
  }

  @Test
  public void testAppExitInfoEvent_notPersistIfAppExitInfoNotAnrButWithinSession() {
    // The timestamp of the applicationExitInfo is 0, and so in this case, we want the session
    // timestamp to be less than or equal to 0.
    final long sessionStartTimestamp = 0;
    final String sessionId = "testSessionId";
    when(reportPersistence.getStartTimestampMillis(sessionId)).thenReturn(sessionStartTimestamp);

    ApplicationExitInfo testApplicationExitInfo =
        addAppExitInfo(ApplicationExitInfo.REASON_DEPENDENCY_DIED);

    reportingCoordinator.onBeginSession(sessionId, sessionStartTimestamp);
    reportingCoordinator.persistAppExitInfoEvent(sessionId, testApplicationExitInfo);

    verify(dataCapture, never())
            .captureEventData(
                    any(Throwable.class),
                    any(Thread.class),
                    eq("anr"),
                    anyLong(),
                    anyInt(),
                    anyInt(),
                    anyBoolean());
    verify(reportPersistence, never())
        .persistAppExitInfoEvent(any(), eq(sessionId), eq(testApplicationExitInfo));
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

  private ApplicationExitInfo addAppExitInfo(int reason) {
    ActivityManager activityManager =
        (ActivityManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.RunningAppProcessInfo runningAppProcessInfo =
        activityManager.getRunningAppProcesses().get(0);
    shadowOf(activityManager)
        .addApplicationExitInfo(
            runningAppProcessInfo.processName, runningAppProcessInfo.pid, reason, 1);
    return activityManager.getHistoricalProcessExitReasons(null, 0, 0).get(0);
  }
}
