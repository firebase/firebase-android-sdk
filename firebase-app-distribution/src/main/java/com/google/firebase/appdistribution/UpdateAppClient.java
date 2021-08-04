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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;

/** Client class for updateApp functionality in {@link FirebaseAppDistribution}. */
public class UpdateAppClient {

  private TaskCompletionSource<Void> updateAppTaskCompletionSource = null;
  private CancellationTokenSource updateAppCancellationSource;
  private UpdateTask updateTask;

  private FirebaseApp firebaseApp;
  private UpdateApkClient updateApkClient;

  public UpdateAppClient(@NonNull FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
    this.updateApkClient = new UpdateApkClient(firebaseApp);
  }

  @NonNull
  public UpdateTask getUpdateTask(
      @NonNull AppDistributionReleaseInternal latestRelease, @NonNull Activity currentActivity)
      throws FirebaseAppDistributionException {

    if (this.updateTask != null && !updateTask.isComplete()) {
      return this.updateTask;
    }

    if (latestRelease.getBinaryType() == BinaryType.AAB) {
      this.updateTask = redirectToPlayForAabUpdate(latestRelease.getDownloadUrl(), currentActivity);
    } else {
      this.updateTask =
          this.updateApkClient.updateApk(latestRelease.getDownloadUrl(), currentActivity);
    }

    return this.updateTask;
  }

  private UpdateTaskImpl redirectToPlayForAabUpdate(String downloadUrl, Activity currentActivity)
      throws FirebaseAppDistributionException {
    if (downloadUrl == null) {
      throw new FirebaseAppDistributionException(
          "Download URL not found.", FirebaseAppDistributionException.Status.NETWORK_FAILURE);
    }
    Intent updateIntent = new Intent(Intent.ACTION_VIEW);
    Uri uri = Uri.parse(downloadUrl);
    updateIntent.setData(uri);
    updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    currentActivity.startActivity(updateIntent);
    UpdateTaskImpl updateTask = new UpdateTaskImpl(Tasks.forResult(null));
    updateTask.updateProgress(
        UpdateProgress.builder()
            .setApkBytesDownloaded(-1)
            .setApkFileTotalBytes(-1)
            .setUpdateStatus(UpdateStatus.REDIRECTED_TO_PLAY)
            .build());
    return updateTask;
  }

  void setInstallationResult(int resultCode) {
    this.updateApkClient.setInstallationResult(resultCode);
    updateAppTaskCompletionSource.setResult(null);
  }
}
