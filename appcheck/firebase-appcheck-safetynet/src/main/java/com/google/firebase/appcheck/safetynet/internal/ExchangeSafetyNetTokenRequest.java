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

package com.google.firebase.appcheck.safetynet.internal;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Client-side model of the ExchangeSafetyNetTokenRequest payload from the Firebase App Check Token
 * Exchange API.
 */
public class ExchangeSafetyNetTokenRequest {

  @VisibleForTesting static final String SAFETY_NET_TOKEN_KEY = "safetyNetToken";

  private final String safetyNetToken;

  public ExchangeSafetyNetTokenRequest(@NonNull String safetyNetToken) {
    this.safetyNetToken = safetyNetToken;
  }

  @NonNull
  public String toJsonString() throws JSONException {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(SAFETY_NET_TOKEN_KEY, safetyNetToken);

    return jsonObject.toString();
  }
}
