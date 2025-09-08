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

import static com.google.common.truth.Truth.assertThat;

import org.json.JSONObject;
import org.junit.Test;

/** Tests for {@link ExchangeRecaptchaEnterpriseTokenRequest}. */
public class ExchangeRecaptchaEnterpriseTokenRequestTest {
  private static final String RECAPTCHA_ENTERPRISE_TOKEN = "recaptchaEnterpriseToken";

  @Test
  public void toJsonString_expectSerialized() throws Exception {
    ExchangeRecaptchaEnterpriseTokenRequest exchangeRecaptchaEnterpriseTokenRequest =
        new ExchangeRecaptchaEnterpriseTokenRequest(RECAPTCHA_ENTERPRISE_TOKEN);

    String jsonString = exchangeRecaptchaEnterpriseTokenRequest.toJsonString();
    JSONObject jsonObject = new JSONObject(jsonString);

    assertThat(
            jsonObject.getString(
                ExchangeRecaptchaEnterpriseTokenRequest.RECAPTCHA_ENTERPRISE_TOKEN_KEY))
        .isEqualTo(RECAPTCHA_ENTERPRISE_TOKEN);
  }
}
