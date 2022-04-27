// Copyright 2021 Google LLC
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

package com.google.firebase.dynamiclinks.internal;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DynamicLinkUTMParamsTest {

  @Test
  public void testAsBundle_WithNullExtensions() {
    DynamicLinkUTMParams dynamicLinkUTMParams = new DynamicLinkUTMParams(getDynamicLinkData(null));
    assertNonNullEmptyBundle(dynamicLinkUTMParams.asBundle());
  }

  @Test
  public void testAsBundle_WithExtensionsButNullScionBundle() {
    Bundle extensions = getExtensions();
    extensions.remove(DynamicLinkUTMParams.KEY_SCION_DATA_BUNDLE);
    DynamicLinkUTMParams dynamicLinkUTMParams =
        new DynamicLinkUTMParams(getDynamicLinkData(extensions));
    assertNonNullEmptyBundle(dynamicLinkUTMParams.asBundle());
  }

  @Test
  public void testAsBundle_WithExtensionsButNullCampaignBundle() {
    Bundle extensions = getExtensions();
    extensions
        .getBundle(DynamicLinkUTMParams.KEY_SCION_DATA_BUNDLE)
        .remove(DynamicLinkUTMParams.KEY_CAMPAIGN_BUNDLE);
    DynamicLinkUTMParams dynamicLinkUTMParams =
        new DynamicLinkUTMParams(getDynamicLinkData(extensions));
    assertNonNullEmptyBundle(dynamicLinkUTMParams.asBundle());
  }

  @Test
  public void testAsBundle_WithExtensionsButNullUtmParams() {
    DynamicLinkUTMParams dynamicLinkUTMParams =
        new DynamicLinkUTMParams(getDynamicLinkData(getExtensions()));
    assertNonNullEmptyBundle(dynamicLinkUTMParams.asBundle());
  }

  @Test
  public void testAsBundle_WithExtensionsButEmptyUtmParams() {
    DynamicLinkUTMParams dynamicLinkUTMParams =
        new DynamicLinkUTMParams(getDynamicLinkData(getExtensions("", "", "")));
    assertNonNullEmptyBundle(dynamicLinkUTMParams.asBundle());
  }

  @Test
  public void testAsBundle_WithExtensionsContainingUtmParams() {
    DynamicLinkUTMParams dynamicLinkUTMParams =
        new DynamicLinkUTMParams(getDynamicLinkData(getExtensions("m", "s", "c")));
    // Non empty check
    assertFalse(isEmptyBundle(dynamicLinkUTMParams.asBundle()));

    // Comparing Utm params
    assertEquals(
        dynamicLinkUTMParams.asBundle().getString(DynamicLinkUTMParams.KEY_UTM_MEDIUM), "m");
    assertEquals(
        dynamicLinkUTMParams.asBundle().getString(DynamicLinkUTMParams.KEY_UTM_SOURCE), "s");
    assertEquals(
        dynamicLinkUTMParams.asBundle().getString(DynamicLinkUTMParams.KEY_UTM_CAMPAIGN), "c");
  }

  private void assertNonNullEmptyBundle(Bundle bundle) {
    assertNotNull(bundle);
    assertTrue(isEmptyBundle(bundle));
  }

  private boolean isEmptyBundle(Bundle bundle) {
    return bundle.size() == 0;
  }

  private Bundle getExtensions() {
    return getExtensions(null, null, null);
  }

  private Bundle getExtensions(String medium, String source, String campaign) {
    Bundle bundle = new Bundle();
    Bundle scionBundle = new Bundle();
    Bundle campaignBundle = new Bundle();

    bundle.putBundle(DynamicLinkUTMParams.KEY_SCION_DATA_BUNDLE, scionBundle);
    scionBundle.putBundle(DynamicLinkUTMParams.KEY_CAMPAIGN_BUNDLE, campaignBundle);

    campaignBundle.putString(DynamicLinkUTMParams.KEY_MEDIUM, medium);
    campaignBundle.putString(DynamicLinkUTMParams.KEY_SOURCE, source);
    campaignBundle.putString(DynamicLinkUTMParams.KEY_CAMPAIGN, campaign);

    return bundle;
  }

  private DynamicLinkData getDynamicLinkData(Bundle extensions) {
    return new DynamicLinkData(null, null, 0, 0L, extensions, null);
  }
}
