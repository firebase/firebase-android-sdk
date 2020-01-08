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
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import org.json.JSONException;
import org.json.JSONObject;

/** Parses settings JSON into SettingsData honoring the appropriate settings_version. */
public class SettingsJsonParser {

  private final CurrentTimeProvider currentTimeProvider;

  SettingsJsonParser(CurrentTimeProvider currentTimeProvider) {
    this.currentTimeProvider = currentTimeProvider;
  }

  public SettingsData parseSettingsJson(JSONObject settingsJson) throws JSONException {
    final int version = settingsJson.getInt(SettingsJsonConstants.SETTINGS_VERSION);
    final SettingsJsonTransform jsonTransform = getJsonTransformForVersion(version);
    return jsonTransform.buildFromJson(currentTimeProvider, settingsJson);
  }

  private static SettingsJsonTransform getJsonTransformForVersion(int settingsVersion) {
    switch (settingsVersion) {
      case 3:
        return new SettingsV3JsonTransform();
      default:
        return new DefaultSettingsJsonTransform();
    }
  }
}
