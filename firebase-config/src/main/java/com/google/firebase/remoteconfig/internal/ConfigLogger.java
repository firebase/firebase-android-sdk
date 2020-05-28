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

package com.google.firebase.remoteconfig.internal;

import android.util.Log;

/**
 * A per-namespace logger with settable log level. Defaults to level {@link Log#INFO}.
 *
 * @author danasilver
 */
public class ConfigLogger {
  private static final String TAG = "FirebaseRemoteConfig";

  private final String tag;
  private int logLevel;

  private ConfigLogger(String tag) {
    this.tag = tag;
    this.logLevel = Log.INFO;
  }

  /** Get the logger for {@param namespace} or create one if it does not already exist. */
  public static ConfigLogger getLogger() {
    return new ConfigLogger(TAG);
  }

  /**
   * Set the log level for this logger.
   *
   * @param logLevel Log level as specified by {@link Log}.
   */
  public synchronized void setLogLevel(int logLevel) {
    this.logLevel = logLevel;
  }

  /** Get the current log level for this logger. */
  public int getLogLevel() {
    return logLevel;
  }

  /** Send a {@link Log#VERBOSE} log message and log the exception. */
  public void v(String text, Throwable throwable) {
    if (canLog(Log.VERBOSE)) {
      Log.v(tag, text, throwable);
    }
  }

  /** Send a {@link Log#DEBUG} log message and log the exception. */
  public void d(String text, Throwable throwable) {
    if (canLog(Log.DEBUG)) {
      Log.d(tag, text, throwable);
    }
  }

  /** Send a {@link Log#INFO} log message and log the exception. */
  public void i(String text, Throwable throwable) {
    if (canLog(Log.INFO)) {
      Log.i(tag, text, throwable);
    }
  }

  /** Send a {@link Log#WARN} log message and log the exception. */
  public void w(String text, Throwable throwable) {
    if (canLog(Log.WARN)) {
      Log.w(tag, text, throwable);
    }
  }

  /** Send a {@link Log#ERROR} log message and log the exception. */
  public void e(String text, Throwable throwable) {
    if (canLog(Log.WARN)) {
      Log.e(tag, text, throwable);
    }
  }

  /** Send a {@link Log#VERBOSE} log message. */
  public void v(String text) {
    v(text, null);
  }

  /** Send a {@link Log#DEBUG} log message. */
  public void d(String text) {
    d(text, null);
  }

  /** Send a {@link Log#INFO} log message. */
  public void i(String text) {
    i(text, null);
  }

  /** Send a {@link Log#WARN} log message. */
  public void w(String text) {
    w(text, null);
  }

  /** Send a {@link Log#ERROR} log message. */
  public void e(String text) {
    e(text, null);
  }

  private boolean canLog(int level) {
    return logLevel <= level;
  }
}
