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

class MyServiceA : Service() {
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i(TAG, "Service A action: ${intent?.action} on process: $myProcessName")

    // Send actions from adb shell this way, so it can start the process if needed:
    // am startservice --user 0 -n com.google.firebase.testing.sessions/.MyServiceA -a PING
    when (intent?.action) {
      "PING" -> ping()
      "CRASH" -> crash()
      "KILL" -> kill()
      "SESSION" -> session()
    }

    return START_STICKY
  }

  private fun ping() {
    repeat(7) { Log.i(TAG, "*** pong ***") }
  }

  private fun crash() {
    Log.i(TAG, "crashing")
    throw IndexOutOfBoundsException("crash service a")
  }

  private fun kill() {
    Log.i(TAG, "killing process $myProcessName")
    exitProcess(0)
  }

  private fun session() {
    Log.i(
      TAG,
      "service a, session id: ${TestApplication.sessionSubscriber.sessionDetails?.sessionId}",
    )
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
