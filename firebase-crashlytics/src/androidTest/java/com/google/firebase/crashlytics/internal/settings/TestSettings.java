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

public class TestSettings extends Settings {

  public TestSettings() {
    this(2);
  }

  public TestSettings(int settingsVersion) {
    this(settingsVersion, 0, 0);
  }

  public TestSettings(int settingsVersion, int reportUploadVariant, int nativeReportUploadVariant) {
    this(settingsVersion, reportUploadVariant, nativeReportUploadVariant, false);
  }

  public TestSettings(
      int settingsVersion,
      int reportUploadVariant,
      int nativeReportUploadVarian,
      boolean collectBuildIds) {
    super(
        5,
        buildSettingsData(),
        buildFeatureFlagData(collectBuildIds),
        settingsVersion,
        3600,
        10,
        1.2,
        60);
  }

  private static Settings.FeatureFlagData buildFeatureFlagData(boolean collectBuildIds) {
    return new Settings.FeatureFlagData(true, false, collectBuildIds);
  }

  private static Settings.SessionData buildSettingsData() {
    return new Settings.SessionData(64, 4);
  }
}
