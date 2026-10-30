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

package com.google.firebase.crashlytics.internal.settings;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.crashlytics.internal.common.CurrentTimeProvider;
import com.google.firebase.crashlytics.internal.common.DataCollectionArbiter;
import com.google.firebase.crashlytics.internal.common.DeliveryMechanism;
import com.google.firebase.crashlytics.internal.common.InstallIdProvider.InstallIds;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests that {@link SettingsController} applies cached settings as before, and that it doesn't
 * apply cached settings again after a launch that applied them and didn't finish initialization.
 */
@RunWith(RobolectricTestRunner.class)
public class SettingsControllerRobolectricTest {
  // The file names are part of the on-disk format, so they are hardcoded here.
  private static final String CACHED_SETTINGS_FILE_NAME = "com.crashlytics.settings.json";
  private static final String MARKER_FILE_NAME = "com.crashlytics.settings.applying";

  private static final long CURRENT_TIME_MILLIS = 1_000_000_000L;
  private static final long UNEXPIRED_MILLIS = CURRENT_TIME_MILLIS + TimeUnit.HOURS.toMillis(1);
  private static final long EXPIRED_MILLIS = CURRENT_TIME_MILLIS - 1;

  private static final double CACHED_UPLOAD_RATE = 20;
  private static final double FETCHED_UPLOAD_RATE = 30;

  private static final long TIMEOUT_SECONDS = 10;

  private final CurrentTimeProvider currentTimeProvider = () -> CURRENT_TIME_MILLIS;
  private final CrashlyticsWorkers crashlyticsWorkers =
      new CrashlyticsWorkers(TestOnlyExecutors.background(), TestOnlyExecutors.blocking());

  private Context context;
  private FileStore fileStore;
  private SettingsSpiCall mockSettingsSpiCall;

