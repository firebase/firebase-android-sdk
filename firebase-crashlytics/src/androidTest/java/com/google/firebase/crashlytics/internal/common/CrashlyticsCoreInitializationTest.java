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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.MissingNativeComponent;
import com.google.firebase.crashlytics.internal.analytics.UnavailableAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.DisabledBreadcrumbSource;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.persistence.FileStoreImpl;
import com.google.firebase.crashlytics.internal.settings.SettingsController;
import com.google.firebase.crashlytics.internal.settings.TestSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class CrashlyticsCoreInitializationTest extends CrashlyticsTestCase {

  // Crashlytics build id, must match CommonUtils#CRASHLYTICS_BUILD_ID
  private static final String CRASHLYTICS_BUILD_ID = "com.crashlytics.android.build_id";

  private static final String PACKAGE_NAME = "testPackageName";
  private static final String BUILD_ID = "testBuildId";
  private static final int RES_ID_REQUIRE_BUILD_ID = 1;
  private static final int RES_ID_BUILD_ID = 2;
  private static final String GOOGLE_APP_ID = "google:app:id";

  private Context mockAppContext;
  private Resources mockResources;
  private FirebaseOptions testFirebaseOptions;
  private FileStore fileStore;
  private SettingsController mockSettingsController;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockAppContext = newMockContext();
    mockResources = mock(Resources.class);
    testFirebaseOptions = new FirebaseOptions.Builder().setApplicationId(GOOGLE_APP_ID).build();

    fileStore = new FileStoreImpl(getContext());

    cleanSdkDirectory();

    mockSettingsController = mock(SettingsController.class);
    final SettingsData settingsData = new TestSettingsData();
    when(mockSettingsController.getSettings()).thenReturn(settingsData);
    when(mockSettingsController.getAppSettings()).thenReturn(Tasks.forResult(settingsData.appData));
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    cleanSdkDirectory();
  }

  private static final class CoreBuilder {
    private FirebaseApp app;
    private IdManager idManager;
    private CrashlyticsNativeComponent nativeComponent;
    private DataCollectionArbiter arbiter;
    private ExecutorService crashHandlerExecutor;

    public CoreBuilder(Context context, FirebaseOptions firebaseOptions) {
      app = mock(FirebaseApp.class);
      when(app.getApplicationContext()).thenReturn(context);
      when(app.getOptions()).thenReturn(firebaseOptions);

      FirebaseInstallationsApi installationsApiMock = mock(FirebaseInstallationsApi.class);
      when(installationsApiMock.getId()).thenReturn(Tasks.forResult("instanceId"));
      idManager = new IdManager(context, context.getPackageName(), installationsApiMock);

      nativeComponent = new MissingNativeComponent();

      arbiter = mock(DataCollectionArbiter.class);
      when(arbiter.isAutomaticDataCollectionEnabled()).thenReturn(true);

      crashHandlerExecutor = new SameThreadExecutorService();
    }

    public CoreBuilder setNativeComponent(CrashlyticsNativeComponent nativeComponent) {
      this.nativeComponent = nativeComponent;
      return this;
    }

    public CrashlyticsCore build() {
      return new CrashlyticsCore(
          app,
          idManager,
          nativeComponent,
          arbiter,
          new DisabledBreadcrumbSource(),
          new UnavailableAnalyticsEventLogger(),
          crashHandlerExecutor);
    }
  }

  private CoreBuilder builder() {
    return new CoreBuilder(mockAppContext, testFirebaseOptions);
  }

  private Context newMockContext() throws Exception {
    final ApplicationInfo testAppInfo = new ApplicationInfo();
    // Set icon id to 0 to fall back to using the PACKAGE_NAME. This is a bit fragile as it
    // relies on knowledge of CommonUtils but refactoring that for these tests is a huge
    // undertaking.
    testAppInfo.icon = 0;
    final PackageInfo testPkgInfo = new PackageInfo();
    testPkgInfo.versionCode = 1;
    testPkgInfo.versionName = "testVersionName";

    final PackageManager mockPm = mock(PackageManager.class);
    when(mockPm.getApplicationInfo(PACKAGE_NAME, PackageManager.GET_META_DATA))
        .thenReturn(testAppInfo);
    when(mockPm.getInstallerPackageName(PACKAGE_NAME)).thenReturn("testInstallerPackageName");
    when(mockPm.getPackageInfo(PACKAGE_NAME, 0)).thenReturn(testPkgInfo);
    when(mockPm.getApplicationLabel(testAppInfo)).thenReturn("testApplicationLabel");

    return new ContextWrapper(getContext().getApplicationContext()) {
      @Override
      public Resources getResources() {
        return mockResources;
      }

      @Override
      public PackageManager getPackageManager() {
        return mockPm;
      }

      @Override
      public String getPackageName() {
        return PACKAGE_NAME;
      }

      @Override
      public ApplicationInfo getApplicationInfo() {
        return testAppInfo;
      }
    };
  }

  private void cleanSdkDirectory() {
    // We need to get rid of all initialization markers and session files to test the
    // behaviors in this test class
    final File[] files = fileStore.getFilesDir().listFiles();

    if (files != null) {
      for (File f : files) {
        f.delete();
      }
    }
  }

  public void testOnPreExecute_openSessionExists() {
    final CrashlyticsCore crashlyticsCore = builder().build();
    setupBuildIdRequired("false");
    assertTrue(crashlyticsCore.onPreExecute(mockSettingsController));
    assertNotNull(crashlyticsCore.getController());
    assertTrue(crashlyticsCore.getController().hasOpenSession());
  }

  public void testOnPreExecute_buildIdRequiredAndExists() {
    final CrashlyticsCore crashlyticsCore = builder().build();
    setupBuildIdRequired("true");
    setupBuildId();
    assertTrue(crashlyticsCore.onPreExecute(mockSettingsController));
  }

  public void testOnPreExecute_buildIdRequiredAndDoesNotExist() {
    final CrashlyticsCore crashlyticsCore = builder().build();
    setupBuildIdRequired("true");
    try {
      crashlyticsCore.onPreExecute(mockSettingsController);
      fail();
    } catch (Exception e) {
      assertTrue(e instanceof IllegalStateException);
    }
  }

  public void testOnPreExecute_buildIdNotRequiredAndExists() {
    final CrashlyticsCore crashlyticsCore = builder().build();
    setupBuildIdRequired("false");
    setupBuildId();
    assertTrue(crashlyticsCore.onPreExecute(mockSettingsController));
  }

  public void testOnPreExecute_buildIdNotRequiredAndDoesNotExist() {
    setupBuildIdRequired("false");
    final CrashlyticsCore crashlyticsCore = builder().build();
    assertTrue(crashlyticsCore.onPreExecute(mockSettingsController));
  }

  public void testOnPreExecute_didCrashOnPreviousExecution() throws Exception {
    final CrashlyticsCore crashlyticsCore = builder().build();
    setupBuildIdRequired(String.valueOf(false));
    setupCrashMarker();

    assertTrue(getCrashMarkerFile().exists());
    assertTrue(crashlyticsCore.onPreExecute(mockSettingsController));
    assertTrue(crashlyticsCore.didCrashOnPreviousExecution());
    assertFalse(getCrashMarkerFile().exists());
  }

  public void testOnPreExecute_didNotCrashOnPreviousExecution() {
    final CrashlyticsCore crashlyticsCore = builder().build();
    setupBuildIdRequired(String.valueOf(false));

    assertFalse(getCrashMarkerFile().exists());
    assertTrue(crashlyticsCore.onPreExecute(mockSettingsController));
    assertFalse(crashlyticsCore.didCrashOnPreviousExecution());
    assertFalse(getCrashMarkerFile().exists());
  }

  private void setupBuildIdRequired(String booleanValue) {
    setupResource(
        RES_ID_REQUIRE_BUILD_ID,
        "string",
        CrashlyticsCore.CRASHLYTICS_REQUIRE_BUILD_ID,
        booleanValue);
  }

  private void setupBuildId() {
    final Integer buildIdResId = RES_ID_BUILD_ID;
    when(mockResources.getIdentifier(CRASHLYTICS_BUILD_ID, "string", PACKAGE_NAME))
        .thenReturn(buildIdResId);
    when(mockResources.getString(buildIdResId)).thenReturn(BUILD_ID);
  }

  private void setupResource(Integer resId, String type, String name, String value) {
    when(mockResources.getIdentifier(name, type, PACKAGE_NAME)).thenReturn(resId);
    when(mockResources.getString(resId)).thenReturn(value);
  }

  private void setupCrashMarker() throws IOException {
    getCrashMarkerFile().createNewFile();
  }

  private File getCrashMarkerFile() {
    return new File(fileStore.getFilesDir(), CrashlyticsCore.CRASH_MARKER_FILE_NAME);
  }
}
