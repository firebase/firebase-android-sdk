// Copyright 2023 Google LLC
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
package com.google.firebase.dataconnect

import android.util.Log
import kotlin.random.Random

enum class LogLevel {
  DEBUG,
  INFO,
  WARNING,
  ERROR,
}

@Volatile var logLevel: LogLevel = LogLevel.INFO

internal interface Logger {
  val name: String
  val idz: String
  val nameWithId: String

  fun log(exception: Throwable?, level: LogLevel, message: String)
}

internal inline fun Logger.debug(message: () -> Any?) {
  if (logLevel <= LogLevel.DEBUG) debug("${message()}")
}

internal fun Logger.debug(message: String) {
  if (logLevel <= LogLevel.DEBUG) log(null, LogLevel.DEBUG, message)
}

internal inline fun Logger.info(message: () -> Any?) {
  if (logLevel <= LogLevel.INFO) info("${message()}")
}

internal fun Logger.info(message: String) {
  if (logLevel <= LogLevel.INFO) log(null, LogLevel.INFO, message)
}

internal inline fun Logger.warn(message: () -> Any?) {
  if (logLevel <= LogLevel.WARNING) warn("${message()}")
}

internal inline fun Logger.warn(exception: Throwable?, message: () -> Any?) {
  if (logLevel <= LogLevel.WARNING) warn(exception, "${message()}")
}

internal fun Logger.warn(message: String) {
  warn(null, message)
}

internal fun Logger.warn(exception: Throwable?, message: String) {
  if (logLevel <= LogLevel.WARNING) log(exception, LogLevel.WARNING, message)
}

internal fun Logger.error(message: String) {
  error(null, message)
}

internal fun Logger.error(exception: Throwable?, message: String) {
  log(exception, LogLevel.ERROR, message)
}

internal fun Logger(name: String): Logger = LoggerImpl(name)

private const val LOG_TAG = "FirebaseDataConnect"

private class LoggerImpl(override val name: String) : Logger {

  override val idz: String by
    lazy(LazyThreadSafetyMode.PUBLICATION) { Random.nextAlphanumericString() }

  override val nameWithId: String by lazy(LazyThreadSafetyMode.PUBLICATION) { "$name[id=$idz]" }

  override fun log(exception: Throwable?, level: LogLevel, message: String) {
    val fullMessage = "$nameWithId $message"
    when (level) {
      LogLevel.DEBUG -> Log.d(LOG_TAG, fullMessage, exception)
      LogLevel.INFO -> Log.i(LOG_TAG, fullMessage, exception)
      LogLevel.WARNING -> Log.w(LOG_TAG, fullMessage, exception)
      LogLevel.ERROR -> Log.e(LOG_TAG, fullMessage, exception)
    }
  }
}
