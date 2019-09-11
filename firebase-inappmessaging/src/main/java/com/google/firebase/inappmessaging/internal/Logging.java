// Copyright 2018 Google LLC
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

package com.google.firebase.inappmessaging.internal;

import android.util.Log;
import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.inappmessaging.BuildConfig;

/**
 * Helper class to facilitate logging. To enable debug logging in production run `adb shell setprop
 * log.tag.FIAM.Headless DEBUG`
 *
 * @hide
 */
public class Logging {

  @VisibleForTesting public static final String TAG = "FIAM.Headless";

  /** Log a message if in debug mode or debug is loggable. */
  public static void logd(String message) {
    if (BuildConfig.DEBUG || Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, message);
    }
  }

  /** Log info messages if they are loggable. */
  public static void logi(String message) {
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, message);
    }
  }

  /** Log error messages normally but add a consistent TAG */
  public static void loge(String message) {
    Log.e(TAG, message);
  }

  /** Log warning messages normally but add a consistent TAG */
  public static void logw(String message) {
    Log.w(TAG, message);
  }
}
