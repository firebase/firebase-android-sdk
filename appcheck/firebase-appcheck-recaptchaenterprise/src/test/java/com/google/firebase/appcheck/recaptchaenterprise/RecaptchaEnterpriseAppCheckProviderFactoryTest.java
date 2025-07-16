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
