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

import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CurrentTimeProvider;
import com.google.firebase.crashlytics.internal.settings.Settings.Profiling;
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
    double onDemandUploadRatePerMinute = getOnDemandUploadRatePerMinute(json);
    double onDemandBackoffBase =
        getNonNegativeDouble(
            json,
            SettingsJsonConstants.ON_DEMAND_BACKOFF_BASE_KEY,
            SettingsJsonConstants.SETTINGS_ON_DEMAND_BACKOFF_BASE_DEFAULT);
    // The step duration is a divisor when calculating the backoff, so it must be positive.
    int onDemandBackoffStepDurationSeconds =
        getPositiveInt(
            json,
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

    Profiling profiling =
        json.has(SettingsJsonConstants.PROFILING_KEY)
            ? buildProfilingDataFrom(json.getJSONObject(SettingsJsonConstants.PROFILING_KEY))
            : buildProfilingDataFrom(new JSONObject());

    return new Settings(
        expiresAtMillis,
        sessionData,
        featureFlagData,
        settingsVersion,
        cacheDuration,
        onDemandUploadRatePerMinute,
        onDemandBackoffBase,
        onDemandBackoffStepDurationSeconds,
        profiling);
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

  private static Settings.Profiling buildProfilingDataFrom(JSONObject json) {
    boolean heapDumpCollectionEnabled =
        json.optBoolean(
            SettingsJsonConstants.PROFILING_HEAP_DUMP_COLLECTION_ENABLED_KEY,
            SettingsJsonConstants.PROFILING_HEAP_DUMP_COLLECTION_ENABLED_DEFAULT);
    return new Profiling(heapDumpCollectionEnabled);
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

  // Settings are downloaded from the server, cached on disk, and read at every launch before
  // Crashlytics can report crashes. Out-of-range values are ignored in favor of the defaults so
  // that a malformed payload can't crash the app or disable Crashlytics. The optDouble and optInt
  // getters already fall back to the defaults for missing and non-numeric values, but optDouble
  // also parses strings such as "NaN" and "Infinity".

  /**
   * Returns the on-demand upload rate, clamped to the maximum. A rate of 0 or a fractional rate is
   * valid. With a rate of 0, every on-demand report is dropped.
   */
  private static double getOnDemandUploadRatePerMinute(JSONObject json) {
    double ratePerMinute =
        getNonNegativeDouble(
            json,
            SettingsJsonConstants.ON_DEMAND_UPLOAD_RATE_PER_MINUTE_KEY,
            SettingsJsonConstants.SETTINGS_ON_DEMAND_UPLOAD_RATE_PER_MINUTE_DEFAULT);
    if (ratePerMinute > SettingsJsonConstants.SETTINGS_ON_DEMAND_UPLOAD_RATE_PER_MINUTE_MAX) {
      Logger.getLogger()
          .d(
              "Clamping "
                  + SettingsJsonConstants.ON_DEMAND_UPLOAD_RATE_PER_MINUTE_KEY
                  + " from "
                  + ratePerMinute
                  + " to "
                  + SettingsJsonConstants.SETTINGS_ON_DEMAND_UPLOAD_RATE_PER_MINUTE_MAX);
      return SettingsJsonConstants.SETTINGS_ON_DEMAND_UPLOAD_RATE_PER_MINUTE_MAX;
    }
    return ratePerMinute;
  }

  /** Returns the value for the key if it is a finite, non-negative number, or the default value. */
  private static double getNonNegativeDouble(JSONObject json, String key, double defaultValue) {
    double value = json.optDouble(key, defaultValue);
    if (!Double.isFinite(value) || value < 0) {
      logIgnoredValue(key, value);
      return defaultValue;
    }
    return value;
  }

  /** Returns the value for the key if it is a positive number, or the default value. */
  private static int getPositiveInt(JSONObject json, String key, int defaultValue) {
    int value = json.optInt(key, defaultValue);
    if (value <= 0) {
      logIgnoredValue(key, value);
      return defaultValue;
    }
    return value;
  }

  private static void logIgnoredValue(String key, Object value) {
    Logger.getLogger().d("Ignoring invalid value for " + key + ": " + value);
  }
}
