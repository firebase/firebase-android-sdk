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
import java.util.concurrent.atomic.AtomicInteger

enum class LogLevel {
  DEBUG,
  INFO,
  WARNING,
}

@Volatile var logLevel: LogLevel = LogLevel.INFO

internal interface Logger {
  val id: String

  fun info(message: () -> Any?)
  fun debug(message: () -> Any?)
  fun warn(message: () -> Any?)
  fun warn(e: Throwable?, message: () -> Any?)
}

internal fun Logger(name: String): Logger =
  LoggerImpl(name = name, idInt = nextLoggerId.getAndIncrement())

private const val LOG_TAG = "FirebaseDataConnect"

// TODO: Use kotlin.concurrent.AtomicInt once kotlin-stdlib is upgraded to 1.9
// The initial value is just an arbitrary, non-zero value so that logger IDs are easily searchable
// in logs due to the "uniqueness" of their first 4 digits.
private val nextLoggerId = AtomicInteger(0x591F0000)

private fun isLogEnabledFor(level: LogLevel) =
  when (logLevel) {
    LogLevel.DEBUG ->
      when (level) {
        LogLevel.DEBUG -> true
        LogLevel.INFO -> true
        LogLevel.WARNING -> true
      }
    LogLevel.INFO ->
      when (level) {
        LogLevel.DEBUG -> false
        LogLevel.INFO -> true
        LogLevel.WARNING -> true
      }
    LogLevel.WARNING ->
      when (level) {
        LogLevel.DEBUG -> false
        LogLevel.INFO -> false
        LogLevel.WARNING -> true
      }
  }

private fun runIfLogEnabled(level: LogLevel, block: () -> Unit) {
  if (isLogEnabledFor(level)) {
    block()
  }
}

private class LoggerImpl(private val name: String, private val idInt: Int) : Logger {

  override val id: String by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      buildString {
        append(name)
        append('[')
        append("0x")
        val idHexString = idInt.toString(16)
        repeat(8 - idHexString.length) { append('0') }
        append(idHexString)
        append(']')
      }
    }

  override fun info(message: () -> Any?) =
    runIfLogEnabled(LogLevel.INFO) { Log.i(LOG_TAG, "$id ${message()}") }

  override fun debug(message: () -> Any?) =
    runIfLogEnabled(LogLevel.DEBUG) { Log.d(LOG_TAG, "$id ${message()}") }

  override fun warn(message: () -> Any?) = warn(null, message)

  override fun warn(e: Throwable?, message: () -> Any?) =
    runIfLogEnabled(LogLevel.WARNING) { Log.w(LOG_TAG, "$id ${message()}", e) }
}
