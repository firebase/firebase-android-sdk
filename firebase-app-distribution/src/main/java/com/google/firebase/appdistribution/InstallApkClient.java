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

import static com.google.firebase.appdistribution.TaskUtils.safeSetTaskException;
import static com.google.firebase.appdistribution.TaskUtils.safeSetTaskResult;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

class InstallApkClient {
  private static final String TAG = "ApkInstallClient:";

  @GuardedBy("installTaskLock")
  private Activity currentActivity;

  @GuardedBy("installTaskLock")
  private TaskCompletionSource<Void> installTaskCompletionSource;

  @GuardedBy("installTaskLock")
  private boolean installInProgress = false;

  @GuardedBy("installTaskLock")
  private String installProgressApkPath = "";

  private final Object installTaskLock = new Object();

  void setCurrentActivity(@Nullable Activity activity) {
    synchronized (installTaskLock) {
      this.currentActivity = activity;

      if (installTaskCompletionSource == null
          || installTaskCompletionSource.getTask().isComplete()) {
        return;
      }

      // This ensures that if the app was backgrounded during download, installation would continue
      // after app resume
      if (activity != null
          && installInProgress
          && installProgressApkPath != null
          && !installProgressApkPath.isEmpty()) {
        startInstallActivity(installProgressApkPath, activity);
      } else {
        safeSetTaskException(
            installTaskCompletionSource,
            new FirebaseAppDistributionException(
                Constants.ErrorMessages.APK_INSTALLATION_FAILED,
                FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));
      }
    }
  }

  public Task<Void> installApk(String path) {
    synchronized (installTaskLock) {
      Activity currentActivity = this.currentActivity;
      // This ensures that we save the state of the install if the app is backgrounded during
      // APK download
      if (currentActivity == null) {
        installInProgress = true;
        installProgressApkPath = path;
      } else {
        // only start the install activity if current Activity is in the foreground
        startInstallActivity(path, currentActivity);
      }

      if (this.installTaskCompletionSource == null
          || this.installTaskCompletionSource.getTask().isComplete()) {
        this.installTaskCompletionSource = new TaskCompletionSource<>();
      }
      return installTaskCompletionSource.getTask();
    }
  }

  void startInstallActivity(String path, Activity currentActivity) {
    installInProgress = false;
    installProgressApkPath = "";
    Intent intent = new Intent(currentActivity, InstallActivity.class);
    intent.putExtra("INSTALL_PATH", path);
    currentActivity.startActivity(intent);
    LogWrapper.getInstance().v(TAG + "Prompting user with install activity ");
  }

  void setInstallationResult(int resultCode) {
    synchronized (installTaskLock) {
      if (resultCode == Activity.RESULT_OK) {
        safeSetTaskResult(installTaskCompletionSource, null);
      } else {
        safeSetTaskException(
            installTaskCompletionSource,
            new FirebaseAppDistributionException(
                Constants.ErrorMessages.APK_INSTALLATION_FAILED,
                FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));
      }
    }
  }
}
