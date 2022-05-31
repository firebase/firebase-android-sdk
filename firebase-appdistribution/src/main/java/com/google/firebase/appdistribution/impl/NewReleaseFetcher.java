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

import static com.google.firebase.appdistribution.impl.ReleaseIdentificationUtils.getPackageInfo;
import static com.google.firebase.appdistribution.impl.TaskUtils.runAsyncInTask;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.PackageInfoCompat;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Class that handles fetching the latest release from App Distribution and determining if it is a
 * new release.
 */
class NewReleaseFetcher {
  private static final String TAG = "CheckForNewReleaseClient:";

  private final FirebaseApp firebaseApp;
  private final FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  private final Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider;
  private final ApkHashExtractor apkHashExtractor;
  private final Context context;
  // Maintain an in-memory mapping from source file to APK hash to avoid re-calculating the hash
  private static final ConcurrentMap<String, String> cachedApkHashes = new ConcurrentHashMap<>();

  Task<AppDistributionReleaseInternal> cachedCheckForNewRelease = null;
  private final Executor taskExecutor; // Executor to run task listeners on a background thread

  NewReleaseFetcher(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient,
      @NonNull Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider,
      ApkHashExtractor apkHashExtractor) {
    this(
        firebaseApp,
        firebaseAppDistributionTesterApiClient,
        firebaseInstallationsApiProvider,
        apkHashExtractor,
        Executors.newSingleThreadExecutor());
  }

  NewReleaseFetcher(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient,
      @NonNull Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider,
      ApkHashExtractor apkHashExtractor,
      @NonNull Executor executor) {
    this.firebaseApp = firebaseApp;
    this.firebaseAppDistributionTesterApiClient = firebaseAppDistributionTesterApiClient;
    this.firebaseInstallationsApiProvider = firebaseInstallationsApiProvider;
    this.apkHashExtractor = apkHashExtractor;
    this.taskExecutor = executor;
    this.context = firebaseApp.getApplicationContext();
  }

  @NonNull
  public synchronized Task<AppDistributionReleaseInternal> checkForNewRelease() {

    if (cachedCheckForNewRelease != null && !cachedCheckForNewRelease.isComplete()) {
      return cachedCheckForNewRelease;
    }

    Task<String> installationIdTask = firebaseInstallationsApiProvider.get().getId();
    // forceRefresh is false to get locally cached token if available
    Task<InstallationTokenResult> installationAuthTokenTask =
        firebaseInstallationsApiProvider.get().getToken(false);

    this.cachedCheckForNewRelease =
        Tasks.whenAllSuccess(installationIdTask, installationAuthTokenTask)
            .continueWithTask(TaskUtils::handleTaskFailure)
            .onSuccessTask(
                unused ->
                    getNewReleaseFromClientTask(
                        installationIdTask.getResult(),
                        firebaseApp.getOptions().getApplicationId(),
                        firebaseApp.getOptions().getApiKey(),
                        installationAuthTokenTask.getResult().getToken()));

    return cachedCheckForNewRelease;
  }

  private Task<AppDistributionReleaseInternal> getNewReleaseFromClientTask(
      String fid, String appId, String apiKey, String authToken) {
    return runAsyncInTask(
        taskExecutor, () -> getNewReleaseFromClient(fid, appId, apiKey, authToken));
  }

  @Nullable
  @VisibleForTesting
  AppDistributionReleaseInternal getNewReleaseFromClient(
      String fid, String appId, String apiKey, String authToken)
      throws FirebaseAppDistributionException {
    AppDistributionReleaseInternal retrievedNewRelease =
        firebaseAppDistributionTesterApiClient.fetchNewRelease(
            fid, appId, apiKey, authToken, firebaseApp.getApplicationContext());

    if (retrievedNewRelease == null) {
      LogWrapper.getInstance().v(TAG + "Tester does not have access to any releases");
      return null;
    }

    long newReleaseBuildVersion = parseBuildVersion(retrievedNewRelease.getBuildVersion());

    if (isOlderBuildVersion(newReleaseBuildVersion)) {
      LogWrapper.getInstance().v(TAG + "New release has lower version code than current release");
      return null;
    }

    if (isNewerBuildVersion(newReleaseBuildVersion)
        || !isSameAsInstalledRelease(retrievedNewRelease)
        || hasDifferentAppVersionName(retrievedNewRelease)) {
      return retrievedNewRelease;
    } else {
      LogWrapper.getInstance().v(TAG + "New release is older or is currently installed");
      return null;
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
      installedIasArtifactId =
          ReleaseIdentificationUtils.extractInternalAppSharingArtifactId(
              firebaseApp.getApplicationContext());
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
    String installedReleaseApkHash = apkHashExtractor.extractApkHash();
    if (newRelease.getApkHash().isEmpty()) {
      throw new FirebaseAppDistributionException(
          "Missing APK hash from new release", Status.UNKNOWN);
    }
    // If the hash of the zipped APK for the retrieved newRelease is equal to the stored hash
    // of the installed release, then they are the same release.
    return installedReleaseApkHash.equals(newRelease.getApkHash());
  }
}
