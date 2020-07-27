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

import static org.mockito.Mockito.*;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.crashlytics.BuildConfig;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.MissingNativeComponent;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import com.google.firebase.crashlytics.internal.analytics.UnavailableAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbHandler;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import com.google.firebase.crashlytics.internal.breadcrumbs.DisabledBreadcrumbSource;
import com.google.firebase.crashlytics.internal.persistence.FileStoreImpl;
import com.google.firebase.crashlytics.internal.settings.SettingsController;
import com.google.firebase.crashlytics.internal.settings.TestSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.mockito.Mockito;

public class CrashlyticsCoreTest extends CrashlyticsTestCase {

  private static final String SESSION_BEGIN_TAG = "BeginSession";
  private static final String SESSION_APP_TAG = "SessionApp";
  private static final String SESSION_OS_TAG = "SessionOS";
  private static final String SESSION_DEVICE_TAG = "SessionDevice";

  private static final String[] SESSION_BEGIN_FILE_TAGS = {
    SESSION_BEGIN_TAG, SESSION_APP_TAG, SESSION_OS_TAG, SESSION_DEVICE_TAG
  };

  private static final String GOOGLE_APP_ID = "google:app:id";

  private static final CrashlyticsNativeComponent MISSING_NATIVE_COMPONENT =
      new MissingNativeComponent();

  private CrashlyticsCore crashlyticsCore;
  private BreadcrumbSource mockBreadcrumbSource;
  private File rootFilesDir;
  private File crashlyticsFilesDir;

  private File logFilesDir;
  private File nonFatalSessionDir;
  private File fatalSessionsDir;
  private File nativeSessionsDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    rootFilesDir = getContext().getFilesDir();
    recursiveDelete(rootFilesDir);

    mockBreadcrumbSource = mock(BreadcrumbSource.class);

    crashlyticsCore = appRestart();

