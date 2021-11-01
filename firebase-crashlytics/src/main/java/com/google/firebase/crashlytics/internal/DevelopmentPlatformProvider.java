package com.google.firebase.crashlytics.internal;

import static com.google.firebase.crashlytics.internal.common.CommonUtils.getResourcesIdentifier;

import android.content.Context;
import androidx.annotation.Nullable;

/** Provider for the development platform info. */
public class DevelopmentPlatformProvider {
  public static final String UNITY_PLATFORM = "Unity";

  private static final String UNITY_EDITOR = "com.google.firebase.crashlytics.unity_version";

  @Nullable private final String developmentPlatform;
  @Nullable private final String developmentPlatformVersion;

  public DevelopmentPlatformProvider(Context context) {
    // Unity Editor
    int unityEditorId = getResourcesIdentifier(context, UNITY_EDITOR, "string");
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
