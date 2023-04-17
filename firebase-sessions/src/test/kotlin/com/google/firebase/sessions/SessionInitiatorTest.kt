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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeTimeProvider
import kotlin.time.Duration.Companion.minutes
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionInitiatorTest {
  class SessionStartCounter {
    var count = 0
      private set

    fun initiateSessionStart() {
      count++
    }
  }

  @Test
  fun coldStart_initiatesSession() {
    val sessionStartCounter = SessionStartCounter()
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settings = SessionsSettings(context)

    // Simulate a cold start by simply constructing the SessionInitiator object
    SessionInitiator(FakeTimeProvider(), sessionStartCounter::initiateSessionStart, settings)

    assertThat(sessionStartCounter.count).isEqualTo(1)
  }

  @Test
  fun appForegrounded_largeInterval_initiatesSession() {
    val fakeTimeProvider = FakeTimeProvider()
    val sessionStartCounter = SessionStartCounter()
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settings = SessionsSettings(context)

    val sessionInitiator =
      SessionInitiator(fakeTimeProvider, sessionStartCounter::initiateSessionStart, settings)

    // First session on cold start
    assertThat(sessionStartCounter.count).isEqualTo(1)

    // Enough tome to initiate a new session, and then foreground
    fakeTimeProvider.addInterval(LARGE_INTERVAL)
    sessionInitiator.appForegrounded()

    // Another session initiated
    assertThat(sessionStartCounter.count).isEqualTo(2)
  }

  @Test
  fun appForegrounded_smallInterval_doesNotInitiatesSession() {
    val fakeTimeProvider = FakeTimeProvider()
    val sessionStartCounter = SessionStartCounter()
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settings = SessionsSettings(context)

    val sessionInitiator =
      SessionInitiator(fakeTimeProvider, sessionStartCounter::initiateSessionStart, settings)

    // First session on cold start
    assertThat(sessionStartCounter.count).isEqualTo(1)

    // Not enough time to initiate a new session, and then foreground
    fakeTimeProvider.addInterval(SMALL_INTERVAL)
    sessionInitiator.appForegrounded()

    // No new session
    assertThat(sessionStartCounter.count).isEqualTo(1)
  }

  @Test
  fun appForegrounded_background_foreground_largeIntervals_initiatesSessions() {
    val fakeTimeProvider = FakeTimeProvider()
    val sessionStartCounter = SessionStartCounter()
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settings = SessionsSettings(context)

    val sessionInitiator =
      SessionInitiator(fakeTimeProvider, sessionStartCounter::initiateSessionStart, settings)

    assertThat(sessionStartCounter.count).isEqualTo(1)

    fakeTimeProvider.addInterval(LARGE_INTERVAL)
    sessionInitiator.appForegrounded()

    assertThat(sessionStartCounter.count).isEqualTo(2)

    sessionInitiator.appBackgrounded()
    fakeTimeProvider.addInterval(LARGE_INTERVAL)
    sessionInitiator.appForegrounded()

    assertThat(sessionStartCounter.count).isEqualTo(3)
  }

  @Test
  fun appForegrounded_background_foreground_smallIntervals_doesNotInitiateNewSessions() {
    val fakeTimeProvider = FakeTimeProvider()
    val sessionStartCounter = SessionStartCounter()
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settings = SessionsSettings(context)

    val sessionInitiator =
      SessionInitiator(fakeTimeProvider, sessionStartCounter::initiateSessionStart, settings)

    // First session on cold start
    assertThat(sessionStartCounter.count).isEqualTo(1)

    fakeTimeProvider.addInterval(SMALL_INTERVAL)
    sessionInitiator.appForegrounded()

    assertThat(sessionStartCounter.count).isEqualTo(1)

    sessionInitiator.appBackgrounded()
    fakeTimeProvider.addInterval(SMALL_INTERVAL)
    sessionInitiator.appForegrounded()

    assertThat(sessionStartCounter.count).isEqualTo(1)

    assertThat(sessionStartCounter.count).isEqualTo(1)
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  companion object {
    private val SMALL_INTERVAL = 29.minutes // not enough time to initiate a new session
    private val LARGE_INTERVAL = 31.minutes // enough to initiate another session
  }
}
