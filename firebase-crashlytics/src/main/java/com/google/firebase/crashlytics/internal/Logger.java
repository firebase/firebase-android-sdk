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

import com.google.firebase.logger.Logger.Level;

/** Default logger that logs to android.util.Log. */
public class Logger {
  public static final String TAG = "FirebaseCrashlytics";

  static final Logger DEFAULT_LOGGER = new Logger();

  private com.google.firebase.logger.Logger logger;

  public Logger() {
    this.logger = com.google.firebase.logger.Logger.getLogger(TAG, true, Level.INFO);
  }

  /** Returns the global {@link Logger}. */
  public static Logger getLogger() {
    return DEFAULT_LOGGER;
  }

  public void toggleLogging(boolean isLoggerEnabled) {
    this.logger = com.google.firebase.logger.Logger.getLogger(TAG, isLoggerEnabled, Level.INFO);
  }

  public void d(String text, Throwable throwable) {
    logger.debug(text, throwable);
  }

  public void v(String text, Throwable throwable) {
    logger.verbose(text, throwable);
  }

  public void i(String text, Throwable throwable) {
    logger.info(text, throwable);
  }

  public void w(String text, Throwable throwable) {
    logger.warn(text, throwable);
  }

  public void e(String text, Throwable throwable) {
    logger.error(text, throwable);
  }

  public void d(String text) {
    d(text, null);
  }

  public void v(String text) {
    v(text, null);
  }

  public void i(String text) {
    i(text, null);
  }

  public void w(String text) {
    w(text, null);
  }

  public void e(String text) {
    e(text, null);
  }
}
