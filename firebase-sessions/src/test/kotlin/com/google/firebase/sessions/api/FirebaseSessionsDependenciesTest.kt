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

package com.google.firebase.sessions.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.sessions.api.SessionSubscriber.Name.CRASHLYTICS
import com.google.firebase.sessions.api.SessionSubscriber.Name.MATT_SAYS_HI
import com.google.firebase.sessions.testing.FakeSessionSubscriber
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FirebaseSessionsDependenciesTest {
  @After
  fun cleanUp() {
    // Reset all dependencies after each test.
    FirebaseSessionsDependencies.reset()
  }

  @Test
  fun register_dependencyAdded_canGet() {
    val crashlyticsSubscriber = FakeSessionSubscriber(sessionSubscriberName = CRASHLYTICS)
    FirebaseSessionsDependencies.addDependency(CRASHLYTICS)
    FirebaseSessionsDependencies.register(crashlyticsSubscriber)

    assertThat(FirebaseSessionsDependencies.getSubscriber(CRASHLYTICS))
      .isEqualTo(crashlyticsSubscriber)
  }

  @Test
  fun register_alreadyRegisteredSameName_ignoresSecondSubscriber() {
    val firstSubscriber = FakeSessionSubscriber(sessionSubscriberName = CRASHLYTICS)
    val secondSubscriber = FakeSessionSubscriber(sessionSubscriberName = CRASHLYTICS)

    FirebaseSessionsDependencies.addDependency(CRASHLYTICS)

    // Register the first time, no problem.
    FirebaseSessionsDependencies.register(firstSubscriber)

    // Attempt to register a second subscriber with the same name.
    FirebaseSessionsDependencies.register(secondSubscriber)

    assertThat(FirebaseSessionsDependencies.getSubscriber(CRASHLYTICS)).isEqualTo(firstSubscriber)
  }

  @Test
  fun getSubscriber_dependencyAdded_notRegistered_throws() {
    FirebaseSessionsDependencies.addDependency(MATT_SAYS_HI)

    val thrown =
      assertThrows(IllegalStateException::class.java) {
        FirebaseSessionsDependencies.getSubscriber(MATT_SAYS_HI)
      }

    assertThat(thrown).hasMessageThat().contains("Subscriber MATT_SAYS_HI has not been registered")
  }

  @Test
  fun getSubscriber_notDepended_throws() {
    val thrown =
      assertThrows(IllegalStateException::class.java) {
        // Crashlytics was never added as a dependency.
        FirebaseSessionsDependencies.getSubscriber(CRASHLYTICS)
      }

    assertThat(thrown).hasMessageThat().contains("Cannot get dependency CRASHLYTICS")
  }

  @Test
  fun getSubscribers_waitsForRegister() = runTest {
    val crashlyticsSubscriber = FakeSessionSubscriber(sessionSubscriberName = CRASHLYTICS)
    FirebaseSessionsDependencies.addDependency(CRASHLYTICS)

    // Wait a few seconds and then register.
    launch(Dispatchers.Default) {
      delay(2.seconds)
      FirebaseSessionsDependencies.register(crashlyticsSubscriber)
    }

    // Block until the register happens.
    val subscribers = FirebaseSessionsDependencies.getRegisteredSubscribers()

    assertThat(subscribers).containsExactly(CRASHLYTICS, crashlyticsSubscriber)
  }

  @Test
  fun getSubscribers_noDependencies() = runTest {
    val subscribers = FirebaseSessionsDependencies.getRegisteredSubscribers()

    assertThat(subscribers).isEmpty()
  }

  @Test(expected = TimeoutCancellationException::class)
  fun getSubscribers_neverRegister_waitsForever() = runTest {
    FirebaseSessionsDependencies.addDependency(CRASHLYTICS)

    // The register never happens, wait until the timeout.
    withTimeout(2.seconds) { FirebaseSessionsDependencies.getRegisteredSubscribers() }
  }
}
