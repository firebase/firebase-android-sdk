// Copyright 2026 Google LLC
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
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import android.content.Context;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.DevelopmentPlatformProvider;
import com.google.firebase.crashlytics.internal.RemoteConfigDeferredProxy;
import com.google.firebase.crashlytics.internal.analytics.UnavailableAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.DisabledBreadcrumbSource;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.Settings;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CrashlyticsSessionIdTest {
  private static final String GOOGLE_APP_ID = "1:1234567890:android:abcdef";
  private static final String BUILD_ID = "build-id-1234";

  private Context context;
  private FileStore fileStore;
  private CrashlyticsWorkers crashlyticsWorkers;

  @Mock private IdManager idManager;
  @Mock private SettingsProvider settingsProvider;
  @Mock private CrashlyticsNativeComponent nativeComponent;
  @Mock private FirebaseApp firebaseApp;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    context = getApplicationContext();
    fileStore = new FileStore(context);
    fileStore.deleteAllCrashlyticsFiles();
    fileStore = new FileStore(context);
    crashlyticsWorkers =
        new CrashlyticsWorkers(TestOnlyExecutors.background(), TestOnlyExecutors.blocking());

    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setApiKey("api-key")
            .setApplicationId(GOOGLE_APP_ID)
            .setProjectId("project-id")
            .build();
    when(firebaseApp.getApplicationContext()).thenReturn(context);
    when(firebaseApp.getOptions()).thenReturn(options);
    // Keep automatic collection off so Crashlytics doesn't try to send reports after the on-demand
    // fatal exception.
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(false);
    when(idManager.getAppIdentifier()).thenReturn(context.getPackageName());
    when(idManager.getInstallerPackageName()).thenReturn("installer");
    when(idManager.getInstallIds()).thenReturn(InstallIdProvider.InstallIds.createWithoutFid("id"));
    when(idManager.getModelName()).thenReturn("Pixel");
    when(idManager.getOsBuildVersionString()).thenReturn("14");
    when(idManager.getOsDisplayVersionString()).thenReturn("14");
    Settings settings =
        new Settings(
            Long.MAX_VALUE,
            new Settings.SessionData(8, 4),
            new Settings.FeatureFlagData(true, true, false),
            0,
            3600,
            10.0,
            1.2,
            60);
    when(settingsProvider.getSettingsSync()).thenReturn(settings);
    when(settingsProvider.getSettingsAsync()).thenReturn(Tasks.forResult(settings));
  }

  @Test
  public void getSessionId_matchesSessionOpenedAtStartup() throws Exception {
    CrashlyticsCore core = createCore();
    core.onPreExecute(createAppData(), settingsProvider);
    String sessionId = core.getSessionId();
    assertThat(sessionId).isNotNull();

    awaitCommonWorker();
    assertThat(fileStore.getAllOpenSessionIds()).containsExactly(sessionId);
  }

  @Test
  public void getSessionId_doesNotChangeAfterOnDemandFatalException() throws Exception {
    CrashlyticsCore core = createCore();
    core.onPreExecute(createAppData(), settingsProvider);
    String sessionId = core.getSessionId();
    awaitCommonWorker();

    core.logFatalException(new RuntimeException("on-demand fatal"));
    // The first wait lets the logging task finish. That task queues the session rotation, and the
    // second wait lets the rotation finish.
    awaitCommonWorker();
    awaitCommonWorker();

    List<String> newSessionIds = new ArrayList<>(fileStore.getAllOpenSessionIds());
    newSessionIds.remove(sessionId);
    assertThat(newSessionIds).isNotEmpty();
    assertThat(core.getSessionId()).isEqualTo(sessionId);
  }

  private AppData createAppData() {
    return new AppData(
        GOOGLE_APP_ID,
        BUILD_ID,
        Collections.emptyList(),
        "installer",
        context.getPackageName(),
        "1",
        "1.0",
        new DevelopmentPlatformProvider(context));
  }

  private CrashlyticsCore createCore() {
    DataCollectionArbiter arbiter = new DataCollectionArbiter(firebaseApp);
    CrashlyticsAppQualitySessionsSubscriber subscriber =
        new CrashlyticsAppQualitySessionsSubscriber(arbiter, fileStore);
    RemoteConfigDeferredProxy remoteConfigProxy = new RemoteConfigDeferredProxy(handler -> {});
    return new CrashlyticsCore(
        firebaseApp,
        idManager,
        nativeComponent,
        arbiter,
        new DisabledBreadcrumbSource(),
        new UnavailableAnalyticsEventLogger(),
        fileStore,
        subscriber,
        remoteConfigProxy,
        crashlyticsWorkers);
  }

  /**
   * Waits for tasks already queued on the Crashlytics common worker. Robolectric runs tests on the
   * main thread, where {@code Tasks.await} is not allowed, so this waits on a latch instead.
   */
  private void awaitCommonWorker() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    crashlyticsWorkers
        .common
        .submit(() -> {})
        .addOnCompleteListener(TestOnlyExecutors.background(), unused -> latch.countDown());
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
  }
}
