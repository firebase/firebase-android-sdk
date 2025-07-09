package com.google.firebase.appcheck.recaptchaenterprise.internal;

import static org.junit.Assert.assertThrows;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@Link SiteKey}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SiteKeyTest {
  private static final String VALUE = "siteKey";

  @Test
  public void testConstructor_nullValue_expectThrows() {
    assertThrows(NullPointerException.class, () -> new SiteKey(null));
  }

  @Test
  public void testConstructor_nonNullValue_succeeds() {
    SiteKey siteKey = new SiteKey(VALUE);
    Truth.assertThat(siteKey.value()).isEqualTo(VALUE);
  }
}
