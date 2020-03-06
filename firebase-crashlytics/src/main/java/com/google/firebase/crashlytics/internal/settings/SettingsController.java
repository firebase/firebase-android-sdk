// Copyright 2019 Google LLC
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.common.CurrentTimeProvider;
import com.google.firebase.crashlytics.internal.common.DataCollectionArbiter;
import com.google.firebase.crashlytics.internal.common.DeliveryMechanism;
import com.google.firebase.crashlytics.internal.common.IdManager;
import com.google.firebase.crashlytics.internal.common.SystemCurrentTimeProvider;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.Settings;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SettingsRequest;
import com.google.firebase.crashlytics.internal.settings.network.DefaultSettingsSpiCall;
import com.google.firebase.crashlytics.internal.settings.network.SettingsSpiCall;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONException;
import org.json.JSONObject;

/** Implements the logic of when to use cached settings, and when to load them from the network. */
public class SettingsController implements SettingsDataProvider {
  private static final String PREFS_BUILD_INSTANCE_IDENTIFIER = "existing_instance_identifier";

  private static final String SETTINGS_URL_FORMAT =
      "https://firebase-settings.crashlytics.com/spi/v2/platforms/android/gmp/%s/settings";

  private final Context context;
  private final SettingsRequest settingsRequest;
  private final SettingsJsonParser settingsJsonParser;
  private final CurrentTimeProvider currentTimeProvider;
  private final CachedSettingsIo cachedSettingsIo;
  private final SettingsSpiCall settingsSpiCall;

  // Data collection permissions.
  private final DataCollectionArbiter dataCollectionArbiter;

  private final AtomicReference<Settings> settings = new AtomicReference<>();
  private final AtomicReference<TaskCompletionSource<AppSettingsData>> appSettingsData =
      new AtomicReference<>(new TaskCompletionSource<>());

  SettingsController(
      Context context,
      SettingsRequest settingsRequest,
      CurrentTimeProvider currentTimeProvider,
      SettingsJsonParser settingsJsonParser,
      CachedSettingsIo cachedSettingsIo,
      SettingsSpiCall settingsSpiCall,
      DataCollectionArbiter dataCollectionArbiter) {
    this.context = context;
    this.settingsRequest = settingsRequest;
    this.currentTimeProvider = currentTimeProvider;
    this.settingsJsonParser = settingsJsonParser;
    this.cachedSettingsIo = cachedSettingsIo;
    this.settingsSpiCall = settingsSpiCall;
    this.dataCollectionArbiter = dataCollectionArbiter;

    settings.set(DefaultSettingsJsonTransform.defaultSettings(currentTimeProvider));
  }

  public static SettingsController create(
      Context context,
      String googleAppId,
      IdManager idManager,
      HttpRequestFactory httpRequestFactory,
      String versionCode,
      String versionName,
      String urlEndpoint,
      DataCollectionArbiter dataCollectionArbiter) {

    final String installerPackageName = idManager.getInstallerPackageName();
    final CurrentTimeProvider currentTimeProvider = new SystemCurrentTimeProvider();
    final SettingsJsonParser settingsJsonParser = new SettingsJsonParser(currentTimeProvider);
    final CachedSettingsIo cachedSettingsIo = new CachedSettingsIo(context);
    final String settingsUrl = String.format(Locale.US, SETTINGS_URL_FORMAT, googleAppId);
    final SettingsSpiCall settingsSpiCall =
        new DefaultSettingsSpiCall(urlEndpoint, settingsUrl, httpRequestFactory);

    final String deviceModel = idManager.getModelName();
    final String osBuildVersion = idManager.getOsBuildVersionString();
    final String osDisplayVersion = idManager.getOsDisplayVersionString();
    final String instanceId =
        CommonUtils.createInstanceIdFrom(
            CommonUtils.getMappingFileId(context), googleAppId, versionName, versionCode);
    final int deliveryMechanismId = DeliveryMechanism.determineFrom(installerPackageName).getId();

    final SettingsRequest settingsRequest =
        new SettingsRequest(
            googleAppId,
            deviceModel,
            osBuildVersion,
            osDisplayVersion,
            idManager,
            instanceId,
            versionName,
            versionCode,
            deliveryMechanismId);

    return new SettingsController(
        context,
        settingsRequest,
        currentTimeProvider,
        settingsJsonParser,
        cachedSettingsIo,
        settingsSpiCall,
        dataCollectionArbiter);
  }

  /** Gets the best available settings that have been loaded. */
  public Settings getSettings() {
    return settings.get();
  }

  /**
   * Returns a Task that will be resolved with AppSettingsData, once it has been fetched from the
   * network or loaded from the cache.
   */
  public Task<AppSettingsData> getAppSettings() {
    return appSettingsData.get().getTask();
  }

  /**
   * Kicks off loading settings either from the cache or the network.
   *
   * @return a task that is resolved when loading is completely finished.
   */
  public Task<Void> loadSettingsData(Executor executor) {
    return loadSettingsData(SettingsCacheBehavior.USE_CACHE, executor);
  }

