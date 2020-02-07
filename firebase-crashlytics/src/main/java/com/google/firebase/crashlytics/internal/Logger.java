// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal;

import android.util.Log;

/** Default logger that logs to android.util.Log. */
public class Logger {
  public static final String TAG = "FirebaseCrashlytics";

  static final Logger DEFAULT_LOGGER = new Logger();

  private int logLevel;

  public Logger() {
    this.logLevel = Log.INFO;
  }

  /** Returns the global {@link Logger}. */
  public static Logger getLogger() {
    return DEFAULT_LOGGER;
  }

  public boolean isLoggable(String tag, int level) {
    return logLevel <= level || Log.isLoggable(tag, level);
  }

  public void d(String tag, String text, Throwable throwable) {
    if (isLoggable(tag, Log.DEBUG)) {
      Log.d(tag, text, throwable);
    }
  }

  public void v(String tag, String text, Throwable throwable) {
    if (isLoggable(tag, Log.VERBOSE)) {
      Log.v(tag, text, throwable);
    }
  }

  public void i(String tag, String text, Throwable throwable) {
    if (isLoggable(tag, Log.INFO)) {
      Log.i(tag, text, throwable);
    }
  }

  public void w(String tag, String text, Throwable throwable) {
    if (isLoggable(tag, Log.WARN)) {
      Log.w(tag, text, throwable);
    }
  }

  public void e(String tag, String text, Throwable throwable) {
    if (isLoggable(tag, Log.ERROR)) {
      Log.e(tag, text, throwable);
    }
  }

  public void d(String tag, String text) {
    d(tag, text, null);
  }

  public void v(String tag, String text) {
    v(tag, text, null);
  }

  public void i(String tag, String text) {
    i(tag, text, null);
  }

  public void w(String tag, String text) {
    w(tag, text, null);
  }

  public void e(String tag, String text) {
    e(tag, text, null);
  }

  public void log(int priority, String tag, String msg) {
    log(priority, tag, msg, false);
  }

  public void log(int priority, String tag, String msg, boolean forceLog) {
    if (forceLog || isLoggable(tag, priority)) {
      Log.println(priority, tag, msg);
    }
  }
}
