// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck.internal.util;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Default logger that logs to android.util.Log. Taken from Firebase Crashlytics. */
public class Logger {
  public static final String TAG = "FirebaseAppCheck";

  static final Logger DEFAULT_LOGGER = new Logger(TAG);

  private final String tag;
  private int logLevel;

  public Logger(@NonNull String tag) {
    this.tag = tag;
    this.logLevel = Log.INFO;
  }

  /** Returns the global {@link Logger}. */
  @NonNull
  public static Logger getLogger() {
    return DEFAULT_LOGGER;
  }

  private boolean canLog(int level) {
    return logLevel <= level || Log.isLoggable(tag, level);
  }

  public void d(@NonNull String text, @Nullable Throwable throwable) {
    if (canLog(Log.DEBUG)) {
      Log.d(tag, text, throwable);
    }
  }

  public void v(@NonNull String text, @Nullable Throwable throwable) {
    if (canLog(Log.VERBOSE)) {
      Log.v(tag, text, throwable);
    }
  }

  public void i(@NonNull String text, @Nullable Throwable throwable) {
    if (canLog(Log.INFO)) {
      Log.i(tag, text, throwable);
    }
  }

  public void w(@NonNull String text, @Nullable Throwable throwable) {
    if (canLog(Log.WARN)) {
      Log.w(tag, text, throwable);
    }
  }

  public void e(@NonNull String text, @Nullable Throwable throwable) {
    if (canLog(Log.ERROR)) {
      Log.e(tag, text, throwable);
    }
  }

  public void d(@NonNull String text) {
    d(text, null);
  }

  public void v(@NonNull String text) {
    v(text, null);
  }

  public void i(@NonNull String text) {
    i(text, null);
  }

  public void w(@NonNull String text) {
    w(text, null);
  }

  public void e(@NonNull String text) {
    e(text, null);
  }

  public void log(int priority, @NonNull String msg) {
    log(priority, msg, false);
  }

  public void log(int priority, @NonNull String msg, boolean forceLog) {
    if (forceLog || canLog(priority)) {
      Log.println(priority, tag, msg);
    }
  }
}
