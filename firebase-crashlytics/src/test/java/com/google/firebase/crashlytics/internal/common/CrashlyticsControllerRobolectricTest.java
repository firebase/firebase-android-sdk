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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.crashlytics.internal.ProviderProxyNativeComponent;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
import com.google.firebase.crashlytics.internal.settings.model.FeaturesSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.Settings;
import com.google.firebase.crashlytics.internal.unity.UnityVersionProvider;
import java.io.File;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CrashlyticsControllerRobolectricTest {
  private static final String GOOGLE_APP_ID = "google:app:id";

  private Context testContext;
  @Mock private IdManager idManager;
  @Mock private SettingsDataProvider mockSettingsDataProvider;
  @Mock private FileStore mockFileStore;
  @Mock private File mockFilesDirectory;
  @Mock private SessionReportingCoordinator mockSessionReportingCoordinator;
  @Mock private DataCollectionArbiter mockDataCollectionArbiter;
  @Mock private LogFileManager.DirectoryProvider mockLogFileDirecotryProvider;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    testContext = getApplicationContext();
  }

  @Test
  public void testDoCloseSession_enabledAnrs_doesNotPersistsAppExitInfoIfItDoesntExist() {
    final String sessionId = "sessionId";
    final CrashlyticsController controller = createController();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(Collections.singletonList(sessionId));
    mockSettingsData(true);
    controller.doCloseSessions(mockSettingsDataProvider);
    // Since we haven't added any app exit info to the shadow activity manager, there won't exist a
    // single app exit info, and so this method won't be called.
    verify(mockSessionReportingCoordinator, never())
        .persistAppExitInfoEvent(
            eq(sessionId), any(), any(LogFileManager.class), any(UserMetadata.class));
  }

  @Test
  public void testDoCloseSession_enabledAnrs_persistsAppExitInfoIfItExists() {
    final String sessionId = "sessionId";
    final CrashlyticsController controller = createController();
    ApplicationExitInfo testApplicationExitInfo = addAppExitInfo(ApplicationExitInfo.REASON_ANR);

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(Collections.singletonList(sessionId));
    mockSettingsData(true);
    controller.doCloseSessions(mockSettingsDataProvider);
    verify(mockSessionReportingCoordinator)
        .persistAppExitInfoEvent(
            eq(sessionId),
            eq(testApplicationExitInfo),
            any(LogFileManager.class),
            any(UserMetadata.class));
  }

  @Test
  public void testDoCloseSession_disabledAnrs_doesNotPersistsAppExitInfo() {
    final String sessionId = "sessionId";
    final CrashlyticsController controller = createController();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(Collections.singletonList(sessionId));
    mockSettingsData(false);
    controller.doCloseSessions(mockSettingsDataProvider);
    verify(mockSessionReportingCoordinator, never())
        .persistAppExitInfoEvent(
            eq(sessionId), any(), any(LogFileManager.class), any(UserMetadata.class));
  }

  private void mockSettingsData(boolean collectAnrs) {
    Settings mockSettings = mock(Settings.class);
    when(mockSettingsDataProvider.getSettings()).thenReturn(mockSettings);
    when(mockSettings.getFeaturesData()).thenReturn(new FeaturesSettingsData(true, collectAnrs));
  }

  /** Creates a new CrashlyticsController with default options and opens a session. */
  private CrashlyticsController createController() {
    AppData appData =
        new AppData(
            GOOGLE_APP_ID,
            "buildId",
            "installerPackageName",
            "packageName",
            "versionCode",
            "versionName",
            mock(UnityVersionProvider.class));

    final CrashlyticsController controller =
        new CrashlyticsController(
            testContext,
            new CrashlyticsBackgroundWorker(Runnable::run),
            idManager,
            mockDataCollectionArbiter,
            mockFileStore,
            new CrashlyticsFileMarker(CrashlyticsCore.CRASH_MARKER_FILE_NAME, mockFileStore),
            appData,
            null,
            null,
            mockLogFileDirecotryProvider,
            mockSessionReportingCoordinator,
            new ProviderProxyNativeComponent(() -> null),
            mock(AnalyticsEventLogger.class));
    controller.openSession();
    return controller;
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
