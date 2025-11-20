/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect

/**
 * The log levels supported by [FirebaseDataConnect].
 *
 * @see FirebaseDataConnect.Companion.logLevel
 */
public enum class LogLevel {

  /** Log all messages, including detailed debug logs. */
  DEBUG,

  /** Only log warnings and errors; this is the default log level. */
  WARN,

  /** Do not log anything. */
  NONE;

  internal companion object {

    /**
     * Returns one of the two given log levels, the one that is "noisier" (that is, the one that
     * logs more).
     *
     * It can be useful to figure out which of two log levels are noisier on log level change, to
     * emit a message about the log level change at the noisiest level.
     */
    fun noisiestOf(logLevel1: LogLevel, logLevel2: LogLevel): LogLevel =
      when (logLevel1) {
        DEBUG -> DEBUG
        NONE -> logLevel2
        WARN ->
          when (logLevel2) {
            DEBUG -> DEBUG
            WARN -> WARN
            NONE -> WARN
          }
      }
  }
}
