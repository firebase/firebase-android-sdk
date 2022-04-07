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

import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Client-side model of the GeneratePlayIntegrityChallengeResponse payload from the Firebase App
 * Check Token Exchange API.
 */
public class GeneratePlayIntegrityChallengeResponse {

  @VisibleForTesting static final String CHALLENGE_KEY = "challenge";
  @VisibleForTesting static final String TIME_TO_LIVE_KEY = "ttl";

  private String challenge;
  private String timeToLive;

  @NonNull
  public static GeneratePlayIntegrityChallengeResponse fromJsonString(@NonNull String jsonString)
      throws JSONException {
    JSONObject jsonObject = new JSONObject(jsonString);
    String challenge = jsonObject.optString(CHALLENGE_KEY, null);
    String timeToLive = jsonObject.optString(TIME_TO_LIVE_KEY, null);
    return new GeneratePlayIntegrityChallengeResponse(challenge, timeToLive);
  }

  private GeneratePlayIntegrityChallengeResponse(
      @NonNull String challenge, @NonNull String timeToLive) {
    checkNotNull(challenge);
    checkNotNull(timeToLive);
    this.challenge = challenge;
    this.timeToLive = timeToLive;
  }

  @NonNull
  public String getChallenge() {
    return challenge;
  }

  @NonNull
  public String getTimeToLive() {
    return timeToLive;
  }
}
