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

package com.google.firebase.database.logging;

/**
 * Private (internal) logging interface used by Firebase Database. See {@link
 * com.google.firebase.database.Config Config} for more information.
 */
public interface Logger {

  /** The log levels used by the Firebase Database library */
  enum Level {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE
  };

  /**
   * This method will be triggered whenever the library has something to log
   *
   * @param level The level of the log message
   * @param tag The component that this log message is coming from
   * @param message The message to be logged
   * @param msTimestamp The timestamp, in milliseconds, at which this message was generated
   */
  void onLogMessage(Level level, String tag, String message, long msTimestamp);

  Level getLogLevel();
}
