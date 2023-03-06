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
import org.json.JSONException;
import org.json.JSONObject;

class SettingsV3JsonTransform implements SettingsJsonTransform {

  @Override
  public Settings buildFromJson(CurrentTimeProvider currentTimeProvider, JSONObject json)
      throws JSONException {
    int settingsVersion =
        json.optInt(
            SettingsJsonConstants.SETTINGS_VERSION, SettingsJsonConstants.SETTINGS_VERSION_DEFAULT);
    int cacheDuration =
        json.optInt(
            SettingsJsonConstants.CACHE_DURATION_KEY,
            SettingsJsonConstants.SETTINGS_CACHE_DURATION_DEFAULT);
    double onDemandUploadRatePerMinute =
        json.optDouble(
            SettingsJsonConstants.ON_DEMAND_UPLOAD_RATE_PER_MINUTE_KEY,
            SettingsJsonConstants.SETTINGS_ON_DEMAND_UPLOAD_RATE_PER_MINUTE_DEFAULT);
    double onDemandBackoffBase =
        json.optDouble(
            SettingsJsonConstants.ON_DEMAND_BACKOFF_BASE_KEY,
            SettingsJsonConstants.SETTINGS_ON_DEMAND_BACKOFF_BASE_DEFAULT);
    int onDemandBackoffStepDurationSeconds =
        json.optInt(
            SettingsJsonConstants.ON_DEMAND_BACKOFF_STEP_DURATION_SECONDS_KEY,
            SettingsJsonConstants.SETTINGS_ON_DEMAND_BACKOFF_STEP_DURATION_SECONDS_DEFAULT);

    // There's an "app" section that includes deprecated fields. We skip reading those.

    // "session" section is not returned in current Settings values.
    Settings.SessionData sessionData =
        json.has(SettingsJsonConstants.SESSION_KEY)
            ? buildSessionDataFrom(json.getJSONObject(SettingsJsonConstants.SESSION_KEY))
            : buildSessionDataFrom(new JSONObject());
    Settings.FeatureFlagData featureFlagData =
        buildFeatureFlagDataFrom(json.getJSONObject(SettingsJsonConstants.FEATURES_KEY));

    long expiresAtMillis = getExpiresAtFrom(currentTimeProvider, cacheDuration, json);

    return new Settings(
        expiresAtMillis,
        sessionData,
        featureFlagData,
        settingsVersion,
        cacheDuration,
        onDemandUploadRatePerMinute,
        onDemandBackoffBase,
        onDemandBackoffStepDurationSeconds);
  }

  private static Settings.FeatureFlagData buildFeatureFlagDataFrom(JSONObject json) {
    final boolean collectReports =
        json.optBoolean(
            SettingsJsonConstants.FEATURES_COLLECT_REPORTS_KEY,
            SettingsJsonConstants.FEATURES_COLLECT_REPORTS_DEFAULT);

    final boolean collectAnrs =
        json.optBoolean(
            SettingsJsonConstants.FEATURES_COLLECT_ANRS_KEY,
            SettingsJsonConstants.FEATURES_COLLECT_ANRS_DEFAULT);

    final boolean collectBuildIds =
        json.optBoolean(
            SettingsJsonConstants.FEATURES_COLLECT_BUILD_IDS_KEY,
            SettingsJsonConstants.FEATURES_COLLECT_BUILD_IDS_DEFAULT);

    return new Settings.FeatureFlagData(collectReports, collectAnrs, collectBuildIds);
  }

  private static Settings.SessionData buildSessionDataFrom(JSONObject json) {
    final int maxCustomExceptionEvents =
        json.optInt(
            SettingsJsonConstants.SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_KEY,
            SettingsJsonConstants.SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_DEFAULT);
    final int maxCompleteSessionsCount =
        SettingsJsonConstants.SETTINGS_MAX_COMPLETE_SESSIONS_COUNT_DEFAULT;

    return new Settings.SessionData(maxCustomExceptionEvents, maxCompleteSessionsCount);
  }

  private static long getExpiresAtFrom(
      CurrentTimeProvider currentTimeProvider, long cacheDurationSeconds, JSONObject json) {

    long expiresAtMillis;
    if (json.has(SettingsJsonConstants.EXPIRES_AT_KEY)) {
      // If the JSON we receive has an expires_at key, use that value
      expiresAtMillis = json.optLong(SettingsJsonConstants.EXPIRES_AT_KEY);
    } else {
      // If not, make a new one from the current time and the loaded cache duration (in seconds)
      expiresAtMillis = currentTimeProvider.getCurrentTimeMillis() + (cacheDurationSeconds * 1000);
    }
    return expiresAtMillis;
  }
}
