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

/**
 * Internal interface defining bi-direction conversion to-from settings JSON and {@link Settings} a
 * model object.
 */
interface SettingsJsonTransform {
  /**
   * Transforms the input JSON into the corresponding {@link Settings} model object. Should throw an
   * exception rather than returning <code>null</code> in the event of a problem.
   *
   * @param currentTimeProvider {@link CurrentTimeProvider} to be used in filling in the {@link
   *     Settings#expiresAtMillis} value
   * @param json {@link JSONObject} to be translated
   * @return {@link Settings} representing the translated JSON data
   * @throws JSONException if an exception occurs while handling the JSON
   */
  Settings buildFromJson(CurrentTimeProvider currentTimeProvider, JSONObject json)
      throws JSONException;
}
