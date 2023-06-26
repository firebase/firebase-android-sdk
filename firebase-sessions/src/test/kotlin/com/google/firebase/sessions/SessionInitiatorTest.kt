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
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeSettingsProvider
import com.google.firebase.sessions.testing.FakeTimeProvider
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionInitiatorTest {
  private class SessionInitiateCounter : SessionInitiateListener {
    var count = 0
      private set

    override suspend fun onInitiateSession(sessionDetails: SessionDetails) {
      count++
    }
  }

  @Test
  fun coldStart_initiatesSession() = runTest {
    val sessionInitiateCounter = SessionInitiateCounter()
    val fakeTimeProvider = FakeTimeProvider()
    val settings =
      SessionsSettings(
        localOverrideSettings = FakeSettingsProvider(),
        remoteSettings = FakeSettingsProvider(),
      )

    // Simulate a cold start by simply constructing the SessionInitiator object
    SessionInitiator(
      fakeTimeProvider,
      TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
      sessionInitiateListener = sessionInitiateCounter,
      settings,
      SessionGenerator(collectEvents = false, fakeTimeProvider),
    )

    // Run onInitiateSession suspend function.
    runCurrent()

    // Session on cold start
    assertThat(sessionInitiateCounter.count).isEqualTo(1)
  }

  @Test
  fun appForegrounded_largeInterval_initiatesSession() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val sessionInitiateCounter = SessionInitiateCounter()
    val settings =
      SessionsSettings(
        localOverrideSettings = FakeSettingsProvider(),
        remoteSettings = FakeSettingsProvider(),
      )

    val sessionInitiator =
      SessionInitiator(
        fakeTimeProvider,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
        sessionInitiateListener = sessionInitiateCounter,
        settings,
        SessionGenerator(collectEvents = false, fakeTimeProvider),
      )

    // Run onInitiateSession suspend function.
    runCurrent()

    // First session on cold start
    assertThat(sessionInitiateCounter.count).isEqualTo(1)

    // Enough tome to initiate a new session, and then foreground
    fakeTimeProvider.addInterval(LARGE_INTERVAL)
    sessionInitiator.appForegrounded()

    runCurrent()

    // Another session initiated
    assertThat(sessionInitiateCounter.count).isEqualTo(2)
  }

  @Test
  fun appForegrounded_smallInterval_doesNotInitiatesSession() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val sessionInitiateCounter = SessionInitiateCounter()
    val settings =
      SessionsSettings(
        localOverrideSettings = FakeSettingsProvider(),
        remoteSettings = FakeSettingsProvider(),
      )

    val sessionInitiator =
      SessionInitiator(
        fakeTimeProvider,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
        sessionInitiateListener = sessionInitiateCounter,
        settings,
        SessionGenerator(collectEvents = false, fakeTimeProvider),
      )

    // Run onInitiateSession suspend function.
    runCurrent()

    // First session on cold start
    assertThat(sessionInitiateCounter.count).isEqualTo(1)

    // Not enough time to initiate a new session, and then foreground
    fakeTimeProvider.addInterval(SMALL_INTERVAL)
    sessionInitiator.appForegrounded()

    runCurrent()

    // No new session
    assertThat(sessionInitiateCounter.count).isEqualTo(1)
  }

  @Test
  fun appForegrounded_background_foreground_largeIntervals_initiatesSessions() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val sessionInitiateCounter = SessionInitiateCounter()
    val settings =
      SessionsSettings(
        localOverrideSettings = FakeSettingsProvider(),
        remoteSettings = FakeSettingsProvider(),
      )

    val sessionInitiator =
      SessionInitiator(
        fakeTimeProvider,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
        sessionInitiateListener = sessionInitiateCounter,
        settings,
        SessionGenerator(collectEvents = false, fakeTimeProvider),
      )

    // Run onInitiateSession suspend function.
    runCurrent()

    assertThat(sessionInitiateCounter.count).isEqualTo(1)

    fakeTimeProvider.addInterval(LARGE_INTERVAL)
    sessionInitiator.appForegrounded()

    runCurrent()

    assertThat(sessionInitiateCounter.count).isEqualTo(2)

    sessionInitiator.appBackgrounded()
    fakeTimeProvider.addInterval(LARGE_INTERVAL)
    sessionInitiator.appForegrounded()

    runCurrent()

    assertThat(sessionInitiateCounter.count).isEqualTo(3)
  }

  @Test
  fun appForegrounded_background_foreground_smallIntervals_doesNotInitiateNewSessions() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val sessionInitiateCounter = SessionInitiateCounter()
    val settings =
      SessionsSettings(
        localOverrideSettings = FakeSettingsProvider(),
        remoteSettings = FakeSettingsProvider(),
      )

    val sessionInitiator =
      SessionInitiator(
        fakeTimeProvider,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
        sessionInitiateListener = sessionInitiateCounter,
        settings,
        SessionGenerator(collectEvents = false, fakeTimeProvider),
      )

    // Run onInitiateSession suspend function.
    runCurrent()

    // First session on cold start
    assertThat(sessionInitiateCounter.count).isEqualTo(1)

    fakeTimeProvider.addInterval(SMALL_INTERVAL)
    sessionInitiator.appForegrounded()

    runCurrent()

    assertThat(sessionInitiateCounter.count).isEqualTo(1)

    sessionInitiator.appBackgrounded()
    fakeTimeProvider.addInterval(SMALL_INTERVAL)
    sessionInitiator.appForegrounded()

    runCurrent()

    assertThat(sessionInitiateCounter.count).isEqualTo(1)
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
