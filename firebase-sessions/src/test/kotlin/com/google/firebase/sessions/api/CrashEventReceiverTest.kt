/*
 * Copyright 2025 Google LLC
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
import com.google.firebase.FirebaseApp
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.sessions.SessionData
import com.google.firebase.sessions.SessionDetails
import com.google.firebase.sessions.SessionFirelogPublisherImpl
import com.google.firebase.sessions.SessionGenerator
import com.google.firebase.sessions.SharedSessionRepositoryImpl
import com.google.firebase.sessions.SharedSessionRepositoryTest.Companion.SESSION_ID_INIT
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeDataStore
import com.google.firebase.sessions.testing.FakeEventGDTLogger
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import com.google.firebase.sessions.testing.FakeProcessDataManager
import com.google.firebase.sessions.testing.FakeSettingsProvider
import com.google.firebase.sessions.testing.FakeTimeProvider
import com.google.firebase.sessions.testing.FakeUuidGenerator
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CrashEventReceiverTest {
  @Test
  fun notifyCrashOccurredOnForegroundOnly() = runTest {
    // Setup
    val fakeFirebaseApp = FakeFirebaseApp()
    val fakeEventGDTLogger = FakeEventGDTLogger()
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD", "FakeAuthToken")
    val fakeTimeProvider = FakeTimeProvider()
    val sessionGenerator = SessionGenerator(fakeTimeProvider, FakeUuidGenerator())
    val localSettingsProvider = FakeSettingsProvider(true, null, 100.0)
    val remoteSettingsProvider = FakeSettingsProvider(true, null, 100.0)
    val sessionsSettings = SessionsSettings(localSettingsProvider, remoteSettingsProvider)
    val publisher =
      SessionFirelogPublisherImpl(
        fakeFirebaseApp.firebaseApp,
        firebaseInstallations,
        sessionsSettings,
        eventGDTLogger = fakeEventGDTLogger,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
      )
    val fakeDataStore =
      FakeDataStore(
        SessionData(
          SessionDetails(SESSION_ID_INIT, SESSION_ID_INIT, 0, fakeTimeProvider.currentTime().ms),
          fakeTimeProvider.currentTime(),
        )
      )
    val sharedSessionRepository =
      SharedSessionRepositoryImpl(
        sessionsSettings = sessionsSettings,
        sessionGenerator = sessionGenerator,
        sessionFirelogPublisher = publisher,
        timeProvider = fakeTimeProvider,
        sessionDataStore = fakeDataStore,
        processDataManager = FakeProcessDataManager(),
        backgroundDispatcher =
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
      )
    CrashEventReceiver.sharedSessionRepository = sharedSessionRepository

    runCurrent()

    // Process starts in the background
    assertThat(sharedSessionRepository.isInForeground).isFalse()

    // This will not update background time since the process is already in the background
    val originalBackgroundTime = fakeTimeProvider.currentTime()
    CrashEventReceiver.notifyCrashOccurred()
    assertThat(sharedSessionRepository.localSessionData.backgroundTime)
      .isEqualTo(originalBackgroundTime)

    // Wait a bit, then bring the process to foreground
    fakeTimeProvider.addInterval(31.minutes)
    sharedSessionRepository.appForeground()

    runCurrent()

    // The background time got cleared
    assertThat(sharedSessionRepository.localSessionData.backgroundTime).isNull()

    // Wait a bit, then notify of a crash
    fakeTimeProvider.addInterval(3.seconds)
    val newBackgroundTime = fakeTimeProvider.currentTime()
    CrashEventReceiver.notifyCrashOccurred()

    runCurrent()

    // Verify the background time got updated
    assertThat(sharedSessionRepository.localSessionData.backgroundTime).isEqualTo(newBackgroundTime)

    // Clean up
    fakeDataStore.close()
    FirebaseApp.clearInstancesForTest()
  }
}
