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

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.annotations.concurrent.Background;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Class that handles storage for App Distribution SignIn persistence. */
// TODO(b/266704696): This currently only supports one FirebaseAppDistribution instance app-wide
@Singleton
class SignInStorage {
  static final String TAG = "SignInStorage";

  @VisibleForTesting
  static final String SIGNIN_PREFERENCES_NAME = "FirebaseAppDistributionSignInStorage";

  @VisibleForTesting static final String SIGN_IN_KEY = "firebase_app_distribution_signin";

  @VisibleForTesting
  static final String DEV_MODE_SIGN_IN_KEY = "firebase_app_distribution_signin_dev_mode";

  private final Context applicationContext;
  private final DevModeDetector devModeDetector;
  @Background private final Executor backgroundExecutor;
  private SharedPreferences sharedPreferences;

  private interface SharedPreferencesFunction<T> {
    T apply(SharedPreferences sharedPreferences);
  }

  @Inject
  SignInStorage(
      Context applicationContext,
      DevModeDetector devModeDetector,
      @Background Executor backgroundExecutor) {
    this.applicationContext = applicationContext;
    this.devModeDetector = devModeDetector;
    this.backgroundExecutor = backgroundExecutor;
  }

  Task<Void> setSignInStatus(boolean testerSignedIn) {
    return applyToSharedPreferences(
        sharedPreferences -> {
          sharedPreferences.edit().putBoolean(storageKey(), testerSignedIn).apply();
          return null;
        });
  }

  Task<Boolean> getSignInStatus() {
    return applyToSharedPreferences(
        sharedPreferences -> sharedPreferences.getBoolean(storageKey(), false));
  }

  boolean getSignInStatusBlocking() {
    return getAndCacheSharedPreferences().getBoolean(storageKey(), false);
  }

  private SharedPreferences getAndCacheSharedPreferences() {
    // This may construct a new SharedPreferences object, which requires storage I/O
    sharedPreferences =
        applicationContext.getSharedPreferences(SIGNIN_PREFERENCES_NAME, Context.MODE_PRIVATE);
    return sharedPreferences;
  }

  private <T> Task<T> applyToSharedPreferences(SharedPreferencesFunction<T> func) {
    // Check nullness of sharedPreferences directly even though multiple threads could be calling
    // this function at once. This isn't a problem because: 1) once it is set it will never be reset
    // back to null, and 2) even if it is initialized twice on different threads the second call
    // will get the exact same instance.
    if (sharedPreferences != null) {
      return Tasks.forResult(func.apply(sharedPreferences));
    }
    TaskCompletionSource<T> taskCompletionSource = new TaskCompletionSource<>();
    backgroundExecutor.execute(
        () -> taskCompletionSource.setResult(func.apply(getAndCacheSharedPreferences())));
    return taskCompletionSource.getTask();
  }

  private String storageKey() {
    return devModeDetector.isDevModeEnabled() ? DEV_MODE_SIGN_IN_KEY : SIGN_IN_KEY;
  }
}
