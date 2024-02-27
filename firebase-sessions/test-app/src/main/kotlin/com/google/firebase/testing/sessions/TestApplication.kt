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
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.multidex.MultiDexApplication
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber

class TestApplication : MultiDexApplication() {
  private val broadcastReceiver = CrashBroadcastReceiver()

  override fun onCreate() {
    super.onCreate()
    registerReceiver(broadcastReceiver, IntentFilter(CrashBroadcastReceiver.CRASH_ACTION))
    registerReceiver(broadcastReceiver, IntentFilter(CrashBroadcastReceiver.TOAST_ACTION))
  }

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
    val sessionSubscriber = FakeSessionSubscriber()

    init {
      FirebaseSessionsDependencies.addDependency(SessionSubscriber.Name.MATT_SAYS_HI)
      FirebaseSessionsDependencies.register(sessionSubscriber)
    }
  }
}
