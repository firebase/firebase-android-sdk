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

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.initialize
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.api.SessionSubscriber.SessionDetails
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeSessionLifecycleServiceBinder
import com.google.firebase.sessions.testing.FakeSessionSubscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@MediumTest
@RunWith(RobolectricTestRunner::class)
internal class SessionLifecycleClientTest {
  private lateinit var fakeService: FakeSessionLifecycleServiceBinder
  private lateinit var lifecycleServiceBinder: FakeSessionLifecycleServiceBinder

  @Before
  fun setUp() {
    val firebaseApp =
      Firebase.initialize(
        ApplicationProvider.getApplicationContext(),
        FirebaseOptions.Builder()
          .setApplicationId(FakeFirebaseApp.MOCK_APP_ID)
          .setApiKey(FakeFirebaseApp.MOCK_API_KEY)
          .setProjectId(FakeFirebaseApp.MOCK_PROJECT_ID)
          .build(),
      )
    fakeService = firebaseApp[FakeSessionLifecycleServiceBinder::class.java]
    lifecycleServiceBinder = firebaseApp[FakeSessionLifecycleServiceBinder::class.java]
  }

  @After
  fun cleanUp() {
    fakeService.serviceDisconnected()
    FirebaseApp.clearInstancesForTest()
    fakeService.clearForTest()
    FirebaseSessionsDependencies.reset()
  }

  @Test
  fun bindToService_registersCallbacks() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      addSubscriber(true, SessionSubscriber.Name.CRASHLYTICS)
      client.bindToService(lifecycleServiceBinder)

