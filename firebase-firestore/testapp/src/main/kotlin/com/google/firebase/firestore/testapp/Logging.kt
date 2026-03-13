/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.firestore.testapp

import android.util.Log
import com.google.firebase.firestore.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow

interface Logger {

  val displayName: String

  enum class Level {
    DEBUG,
    INFO,
    WARN,
    ERROR,
  }

  fun log(level: Level, message: String, exception: Throwable?)
}

object Logging {

  val level: MutableStateFlow<Logger.Level> =
    MutableStateFlow(if (BuildConfig.DEBUG) Logger.Level.DEBUG else Logger.Level.WARN)
}

val Logger.Level.isEnabled: Boolean
  get() =
    when (this) {
      Logger.Level.DEBUG ->
        when (Logging.level.value) {
          Logger.Level.DEBUG -> true
          else -> false
        }
      Logger.Level.INFO ->
        when (Logging.level.value) {
          Logger.Level.INFO,
          Logger.Level.DEBUG -> true
          else -> false
        }
      Logger.Level.WARN ->
        when (Logging.level.value) {
          Logger.Level.WARN,
          Logger.Level.INFO,
          Logger.Level.DEBUG -> true
          else -> false
        }
      Logger.Level.ERROR -> true
    }

inline fun Logger.logIfEnabled(
  level: Logger.Level,
  exception: Throwable? = null,
  message: () -> String
) {
  if (level.isEnabled) {
    log(level, message(), exception)
  }
}

inline fun Logger.debug(exception: Throwable? = null, message: () -> String) =
  logIfEnabled(Logger.Level.DEBUG, exception, message)

inline fun Logger.info(exception: Throwable? = null, message: () -> String) =
  logIfEnabled(Logger.Level.INFO, exception, message)

inline fun Logger.warn(exception: Throwable? = null, message: () -> String) =
  logIfEnabled(Logger.Level.WARN, exception, message)

inline fun Logger.error(exception: Throwable? = null, message: () -> String) =
  logIfEnabled(Logger.Level.ERROR, exception, message)

private class LoggerImpl(override val displayName: String) : Logger {

  constructor(id: String, name: String) : this("$name[$id]")

  override fun log(level: Logger.Level, message: String, exception: Throwable?) {
    if (!level.isEnabled) {
      return
    }
    when (level) {
      Logger.Level.DEBUG ->
        if (exception === null) {
          Log.d(TAG, message)
        } else {
          Log.d(TAG, message, exception)
        }
      Logger.Level.INFO ->
        if (exception === null) {
          Log.i(TAG, message)
        } else {
          Log.i(TAG, message, exception)
        }
      Logger.Level.WARN ->
        if (exception === null) {
          Log.w(TAG, message)
        } else {
          Log.w(TAG, message, exception)
        }
      Logger.Level.ERROR ->
        if (exception === null) {
          Log.e(TAG, message)
        } else {
          Log.e(TAG, message, exception)
        }
    }
  }

  override fun toString() = "Logger($displayName)"
}

private const val TAG = "FirestoreTestApp"

fun Logger(name: String): Logger = LoggerImpl(randomAlphanumericId(), name)
