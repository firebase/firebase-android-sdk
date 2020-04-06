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
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

class SettingsV3JsonTransform implements SettingsJsonTransform {

  private static final String CRASHLYTICS_APP_URL =
      "https://update.crashlytics.com/spi/v1/platforms/android/apps";
  private static final String CRASHLYTICS_APP_URL_FORMAT =
      "https://update.crashlytics.com/spi/v1/platforms/android/apps/%s";
  private static final String REPORTS_URL_FORMAT =
      "https://reports.crashlytics.com/spi/v1/platforms/android/apps/%s/reports";
  private static final String NDK_REPORTS_URL_FORMAT =
      "https://reports.crashlytics.com/sdk-api/v1/platforms/android/apps/%s/minidumps";

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
        buildAppDataFrom(
            json.getJSONObject(SettingsJsonConstants.FABRIC_KEY),
            json.getJSONObject(SettingsJsonConstants.APP_KEY));
    final SessionSettingsData sessionData = defaultSessionData();
    final FeaturesSettingsData featureData =
        buildFeaturesSessionDataFrom(json.getJSONObject(SettingsJsonConstants.FEATURES_KEY));

    final long expiresAtMillis = getExpiresAtFrom(currentTimeProvider, cacheDuration, json);

    return new SettingsData(
        expiresAtMillis, appData, sessionData, featureData, settingsVersion, cacheDuration);
  }

  @Override
  public JSONObject toJson(SettingsData settingsData) throws JSONException {
    return new JSONObject()
        .put(SettingsJsonConstants.EXPIRES_AT_KEY, settingsData.expiresAtMillis)
        .put(SettingsJsonConstants.CACHE_DURATION_KEY, settingsData.cacheDuration)
        .put(SettingsJsonConstants.SETTINGS_VERSION, settingsData.settingsVersion)
        .put(SettingsJsonConstants.FEATURES_KEY, toFeaturesJson(settingsData.featuresData))
        .put(SettingsJsonConstants.APP_KEY, toAppJson(settingsData.appData))
        .put(SettingsJsonConstants.FABRIC_KEY, toFabricJson(settingsData.appData));
  }

  private static AppSettingsData buildAppDataFrom(JSONObject fabricJson, JSONObject appJson)
      throws JSONException {
    final String status = appJson.getString(SettingsJsonConstants.APP_STATUS_KEY);
    final boolean isNewApp = AppSettingsData.STATUS_NEW.equals(status);

    final String bundleId = fabricJson.getString(SettingsJsonConstants.FABRIC_BUNDLE_ID);
    final String organizationId =
        fabricJson.getString(SettingsJsonConstants.FABRIC_ORGANIZATION_ID);

    final String url =
        isNewApp
            ? CRASHLYTICS_APP_URL
            : String.format(Locale.US, CRASHLYTICS_APP_URL_FORMAT, bundleId);
    final String reportsUrl = String.format(Locale.US, REPORTS_URL_FORMAT, bundleId);
    final String ndkReportsUrl = String.format(Locale.US, NDK_REPORTS_URL_FORMAT, bundleId);

    final boolean updateRequired =
        appJson.optBoolean(
            SettingsJsonConstants.APP_UPDATE_REQUIRED_KEY,
            SettingsJsonConstants.APP_UPDATE_REQUIRED_DEFAULT);

    final int reportUploadVariant =
        appJson.optInt(
            SettingsJsonConstants.APP_REPORT_UPLOAD_VARIANT_KEY,
            SettingsJsonConstants.APP_REPORT_UPLOAD_VARIANT_DEFAULT);

    final int nativeReportUploadVariant =
        appJson.optInt(
            SettingsJsonConstants.APP_NATIVE_REPORT_UPLOAD_VARIANT_KEY,
            SettingsJsonConstants.APP_NATIVE_REPORT_UPLOAD_VARIANT_DEFAULT);

    return new AppSettingsData(
        status,
        url,
        reportsUrl,
        ndkReportsUrl,
        bundleId,
        organizationId,
        updateRequired,
        reportUploadVariant,
        nativeReportUploadVariant);
  }

  private static FeaturesSettingsData buildFeaturesSessionDataFrom(JSONObject json) {
    final boolean collectReports =
        json.optBoolean(
            SettingsJsonConstants.FEATURES_COLLECT_REPORTS_KEY,
            SettingsJsonConstants.FEATURES_COLLECT_REPORTS_DEFAULT);

    // TODO: Build support back for "collect logged exceptions"

    return new FeaturesSettingsData(collectReports);
  }

  private static SessionSettingsData defaultSessionData() {
    final int maxCustomExceptionEvents =
        SettingsJsonConstants.SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_DEFAULT;
    final int maxCompleteSessionsCount =
        SettingsJsonConstants.SETTINGS_MAX_COMPLETE_SESSIONS_COUNT_DEFAULT;

    return new SessionSettingsData(maxCustomExceptionEvents, maxCompleteSessionsCount);
  }

  private JSONObject toFabricJson(AppSettingsData appData) throws JSONException {
    final JSONObject json =
        new JSONObject()
            .put(SettingsJsonConstants.FABRIC_BUNDLE_ID, appData.bundleId)
            .put(SettingsJsonConstants.FABRIC_ORGANIZATION_ID, appData.organizationId);

    return json;
  }

  private JSONObject toAppJson(AppSettingsData appData) throws JSONException {
    final JSONObject json =
        new JSONObject()
            .put(SettingsJsonConstants.APP_STATUS_KEY, appData.status)
            .put(SettingsJsonConstants.APP_UPDATE_REQUIRED_KEY, appData.updateRequired)
            .put(SettingsJsonConstants.APP_REPORT_UPLOAD_VARIANT_KEY, appData.reportUploadVariant)
            .put(
                SettingsJsonConstants.APP_NATIVE_REPORT_UPLOAD_VARIANT_KEY,
                appData.nativeReportUploadVariant);

    return json;
  }

  private JSONObject toFeaturesJson(FeaturesSettingsData features) throws JSONException {
    return new JSONObject()
        .put(SettingsJsonConstants.FEATURES_COLLECT_REPORTS_KEY, features.collectReports);
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
}
