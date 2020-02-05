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

/**
 * Internal interface defining bi-direction conversion to-from settings JSON and {@link
 * SettingsData} a model object.
 */
interface SettingsJsonTransform {
  /**
   * Transforms the input JSON into the corresponding {@link SettingsData} model object. Should
   * throw an exception rather than returning <code>null</code> in the event of a problem.
   *
   * @param currentTimeProvider {@link CurrentTimeProvider} to be used in filling in the {@link
   *     SettingsData#expiresAtMillis} value
   * @param json {@link JSONObject} to be translated
   * @return {@link SettingsData} representing the translated JSON data
   * @throws JSONException if an exception occurs while handling the JSON
   */
  SettingsData buildFromJson(CurrentTimeProvider currentTimeProvider, JSONObject json)
      throws JSONException;

  /**
   * Transforms the provided {@link SettingsData} model object to its JSON representation. Should
   * throw an exception rather than returning <code>null</code> in the event of a problem.
   *
   * @param settingsData {@link SettingsData} to translate
   * @return {@link JSONObject} representing the settings data
   * @throws JSONException if a problem occurs while translating the settings data.
   */
  JSONObject toJson(SettingsData settingsData) throws JSONException;
}
