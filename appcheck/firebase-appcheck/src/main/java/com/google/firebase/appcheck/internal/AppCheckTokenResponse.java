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

package com.google.firebase.appcheck.internal;

import static com.google.android.gms.common.internal.Preconditions.checkNotNull;
import static com.google.android.gms.common.util.Strings.emptyToNull;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Client-side model of the AttestationTokenResponse payload from the Firebase App Check Token
 * Exchange API.
 */
public class AppCheckTokenResponse {

  @VisibleForTesting static final String ATTESTATION_TOKEN_KEY = "attestationToken";
  @VisibleForTesting static final String TIME_TO_LIVE_KEY = "ttl";

  private String attestationToken;
  private String timeToLive;

  @NonNull
  public static AppCheckTokenResponse fromJsonString(@NonNull String jsonString)
      throws JSONException {
    JSONObject jsonObject = new JSONObject(jsonString);
    String attestationToken = emptyToNull(jsonObject.optString(ATTESTATION_TOKEN_KEY));
    String timeToLive = emptyToNull(jsonObject.optString(TIME_TO_LIVE_KEY));
    return new AppCheckTokenResponse(attestationToken, timeToLive);
  }

  private AppCheckTokenResponse(@NonNull String attestationToken, @NonNull String timeToLive) {
    checkNotNull(attestationToken);
    checkNotNull(timeToLive);
    this.attestationToken = attestationToken;
    this.timeToLive = timeToLive;
  }

  @NonNull
  public String getAttestationToken() {
    return attestationToken;
  }

  @NonNull
  public String getTimeToLive() {
    return timeToLive;
  }
}
