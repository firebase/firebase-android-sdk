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

package com.google.firebase.appdistribution.impl;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Class that handles installing APKs in {@link FirebaseAppDistribution}. */
@Singleton // Only one update should happen at a time, even across FirebaseAppDistribution instances
class ApkInstaller {
  private static final String TAG = "ApkInstaller";

  private final TaskCompletionSourceCache<Void> installTaskCompletionSourceCache;

  @Inject
  ApkInstaller(
      FirebaseAppDistributionLifecycleNotifier lifeCycleNotifier,
      @Lightweight Executor lightweightExecutor) {
    this.installTaskCompletionSourceCache = new TaskCompletionSourceCache<>(lightweightExecutor);
    lifeCycleNotifier.addOnActivityDestroyedListener(this::onActivityDestroyed);
  }

  void onActivityDestroyed(@Nullable Activity activity) {
    if (activity instanceof InstallActivity) {
      // Since install activity is destroyed but app is still active, installation has failed /
      // cancelled.
      installTaskCompletionSourceCache.setException(
          new FirebaseAppDistributionException(
              ErrorMessages.APK_INSTALLATION_FAILED,
              FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));
    }
  }

  Task<Void> installApk(String path, Activity currentActivity) {
    return installTaskCompletionSourceCache.getOrCreateTaskFromCompletionSource(
        () -> {
          startInstallActivity(path, currentActivity);
          return new TaskCompletionSource<>();
        });
  }

  private void startInstallActivity(String path, Activity currentActivity) {
    Intent intent = new Intent(currentActivity, InstallActivity.class);
    intent.putExtra("INSTALL_PATH", path);
    currentActivity.startActivity(intent);
    LogWrapper.v(TAG, "Prompting tester with install activity");
  }
}
