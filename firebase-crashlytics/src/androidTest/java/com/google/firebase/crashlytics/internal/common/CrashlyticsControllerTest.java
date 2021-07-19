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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import com.google.firebase.crashlytics.internal.ProviderProxyNativeComponent;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
import com.google.firebase.crashlytics.internal.settings.TestSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import com.google.firebase.crashlytics.internal.unity.UnityVersionProvider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.io.File;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;

public class CrashlyticsControllerTest extends CrashlyticsTestCase {
  private static final String GOOGLE_APP_ID = "google:app:id";

  private Context testContext;
  private IdManager idManager;
  private SettingsDataProvider testSettingsDataProvider;
  private FileStore mockFileStore;
  private File testFilesDirectory;
  private SessionReportingCoordinator mockSessionReportingCoordinator;
  private DataCollectionArbiter mockDataCollectionArbiter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    testContext = getContext();

    FirebaseInstallationsApi installationsApiMock = mock(FirebaseInstallationsApi.class);
    when(installationsApiMock.getId()).thenReturn(Tasks.forResult("instanceId"));
    idManager =
        new IdManager(
            testContext,
            testContext.getPackageName(),
            installationsApiMock,
            DataCollectionArbiterTest.MOCK_ARBITER_ENABLED);

    // For each test case, create a new, random subdirectory to guarantee a clean slate for file
    // manipulation.
    testFilesDirectory = new File(testContext.getFilesDir(), UUID.randomUUID().toString());
    testFilesDirectory.mkdirs();
    mockFileStore = mock(FileStore.class);
    when(mockFileStore.getFilesDir()).thenReturn(testFilesDirectory);
    when(mockFileStore.getFilesDirPath()).thenReturn(testFilesDirectory.getPath());

    final SettingsData testSettingsData = new TestSettingsData(3);

    mockSessionReportingCoordinator = mock(SessionReportingCoordinator.class);

    mockDataCollectionArbiter = mock(DataCollectionArbiter.class);
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(true);

