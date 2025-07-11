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
  static final String SITE_KEY_1 = "siteKey1";
  static final String SITE_KEY_2 = "siteKey2";

  @Test
  public void testGetInstance_callTwiceSameSiteKey_sameInstance() {
    RecaptchaEnterpriseAppCheckProviderFactory firstInstance =
        RecaptchaEnterpriseAppCheckProviderFactory.getInstance(SITE_KEY_1);
    RecaptchaEnterpriseAppCheckProviderFactory secondInstance =
        RecaptchaEnterpriseAppCheckProviderFactory.getInstance(SITE_KEY_1);

    assertThat(firstInstance).isEqualTo(secondInstance);
  }

  @Test
  public void testGetInstance_callTwiceDifferentSiteKey_differentInstance() {
    RecaptchaEnterpriseAppCheckProviderFactory firstInstance =
        RecaptchaEnterpriseAppCheckProviderFactory.getInstance(SITE_KEY_1);
    RecaptchaEnterpriseAppCheckProviderFactory secondInstance =
        RecaptchaEnterpriseAppCheckProviderFactory.getInstance(SITE_KEY_2);

    assertThat(firstInstance).isNotEqualTo(secondInstance);
  }
}
