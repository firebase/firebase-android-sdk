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

package com.google.firebase.sessions.testing

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.google.firebase.sessions.SessionLifecycleService
import com.google.firebase.sessions.SessionLifecycleServiceBinder
import java.util.concurrent.LinkedBlockingQueue
import org.robolectric.Shadows.shadowOf

/**
 * Fake implementation of the [SessionLifecycleServiceBinder] that allows for inspecting the
 * callbacks and received messages of the service in unit tests.
 */
internal class FakeSessionLifecycleServiceBinder : SessionLifecycleServiceBinder {

  val clientCallbacks = mutableListOf<Messenger>()
  val connectionCallbacks = mutableListOf<ServiceConnection>()
  val receivedMessageCodes = LinkedBlockingQueue<Int>()
  var service = Messenger(FakeServiceHandler())

  internal inner class FakeServiceHandler() : Handler(Looper.getMainLooper()) {
    override fun handleMessage(msg: Message) {
      receivedMessageCodes.add(msg.what)
    }
  }

  override fun bindToService(callback: Messenger, serviceConnection: ServiceConnection) {
    clientCallbacks.add(callback)
    connectionCallbacks.add(serviceConnection)
  }

  fun serviceConnected() {
    connectionCallbacks.forEach { it.onServiceConnected(componentName, service.getBinder()) }
  }

  fun serviceDisconnected() {
    connectionCallbacks.forEach { it.onServiceDisconnected(componentName) }
  }

  fun broadcastSession(sessionId: String) {
    clientCallbacks.forEach { client ->
      val msgData =
        Bundle().also { it.putString(SessionLifecycleService.SESSION_UPDATE_EXTRA, sessionId) }
      client.send(
        Message.obtain(null, SessionLifecycleService.SESSION_UPDATED, 0, 0).also {
          it.data = msgData
        }
      )
    }
  }

  fun waitForAllMessages() {
    shadowOf(Looper.getMainLooper()).idle()
  }

  fun clearForTest() {
    clientCallbacks.clear()
    connectionCallbacks.clear()
    receivedMessageCodes.clear()
    service = Messenger(FakeServiceHandler())
  }

  companion object {
    val componentName =
      ComponentName("com.google.firebase.sessions.testing", "FakeSessionLifecycleServiceBinder")
  }
}
