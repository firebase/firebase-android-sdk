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

package com.google.firebase.dataconnect.core

import android.util.Log
import com.google.firebase.dataconnect.BuildConfig
import com.google.firebase.dataconnect.DataConnectLogging
import com.google.firebase.dataconnect.DataConnectLogging.LogLevelStackFrame
import com.google.firebase.dataconnect.LogLevel
import com.google.firebase.dataconnect.core.LoggerGlobals.LOG_TAG
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.util.nextAlphanumericString
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object DataConnectLoggingImpl : DataConnectLogging {

  private val _level = MutableStateFlow(LogLevel.WARN)

  override val flow = _level.asSharedFlow()

  override var level by _level::value

  override fun push(level: LogLevel): LogLevelStackFrame {
    while (true) {
      val originalLevel = _level.value
      if (_level.compareAndSet(originalLevel, level)) {
        return LogLevelStackFrameImpl(originalLevel, level)
      }
    }
  }

  private data class LogLevelStackFrameImpl(
    override val originalLevel: LogLevel,
    override val newLevel: LogLevel,
  ) : LogLevelStackFrame {

    private val closedMutex = Mutex()
    private var closed = false

    override fun close() = runBlocking { suspendingClose() }

    override suspend fun suspendingClose() =
      closedMutex.withLock {
        if (!closed) {
          level = originalLevel
          closed = true
        }
      }
  }
}

internal interface Logger {
  val name: String
  val id: String
  val nameWithId: String

  fun log(exception: Throwable?, level: LogLevel, message: String)
}

private class LoggerImpl(override val name: String) : Logger {

  override val id: String by
    lazy(LazyThreadSafetyMode.PUBLICATION) { "lgr" + Random.nextAlphanumericString(length = 10) }

  override val nameWithId: String by lazy(LazyThreadSafetyMode.PUBLICATION) { "$name[id=$id]" }

  override fun log(exception: Throwable?, level: LogLevel, message: String) {
    val fullMessage = "[${BuildConfig.VERSION_NAME}] $nameWithId $message"
    when (level) {
      LogLevel.DEBUG -> Log.d(LOG_TAG, fullMessage, exception)
      LogLevel.WARN -> Log.w(LOG_TAG, fullMessage, exception)
      LogLevel.NONE -> {}
    }
  }
}

/**
 * Holder for "global" functions related to [Logger].
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * LoggerKt Java class with public visibility, which pollutes the public API. Using an "internal"
 * object, instead, to gather together the top-level functions avoids this public API pollution.
 */
internal object LoggerGlobals {
  const val LOG_TAG = "FirebaseDataConnect"

  private val logLevel: LogLevel by DataConnectLoggingImpl::level

  inline fun Logger.debug(message: () -> Any?) {
    if (logLevel <= LogLevel.DEBUG) debug("${message()}")
  }

  fun Logger.debug(message: String) {
    if (logLevel <= LogLevel.DEBUG) log(null, LogLevel.DEBUG, message)
  }

  inline fun Logger.warn(message: () -> Any?) {
    if (logLevel <= LogLevel.WARN) warn("${message()}")
  }

  inline fun Logger.warn(exception: Throwable?, message: () -> Any?) {
    if (logLevel <= LogLevel.WARN) warn(exception, "${message()}")
  }

  fun Logger.warn(message: String) {
    warn(null, message)
  }

  fun Logger.warn(exception: Throwable?, message: String) {
    if (logLevel <= LogLevel.WARN) log(exception, LogLevel.WARN, message)
  }

  fun Logger(name: String): Logger = LoggerImpl(name)
}