  // The data collection arbiter and permission of the latest simulated launch.
  private DataCollectionArbiter mockDataCollectionArbiter;
  private TaskCompletionSource<Void> dataCollectionPermission;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    fileStore = new FileStore(context);
    mockSettingsSpiCall = mock(SettingsSpiCall.class);
    when(mockSettingsSpiCall.invoke(any(SettingsRequest.class), anyBoolean()))
        .thenAnswer(invocation -> settingsJson(FETCHED_UPLOAD_RATE));
  }

  @After
  public void tearDown() {
    fileStore.deleteAllCrashlyticsFiles();
  }

  @Test
  public void loadSettingsData_unexpiredCachedSettings_appliesThemAndCreatesMarker()
      throws Exception {
    writeCachedSettings(UNEXPIRED_MILLIS);
    SettingsController controller = newLaunch(/* buildInstanceIdentifierChanged= */ false);

    Task<Void> loadTask = controller.loadSettingsData(crashlyticsWorkers);

    assertCachedSettingsApplied(controller);
    assertThat(markerFile().exists()).isTrue();
    // As before, unexpired settings from the same build are used without a fetch.
    assertThat(loadTask.isComplete()).isTrue();
    verify(mockDataCollectionArbiter, never()).waitForDataCollectionPermission();
    verifyNoInteractions(mockSettingsSpiCall);
  }

  @Test
  public void onSynchronousInitializationComplete_removesMarkerAndKeepsCachedSettings()
      throws Exception {
    writeCachedSettings(UNEXPIRED_MILLIS);
    SettingsController controller = newLaunch(/* buildInstanceIdentifierChanged= */ false);
    controller.loadSettingsData(crashlyticsWorkers);
    assertThat(markerFile().exists()).isTrue();

    controller.onSynchronousInitializationComplete();

    assertThat(markerFile().exists()).isFalse();
    assertThat(cachedSettingsFile().exists()).isTrue();
    assertCachedSettingsApplied(controller);

    // The next launch applies the cached settings again.
    SettingsController nextLaunch = newLaunch(/* buildInstanceIdentifierChanged= */ false);
    nextLaunch.loadSettingsData(crashlyticsWorkers);
    assertCachedSettingsApplied(nextLaunch);
    assertThat(markerFile().exists()).isTrue();
  }

  @Test
  public void loadSettingsData_expiredCachedSettings_appliesThemCreatesMarkerAndFetches()
      throws Exception {
    writeCachedSettings(EXPIRED_MILLIS);
    SettingsController controller = newLaunch(/* buildInstanceIdentifierChanged= */ false);

    Task<Void> loadTask = controller.loadSettingsData(crashlyticsWorkers);

    assertCachedSettingsApplied(controller);
    assertThat(markerFile().exists()).isTrue();
    assertFetchesAndAppliesNewSettings(controller, loadTask);
  }

  @Test
  public void loadSettingsData_cachedSettingsFromAnotherBuild_appliesThemCreatesMarkerAndFetches()
      throws Exception {
    writeCachedSettings(UNEXPIRED_MILLIS);
    SettingsController controller = newLaunch(/* buildInstanceIdentifierChanged= */ true);

    Task<Void> loadTask = controller.loadSettingsData(crashlyticsWorkers);

    assertCachedSettingsApplied(controller);
    assertThat(markerFile().exists()).isTrue();
    assertFetchesAndAppliesNewSettings(controller, loadTask);
  }

  @Test
  public void loadSettingsData_noCachedSettings_keepsDefaultsWithoutMarkerAndFetches()
      throws Exception {
    SettingsController controller = newLaunch(/* buildInstanceIdentifierChanged= */ true);
    Settings defaultSettings = controller.getSettingsSync();

    Task<Void> loadTask = controller.loadSettingsData(crashlyticsWorkers);

    assertThat(controller.getSettingsSync()).isSameInstanceAs(defaultSettings);
    assertThat(controller.getSettingsAsync().isComplete()).isFalse();
    assertThat(markerFile().exists()).isFalse();
    assertFetchesAndAppliesNewSettings(controller, loadTask);
    assertThat(markerFile().exists()).isFalse();
  }

  @Test
  public void loadSettingsData_afterLaunchThatDidNotFinishInit_discardsCachedSettingsAndFetches()
      throws Exception {
    writeCachedSettings(UNEXPIRED_MILLIS);
    // This launch applies the cached settings, and then the app crashes before initialization
    // finishes, so onSynchronousInitializationComplete is not called.
    newLaunch(/* buildInstanceIdentifierChanged= */ false).loadSettingsData(crashlyticsWorkers);

    SettingsController nextLaunch = newLaunch(/* buildInstanceIdentifierChanged= */ false);
    Settings defaultSettings = nextLaunch.getSettingsSync();
    Task<Void> loadTask = nextLaunch.loadSettingsData(crashlyticsWorkers);

    assertThat(nextLaunch.getSettingsSync()).isSameInstanceAs(defaultSettings);
    assertThat(nextLaunch.getSettingsAsync().isComplete()).isFalse();
    assertThat(cachedSettingsFile().exists()).isFalse();
    assertThat(markerFile().exists()).isFalse();
    assertFetchesAndAppliesNewSettings(nextLaunch, loadTask);
  }

  @Test
  public void loadSettingsData_afterAppUpdate_discardsCachedSettingsThatDidNotFinishInit()
      throws Exception {
    writeCachedSettings(EXPIRED_MILLIS);
    // This launch applies the expired settings and starts a fetch, and then the app crashes before
    // initialization finishes and before the fetch completes.
    newLaunch(/* buildInstanceIdentifierChanged= */ false).loadSettingsData(crashlyticsWorkers);

    // The next launch runs a new build of the app.
    SettingsController nextLaunch = newLaunch(/* buildInstanceIdentifierChanged= */ true);
    Settings defaultSettings = nextLaunch.getSettingsSync();
    Task<Void> loadTask = nextLaunch.loadSettingsData(crashlyticsWorkers);

    assertThat(nextLaunch.getSettingsSync()).isSameInstanceAs(defaultSettings);
    assertThat(nextLaunch.getSettingsAsync().isComplete()).isFalse();
    assertThat(cachedSettingsFile().exists()).isFalse();
    assertThat(markerFile().exists()).isFalse();
    assertFetchesAndAppliesNewSettings(nextLaunch, loadTask);
  }

  @Test
  public void loadSettingsData_markerPresentAndCacheNotDeletable_doesNotApplyCachedSettings()
      throws Exception {
    writeCachedSettings(UNEXPIRED_MILLIS);
    // The previous launch applied the cached settings and didn't finish initialization.
    assertThat(markerFile().createNewFile()).isTrue();
    File undeletableCachedSettingsFile =
        new File(cachedSettingsFile().getPath()) {
          @Override
          public boolean delete() {
            return false;
          }
        };
    FileStore fileStoreWithUndeletableCache = spy(fileStore);
    doReturn(undeletableCachedSettingsFile)
        .when(fileStoreWithUndeletableCache)
        .getCommonFile(CACHED_SETTINGS_FILE_NAME);
    SettingsController controller =
        newLaunch(/* buildInstanceIdentifierChanged= */ false, fileStoreWithUndeletableCache);
    Settings defaultSettings = controller.getSettingsSync();

    controller.loadSettingsData(crashlyticsWorkers);

    assertThat(controller.getSettingsSync()).isSameInstanceAs(defaultSettings);
    assertThat(controller.getSettingsAsync().isComplete()).isFalse();
    assertThat(cachedSettingsFile().exists()).isTrue();
    assertThat(markerFile().exists()).isFalse();
  }

  @Test
  public void loadSettingsData_markerCannotBeCreated_stillAppliesCachedSettings() throws Exception {
    writeCachedSettings(UNEXPIRED_MILLIS);
    // Creating the marker fails because its directory doesn't exist.
    File markerInMissingDirectory =
        new File(new File(context.getFilesDir(), "missing-directory"), MARKER_FILE_NAME);
    FileStore fileStoreWithBadMarker = spy(fileStore);
    doReturn(markerInMissingDirectory).when(fileStoreWithBadMarker).getCommonFile(MARKER_FILE_NAME);
    SettingsController controller =
        newLaunch(/* buildInstanceIdentifierChanged= */ false, fileStoreWithBadMarker);

    controller.loadSettingsData(crashlyticsWorkers);
    controller.onSynchronousInitializationComplete();

    assertCachedSettingsApplied(controller);
    assertThat(markerInMissingDirectory.exists()).isFalse();
  }

  @Test
  public void onSynchronousInitializationComplete_withoutMarker_doesNothing() throws Exception {
    SettingsController controller = newLaunch(/* buildInstanceIdentifierChanged= */ true);
    Settings defaultSettings = controller.getSettingsSync();
    controller.loadSettingsData(crashlyticsWorkers);
    assertThat(markerFile().exists()).isFalse();

    controller.onSynchronousInitializationComplete();

    assertThat(markerFile().exists()).isFalse();
    assertThat(controller.getSettingsSync()).isSameInstanceAs(defaultSettings);
  }

  /**
   * Simulates a new app launch: returns a new controller that uses the files that earlier launches
   * left behind. A fetch only runs after the test completes {@link #dataCollectionPermission}, so
   * fetches started by earlier launches never complete, as if their process died.
   */
  private SettingsController newLaunch(boolean buildInstanceIdentifierChanged) {
    return newLaunch(buildInstanceIdentifierChanged, fileStore);
  }

  private SettingsController newLaunch(
      boolean buildInstanceIdentifierChanged, FileStore launchFileStore) {
    dataCollectionPermission = new TaskCompletionSource<>();
    mockDataCollectionArbiter = mock(DataCollectionArbiter.class);
    when(mockDataCollectionArbiter.waitForDataCollectionPermission())
        .thenReturn(dataCollectionPermission.getTask());
    return new SettingsController(
        context,
        newSettingsRequest(),
        currentTimeProvider,
        new SettingsJsonParser(currentTimeProvider),
        new CachedSettingsIo(launchFileStore),
        mockSettingsSpiCall,
        mockDataCollectionArbiter) {
      @Override
      boolean buildInstanceIdentifierChanged() {
        return buildInstanceIdentifierChanged;
      }
    };
  }

  private void assertCachedSettingsApplied(SettingsController controller) {
    Settings settings = controller.getSettingsSync();
    assertThat(settings.onDemandUploadRatePerMinute).isEqualTo(CACHED_UPLOAD_RATE);
    assertThat(controller.getSettingsAsync().isComplete()).isTrue();
    assertThat(controller.getSettingsAsync().getResult()).isSameInstanceAs(settings);
  }

  /** Grants data collection permission, and checks that new settings are fetched and cached. */
  private void assertFetchesAndAppliesNewSettings(
      SettingsController controller, Task<Void> loadTask) throws Exception {
    dataCollectionPermission.setResult(null);
    await(loadTask);

    assertThat(loadTask.isSuccessful()).isTrue();
    verify(mockSettingsSpiCall).invoke(any(SettingsRequest.class), eq(true));
    assertThat(controller.getSettingsSync().onDemandUploadRatePerMinute)
        .isEqualTo(FETCHED_UPLOAD_RATE);
    assertThat(controller.getSettingsAsync().isComplete()).isTrue();
    JSONObject cachedSettingsJson = new CachedSettingsIo(fileStore).readCachedSettings();
    assertThat(cachedSettingsJson.getDouble("on_demand_upload_rate_per_minute"))
        .isEqualTo(FETCHED_UPLOAD_RATE);
  }

  private void writeCachedSettings(long expiresAtMillis) throws JSONException {
    new CachedSettingsIo(fileStore)
        .writeCachedSettings(expiresAtMillis, settingsJson(CACHED_UPLOAD_RATE));
  }

  private File cachedSettingsFile() {
    return fileStore.getCommonFile(CACHED_SETTINGS_FILE_NAME);
  }

  private File markerFile() {
    return fileStore.getCommonFile(MARKER_FILE_NAME);
  }

  private static JSONObject settingsJson(double onDemandUploadRatePerMinute) throws JSONException {
    return new JSONObject()
        .put("settings_version", 3)
        .put("cache_duration", 3600)
        .put("on_demand_upload_rate_per_minute", onDemandUploadRatePerMinute)
        .put("features", new JSONObject().put("collect_reports", true));
  }

  private static SettingsRequest newSettingsRequest() {
    return new SettingsRequest(
        "1:12345678901:android:1234567890abcdef",
        "deviceModel",
        "osBuildVersion",
        "osDisplayVersion",
        () -> InstallIds.createWithoutFid("crashlyticsInstallId"),
        "instanceId",
        "1.0",
        "1",
        DeliveryMechanism.APP_STORE.getId());
  }

  /** Waits for the task. Tasks.await can't be used because tests run on the main thread. */
  private static void await(Task<?> task) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    task.addOnCompleteListener(Runnable::run, completedTask -> latch.countDown());
    assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
  }
}
