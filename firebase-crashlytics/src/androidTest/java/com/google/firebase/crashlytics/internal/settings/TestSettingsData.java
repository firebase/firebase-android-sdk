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

import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.FeaturesSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SessionSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;

public class TestSettingsData extends SettingsData {

  public TestSettingsData() {
    this(2, 0, 0);
  }

  public TestSettingsData(
      int settingsVersion, int reportUploadVariant, int nativeReportUploadVariant) {
    super(
        5,
        buildAppData(reportUploadVariant, nativeReportUploadVariant),
        buildSettingsData(),
        buildFeaturesData(),
        settingsVersion,
        3600);
  }

  private static FeaturesSettingsData buildFeaturesData() {
    return new FeaturesSettingsData(true);
  }

  private static SessionSettingsData buildSettingsData() {
    return new SessionSettingsData(64, 4);
  }

  private static AppSettingsData buildAppData(
      int reportUploadVariant, int nativeReportUploadVariant) {
    return new AppSettingsData(
        AppSettingsData.STATUS_ACTIVATED,
        "http://localhost",
        "http://localhost",
        "http://localhost",
        "testBundleId",
        "testOrganizationId",
        false,
        reportUploadVariant,
        nativeReportUploadVariant);
  }
}
