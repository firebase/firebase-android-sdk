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

import com.google.firebase.crashlytics.internal.common.CurrentTimeProvider;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.FeaturesSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SessionSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.Settings;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import org.json.JSONException;
import org.json.JSONObject;

/** Default implementation of the JSON <-> {@link SettingsData} transform logic. */
class DefaultSettingsJsonTransform implements SettingsJsonTransform {

  @Override
  public SettingsData buildFromJson(CurrentTimeProvider currentTimeProvider, JSONObject json)
      throws JSONException {

    final int settingsVersion =
        json.optInt(
            SettingsJsonConstants.SETTINGS_VERSION, SettingsJsonConstants.SETTINGS_VERSION_DEFAULT);
    final int cacheDuration =
        json.optInt(
            SettingsJsonConstants.CACHE_DURATION_KEY,
            SettingsJsonConstants.SETTINGS_CACHE_DURATION_DEFAULT);

    final AppSettingsData appData =
        buildAppDataFrom(json.getJSONObject(SettingsJsonConstants.APP_KEY));
    final SessionSettingsData settingsData =
        buildSessionDataFrom(json.getJSONObject(SettingsJsonConstants.SESSION_KEY));
    final FeaturesSettingsData featureData =
        buildFeaturesSessionDataFrom(json.getJSONObject(SettingsJsonConstants.FEATURES_KEY));

    final long expiresAtMillis = getExpiresAtFrom(currentTimeProvider, cacheDuration, json);

    return new SettingsData(
        expiresAtMillis, appData, settingsData, featureData, settingsVersion, cacheDuration);
  }

  /** Creates a new Settings with reasonable default values. */
  static Settings defaultSettings(CurrentTimeProvider currentTimeProvider) {
    final int settingsVersion = SettingsJsonConstants.SETTINGS_VERSION_DEFAULT;
    final int cacheDuration = SettingsJsonConstants.SETTINGS_CACHE_DURATION_DEFAULT;

    JSONObject empty = new JSONObject();
    final AppSettingsData appData = null;
    final SessionSettingsData settingsData = buildSessionDataFrom(empty);
    final FeaturesSettingsData featureData = buildFeaturesSessionDataFrom(empty);

    final long expiresAtMillis = getExpiresAtFrom(currentTimeProvider, cacheDuration, empty);

    return new SettingsData(
        expiresAtMillis, appData, settingsData, featureData, settingsVersion, cacheDuration);
  }

  @Override
  public JSONObject toJson(SettingsData settingsData) throws JSONException {
    return new JSONObject()
        .put(SettingsJsonConstants.EXPIRES_AT_KEY, settingsData.expiresAtMillis)
        .put(SettingsJsonConstants.CACHE_DURATION_KEY, settingsData.cacheDuration)
        .put(SettingsJsonConstants.SETTINGS_VERSION, settingsData.settingsVersion)
        .put(SettingsJsonConstants.FEATURES_KEY, toFeaturesJson(settingsData.featuresData))
        .put(SettingsJsonConstants.APP_KEY, toAppJson(settingsData.appData))
        .put(SettingsJsonConstants.SESSION_KEY, toSessionJson(settingsData.sessionData));
  }

  private static AppSettingsData buildAppDataFrom(JSONObject json) throws JSONException {
    final String status = json.getString(SettingsJsonConstants.APP_STATUS_KEY);
    final String url = json.getString(SettingsJsonConstants.APP_URL_KEY);
    final String reportsUrl = json.getString(SettingsJsonConstants.APP_REPORTS_URL_KEY);
    final String ndkReportsUrl = json.getString(SettingsJsonConstants.APP_NDK_REPORTS_URL_KEY);
    final boolean updateRequired =
        json.optBoolean(
            SettingsJsonConstants.APP_UPDATE_REQUIRED_KEY,
            SettingsJsonConstants.APP_UPDATE_REQUIRED_DEFAULT);

    return new AppSettingsData(status, url, reportsUrl, ndkReportsUrl, updateRequired);
  }

  private static FeaturesSettingsData buildFeaturesSessionDataFrom(JSONObject json) {
    final boolean collectReports =
        json.optBoolean(
            SettingsJsonConstants.FEATURES_COLLECT_REPORTS_KEY,
            SettingsJsonConstants.FEATURES_COLLECT_REPORTS_DEFAULT);

    return new FeaturesSettingsData(collectReports);
  }

  private static SessionSettingsData buildSessionDataFrom(JSONObject json) {
    final int maxCustomExceptionEvents =
        json.optInt(
            SettingsJsonConstants.SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_KEY,
            SettingsJsonConstants.SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_DEFAULT);
    final int maxCompleteSessionsCount =
        SettingsJsonConstants.SETTINGS_MAX_COMPLETE_SESSIONS_COUNT_DEFAULT;

    return new SessionSettingsData(maxCustomExceptionEvents, maxCompleteSessionsCount);
  }

  private static long getExpiresAtFrom(
      CurrentTimeProvider currentTimeProvider, long cacheDurationSeconds, JSONObject json) {
    long expiresAtMillis = 0;

    if (json.has(SettingsJsonConstants.EXPIRES_AT_KEY)) {
      // If the JSON we receive has an expires_at key, use that value
      expiresAtMillis = json.optLong(SettingsJsonConstants.EXPIRES_AT_KEY);
    } else {
      // If not, construct a new one from the current time and the loaded cache duration
      // (in seconds)
      final long currentTimeMillis = currentTimeProvider.getCurrentTimeMillis();
      expiresAtMillis = currentTimeMillis + (cacheDurationSeconds * 1000);
    }

    return expiresAtMillis;
  }

  private JSONObject toAppJson(AppSettingsData appData) throws JSONException {
    final JSONObject json =
        new JSONObject()
            .put(SettingsJsonConstants.APP_STATUS_KEY, appData.status)
            .put(SettingsJsonConstants.APP_URL_KEY, appData.url)
            .put(SettingsJsonConstants.APP_REPORTS_URL_KEY, appData.reportsUrl)
            .put(SettingsJsonConstants.APP_NDK_REPORTS_URL_KEY, appData.ndkReportsUrl)
            .put(SettingsJsonConstants.APP_UPDATE_REQUIRED_KEY, appData.updateRequired);

    return json;
  }

  private JSONObject toFeaturesJson(FeaturesSettingsData features) throws JSONException {
    return new JSONObject()
        .put(SettingsJsonConstants.FEATURES_COLLECT_REPORTS_KEY, features.collectReports);
  }

  private JSONObject toSessionJson(SessionSettingsData data) throws JSONException {
    return new JSONObject()
        .put(
            SettingsJsonConstants.SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_KEY,
            data.maxCustomExceptionEvents)
        .put(
            SettingsJsonConstants.SETTINGS_MAX_COMPLETE_SESSIONS_COUNT_KEY,
            data.maxCompleteSessionsCount);
  }
}
