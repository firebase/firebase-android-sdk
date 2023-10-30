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

import androidx.lifecycle.Lifecycle.State
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.initialize
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseSessionsTest {
  @Before
  fun setUp() {
    Firebase.initialize(ApplicationProvider.getApplicationContext())
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun coldStart_generatesSessionEventOnFirstForeground() {
    // Add a fake dependency and register it, otherwise sessions will never send.
    val fakeSessionSubscriber = FakeSessionSubscriber()
    FirebaseSessionsDependencies.register(fakeSessionSubscriber)

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity {
        // Wait for the settings to be fetched from the server.
        Thread.sleep(TIME_TO_READ_SETTINGS)
      }
      // Bring to foreground
      scenario.moveToState(State.RESUMED)
      scenario.onActivity {
        // Wait for the session start event to send.
        Thread.sleep(TIME_TO_PROPAGATE_SESSION)
        // Assert that some session was generated and sent to the subscriber.
        assertThat(fakeSessionSubscriber.sessionDetails).isNotNull()
      }
    }
  }

  @Test
  fun newSessionAfterLongBackground() {
    // Add a fake dependency and register it, otherwise sessions will never send.
    val fakeSessionSubscriber = FakeSessionSubscriber()
    FirebaseSessionsDependencies.register(fakeSessionSubscriber)

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity {
        // Wait for the settings to be fetched from the server.
        Thread.sleep(TIME_TO_READ_SETTINGS)
      }
      // Bring to foreground, and then go to background
      scenario.moveToState(State.RESUMED)
      scenario.moveToState(State.CREATED)
      // Wait for the session timeout in background
      scenario.onActivity {
        // Wait for the session start event to send.
        Thread.sleep(BACKGROUND_SESSION_TIMEOUT)
      }
      val coldStartSession = fakeSessionSubscriber.sessionDetails
      // Foreground after the timeout
      scenario.moveToState(State.RESUMED)
      scenario.onActivity {
        // Wait for the settings to be fetched from the server.
        Thread.sleep(TIME_TO_PROPAGATE_SESSION)
        // Assert that some session was generated and sent to the subscriber.
        assertThat(fakeSessionSubscriber.sessionDetails).isNotEqualTo(coldStartSession)
      }
    }
  }

  @Test
  fun noNewSessionAfterShortBackground() {
    // Add a fake dependency and register it, otherwise sessions will never send.
    val fakeSessionSubscriber = FakeSessionSubscriber()
    FirebaseSessionsDependencies.register(fakeSessionSubscriber)

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity {
        // Wait for the settings to be fetched from the server.
        Thread.sleep(TIME_TO_READ_SETTINGS)
      }
      // Bring to foreground, and then go to background
      scenario.moveToState(State.RESUMED)
      scenario.moveToState(State.CREATED)
      // Wait for a small amount of time
      scenario.onActivity {
        // Wait for the session start event to send.
        Thread.sleep(1_000L)
      }
      val coldStartSession = fakeSessionSubscriber.sessionDetails
      // Foreground after the timeout
      scenario.moveToState(State.RESUMED)
      scenario.onActivity {
        // Wait for the settings to be fetched from the server.
        Thread.sleep(TIME_TO_PROPAGATE_SESSION)
        // Assert that some session was generated and sent to the subscriber.
        assertThat(fakeSessionSubscriber.sessionDetails).isEqualTo(coldStartSession)
      }
    }
  }

  companion object {
    private const val TIME_TO_READ_SETTINGS = 60_000L
    private const val TIME_TO_PROPAGATE_SESSION = 5_000L
    // The test app has background timeout override to 5s
    private const val BACKGROUND_SESSION_TIMEOUT = 6_000L

    init {
      FirebaseSessionsDependencies.addDependency(SessionSubscriber.Name.MATT_SAYS_HI)
    }
  }

  private class FakeSessionSubscriber(
    override val isDataCollectionEnabled: Boolean = true,
    override val sessionSubscriberName: SessionSubscriber.Name = SessionSubscriber.Name.MATT_SAYS_HI
  ) : SessionSubscriber {
    var sessionDetails: SessionSubscriber.SessionDetails? = null
      private set

    override fun onSessionChanged(sessionDetails: SessionSubscriber.SessionDetails) {
      this.sessionDetails = sessionDetails
    }
  }
}
