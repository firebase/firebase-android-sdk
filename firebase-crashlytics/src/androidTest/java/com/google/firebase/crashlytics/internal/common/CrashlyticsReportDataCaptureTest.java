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

import static com.google.firebase.crashlytics.internal.common.CrashlyticsReportDataCapture.GENERATOR;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsReportDataCapture.GENERATOR_TYPE;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution;
import com.google.firebase.crashlytics.internal.stacktrace.StackTraceTrimmingStrategy;
import com.google.firebase.crashlytics.internal.unity.UnityVersionProvider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class CrashlyticsReportDataCaptureTest {

  private CrashlyticsReportDataCapture dataCapture;
  private long timestamp;
  private String eventType;
  private int eventThreadImportance;
  private int maxChainedExceptions;

  @Mock private UnityVersionProvider unityVersionProvider;

  @Mock private StackTraceTrimmingStrategy stackTraceTrimmingStrategy;

  @Mock private FirebaseInstallationsApi installationsApiMock;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(installationsApiMock.getId()).thenReturn(Tasks.forResult("installId"));
    when(stackTraceTrimmingStrategy.getTrimmedStackTrace(any(StackTraceElement[].class)))
        .thenAnswer(i -> i.getArguments()[0]);
    final Context context = getContext();
    final IdManager idManager =
        new IdManager(
            context,
            context.getPackageName(),
            installationsApiMock,
            DataCollectionArbiterTest.MOCK_ARBITER_ENABLED);
    final AppData appData =
        AppData.create(context, idManager, "googleAppId", "buildId", unityVersionProvider);
    dataCapture =
        new CrashlyticsReportDataCapture(context, idManager, appData, stackTraceTrimmingStrategy);
    timestamp = System.currentTimeMillis();
    eventType = "crash";
    eventThreadImportance = 4;
    maxChainedExceptions = 8;
  }

  @Test
  public void testCaptureReport_containsUnityVersionInDeveloperPlatformFieldsWhenAvailable() {
    final String expectedUnityVersion = "1.0.0";
    when(unityVersionProvider.getUnityVersion()).thenReturn(expectedUnityVersion);

    final CrashlyticsReport report = dataCapture.captureReportData("sessionId", 0);

    assertEquals("Unity", report.getSession().getApp().getDevelopmentPlatform());
    assertEquals(
        expectedUnityVersion, report.getSession().getApp().getDevelopmentPlatformVersion());
  }

  @Test
  public void testCaptureReport_containsNoDeveloperPlatformFieldsWhenUnityIsMissing() {
    when(unityVersionProvider.getUnityVersion()).thenReturn(null);

    final CrashlyticsReport report = dataCapture.captureReportData("sessionId", 0);

    assertNull(report.getSession().getApp().getDevelopmentPlatform());
    assertNull(report.getSession().getApp().getDevelopmentPlatformVersion());
  }

  @Test
  public void testCaptureAnrEvent_foregroundAnr() {
    CrashlyticsReport.ApplicationExitInfo testApplicationExitInfo = makeAppExitInfo(false);
    final CrashlyticsReport.Session.Event event =
        dataCapture.captureAnrEventData(testApplicationExitInfo);

    assertEquals("anr", event.getType());
    assertEquals(testApplicationExitInfo, event.getApp().getExecution().getAppExitInfo());
    assertEquals(testApplicationExitInfo.getTimestamp(), event.getTimestamp());
    assertEquals(false, event.getApp().getBackground());
  }

  @Test
  public void testCaptureAnrEvent_backgroundAnr() {
    CrashlyticsReport.ApplicationExitInfo testApplicationExitInfo = makeAppExitInfo(true);
    final CrashlyticsReport.Session.Event event =
        dataCapture.captureAnrEventData(testApplicationExitInfo);

    assertEquals("anr", event.getType());
    assertEquals(testApplicationExitInfo, event.getApp().getExecution().getAppExitInfo());
    assertEquals(testApplicationExitInfo.getTimestamp(), event.getTimestamp());
    assertEquals(true, event.getApp().getBackground());
  }

  @Test
  public void testCaptureReportSessionFields() {
    final String sessionId = "sessionId";
    final long timestamp = System.currentTimeMillis();
    final CrashlyticsReport report = dataCapture.captureReportData(sessionId, timestamp);

    assertEquals(sessionId, report.getSession().getIdentifier());
    assertEquals(timestamp, report.getSession().getStartedAt());
    assertEquals(GENERATOR, report.getSession().getGenerator());
    assertEquals(GENERATOR_TYPE, report.getSession().getGeneratorType());
  }

  @Test
  public void testCaptureEvent_noChainedExceptionsAllThreads() {
    final RuntimeException eventException = new RuntimeException("fatal");
    final Thread eventThread = Thread.currentThread();

    final CrashlyticsReport.Session.Event event =
        dataCapture.captureEventData(
            eventException,
            eventThread,
            eventType,
            timestamp,
            eventThreadImportance,
            maxChainedExceptions,
            true);

    assertEquals(eventType, event.getType());
    assertEquals(timestamp, event.getTimestamp());
    final List<Execution.Thread> threads = event.getApp().getExecution().getThreads();
    assertTrue(threads.size() > 1);
    final Execution.Thread firstThread = threads.get(0);
    assertThread(firstThread, eventThread.getName(), eventThreadImportance);
    for (int i = 1; i < threads.size(); ++i) {
      assertThread(threads.get(i), 0);
    }

    final Execution.Exception builtException = event.getApp().getExecution().getException();
    assertException(eventException, builtException, 0, eventThreadImportance);
    assertNull(builtException.getCausedBy());

    assertArrayEquals(firstThread.getFrames().toArray(), builtException.getFrames().toArray());

    assertEquals(1, event.getApp().getExecution().getBinaries().size());

    assertNotNull(event.getApp().getExecution().getSignal());
  }

  @Test
  public void testCaptureEvent_someChainedExceptionsAllThreads() {
    final IllegalStateException cause2 = new IllegalStateException("cause 2");
    final IllegalArgumentException cause = new IllegalArgumentException("cause", cause2);
    final RuntimeException eventException = new RuntimeException("fatal", cause);
    final Thread eventThread = Thread.currentThread();

    final CrashlyticsReport.Session.Event event =
        dataCapture.captureEventData(
            eventException,
            eventThread,
            eventType,
            timestamp,
            eventThreadImportance,
            maxChainedExceptions,
            true);

    assertEquals(eventType, event.getType());
    assertEquals(timestamp, event.getTimestamp());
    final List<Execution.Thread> threads = event.getApp().getExecution().getThreads();
    assertTrue(threads.size() > 1);
    final Execution.Thread firstThread = threads.get(0);
    assertThread(firstThread, eventThread.getName(), eventThreadImportance);
    for (int i = 1; i < threads.size(); ++i) {
      assertThread(threads.get(i), 0);
    }

    final Execution.Exception builtException = event.getApp().getExecution().getException();
    assertException(eventException, builtException, 0, eventThreadImportance);

    Execution.Exception builtCause = builtException.getCausedBy();
    assertNotNull(builtCause);

    assertException(cause, builtCause, 0, eventThreadImportance);

    builtCause = builtCause.getCausedBy();
    assertNotNull(builtCause);

    assertException(cause2, builtCause, 0, eventThreadImportance);

    assertNull(builtCause.getCausedBy());

    assertEquals(1, event.getApp().getExecution().getBinaries().size());

    assertNotNull(event.getApp().getExecution().getSignal());
  }

  @Test
  public void testCaptureEvent_noChainedExceptionsOneThread() {
    final RuntimeException eventException = new RuntimeException("fatal");
    final Thread eventThread = Thread.currentThread();

    final CrashlyticsReport.Session.Event event =
        dataCapture.captureEventData(
            eventException,
            eventThread,
            eventType,
            timestamp,
            eventThreadImportance,
            maxChainedExceptions,
            false);

    assertEquals(eventType, event.getType());
    assertEquals(timestamp, event.getTimestamp());
    final List<Execution.Thread> threads = event.getApp().getExecution().getThreads();
    assertEquals(1, threads.size());
    final Execution.Thread firstThread = threads.get(0);
    assertThread(firstThread, eventThread.getName(), eventThreadImportance);

    final Execution.Exception builtException = event.getApp().getExecution().getException();
    assertException(eventException, builtException, 0, eventThreadImportance);
    assertNull(builtException.getCausedBy());

    assertEquals(1, event.getApp().getExecution().getBinaries().size());

    assertNotNull(event.getApp().getExecution().getSignal());
  }

  @Test
  public void testCaptureEventBatteryLevel() {
    final double expectedBatteryLevel = 0.25;
    final RuntimeException eventException = new RuntimeException("fatal");
    final Thread eventThread = Thread.currentThread();

    final CrashlyticsReport.Session.Event event =
        dataCapture.captureEventData(
            eventException,
            eventThread,
            eventType,
            timestamp,
            eventThreadImportance,
            maxChainedExceptions,
            true);

    assertNotNull(event.getDevice().getBatteryLevel());
    assertEquals(expectedBatteryLevel, event.getDevice().getBatteryLevel(), 0.1);
  }

  @Test
  public void testCaptureEventBatteryLevel_notWrittenWhenBatteryIntentIsNull() {
    final RuntimeException eventException = new RuntimeException("fatal");
    final Thread eventThread = Thread.currentThread();

    BatteryIntentProvider.returnNull = true;

    final CrashlyticsReport.Session.Event event =
        dataCapture.captureEventData(
            eventException,
            eventThread,
            eventType,
            timestamp,
            eventThreadImportance,
            maxChainedExceptions,
            true);

    assertNull(event.getDevice().getBatteryLevel());
  }

  @Test
  public void testCaptureEvent_overflowChainedExceptionsOneThread() {
    final IllegalStateException cause2 = new IllegalStateException("cause 2");
    final IllegalArgumentException cause = new IllegalArgumentException("cause", cause2);
    final RuntimeException eventException = new RuntimeException("fatal", cause);
    final Thread eventThread = Thread.currentThread();

    final CrashlyticsReport.Session.Event event =
        dataCapture.captureEventData(
            eventException, eventThread, eventType, timestamp, eventThreadImportance, 1, false);

    assertEquals(eventType, event.getType());
    assertEquals(timestamp, event.getTimestamp());
    final List<Execution.Thread> threads = event.getApp().getExecution().getThreads();
    assertEquals(1, threads.size());
    final Execution.Thread firstThread = threads.get(0);
    assertThread(firstThread, eventThread.getName(), eventThreadImportance);

    final Execution.Exception builtException = event.getApp().getExecution().getException();
    assertException(eventException, builtException, 0, eventThreadImportance);

    Execution.Exception builtCause = builtException.getCausedBy();
    assertNotNull(builtCause);

    assertException(cause, builtCause, 1, eventThreadImportance);
    assertNull(builtCause.getCausedBy());

    assertEquals(1, event.getApp().getExecution().getBinaries().size());

    assertNotNull(event.getApp().getExecution().getSignal());
  }

  private static void assertThread(
      Execution.Thread thread, String expectedName, int expectedImportance) {
    assertEquals(expectedName, thread.getName());
    assertThread(thread, expectedImportance);
  }

  private static void assertThread(Execution.Thread thread, int expectedImportance) {
    int threadImportance = thread.getImportance();
    assertEquals(expectedImportance, threadImportance);
    assertFrameImportance(thread.getFrames(), threadImportance);
  }

  private static void assertException(
      Exception actualException,
      Execution.Exception builtException,
      int expectedOverflowCount,
      int expectedImportance) {
    assertEquals(actualException.getMessage(), builtException.getReason());
    assertEquals(expectedOverflowCount, builtException.getOverflowCount());
    assertFalse(builtException.getFrames().isEmpty());
    for (Execution.Thread.Frame frame : builtException.getFrames()) {
      assertEquals(expectedImportance, frame.getImportance());
    }
  }

  private static void assertFrameImportance(
      List<Execution.Thread.Frame> frames, int expectedImportance) {
    for (Execution.Thread.Frame frame : frames) {
      assertEquals(expectedImportance, frame.getImportance());
    }
  }

  private static class BatteryIntentProvider {
    public static boolean returnNull;

    public static Intent getBatteryIntent() {
      if (returnNull) {
        return null;
      }

      final Intent intent = new Intent();
      // Set the battery level to 25% and charging.
      intent.putExtra(BatteryManager.EXTRA_LEVEL, 50);
      intent.putExtra(BatteryManager.EXTRA_SCALE, 200);
      intent.putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING);
      return intent;
    }
  }

  public Context getContext() {
    // Return a context wrapper that will allow us to override the behavior of registering
    // the receiver for battery changed events.
    return new ContextWrapper(ApplicationProvider.getApplicationContext()) {
      @Override
      public Context getApplicationContext() {
        return this;
      }

      @Override
      public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        // For the BatteryIntent, use test values to avoid breaking from emulator changes.
        if (filter.hasAction(Intent.ACTION_BATTERY_CHANGED)) {
          // If we ever call this with a receiver, it will be broken.
          assertNull(receiver);
          return BatteryIntentProvider.getBatteryIntent();
        }
        return getBaseContext().registerReceiver(receiver, filter);
      }
    };
  }

  private static CrashlyticsReport.ApplicationExitInfo makeAppExitInfo(boolean isBackground) {
    final int anrImportance =
        isBackground
            ? RunningAppProcessInfo.IMPORTANCE_CACHED
            : ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    return CrashlyticsReport.ApplicationExitInfo.builder()
        .setTraceFile("trace")
        .setTimestamp(1L)
        .setImportance(anrImportance)
        .setReasonCode(1)
        .setProcessName("test")
        .setPid(1)
        .setPss(1L)
        .setRss(1L)
        .build();
  }
}
