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

package com.google.firebase.inappmessaging.display.internal;

import android.util.Log;
import com.google.firebase.inappmessaging.display.BuildConfig;

/** @hide */
public class Logging {

  private static final String TAG = "FIAM.Display";

  /** Log a number with a label. */
  public static void logdNumber(String label, float num) {
    logd(label + ": " + num);
  }

  /** Log two numbers as a coordinate pair, with a label. */
  public static void logdPair(String label, float fst, float snd) {
    logd(label + ": (" + fst + ", " + snd + ")");
  }

  /** Log a big header, */
  public static void logdHeader(String label) {
    logd("============ " + label + " ============");
  }

  /** Log a message if in debug mode. */
  public static void logd(String message) {
    if (BuildConfig.DEBUG || Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, message);
    }
  }

  /** Log error messages normally but add a consistent TAG */
  public static void loge(String message) {
    Log.e(TAG, message);
  }

  /** Log info messages normally but add a consistent TAG */
  public static void logi(String message) {
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, message);
    }
  }
}
