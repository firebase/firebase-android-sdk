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
  private static final String TAG = "UpdateAppClient";

  @GuardedBy("activityLock")
  private Activity currentActivity;

  private final Object activityLock = new Object();
  private final Object updateAabLock = new Object();

  @GuardedBy("updateAabLock")
  private UpdateTaskImpl cachedAabUpdateTask;

  @GuardedBy("updateAabLock")
  private AppDistributionReleaseInternal aabReleaseInProgress;

  public UpdateAppClient(@NonNull FirebaseApp firebaseApp) {
    this.updateApkClient = new UpdateApkClient(firebaseApp);
  }

  @NonNull
  synchronized UpdateTask updateApp(
      @Nullable AppDistributionReleaseInternal newRelease,
      boolean showDownloadInNotificationManager) {

    if (newRelease == null) {
      LogWrapper.getInstance().v(TAG + "New release not found.");
      return getErrorUpdateTask(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.NOT_FOUND_ERROR, UPDATE_NOT_AVAILABLE));
    }

    if (newRelease.getDownloadUrl() == null) {
      LogWrapper.getInstance().v(TAG + "Download failed to execute");
      return getErrorUpdateTask(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.DOWNLOAD_URL_NOT_FOUND,
              FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
    }

    if (newRelease.getBinaryType() == BinaryType.AAB) {
      synchronized (updateAabLock) {
        if (cachedAabUpdateTask != null && !cachedAabUpdateTask.isComplete()) {
          return cachedAabUpdateTask;
        }

        cachedAabUpdateTask = new UpdateTaskImpl();
        aabReleaseInProgress = newRelease;
        redirectToPlayForAabUpdate(newRelease.getDownloadUrl());

        return cachedAabUpdateTask;
      }
    } else {
      return this.updateApkClient.updateApk(newRelease, showDownloadInNotificationManager);
    }
  }

  private void redirectToPlayForAabUpdate(String downloadUrl) {
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      synchronized (updateAabLock) {
        cachedAabUpdateTask.setException(
            new FirebaseAppDistributionException(
                Constants.ErrorMessages.APP_BACKGROUNDED,
                FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
        return;
      }
    }

    Intent updateIntent = new Intent(Intent.ACTION_VIEW);
    Uri uri = Uri.parse(downloadUrl);
    updateIntent.setData(uri);
    updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    currentActivity.startActivity(updateIntent);

    synchronized (updateAabLock) {
      cachedAabUpdateTask.updateProgress(
          UpdateProgress.builder()
              .setApkBytesDownloaded(-1)
              .setApkFileTotalBytes(-1)
              .setUpdateStatus(UpdateStatus.REDIRECTED_TO_PLAY)
              .build());
    }
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

  private UpdateTask getErrorUpdateTask(Exception e) {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    updateTask.setException(e);
    return updateTask;
  }

  void tryCancelAabUpdateTask() {
    synchronized (updateAabLock) {
      if (cachedAabUpdateTask != null && !cachedAabUpdateTask.isComplete()) {
        cachedAabUpdateTask.setException(
            new FirebaseAppDistributionException(
                Constants.ErrorMessages.UPDATE_CANCELED,
                FirebaseAppDistributionException.Status.INSTALLATION_CANCELED,
                ReleaseUtils.convertToAppDistributionRelease(aabReleaseInProgress)));
      }
    }
  }
}
