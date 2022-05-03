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

package com.google.firebase.appdistributionimpl;

import static com.google.firebase.appdistributionimpl.TaskUtils.safeSetTaskException;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistributionimpl.internal.InstallActivity;
import com.google.firebase.appdistributionimpl.internal.LogWrapper;

/** Class that handles installing APKs in {@link FirebaseAppDistribution}. */
class ApkInstaller {
  private static final String TAG = "ApkInstaller:";
  private final FirebaseAppDistributionLifecycleNotifier lifeCycleNotifier;

  @GuardedBy("installTaskLock")
  private TaskCompletionSource<Void> installTaskCompletionSource;

  private final Object installTaskLock = new Object();

  ApkInstaller(FirebaseAppDistributionLifecycleNotifier lifeCycleNotifier) {
    this.lifeCycleNotifier = lifeCycleNotifier;
    lifeCycleNotifier.addOnActivityStartedListener(this::onActivityStarted);
    lifeCycleNotifier.addOnActivityDestroyedListener(this::onActivityDestroyed);
  }

  ApkInstaller() {
    this(FirebaseAppDistributionLifecycleNotifier.getInstance());
  }

  void onActivityStarted(@Nullable Activity activity) {
    synchronized (installTaskLock) {
      if (installTaskCompletionSource == null
          || installTaskCompletionSource.getTask().isComplete()
          || activity == null) {
        return;
      }
    }
  }

  void onActivityDestroyed(@Nullable Activity activity) {
    if (activity instanceof InstallActivity) {
      // Since install activity is destroyed but app is still active, installation has failed /
      // cancelled.
      this.trySetInstallTaskError();
    }
  }

  Task<Void> installApk(String path, Activity currentActivity) {
    synchronized (installTaskLock) {
      startInstallActivity(path, currentActivity);

      if (this.installTaskCompletionSource == null
          || this.installTaskCompletionSource.getTask().isComplete()) {
        this.installTaskCompletionSource = new TaskCompletionSource<>();
      }
      return installTaskCompletionSource.getTask();
    }
  }

  private void startInstallActivity(String path, Activity currentActivity) {
    Intent intent = new Intent(currentActivity, InstallActivity.class);
    intent.putExtra("INSTALL_PATH", path);
    currentActivity.startActivity(intent);
    LogWrapper.getInstance().v(TAG + "Prompting tester with install activity ");
  }

  void trySetInstallTaskError() {
    synchronized (installTaskLock) {
      safeSetTaskException(
          installTaskCompletionSource,
          new FirebaseAppDistributionException(
              FirebaseAppDistributionExceptions.ErrorMessages.APK_INSTALLATION_FAILED,
              FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));
    }
  }
}
