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
import java.io.IOException;
import java.io.InputStream;

/** Provider for the development platform info. */
public class DevelopmentPlatformProvider {
  private static final String UNITY_PLATFORM = "Unity";
  private static final String FLUTTER_PLATFORM = "Flutter";

  private static final String UNITY_VERSION_FIELD = "com.google.firebase.crashlytics.unity_version";
  private static final String FLUTTER_ASSET_FILE = "flutter_assets/NOTICES.Z";

  private final Context context;
  @Nullable private DevelopmentPlatform developmentPlatform;

  public DevelopmentPlatformProvider(Context context) {
    this.context = context;
    developmentPlatform = null;
  }

  /**
   * Gets the development platform name.
   *
   * <p>Returns <code>null</code> for unknown/no development platform.
   */
  @Nullable
  public String getDevelopmentPlatform() {
    return initDevelopmentPlatform().developmentPlatform;
  }

  /**
   * Gets the development platform version.
   *
   * <p>Returns <code>null</code> for unknown/no development platform version.
   */
  @Nullable
  public String getDevelopmentPlatformVersion() {
    return initDevelopmentPlatform().developmentPlatformVersion;
  }

  /**
   * Returns if the development platform is Unity, without initializing the rest of the
   * DevelopmentPlatform object.
   *
   * <p>This is useful for the NDK to avoid an expensive file operation during start up.
   */
  public static boolean isUnity(Context context) {
    return getResourcesIdentifier(context, UNITY_VERSION_FIELD, "string") != 0;
  }

  /** Quickly and safely check if the given asset file exists. */
  private boolean assetFileExists(String file) {
    if (context.getAssets() == null) {
      return false;
    }
    try (InputStream ignored = context.getAssets().open(file)) {
      return true;
    } catch (IOException ex) {
      return false;
    }
  }

  private DevelopmentPlatform initDevelopmentPlatform() {
    if (developmentPlatform == null) {
      developmentPlatform = new DevelopmentPlatform();
    }
    return developmentPlatform;
  }

  private class DevelopmentPlatform {
    @Nullable private final String developmentPlatform;
    @Nullable private final String developmentPlatformVersion;

    private DevelopmentPlatform() {
      // Unity
      int unityEditorId = getResourcesIdentifier(context, UNITY_VERSION_FIELD, "string");
      if (unityEditorId != 0) {
        developmentPlatform = UNITY_PLATFORM;
        developmentPlatformVersion = context.getResources().getString(unityEditorId);
        Logger.getLogger().v("Unity Editor version is: " + developmentPlatformVersion);
        return;
      }

      // Flutter
      if (assetFileExists(FLUTTER_ASSET_FILE)) {
        developmentPlatform = FLUTTER_PLATFORM;
        // TODO: Get the version when available - https://github.com/flutter/flutter/issues/92681
        developmentPlatformVersion = null;
        Logger.getLogger().v("Development platform is: " + FLUTTER_PLATFORM);
        return;
      }

      // Unknown/no development platform
      developmentPlatform = null;
      developmentPlatformVersion = null;
    }
  }
}
