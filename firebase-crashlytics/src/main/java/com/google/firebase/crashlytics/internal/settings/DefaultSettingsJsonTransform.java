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
import org.json.JSONObject;

/**
 * Default implementation of a SettingsJsonTransform that ignores the JSON and populates all fields
 * with default values.
 */
class DefaultSettingsJsonTransform implements SettingsJsonTransform {

  @Override
  public Settings buildFromJson(CurrentTimeProvider currentTimeProvider, JSONObject json) {

    return defaultSettings(currentTimeProvider);
  }

  /** Creates a new Settings with reasonable default values. */
  static Settings defaultSettings(CurrentTimeProvider currentTimeProvider) {
    int settingsVersion = SettingsJsonConstants.SETTINGS_VERSION_DEFAULT;
    int cacheDurationSeconds = SettingsJsonConstants.SETTINGS_CACHE_DURATION_DEFAULT;
    double onDemandUploadRatePerMinute =
        SettingsJsonConstants.SETTINGS_ON_DEMAND_UPLOAD_RATE_PER_MINUTE_DEFAULT;
    double onDemandBackoffBase = SettingsJsonConstants.SETTINGS_ON_DEMAND_BACKOFF_BASE_DEFAULT;
    int onDemandBackoffStepDurationSeconds =
        SettingsJsonConstants.SETTINGS_ON_DEMAND_BACKOFF_STEP_DURATION_SECONDS_DEFAULT;

    final Settings.SessionData sessionData =
        new Settings.SessionData(
            SettingsJsonConstants.SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_DEFAULT,
            SettingsJsonConstants.SETTINGS_MAX_COMPLETE_SESSIONS_COUNT_DEFAULT);
    final Settings.FeatureFlagData featureFlagData =
        new Settings.FeatureFlagData(
            SettingsJsonConstants.FEATURES_COLLECT_REPORTS_DEFAULT,
            SettingsJsonConstants.FEATURES_COLLECT_ANRS_DEFAULT,
            SettingsJsonConstants.FEATURES_COLLECT_BUILD_IDS_DEFAULT);

    long expiresAtMillis =
        currentTimeProvider.getCurrentTimeMillis() + (cacheDurationSeconds * 1000);

    return new Settings(
        expiresAtMillis,
        sessionData,
        featureFlagData,
        settingsVersion,
        cacheDurationSeconds,
        onDemandUploadRatePerMinute,
        onDemandBackoffBase,
        onDemandBackoffStepDurationSeconds);
  }
}
