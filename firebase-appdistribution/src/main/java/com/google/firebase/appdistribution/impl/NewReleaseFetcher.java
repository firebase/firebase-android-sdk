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

import static com.google.firebase.appdistribution.impl.PackageInfoUtils.getPackageInfo;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.PackageInfoCompat;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;

/**
 * Class that handles fetching the latest release from App Distribution and determining if it is a
 * new release.
 */
class NewReleaseFetcher {
  private static final String TAG = "CheckForNewReleaseClient:";

  private final FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  private final ReleaseIdentifier releaseIdentifier;
  private final Context context;

  Task<AppDistributionReleaseInternal> cachedCheckForNewRelease = null;

  NewReleaseFetcher(
      @NonNull Context applicationContext,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient,
      ReleaseIdentifier releaseIdentifier) {
    this.firebaseAppDistributionTesterApiClient = firebaseAppDistributionTesterApiClient;
    this.context = applicationContext;
    this.releaseIdentifier = releaseIdentifier;
  }

  // TODO(b/261014422): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  @NonNull
  public synchronized Task<AppDistributionReleaseInternal> checkForNewRelease() {
    if (cachedCheckForNewRelease != null && !cachedCheckForNewRelease.isComplete()) {
      return cachedCheckForNewRelease;
    }

    this.cachedCheckForNewRelease =
        firebaseAppDistributionTesterApiClient
            .fetchNewRelease()
            .onSuccessTask(release -> Tasks.forResult(isNewerRelease(release) ? release : null));

    return cachedCheckForNewRelease;
  }

  private boolean isNewerRelease(AppDistributionReleaseInternal retrievedNewRelease)
      throws FirebaseAppDistributionException {
    if (retrievedNewRelease == null) {
      LogWrapper.getInstance().v(TAG + "Tester does not have access to any releases");
      return false;
    }

    long newReleaseBuildVersion = parseBuildVersion(retrievedNewRelease.getBuildVersion());

    if (isOlderBuildVersion(newReleaseBuildVersion)) {
      LogWrapper.getInstance().v(TAG + "New release has lower version code than current release");
      return false;
    }

    if (isNewerBuildVersion(newReleaseBuildVersion)
        || !isSameAsInstalledRelease(retrievedNewRelease)
        || hasDifferentAppVersionName(retrievedNewRelease)) {
      return true;
    } else {
      LogWrapper.getInstance().v(TAG + "New release is older or is currently installed");
      return false;
    }
  }

  private long parseBuildVersion(String buildVersion) throws FirebaseAppDistributionException {
    try {
      return Long.parseLong(buildVersion);
    } catch (NumberFormatException e) {
      throw new FirebaseAppDistributionException(
          "Could not parse build version of new release: " + buildVersion, Status.UNKNOWN, e);
    }
  }

  private boolean isOlderBuildVersion(long newReleaseBuildVersion)
      throws FirebaseAppDistributionException {
    return newReleaseBuildVersion < getInstalledAppVersionCode(context);
  }

  private boolean isNewerBuildVersion(long newReleaseBuildVersion)
      throws FirebaseAppDistributionException {
    return newReleaseBuildVersion > getInstalledAppVersionCode(context);
  }

  private boolean hasDifferentAppVersionName(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    return !newRelease.getDisplayVersion().equals(getInstalledAppVersionName(context));
  }

  @VisibleForTesting
  boolean isSameAsInstalledRelease(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    if (newRelease.getBinaryType().equals(BinaryType.APK)) {
      return hasSameHashAsInstalledRelease(newRelease);
    }

    if (newRelease.getIasArtifactId() == null || newRelease.getIasArtifactId().isEmpty()) {
      LogWrapper.getInstance()
          .w(TAG + "AAB release missing IAS Artifact ID. Assuming new release is different.");
      return false;
    }

    String installedIasArtifactId;
    try {
      installedIasArtifactId = releaseIdentifier.extractInternalAppSharingArtifactId();
    } catch (FirebaseAppDistributionException e) {
      LogWrapper.getInstance()
          .w(
              TAG + "Could not get installed IAS artifact ID. Assuming new release is different.",
              e);
      return false;
    }

    return newRelease.getIasArtifactId().equals(installedIasArtifactId);
  }

  private long getInstalledAppVersionCode(Context context) throws FirebaseAppDistributionException {
    return PackageInfoCompat.getLongVersionCode(getPackageInfo(context));
  }

  private String getInstalledAppVersionName(Context context)
      throws FirebaseAppDistributionException {
    return getPackageInfo(context).versionName;
  }

  private boolean hasSameHashAsInstalledRelease(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    if (newRelease.getApkHash().isEmpty()) {
      throw new FirebaseAppDistributionException(
          "Missing APK hash from new release", Status.UNKNOWN);
    }
    return releaseIdentifier.extractApkHash().equals(newRelease.getApkHash());
  }
}
