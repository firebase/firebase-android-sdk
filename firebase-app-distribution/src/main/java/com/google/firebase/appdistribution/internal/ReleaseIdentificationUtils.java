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

package com.google.firebase.appdistribution.internal;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ReleaseIdentificationUtils {
  private static final String TAG = "ReleaseIdentification";

  @Nullable
  public static String extractInternalAppSharingArtifactId(@NonNull Context appContext) {
    try {
      PackageInfo packageInfo =
          appContext
              .getPackageManager()
              .getPackageInfo(appContext.getPackageName(), PackageManager.GET_META_DATA);
      if (packageInfo.applicationInfo.metaData == null) {
        return null;
      }
      return packageInfo.applicationInfo.metaData.getString("com.android.vending.internal.apk.id");
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, "Could not extract internal app sharing artifact ID");
      return null;
    }
  }
}
