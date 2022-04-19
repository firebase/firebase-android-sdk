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
import com.google.firebase.FirebaseException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Client-side model of the AppCheckToken payload from the Firebase App Check Token Exchange API.
 */
public class AppCheckTokenResponse {

  @VisibleForTesting static final String TOKEN_KEY = "token";
  @VisibleForTesting static final String TIME_TO_LIVE_KEY = "ttl";

  private String token;
  private String timeToLive;

  @NonNull
  public static AppCheckTokenResponse fromJsonString(@NonNull String jsonString)
      throws FirebaseException, JSONException {
    JSONObject jsonObject = new JSONObject(jsonString);
    String token = emptyToNull(jsonObject.optString(TOKEN_KEY));
    String timeToLive = emptyToNull(jsonObject.optString(TIME_TO_LIVE_KEY));
    if (token == null || timeToLive == null) {
      throw new FirebaseException("Unexpected server response.");
    }
    return new AppCheckTokenResponse(token, timeToLive);
  }

  private AppCheckTokenResponse(@NonNull String token, @NonNull String timeToLive) {
    checkNotNull(token);
    checkNotNull(timeToLive);
    this.token = token;
    this.timeToLive = timeToLive;
  }

  @NonNull
  public String getToken() {
    return token;
  }

  @NonNull
  public String getTimeToLive() {
    return timeToLive;
  }
}
