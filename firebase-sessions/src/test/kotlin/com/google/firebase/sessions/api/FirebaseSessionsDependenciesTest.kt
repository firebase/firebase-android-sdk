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
import com.google.firebase.sessions.api.SessionSubscriber.Name.PERFORMANCE
import com.google.firebase.sessions.testing.FakeSessionSubscriber
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    // Reset any registered subscribers after each test.
    FirebaseSessionsDependencies.reset()
  }

  @Test
  fun register_dependencyAdded_canGet() {
    FirebaseSessionsDependencies.register(crashlyticsSubscriber)

    assertThat(FirebaseSessionsDependencies[CRASHLYTICS]).isEqualTo(crashlyticsSubscriber)
  }

  @Test
  fun register_alreadyRegistered_throws() {
    // Register the first time, no problem.
    FirebaseSessionsDependencies.register(crashlyticsSubscriber)

    val thrown =
      assertThrows(IllegalArgumentException::class.java) {
        // Attempt to register the same subscriber a second time.
        FirebaseSessionsDependencies.register(crashlyticsSubscriber)
      }

    assertThat(thrown).hasMessageThat().contains("Subscriber CRASHLYTICS already registered")
  }

  @Test
  fun getSubscriber_dependencyAdded_notRegistered_throws() {
    val thrown =
      assertThrows(IllegalStateException::class.java) { FirebaseSessionsDependencies[CRASHLYTICS] }

    assertThat(thrown).hasMessageThat().contains("Subscriber CRASHLYTICS has not been registered")
  }

  @Test
  fun getSubscriber_notDepended_throws() {
    val thrown =
      assertThrows(IllegalStateException::class.java) {
        // Performance was never added as a dependency.
        FirebaseSessionsDependencies[PERFORMANCE]
      }

    assertThat(thrown).hasMessageThat().contains("Cannot get dependency PERFORMANCE")
  }

  @Test
  fun addDependencyTwice_throws() {
    val thrown =
      assertThrows(IllegalArgumentException::class.java) {
        // CRASHLYTICS has already been added. Attempt to add it again.
        FirebaseSessionsDependencies.addDependency(CRASHLYTICS)
      }

    assertThat(thrown).hasMessageThat().contains("Dependency CRASHLYTICS already added")
  }

  @Test
  fun getSubscribers_waitsForRegister(): Unit = runBlocking {
    // Wait a few seconds and then register.
    launch {
      delay(2.seconds)
      FirebaseSessionsDependencies.register(crashlyticsSubscriber)
    }

    // Block until the register happens.
    val subscribers = runBlocking { FirebaseSessionsDependencies.getSubscribers() }

    assertThat(subscribers).containsExactly(CRASHLYTICS, crashlyticsSubscriber)
  }

  @Test(expected = TimeoutCancellationException::class)
  fun getSubscribers_neverRegister_waitsForever() = runTest {
    // The register never happens, wait until the timeout.
    withTimeout(2.seconds) { FirebaseSessionsDependencies.getSubscribers() }
  }

  companion object {
    init {
      // Add only Crashlytics as a dependency, not Performance.
      // This is similar to how 1P SDKs will add themselves as dependencies. Note that this
      // dependency is added for all unit tests after this class is loaded into memory.
      FirebaseSessionsDependencies.addDependency(CRASHLYTICS)
    }
  }

  private val crashlyticsSubscriber = FakeSessionSubscriber(sessionSubscriberName = CRASHLYTICS)
}