  /**
   * Kicks off loading settings either from the cache or the network.
   *
   * @return a task that is resolved when loading is completely finished.
   */
  public Task<Void> loadSettingsData(SettingsCacheBehavior cacheBehavior, Executor executor) {
    // TODO: Refactor this so that it doesn't do the cache lookup twice when settings are
    // expired.

    // We need to bypass the cache if this is the first time a new build has run so the
    // backend will know about it.
    if (!buildInstanceIdentifierChanged()) {
      final SettingsData cachedSettings = getCachedSettingsData(cacheBehavior);
      if (cachedSettings != null) {
        settings.set(cachedSettings);
        appSettingsData.get().trySetResult(cachedSettings.getAppSettingsData());
        return Tasks.forResult(null);
      }
    }

    // SKIP_CACHE doesn't actually skip the cache completely; it just skips the first lookup, since
    // we are doing the cache lookup again here with IGNORE_CACHE_EXPIRATION.
    // This has been true in production for some time, though, so no rush to "fix" it.

    // The cached settings are too old, so if there are expired settings, use those for now.
    final SettingsData expiredSettings =
        getCachedSettingsData(SettingsCacheBehavior.IGNORE_CACHE_EXPIRATION);
    if (expiredSettings != null) {
      settings.set(expiredSettings);
      appSettingsData.get().trySetResult(expiredSettings.getAppSettingsData());
    }

    // Kick off fetching fresh settings.
    return dataCollectionArbiter
        .waitForDataCollectionPermission()
        .onSuccessTask(
            executor,
            new SuccessContinuation<Void, Void>() {
              @NonNull
              @Override
              public Task<Void> then(@Nullable Void aVoid) throws Exception {
                // Waited for data collection permission, so this is safe.
                final boolean dataCollectionToken = true;
                final JSONObject settingsJson =
                    settingsSpiCall.invoke(settingsRequest, dataCollectionToken);

                if (settingsJson != null) {
                  final SettingsData fetchedSettings =
                      settingsJsonParser.parseSettingsJson(settingsJson);
                  cachedSettingsIo.writeCachedSettings(
                      fetchedSettings.getExpiresAtMillis(), settingsJson);
                  logSettings(settingsJson, "Loaded settings: ");

                  setStoredBuildInstanceIdentifier(settingsRequest.instanceId);

                  // Update the regular settings.
                  settings.set(fetchedSettings);

                  // Signal the app settings on any Tasks that already exist, and then replace the
                  // task so
                  // that any new callers get the new app settings instead of any old ones.
                  appSettingsData.get().trySetResult(fetchedSettings.getAppSettingsData());
                  TaskCompletionSource<AppSettingsData> fetchedAppSettings =
                      new TaskCompletionSource<>();
                  fetchedAppSettings.trySetResult(fetchedSettings.getAppSettingsData());
                  appSettingsData.set(fetchedAppSettings);
                }

                return Tasks.forResult(null);
              }
            });
  }

  private SettingsData getCachedSettingsData(SettingsCacheBehavior cacheBehavior) {
    SettingsData toReturn = null;

    try {
      if (!SettingsCacheBehavior.SKIP_CACHE_LOOKUP.equals(cacheBehavior)) {
        final JSONObject settingsJson = cachedSettingsIo.readCachedSettings();

        if (settingsJson != null) {
          final SettingsData settingsData = settingsJsonParser.parseSettingsJson(settingsJson);

          if (settingsData != null) {
            logSettings(settingsJson, "Loaded cached settings: ");

            final long currentTimeMillis = currentTimeProvider.getCurrentTimeMillis();

            if (SettingsCacheBehavior.IGNORE_CACHE_EXPIRATION.equals(cacheBehavior)
                || !settingsData.isExpired(currentTimeMillis)) {
              toReturn = settingsData;
              Logger.getLogger().d("Returning cached settings.");
            } else {
              Logger.getLogger().d("Cached settings have expired.");
            }
          } else {
            Logger.getLogger().e("Failed to parse cached settings data.", null);
          }
        } else {
          Logger.getLogger().d("No cached settings data found.");
        }
      }
    } catch (Exception e) {
      Logger.getLogger().e("Failed to get cached settings", e);
    }

    return toReturn;
  }

  private void logSettings(JSONObject json, String message) throws JSONException {
    Logger.getLogger().d(message + json.toString());
  }

  private String getStoredBuildInstanceIdentifier() {
    final SharedPreferences prefs = CommonUtils.getSharedPrefs(context);
    return prefs.getString(PREFS_BUILD_INSTANCE_IDENTIFIER, "");
  }

  @SuppressLint("CommitPrefEdits")
  private boolean setStoredBuildInstanceIdentifier(String buildInstanceIdentifier) {
    final SharedPreferences prefs = CommonUtils.getSharedPrefs(context);
    final SharedPreferences.Editor editor = prefs.edit();
    editor.putString(PREFS_BUILD_INSTANCE_IDENTIFIER, buildInstanceIdentifier);
    editor.apply();
    return true;
  }

  boolean buildInstanceIdentifierChanged() {
    final String existingInstanceIdentifier = getStoredBuildInstanceIdentifier();
    final String currentInstanceIdentifier = settingsRequest.instanceId;
    return !existingInstanceIdentifier.equals(currentInstanceIdentifier);
  }
}
