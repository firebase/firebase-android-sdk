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
