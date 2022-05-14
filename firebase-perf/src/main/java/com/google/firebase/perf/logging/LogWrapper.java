// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.logging;

import android.util.Log;

/** Wrapper that handles Android logcat logging. */
class LogWrapper {

  private static final String LOG_TAG = "FirebasePerformance";

  private static LogWrapper instance;

  public static synchronized LogWrapper getInstance() {
    if (instance == null) {
      instance = new LogWrapper();
    }

    return instance;
  }

  void d(String msg) {
    Log.d(LOG_TAG, msg);
  }

  void v(String msg) {
    Log.v(LOG_TAG, msg);
  }

  void i(String msg) {
    Log.i(LOG_TAG, msg);
  }

  void w(String msg) {
    Log.w(LOG_TAG, msg);
  }

  void e(String msg) {
    Log.e(LOG_TAG, msg);
  }

  private LogWrapper() {
    Log.i(LOG_TAG, "Initialized LogWrapper");
  }
}
