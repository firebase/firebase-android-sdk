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

package com.google.firebase.appdistribution.impl;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Build.VERSION;
import androidx.core.content.ContextCompat;
import javax.inject.Inject;

class AppIconSource {
  private static final String TAG = "AppIconSource";

  private static final int DEFAULT_ICON = android.R.drawable.sym_def_app_icon;

  @Inject
  AppIconSource() {}

  /**
   * Get an icon for the given app context, or a default icon if the app icon is adaptive or does
   * not exist.
   *
   * <p>This is useful for showing an icon in notifications, since adaptive icons cause a crash loop
   * in the NotificationsManager (b/69969749).
   *
   * @return A drawable resource identifier (in the package's resources) of the icon.
   */
  int getNonAdaptiveIconOrDefault(Context context) {
    int iconId = context.getApplicationInfo().icon;
    if (iconId == 0) {
      return DEFAULT_ICON;
    }

    Drawable icon;
    try {
      icon = ContextCompat.getDrawable(context, iconId);
    } catch (Resources.NotFoundException ex) {
      return DEFAULT_ICON;
    }

    if (isAdaptiveIcon(icon)) {
      LogWrapper.e(
          TAG, "Adaptive icons cannot be used in notifications. Ignoring icon id: " + iconId);
      return DEFAULT_ICON;
    }

    return iconId;
  }

  private boolean isAdaptiveIcon(Drawable icon) {
    // AdaptiveIcons were introduced in API 26
    return VERSION.SDK_INT >= Build.VERSION_CODES.O && icon instanceof AdaptiveIconDrawable;
  }
}
