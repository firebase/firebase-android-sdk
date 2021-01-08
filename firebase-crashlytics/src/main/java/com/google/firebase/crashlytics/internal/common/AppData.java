// Copyright 2020 Google LLC
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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.google.firebase.crashlytics.internal.unity.UnityVersionProvider;

/** Carries static information about the app. */
class AppData {
  public final String googleAppId;
  public final String buildId;

  public final String installerPackageName;

  public final String packageName;
  public final String versionCode;
  public final String versionName;

  public final UnityVersionProvider unityVersionProvider;

  public static AppData create(
      Context context,
      IdManager idManager,
      String googleAppId,
      String buildId,
      UnityVersionProvider unityVersionProvider)
      throws PackageManager.NameNotFoundException {
    final String packageName = context.getPackageName();
    final String installerPackageName = idManager.getInstallerPackageName();
    final PackageManager packageManager = context.getPackageManager();
    final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
    final String versionCode = Integer.toString(packageInfo.versionCode);
    final String versionName =
        packageInfo.versionName == null ? IdManager.DEFAULT_VERSION_NAME : packageInfo.versionName;

    return new AppData(
        googleAppId,
        buildId,
        installerPackageName,
        packageName,
        versionCode,
        versionName,
        unityVersionProvider);
  }

  public AppData(
      String googleAppId,
      String buildId,
      String installerPackageName,
      String packageName,
      String versionCode,
      String versionName,
      UnityVersionProvider unityVersionProvider) {
    this.googleAppId = googleAppId;
    this.buildId = buildId;
    this.installerPackageName = installerPackageName;
    this.packageName = packageName;
    this.versionCode = versionCode;
    this.versionName = versionName;
    this.unityVersionProvider = unityVersionProvider;
  }
}