    crashlyticsFilesDir = new File(rootFilesDir, FileStoreImpl.FILES_PATH);
    for (File f : rootFilesDir.listFiles()) {
      recursiveDelete(f);
    }
    logFilesDir = new File(crashlyticsFilesDir, "log-files");
    nonFatalSessionDir = new File(crashlyticsFilesDir, "nonfatal-sessions");
    fatalSessionsDir = new File(crashlyticsFilesDir, "fatal-sessions");
    nativeSessionsDir = new File(crashlyticsFilesDir, "native-sessions");
  }

  private static void recursiveDelete(File f) {
    if (f.isDirectory()) {
      for (File s : f.listFiles()) {
        recursiveDelete(s);
      }
    }
    f.delete();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testCustomAttributes() throws Exception {
    UserMetadata metadata = crashlyticsCore.getController().getUserMetadata();

    assertNull(metadata.getUserId());
    assertTrue(metadata.getCustomKeys().isEmpty());

    final String id = "id012345";
    crashlyticsCore.setUserId(id);

    assertEquals(id, metadata.getUserId());

    final StringBuffer idBuffer = new StringBuffer(id);
    while (idBuffer.length() < UserMetadata.MAX_ATTRIBUTE_SIZE) {
      idBuffer.append("0");
    }
    final String longId = idBuffer.toString();
    final String superLongId = longId + "more chars";

    crashlyticsCore.setUserId(superLongId);
    assertEquals(longId, metadata.getUserId());

    final String key1 = "key1";
    final String value1 = "value1";
    crashlyticsCore.setCustomKey(key1, value1);
    assertEquals(value1, metadata.getCustomKeys().get(key1));

    final String longValue = longId.replaceAll("0", "x");
    final String superLongValue = longValue + "some more chars";

    // test truncation of custom keys and attributes
    crashlyticsCore.setCustomKey(superLongId, superLongValue);
    assertNull(metadata.getCustomKeys().get(superLongId));
    assertEquals(longValue, metadata.getCustomKeys().get(longId));

    // test the max number of attributes. We've already set 2.
    for (int i = 2; i < UserMetadata.MAX_ATTRIBUTES; ++i) {
      final String key = "key" + i;
      final String value = "value" + i;
      crashlyticsCore.setCustomKey(key, value);
      assertEquals(value, metadata.getCustomKeys().get(key));
    }
    // should be full now, extra key, value pairs will be dropped.
    final String key = "new key";
    crashlyticsCore.setCustomKey(key, "some value");
    assertFalse(metadata.getCustomKeys().containsKey(key));

    // should be able to update existing keys
    crashlyticsCore.setCustomKey(key1, longValue);
    assertEquals(longValue, metadata.getCustomKeys().get(key1));

    // when we set a key to null, it should still exist with an empty value
    crashlyticsCore.setCustomKey(key1, null);
    assertEquals("", metadata.getCustomKeys().get(key1));

    // keys and values are trimmed.
    crashlyticsCore.setCustomKey(" " + key1 + " ", " " + longValue + " ");
    assertTrue(metadata.getCustomKeys().containsKey(key1));
    assertEquals(longValue, metadata.getCustomKeys().get(key1));
  }

  public void testGetVersion() {
    assertFalse(TextUtils.isEmpty(CrashlyticsCore.getVersion()));
    assertFalse(CrashlyticsCore.getVersion().equalsIgnoreCase("version"));
    assertEquals(BuildConfig.VERSION_NAME, CrashlyticsCore.getVersion());
  }

  public void testNullBuildIdRequiredTrue() {
    assertFalse(CrashlyticsCore.isBuildIdValid(null, true));
  }

  public void testEmptyBuildIdRequiredTrue() {
    assertFalse(CrashlyticsCore.isBuildIdValid("", true));
  }

  public void testValidBuildIdRequiredTrue() {
    assertTrue(CrashlyticsCore.isBuildIdValid("buildId", true));
  }

  public void testNullBuildIdRequiredFalse() {
    assertTrue(CrashlyticsCore.isBuildIdValid(null, false));
  }

  public void testEmptyBuildIdRequiredFalse() {
    assertTrue(CrashlyticsCore.isBuildIdValid("", false));
  }

  public void testBreadcrumbSourceIsRegistered() {
    Mockito.verify(mockBreadcrumbSource).registerBreadcrumbHandler(any(BreadcrumbHandler.class));
  }

  public void testFreshStartAndCrash() throws Exception {
    assertNull(crashlyticsFilesDir.listFiles());

    appRestart();

    assertEquals(1, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertFalse(fatalSessionsDir.exists());
    assertFalse(nativeSessionsDir.exists());
    assertEquals(0, crashlyticsFilesDir.listFiles(META_FILE_FILTER).length);
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);

    javaCrash(new RuntimeException());

    // Directories
    assertEquals(2, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertTrue(fatalSessionsDir.isDirectory());

    // Finalized session
    assertEquals(1, fatalSessionsDir.listFiles(CLS_FILE_FILTER).length);

    // New opened session files
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);
  }

  public void testMultipleJavaCrashesWithKeysAndUser() throws Exception {
    assertNull(crashlyticsFilesDir.listFiles());

    CrashlyticsCore core = appRestart();

    assertEquals(1, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertFalse(fatalSessionsDir.exists());
    assertFalse(nativeSessionsDir.exists());
    assertEquals(0, crashlyticsFilesDir.listFiles(META_FILE_FILTER).length);
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);

    core.setCustomKey("number", Integer.toString(1));
    core.setUserId("user");

    assertEquals(2, crashlyticsFilesDir.listFiles(META_FILE_FILTER).length);

    javaCrash(new RuntimeException());
    core = appRestart();

    assertEquals(0, crashlyticsFilesDir.listFiles(META_FILE_FILTER).length);
    core.setCustomKey("string", "string");
    assertEquals(1, crashlyticsFilesDir.listFiles(META_FILE_FILTER).length);

    javaCrash(new IllegalStateException());
    core = appRestart();

    assertEquals(0, crashlyticsFilesDir.listFiles(META_FILE_FILTER).length);
    core.setUserId("user");
    assertEquals(1, crashlyticsFilesDir.listFiles(META_FILE_FILTER).length);

    javaCrash(new NullPointerException());

    assertEquals(2, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertTrue(fatalSessionsDir.isDirectory());
    assertFalse(nativeSessionsDir.exists());
    assertEquals(3, fatalSessionsDir.listFiles(CLS_FILE_FILTER).length);
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);
  }

  public void testMultipleOpenSessionNoCrash() throws Exception {
    assertNull(crashlyticsFilesDir.listFiles());

    appRestart();
    appRestart();
    appRestart();

    assertEquals(1, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertFalse(nativeSessionsDir.exists());
    assertFalse(fatalSessionsDir.exists());
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);
  }

  public void testNonFatalOnlySession() throws Exception {
    assertNull(crashlyticsFilesDir.listFiles());

    CrashlyticsCore core = appRestart();

    assertEquals(1, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertFalse(nonFatalSessionDir.exists());
    assertFalse(nativeSessionsDir.exists());
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);

    core.logException(new RuntimeException());
    core.logException(new IllegalStateException());

    assertEquals(2, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);

    appRestart();

    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertEquals(2, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertTrue(nonFatalSessionDir.isDirectory());
    assertFalse(nativeSessionsDir.exists());
    assertEquals(1, nonFatalSessionDir.listFiles().length);
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);
  }

  public void testNativeCrash() throws Exception {
    assertNull(crashlyticsFilesDir.listFiles());

    final File ndkDataDir = new File(rootFilesDir, "ndkkit");
    ndkDataDir.mkdir();
    final NativeSessionFileProvider ndkData = populateNdkData(ndkDataDir);
    final CrashlyticsNativeComponent mocknativeComponent = mock(CrashlyticsNativeComponent.class);
    when(mocknativeComponent.hasCrashDataForSession(anyString())).thenReturn(true);
    when(mocknativeComponent.getSessionFileProvider(anyString())).thenReturn(ndkData);

    // Initialize Crashlytics to open a session, then "restart app" with an NDK provider to provide
    // mock native crash data.
    appRestart();

    assertEquals(1, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertFalse(nativeSessionsDir.isDirectory());
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);

    appRestart(mocknativeComponent);

    assertEquals(2, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);

    assertTrue(nativeSessionsDir.isDirectory());
    final File[] sessionIdDirectories =
        nativeSessionsDir.listFiles(ANY_SESSION_ID_DIRECTORY_FILTER);
    assertEquals(1, sessionIdDirectories.length);
    assertEquals(7, sessionIdDirectories[0].listFiles().length);
  }

  public void testNativeCrashWithMetadata() throws Exception {
    assertNull(crashlyticsFilesDir.listFiles());

    final File ndkDataDir = new File(rootFilesDir, "ndkkit");
    ndkDataDir.mkdir();
    final NativeSessionFileProvider ndkData = populateNdkData(ndkDataDir);
    final CrashlyticsNativeComponent mocknativeComponent = mock(CrashlyticsNativeComponent.class);
    when(mocknativeComponent.hasCrashDataForSession(anyString())).thenReturn(true);
    when(mocknativeComponent.getSessionFileProvider(anyString())).thenReturn(ndkData);

    // Initialize Crashlytics to open a session, then "restart app" with an NDK provider to provide
    // mock native crash data.
    CrashlyticsCore core = appRestart();

    assertEquals(1, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertFalse(nativeSessionsDir.isDirectory());
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);

    core.log("log log log");
    core.setCustomKey("test", "test");
    core.setUserId("user");

    appRestart(mocknativeComponent);

    assertTrue(nativeSessionsDir.isDirectory());
    final File[] sessionIdDirectories =
        nativeSessionsDir.listFiles(ANY_SESSION_ID_DIRECTORY_FILTER);
    assertEquals(1, sessionIdDirectories.length);
    assertEquals(10, sessionIdDirectories[0].listFiles().length);
  }

  public void testMultipleNativeCrashes() throws Exception {
    assertNull(crashlyticsFilesDir.listFiles());

    final File ndkDataDir = new File(rootFilesDir, "ndkkit");
    ndkDataDir.mkdir();
    final NativeSessionFileProvider ndkData = populateNdkData(ndkDataDir);
    final CrashlyticsNativeComponent mocknativeComponent = mock(CrashlyticsNativeComponent.class);
    when(mocknativeComponent.hasCrashDataForSession(anyString())).thenReturn(true);
    when(mocknativeComponent.getSessionFileProvider(anyString())).thenReturn(ndkData);

    // Initialize Crashlytics to open a session, then "restart app" with an NDK provider to provide
    // mock native crash data.
    appRestart();

    assertEquals(1, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertFalse(nativeSessionsDir.isDirectory());
    assertEquals(0, crashlyticsFilesDir.listFiles(ANY_SESSION_ID_DIRECTORY_FILTER).length);
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);

    appRestart(mocknativeComponent);

    assertTrue(nativeSessionsDir.isDirectory());
    final File[] sessionIdDirectories =
        nativeSessionsDir.listFiles(ANY_SESSION_ID_DIRECTORY_FILTER);
    // We expect a single native crash report, because the current implementation does not
    // handle finalizing multiple native sessions.
    assertEquals(1, sessionIdDirectories.length);
    assertEquals(7, sessionIdDirectories[0].listFiles().length);
  }

  public void testInterleavedJavaAndNativeCrashes() throws Exception {
    assertNull(crashlyticsFilesDir.listFiles());

    final File ndkDataDir = new File(rootFilesDir, "ndkkit");
    ndkDataDir.mkdir();
    final NativeSessionFileProvider emptyNdkData =
        new MissingNativeComponent().getSessionFileProvider("missing");
    final CrashlyticsNativeComponent mocknativeComponent = mock(CrashlyticsNativeComponent.class);

    appRestart(mocknativeComponent);

    assertEquals(1, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertFalse(nativeSessionsDir.isDirectory());
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);

    when(mocknativeComponent.hasCrashDataForSession(anyString())).thenReturn(false);
    when(mocknativeComponent.getSessionFileProvider(anyString()))
        .thenReturn(emptyNdkData); // Java crash

    javaCrash(new IllegalStateException());

    appRestart(mocknativeComponent);

    assertEquals(2, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertTrue(fatalSessionsDir.isDirectory());
    assertEquals(1, fatalSessionsDir.listFiles(CLS_FILE_FILTER).length);
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertFalse(nativeSessionsDir.isDirectory());
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);

    when(mocknativeComponent.hasCrashDataForSession(anyString())).thenReturn(true);
    when(mocknativeComponent.getSessionFileProvider(anyString()))
        .thenReturn(populateNdkData(ndkDataDir)); // Native crash

    appRestart(mocknativeComponent);

    assertEquals(3, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertTrue(fatalSessionsDir.isDirectory());
    assertEquals(1, fatalSessionsDir.listFiles(CLS_FILE_FILTER).length);
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);
    assertTrue(nativeSessionsDir.isDirectory());
    File[] nativeSessionsDirectories = nativeSessionsDir.listFiles(ANY_SESSION_ID_DIRECTORY_FILTER);
    assertEquals(1, nativeSessionsDirectories.length);
    assertEquals(7, nativeSessionsDirectories[0].listFiles().length);

    when(mocknativeComponent.hasCrashDataForSession(anyString())).thenReturn(false);
    when(mocknativeComponent.getSessionFileProvider(anyString())).thenReturn(emptyNdkData);

    javaCrash(new NullPointerException());
    appRestart(mocknativeComponent);

    assertEquals(3, crashlyticsFilesDir.listFiles(ONLY_SESSION_DIRECTORIES).length);
    assertTrue(logFilesDir.isDirectory());
    assertTrue(fatalSessionsDir.isDirectory());
    assertEquals(2, fatalSessionsDir.listFiles(CLS_FILE_FILTER).length);
    assertEquals(0, crashlyticsFilesDir.listFiles(NON_FATAL_FILES_FILTER).length);
    assertEquals(
        4,
        crashlyticsFilesDir.listFiles(new SessionBeginFilesWithExtensionFilter(CLS_FILE_FILTER))
            .length);
    assertTrue(nativeSessionsDir.isDirectory());
    nativeSessionsDirectories = nativeSessionsDir.listFiles(ANY_SESSION_ID_DIRECTORY_FILTER);
    assertEquals(1, nativeSessionsDirectories.length);
    assertEquals(7, nativeSessionsDirectories[0].listFiles().length);
  }

  public void testOnPreExecute_nativeDidCrashOnPreviousExecution() throws Exception {
    appRestart(); // Create a previous execution
    final CrashlyticsNativeComponent mockNativeComponent = mock(CrashlyticsNativeComponent.class);
    when(mockNativeComponent.hasCrashDataForSession(anyString())).thenReturn(true);
    final CrashlyticsCore crashlyticsCore = appRestart(mockNativeComponent);
    assertTrue(crashlyticsCore.didCrashOnPreviousExecution());
  }

  public void testOnPreExecute_nativeDidNotCrashOnPreviousExecution() throws Exception {
    appRestart(); // Create a previous execution
    final CrashlyticsNativeComponent mockNativeComponent = mock(CrashlyticsNativeComponent.class);
    when(mockNativeComponent.hasCrashDataForSession(anyString())).thenReturn(false);
    final CrashlyticsCore crashlyticsCore = appRestart(mockNativeComponent);
    assertFalse(crashlyticsCore.didCrashOnPreviousExecution());
  }

  private NativeSessionFileProvider populateNdkData(File rootNdkDir) throws IOException {
    final File crashDir = new File(rootNdkDir, "12345");
    crashDir.mkdir();
    final File minidump = new File(crashDir, "crash.dmp");
    final File binaryImages = new File(crashDir, "crash.maps");
    final File metadata = new File(crashDir, "crash.device_info");
    final File session = new File(crashDir, "session.json");
    final File app = new File(crashDir, "app.json");
    final File device = new File(crashDir, "device.json");
    final File os = new File(crashDir, "os.json");

    TestUtils.writeStringToFile("minidump", minidump);
    TestUtils.writeStringToFile("binaryLibs", binaryImages);
    TestUtils.writeStringToFile("metadata", metadata);
    TestUtils.writeStringToFile("session", session);
    TestUtils.writeStringToFile("app", app);
    TestUtils.writeStringToFile("device", device);
    TestUtils.writeStringToFile("os", os);

    return new NativeSessionFileProvider() {
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
    };
  }

  private static void javaCrash(Throwable throwable) {
    Thread.getDefaultUncaughtExceptionHandler()
        .uncaughtException(Thread.currentThread(), throwable);
  }

  // Convenience method that recreates the CrashlyticsCore and starts it up.
  private CrashlyticsCore appRestart() throws Exception {
    return appRestart(MISSING_NATIVE_COMPONENT);
  }

  // Convenience method because so many tests was to replace the NDK data provider.
  private CrashlyticsCore appRestart(CrashlyticsNativeComponent mocknativeComponent)
      throws Exception {
    CrashlyticsCore core =
        CoreBuilder.newBuilder()
            .setCrashlyticsnativeComponent(mocknativeComponent)
            .setBreadcrumbSource(mockBreadcrumbSource)
            .build(getContext());
    return await(startCoreAsync(core));
  }

  // Wraps Tasks.await with a default timeout, so tests fail gracefully.
  private <T> T await(Task<T> task) throws Exception {
    return Tasks.await(task, 5, TimeUnit.SECONDS);
  }

  // Starts the given CrashlyticsCore.
  private Task<CrashlyticsCore> startCoreAsync(CrashlyticsCore crashlyticsCore) {
    // Swallow exceptions so tests don't crash.
    Thread.setDefaultUncaughtExceptionHandler(NOOP_HANDLER);

    SettingsController mockSettingsController = mock(SettingsController.class);
    final SettingsData settings =
        new TestSettingsData(
            3,
            DataTransportState.REPORT_UPLOAD_VARIANT_LEGACY,
            DataTransportState.REPORT_UPLOAD_VARIANT_LEGACY);
    when(mockSettingsController.getSettings()).thenReturn(settings);
    when(mockSettingsController.getAppSettings()).thenReturn(Tasks.forResult(settings.appData));

    crashlyticsCore.onPreExecute(mockSettingsController);

    return crashlyticsCore
        .doBackgroundInitializationAsync(mockSettingsController)
        .onSuccessTask(
            new SuccessContinuation<Void, CrashlyticsCore>() {
              @NonNull
              @Override
              public Task<CrashlyticsCore> then(@Nullable Void aVoid) throws Exception {
                return Tasks.forResult(crashlyticsCore);
              }
            });
  }

  /** Helper class for building CrashlyticsCore instances. */
  private static class CoreBuilder {
    private DataCollectionArbiter arbiter;
    private CrashlyticsNativeComponent nativeComponent;
    private BreadcrumbSource breadcrumbSource;

    CoreBuilder() {
      setDataCollectionEnabled(true);
    }

    static CoreBuilder newBuilder() {
      return new CoreBuilder();
    }

    CoreBuilder setDataCollectionEnabled(boolean enabled) {
      arbiter = mock(DataCollectionArbiter.class);
      when(arbiter.isAutomaticDataCollectionEnabled()).thenReturn(enabled);
      return this;
    }

    CoreBuilder setCrashlyticsnativeComponent(CrashlyticsNativeComponent nativeComponent) {
      this.nativeComponent = nativeComponent;
      return this;
    }

    CoreBuilder setBreadcrumbSource(BreadcrumbSource breadcrumbSource) {
      this.breadcrumbSource = breadcrumbSource;
      return this;
    }

    CrashlyticsCore build(Context context) {
      FirebaseOptions testFirebaseOptions;
      testFirebaseOptions = new FirebaseOptions.Builder().setApplicationId(GOOGLE_APP_ID).build();

      FirebaseApp app = mock(FirebaseApp.class);
      when(app.getApplicationContext()).thenReturn(context);
      when(app.getOptions()).thenReturn(testFirebaseOptions);
      FirebaseInstallationsApi installationsApiMock = mock(FirebaseInstallationsApi.class);
      when(installationsApiMock.getId()).thenReturn(Tasks.forResult("instanceId"));
      BreadcrumbSource breadcrumbSource =
          this.breadcrumbSource == null ? new DisabledBreadcrumbSource() : this.breadcrumbSource;
      final CrashlyticsCore crashlyticsCore =
          new CrashlyticsCore(
              app,
              new IdManager(context, "unused", installationsApiMock),
              nativeComponent,
              arbiter,
              breadcrumbSource,
              new UnavailableAnalyticsEventLogger(),
              new SameThreadExecutorService());
      return crashlyticsCore;
    }
  }

  private static class FilesOnlyFilter implements FileFilter {
    @Override
    public boolean accept(File file) {
      return file.isFile();
    }
  }

  private static class SessionDirectoriesOnlyFilter implements FileFilter {
    @Override
    public boolean accept(File file) {
      return file.isDirectory() && !file.getName().equals("report-persistence");
    }
  }

  private static class FileExtensionFilter extends FilesOnlyFilter {
    private final String extension;

    public FileExtensionFilter(String extension) {
      this.extension = extension;
    }

    @Override
    public boolean accept(File file) {
      return super.accept(file) && file.getName().endsWith(extension);
    }
  }

  private static class SessionBeginFilesFilter extends FilesOnlyFilter {
    @Override
    public boolean accept(File file) {
      return super.accept(file) && containsBeginTag(file.getName());
    }

    private boolean containsBeginTag(String filename) {
      for (String tag : SESSION_BEGIN_FILE_TAGS) {
        if (filename.contains(tag)) {
          return true;
        }
      }
      return false;
    }
  }

  private static class SessionBeginFilesWithExtensionFilter extends SessionBeginFilesFilter {
    private final FileExtensionFilter extensionFilter;

    public SessionBeginFilesWithExtensionFilter(FileExtensionFilter extensionFilter) {
      this.extensionFilter = extensionFilter;
    }

    @Override
    public boolean accept(File file) {
      return super.accept(file) && extensionFilter.accept(file);
    }
  }

  private static class NonFatalFilesFilter extends FilesOnlyFilter {
    private static String NON_FATAL_FILENAME_REGEX = "[a-zA-Z0-9-]*SessionEvent\\d{10}\\.cls";

    @Override
    public boolean accept(File file) {
      return super.accept(file) && file.getName().matches(NON_FATAL_FILENAME_REGEX);
    }
  }

  private static class AnySessionIdDirectoryFilter extends SessionDirectoriesOnlyFilter {
    private static String ANY_SESSION_ID_REGEX =
        "[A-Z0-9]{12}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{12}";

    @Override
    public boolean accept(File file) {
      return super.accept(file) && file.getName().matches(ANY_SESSION_ID_REGEX);
    }
  }

  private static final FileFilter ONLY_FILES = new FilesOnlyFilter();
  private static final FileFilter ONLY_SESSION_DIRECTORIES = new SessionDirectoriesOnlyFilter();
  private static final FileExtensionFilter CLS_FILE_FILTER = new FileExtensionFilter(".cls");
  private static final FileExtensionFilter META_FILE_FILTER = new FileExtensionFilter(".meta");
  private static final FileFilter NON_FATAL_FILES_FILTER = new NonFatalFilesFilter();
  private static final FileFilter ANY_SESSION_ID_DIRECTORY_FILTER =
      new AnySessionIdDirectoryFilter();

  private static final Thread.UncaughtExceptionHandler NOOP_HANDLER =
      new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {}
      };
}
