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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;

/** Client class for updateApp functionality in {@link FirebaseAppDistribution}. */
public class UpdateAppClient {

  private final UpdateApkClient updateApkClient;
  private final InstallApkClient installApkClient;
  private final UpdateAabClient updateAabClient;
  private static final String TAG = "UpdateAppClient";

  private final Object activityLock = new Object();

  public UpdateAppClient(@NonNull FirebaseApp firebaseApp) {
    this.installApkClient = new InstallApkClient();
    this.updateApkClient = new UpdateApkClient(firebaseApp, installApkClient);
    this.updateAabClient = new UpdateAabClient();
  }

  public UpdateAppClient(UpdateApkClient updateApkClient, UpdateAabClient updateAabClient) {
    this.installApkClient = new InstallApkClient();
    this.updateApkClient = updateApkClient;
    this.updateAabClient = updateAabClient;
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
      return this.updateAabClient.updateAab(newRelease);
    } else {
      return this.updateApkClient.updateApk(newRelease, showDownloadInNotificationManager);
    }
  }

  void trySetInstallTaskError() {
    this.installApkClient.trySetInstallTaskError();
  }

  void setCurrentActivity(@Nullable Activity activity) {
    synchronized (activityLock) {
      this.updateAabClient.setCurrentActivity(activity);
      this.installApkClient.setCurrentActivity(activity);
    }
  }

  private UpdateTask getErrorUpdateTask(Exception e) {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    updateTask.setException(e);
    return updateTask;
  }

  void tryCancelAabUpdateTask() {
    this.updateAabClient.tryCancelAabUpdateTask();
  }
}
