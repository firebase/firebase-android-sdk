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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.test.filters.SdkSuppress;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.DevelopmentPlatformProvider;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers;
import com.google.firebase.crashlytics.internal.metadata.EventMetadata;
import com.google.firebase.crashlytics.internal.metadata.LogFileManager;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.Settings;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
import com.google.firebase.crashlytics.internal.settings.TestSettings;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class CrashlyticsControllerTest extends CrashlyticsTestCase {
  private static final String GOOGLE_APP_ID = "google:app:id";
  private static final String SESSION_ID = "session_id";

  private final CrashlyticsWorkers crashlyticsWorkers =
      new CrashlyticsWorkers(TestOnlyExecutors.background(), TestOnlyExecutors.blocking());

  private Context testContext;
  private IdManager idManager;
  private SettingsProvider testSettingsProvider;
  private FileStore testFileStore;
  private SessionReportingCoordinator mockSessionReportingCoordinator;
  private DataCollectionArbiter mockDataCollectionArbiter;
  private CrashlyticsNativeComponent mockNativeComponent = mock(CrashlyticsNativeComponent.class);

  @Before
  public void setUp() throws Exception {
    testContext = getContext();

    FirebaseInstallationsApi installationsApiMock = mock(FirebaseInstallationsApi.class);
    when(installationsApiMock.getId()).thenReturn(Tasks.forResult("instanceId"));
    idManager =
        new IdManager(
            testContext,
            testContext.getPackageName(),
            installationsApiMock,
            DataCollectionArbiterTest.MOCK_ARBITER_ENABLED);

    testFileStore = new FileStore(testContext);

    Settings testSettings = new TestSettings(3);

    mockSessionReportingCoordinator = mock(SessionReportingCoordinator.class);

    mockDataCollectionArbiter = mock(DataCollectionArbiter.class);
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(true);

    testSettingsProvider = mock(SettingsProvider.class);
    when(testSettingsProvider.getSettingsSync()).thenReturn(testSettings);
    when(testSettingsProvider.getSettingsAsync()).thenReturn(Tasks.forResult(testSettings));
  }

  @After
  public void tearDown() throws Exception {
    crashlyticsWorkers.common.await();
  }

  /** A convenience class for building CrashlyticsController instances for testing. */
  private class ControllerBuilder {
    private DataCollectionArbiter dataCollectionArbiter;
    private CrashlyticsNativeComponent nativeComponent = null;
    private AnalyticsEventLogger analyticsEventLogger;
    private SessionReportingCoordinator sessionReportingCoordinator;

    private LogFileManager logFileManager = null;

    private UserMetadata userMetadata = null;

    ControllerBuilder() {
      dataCollectionArbiter = mockDataCollectionArbiter;
      nativeComponent = mockNativeComponent;

      analyticsEventLogger = mock(AnalyticsEventLogger.class);

      sessionReportingCoordinator = mockSessionReportingCoordinator;
    }

    ControllerBuilder setDataCollectionArbiter(DataCollectionArbiter arbiter) {
      dataCollectionArbiter = arbiter;
      return this;
    }

    ControllerBuilder setUserMetadata(UserMetadata userMetadata) {
      this.userMetadata = userMetadata;
      return this;
    }

    public ControllerBuilder setNativeComponent(CrashlyticsNativeComponent nativeComponent) {
      this.nativeComponent = nativeComponent;
      return this;
    }

    public ControllerBuilder setLogFileManager(LogFileManager logFileManager) {
      this.logFileManager = logFileManager;
      return this;
    }

    public ControllerBuilder setAnalyticsEventLogger(AnalyticsEventLogger logger) {
      analyticsEventLogger = logger;
      return this;
    }

    public CrashlyticsController build() {

      CrashlyticsFileMarker crashMarker =
          new CrashlyticsFileMarker(CrashlyticsCore.CRASH_MARKER_FILE_NAME, testFileStore);

      List<BuildIdInfo> buildIdInfoList = new ArrayList<>();
      buildIdInfoList.add(new BuildIdInfo("lib.so", "x86", "aabb"));
      AppData appData =
          new AppData(
              GOOGLE_APP_ID,
              "buildId",
              buildIdInfoList,
              "installerPackageName",
              "packageName",
              "versionCode",
              "versionName",
              mock(DevelopmentPlatformProvider.class));

      final CrashlyticsController controller =
          new CrashlyticsController(
              testContext.getApplicationContext(),
              idManager,
              dataCollectionArbiter,
              testFileStore,
              crashMarker,
              appData,
              userMetadata,
              logFileManager,
              sessionReportingCoordinator,
              nativeComponent,
              analyticsEventLogger,
              mock(CrashlyticsAppQualitySessionsSubscriber.class),
              crashlyticsWorkers);
      return controller;
    }
  }

  private ControllerBuilder builder() {
    return new ControllerBuilder();
  }

  /** Creates a new CrashlyticsController with default options and opens a session. */
  private CrashlyticsController createController() {
    final CrashlyticsController controller = builder().build();
    controller.openSession(SESSION_ID);
    return controller;
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testWriteNonFatal_callsSessionReportingCoordinatorPersistNonFatal() throws Exception {
    final String sessionId = "sessionId";
    final Thread thread = Thread.currentThread();
    final Exception nonFatal = new RuntimeException("Non-fatal");
    final CrashlyticsController controller = createController();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Collections.singleton(sessionId)));

    controller.writeNonFatalException(thread, nonFatal, Map.of());
    crashlyticsWorkers.common.submit(() -> controller.doCloseSessions(testSettingsProvider));

    crashlyticsWorkers.common.await();
    crashlyticsWorkers.diskWrite.await();

    verify(mockSessionReportingCoordinator)
        .persistNonFatalEvent(eq(nonFatal), eq(thread), any(EventMetadata.class));
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testFatalException_callsSessionReportingCoordinatorPersistFatal() throws Exception {
    final String sessionId = "sessionId";
    final Thread thread = Thread.currentThread();
    final Exception fatal = new RuntimeException("Fatal");
    final CrashlyticsController controller = createController();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Collections.singleton(sessionId)));

    controller.handleUncaughtException(testSettingsProvider, thread, fatal);

    verify(mockSessionReportingCoordinator)
        .persistFatalEvent(eq(fatal), eq(thread), eq(sessionId), anyLong());
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testOnDemandFatal_callLogFatalException() throws Exception {
    Thread thread = Thread.currentThread();
    Exception fatal = new RuntimeException("Fatal");
    Thread.UncaughtExceptionHandler exceptionHandler = mock(Thread.UncaughtExceptionHandler.class);
    UserMetadata mockUserMetadata = mock(UserMetadata.class);
    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Collections.singleton(SESSION_ID)).descendingSet());

    final CrashlyticsController controller =
        builder()
            .setLogFileManager(new LogFileManager(testFileStore))
            .setUserMetadata(mockUserMetadata)
            .build();
    controller.enableExceptionHandling(SESSION_ID, exceptionHandler, testSettingsProvider);
    controller.logFatalException(thread, fatal);

    crashlyticsWorkers.common.await();

    verify(mockUserMetadata).setNewSession(not(eq(SESSION_ID)));
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testNativeCrashDataCausesNativeReport() throws Exception {
    final String sessionId = "sessionId_1_new";
    final String previousSessionId = "sessionId_0_previous";

    final File testDir = testFileStore.getNativeSessionDir(previousSessionId);

    final File minidump = new File(testDir, "crash.dmp");
    final File metadata = new File(testDir, "crash.device_info");
    final File session = new File(testDir, "session.json");
    final File app = new File(testDir, "app.json");
    final File device = new File(testDir, "device.json");
    final File os = new File(testDir, "os.json");

    TestUtils.writeStringToFile("minidump", minidump);
    TestUtils.writeStringToFile("metadata", metadata);
    TestUtils.writeStringToFile("session", session);
    TestUtils.writeStringToFile("app", app);
    TestUtils.writeStringToFile("device", device);
    TestUtils.writeStringToFile("os", os);

    final CrashlyticsNativeComponent mockNativeComponent = mock(CrashlyticsNativeComponent.class);
    when(mockNativeComponent.hasCrashDataForSession(anyString())).thenReturn(true);
    when(mockNativeComponent.getSessionFileProvider(anyString()))
        .thenReturn(
            new NativeSessionFileProvider() {
              @Override
              public File getMinidumpFile() {
                return minidump;
              }

              @Override
              public CrashlyticsReport.ApplicationExitInfo getApplicationExitInto() {
                return null;
              }

              @Override
              public File getBinaryImagesFile() {
                return null;
              }

              @Override
              public File getMetadataFile() {
                return metadata;
              }

              @Override
              public File getSessionFile() {
                return session;
              }

              @Override
              public File getAppFile() {
                return app;
              }

              @Override
              public File getDeviceFile() {
                return device;
              }

              @Override
              public File getOsFile() {
                return os;
              }
            });

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Arrays.asList(sessionId, previousSessionId)).descendingSet());

    final LogFileManager logFileManager = new LogFileManager(testFileStore, sessionId);
    final CrashlyticsController controller =
        builder().setNativeComponent(mockNativeComponent).setLogFileManager(logFileManager).build();

    crashlyticsWorkers.common.submit(() -> controller.finalizeSessions(testSettingsProvider));
    crashlyticsWorkers.common.await();

    verify(mockSessionReportingCoordinator)
        .finalizeSessionWithNativeEvent(eq(previousSessionId), any(), any());
    verify(mockSessionReportingCoordinator, never())
        .finalizeSessionWithNativeEvent(eq(sessionId), any(), any());
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testMissingNativeComponentCausesNoReports() throws Exception {
    final CrashlyticsController controller = createController();
    crashlyticsWorkers.common.submit(() -> controller.finalizeSessions(testSettingsProvider));
    crashlyticsWorkers.common.await();

    List<String> sessions = testFileStore.getAllOpenSessionIds();
    for (String sessionId : sessions) {
      final File[] nativeSessionFiles = testFileStore.getNativeSessionDir(sessionId).listFiles();
      assertEquals(0, nativeSessionFiles.length);
    }
  }

  /**
   * Crashing should shut down the executor service, but we don't want further calls that would use
   * it to throw exceptions!
   */
  // FIXME: Validate this test is working as intended
  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testLoggedExceptionsAfterCrashOk() {
    final CrashlyticsController controller = builder().build();
    controller.handleUncaughtException(
        testSettingsProvider, Thread.currentThread(), new RuntimeException());

    // This should not throw.
    controller.writeNonFatalException(Thread.currentThread(), new RuntimeException(), Map.of());
  }

  /**
   * Crashing should shut down the executor service, but we don't want further calls that would use
   * it to throw exceptions!
   */
  // FIXME: Validate this test works as intended
  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testLogStringAfterCrashOk() throws Exception {
    final CrashlyticsController controller = builder().build();
    controller.handleUncaughtException(
        testSettingsProvider, Thread.currentThread(), new RuntimeException());

    // This should not throw.
    crashlyticsWorkers.diskWrite.submit(
        () -> controller.writeToLog(System.currentTimeMillis(), "Hi"));
    crashlyticsWorkers.diskWrite.await();
  }

  /**
   * Crashing should shut down the executor service, but we don't want further calls that would use
   * it to throw exceptions!
   */
  // FIXME: Validate this test works as intended
  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testFinalizeSessionAfterCrashOk() throws Exception {
    final CrashlyticsController controller = builder().build();
    controller.handleUncaughtException(
        testSettingsProvider, Thread.currentThread(), new RuntimeException());

    // This should not throw.
    crashlyticsWorkers.common.submit(() -> controller.finalizeSessions(testSettingsProvider));
    crashlyticsWorkers.common.await();
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testUploadWithNoReports() throws Exception {
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(false);

    final CrashlyticsController controller = createController();

    controller.submitAllReports(testSettingsProvider.getSettingsAsync());

    crashlyticsWorkers.common.await();

    verify(mockSessionReportingCoordinator).hasReportsToSend();
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testUploadWithDataCollectionAlwaysEnabled() throws Exception {
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(true);
    when(mockSessionReportingCoordinator.sendReports(any(Executor.class)))
        .thenReturn(Tasks.forResult(null));

    final CrashlyticsController controller = createController();

    controller.submitAllReports(testSettingsProvider.getSettingsAsync());

    crashlyticsWorkers.common.await();

    verify(mockSessionReportingCoordinator).hasReportsToSend();
    verify(mockDataCollectionArbiter).isAutomaticDataCollectionEnabled();
    verify(mockSessionReportingCoordinator).sendReports(any(Executor.class));
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testUploadDisabledThenOptIn() throws Exception {
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(true);
    when(mockSessionReportingCoordinator.sendReports(any(Executor.class)))
        .thenReturn(Tasks.forResult(null));

    final DataCollectionArbiter arbiter = mock(DataCollectionArbiter.class);
    when(arbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(arbiter.waitForDataCollectionPermission())
        .thenReturn(new TaskCompletionSource<Void>().getTask());
    when(arbiter.waitForAutomaticDataCollectionEnabled())
        .thenReturn(new TaskCompletionSource<Void>().getTask());

    final ControllerBuilder builder = builder();
    builder.setDataCollectionArbiter(arbiter);
    final CrashlyticsController controller = builder.build();
    controller.submitAllReports(testSettingsProvider.getSettingsAsync());
    verify(arbiter).isAutomaticDataCollectionEnabled();
    verify(mockSessionReportingCoordinator).hasReportsToSend();
    verify(mockSessionReportingCoordinator, never()).sendReports(any(Executor.class));

    await(controller.sendUnsentReports());
    crashlyticsWorkers.common.await();

    verify(mockSessionReportingCoordinator).sendReports(any(Executor.class));
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testUploadDisabledThenOptOut() throws Exception {
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(true);
    when(mockSessionReportingCoordinator.sendReports(any(Executor.class)))
        .thenReturn(Tasks.forResult(null));

    DataCollectionArbiter arbiter = mock(DataCollectionArbiter.class);
    when(arbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(arbiter.waitForAutomaticDataCollectionEnabled())
        .thenReturn(new TaskCompletionSource<Void>().getTask());

    final ControllerBuilder builder = builder();
    builder.setDataCollectionArbiter(arbiter);
    final CrashlyticsController controller = builder.build();

    controller.submitAllReports(testSettingsProvider.getSettingsAsync());

    verify(arbiter).isAutomaticDataCollectionEnabled();
    verify(mockSessionReportingCoordinator).hasReportsToSend();
    verify(mockSessionReportingCoordinator, never()).removeAllReports();

    await(controller.deleteUnsentReports());
    crashlyticsWorkers.common.await();

    crashlyticsWorkers.diskWrite.await();

    verify(mockSessionReportingCoordinator).removeAllReports();
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testUploadDisabledThenEnabled() throws Exception {
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(true);
    when(mockSessionReportingCoordinator.sendReports(any(Executor.class)))
        .thenReturn(Tasks.forResult(null));

    // Mock the DataCollectionArbiter dependencies.
    final String PREFS_NAME = CommonUtils.SHARED_PREFS_NAME;
    final String PREFS_KEY = "firebase_crashlytics_collection_enabled";
    final SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
    when(mockEditor.putBoolean(PREFS_KEY, true)).thenReturn(mockEditor);
    when(mockEditor.commit()).thenReturn(true);
    final SharedPreferences mockPrefs = mock(SharedPreferences.class);
    when(mockPrefs.contains(PREFS_KEY)).thenReturn(true);
    when(mockPrefs.getBoolean(PREFS_KEY, true)).thenReturn(false);
    when(mockPrefs.edit()).thenReturn(mockEditor);
    final Context mockContext = mock(Context.class);
    when(mockContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(mockPrefs);
    final FirebaseApp app = mock(FirebaseApp.class);
    when(app.getApplicationContext()).thenReturn(mockContext);

    // Use a real DataCollectionArbiter to test its switching behavior.
    final DataCollectionArbiter arbiter = new DataCollectionArbiter(app);
    assertFalse(arbiter.isAutomaticDataCollectionEnabled());

    final ControllerBuilder builder = builder();
    builder.setDataCollectionArbiter(arbiter);
    final CrashlyticsController controller = builder.build();

    controller.submitAllReports(testSettingsProvider.getSettingsAsync());

    verify(mockSessionReportingCoordinator).hasReportsToSend();
    verify(mockSessionReportingCoordinator, never()).sendReports(any(Executor.class));

    arbiter.setCrashlyticsDataCollectionEnabled(true);
    assertTrue(arbiter.isAutomaticDataCollectionEnabled());

    when(mockEditor.putBoolean(PREFS_KEY, false)).thenReturn(mockEditor);
    when(mockPrefs.getBoolean(PREFS_KEY, true)).thenReturn(false);
    arbiter.setCrashlyticsDataCollectionEnabled(false);
    assertFalse(arbiter.isAutomaticDataCollectionEnabled());

    // FIXME: Split this out into multiple tests
    when(mockPrefs.contains(PREFS_KEY)).thenReturn(false);
    when(mockEditor.remove(PREFS_KEY)).thenReturn(mockEditor);
    arbiter.setCrashlyticsDataCollectionEnabled(null);
    when(app.isDataCollectionDefaultEnabled()).thenReturn(true);
    assertTrue(arbiter.isAutomaticDataCollectionEnabled());
    when(app.isDataCollectionDefaultEnabled()).thenReturn(false);
    assertFalse(arbiter.isAutomaticDataCollectionEnabled());

    crashlyticsWorkers.common.await();

    verify(mockSessionReportingCoordinator).sendReports(any(Executor.class));
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

  @SdkSuppress(minSdkVersion = 30) // ApplicationExitInfo
  @Test
  public void testFatalEvent_sendsAppExceptionEvent() throws Exception {
    final String sessionId = "sessionId";
    final LogFileManager logFileManager = new LogFileManager(testFileStore);
    final AnalyticsEventLogger mockFirebaseAnalyticsLogger = mock(AnalyticsEventLogger.class);
    final CrashlyticsController controller =
        builder()
            .setAnalyticsEventLogger(mockFirebaseAnalyticsLogger)
            .setLogFileManager(logFileManager)
            .build();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Collections.singleton(sessionId)));

    crashlyticsWorkers.common.submit(
        () -> {
          controller.openSession(SESSION_ID);
          controller.handleUncaughtException(
              testSettingsProvider, Thread.currentThread(), new RuntimeException("Fatal"));
          controller.finalizeSessions(testSettingsProvider);
        });
    crashlyticsWorkers.common.await();

    assertFirebaseAnalyticsCrashEvent(mockFirebaseAnalyticsLogger);
  }

  private void assertFirebaseAnalyticsCrashEvent(AnalyticsEventLogger mockFirebaseAnalyticsLogger) {
    final ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);

    // The event gets sent back almost immediately, but on a separate thread.
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
    }
    verify(mockFirebaseAnalyticsLogger, times(1))
        .logEvent(eq(CrashlyticsController.FIREBASE_APPLICATION_EXCEPTION), captor.capture());
    assertEquals(
        CrashlyticsController.FIREBASE_CRASH_TYPE_FATAL,
        captor.getValue().getInt(CrashlyticsController.FIREBASE_CRASH_TYPE));
    assertTrue(captor.getValue().getLong(CrashlyticsController.FIREBASE_TIMESTAMP) > 0);
  }

  private static <T> T await(Task<T> task) throws Exception {
    return Tasks.await(task, 5, TimeUnit.SECONDS);
  }
}
