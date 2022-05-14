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

import androidx.annotation.VisibleForTesting;

import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.util.Utils;

import java.util.Locale;

/** Firebase Performance logger that writes to logcat. */
public class AndroidLogger {

  private static volatile AndroidLogger instance;

  private final LogWrapper logWrapper;

  private boolean isLogcatEnabled = false;

  public static AndroidLogger getInstance() {
    if (instance == null) {
      synchronized (AndroidLogger.class) {
        if (instance == null) {
          instance = new AndroidLogger();
          instance.info("AndroidLogger requested %s", Utils.invoker());
        }
      }
    }

    return instance;
  }

  @VisibleForTesting
  public AndroidLogger(LogWrapper logWrapper) {
    Timer timer = new Timer();
    this.logWrapper = (logWrapper == null) ? LogWrapper.getInstance() : logWrapper;
    this.logWrapper.i("Initialized AndroidLogger in " + timer.getDurationMicros() + " us");
  }

  private AndroidLogger() {
    this(null);
  }

  public void setLogcatEnabled(boolean logcatEnabled) {
    this.isLogcatEnabled = logcatEnabled;
  }

  /**
   * Returns whether console logging is enabled by the developer or not.
   *
   * @see #setLogcatEnabled(boolean)
   */
  public boolean isLogcatEnabled() {
    return isLogcatEnabled;
  }

  /**
   * Logs a DEBUG message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param msg The string to log.
   */
  public void debug(String msg) {
    if (isLogcatEnabled) {
      logWrapper.d(msg);
    }
  }

  /**
   * Logs a DEBUG message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param format A <a href="../util/Formatter.html#syntax">format string</a>.
   * @param args Arguments referenced by the format specifiers in the format string.
   * @see String#format(Locale, String, Object...)
   */
  public void debug(String format, Object... args) {
    if (isLogcatEnabled) {
      logWrapper.d(String.format(Locale.ENGLISH, format, args));
    }
  }

  /**
   * Logs a VERBOSE message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param msg The string to log.
   */
  public void verbose(String msg) {
    if (isLogcatEnabled) {
      logWrapper.v(msg);
    }
  }

  /**
   * Logs a VERBOSE message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param format A <a href="../util/Formatter.html#syntax">format string</a>.
   * @param args Arguments referenced by the format specifiers in the format string.
   * @see String#format(Locale, String, Object...)
   */
  public void verbose(String format, Object... args) {
    if (isLogcatEnabled) {
      logWrapper.v(String.format(Locale.ENGLISH, format, args));
    }
  }

  /**
   * Logs a INFO message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param msg The string to log.
   */
  public void info(String msg) {
    if (isLogcatEnabled) {
      logWrapper.i(msg);
    }
  }

  /**
   * Logs an INFO message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param format A <a href="../util/Formatter.html#syntax">format string</a>.
   * @param args Arguments referenced by the format specifiers in the format string.
   * @see String#format(Locale, String, Object...)
   */
  public void info(String format, Object... args) {
    if (isLogcatEnabled) {
      logWrapper.i(String.format(Locale.ENGLISH, format, args));
    }
  }

  /**
   * Logs a WARN message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param msg The string to log.
   */
  public void warn(String msg) {
    if (isLogcatEnabled) {
      logWrapper.w(msg);
    }
  }

  /**
   * Logs a WARN message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param format A <a href="../util/Formatter.html#syntax">format string</a>.
   * @param args Arguments referenced by the format specifiers in the format string.
   * @see String#format(Locale, String, Object...)
   */
  public void warn(String format, Object... args) {
    if (isLogcatEnabled) {
      logWrapper.w(String.format(Locale.ENGLISH, format, args));
    }
  }

  /**
   * Logs a ERROR message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param msg The string to log.
   */
  public void error(String msg) {
    if (isLogcatEnabled) {
      logWrapper.e(msg);
    }
  }

  /**
   * Logs an ERROR message to the console (logcat) if {@link #isLogcatEnabled} is {@code true}.
   *
   * @param format A <a href="../util/Formatter.html#syntax">format string</a>.
   * @param args Arguments referenced by the format specifiers in the format string.
   * @see String#format(Locale, String, Object...)
   */
  public void error(String format, Object... args) {
    if (isLogcatEnabled) {
      logWrapper.e(String.format(Locale.ENGLISH, format, args));
    }
  }
}
