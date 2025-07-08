/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.testing.sessions

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.testing.sessions.TestApplication.Companion.TAG
import com.google.firebase.testing.sessions.TestApplication.Companion.myProcessName
import kotlin.system.exitProcess

class MyServiceB : Service() {
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i(TAG, "Service B action: ${intent?.action} on process: $myProcessName")

    when (intent?.action) {
      "PING" -> ping()
      "CRASH" -> crash()
      "KILL" -> kill()
    }

    return START_STICKY
  }

  private fun ping() {
    repeat(7) { Log.i(TAG, "*** hello ***") }
    Log.i(TAG, "session id: ${TestApplication.sessionSubscriber.sessionDetails?.sessionId}")
  }

  private fun crash() {
    Log.i(TAG, "crashing")
    throw IllegalStateException("crash in service b")
  }

  private fun kill() {
    Log.i(TAG, "killing process $myProcessName")
    exitProcess(0)
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
