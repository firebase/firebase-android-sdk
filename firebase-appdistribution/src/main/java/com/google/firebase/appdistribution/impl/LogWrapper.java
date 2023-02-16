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

import android.util.Log;
import androidx.annotation.NonNull;

/** Wrapper that handles Android logcat logging. */
class LogWrapper {
  private static final String LOG_TAG = "FirebaseAppDistribution";

  static void d(@NonNull String additionalTag, @NonNull String msg) {
    Log.d(LOG_TAG, prependTag(additionalTag, msg));
  }

  static void v(@NonNull String additionalTag, @NonNull String msg) {
    Log.v(LOG_TAG, prependTag(additionalTag, msg));
  }

  static void i(@NonNull String additionalTag, @NonNull String msg) {
    Log.i(LOG_TAG, prependTag(additionalTag, msg));
  }

  static void w(@NonNull String additionalTag, @NonNull String msg) {
    Log.w(LOG_TAG, prependTag(additionalTag, msg));
  }

  static void w(@NonNull String additionalTag, @NonNull String msg, @NonNull Throwable tr) {
    Log.w(LOG_TAG, prependTag(additionalTag, msg), tr);
  }

  static void e(@NonNull String additionalTag, @NonNull String msg) {
    Log.e(LOG_TAG, prependTag(additionalTag, msg));
  }

  static void e(@NonNull String additionalTag, @NonNull String msg, @NonNull Throwable tr) {
    Log.e(LOG_TAG, prependTag(additionalTag, msg), tr);
  }

  private static String prependTag(String tag, String msg) {
    return String.format("%s: %s", tag, msg);
  }

  private LogWrapper() {}
}
