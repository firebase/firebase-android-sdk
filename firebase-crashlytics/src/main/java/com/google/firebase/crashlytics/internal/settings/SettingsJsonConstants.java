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
  static final String APP_KEY = "app";
  static final String SESSION_KEY = "session";
  static final String SETTINGS_VERSION = "settings_version";
  static final String FEATURES_KEY = "features";
  static final String CACHE_DURATION_KEY = "cache_duration";
  static final String FABRIC_KEY = "fabric";

  // Top-level Defaults
  static final int SETTINGS_VERSION_DEFAULT = 0;

  // Feature Switch Keys
  static final String FEATURES_COLLECT_REPORTS_KEY = "collect_reports";

  // Feature Switch Defaults
  static final boolean FEATURES_COLLECT_REPORTS_DEFAULT = true;

  // Fabric JSON Keys
  static final String FABRIC_BUNDLE_ID = "bundle_id";
  static final String FABRIC_ORGANIZATION_ID = "org_id";

  // App JSON Keys
  static final String APP_STATUS_KEY = "status";
  static final String APP_URL_KEY = "url";
  static final String APP_REPORTS_URL_KEY = "reports_url";
  static final String APP_NDK_REPORTS_URL_KEY = "ndk_reports_url";
  static final String APP_UPDATE_REQUIRED_KEY = "update_required";
  static final String APP_REPORT_UPLOAD_VARIANT_KEY = "report_upload_variant";
  static final String APP_NATIVE_REPORT_UPLOAD_VARIANT_KEY = "native_report_upload_variant";

  // App JSON Defaults
  static final boolean APP_UPDATE_REQUIRED_DEFAULT = false;
  static final int APP_REPORT_UPLOAD_VARIANT_DEFAULT = 0;
  static final int APP_NATIVE_REPORT_UPLOAD_VARIANT_DEFAULT = 0;

  // Settings JSON Keys
  static final String SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_KEY = "max_custom_exception_events";
  static final String SETTINGS_MAX_COMPLETE_SESSIONS_COUNT_KEY = "max_complete_sessions_count";

  // Settings JSON Defaults
  static final int SETTINGS_CACHE_DURATION_DEFAULT = 3600;
  static final int SETTINGS_MAX_CUSTOM_EXCEPTION_EVENTS_DEFAULT = 8;
  static final int SETTINGS_MAX_COMPLETE_SESSIONS_COUNT_DEFAULT = 4;
}
