// Copyright 2022 Google LLC
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

package com.google.firebase.appcheck.playintegrity.internal;

import androidx.annotation.NonNull;
import org.json.JSONObject;

/**
 * Client-side model of the GeneratePlayIntegrityChallengeRequest payload from the Firebase App
 * Check Token Exchange API.
 */
class GeneratePlayIntegrityChallengeRequest {

  public GeneratePlayIntegrityChallengeRequest() {}

  @NonNull
  public String toJsonString() {
    JSONObject jsonObject = new JSONObject();

    // GeneratePlayIntegrityChallenge takes an empty POST body since the app ID is in the URL.
    return jsonObject.toString();
  }
}
