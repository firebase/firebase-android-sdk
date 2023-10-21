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

interface Logger {
  val id: String

  fun info(message: () -> Any?)
  fun debug(message: () -> Any?)
  fun warn(message: () -> Any?)
  fun warn(e: Throwable?, message: () -> Any?)

  enum class Level {
    DEBUG,
    INFO,
    WARNING,
  }
}

@Volatile var logLevel: Logger.Level = Logger.Level.INFO

fun Logger(name: String): Logger = LoggerImpl(name = name, idInt = nextLoggerId.getAndIncrement())

private const val LOG_TAG = "FirebaseDataConnect"

// TODO: Use kotlin.concurrent.AtomicInt once kotlin-stdlib is upgraded to 1.9
// The initial value is just an arbitrary, non-zero value so that logger IDs are easily searchable
// in logs due to the "uniqueness" of their first 4 digits.
private val nextLoggerId = AtomicInteger(0x591F0000)

private fun isLogEnabledFor(level: Logger.Level) =
  when (logLevel) {
    Logger.Level.DEBUG ->
      when (level) {
        Logger.Level.DEBUG -> true
        Logger.Level.INFO -> true
        Logger.Level.WARNING -> true
      }
    Logger.Level.INFO ->
      when (level) {
        Logger.Level.DEBUG -> false
        Logger.Level.INFO -> true
        Logger.Level.WARNING -> true
      }
    Logger.Level.WARNING ->
      when (level) {
        Logger.Level.DEBUG -> false
        Logger.Level.INFO -> false
        Logger.Level.WARNING -> true
      }
  }

private fun runIfLogEnabled(level: Logger.Level, block: () -> Unit) {
  if (isLogEnabledFor(level)) {
    block()
  }
}

private class LoggerImpl(private val name: String, private val idInt: Int) : Logger {

  override val id: String by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      StringBuilder().run {
        append(name)
        append('[')
        append("0x")
        val idHexString = idInt.toString(16)
        repeat(8 - idHexString.length) { append('0') }
        append(idHexString)
        append(']')
        toString()
      }
    }

  override fun info(message: () -> Any?) =
    runIfLogEnabled(Logger.Level.INFO) { Log.i(LOG_TAG, "$id ${message()}") }

  override fun debug(message: () -> Any?) =
    runIfLogEnabled(Logger.Level.DEBUG) { Log.d(LOG_TAG, "$id ${message()}") }

  override fun warn(message: () -> Any?) = warn(null, message)

  override fun warn(e: Throwable?, message: () -> Any?) =
    runIfLogEnabled(Logger.Level.WARNING) { Log.w(LOG_TAG, "$id ${message()}", e) }
}
