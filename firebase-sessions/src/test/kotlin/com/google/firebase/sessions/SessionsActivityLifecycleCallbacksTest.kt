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

import android.app.Activity
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.initialize
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeSessionLifecycleServiceBinder
import com.google.firebase.sessions.testing.FakeSessionSubscriber
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
internal class SessionsActivityLifecycleCallbacksTest {
  private lateinit var fakeService: FakeSessionLifecycleServiceBinder
  private lateinit var lifecycleServiceBinder: FakeSessionLifecycleServiceBinder
  private val fakeActivity = Activity()

  @Before
  fun setUp() {
    // Reset the state of the SessionsActivityLifecycleCallbacks object.
    SessionsActivityLifecycleCallbacks.hasPendingForeground = false
    SessionsActivityLifecycleCallbacks.lifecycleClient = null

    FirebaseSessionsDependencies.addDependency(SessionSubscriber.Name.MATT_SAYS_HI)
    FirebaseSessionsDependencies.register(
      FakeSessionSubscriber(
        isDataCollectionEnabled = true,
        sessionSubscriberName = SessionSubscriber.Name.MATT_SAYS_HI,
      )
    )

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
  fun hasPendingForeground_thenSetLifecycleClient_callsBackgrounded() =
    runTest(UnconfinedTestDispatcher()) {
      val lifecycleClient = SessionLifecycleClient(backgroundDispatcher(coroutineContext))

      // Activity comes to foreground before the lifecycle client was set due to no settings.
      SessionsActivityLifecycleCallbacks.onActivityResumed(fakeActivity)

      // Settings fetched and set the lifecycle client.
      lifecycleClient.bindToService(lifecycleServiceBinder)
      fakeService.serviceConnected()
      SessionsActivityLifecycleCallbacks.lifecycleClient = lifecycleClient

      // Assert lifecycleClient.foregrounded got called.
      waitForMessages()
      assertThat(fakeService.receivedMessageCodes).hasSize(1)
    }

  @Test
  fun noPendingForeground_thenSetLifecycleClient_doesNotCallBackgrounded() =
    runTest(UnconfinedTestDispatcher()) {
      val lifecycleClient = SessionLifecycleClient(backgroundDispatcher(coroutineContext))

      // Set lifecycle client before any foreground happened.
      lifecycleClient.bindToService(lifecycleServiceBinder)
      fakeService.serviceConnected()
      SessionsActivityLifecycleCallbacks.lifecycleClient = lifecycleClient

      // Assert lifecycleClient.foregrounded did not get called.
      waitForMessages()
      assertThat(fakeService.receivedMessageCodes).hasSize(0)

      // Activity comes to foreground.
      SessionsActivityLifecycleCallbacks.onActivityResumed(fakeActivity)

      // Assert lifecycleClient.foregrounded did get called.
      waitForMessages()
      assertThat(fakeService.receivedMessageCodes).hasSize(1)
    }

  private fun waitForMessages() = Shadows.shadowOf(Looper.getMainLooper()).idle()

  private fun backgroundDispatcher(coroutineContext: CoroutineContext) =
    TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext
}
