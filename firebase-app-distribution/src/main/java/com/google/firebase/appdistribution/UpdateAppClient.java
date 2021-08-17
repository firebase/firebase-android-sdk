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

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;

/** Client class for updateApp functionality in {@link FirebaseAppDistribution}. */
public class UpdateAppClient {

  private final UpdateApkClient updateApkClient;

  @GuardedBy("activityLock")
  private Activity currentActivity;

  private final Object activityLock = new Object();
  private UpdateTaskImpl cachedUpdateAppTask;

  public UpdateAppClient(@NonNull FirebaseApp firebaseApp) {
    this.updateApkClient = new UpdateApkClient(firebaseApp);
  }

  @NonNull
  synchronized UpdateTask updateApp(
      @NonNull AppDistributionReleaseInternal latestRelease,
      @NonNull boolean showDownloadInNotificationManager) {

    if (cachedUpdateAppTask != null && !cachedUpdateAppTask.isComplete()) {
      return cachedUpdateAppTask;
    }

    cachedUpdateAppTask = new UpdateTaskImpl();

    if (latestRelease == null) {
      cachedUpdateAppTask.setException(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.NOT_FOUND_ERROR, UPDATE_NOT_AVAILABLE));
      return cachedUpdateAppTask;
    }

    if (latestRelease.getDownloadUrl() == null) {
      cachedUpdateAppTask.setException(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.DOWNLOAD_URL_NOT_FOUND,
              FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
      return cachedUpdateAppTask;
    }

    if (latestRelease.getBinaryType() == BinaryType.AAB) {
      redirectToPlayForAabUpdate(cachedUpdateAppTask, latestRelease.getDownloadUrl());
    } else {
      this.updateApkClient.updateApk(
          cachedUpdateAppTask, latestRelease, showDownloadInNotificationManager);
    }
    return cachedUpdateAppTask;
  }

  private void redirectToPlayForAabUpdate(UpdateTaskImpl updateTask, String downloadUrl) {
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      updateTask.setException(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.APP_BACKGROUNDED,
              FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
      return;
    }

    Intent updateIntent = new Intent(Intent.ACTION_VIEW);
    Uri uri = Uri.parse(downloadUrl);
    updateIntent.setData(uri);
    updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    currentActivity.startActivity(updateIntent);
    updateTask.updateProgress(
        UpdateProgress.builder()
            .setApkBytesDownloaded(-1)
            .setApkFileTotalBytes(-1)
            .setUpdateStatus(UpdateStatus.REDIRECTED_TO_PLAY)
            .build());
    updateTask.setResult();
  }

  void setInstallationResult(int resultCode) {
    this.updateApkClient.setInstallationResult(resultCode);
  }

  @Nullable
  Activity getCurrentActivity() {
    synchronized (activityLock) {
      return this.currentActivity;
    }
  }

  void setCurrentActivity(@Nullable Activity activity) {
    synchronized (activityLock) {
      this.currentActivity = activity;
      this.updateApkClient.setCurrentActivity(activity);
    }
  }
}
