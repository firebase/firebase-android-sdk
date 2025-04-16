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

package com.google.firebase.sessions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.sessions.SessionGeneratorTest.Companion.SESSION_ID_1
import com.google.firebase.sessions.SessionGeneratorTest.Companion.SESSION_ID_2
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeDataStore
import com.google.firebase.sessions.testing.FakeEventGDTLogger
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import com.google.firebase.sessions.testing.FakeSettingsProvider
import com.google.firebase.sessions.testing.FakeTimeProvider
import com.google.firebase.sessions.testing.FakeUuidGenerator
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SharedSessionRepositoryTest {
  private val fakeFirebaseApp = FakeFirebaseApp()
  private val fakeEventGDTLogger = FakeEventGDTLogger()
  private val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD", "FakeAuthToken")
  private var fakeTimeProvider = FakeTimeProvider()
  private val sessionGenerator = SessionGenerator(fakeTimeProvider, FakeUuidGenerator())
  private var localSettingsProvider = FakeSettingsProvider(true, null, 100.0)
  private var remoteSettingsProvider = FakeSettingsProvider(true, null, 100.0)
  private var sessionsSettings = SessionsSettings(localSettingsProvider, remoteSettingsProvider)

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun initSharedSessionRepo_readFromDatastore() = runTest {
    val publisher =
      SessionFirelogPublisherImpl(
        fakeFirebaseApp.firebaseApp,
        firebaseInstallations,
        sessionsSettings,
        eventGDTLogger = fakeEventGDTLogger,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
      )
    val fakeDataStore =
      FakeDataStore<SessionData>(
        SessionData(
          SessionDetails(
            SESSION_ID_INIT,
            SESSION_ID_INIT,
            0,
            fakeTimeProvider.currentTime().ms,
          ),
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
        backgroundDispatcher =
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext
      )
    runCurrent()
    fakeDataStore.close()
    assertThat(sharedSessionRepository.localSessionData.sessionDetails.sessionId)
      .isEqualTo(SESSION_ID_INIT)
  }

  @Test
  fun initSharedSessionRepo_initException() = runTest {
    val sessionFirelogPublisher =
      SessionFirelogPublisherImpl(
        fakeFirebaseApp.firebaseApp,
        firebaseInstallations,
        sessionsSettings,
        eventGDTLogger = fakeEventGDTLogger,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
      )
    val fakeDataStore =
      FakeDataStore<SessionData>(
        SessionData(
          SessionDetails(
            SESSION_ID_INIT,
            SESSION_ID_INIT,
            0,
            fakeTimeProvider.currentTime().ms,
          ),
          fakeTimeProvider.currentTime(),
        ),
        IllegalArgumentException("Datastore init failed")
      )
    val sharedSessionRepository =
      SharedSessionRepositoryImpl(
        sessionsSettings,
        sessionGenerator,
        sessionFirelogPublisher,
        fakeTimeProvider,
        fakeDataStore,
        backgroundDispatcher =
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext
      )
    runCurrent()
    fakeDataStore.close()
    assertThat(sharedSessionRepository.localSessionData.sessionDetails.sessionId)
      .isEqualTo(SESSION_ID_1)
  }

  @Test
  fun appForegroundGenerateNewSession_updateSuccess() = runTest {
    val sessionFirelogPublisher =
      SessionFirelogPublisherImpl(
        fakeFirebaseApp.firebaseApp,
        firebaseInstallations,
        sessionsSettings,
        eventGDTLogger = fakeEventGDTLogger,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
      )
    val fakeDataStore =
      FakeDataStore<SessionData>(
        SessionData(
          SessionDetails(
            SESSION_ID_INIT,
            SESSION_ID_INIT,
            0,
            fakeTimeProvider.currentTime().ms,
          ),
          fakeTimeProvider.currentTime(),
        )
      )
    val sharedSessionRepository =
      SharedSessionRepositoryImpl(
        sessionsSettings,
        sessionGenerator,
        sessionFirelogPublisher,
        fakeTimeProvider,
        fakeDataStore,
        backgroundDispatcher =
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext
      )
    runCurrent()

    fakeTimeProvider.addInterval(20.hours)
    sharedSessionRepository.appForeground()
    runCurrent()

    assertThat(sharedSessionRepository.localSessionData.sessionDetails.sessionId)
      .isEqualTo(SESSION_ID_1)
    assertThat(sharedSessionRepository.localSessionData.backgroundTime).isNull()
    assertThat(sharedSessionRepository.previousNotificationType)
      .isEqualTo(SharedSessionRepositoryImpl.NotificationType.GENERAL)
    fakeDataStore.close()
  }

  @Test
  fun appForegroundGenerateNewSession_updateFail() = runTest {
    val sessionFirelogPublisher =
      SessionFirelogPublisherImpl(
        fakeFirebaseApp.firebaseApp,
        firebaseInstallations,
        sessionsSettings,
        eventGDTLogger = fakeEventGDTLogger,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
      )
    val fakeDataStore =
      FakeDataStore<SessionData>(
        SessionData(
          SessionDetails(
            SESSION_ID_INIT,
            SESSION_ID_INIT,
            0,
            fakeTimeProvider.currentTime().ms,
          ),
          fakeTimeProvider.currentTime(),
        ),
        IllegalArgumentException("Datastore init failed")
      )
    val sharedSessionRepository =
      SharedSessionRepositoryImpl(
        sessionsSettings,
        sessionGenerator,
        sessionFirelogPublisher,
        fakeTimeProvider,
        fakeDataStore,
        backgroundDispatcher =
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext
      )
    runCurrent()

    // set background time first
    fakeDataStore.throwOnNextUpdateData(IllegalArgumentException("Datastore update failed"))
    sharedSessionRepository.appBackground()
    runCurrent()

    // foreground update session
    fakeTimeProvider.addInterval(20.hours)
    fakeDataStore.throwOnNextUpdateData(IllegalArgumentException("Datastore update failed"))
    sharedSessionRepository.appForeground()
    runCurrent()

    // session_2 here because session_1 is failed when try to init datastore
    assertThat(sharedSessionRepository.localSessionData.sessionDetails.sessionId)
      .isEqualTo(SESSION_ID_2)
    assertThat(sharedSessionRepository.localSessionData.backgroundTime).isNull()
    assertThat(sharedSessionRepository.previousNotificationType)
      .isEqualTo(SharedSessionRepositoryImpl.NotificationType.FALLBACK)
    fakeDataStore.close()
  }

  companion object {
    const val SESSION_ID_INIT = "12345678901234546677960"
  }
}
