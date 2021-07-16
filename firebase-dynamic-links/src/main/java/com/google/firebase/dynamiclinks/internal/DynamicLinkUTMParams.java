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

import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * Class to extract UTM parameters from firebase dynamic link.
 *
 * @hide
 */
public class DynamicLinkUTMParams {

  @VisibleForTesting public static final String KEY_CAMPAIGN_BUNDLE = "_cmp";
  @VisibleForTesting public static final String KEY_SCION_DATA_BUNDLE = "scionData";
  @VisibleForTesting public static final String KEY_MEDIUM = "medium";
  @VisibleForTesting public static final String KEY_SOURCE = "source";
  @VisibleForTesting public static final String KEY_CAMPAIGN = "campaign";

  /** Key to retrieve utm_medium from utm params bundle returned by {@link #asBundle()} */
  public static final String KEY_UTM_MEDIUM = "utm_medium";
  /** Key to retrieve utm_source from utm params bundle returned by {@link #asBundle()} */
  public static final String KEY_UTM_SOURCE = "utm_source";
  /** Key to retrieve utm_campaign from utm params bundle returned by {@link #asBundle()} */
  public static final String KEY_UTM_CAMPAIGN = "utm_campaign";

  private final DynamicLinkData dynamicLinkData;
  @NonNull private final Bundle utmParamsBundle;

  public DynamicLinkUTMParams(DynamicLinkData dynamicLinkData) {
    this.dynamicLinkData = dynamicLinkData;
    this.utmParamsBundle = initUTMParamsBundle(dynamicLinkData);
  }

  @NonNull
  public Bundle asBundle() {
    return new Bundle(utmParamsBundle);
  }

  @NonNull
  private static Bundle initUTMParamsBundle(DynamicLinkData dynamicLinkData) {
    Bundle bundle = new Bundle();
    if (dynamicLinkData == null || dynamicLinkData.getExtensionBundle() == null) {
      return bundle;
    }

    Bundle scionBundle = dynamicLinkData.getExtensionBundle().getBundle(KEY_SCION_DATA_BUNDLE);

    if (scionBundle == null) {
      return bundle;
    }

    Bundle campaignBundle = scionBundle.getBundle(KEY_CAMPAIGN_BUNDLE);
    if (campaignBundle == null) {
      return bundle;
    }

    checkAndAdd(KEY_MEDIUM, KEY_UTM_MEDIUM, campaignBundle, bundle);
    checkAndAdd(KEY_SOURCE, KEY_UTM_SOURCE, campaignBundle, bundle);
    checkAndAdd(KEY_CAMPAIGN, KEY_UTM_CAMPAIGN, campaignBundle, bundle);

    return bundle;
  }

  /*
   * Checks and adds the value from source bundle to the destination bundle based on the source
   *  key and destination key.
   */
  private static void checkAndAdd(
      @NonNull String sourceKey,
      @NonNull String destKey,
      @NonNull Bundle source,
      @NonNull Bundle dest) {
    String value = source.getString(sourceKey);
    if (!TextUtils.isEmpty(value)) {
      dest.putString(destKey, value);
    }
  }
}
