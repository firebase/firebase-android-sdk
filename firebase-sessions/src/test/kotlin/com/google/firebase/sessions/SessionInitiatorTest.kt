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
import org.junit.Test

class SessionInitiatorTest {
  class FakeTime {
    var currentTimeMs = 0L
      private set

    fun addTimeMs(intervalMs: Long) {
      currentTimeMs += intervalMs
    }
  }

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

    // Simulate a cold start by simply constructing the SessionInitiator object
    SessionInitiator({ 0 }, sessionStartCounter::initiateSessionStart)

    assertThat(sessionStartCounter.count).isEqualTo(1)
  }

  @Test
  fun appForegrounded_largeInterval_initiatesSession() {
    val fakeTime = FakeTime()
    val sessionStartCounter = SessionStartCounter()

    val sessionInitiator =
      SessionInitiator(fakeTime::currentTimeMs, sessionStartCounter::initiateSessionStart)

    // First session on cold start
    assertThat(sessionStartCounter.count).isEqualTo(1)

    // Enough tome to initiate a new session, and then foreground
    fakeTime.addTimeMs(LARGE_INTERVAL_MS)
    sessionInitiator.appForegrounded()

    // Another session initiated
    assertThat(sessionStartCounter.count).isEqualTo(2)
  }

  @Test
  fun appForegrounded_smallInterval_doesNotInitiatesSession() {
    val fakeTime = FakeTime()
    val sessionStartCounter = SessionStartCounter()

    val sessionInitiator =
      SessionInitiator(fakeTime::currentTimeMs, sessionStartCounter::initiateSessionStart)

    // First session on cold start
    assertThat(sessionStartCounter.count).isEqualTo(1)

    // Not enough time to initiate a new session, and then foreground
    fakeTime.addTimeMs(SMALL_INTERVAL_MS)
    sessionInitiator.appForegrounded()

    // No new session
    assertThat(sessionStartCounter.count).isEqualTo(1)
  }

  @Test
  fun appForegrounded_background_foreground_largeIntervals_initiatesSessions() {
    val fakeTime = FakeTime()
    val sessionStartCounter = SessionStartCounter()

    val sessionInitiator =
      SessionInitiator(fakeTime::currentTimeMs, sessionStartCounter::initiateSessionStart)

    assertThat(sessionStartCounter.count).isEqualTo(1)

    fakeTime.addTimeMs(LARGE_INTERVAL_MS)
    sessionInitiator.appForegrounded()

    assertThat(sessionStartCounter.count).isEqualTo(2)

    sessionInitiator.appBackgrounded()
    fakeTime.addTimeMs(LARGE_INTERVAL_MS)
    sessionInitiator.appForegrounded()

    assertThat(sessionStartCounter.count).isEqualTo(3)
  }

  @Test
  fun appForegrounded_background_foreground_smallIntervals_doesNotInitiateNewSessions() {
    val fakeTime = FakeTime()
    val sessionStartCounter = SessionStartCounter()

    val sessionInitiator =
      SessionInitiator(fakeTime::currentTimeMs, sessionStartCounter::initiateSessionStart)

    // First session on cold start
    assertThat(sessionStartCounter.count).isEqualTo(1)

    fakeTime.addTimeMs(SMALL_INTERVAL_MS)
    sessionInitiator.appForegrounded()

    assertThat(sessionStartCounter.count).isEqualTo(1)

    sessionInitiator.appBackgrounded()
    fakeTime.addTimeMs(SMALL_INTERVAL_MS)
    sessionInitiator.appForegrounded()

    assertThat(sessionStartCounter.count).isEqualTo(1)

    assertThat(sessionStartCounter.count).isEqualTo(1)
  }

  companion object {
    private const val SMALL_INTERVAL_MS = 3 * 1000L // not enough time to initiate a new session
    private const val LARGE_INTERVAL_MS = 90 * 60 * 1000L // enough to initiate another session
  }
}
