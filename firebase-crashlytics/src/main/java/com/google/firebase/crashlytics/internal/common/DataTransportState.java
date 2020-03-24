// Copyright 2020 Google LLC
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

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;

public enum DataTransportState {
  NONE,
  JAVA_ONLY,
  ALL;

  // Used to determine whether to upload reports through the legacy reports endpoint
  static final int REPORT_UPLOAD_VARIANT_LEGACY = 1;
  // Used to determine whether to upload reports through the new DataTransport API.
  static final int REPORT_UPLOAD_VARIANT_DATATRANSPORT = 2;

  @NonNull
  static DataTransportState getState(boolean dataTransportState, boolean dataTransportNativeState) {
    if (!dataTransportState) {
      return NONE;
    }
    if (!dataTransportNativeState) {
      return JAVA_ONLY;
    }
    return ALL;
  }

  @NonNull
  static DataTransportState getState(@NonNull AppSettingsData appSettingsData) {
    final boolean dataTransportState =
        appSettingsData.reportUploadVariant == REPORT_UPLOAD_VARIANT_DATATRANSPORT;
    final boolean dataTransportNativeState =
        appSettingsData.nativeReportUploadVariant == REPORT_UPLOAD_VARIANT_DATATRANSPORT;
    return getState(dataTransportState, dataTransportNativeState);
  }
}
