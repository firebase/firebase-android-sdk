package com.google.firebase.appcheck.recaptchaenterprise;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link RecaptchaEnterpriseAppCheckProviderFactory}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RecaptchaEnterpriseAppCheckProviderFactoryTest {
  static final String SITE_KEY = "siteKey";

  @Test
  public void testGetInstance_callTwice_sameInstance() {
    RecaptchaEnterpriseAppCheckProviderFactory firstInstance =
        RecaptchaEnterpriseAppCheckProviderFactory.getInstance(SITE_KEY);
    RecaptchaEnterpriseAppCheckProviderFactory secondInstance =
        RecaptchaEnterpriseAppCheckProviderFactory.getInstance(SITE_KEY);

    assertThat(firstInstance).isEqualTo(secondInstance);
  }
}
