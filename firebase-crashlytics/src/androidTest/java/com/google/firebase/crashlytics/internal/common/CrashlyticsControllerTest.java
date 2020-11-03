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

import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.LARGEST_FILE_NAME_FIRST;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_APP_TAG;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_BEGIN_TAG;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_DEVICE_TAG;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_FATAL_TAG;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_NON_FATAL_TAG;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_OS_TAG;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_USER_TAG;
import static com.google.firebase.crashlytics.internal.proto.ClsFileOutputStream.IN_PROGRESS_SESSION_FILE_EXTENSION;
import static com.google.firebase.crashlytics.internal.proto.ClsFileOutputStream.SESSION_FILE_EXTENSION;
import static org.mockito.Mockito.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.device.session.Crashlytics;
import com.google.firebase.crashlytics.device.session.Crashlytics.Session;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.MissingNativeComponent;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.report.ReportManager;
import com.google.firebase.crashlytics.internal.report.ReportUploader;
import com.google.firebase.crashlytics.internal.report.model.Report;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
import com.google.firebase.crashlytics.internal.settings.TestSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.FeaturesSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SessionSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import com.google.firebase.crashlytics.internal.unity.UnityVersionProvider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CrashlyticsControllerTest extends CrashlyticsTestCase {

  private static final String GOOGLE_APP_ID = "google:app:id";

  // Finds all directories other than the log file directory.
  private static final FileFilter NON_LOG_DIRECTORY_FILTER =
      new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          return pathname.isDirectory()
              && !pathname.getName().equals("log-files")
              && !pathname.getName().equals("report-persistence");
        }
      };

  private Context testContext;
  private IdManager idManager;
  private SettingsDataProvider testSettingsDataProvider;
  private FileStore mockFileStore;
  private File testFilesDirectory;
  private AppSettingsData appSettingsData;
  private SessionSettingsData sessionSettingsData;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    testContext = getContext();

    FirebaseInstallationsApi installationsApiMock = mock(FirebaseInstallationsApi.class);
    when(installationsApiMock.getId()).thenReturn(Tasks.forResult("instanceId"));
    idManager = new IdManager(testContext, testContext.getPackageName(), installationsApiMock);

    BatteryIntentProvider.returnNull = false;

    // For each test case, create a new, random subdirectory to guarantee a clean slate for file
    // manipulation.
    testFilesDirectory = new File(testContext.getFilesDir(), UUID.randomUUID().toString());
    testFilesDirectory.mkdirs();
    mockFileStore = mock(FileStore.class);
    when(mockFileStore.getFilesDir()).thenReturn(testFilesDirectory);
    when(mockFileStore.getFilesDirPath()).thenReturn(testFilesDirectory.getPath());

    final SettingsData testSettingsData =
        new TestSettingsData(
            3,
            DataTransportState.REPORT_UPLOAD_VARIANT_LEGACY,
            DataTransportState.REPORT_UPLOAD_VARIANT_LEGACY);
    appSettingsData = testSettingsData.appData;
    sessionSettingsData = testSettingsData.sessionData;

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
    private ReportManager reportManager;
    private ReportUploader.Provider reportUploaderProvider;
    private CrashlyticsNativeComponent nativeComponent;
    private UnityVersionProvider unityVersionProvider;
    private AnalyticsEventLogger analyticsEventLogger;

    ControllerBuilder() {
      dataCollectionArbiter = mock(DataCollectionArbiter.class);
      when(dataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(true);

      nativeComponent = new MissingNativeComponent();

      unityVersionProvider = mock(UnityVersionProvider.class);
      when(unityVersionProvider.getUnityVersion()).thenReturn(null);

      analyticsEventLogger = mock(AnalyticsEventLogger.class);

      reportManager = null;
    }

    ControllerBuilder setDataCollectionArbiter(DataCollectionArbiter arbiter) {
      dataCollectionArbiter = arbiter;
      return this;
    }

    ControllerBuilder setReportUploaderProvider(ReportUploader.Provider provider) {
      reportUploaderProvider = provider;
      return this;
    }

    public ControllerBuilder setReportManager(ReportManager reportManager) {
      this.reportManager = reportManager;
      return this;
    }

    ControllerBuilder setReportUploader(ReportUploader uploader) {
      return setReportUploaderProvider(
          new ReportUploader.Provider() {
            @Override
            public ReportUploader createReportUploader(@NonNull AppSettingsData settingsData) {
              return uploader;
            }
          });
    }

    public ControllerBuilder setNativeComponent(CrashlyticsNativeComponent nativeComponent) {
      this.nativeComponent = nativeComponent;
      return this;
    }

    public ControllerBuilder setUnityVersionProvider(UnityVersionProvider provider) {
      unityVersionProvider = provider;
      return this;
    }

    public ControllerBuilder setAnalyticsEventLogger(AnalyticsEventLogger logger) {
      analyticsEventLogger = logger;
      return this;
    }

    public CrashlyticsController build() {
      HttpRequestFactory mockRequestFactory = mock(HttpRequestFactory.class);

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
              mockRequestFactory,
              idManager,
              dataCollectionArbiter,
              mockFileStore,
              crashMarker,
              appData,
              reportManager,
              reportUploaderProvider,
              nativeComponent,
              analyticsEventLogger,
              testSettingsDataProvider);
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
    controller.cleanInvalidTempFiles();
    return controller;
  }

  public void testLoggedExceptionsOnlyCaptureMainThread() throws Exception {
    final CrashlyticsController controller = createController();
    controller.writeNonFatalException(Thread.currentThread(), new RuntimeException("Logged"));
    controller.doCloseSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] sessionFiles = controller.listCompleteSessionFiles();

    assertEquals(1, sessionFiles.length);

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(sessionFiles[0]);
      final Session session = Session.parseFrom(fis);
      assertEquals(1, session.getEventsCount());
      assertEquals(1, session.getEvents(0).getApp().getExecution().getThreadsCount());
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  public void testFatalExceptionsCaptureAllThreads() throws Exception {
    final CrashlyticsController controller = createController();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException("Fatal"));
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] sessionFiles = controller.listCompleteSessionFiles();

    assertEquals(1, sessionFiles.length);

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(sessionFiles[0]);
      final Session session = Session.parseFrom(fis);
      assertEquals(1, session.getEventsCount());
      assertTrue(session.getEvents(0).getApp().getExecution().getThreadsCount() > 1);
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  public void testSessionTrimmingResultsInAppropriateCompletedSessionsCount() throws Exception {
    final CrashlyticsController controller = createController();

    final int maxSessions = 2;
    final int totalSessions = maxSessions + 2;

    for (int i = 0; i < totalSessions; i++) {
      controller.writeNonFatalException(Thread.currentThread(), new RuntimeException("Logged"));
      controller.doCloseSessions(sessionSettingsData.maxCustomExceptionEvents);
      controller.openSession();
    }

    assertEquals(totalSessions, controller.listCompleteSessionFiles().length);

    controller.trimSessionFiles(maxSessions);

    assertEquals(maxSessions, controller.listCompleteSessionFiles().length);
  }

  public void testSessionTrimmingFavorsCrashSessions() throws Exception {
    final CrashlyticsController controller = createController();

    final int numNonFatalSessions = 2;
    final int numFatalSessions = 2;

    // Create some fatal sessions.
    for (int i = 0; i < numFatalSessions; i++) {
      controller.handleUncaughtException(
          testSettingsDataProvider, Thread.currentThread(), new RuntimeException());
    }

    // Create some more recent nonfatal sessions.
    for (int i = 0; i < numNonFatalSessions; i++) {
      controller.writeNonFatalException(Thread.currentThread(), new RuntimeException("Logged"));
      controller.doCloseSessions(sessionSettingsData.maxCustomExceptionEvents);
      controller.openSession();
    }

    final int totalSessions = numNonFatalSessions + numFatalSessions;

    assertEquals(totalSessions, controller.listCompleteSessionFiles().length);
    assertTrue(totalSessions > numFatalSessions);

    controller.trimSessionFiles(numFatalSessions);

    final File[] trimmedSessions = controller.listCompleteSessionFiles();

    assertEquals(numFatalSessions, trimmedSessions.length);

    // Make sure all remaining sessions are fatal.
    for (File f : trimmedSessions) {
      FileInputStream fis = null;
      try {
        fis = new FileInputStream(f);
        final Session session = Session.parseFrom(fis);
        assertEquals(1, session.getEventsCount());
        assertEquals("crash", session.getEvents(0).getType());
      } finally {
        if (fis != null) {
          fis.close();
        }
      }
    }
  }

  public void testNativeCrashDataCausesNativeReport() throws Exception {
    final File testDir = new File(testContext.getFilesDir(), "testNative");
    testDir.mkdir();

    final File minidump = new File(testDir, "crash.dmp");
    final File binaryImages = new File(testDir, "crash.maps");
    final File metadata = new File(testDir, "crash.device_info");
    final File session = new File(testDir, "session.json");
    final File app = new File(testDir, "app.json");
    final File device = new File(testDir, "device.json");
    final File os = new File(testDir, "os.json");

    TestUtils.writeStringToFile("minidump", minidump);
    TestUtils.writeStringToFile("binaryLibs", binaryImages);
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
                return binaryImages;
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
    final CrashlyticsController controller =
        builder().setNativeComponent(mockNativeComponent).build();
    controller.openSession();

    // Create a new session, leaving the previous session to be finalized.
    controller.openSession();

    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] nativeDirectories = controller.listNativeSessionFileDirectories();

    assertEquals(1, nativeDirectories.length);
    final File[] processedFiles = nativeDirectories[0].listFiles();
    assertEquals(
        "Unexpected number of files found: " + Arrays.toString(processedFiles),
        7,
        processedFiles.length);
  }

  public void testMissingNativeComponentCausesNoReports() {
    final CrashlyticsController controller = createController();
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] sessionFiles = controller.listNativeSessionFileDirectories();

    assertEquals(0, sessionFiles.length);
  }

  public void testCleanupSessionsWithInvalidParts() throws Exception {
    final String invalidSessionId = new CLSUUID(idManager).toString();

    final FilenameFilter invalidSessionPartFilter =
        new FilenameFilter() {
          @Override
          public boolean accept(File f, String name) {
            return name.startsWith(invalidSessionId) && name.endsWith(SESSION_FILE_EXTENSION);
          }
        };

    // These are the files we expect to get quarantined
    final File sdkDir = mockFileStore.getFilesDir();
    new File(sdkDir, invalidSessionId + SESSION_BEGIN_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, invalidSessionId + SESSION_APP_TAG + IN_PROGRESS_SESSION_FILE_EXTENSION)
        .createNewFile();
    new File(sdkDir, invalidSessionId + SESSION_DEVICE_TAG + SESSION_FILE_EXTENSION)
        .createNewFile();
    new File(sdkDir, invalidSessionId + SESSION_FATAL_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, invalidSessionId + SESSION_NON_FATAL_TAG + SESSION_FILE_EXTENSION)
        .createNewFile();
    new File(sdkDir, invalidSessionId + SESSION_OS_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, invalidSessionId + SESSION_USER_TAG + SESSION_FILE_EXTENSION).createNewFile();

    final String validSessionId = new CLSUUID(idManager).toString();

    final FilenameFilter validSessionPartFilter =
        new FilenameFilter() {
          @Override
          public boolean accept(File f, String name) {
            return name.startsWith(validSessionId) && name.endsWith(SESSION_FILE_EXTENSION);
          }
        };

    // Finds all directories other than the log file directory.

    // These are the files we expect to get left alone
    new File(sdkDir, validSessionId + SESSION_BEGIN_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, validSessionId + SESSION_APP_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, validSessionId + SESSION_DEVICE_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, validSessionId + SESSION_OS_TAG + SESSION_FILE_EXTENSION).createNewFile();

    final CrashlyticsController controller = createController();
    controller.cleanInvalidTempFiles();

    // Invalid files were moved, valid files were left alone
    assertEquals(0, sdkDir.listFiles(invalidSessionPartFilter).length);
    assertEquals(4, sdkDir.listFiles(validSessionPartFilter).length);
    assertEquals(0, sdkDir.listFiles(NON_LOG_DIRECTORY_FILTER).length);
  }

  public void testCleanupSessions_deletesSessionWithNoBinaryImages() throws Exception {
    final String invalidSessionId = new CLSUUID(idManager).toString();

    final FilenameFilter invalidSessionPartFilter =
        new FilenameFilter() {
          @Override
          public boolean accept(File f, String name) {
            return name.startsWith(invalidSessionId) && name.endsWith(SESSION_FILE_EXTENSION);
          }
        };

    // These are the files we expect to get deleted
    final File sdkDir = mockFileStore.getFilesDir();
    new File(sdkDir, invalidSessionId + SESSION_BEGIN_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, invalidSessionId + SESSION_APP_TAG + SESSION_FILE_EXTENSION).createNewFile();
    final File eventFileMissingBinaryImages =
        new File(
            sdkDir,
            invalidSessionId
                + CrashlyticsController.SESSION_EVENT_MISSING_BINARY_IMGS_TAG
                + SESSION_FILE_EXTENSION);
    eventFileMissingBinaryImages.createNewFile();

    final String validSessionId = new CLSUUID(idManager).toString();

    final FilenameFilter validSessionPartFilter =
        new FilenameFilter() {
          @Override
          public boolean accept(File f, String name) {
            return name.startsWith(validSessionId) && name.endsWith(SESSION_FILE_EXTENSION);
          }
        };

    // These are the files we expect to get left alone
    new File(sdkDir, validSessionId + SESSION_BEGIN_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, validSessionId + SESSION_APP_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, validSessionId + SESSION_DEVICE_TAG + SESSION_FILE_EXTENSION).createNewFile();
    new File(sdkDir, validSessionId + SESSION_OS_TAG + SESSION_FILE_EXTENSION).createNewFile();

    assertEquals(3, sdkDir.listFiles(invalidSessionPartFilter).length);

    final CrashlyticsController controller = createController();
    controller.cleanInvalidTempFiles();

    // Invalid files were deleted, valid files were left alone
    assertEquals(0, sdkDir.listFiles(invalidSessionPartFilter).length);
    assertEquals(4, sdkDir.listFiles(validSessionPartFilter).length);
    assertEquals(0, sdkDir.listFiles(NON_LOG_DIRECTORY_FILTER).length);
  }

  public void testLargestFileNameFirst() {
    final File smaller = new File(new CLSUUID(idManager).toString());
    final File larger = new File(new CLSUUID(idManager).toString());

    final File[] expectedOrder = new File[] {larger, smaller};
    final File[] testOrder = new File[] {smaller, larger};

    Arrays.sort(testOrder, LARGEST_FILE_NAME_FIRST);

    assertTrue(Arrays.equals(expectedOrder, testOrder));
  }

  // TODO: There's only ever one open session now that we can close sessions while offline.
  // Is that the behavior we want?
  /*
  public void testMaxOpenSessions() throws Exception {
    // This test relies on not having any settings because of no internet connection
    final SettingsDataProvider nullSettingsProvider = mock(SettingsDataProvider.class);
    Mockito.when(nullSettingsProvider.getSettingsData()).thenReturn(null);

    final CrashlyticsController controller = builder().build();

    final int CRASH_COUNT = 12;
    final int expectedCount = MAX_OPEN_SESSIONS + 1;

    for (int i = 0; i < CRASH_COUNT; i++) {
      controller.handleUncaughtException(
          nullSettingsProvider, Thread.currentThread(), new RuntimeException());
    }

    final int openSessions = controller.listSessionBeginFiles().length;

    assertEquals(expectedCount, openSessions);
  }
  */

  // TODO: MW 2016-10-04 Restore this test once the exception handler is separate.
  /*
   * Not my favorite kind of test b/c it involves timing... but timing/responsiveness is what we're trying
   * to test, so thems the breaks.
   *
  public void testBigBacklogOfLoggedExceptionsIgnored() throws InterruptedException {
      // Used to block the test until the exception handling is complete.
      final CountDownLatch latch = new CountDownLatch(1);

      final UncaughtExceptionHandler origHandler = new UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread t, Throwable ex) {
              // When the exception gets passed to the original handler, the app should quit.
              // Release the latch so that the test can continue
              latch.countDown();
          }
      };

      final DefaultCrashlyticsController controller =
              new DefaultCrashlyticsController(crashlyticsCore,
                      new CrashlyticsExecutorServiceWrapper(Executors.newSingleThreadExecutor()),
                      idManager, mockFileStore, mockUnityVersionProvider);

      // Queue up a large number of non-fatal exceptions to be processed, such that they would
      // take the handler a while to get through.
      final int NON_FATAL_COUNT = 1000;

      for (int i = 0; i < NON_FATAL_COUNT; i++) {
          controller.writeNonFatalException(Thread.currentThread(), new RuntimeException());
      }

      // We expect the fatal exception to halt the processing of the backlog of non-fatal
      // exceptions, and bring things to a halt immediately. If not, we'd expect processing them
      // to take longer than the timeout we're providing. If we throw an InterruptedException here
      // and fail the test, it's because we wouldn't have allowed the Android app to crash quickly
      // enough to prevent an ANR.
      controller.handleUncaughtException(Thread.currentThread(), new RuntimeException());

      // An ANR happens after 5 seconds, so we need to be done faster than that.
      latch.await(3, TimeUnit.SECONDS);
  }*/

  /**
   * Crashing should shut down the executor service, but we don't want further calls that would use
   * it to throw exceptions!
   */
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
  public void testFinalizeSessionAfterCrashOk() throws Exception {
    final CrashlyticsController controller = builder().build();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException());

    // This should not throw.
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);
  }

  private static <T> T await(Task<T> task) throws Exception {
    return Tasks.await(task, 5, TimeUnit.SECONDS);
  }

  public void testUploadWithNoReports() throws Exception {
    ReportManager mockReportManager = mock(ReportManager.class);
    when(mockReportManager.areReportsAvailable()).thenReturn(false);
    ReportUploader uploader = mock(ReportUploader.class);

    final ControllerBuilder builder = builder();
    builder.setReportManager(mockReportManager);
    builder.setReportUploader(uploader);
    final CrashlyticsController controller = builder.build();

    Task<Void> task = controller.submitAllReports(1.0f, testSettingsDataProvider.getAppSettings());

    await(task);

    verify(mockReportManager).areReportsAvailable();
    verifyNoMoreInteractions(mockReportManager);
    verifyZeroInteractions(uploader);
  }

  public void testUploadWithDataCollectionAlwaysEnabled() throws Exception {
    final File reportFile = new File(testFilesDirectory, "reportFile.cls");
    Report mockReport = mock(Report.class);
    List<Report> mockReportList = Arrays.asList(mockReport);
    ReportManager mockReportManager = mock(ReportManager.class);
    when(mockReport.getType()).thenReturn(Report.Type.JAVA);
    when(mockReport.getFile()).thenReturn(reportFile);
    when(mockReportManager.areReportsAvailable()).thenReturn(true);
    when(mockReportManager.findReports()).thenReturn(mockReportList);
    ReportUploader mockUploader = mock(ReportUploader.class);

    final ControllerBuilder builder = builder();
    builder.setReportManager(mockReportManager);
    builder.setReportUploader(mockUploader);
    final CrashlyticsController controller = builder.build();

    Task<Void> task = controller.submitAllReports(1.0f, testSettingsDataProvider.getAppSettings());

    await(task);

    verify(mockReportManager).areReportsAvailable();
    verify(mockReportManager).findReports();
    verifyNoMoreInteractions(mockReportManager);
    verify(mockUploader).uploadReportsAsync(mockReportList, true, 1.0f);
    verifyNoMoreInteractions(mockUploader);
    verify(mockReport).getType();
    verify(mockReport).getFile();
    verifyNoMoreInteractions(mockReport);
  }

  public void testUploadDisabledThenOptIn() throws Exception {
    final File reportFile = new File(testFilesDirectory, "reportFile.cls");
    Report mockReport = mock(Report.class);
    List<Report> mockReportList = Arrays.asList(mockReport);
    ReportManager mockReportManager = mock(ReportManager.class);
    when(mockReport.getType()).thenReturn(Report.Type.JAVA);
    when(mockReport.getFile()).thenReturn(reportFile);
    when(mockReportManager.areReportsAvailable()).thenReturn(true);
    when(mockReportManager.findReports()).thenReturn(mockReportList);

    DataCollectionArbiter arbiter = mock(DataCollectionArbiter.class);
    when(arbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(arbiter.waitForDataCollectionPermission())
        .thenReturn(new TaskCompletionSource<Void>().getTask());
    when(arbiter.waitForAutomaticDataCollectionEnabled())
        .thenReturn(new TaskCompletionSource<Void>().getTask());

    ReportUploader mockUploader = mock(ReportUploader.class);

    final ControllerBuilder builder = builder();
    builder.setDataCollectionArbiter(arbiter);
    builder.setReportManager(mockReportManager);
    builder.setReportUploader(mockUploader);
    final CrashlyticsController controller = builder.build();

    Task<Void> task = controller.submitAllReports(1.0f, testSettingsDataProvider.getAppSettings());

    await(controller.sendUnsentReports());
    await(task);

    verify(mockReportManager).areReportsAvailable();
    verify(mockReportManager).findReports();
    verifyNoMoreInteractions(mockReportManager);
    verify(mockUploader).uploadReportsAsync(mockReportList, true, 1.0f);
    verifyNoMoreInteractions(mockUploader);
    verify(mockReport).getType();
    verify(mockReport).getFile();
    verifyNoMoreInteractions(mockReport);
  }

  public void testUploadDisabledThenOptOut() throws Exception {
    final File reportFile = new File(testFilesDirectory, "reportFile.cls");
    Report mockReport = mock(Report.class);
    List<Report> mockReportList = Arrays.asList(mockReport);
    ReportManager mockReportManager = mock(ReportManager.class);
    when(mockReport.getType()).thenReturn(Report.Type.JAVA);
    when(mockReport.getFile()).thenReturn(reportFile);
    when(mockReportManager.areReportsAvailable()).thenReturn(true);
    when(mockReportManager.findReports()).thenReturn(mockReportList);

    ReportUploader mockUploader = mock(ReportUploader.class);

    DataCollectionArbiter arbiter = mock(DataCollectionArbiter.class);
    when(arbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(arbiter.waitForAutomaticDataCollectionEnabled())
        .thenReturn(new TaskCompletionSource<Void>().getTask());

    final ControllerBuilder builder = builder();
    builder.setDataCollectionArbiter(arbiter);
    builder.setReportManager(mockReportManager);
    builder.setReportUploader(mockUploader);
    final CrashlyticsController controller = builder.build();

    Task<Void> task = controller.submitAllReports(1.0f, testSettingsDataProvider.getAppSettings());

    await(controller.deleteUnsentReports());

    await(task);

    verify(mockReportManager).areReportsAvailable();
    verify(mockReportManager).findReports();
    verify(mockReportManager).deleteReports(mockReportList);
    verifyNoMoreInteractions(mockReportManager);
    verifyZeroInteractions(mockUploader);
    verifyZeroInteractions(mockReport);
  }

  public void testUploadDisabledThenEnabled() throws Exception {
    final File reportFile = new File(testFilesDirectory, "reportFile.cls");
    reportFile.createNewFile();
    Report mockReport = mock(Report.class);
    List<Report> mockReportList = Arrays.asList(mockReport);
    ReportManager mockReportManager = mock(ReportManager.class);
    when(mockReport.getType()).thenReturn(Report.Type.JAVA);
    when(mockReport.getFile()).thenReturn(reportFile);
    when(mockReportManager.areReportsAvailable()).thenReturn(true);
    when(mockReportManager.findReports()).thenReturn(mockReportList);

    ReportUploader mockUploader = mock(ReportUploader.class);

    // Mock the DataCollectionArbiter dependencies.
    final String PREFS_NAME = CommonUtils.SHARED_PREFS_NAME;
    final String PREFS_KEY = "firebase_crashlytics_collection_enabled";
    SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
    when(mockEditor.putBoolean(PREFS_KEY, true)).thenReturn(mockEditor);
    when(mockEditor.commit()).thenReturn(true);
    SharedPreferences mockPrefs = mock(SharedPreferences.class);
    when(mockPrefs.contains(PREFS_KEY)).thenReturn(true);
    when(mockPrefs.getBoolean(PREFS_KEY, true)).thenReturn(false);
    when(mockPrefs.edit()).thenReturn(mockEditor);
    Context mockContext = mock(Context.class);
    when(mockContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(mockPrefs);
    FirebaseApp app = mock(FirebaseApp.class);
    when(app.getApplicationContext()).thenReturn(mockContext);

    // Use a real DataCollectionArbiter to test its switching behavior.
    DataCollectionArbiter arbiter = new DataCollectionArbiter(app);
    assertFalse(arbiter.isAutomaticDataCollectionEnabled());

    final ControllerBuilder builder = builder();
    builder.setDataCollectionArbiter(arbiter);
    builder.setReportManager(mockReportManager);
    builder.setReportUploader(mockUploader);
    final CrashlyticsController controller = builder.build();

    Task<Void> task = controller.submitAllReports(1.0f, testSettingsDataProvider.getAppSettings());

    arbiter.setCrashlyticsDataCollectionEnabled(true);
    assertTrue(arbiter.isAutomaticDataCollectionEnabled());

    when(mockEditor.putBoolean(PREFS_KEY, false)).thenReturn(mockEditor);
    when(mockPrefs.getBoolean(PREFS_KEY, true)).thenReturn(false);
    arbiter.setCrashlyticsDataCollectionEnabled(false);
    assertFalse(arbiter.isAutomaticDataCollectionEnabled());

    when(mockPrefs.contains(PREFS_KEY)).thenReturn(false);
    when(mockEditor.remove(PREFS_KEY)).thenReturn(mockEditor);
    arbiter.setCrashlyticsDataCollectionEnabled(null);
    when(app.isDataCollectionDefaultEnabled()).thenReturn(true);
    assertTrue(arbiter.isAutomaticDataCollectionEnabled());
    when(app.isDataCollectionDefaultEnabled()).thenReturn(false);
    assertFalse(arbiter.isAutomaticDataCollectionEnabled());

    await(task);
    verify(mockReportManager).areReportsAvailable();
    verify(mockReportManager).findReports();
    verifyNoMoreInteractions(mockReportManager);
    verify(mockUploader).uploadReportsAsync(mockReportList, true, 1.0f);
    verifyNoMoreInteractions(mockUploader);
    verify(mockReport).getType();
    verify(mockReport).getFile();
    verifyNoMoreInteractions(mockReport);
  }

  public void testCrashTimeUploadAddsOrganizationId() throws Exception {
    final ControllerBuilder builder = builder();
    final CrashlyticsController controller = builder.build();
    controller.openSession();
    controller.cleanInvalidTempFiles();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException("Fatal"));

    final File[] sessionFiles = controller.listCompleteSessionFiles();

    assertEquals(1, sessionFiles.length);

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(sessionFiles[0]);
      final Session session = Session.parseFrom(fis);
      assertEquals(appSettingsData.organizationId, session.getApp().getOrganization().getClsId());
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  public void testStartupUploadAddsOrganizationId() throws Exception {
    Report mockReport = mock(Report.class);
    ReportManager mockReportManager = mock(ReportManager.class);
    when(mockReportManager.areReportsAvailable()).thenReturn(true);
    ReportUploader mockUploader = mock(ReportUploader.class);

    final ControllerBuilder builder = builder();
    builder.setReportManager(mockReportManager);
    builder.setReportUploader(mockUploader);
    final CrashlyticsController controller = builder.build();
    controller.openSession();
    controller.cleanInvalidTempFiles();
    controller.writeNonFatalException(Thread.currentThread(), new RuntimeException("Logged"));
    controller.doCloseSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] sessionFiles = controller.listCompleteSessionFiles();

    assertEquals(1, sessionFiles.length);

    final File reportFile = sessionFiles[0];

    List<Report> mockReportList = Arrays.asList(mockReport);
    when(mockReport.getType()).thenReturn(Report.Type.JAVA);
    when(mockReport.getFile()).thenReturn(reportFile);
    when(mockReportManager.findReports()).thenReturn(mockReportList);

    Task<Void> task = controller.submitAllReports(1.0f, testSettingsDataProvider.getAppSettings());

    await(task);

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(reportFile);
      final Session session = Session.parseFrom(fis);
      assertEquals(appSettingsData.organizationId, session.getApp().getOrganization().getClsId());
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  public void testUnityVersionAppearsInDeveloperPlatformFields() throws Exception {
    final String expectedUnityVersion = "1.0.0";
    final UnityVersionProvider unityVersionProvider =
        new UnityVersionProvider() {
          @Override
          public String getUnityVersion() {
            return expectedUnityVersion;
          }
        };

    final CrashlyticsController controller =
        builder().setUnityVersionProvider(unityVersionProvider).build();
    controller.openSession();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException("Fatal"));
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] sessionFiles = controller.listCompleteSessionFiles();

    assertEquals(1, sessionFiles.length);

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(sessionFiles[0]);
      final Session session = Session.parseFrom(fis);
      assertEquals(1, session.getEventsCount());
      assertEquals("Unity", session.getApp().getDevelopmentPlatform());
      assertEquals(expectedUnityVersion, session.getApp().getDevelopmentPlatformVersion());
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  public void testNoDeveloperPlatformFields_whenUnityIsMissing() throws Exception {
    final CrashlyticsController controller = createController();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException("Fatal"));
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] sessionFiles = controller.listCompleteSessionFiles();

    assertEquals(1, sessionFiles.length);

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(sessionFiles[0]);
      final Session session = Session.parseFrom(fis);
      assertEquals(1, session.getEventsCount());
      assertEquals("", session.getApp().getDevelopmentPlatform());
      assertEquals("", session.getApp().getDevelopmentPlatformVersion());
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  public void testFirebaseAnalyticsEventIsSent_whenSettingFalseClientFlagTrue() throws Exception {
    final AnalyticsEventLogger mockFirebaseAnalyticsLogger = mock(AnalyticsEventLogger.class);
    final CrashlyticsController controller =
        builder().setAnalyticsEventLogger(mockFirebaseAnalyticsLogger).build();
    controller.openSession();
    controller.handleUncaughtException(
        firebaseCrashlyticsSettingsProvider(),
        Thread.currentThread(),
        new RuntimeException("Fatal"));
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    assertFirebaseAnalyticsCrashEvent(mockFirebaseAnalyticsLogger);
  }

  public void testFirebaseAnalyticsEventIsSent_whenSettingTrueClientFlagFalse() throws Exception {
    final AnalyticsEventLogger mockFirebaseAnalyticsLogger = mock(AnalyticsEventLogger.class);
    final CrashlyticsController controller =
        builder().setAnalyticsEventLogger(mockFirebaseAnalyticsLogger).build();
    controller.openSession();
    controller.handleUncaughtException(
        firebaseCrashlyticsSettingsProvider(),
        Thread.currentThread(),
        new RuntimeException("Fatal"));
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    assertFirebaseAnalyticsCrashEvent(mockFirebaseAnalyticsLogger);
  }

  public void testFirebaseAnalyticsEventIsSent_whenSettingTrueClientFlagTrue() throws Exception {
    final AnalyticsEventLogger mockFirebaseAnalyticsLogger = mock(AnalyticsEventLogger.class);
    final CrashlyticsController controller =
        builder().setAnalyticsEventLogger(mockFirebaseAnalyticsLogger).build();
    controller.openSession();
    controller.handleUncaughtException(
        firebaseCrashlyticsSettingsProvider(),
        Thread.currentThread(),
        new RuntimeException("Fatal"));
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    assertFirebaseAnalyticsCrashEvent(mockFirebaseAnalyticsLogger);
  }

  public void testGeneratorAndAnalyzerVersion() throws Exception {
    final CrashlyticsController controller = createController();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException("Fatal"));
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] sessionFiles = controller.listCompleteSessionFiles();

    assertEquals(1, sessionFiles.length);

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(sessionFiles[0]);
      final Session session = Session.parseFrom(fis);
      assertEquals(1, session.getAnalyzer());
      assertEquals(Crashlytics.GeneratorType.ANDROID_SDK, session.getGeneratorType());
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  public void testBatteryLevel() throws Exception {
    final CrashlyticsController controller = createController();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException("Fatal"));
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] sessionFiles = controller.listCompleteSessionFiles();

    assertEquals(1, sessionFiles.length);

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(sessionFiles[0]);
      final Session session = Session.parseFrom(fis);
      assertTrue(session.getEvents(0).getDevice().hasBatteryLevel());
      assertTrue(session.getEvents(0).getDevice().getBatteryLevel() > 0.0);
      assertEquals(2, session.getEvents(0).getDevice().getBatteryVelocity());
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  public void testBatteryLevelNotWritten_whenBatteryIntentIsNull() throws Exception {
    BatteryIntentProvider.returnNull = true;

    final CrashlyticsController controller = createController();
    controller.handleUncaughtException(
        testSettingsDataProvider, Thread.currentThread(), new RuntimeException("Fatal"));
    controller.finalizeSessions(sessionSettingsData.maxCustomExceptionEvents);

    final File[] sessionFiles = controller.listCompleteSessionFiles();

    assertEquals(1, sessionFiles.length);

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(sessionFiles[0]);
      final Session session = Session.parseFrom(fis);
      assertFalse(session.getEvents(0).getDevice().hasBatteryLevel());
      assertEquals(1, session.getEvents(0).getDevice().getBatteryVelocity());
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  private SettingsDataProvider firebaseCrashlyticsSettingsProvider() {
    final FeaturesSettingsData featureData = new FeaturesSettingsData(false);
    final SettingsData testSettingsData = new TestSettingsData();
    final SettingsData settingsData =
        new SettingsData(
            1, testSettingsData.appData, testSettingsData.sessionData, featureData, 2, 1000);
    final SettingsDataProvider settingsProvider = mock(SettingsDataProvider.class);
    Mockito.when(settingsProvider.getSettings()).thenReturn(settingsData);
    Mockito.when(settingsProvider.getAppSettings())
        .thenReturn(Tasks.forResult(settingsData.appData));
    return settingsProvider;
  }

  private void assertFirebaseAnalyticsCrashEvent(AnalyticsEventLogger mockFirebaseAnalyticsLogger) {
    final ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);

    Mockito.verify(mockFirebaseAnalyticsLogger, Mockito.times(1))
        .logEvent(
            Mockito.eq(CrashlyticsController.FIREBASE_APPLICATION_EXCEPTION), captor.capture());
    assertEquals(
        CrashlyticsController.FIREBASE_CRASH_TYPE_FATAL,
        captor.getValue().getInt(CrashlyticsController.FIREBASE_CRASH_TYPE));
    assertTrue(captor.getValue().getLong(CrashlyticsController.FIREBASE_TIMESTAMP) > 0);
  }

  @Override
  public Context getContext() {
    // Return a context wrapper that will allow us to override the behavior of registering
    // the receiver for battery changed events.
    return new ContextWrapper(super.getContext()) {
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
}
