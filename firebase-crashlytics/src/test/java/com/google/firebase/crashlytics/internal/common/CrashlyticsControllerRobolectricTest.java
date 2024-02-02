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
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponentDeferredProxy;
import com.google.firebase.crashlytics.internal.DevelopmentPlatformProvider;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.metadata.LogFileManager;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.Settings;
import com.google.firebase.crashlytics.internal.settings.Settings.FeatureFlagData;
import com.google.firebase.crashlytics.internal.settings.Settings.SessionData;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
import com.google.firebase.inject.Deferred;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CrashlyticsControllerRobolectricTest {
  private static final String GOOGLE_APP_ID = "google:app:id";
  private static final String SESSION_ID = "session_id";

  private Context testContext;
  @Mock private IdManager idManager;
  @Mock private SettingsProvider mockSettingsProvider;
  @Mock private FileStore testFileStore;
  @Mock private SessionReportingCoordinator mockSessionReportingCoordinator;
  @Mock private DataCollectionArbiter mockDataCollectionArbiter;

  private static final CrashlyticsNativeComponent MISSING_NATIVE_COMPONENT =
      new CrashlyticsNativeComponentDeferredProxy(
          new Deferred<CrashlyticsNativeComponent>() {
            @Override
            public void whenAvailable(
                @NonNull Deferred.DeferredHandler<CrashlyticsNativeComponent> handler) {
              // no-op
            }
          });

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    testContext = getApplicationContext();
    testFileStore = new FileStore(testContext);
  }

  @Test
  public void testDoCloseSession_enabledAnrs_doesNotPersistsAppExitInfoIfItDoesntExist() {
    final String sessionId = "sessionId";
    final CrashlyticsController controller = createController();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Collections.singletonList(sessionId)));
    mockSettingsProvider(true, false);
    controller.doCloseSessions(mockSettingsProvider);
    // Since we haven't added any app exit info to the shadow activity manager, there won't exist a
    // single app exit info, and so this method won't be called.
    verify(mockSessionReportingCoordinator, never())
        .persistRelevantAppExitInfoEvent(
            eq(sessionId), any(), any(LogFileManager.class), any(UserMetadata.class));
  }

  @Test
  public void testDoCloseSession_enabledAnrs_persistsAppExitInfoIfItExists() {
    final String sessionId = "sessionId";
    final CrashlyticsController controller = createController();
    // Adds multiple AppExitInfos to confirm that Crashlytics loops through
    // them rather than just looking at the first.
    addAppExitInfo(ApplicationExitInfo.REASON_ANR);
    addAppExitInfo(ApplicationExitInfo.REASON_EXIT_SELF);
    List<ApplicationExitInfo> testApplicationExitInfo = getApplicationExitInfoList();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Collections.singletonList(sessionId)));
    mockSettingsProvider(true, false);
    controller.doCloseSessions(mockSettingsProvider);
    verify(mockSessionReportingCoordinator)
        .persistRelevantAppExitInfoEvent(
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
        .thenReturn(new TreeSet<>(Collections.singletonList(sessionId)));
    mockSettingsProvider(false, false);
    controller.doCloseSessions(mockSettingsProvider);
    verify(mockSessionReportingCoordinator, never())
        .persistRelevantAppExitInfoEvent(
            eq(sessionId), any(), any(LogFileManager.class), any(UserMetadata.class));
  }

  private void mockSettingsProvider(boolean collectAnrs, boolean collectBuildIds) {
    Settings settings =
        new Settings(
            0,
            new SessionData(4, 4),
            new FeatureFlagData(true, collectAnrs, collectBuildIds),
            3,
            0,
            1.0,
            1.0,
            1);
    when(mockSettingsProvider.getSettingsSync()).thenReturn(settings);
  }

  /** Creates a new CrashlyticsController with default options and opens a session. */
  private CrashlyticsController createController() {
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
            testContext,
            new CrashlyticsBackgroundWorker(Runnable::run),
            idManager,
            mockDataCollectionArbiter,
            testFileStore,
            new CrashlyticsFileMarker(CrashlyticsCore.CRASH_MARKER_FILE_NAME, testFileStore),
            appData,
            null,
            null,
            mockSessionReportingCoordinator,
            MISSING_NATIVE_COMPONENT,
            mock(AnalyticsEventLogger.class),
            mock(CrashlyticsAppQualitySessionsSubscriber.class));
    controller.openSession(SESSION_ID);
    return controller;
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
  }

  private List<ApplicationExitInfo> getApplicationExitInfoList() {
    ActivityManager activityManager =
        (ActivityManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
    return activityManager.getHistoricalProcessExitReasons(null, 0, 0);
  }
}
