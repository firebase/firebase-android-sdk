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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.annotations.concurrent.Background;
import java.util.concurrent.Executor;

/** Class that handles storage for App Distribution SignIn persistence. */
class SignInStorage {

  private static final String SIGNIN_PREFERENCES_NAME = "FirebaseAppDistributionSignInStorage";
  private static final String SIGNIN_TAG = "firebase_app_distribution_signin";

  private final Context applicationContext;
  @Background private final Executor backgroundExecutor;

  SignInStorage(Context applicationContext, @Background Executor backgroundExecutor) {
    this.applicationContext = applicationContext;
    this.backgroundExecutor = backgroundExecutor;
  }

  Task<Void> setSignInStatus(boolean testerSignedIn) {
    return getSharedPreferences()
        .onSuccessTask(
            backgroundExecutor,
            sharedPreferences -> {
              sharedPreferences.edit().putBoolean(SIGNIN_TAG, testerSignedIn).apply();
              return null;
            });
  }

  Task<Boolean> getSignInStatus() {
    return getSharedPreferences()
        .onSuccessTask(
            backgroundExecutor,
            sharedPreferences -> Tasks.forResult(sharedPreferences.getBoolean(SIGNIN_TAG, false)));
  }

  boolean getSignInStatusBlocking() {
    return getSharedPreferencesBlocking().getBoolean(SIGNIN_TAG, false);
  }

  private Task<SharedPreferences> getSharedPreferences() {
    TaskCompletionSource<SharedPreferences> taskCompletionSource = new TaskCompletionSource<>();
    backgroundExecutor.execute(
        () -> taskCompletionSource.setResult(getSharedPreferencesBlocking()));
    return taskCompletionSource.getTask();
  }

  private SharedPreferences getSharedPreferencesBlocking() {
    // This may construct a new SharedPreferences object, which requires storage I/O
    return applicationContext.getSharedPreferences(SIGNIN_PREFERENCES_NAME, Context.MODE_PRIVATE);
  }
}
