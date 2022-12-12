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

class SettingsJsonConstants {
  // Top-level JSON Keys
  static final String EXPIRES_AT_KEY = "expires_at";
  static final String SESSION_KEY = "session";
  static final String SETTINGS_VERSION = "settings_version";
  static final String FEATURES_KEY = "features";
  static final String CACHE_DURATION_KEY = "cache_duration";
  static final String ON_DEMAND_UPLOAD_RATE_PER_MINUTE_KEY = "on_demand_upload_rate_per_minute";
  static final String ON_DEMAND_BACKOFF_BASE_KEY = "on_demand_backoff_base";
  static final String ON_DEMAND_BACKOFF_STEP_DURATION_SECONDS_KEY =
      "on_demand_backoff_step_duration_seconds";

  // Top-level Defaults
  static final int SETTINGS_VERSION_DEFAULT = 0;

  // Feature Switch Keys
  static final String FEATURES_COLLECT_REPORTS_KEY = "collect_reports";
  static final String FEATURES_COLLECT_ANRS_KEY = "collect_anrs";
  static final String FEATURES_COLLECT_BUILD_IDS_KEY = "collect_build_ids";

  // Feature Switch Defaults
  static final boolean FEATURES_COLLECT_REPORTS_DEFAULT = true;
  static final boolean FEATURES_COLLECT_ANRS_DEFAULT = false;
  static final boolean FEATURES_COLLECT_BUILD_IDS_DEFAULT = false;

  // App JSON Keys
  static final String APP_STATUS_KEY = "status";

  // Settings JSON Keys
  static final String SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_KEY = "max_custom_exception_events";
  static final String SETTINGS_MAX_COMPLETE_SESSIONS_COUNT_KEY = "max_complete_sessions_count";

  // Settings JSON Defaults
  static final int SETTINGS_CACHE_DURATION_DEFAULT = 3600;
  static final int SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_DEFAULT = 8;
  static final int SETTINGS_MAX_COMPLETE_SESSIONS_COUNT_DEFAULT = 4;
  static final double SETTINGS_ON_DEMAND_UPLOAD_RATE_PER_MINUTE_DEFAULT = 10;
  static final double SETTINGS_ON_DEMAND_BACKOFF_BASE_DEFAULT = 1.2;
  static final int SETTINGS_ON_DEMAND_BACKOFF_STEP_DURATION_SECONDS_DEFAULT = 60;
}
