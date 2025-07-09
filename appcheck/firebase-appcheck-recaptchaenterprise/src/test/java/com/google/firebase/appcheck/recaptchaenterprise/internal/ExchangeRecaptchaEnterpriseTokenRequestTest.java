package com.google.firebase.appcheck.recaptchaenterprise.internal;

import static com.google.common.truth.Truth.assertThat;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link ExchangeRecaptchaEnterpriseTokenRequest}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
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
