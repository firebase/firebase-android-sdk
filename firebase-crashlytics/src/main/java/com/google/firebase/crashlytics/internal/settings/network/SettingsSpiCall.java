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

package com.google.firebase.crashlytics.internal.settings.network;

import com.google.firebase.crashlytics.internal.settings.model.SettingsRequest;
import org.json.JSONObject;

/** Internal interface representing the SPI REST call to get settings from the server. */
public interface SettingsSpiCall {
  /**
   * @param requestData {@link SettingsRequest} data to be sent to the server
   * @return the {@link JSONObject} created from JSON returned from the server, or <code>null</code>
   *     if there was a problem getting the settings JSON from the server.
   */
  JSONObject invoke(SettingsRequest requestData, boolean dataCollectionToken);
}
