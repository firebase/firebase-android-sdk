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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.DevelopmentPlatformProvider;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.RemoteConfigDeferredProxy;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.Settings;
import com.google.firebase.crashlytics.internal.settings.Settings.FeatureFlagData;
import com.google.firebase.crashlytics.internal.settings.Settings.SessionData;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class)
public class CrashlyticsCoreRobolectricTest {
  private static final String GOOGLE_APP_ID = "google:app:id";

  private Context context;
  private CrashlyticsWorkers crashlyticsWorkers;
  private RemoteConfigDeferredProxy remoteConfigDeferredProxy;
  private SettingsProvider settingsProvider;
  private Thread.UncaughtExceptionHandler originalDefaultHandler;

  @Before
  public void setUp() {
    // ShadowLog accumulates entries across tests, and some tests assert that nothing was logged.
    ShadowLog.clear();
    context = ApplicationProvider.getApplicationContext();
    crashlyticsWorkers =
        new CrashlyticsWorkers(TestOnlyExecutors.background(), TestOnlyExecutors.blocking());
    remoteConfigDeferredProxy = mock(RemoteConfigDeferredProxy.class);

    Settings settings =
        new Settings(
            0, new SessionData(4, 4), new FeatureFlagData(true, false, false), 3, 0, 1.0, 1.0, 1);
    settingsProvider = mock(SettingsProvider.class);
    when(settingsProvider.getSettingsSync()).thenReturn(settings);
    when(settingsProvider.getSettingsAsync()).thenReturn(Tasks.forResult(settings));

    // A successful onPreExecute installs the Crashlytics uncaught exception handler, which calls
    // System.exit when it has no default handler to delegate to. Give it a no-op one.
    originalDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {});
  }

  @After
  public void tearDown() throws InterruptedException {
    Thread.setDefaultUncaughtExceptionHandler(originalDefaultHandler);
    awaitCommonWorker();
  }

  @Test
  public void checkForUnsentReports_initializationFailed_returnsFalse() {
    CrashlyticsCore core = createCoreWithFailedInitialization();

    Task<Boolean> task = core.checkForUnsentReports();

    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isFalse();
    assertThat(countWarningsMentioning("checkForUnsentReports")).isEqualTo(1L);
  }

  @Test
  public void sendUnsentReports_initializationFailed_returnsCompletedTask() {
    CrashlyticsCore core = createCoreWithFailedInitialization();

    Task<Void> task = core.sendUnsentReports();

    assertThat(task.isSuccessful()).isTrue();
    assertThat(countWarningsMentioning("sendUnsentReports")).isEqualTo(1L);
  }

  @Test
  public void deleteUnsentReports_initializationFailed_returnsCompletedTask() {
    CrashlyticsCore core = createCoreWithFailedInitialization();

    Task<Void> task = core.deleteUnsentReports();

    assertThat(task.isSuccessful()).isTrue();
    assertThat(countWarningsMentioning("deleteUnsentReports")).isEqualTo(1L);
  }

  @Test
  public void checkForUnsentReports_initialized_returnsControllerTask() {
    CrashlyticsCore core = createInitializedCore();
    CrashlyticsController controller = core.getController();

    Task<Boolean> task = core.checkForUnsentReports();

    // The task stays pending until background initialization has looked for unsent reports.
    assertThat(task).isSameInstanceAs(controller.unsentReportsAvailable.getTask());
    assertThat(task.isComplete()).isFalse();
    assertThat(countWarningsMentioning("checkForUnsentReports")).isEqualTo(0L);
  }

  @Test
  public void sendUnsentReports_initialized_forwardsSendActionToController() {
    CrashlyticsCore core = createInitializedCore();
    CrashlyticsController controller = core.getController();

    Task<Void> task = core.sendUnsentReports();

    assertThat(task).isSameInstanceAs(controller.unsentReportsHandled.getTask());
    assertThat(controller.reportActionProvided.getTask().getResult()).isTrue();
    assertThat(countWarningsMentioning("sendUnsentReports")).isEqualTo(0L);
  }

  @Test
  public void deleteUnsentReports_initialized_forwardsDeleteActionToController() {
    CrashlyticsCore core = createInitializedCore();
    CrashlyticsController controller = core.getController();

    Task<Void> task = core.deleteUnsentReports();

    assertThat(task).isSameInstanceAs(controller.unsentReportsHandled.getTask());
    assertThat(controller.reportActionProvided.getTask().getResult()).isFalse();
    assertThat(countWarningsMentioning("deleteUnsentReports")).isEqualTo(0L);
  }

  /**
   * Returns a core whose {@code onPreExecute} caught an exception, which is the state that any
   * initialization failure (for example, a bad server setting) leaves behind.
   */
  private CrashlyticsCore createCoreWithFailedInitialization() {
    doThrow(new RuntimeException("Simulated initialization failure"))
        .when(remoteConfigDeferredProxy)
        .setupListener(any());
    CrashlyticsCore core = createCore();

    assertThat(core.onPreExecute(createAppData(), settingsProvider)).isFalse();
    assertThat(core.getController()).isNull();
    return core;
  }

  private CrashlyticsCore createInitializedCore() {
    CrashlyticsCore core = createCore();

    assertThat(core.onPreExecute(createAppData(), settingsProvider)).isTrue();
    assertThat(core.getController()).isNotNull();
    return core;
  }

  private CrashlyticsCore createCore() {
    FirebaseApp app = mock(FirebaseApp.class);
    when(app.getApplicationContext()).thenReturn(context);
    when(app.getOptions())
        .thenReturn(new FirebaseOptions.Builder().setApplicationId(GOOGLE_APP_ID).build());

    IdManager idManager = mock(IdManager.class);
    when(idManager.getAppIdentifier()).thenReturn("packageName");
    when(idManager.getInstallIds())
        .thenReturn(InstallIdProvider.InstallIds.createWithoutFid("crashlyticsInstallId"));

    // Automatic data collection is disabled, as in apps that use opt-in reporting.
    DataCollectionArbiter arbiter = mock(DataCollectionArbiter.class);
    when(arbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);

    return new CrashlyticsCore(
        app,
        idManager,
        mock(CrashlyticsNativeComponent.class),
        arbiter,
        mock(BreadcrumbSource.class),
        mock(AnalyticsEventLogger.class),
        new FileStore(context),
        mock(CrashlyticsAppQualitySessionsSubscriber.class),
        remoteConfigDeferredProxy,
        crashlyticsWorkers);
  }

  private static AppData createAppData() {
    return new AppData(
        GOOGLE_APP_ID,
        "buildId",
        Collections.emptyList(),
        "installerPackageName",
        "packageName",
        "versionCode",
        "versionName",
        mock(DevelopmentPlatformProvider.class));
  }

  private static long countWarningsMentioning(String text) {
    return ShadowLog.getLogsForTag(Logger.TAG).stream()
        .filter(item -> item.type == Log.WARN && item.msg.contains(text))
        .count();
  }

  /** Waits for work queued on the common worker, such as opening a session, to finish. */
  private void awaitCommonWorker() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    crashlyticsWorkers.common.submit(latch::countDown);
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
  }
}
