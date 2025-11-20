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

import android.annotation.SuppressLint
import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.multidex.MultiDexApplication
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import java.io.File

class TestApplication : MultiDexApplication() {
  private val broadcastReceiver = CrashBroadcastReceiver()

  @SuppressLint("UnspecifiedRegisterReceiverFlag")
  override fun onCreate() {
    super.onCreate()
    Log.i(TAG, "TestApplication created on process: $myProcessName")
    FirebaseApp.initializeApp(this)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(
        broadcastReceiver,
        IntentFilter(CrashBroadcastReceiver.CRASH_ACTION),
        RECEIVER_NOT_EXPORTED,
      )
      registerReceiver(
        broadcastReceiver,
        IntentFilter(CrashBroadcastReceiver.TOAST_ACTION),
        RECEIVER_NOT_EXPORTED,
      )
    } else {
      registerReceiver(broadcastReceiver, IntentFilter(CrashBroadcastReceiver.CRASH_ACTION))
      registerReceiver(broadcastReceiver, IntentFilter(CrashBroadcastReceiver.TOAST_ACTION))
    }
  }

  @SuppressLint("DiscouragedApi")
  class FakeSessionSubscriber : SessionSubscriber {
    override val isDataCollectionEnabled = true
    override val sessionSubscriberName = SessionSubscriber.Name.MATT_SAYS_HI
    private val viewsToUpdate = mutableListOf<TextView>()
    private val uiHandler = Handler(Looper.getMainLooper())

    var sessionDetails: SessionSubscriber.SessionDetails? = null
      private set

    override fun onSessionChanged(sessionDetails: SessionSubscriber.SessionDetails) {
      this.sessionDetails = sessionDetails
      viewsToUpdate.forEach { updateView(it, sessionDetails.sessionId) }
    }

    fun registerView(textView: TextView) {
      viewsToUpdate.add(textView)
      updateView(textView, sessionDetails?.sessionId)
    }

    fun unregisterView(textView: TextView) {
      viewsToUpdate.remove(textView)
    }

    private fun updateView(textView: TextView, sessionId: String?) {
      uiHandler.post { textView.text = sessionId ?: "No Session Id" }
    }
  }

  @SuppressLint("DiscouragedApi")
  companion object {
    const val TAG = "SessionsTestApp"

    val sessionSubscriber = FakeSessionSubscriber()

    val myProcessName: String =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
      else
        try {
          File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
        } catch (_: Exception) {
          null
        } ?: "unknown"

    init {
      FirebaseSessionsDependencies.addDependency(SessionSubscriber.Name.MATT_SAYS_HI)
      FirebaseSessionsDependencies.register(sessionSubscriber)
    }
  }
}
