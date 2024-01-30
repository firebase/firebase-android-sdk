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

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.PackageInfoCompat;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.util.concurrent.Executor;
import javax.inject.Inject;

/**
 * Class that handles fetching the latest release from App Distribution and determining if it is a
 * new release.
 */
class NewReleaseFetcher {
  private static final String TAG = "NewReleaseFetcher";

  private final FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  private final ReleaseIdentifier releaseIdentifier;
  private final DevModeDetector devModeDetector;
  private @Lightweight Executor lightweightExecutor;
  private final Context context;

  TaskCache<AppDistributionReleaseInternal> cachedCheckForNewRelease;

  @Inject
  NewReleaseFetcher(
      @NonNull Context applicationContext,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient,
      ReleaseIdentifier releaseIdentifier,
      DevModeDetector devModeDetector,
      @Lightweight Executor lightweightExecutor) {
    this.firebaseAppDistributionTesterApiClient = firebaseAppDistributionTesterApiClient;
    this.context = applicationContext;
    this.releaseIdentifier = releaseIdentifier;
    this.devModeDetector = devModeDetector;
    this.lightweightExecutor = lightweightExecutor;
    this.cachedCheckForNewRelease = new TaskCache<>(lightweightExecutor);
  }

  @NonNull
  public Task<AppDistributionReleaseInternal> checkForNewRelease() {
    if (devModeDetector.isDevModeEnabled()) {
      LogWrapper.w(TAG, "Not checking for new release because development mode is enabled.");
      return Tasks.forResult(null);
    }
    return cachedCheckForNewRelease.getOrCreateTask(
        () ->
            firebaseAppDistributionTesterApiClient
                .fetchNewRelease()
                .onSuccessTask(
                    lightweightExecutor,
                    release ->
                        isNewerRelease(release)
                            .onSuccessTask(
                                lightweightExecutor,
                                isNewer -> Tasks.forResult(isNewer ? release : null))));
  }

  private Task<Boolean> isNewerRelease(AppDistributionReleaseInternal retrievedNewRelease)
      throws FirebaseAppDistributionException {
    if (retrievedNewRelease == null) {
      LogWrapper.v(TAG, "Tester does not have access to any releases");
      return Tasks.forResult(false);
    }

    long newReleaseBuildVersion = parseBuildVersion(retrievedNewRelease.getBuildVersion());

    if (isOlderBuildVersion(newReleaseBuildVersion)) {
      LogWrapper.v(TAG, "New release has lower version code than current release");
      return Tasks.forResult(false);
    }

    if (isNewerBuildVersion(newReleaseBuildVersion)
        || hasDifferentAppVersionName(retrievedNewRelease)) {
      return Tasks.forResult(true);
    }

    return hasSameBinaryIdentifier(retrievedNewRelease)
        .onSuccessTask(
            lightweightExecutor,
            hasSameBinaryIdentifier -> {
              if (hasSameBinaryIdentifier) {
                // The release has the same app version name and binary identifier, and has either
                // an equal or older build version
                LogWrapper.v(TAG, "New release is older or is currently installed");
                return Tasks.forResult(false);
              }
              return Tasks.forResult(true);
            });
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
  Task<Boolean> hasSameBinaryIdentifier(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    if (newRelease.getBinaryType().equals(BinaryType.APK)) {
      return hasSameHashAsInstalledRelease(newRelease);
    }
    return Tasks.forResult(hasSameIasArtifactId(newRelease));
  }

  boolean hasSameIasArtifactId(AppDistributionReleaseInternal newRelease) {
    if (newRelease.getIasArtifactId() == null || newRelease.getIasArtifactId().isEmpty()) {
      LogWrapper.w(TAG, "AAB release missing IAS Artifact ID. Assuming new release is different.");
      return false;
    }

    String installedIasArtifactId;
    try {
      installedIasArtifactId = releaseIdentifier.extractInternalAppSharingArtifactId();
    } catch (FirebaseAppDistributionException e) {
      LogWrapper.w(
          TAG, "Could not get installed IAS artifact ID. Assuming new release is different.", e);
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

  private Task<Boolean> hasSameHashAsInstalledRelease(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    if (newRelease.getApkHash().isEmpty()) {
      return Tasks.forException(
          new FirebaseAppDistributionException(
              "Missing APK hash from new release", Status.UNKNOWN));
    }
    return releaseIdentifier
        .extractApkHash()
        .onSuccessTask(
            lightweightExecutor,
            apkHash -> Tasks.forResult(apkHash.equals(newRelease.getApkHash())));
  }
}
