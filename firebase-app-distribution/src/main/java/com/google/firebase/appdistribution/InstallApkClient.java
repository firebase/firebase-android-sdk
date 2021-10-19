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

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class InstallApkClient {
  private static final String TAG = "ApkInstallClient:";
  private final ExecutorService executor;

  @GuardedBy("installTaskLock")
  private TaskCompletionSource<Void> installTaskCompletionSource;

  @GuardedBy("installTaskLock")
  private boolean promptInstallOnActivityResume = false;

  @GuardedBy("installTaskLock")
  private String cachedInstallApkPath = "";

  private final Object installTaskLock = new Object();
  private FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;

  public InstallApkClient() {
    this(FirebaseAppDistributionLifecycleNotifier.getInstance());
  }

  @VisibleForTesting
  InstallApkClient(@NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier) {
    this.lifecycleNotifier = lifecycleNotifier;
    this.executor = Executors.newSingleThreadExecutor();

    this.lifecycleNotifier.addOnActivityDestroyedListener(executor, this::onActivityDestroyed);
    this.lifecycleNotifier.addOnActivityStartedListener(executor, this::onActivityStarted);
  }

  void onActivityStarted(@Nullable Activity activity) {
    synchronized (installTaskLock) {
      if (installTaskCompletionSource == null
          || installTaskCompletionSource.getTask().isComplete()
          || activity == null) {
        return;
      }
    }

    handleAppResume(activity);
  }

  @VisibleForTesting
  void onActivityDestroyed(Activity activity) {
    if (activity instanceof InstallActivity) {
      // Since install activity is destroyed but app is still active, installation has failed /
      // cancelled.
      this.trySetInstallTaskError();
    }
  }

  void handleAppResume(Activity activity) {
    // This ensures that if the app was backgrounded during download, installation would continue
    // after app resume
    synchronized (installTaskLock) {
      if (promptInstallOnActivityResume
          && cachedInstallApkPath != null
          && !cachedInstallApkPath.isEmpty()) {
        startInstallActivity(cachedInstallApkPath, activity);
      }
    }
  }

  Task<Void> installApk(String path) {
    synchronized (installTaskLock) {
      Activity currentActivity = lifecycleNotifier.getCurrentActivity();
      // This ensures that we save the state of the install if the app is backgrounded during
      // APK download
      if (currentActivity == null) {
        promptInstallOnActivityResume = true;
        cachedInstallApkPath = path;
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

  private void startInstallActivity(String path, Activity currentActivity) {
    synchronized (installTaskLock) {
      promptInstallOnActivityResume = false;
      cachedInstallApkPath = "";
    }
    Intent intent = new Intent(currentActivity, InstallActivity.class);
    intent.putExtra("INSTALL_PATH", path);
    currentActivity.startActivity(intent);
    LogWrapper.getInstance().v(TAG + "Prompting user with install activity ");
  }

  void trySetInstallTaskError() {
    synchronized (installTaskLock) {
      safeSetTaskException(
          installTaskCompletionSource,
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.APK_INSTALLATION_FAILED,
              FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));
    }
  }
}