      waitForMessages()
      assertThat(fakeService.clientCallbacks).hasSize(1)
      assertThat(fakeService.connectionCallbacks).hasSize(1)
    }

  @Test
  fun onServiceConnected_sendsQueuedMessages() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      addSubscriber(true, SessionSubscriber.Name.CRASHLYTICS)
      client.bindToService(lifecycleServiceBinder)
      client.foregrounded()
      client.backgrounded()

      fakeService.serviceConnected()

      waitForMessages()
      assertThat(fakeService.receivedMessageCodes)
        .containsExactly(SessionLifecycleService.FOREGROUNDED, SessionLifecycleService.BACKGROUNDED)
    }

  @Test
  fun onServiceConnected_sendsOnlyLatestMessages() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      addSubscriber(true, SessionSubscriber.Name.CRASHLYTICS)
      client.bindToService(lifecycleServiceBinder)
      client.foregrounded()
      client.backgrounded()
      client.foregrounded()
      client.backgrounded()
      client.foregrounded()
      client.backgrounded()

      fakeService.serviceConnected()

      waitForMessages()
      assertThat(fakeService.receivedMessageCodes)
        .containsExactly(SessionLifecycleService.FOREGROUNDED, SessionLifecycleService.BACKGROUNDED)
    }

  @Test
  fun onServiceDisconnected_noMoreEventsSent() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      addSubscriber(true, SessionSubscriber.Name.CRASHLYTICS)
      client.bindToService(lifecycleServiceBinder)

      fakeService.serviceConnected()
      fakeService.serviceDisconnected()
      client.foregrounded()
      client.backgrounded()

      waitForMessages()
      assertThat(fakeService.receivedMessageCodes).isEmpty()
    }

  @Test
  fun serviceReconnection_handlesNewMessages() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      addSubscriber(true, SessionSubscriber.Name.CRASHLYTICS)
      client.bindToService(lifecycleServiceBinder)

      fakeService.serviceConnected()
      fakeService.serviceDisconnected()
      fakeService.serviceConnected()
      client.foregrounded()
      client.backgrounded()

      waitForMessages()
      assertThat(fakeService.receivedMessageCodes)
        .containsExactly(SessionLifecycleService.FOREGROUNDED, SessionLifecycleService.BACKGROUNDED)
    }

  @Test
  fun serviceReconnection_queuesOldMessages() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      addSubscriber(true, SessionSubscriber.Name.CRASHLYTICS)
      client.bindToService(lifecycleServiceBinder)

      fakeService.serviceConnected()
      fakeService.serviceDisconnected()
      client.foregrounded()
      client.backgrounded()
      fakeService.serviceConnected()

      waitForMessages()
      assertThat(fakeService.receivedMessageCodes)
        .containsExactly(SessionLifecycleService.FOREGROUNDED, SessionLifecycleService.BACKGROUNDED)
    }

  @Test
  fun doesNotSendLifecycleEventsWithoutSubscribers() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      client.bindToService(lifecycleServiceBinder)

      fakeService.serviceConnected()
      client.foregrounded()
      client.backgrounded()

      waitForMessages()
      assertThat(fakeService.receivedMessageCodes).isEmpty()
    }

  @Test
  fun doesNotSendLifecycleEventsWithoutEnabledSubscribers() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      addSubscriber(collectionEnabled = false, SessionSubscriber.Name.CRASHLYTICS)
      addSubscriber(collectionEnabled = false, SessionSubscriber.Name.MATT_SAYS_HI)
      client.bindToService(lifecycleServiceBinder)

      fakeService.serviceConnected()
      client.foregrounded()
      client.backgrounded()

      waitForMessages()
      assertThat(fakeService.receivedMessageCodes).isEmpty()
    }

  @Test
  fun sendsLifecycleEventsWhenAtLeastOneEnabledSubscriber() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      addSubscriber(collectionEnabled = true, SessionSubscriber.Name.CRASHLYTICS)
      addSubscriber(collectionEnabled = false, SessionSubscriber.Name.MATT_SAYS_HI)
      client.bindToService(lifecycleServiceBinder)

      fakeService.serviceConnected()
      client.foregrounded()
      client.backgrounded()

      waitForMessages()
      assertThat(fakeService.receivedMessageCodes).hasSize(2)
    }

  @Test
  fun handleSessionUpdate_noSubscribers() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      client.bindToService(lifecycleServiceBinder)

      fakeService.serviceConnected()
      fakeService.broadcastSession("123")

      waitForMessages()
    }

  @Test
  fun handleSessionUpdate_sendsToSubscribers() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      val crashlyticsSubscriber = addSubscriber(true, SessionSubscriber.Name.CRASHLYTICS)
      val mattSaysHiSubscriber = addSubscriber(true, SessionSubscriber.Name.MATT_SAYS_HI)
      client.bindToService(lifecycleServiceBinder)

      fakeService.serviceConnected()
      fakeService.broadcastSession("123")

      waitForMessages()
      assertThat(crashlyticsSubscriber.sessionChangedEvents).containsExactly(SessionDetails("123"))
      assertThat(mattSaysHiSubscriber.sessionChangedEvents).containsExactly(SessionDetails("123"))
    }

  @Test
  fun handleSessionUpdate_sendsToAllSubscribersAsLongAsOneIsEnabled() =
    runTest(UnconfinedTestDispatcher()) {
      val client = SessionLifecycleClient(backgroundDispatcher() + coroutineContext)
      val crashlyticsSubscriber = addSubscriber(true, SessionSubscriber.Name.CRASHLYTICS)
      val mattSaysHiSubscriber = addSubscriber(false, SessionSubscriber.Name.MATT_SAYS_HI)
      client.bindToService(lifecycleServiceBinder)

      fakeService.serviceConnected()
      fakeService.broadcastSession("123")

      waitForMessages()
      assertThat(crashlyticsSubscriber.sessionChangedEvents).containsExactly(SessionDetails("123"))
      assertThat(mattSaysHiSubscriber.sessionChangedEvents).containsExactly(SessionDetails("123"))
    }

  private fun addSubscriber(
    collectionEnabled: Boolean,
    name: SessionSubscriber.Name,
  ): FakeSessionSubscriber {
    val fakeSubscriber = FakeSessionSubscriber(collectionEnabled, sessionSubscriberName = name)
    FirebaseSessionsDependencies.addDependency(name)
    FirebaseSessionsDependencies.register(fakeSubscriber)
    return fakeSubscriber
  }

  private fun waitForMessages() {
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun backgroundDispatcher() = TestOnlyExecutors.background().asCoroutineDispatcher()
}
