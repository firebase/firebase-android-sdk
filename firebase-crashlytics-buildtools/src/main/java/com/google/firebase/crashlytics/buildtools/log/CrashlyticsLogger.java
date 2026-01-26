/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.log;

/**
 * This custom logger uses a non-standard API, because we needed to ensure consistent behaviour
 * across multiple platforms, build tools, and IDEs that had their own logging configurations.
 *
 * <p>This interface is (hopefully) a stop-gap until we implement common logging across the toolset.
 */
public interface CrashlyticsLogger {

  enum Level {
    ERROR(0),
    WARNING(1),
    INFO(2),
    DEBUG(3),
    VERBOSE(4);

    private final int value;

    Level(int value) {
      this.value = value;
    }

    /** Returns TRUE if this level is of greater or equal priority to lvl. */
    public boolean logsFor(Level lvl) {
      return this.value >= lvl.value;
    }
  }

  void setLevel(Level level);

  /*
   * Log a verbose message.
   */
  void logV(String msg);

  /*
   * Log a debug message.
   */
  void logD(String msg);

  /** Log an info message. */
  void logI(String msg);

  /** Log a warning message with optional Throwable (can be null). */
  void logW(String msg, Throwable t);

  /** Log an error message with optional Throwable (can be null). */
  void logE(String msg, Throwable t);
}
