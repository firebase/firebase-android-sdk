// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * This class serves to retrieve the name of the package which installed the host app. It caches the
 * return value, since retrieving it is somewhat expensive.
 */
class InstallerPackageNameProvider {
  // A "null" installer package name value indicates it hasn't been loaded yet. However, the load
  // can return null, so we use this as a non-null sentinel value to indicate the load has
  // completed but did not return a value.
  private static final String NO_INSTALLER_PACKAGE_NAME = "";

  private String installerPackageName;

  synchronized String getInstallerPackageName(Context appContext) {
    if (installerPackageName == null) {
      installerPackageName = loadInstallerPackageName(appContext);
    }
    return NO_INSTALLER_PACKAGE_NAME.equals(installerPackageName) ? null : installerPackageName;
  }

  private static String loadInstallerPackageName(Context appContext) {
    final PackageManager pm = appContext.getPackageManager();
    final String hostAppPackageName = appContext.getPackageName();
    final String installerPackageName = pm.getInstallerPackageName(hostAppPackageName);

    // The PackageManager API returns null if the value is not set, but we need a non-null
    // value for caching, so we return the constant instead
    return installerPackageName == null ? NO_INSTALLER_PACKAGE_NAME : installerPackageName;
  }
}
