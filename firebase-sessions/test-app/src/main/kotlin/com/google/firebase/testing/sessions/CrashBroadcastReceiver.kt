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

package com.google.firebase.testing.sessions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class CrashBroadcastReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    Log.i(TAG, "Received intent: $intent")
    when (intent.action) {
      CRASH_ACTION -> crash(context)
      TOAST_ACTION -> toast(context)
    }
  }

  fun crash(context: Context) {
    Toast.makeText(context, "KABOOM!", Toast.LENGTH_LONG).show()
    throw RuntimeException("CRASH_BROADCAST")
  }

  fun toast(context: Context) {
    Toast.makeText(context, "Cheers!", Toast.LENGTH_LONG).show()
  }

  companion object {
    val TAG = "CrashBroadcastReceiver"
    val CRASH_ACTION = "com.google.firebase.testing.sessions.CrashBroadcastReceiver.CRASH_ACTION"
    val TOAST_ACTION = "com.google.firebase.testing.sessions.CrashBroadcastReceiver.TOAST_ACTION"
  }
}
