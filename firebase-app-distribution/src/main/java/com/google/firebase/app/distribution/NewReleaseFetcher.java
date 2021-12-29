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

package com.google.firebase.app.distribution;

import static com.google.firebase.app.distribution.ReleaseIdentificationUtils.calculateApkHash;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.PackageInfoCompat;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.app.distribution.internal.LogWrapper;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.io.File;
import java.util.Locale;
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
  // Maintain an in-memory mapping from source file to APK hash to avoid re-calculating the hash
  private static final ConcurrentMap<String, String> cachedApkHashes = new ConcurrentHashMap<>();

  Task<AppDistributionReleaseInternal> cachedCheckForNewRelease = null;
  private final Executor taskExecutor; // Executor to run task listeners on a background thread

  NewReleaseFetcher(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient,
      @NonNull Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider) {
    this.firebaseApp = firebaseApp;
    this.firebaseAppDistributionTesterApiClient = firebaseAppDistributionTesterApiClient;
    this.firebaseInstallationsApiProvider = firebaseInstallationsApiProvider;
    this.taskExecutor = Executors.newSingleThreadExecutor();
  }

  NewReleaseFetcher(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient,
      @NonNull Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider,
      @NonNull Executor executor) {
    this.firebaseApp = firebaseApp;
    this.firebaseAppDistributionTesterApiClient = firebaseAppDistributionTesterApiClient;
    this.firebaseInstallationsApiProvider = firebaseInstallationsApiProvider;
    this.taskExecutor = executor;
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
            .onSuccessTask(
                taskExecutor,
                tasks -> {
                  String fid = installationIdTask.getResult();
                  InstallationTokenResult installationTokenResult =
                      installationAuthTokenTask.getResult();
                  try {
                    AppDistributionReleaseInternal newRelease =
                        getNewReleaseFromClient(
                            fid,
                            firebaseApp.getOptions().getApplicationId(),
                            firebaseApp.getOptions().getApiKey(),
                            installationTokenResult.getToken());
                    return Tasks.forResult(newRelease);
                  } catch (FirebaseAppDistributionException ex) {
                    return Tasks.forException(ex);
                  }
                })
            .continueWithTask(
                taskExecutor,
                task ->
                    TaskUtils.handleTaskFailure(
                        task,
                        Constants.ErrorMessages.NETWORK_ERROR,
                        FirebaseAppDistributionException.Status.NETWORK_FAILURE));

    return cachedCheckForNewRelease;
  }

  @VisibleForTesting
  AppDistributionReleaseInternal getNewReleaseFromClient(
      String fid, String appId, String apiKey, String authToken)
      throws FirebaseAppDistributionException {
    try {
      AppDistributionReleaseInternal retrievedNewRelease =
          firebaseAppDistributionTesterApiClient.fetchNewRelease(
              fid, appId, apiKey, authToken, firebaseApp.getApplicationContext());

      if (retrievedNewRelease == null) {
        LogWrapper.getInstance().v(TAG + "Tester does not have access to any releases");
        return null;
      }

      if (!canInstall(retrievedNewRelease)) {
        LogWrapper.getInstance().v(TAG + "New release has lower version code than current release");
        return null;
      }

      if (isNewerBuildVersion(retrievedNewRelease)
          || !isSameAsInstalledRelease(retrievedNewRelease)
          || hasDifferentAppVersionName(retrievedNewRelease)) {
        return retrievedNewRelease;
      } else {
        // Return null if retrieved new release is older or currently installed
        LogWrapper.getInstance().v(TAG + "New release is older or is currently installed");
        return null;
      }
    } catch (NumberFormatException e) {
      LogWrapper.getInstance().e(TAG + "Error parsing buildVersion.", e);
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.NETWORK_ERROR,
          FirebaseAppDistributionException.Status.NETWORK_FAILURE,
          e);
    }
  }

  private boolean canInstall(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    return Long.parseLong(newRelease.getBuildVersion())
        >= getInstalledAppVersionCode(firebaseApp.getApplicationContext());
  }

  private boolean isNewerBuildVersion(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    return Long.parseLong(newRelease.getBuildVersion())
        > getInstalledAppVersionCode(firebaseApp.getApplicationContext());
  }

  private boolean hasDifferentAppVersionName(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    return !newRelease
        .getDisplayVersion()
        .equals(getInstalledAppVersionName(firebaseApp.getApplicationContext()));
  }

  @VisibleForTesting
  boolean isSameAsInstalledRelease(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    if (newRelease.getBinaryType().equals(BinaryType.APK)) {
      return hasSameHashAsInstalledRelease(newRelease);
    }

    // TODO(lkellogg): getIasArtifactId() will likely never be null since it's set to the empty
    //  string if not present in the response
    if (newRelease.getIasArtifactId() == null) {
      return false;
    }
    // AAB BinaryType
    return newRelease
        .getIasArtifactId()
        .equals(
            ReleaseIdentificationUtils.extractInternalAppSharingArtifactId(
                firebaseApp.getApplicationContext()));
  }

  private long getInstalledAppVersionCode(Context context) throws FirebaseAppDistributionException {
    return PackageInfoCompat.getLongVersionCode(getPackageInfo(context));
  }

  private String getInstalledAppVersionName(Context context)
      throws FirebaseAppDistributionException {
    return getPackageInfo(context).versionName;
  }

  private PackageInfo getPackageInfo(Context context) throws FirebaseAppDistributionException {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    } catch (PackageManager.NameNotFoundException e) {
      LogWrapper.getInstance()
          .e(TAG + "Unable to find package with name " + context.getPackageName(), e);
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.UNKNOWN_ERROR,
          FirebaseAppDistributionException.Status.UNKNOWN,
          e);
    }
  }

  @VisibleForTesting
  String extractApkHash(PackageInfo packageInfo) {
    File sourceFile = new File(packageInfo.applicationInfo.sourceDir);

    String key =
        String.format(
            Locale.ENGLISH, "%s.%d", sourceFile.getAbsolutePath(), sourceFile.lastModified());
    if (!cachedApkHashes.containsKey(key)) {
      cachedApkHashes.put(key, calculateApkHash(sourceFile));
    }
    return cachedApkHashes.get(key);
  }

  private boolean hasSameHashAsInstalledRelease(AppDistributionReleaseInternal newRelease)
      throws FirebaseAppDistributionException {
    try {
      Context context = firebaseApp.getApplicationContext();
      PackageInfo metadataPackageInfo =
          context
              .getPackageManager()
              .getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      String installedReleaseApkHash = extractApkHash(metadataPackageInfo);

      if (installedReleaseApkHash.isEmpty() || newRelease.getApkHash().isEmpty()) {
        LogWrapper.getInstance().e(TAG + "Missing APK hash.");
        throw new FirebaseAppDistributionException(
            Constants.ErrorMessages.UNKNOWN_ERROR, FirebaseAppDistributionException.Status.UNKNOWN);
      }
      // If the hash of the zipped APK for the retrieved newRelease is equal to the stored hash
      // of the installed release, then they are the same release.
      return installedReleaseApkHash.equals(newRelease.getApkHash());
    } catch (PackageManager.NameNotFoundException e) {
      LogWrapper.getInstance().e(TAG + "Unable to locate App.", e);
      return false;
    }
  }
}
