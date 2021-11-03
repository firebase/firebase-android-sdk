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

package com.google.firebase.crashlytics.internal;

import static com.google.firebase.crashlytics.internal.common.CommonUtils.getResourcesIdentifier;

import android.content.Context;
import androidx.annotation.Nullable;

/** Provider for the development platform info. */
public class DevelopmentPlatformProvider {
  public static final String UNITY_PLATFORM = "Unity";

  private static final String UNITY_VERSION_FIELD = "com.google.firebase.crashlytics.unity_version";

  @Nullable private final String developmentPlatform;
  @Nullable private final String developmentPlatformVersion;

  public DevelopmentPlatformProvider(Context context) {
    // Unity
    int unityEditorId = getResourcesIdentifier(context, UNITY_VERSION_FIELD, "string");
    if (unityEditorId != 0) {
      developmentPlatform = UNITY_PLATFORM;
      developmentPlatformVersion = context.getResources().getString(unityEditorId);
      Logger.getLogger().v("Unity Editor version is: " + developmentPlatformVersion);
      return;
    }

    developmentPlatform = null;
    developmentPlatformVersion = null;
  }

  /**
   * Gets the development platform name.
   *
   * <p>Returns <code>null</code> for unknown/no development platform.
   */
  @Nullable
  public String getDevelopmentPlatform() {
    return developmentPlatform;
  }

  /**
   * Gets the development platform version.
   *
   * <p>Returns <code>null</code> for unknown/no development platform version.
   */
  @Nullable
  public String getDevelopmentPlatformVersion() {
    return developmentPlatformVersion;
  }
}
