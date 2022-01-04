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

package com.google.firebase.app.distribution.internal;

import android.util.Log;
import androidx.annotation.NonNull;

/** Wrapper that handles Android logcat logging. */
public class LogWrapper {

  private static final String LOG_TAG = "FirebaseAppDistribution";
  private static LogWrapper instance;

  @NonNull
  public static synchronized LogWrapper getInstance() {
    if (instance == null) {
      instance = new LogWrapper();
    }

    return instance;
  }

  public void d(@NonNull String msg) {
    Log.d(LOG_TAG, msg);
  }

  public void v(@NonNull String msg) {
    Log.v(LOG_TAG, msg);
  }

  public void i(@NonNull String msg) {
    Log.i(LOG_TAG, msg);
  }

  public void w(@NonNull String msg) {
    Log.w(LOG_TAG, msg);
  }

  public void w(@NonNull String msg, @NonNull Throwable tr) {
    Log.w(LOG_TAG, msg, tr);
  }

  public void e(@NonNull String msg) {
    Log.e(LOG_TAG, msg);
  }

  public void e(@NonNull String msg, @NonNull Throwable tr) {
    Log.e(LOG_TAG, msg, tr);
  }

  private LogWrapper() {}
}
