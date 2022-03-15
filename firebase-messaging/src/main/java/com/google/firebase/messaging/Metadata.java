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
package com.google.firebase.messaging;

import static com.google.firebase.messaging.FirebaseMessaging.TAG;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import com.google.android.gms.common.util.PlatformVersion;
import com.google.firebase.FirebaseApp;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/** Helper to query app's metadata. */
class Metadata {
  /** GMScore holds this permission. Use it for security checks. */
  private static final String GMSCORE_SEND_PERMISSION = "com.google.android.c2dm.permission.SEND";
  /* GmsCore package name. package visible as it's used by other classes in firebase-iid. */
  static final String GMS_PACKAGE = "com.google.android.gms";

  private static final String ACTION_IID_TOKEN_REQUEST = "com.google.iid.TOKEN_REQUEST";

  private static final String ACTION_C2DM_REGISTER = "com.google.android.c2dm.intent.REGISTER";

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    GMSCORE_NOT_FOUND,
    IID_VIA_SERVICE,
    IID_VIA_RECEIVER,
  })
  @interface IidImplementationType {};

  static final int GMSCORE_NOT_FOUND = 0;
  static final int IID_VIA_SERVICE = 1;
  static final int IID_VIA_RECEIVER = 2;

  private final Context context;

  @GuardedBy("this")
  private String appVersionCode;

  @GuardedBy("this")
  private String appVersionName;

  @GuardedBy("this")
  private int gmsVersionCode;

  /** Cache of whether gmscore is present on the device */
  @GuardedBy("this")
  @IidImplementationType
  private int iidImplementation = GMSCORE_NOT_FOUND;

  Metadata(Context context) {
    this.context = context;
  }

  boolean isGmscorePresent() {
    return getIidImplementation() != GMSCORE_NOT_FOUND;
  }

  /**
   * Find the InstanceID implementation package - for maximum backward compatibility it is the
   * service implementing ACTION_C2DM_REGISTER with maximum priority.
   *
   * <p>It is GSF, GMS - can also be another google-signed package if we release a standalone GCM
   * for subset of devices.
   *
   * <p>It can also be a vendor-provided package if GMS is completely missing, InstanceID is
   * intended to be implementable by anyone.
   *
   * <p>Android will refuse to install packages with different signatures declaring same signature,
   * and the RECEIVE permission is declared in GSF.
   */
  @IidImplementationType
  synchronized int getIidImplementation() {
    // If present, return cached value
    if (iidImplementation != GMSCORE_NOT_FOUND) {
      return iidImplementation;
    }

    PackageManager pm = context.getPackageManager();

    int permissionState = pm.checkPermission(GMSCORE_SEND_PERMISSION, GMS_PACKAGE);
    if (permissionState == PackageManager.PERMISSION_DENIED) {
      Log.e(TAG, "Google Play services missing or without correct permission.");
      return GMSCORE_NOT_FOUND;
    }

    // Search for the gmscore IID service first (only in Pre O).
    // Gmscore v10 introduced an IID implementation via broadcast receiver to comply with Android O
    // (startService from background is not allowed). Unfortunately v10/v11 on Android < O have a
    // bug that mean it doesn't work for multiuser profile. (this was fix in O sidecar, and v12+)
    if (!PlatformVersion.isAtLeastO()) {
      Intent intent = new Intent(ACTION_C2DM_REGISTER);
      intent.setPackage(GMS_PACKAGE);
      List<ResolveInfo> infos = pm.queryIntentServices(intent, /* flags= */ 0);
      if (infos != null && infos.size() > 0) {
        iidImplementation = IID_VIA_SERVICE;
        return iidImplementation;
      }
    }

    Intent intent = new Intent(ACTION_IID_TOKEN_REQUEST);
    intent.setPackage(GMS_PACKAGE);
    List<ResolveInfo> infos = pm.queryBroadcastReceivers(intent, /* flags= */ 0);
    if (infos != null && infos.size() > 0) {
      iidImplementation = IID_VIA_RECEIVER;
      return iidImplementation;
    }

    Log.w(TAG, "Failed to resolve IID implementation package, falling back");
    // If no receiver/service is found, fallback to GMS to a per-platform default
    if (PlatformVersion.isAtLeastO()) {
      iidImplementation = IID_VIA_RECEIVER;
    } else {
      iidImplementation = IID_VIA_SERVICE;
    }
    return iidImplementation;
  }

  static String getDefaultSenderId(FirebaseApp app) {
    // Check for an explicit sender id
    String senderId = app.getOptions().getGcmSenderId();
    if (senderId != null) {
      return senderId;
    }
    String appId = app.getOptions().getApplicationId();
    if (!appId.startsWith("1:")) {
      // Not v1, server should be updated to accept the full app ID now
      return appId;
    } else {
      // For v1 app IDs, fall back to parsing the project ID out
      String[] parts = appId.split(":");
      if (parts.length < 2) {
        return null; // Invalid format
      }
      String projectId = parts[1];
      if (projectId.isEmpty()) {
        return null; // No project ID
      }
      return projectId;
    }
  }

  /** Gets the application version code. */
  synchronized String getAppVersionCode() {
    if (appVersionCode == null) {
      populateAppVersionInfo();
    }
    return appVersionCode;
  }

  /** Gets the application version name. */
  synchronized String getAppVersionName() {
    if (appVersionName == null) {
      populateAppVersionInfo();
    }
    return appVersionName;
  }

  /** Gets the gmscore version. */
  synchronized int getGmsVersionCode() {
    if (gmsVersionCode == 0) {
      PackageInfo info = getPackageInfo(GMS_PACKAGE);
      if (info != null) {
        gmsVersionCode = info.versionCode;
      }
    }
    return gmsVersionCode;
  }

  private synchronized void populateAppVersionInfo() {
    PackageInfo info = getPackageInfo(context.getPackageName());
    if (info != null) {
      appVersionCode = Integer.toString(info.versionCode);
      appVersionName = info.versionName;
    }
  }

  private PackageInfo getPackageInfo(String packageName) {
    try {
      return context.getPackageManager().getPackageInfo(packageName, 0);
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, "Failed to find package " + e);
      return null;
    }
  }
}
