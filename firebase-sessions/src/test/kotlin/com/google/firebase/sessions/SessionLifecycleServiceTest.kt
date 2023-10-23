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

package com.google.firebase.sessions

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.initialize
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirelogPublisher
import com.google.firebase.sessions.testing.FakeSessionDatastore
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED
import org.robolectric.shadows.ShadowSystemClock

@OptIn(ExperimentalCoroutinesApi::class)
@MediumTest
@LooperMode(PAUSED)
@RunWith(RobolectricTestRunner::class)
internal class SessionLifecycleServiceTest {

  lateinit var service: ServiceController<SessionLifecycleService>
  lateinit var firebaseApp: FirebaseApp

  data class CallbackMessage(val code: Int, val sessionId: String?)

  internal inner class TestCallbackHandler(looper: Looper = Looper.getMainLooper()) :
    Handler(looper) {
    val callbackMessages = ArrayList<CallbackMessage>()

    override fun handleMessage(msg: Message) {
      callbackMessages.add(CallbackMessage(msg.what, getSessionId(msg)))
    }
  }

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    firebaseApp =
      Firebase.initialize(
        ApplicationProvider.getApplicationContext(),
        FirebaseOptions.Builder()
          .setApplicationId(FakeFirebaseApp.MOCK_APP_ID)
          .setApiKey(FakeFirebaseApp.MOCK_API_KEY)
          .setProjectId(FakeFirebaseApp.MOCK_PROJECT_ID)
          .build()
      )
    service = createService()
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun binding_noCallbackOnInitialBindingWhenNoneStored() {
    val client = TestCallbackHandler()

    bindToService(client)

    waitForAllMessages()
    assertThat(client.callbackMessages).isEmpty()
  }

  @Test
  fun binding_callbackOnInitialBindWhenSessionIdSet() {
    val client = TestCallbackHandler()
    firebaseApp.get(FakeSessionDatastore::class.java).updateSessionId("123")

    bindToService(client)

    waitForAllMessages()
    assertThat(client.callbackMessages).hasSize(1)
    val msg = client.callbackMessages.first()
    assertThat(msg.code).isEqualTo(SessionLifecycleService.SESSION_UPDATED)
    assertThat(msg.sessionId).isNotEmpty()
    // We should not send stored session IDs to firelog
    assertThat(getUploadedSessions()).isEmpty()
  }

  @Test
  fun foregrounding_startsSessionOnFirstForegrounding() {
    val client = TestCallbackHandler()
    val messenger = bindToService(client)

    messenger.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))

    waitForAllMessages()
    assertThat(client.callbackMessages).hasSize(1)
    assertThat(getUploadedSessions()).hasSize(1)
    assertThat(client.callbackMessages.first().code)
      .isEqualTo(SessionLifecycleService.SESSION_UPDATED)
    assertThat(client.callbackMessages.first().sessionId).isNotEmpty()
    assertThat(getUploadedSessions().first().sessionId)
      .isEqualTo(client.callbackMessages.first().sessionId)
  }

  @Test
  fun foregrounding_onlyOneSessionOnMultipleForegroundings() {
    val client = TestCallbackHandler()
    val messenger = bindToService(client)

    messenger.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))
    messenger.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))
    messenger.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))

    waitForAllMessages()
    assertThat(client.callbackMessages).hasSize(1)
    assertThat(getUploadedSessions()).hasSize(1)
  }

  @Test
  fun foregrounding_newSessionAfterLongDelay() {
    val client = TestCallbackHandler()
    val messenger = bindToService(client)

    messenger.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))
    ShadowSystemClock.advanceBy(Duration.ofMinutes(31))
    messenger.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))

    waitForAllMessages()
    assertThat(client.callbackMessages).hasSize(2)
    assertThat(getUploadedSessions()).hasSize(2)
    assertThat(client.callbackMessages.first().sessionId)
      .isNotEqualTo(client.callbackMessages.last().sessionId)
    assertThat(getUploadedSessions().first().sessionId)
      .isEqualTo(client.callbackMessages.first().sessionId)
    assertThat(getUploadedSessions().last().sessionId)
      .isEqualTo(client.callbackMessages.last().sessionId)
  }

  @Test
  fun sendsSessionsToMultipleClients() {
    val client1 = TestCallbackHandler()
    val client2 = TestCallbackHandler()
    val client3 = TestCallbackHandler()
    bindToService(client1)
    val messenger = bindToService(client2)
    bindToService(client3)
    waitForAllMessages()

    messenger.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))

    waitForAllMessages()
    assertThat(client1.callbackMessages).hasSize(1)
    assertThat(client1.callbackMessages).isEqualTo(client2.callbackMessages)
    assertThat(client1.callbackMessages).isEqualTo(client3.callbackMessages)
    assertThat(getUploadedSessions()).hasSize(1)
  }

  @Test
  fun onlyOneSessionForMultipleClientsForegrounding() {
    val client1 = TestCallbackHandler()
    val client2 = TestCallbackHandler()
    val client3 = TestCallbackHandler()
    val messenger1 = bindToService(client1)
    val messenger2 = bindToService(client2)
    val messenger3 = bindToService(client3)
    waitForAllMessages()

    messenger1.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))
    messenger1.send(Message.obtain(null, SessionLifecycleService.BACKGROUNDED, 0, 0))
    messenger2.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))
    messenger2.send(Message.obtain(null, SessionLifecycleService.BACKGROUNDED, 0, 0))
    messenger3.send(Message.obtain(null, SessionLifecycleService.FOREGROUNDED, 0, 0))

    waitForAllMessages()
    assertThat(client1.callbackMessages).hasSize(1)
    assertThat(client1.callbackMessages).isEqualTo(client2.callbackMessages)
    assertThat(client1.callbackMessages).isEqualTo(client3.callbackMessages)
    assertThat(getUploadedSessions()).hasSize(1)
  }

  @Test
  fun backgrounding_doesNotStartSession() {
    val client = TestCallbackHandler()
    val messenger = bindToService(client)

    messenger.send(Message.obtain(null, SessionLifecycleService.BACKGROUNDED, 0, 0))

    waitForAllMessages()
    assertThat(client.callbackMessages).isEmpty()
    assertThat(getUploadedSessions()).isEmpty()
  }

  private fun bindToService(client: TestCallbackHandler): Messenger {
    return Messenger(service.get()?.onBind(createServiceLaunchIntent(client)))
  }

  private fun createServiceLaunchIntent(client: TestCallbackHandler) =
    Intent(
        ApplicationProvider.getApplicationContext<Context>(),
        SessionLifecycleService::class.java
      )
      .apply { putExtra(SessionLifecycleService.CLIENT_CALLBACK_MESSENGER, Messenger(client)) }

  private fun createService() =
    Robolectric.buildService(SessionLifecycleService::class.java).create()

  private fun waitForAllMessages() {
    shadowOf(service.get()?.handlerThread?.getLooper()).idle()
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun getUploadedSessions() =
    firebaseApp.get(FakeFirelogPublisher::class.java).loggedSessions

  private fun getSessionId(msg: Message) =
    msg.data?.getString(SessionLifecycleService.SESSION_UPDATE_EXTRA)
}
