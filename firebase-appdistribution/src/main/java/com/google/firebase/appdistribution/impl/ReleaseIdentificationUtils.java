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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;

final class ReleaseIdentificationUtils {

  private static final int NO_FLAGS = 0;
  static final String IAS_ARTIFACT_ID_METADATA_KEY = "com.android.vending.internal.apk.id";

  /**
   * Get the package info for the currently installed app.
   *
   * @throws FirebaseAppDistributionException if the package name can't be found.
   */
  static PackageInfo getPackageInfo(Context context) throws FirebaseAppDistributionException {
    return getPackageInfoWithFlags(context, NO_FLAGS);
  }

  /**
   * Get the package info for the currently installed app, with the PackageManager.GET_META_DATA
   * flag set.
   *
   * @throws FirebaseAppDistributionException if the package name can't be found.
   */
  static PackageInfo getPackageInfoWithMetadata(Context context)
      throws FirebaseAppDistributionException {
    return getPackageInfoWithFlags(context, PackageManager.GET_META_DATA);
  }

  private static PackageInfo getPackageInfoWithFlags(Context context, int flags)
      throws FirebaseAppDistributionException {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
    } catch (PackageManager.NameNotFoundException e) {
      throw new FirebaseAppDistributionException(
          "Unable to find package with name " + context.getPackageName(), Status.UNKNOWN, e);
    }
  }

  @Nullable
  static String extractInternalAppSharingArtifactId(@NonNull Context appContext)
      throws FirebaseAppDistributionException {
    PackageInfo packageInfo = getPackageInfoWithMetadata(appContext);
    if (packageInfo.applicationInfo.metaData == null) {
      throw new FirebaseAppDistributionException("Missing package info metadata", Status.UNKNOWN);
    }
    return packageInfo.applicationInfo.metaData.getString(IAS_ARTIFACT_ID_METADATA_KEY);
  }
}
