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

package com.google.firebase.appdistribution;

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.os.HandlerCompat;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.appdistribution.internal.ReleaseIdentificationUtils;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CheckForUpdateClient {
  private static final int UPDATE_THREAD_POOL_SIZE = 4;

  private final FirebaseApp firebaseApp;
  private FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  private final FirebaseInstallationsApi firebaseInstallationsApi;

  private TaskCompletionSource<AppDistributionRelease> checkForUpdateTaskCompletionSource = null;
  private CancellationTokenSource checkForUpdateCancellationSource;
  private final Executor checkForUpdateExecutor;

  CheckForUpdateClient(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi) {
    this.firebaseApp = firebaseApp;
    this.firebaseAppDistributionTesterApiClient = firebaseAppDistributionTesterApiClient;
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    // TODO: verify if this is best way to use executorservice here
    this.checkForUpdateExecutor = Executors.newFixedThreadPool(UPDATE_THREAD_POOL_SIZE);
  }

  @NonNull
  public Task<AppDistributionRelease> checkForUpdate() {

    if (checkForUpdateTaskCompletionSource != null
        && !checkForUpdateTaskCompletionSource.getTask().isComplete()) {
      checkForUpdateCancellationSource.cancel();
    }

    checkForUpdateCancellationSource = new CancellationTokenSource();
    checkForUpdateTaskCompletionSource =
        new TaskCompletionSource<>(checkForUpdateCancellationSource.getToken());

    Task<String> installationIdTask = firebaseInstallationsApi.getId();
    // forceRefresh is false to get locally cached token if available
    Task<InstallationTokenResult> installationAuthTokenTask =
        firebaseInstallationsApi.getToken(false);

    Tasks.whenAllSuccess(installationIdTask, installationAuthTokenTask)
        .addOnSuccessListener(
            tasks -> {
              String fid = installationIdTask.getResult();
              InstallationTokenResult installationTokenResult =
                  installationAuthTokenTask.getResult();
              checkForUpdateExecutor.execute(
                  () -> {
                    try {
                      AppDistributionReleaseInternal latestRelease =
                          getLatestReleaseFromClient(
                              fid,
                              firebaseApp.getOptions().getApplicationId(),
                              firebaseApp.getOptions().getApiKey(),
                              installationTokenResult.getToken());
                      updateOnUiThread(
                          () -> {
                            // TODO: add latest release to storage
                            if (checkForUpdateTaskCompletionSource != null
                                && !checkForUpdateTaskCompletionSource.getTask().isComplete())
                              checkForUpdateTaskCompletionSource.setResult(
                                  convertToAppDistributionRelease(latestRelease));
                          });
                    } catch (FirebaseAppDistributionException ex) {
                      updateOnUiThread(() -> setCheckForUpdateTaskCompletionError(ex));
                    }
                  });
            })
        .addOnFailureListener(
            e ->
                setCheckForUpdateTaskCompletionError(
                    new FirebaseAppDistributionException(
                        Constants.ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE, e)));

    return checkForUpdateTaskCompletionSource.getTask();
  }

  @VisibleForTesting
  AppDistributionReleaseInternal getLatestReleaseFromClient(
      String fid, String appId, String apiKey, String authToken)
      throws FirebaseAppDistributionException {
    try {
      AppDistributionReleaseInternal retrievedLatestRelease =
          firebaseAppDistributionTesterApiClient.fetchLatestRelease(fid, appId, apiKey, authToken);

      if (isNewerBuildVersion(retrievedLatestRelease)
          && !isInstalledRelease(retrievedLatestRelease)) {
        return retrievedLatestRelease;
      } else {
        // Return null if retrieved latest release is older or currently installed
        return null;
      }
    } catch (NumberFormatException e) {
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.NETWORK_ERROR,
          FirebaseAppDistributionException.Status.NETWORK_FAILURE,
          e);
    }
  }

  private AppDistributionRelease convertToAppDistributionRelease(
      AppDistributionReleaseInternal internalRelease) {
    return AppDistributionRelease.builder()
        .setBuildVersion(internalRelease.getBuildVersion())
        .setDisplayVersion(internalRelease.getDisplayVersion())
        .setReleaseNotes(internalRelease.getReleaseNotes())
        .setBinaryType(internalRelease.getBinaryType())
        .build();
  }

  private void updateOnUiThread(Runnable runnable) {
    HandlerCompat.createAsync(Looper.getMainLooper()).post(runnable);
  }

  private void setCheckForUpdateTaskCompletionError(FirebaseAppDistributionException e) {
    if (checkForUpdateTaskCompletionSource != null
        && !checkForUpdateTaskCompletionSource.getTask().isComplete()) {
      this.checkForUpdateTaskCompletionSource.setException(e);
    }
  }

  private boolean isNewerBuildVersion(AppDistributionReleaseInternal latestRelease) {
    return Long.parseLong(latestRelease.getBuildVersion())
        >= getInstalledAppVersionCode(firebaseApp.getApplicationContext());
  }

  private boolean isInstalledRelease(AppDistributionReleaseInternal latestRelease) {
    if (latestRelease.getBinaryType().equals(BinaryType.APK)) {
      // TODO(rachelprince): APK codehash verification
      return false;
    }

    if (latestRelease.getIasArtifactId() == null) {
      return false;
    }
    // AAB BinaryType
    return latestRelease
        .getIasArtifactId()
        .equals(
            ReleaseIdentificationUtils.extractInternalAppSharingArtifactId(
                firebaseApp.getApplicationContext()));
  }

  private long getInstalledAppVersionCode(Context context) {
    PackageInfo pInfo = null;
    try {
      pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    } catch (PackageManager.NameNotFoundException e) {
      checkForUpdateTaskCompletionSource.setException(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.UNKNOWN_ERROR,
              FirebaseAppDistributionException.Status.UNKNOWN,
              e));
    }
    return PackageInfoCompat.getLongVersionCode(pInfo);
  }
}
