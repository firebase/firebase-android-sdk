// Copyright 2018 Google LLC
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

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;

/** */
@SafeParcelable.Class(creator = "DynamicLinkDataCreator")
public class DynamicLinkData extends AbstractSafeParcelable {

  @SafeParcelable.Field(id = 1, getter = "getDynamicLink")
  private String dynamicLink;

  @SafeParcelable.Field(id = 2, getter = "getDeepLink")
  private String deepLink;

  @SafeParcelable.Field(id = 3, getter = "getMinVersion")
  private int minVersion;

  @SafeParcelable.Field(id = 4, getter = "getClickTimestamp")
  private long clickTimestamp = 0L;

  @SafeParcelable.Field(id = 5, getter = "getExtensionBundle")
  private Bundle extensionBundle = null;

  @SafeParcelable.Field(id = 6, getter = "getRedirectUrl")
  private Uri redirectUrl;

  public String getDynamicLink() {
    return dynamicLink;
  }

  public void setDynamicLink(String dynamicLink) {
    this.dynamicLink = dynamicLink;
  }

  public String getDeepLink() {
    return deepLink;
  }

  public void setDeepLink(String deepLink) {
    this.deepLink = deepLink;
  }

  public int getMinVersion() {
    return minVersion;
  }

  public void setMinVersion(int minVersion) {
    this.minVersion = minVersion;
  }

  public long getClickTimestamp() {
    return clickTimestamp;
  }

  public void setClickTimestamp(long timestamp) {
    clickTimestamp = timestamp;
  }

  public Bundle getExtensionBundle() {
    return (extensionBundle == null) ? new Bundle() : extensionBundle;
  }

  public void setRedirectUrl(Uri redirectUrl) {
    this.redirectUrl = redirectUrl;
  }

  public Uri getRedirectUrl() {
    return redirectUrl;
  }

  /**
   * Replace the existing extension data with the bundle. Clients should use {@link
   * #getExtensionBundle()} to retrieve the current values, add new values, the replace the bundle
   * with the updated values.
   *
   * @param bundle
   */
  public void setExtensionData(Bundle bundle) {
    extensionBundle = bundle;
  }

  public static final Creator<DynamicLinkData> CREATOR = new DynamicLinkDataCreator();

  @SafeParcelable.Constructor
  public DynamicLinkData(
      @Param(id = 1) String dynamicLink,
      @Param(id = 2) String deepLink,
      @Param(id = 3) int minVersion,
      @Param(id = 4) long clickTimestamp,
      @Param(id = 5) Bundle extensions,
      @Param(id = 6) Uri redirectUrl) {
    this.dynamicLink = dynamicLink;
    this.deepLink = deepLink;
    this.minVersion = minVersion;
    this.clickTimestamp = clickTimestamp;
    extensionBundle = extensions;
    this.redirectUrl = redirectUrl;
  }

  @SuppressWarnings("static-access")
  @Override
  public void writeToParcel(Parcel dest, int flags) {
    DynamicLinkDataCreator.writeToParcel(this, dest, flags);
  }
}
