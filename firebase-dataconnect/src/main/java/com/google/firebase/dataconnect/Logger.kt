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

interface Logger {

  val name: String
  var level: Level

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

internal class LoggerImpl(override val name: String, override var level: Logger.Level) : Logger {

  override fun info(message: () -> Any?) {
    if (level == Logger.Level.INFO || level == Logger.Level.DEBUG) {
      Log.i("FirebaseDataConnect", message().toString())
    }
  }

  override fun debug(message: () -> Any?) {
    if (level == Logger.Level.DEBUG) {
      Log.d("FirebaseDataConnect", message().toString())
    }
  }

  override fun warn(message: () -> Any?) = warn(null, message)

  override fun warn(e: Throwable?, message: () -> Any?) {
    Log.w("FirebaseDataConnect", message().toString(), e)
  }
}
