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

package com.google.firebase.dynamiclinks;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.util.DefaultClock;
import com.google.firebase.dynamiclinks.internal.DynamicLinkData;

/** Provides accessor methods to dynamic links data. */
public class PendingDynamicLinkData {

  private final DynamicLinkData dynamicLinkData;

  /**
   * Create a dynamic link from parameters.
   *
   * @hide
   */
  @VisibleForTesting
  public PendingDynamicLinkData(DynamicLinkData dynamicLinkData) {
    if (dynamicLinkData == null) {
      this.dynamicLinkData = null;
      return;
    }
    if (dynamicLinkData.getClickTimestamp() == 0L) {
      long now = DefaultClock.getInstance().currentTimeMillis();
      dynamicLinkData.setClickTimestamp(now);
    }
    this.dynamicLinkData = dynamicLinkData;
  }

  /**
   * Create a PendingDynamicLinkData which can be used for testing.
   *
   * @param deepLink dynamic link deep link, can be null.
   * @param minVersion app minimum version. 0 if no minimum version required.
   * @param clickTimestamp timestamp of when the dynamic link was clicked. If zero, will be current
   *     time.
   */
  protected PendingDynamicLinkData(
      @Nullable String deepLink, int minVersion, long clickTimestamp, @Nullable Uri redirectUrl) {
    dynamicLinkData =
        new DynamicLinkData(null, deepLink, minVersion, clickTimestamp, null, redirectUrl);
  }

  /**
   * Return the {@link Bundle} so that 1P dynamic links extensions can access extension data. The
   * data is stored bundle with keys defined by the extension. The bundle is shared with all
   * extensions, so the keys should be unique using the package name of the extension to define the
   * namespace.
   *
   * @return A bundle will all extension data.
   * @hide
   */
  @Nullable
  public Bundle getExtensions() {
    if (dynamicLinkData == null) {
      return new Bundle();
    }
    return dynamicLinkData.getExtensionBundle();
  }

  /**
   * Return the link parameter of the dynamic link.
   *
   * <p>This link will be set as data in the launch Intent, see {@link Intent#setData(Uri)}, which
   * will match {@link android.content.IntentFilter} to deep link into the app.
   *
   * @return The deep link if it exists, null otherwise.
   */
  @Nullable
  public Uri getLink() {
    if (dynamicLinkData == null) {
      return null;
    }
    String deepLink = dynamicLinkData.getDeepLink();
    if (deepLink != null) {
      return Uri.parse(deepLink);
    }
    return null;
  }

  /**
   * The minimum app version requested to process the dynamic link that can be compared directly
   * with {@link android.content.pm.PackageInfo#versionCode}. If the minimum version code is higher
   * than the installed app version code, the app can upgrade using {@link
   * #getUpdateAppIntent(Context)}.
   *
   * @return minimum version code set on the dynamic link, or 0 if not specified.
   */
  public int getMinimumAppVersion() {
    if (dynamicLinkData == null) {
      return 0;
    }
    return dynamicLinkData.getMinVersion();
  }

  /**
   * The time that the user clicked on the dynamic link. This can be used to determine the amount of
   * time that has passed since the user selected the link until the app is launched.
   *
   * @return The number of milliseconds that have elapsed since January 1, 1970.
   */
  public long getClickTimestamp() {
    if (dynamicLinkData == null) {
      return 0L;
    }
    return dynamicLinkData.getClickTimestamp();
  }

  /**
   * Provides the redirect url, which is used as the alternative to opening the app. This url may
   * install the app or go to an app specific website.
   *
   * @return Url that can be used to create an intent to launch an activity.
   * @hide
   */
  @VisibleForTesting
  public Uri getRedirectUrl() {
    if (dynamicLinkData == null) {
      return null;
    }
    return dynamicLinkData.getRedirectUrl();
  }

  /**
   * Provide an intent to update the app to the version in the Play Store.
   *
   * <p>An intent is returned to be used as a parameter to {@link
   * android.app.Activity#startActivity(Intent)} to launch the Play Store update flow for the app.
   * After update, if the user re-launches the app from the Play Store by selecting the displayed
   * Continue button then the deep link will be set as the data in the re-launch intent and will
   * launch any Activity with an {@link android.content.IntentFilter} that matches the deeplink.
   * This is the same as the new install flow. The dynamic link returned during initial launch will
   * not be available from {@link FirebaseDynamicLinks#getDynamicLink(Intent)} during the update
   * re-launch.
   *
   * <p>If the minimum version required by the dynamic link is not greater than the currently
   * installed version, then null is returned.
   *
   * @return - An {@link Intent} that will launch the Play Store to update the app, or null if the
   *     dynamic link minimum version code is not greater than the installed version.
   */
  @Nullable
  public Intent getUpdateAppIntent(@NonNull Context context) {
    int versionCode;
    // zero indicates any version is accepted.
    if (getMinimumAppVersion() == 0) {
      return null;
    }
    try {
      versionCode =
          context
              .getPackageManager()
              .getPackageInfo(context.getApplicationContext().getPackageName(), 0)
              .versionCode;
    } catch (NameNotFoundException e) {
      // Unexpected exception, so return null indicating don't update.
      return null;
    }
    if (versionCode < getMinimumAppVersion() && getRedirectUrl() != null) {
      return new Intent(Intent.ACTION_VIEW)
          .setData(getRedirectUrl())
          .setPackage(GooglePlayServicesUtil.GOOGLE_PLAY_STORE_PACKAGE);
    }
    return null;
  }
}
