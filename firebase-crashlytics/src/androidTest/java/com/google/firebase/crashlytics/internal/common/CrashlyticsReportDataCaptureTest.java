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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution;
import com.google.firebase.crashlytics.internal.stacktrace.StackTraceTrimmingStrategy;
import com.google.firebase.crashlytics.internal.unity.ResourceUnityVersionProvider;
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

  @Mock private StackTraceTrimmingStrategy stackTraceTrimmingStrategy;

  @Mock private FirebaseInstallationsApi installationsApiMock;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(installationsApiMock.getId()).thenReturn(Tasks.forResult("installId"));
    when(stackTraceTrimmingStrategy.getTrimmedStackTrace(any(StackTraceElement[].class)))
        .thenAnswer(i -> i.getArguments()[0]);
    final Context context = ApplicationProvider.getApplicationContext();
    final IdManager idManager =
        new IdManager(context, context.getPackageName(), installationsApiMock);
    final UnityVersionProvider unityVersionProvider = new ResourceUnityVersionProvider(context);
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
  public void testEventBuilderHandler_noChainedExceptionsAllThreads() {
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
  public void testEventBuilderHandler_someChainedExceptionsAllThreads() {
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
  public void testEventBuilderHandler_noChainedExceptionsOneThread() {
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
  public void testEventBuilderHandler_overflowChainedExceptionsOneThread() {
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
}
