// Copyright 2025 Google LLC
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

package com.google.firebase.appcheck.recaptchaenterprise.internal;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Client-side model of the ExchangeRecaptchaEnterpriseTokenRequest payload from the Firebase App
 * Check Token Exchange API.
 */
public class ExchangeRecaptchaEnterpriseTokenRequest {

  @VisibleForTesting
  static final String RECAPTCHA_ENTERPRISE_TOKEN_KEY = "recaptchaEnterpriseToken";

  private final String recaptchaEnterpriseToken;

  public ExchangeRecaptchaEnterpriseTokenRequest(@NonNull String recaptchaEnterpriseToken) {
    this.recaptchaEnterpriseToken = recaptchaEnterpriseToken;
  }

  @NonNull
  public String toJsonString() throws JSONException {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(RECAPTCHA_ENTERPRISE_TOKEN_KEY, recaptchaEnterpriseToken);

    return jsonObject.toString();
  }
}
