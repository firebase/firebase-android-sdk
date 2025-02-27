/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.logger

import android.util.Log
import androidx.annotation.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

/**
 * Common logger interface that handles Android logcat logging for Firebase SDKs.
 *
 * @hide
 */
abstract class Logger
private constructor(
  val tag: String,
  var enabled: Boolean,
  var minLevel: Level,
) {
  @JvmOverloads
  fun verbose(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    logIfAble(Level.VERBOSE, format, args, throwable = throwable)

  @JvmOverloads
  fun verbose(msg: String, throwable: Throwable? = null): Int =
    logIfAble(Level.VERBOSE, msg, throwable = throwable)

  @JvmOverloads
  fun debug(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    logIfAble(Level.DEBUG, format, args, throwable = throwable)

  @JvmOverloads
  fun debug(msg: String, throwable: Throwable? = null): Int =
    logIfAble(Level.DEBUG, msg, throwable = throwable)

  @JvmOverloads
  fun info(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    logIfAble(Level.INFO, format, args, throwable = throwable)

  @JvmOverloads
  fun info(msg: String, throwable: Throwable? = null): Int =
    logIfAble(Level.INFO, msg, throwable = throwable)

  @JvmOverloads
  fun warn(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    logIfAble(Level.WARN, format, args, throwable = throwable)

  @JvmOverloads
  fun warn(msg: String, throwable: Throwable? = null): Int =
    logIfAble(Level.WARN, msg, throwable = throwable)

  @JvmOverloads
  fun error(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    logIfAble(Level.ERROR, format, args, throwable = throwable)

  @JvmOverloads
  fun error(msg: String, throwable: Throwable? = null): Int =
    logIfAble(Level.ERROR, msg, throwable = throwable)

  /** Log if [enabled] is set and the given level is loggable. */
  private fun logIfAble(
    level: Level,
    format: String,
    args: Array<out Any?> = emptyArray(),
    throwable: Throwable?,
  ): Int =
    if (enabled && (minLevel.priority <= level.priority || Log.isLoggable(tag, level.priority))) {
      log(level, format, args, throwable = throwable)
    } else {
      0
    }

  abstract fun log(
    level: Level,
    format: String,
    args: Array<out Any?>,
    throwable: Throwable?,
  ): Int

  /** Simple wrapper around [Log]. */
  private class AndroidLogger(
    tag: String,
    enabled: Boolean,
    minLevel: Level,
  ) : Logger(tag, enabled, minLevel) {
    override fun log(
      level: Level,
      format: String,
      args: Array<out Any?>,
      throwable: Throwable?,
    ): Int {
      val msg = if (args.isEmpty()) format else String.format(format, *args)
      return when (level) {
        Level.VERBOSE -> throwable?.let { Log.v(tag, msg, throwable) } ?: Log.v(tag, msg)
        Level.DEBUG -> throwable?.let { Log.d(tag, msg, throwable) } ?: Log.d(tag, msg)
        Level.INFO -> throwable?.let { Log.i(tag, msg, throwable) } ?: Log.i(tag, msg)
        Level.WARN -> throwable?.let { Log.w(tag, msg, throwable) } ?: Log.w(tag, msg)
        Level.ERROR -> throwable?.let { Log.e(tag, msg, throwable) } ?: Log.e(tag, msg)
      }
    }
  }

  /** Fake implementation that allows recording and asserting on log messages. */
  @VisibleForTesting
  class FakeLogger
  internal constructor(
    tag: String,
    enabled: Boolean,
    minLevel: Level,
  ) : Logger(tag, enabled, minLevel) {
    private val record: MutableList<String> = ArrayList()

    override fun log(
      level: Level,
      format: String,
      args: Array<out Any?>,
      throwable: Throwable?,
    ): Int {
      val logMessage = toLogMessage(level, format, args, throwable = throwable)
      println("Log: $logMessage")
      record.add(logMessage)
      return logMessage.length
    }

    /** Clear the recorded log messages. */
    @VisibleForTesting fun clearLogMessages(): Unit = record.clear()

    /** Returns if the record has any message that contains the given [message] as a substring. */
    @VisibleForTesting
    fun hasLogMessage(message: String): Boolean = record.any { it.contains(message) }

    /** Returns if the record has any message that matches the given [predicate]. */
    @VisibleForTesting
    fun hasLogMessageThat(predicate: (String) -> Boolean): Boolean = record.any(predicate)

    /** Builds a log message from all the log params. */
    private fun toLogMessage(
      level: Level,
      format: String,
      args: Array<out Any?>,
      throwable: Throwable?,
    ): String {
      val msg = if (args.isEmpty()) format else String.format(format, *args)
      return throwable?.let { "$level $msg ${Log.getStackTraceString(throwable)}" } ?: "$level $msg"
    }
  }

  /** Log levels with each [priority] that matches [Log]. */
  enum class Level(internal val priority: Int) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
  }

  companion object {
    private val loggers = ConcurrentHashMap<String, Logger>()

    /** Gets (or creates) the single instance of [Logger] with the given [tag]. */
    @JvmStatic
    fun getLogger(
      tag: String,
      enabled: Boolean = true,
      minLevel: Level = Level.INFO,
    ): Logger = loggers.getOrPut(tag) { AndroidLogger(tag, enabled, minLevel) }

    /** Sets (or replaces) the instance of [Logger] with the given [tag] for testing purposes. */
    @VisibleForTesting
    @JvmStatic
    fun setupFakeLogger(
      tag: String,
      enabled: Boolean = true,
      minLevel: Level = Level.DEBUG,
    ): FakeLogger {
      val fakeLogger = FakeLogger(tag, enabled, minLevel)
      loggers[tag] = fakeLogger
      return fakeLogger
    }
  }
}