    testSettingsDataProvider = mock(SettingsDataProvider.class);
    when(testSettingsDataProvider.getSettings()).thenReturn(testSettingsData);
    when(testSettingsDataProvider.getAppSettings())
        .thenReturn(Tasks.forResult(testSettingsData.appData));
  }

  @Override
  protected void tearDown() throws Exception {
    recursiveDelete(testFilesDirectory);
    super.tearDown();
  }

  private static void recursiveDelete(File file) {
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        recursiveDelete(f);
      }
    }
    file.delete();
  }

  /** A convenience class for building CrashlyticsController instances for testing. */
  private class ControllerBuilder {
    private DataCollectionArbiter dataCollectionArbiter;
    private CrashlyticsNativeComponent nativeComponent;
    private UnityVersionProvider unityVersionProvider;
    private AnalyticsEventLogger analyticsEventLogger;
    private SessionReportingCoordinator sessionReportingCoordinator;
    private LogFileManager.DirectoryProvider logFileDirectoryProvider = null;
    private LogFileManager logFileManager = null;

    ControllerBuilder() {
      dataCollectionArbiter = mockDataCollectionArbiter;
      nativeComponent = new ProviderProxyNativeComponent(() -> null);

      unityVersionProvider = mock(UnityVersionProvider.class);
      when(unityVersionProvider.getUnityVersion()).thenReturn(null);

      analyticsEventLogger = mock(AnalyticsEventLogger.class);

      sessionReportingCoordinator = mockSessionReportingCoordinator;
    }

    ControllerBuilder setDataCollectionArbiter(DataCollectionArbiter arbiter) {
      dataCollectionArbiter = arbiter;
      return this;
    }

    public ControllerBuilder setNativeComponent(CrashlyticsNativeComponent nativeComponent) {
      this.nativeComponent = nativeComponent;
      return this;
    }

    public ControllerBuilder setLogFileDirectoryProvider(
        LogFileManager.DirectoryProvider logFileDirectoryProvider) {
      this.logFileDirectoryProvider = logFileDirectoryProvider;
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
          new CrashlyticsFileMarker(CrashlyticsCore.CRASH_MARKER_FILE_NAME, mockFileStore);

      AppData appData =
          new AppData(
              GOOGLE_APP_ID,
              "buildId",
              "installerPackageName",
              "packageName",
              "versionCode",
              "versionName",
              unityVersionProvider);

      final CrashlyticsController controller =
          new CrashlyticsController(
              testContext.getApplicationContext(),
              new CrashlyticsBackgroundWorker(new SameThreadExecutorService()),
              idManager,
              dataCollectionArbiter,
              mockFileStore,
              crashMarker,
              appData,
              null,
              logFileManager,
              logFileDirectoryProvider,
              sessionReportingCoordinator,
              nativeComponent,
              analyticsEventLogger);
      return controller;
    }
  }

  private ControllerBuilder builder() {
    return new ControllerBuilder();
  }

  /** Creates a new CrashlyticsController with default options and opens a session. */
  private CrashlyticsController createController() {
    final CrashlyticsController controller = builder().build();
    controller.openSession();
    return controller;
  }

  public void testWriteNonFatal_callsSessionReportingCoordinatorPersistNonFatal() throws Exception {
    final String sessionId = "sessionId";
    final Thread thread = Thread.currentThread();
    final Exception nonFatal = new RuntimeException("Non-fatal");
    final CrashlyticsController controller = createController();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(Arrays.asList(sessionId));

    controller.writeNonFatalException(thread, nonFatal);
    controller.doCloseSessions(testSettingsDataProvider);

    verify(mockSessionReportingCoordinator)
        .persistNonFatalEvent(eq(nonFatal), eq(thread), eq(sessionId), anyLong());
  }

  public void testFatalException_callsSessionReportingCoordinatorPersistFatal() throws Exception {
    final String sessionId = "sessionId";
    final Thread thread = Thread.currentThread();
    final Exception fatal = new RuntimeException("Fatal");
    final CrashlyticsController controller = createController();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(Arrays.asList(sessionId));

    controller.handleUncaughtException(testSettingsDataProvider, thread, fatal);

    verify(mockSessionReportingCoordinator)
        .persistFatalEvent(eq(fatal), eq(thread), eq(sessionId), anyLong());
  }

  public void testNativeCrashDataCausesNativeReport() throws Exception {
    final String sessionId = "sessionId";
    final String previousSessionId = "previousSessionId";

    final File testDir = new File(testContext.getFilesDir(), "testNative");
    testDir.mkdir();

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
        .thenReturn(Arrays.asList(sessionId, previousSessionId));

    final File logFileDirectory = new File(testFilesDirectory, "logfiles");
    final LogFileManager.DirectoryProvider logFileDirectoryProvider = () -> logFileDirectory;
    final LogFileManager logFileManager =
        new LogFileManager(testContext, logFileDirectoryProvider, sessionId);
    final CrashlyticsController controller =
        builder()
            .setNativeComponent(mockNativeComponent)
            .setLogFileDirectoryProvider(logFileDirectoryProvider)
            .setLogFileManager(logFileManager)
            .build();

    controller.finalizeSessions(testSettingsDataProvider);

    final File[] nativeDirectories = controller.listNativeSessionFileDirectories();

    assertEquals(1, nativeDirectories.length);
    final File[] processedFiles = nativeDirectories[0].listFiles();
    assertEquals(
        "Unexpected number of files found: " + Arrays.toString(processedFiles),
        6,
        processedFiles.length);
  }

  public void testMissingNativeComponentCausesNoReports() {
    final CrashlyticsController controller = createController();
    controller.finalizeSessions(testSettingsDataProvider);

    final File[] sessionFiles = controller.listNativeSessionFileDirectories();

    assertEquals(0, sessionFiles.length);
  }

  /**
   * Crashing should shut down the executor service, but we don't want further calls that would use
   * it to throw exceptions!
   */
  // FIXME: Validate this test is working as intended
  public void testLoggedExceptionsAfterCrashOk() {
    final CrashlyticsController controller = builder().build();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException());

    // This should not throw.
    controller.writeNonFatalException(Thread.currentThread(), new RuntimeException());
  }

  /**
   * Crashing should shut down the executor service, but we don't want further calls that would use
   * it to throw exceptions!
   */
  // FIXME: Validate this test works as intended
  public void testLogStringAfterCrashOk() {
    final CrashlyticsController controller = builder().build();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException());

    // This should not throw.
    controller.writeToLog(System.currentTimeMillis(), "Hi");
  }

  /**
   * Crashing should shut down the executor service, but we don't want further calls that would use
   * it to throw exceptions!
   */
  // FIXME: Validate this test works as intended
  public void testFinalizeSessionAfterCrashOk() throws Exception {
    final CrashlyticsController controller = builder().build();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException());

    // This should not throw.
    controller.finalizeSessions(testSettingsDataProvider);
  }

  public void testUploadWithNoReports() throws Exception {
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(false);

    final CrashlyticsController controller = createController();

    Task<Void> task = controller.submitAllReports(testSettingsDataProvider.getAppSettings());

    await(task);

    verify(mockSessionReportingCoordinator).hasReportsToSend();
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

  public void testUploadWithDataCollectionAlwaysEnabled() throws Exception {
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(true);
    when(mockSessionReportingCoordinator.sendReports(any(Executor.class)))
        .thenReturn(Tasks.forResult(null));

    final CrashlyticsController controller = createController();

    final Task<Void> task = controller.submitAllReports(testSettingsDataProvider.getAppSettings());

    await(task);

    verify(mockSessionReportingCoordinator).hasReportsToSend();
    verify(mockDataCollectionArbiter).isAutomaticDataCollectionEnabled();
    verify(mockSessionReportingCoordinator).sendReports(any(Executor.class));
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

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

    final Task<Void> task = controller.submitAllReports(testSettingsDataProvider.getAppSettings());

    verify(arbiter).isAutomaticDataCollectionEnabled();
    verify(mockSessionReportingCoordinator).hasReportsToSend();
    verify(mockSessionReportingCoordinator, never()).sendReports(any(Executor.class));

    await(controller.sendUnsentReports());
    await(task);

    verify(mockSessionReportingCoordinator).sendReports(any(Executor.class));
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

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

    final Task<Void> task = controller.submitAllReports(testSettingsDataProvider.getAppSettings());

    verify(arbiter).isAutomaticDataCollectionEnabled();
    verify(mockSessionReportingCoordinator).hasReportsToSend();
    verify(mockSessionReportingCoordinator, never()).removeAllReports();

    await(controller.deleteUnsentReports());
    await(task);

    verify(mockSessionReportingCoordinator).removeAllReports();
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

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

    final Task<Void> task = controller.submitAllReports(testSettingsDataProvider.getAppSettings());

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

    await(task);

    verify(mockSessionReportingCoordinator).sendReports(any(Executor.class));
    verifyNoMoreInteractions(mockSessionReportingCoordinator);
  }

  public void testFatalEvent_sendsAppExceptionEvent() {
    final File logFileDirectory = new File(testFilesDirectory, "logfiles");
    final String sessionId = "sessionId";
    final LogFileManager logFileManager =
        new LogFileManager(testContext, () -> logFileDirectory, sessionId);
    final AnalyticsEventLogger mockFirebaseAnalyticsLogger = mock(AnalyticsEventLogger.class);
    final CrashlyticsController controller =
        builder()
            .setAnalyticsEventLogger(mockFirebaseAnalyticsLogger)
            .setLogFileManager(logFileManager)
            .build();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(Arrays.asList(sessionId));
    logFileDirectory.mkdir();

    controller.openSession();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException("Fatal"));
    controller.finalizeSessions(testSettingsDataProvider);

    assertFirebaseAnalyticsCrashEvent(mockFirebaseAnalyticsLogger);
  }

  private void assertFirebaseAnalyticsCrashEvent(AnalyticsEventLogger mockFirebaseAnalyticsLogger) {
    final ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);

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
