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
import android.content.Context;
import android.content.Intent;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.appdistribution.internal.InstallActivity;
import com.google.firebase.appdistribution.internal.LogWrapper;

/** Class that handles installing APKs in {@link FirebaseAppDistribution}. */
class ApkInstaller {
  private static final String TAG = "ApkInstallClient:";
  private final FirebaseAppDistributionLifecycleNotifier lifeCycleNotifier;
  private final Context applicationContext;

  @GuardedBy("installTaskLock")
  private TaskCompletionSource<Void> installTaskCompletionSource;

  @GuardedBy("installTaskLock")
  private boolean promptInstallOnActivityResume = false;

  @GuardedBy("installTaskLock")
  private String cachedInstallApkPath = "";

  private final Object installTaskLock = new Object();

  ApkInstaller(
      Context applicationContext, FirebaseAppDistributionLifecycleNotifier lifeCycleNotifier) {
    this.applicationContext = applicationContext;
    this.lifeCycleNotifier = lifeCycleNotifier;
    lifeCycleNotifier.addOnActivityStartedListener(this::onActivityStarted);
    lifeCycleNotifier.addOnActivityDestroyedListener(this::onActivityDestroyed);
  }

  ApkInstaller(Context applicationContext) {
    this(applicationContext, FirebaseAppDistributionLifecycleNotifier.getInstance());
  }

  void onActivityStarted(@Nullable Activity activity) {
    synchronized (installTaskLock) {
      if (installTaskCompletionSource == null
          || installTaskCompletionSource.getTask().isComplete()
          || activity == null) {
        return;
      }
    }

    handleAppResume();
  }

  void onActivityDestroyed(@Nullable Activity activity) {
    if (activity instanceof InstallActivity) {
      // Since install activity is destroyed but app is still active, installation has failed /
      // cancelled.
      this.trySetInstallTaskError();
    }
  }

  void handleAppResume() {
    // This ensures that if the app was backgrounded during download, installation would continue
    // after app resume
    synchronized (installTaskLock) {
      if (promptInstallOnActivityResume
          && cachedInstallApkPath != null
          && !cachedInstallApkPath.isEmpty()) {
        startInstallActivity(cachedInstallApkPath);
      }
    }
  }

  Task<Void> installApk(String path) {
    synchronized (installTaskLock) {
      Activity currentActivity = lifeCycleNotifier.getCurrentActivity();
      // This ensures that we save the state of the install if the app is backgrounded during
      // APK download
      if (currentActivity == null) {
        promptInstallOnActivityResume = true;
        cachedInstallApkPath = path;
      } else {
        // only start the install activity if current Activity is in the foreground
        startInstallActivity(path);
      }

      if (this.installTaskCompletionSource == null
          || this.installTaskCompletionSource.getTask().isComplete()) {
        this.installTaskCompletionSource = new TaskCompletionSource<>();
      }
      return installTaskCompletionSource.getTask();
    }
  }

  private void startInstallActivity(String path) {
    synchronized (installTaskLock) {
      promptInstallOnActivityResume = false;
      cachedInstallApkPath = "";
    }
    Intent intent = new Intent(applicationContext, InstallActivity.class);
    intent.putExtra("INSTALL_PATH", path);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    applicationContext.startActivity(intent);
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
