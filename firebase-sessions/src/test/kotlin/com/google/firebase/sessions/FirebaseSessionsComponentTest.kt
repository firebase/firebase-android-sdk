/*
 * Copyright 2026 Google LLC
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

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.firebase.sessions.settings.SessionConfigs
import com.google.firebase.sessions.settings.SessionConfigsSerializer
import com.google.firebase.sessions.testing.FakeTimeProvider
import com.google.firebase.sessions.testing.FakeUuidGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseSessionsComponentTest {
  private val appContext: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun sessionConfigsDataStore_readsDefaultThenPersistsUpdate() = runTest {
    val dataStore =
      FirebaseSessionsComponent.MainModule.sessionConfigsDataStore(
        appContext = appContext,
        blockingDispatcher = StandardTestDispatcher(testScheduler, "blocking"),
      )

    assertThat(dataStore.data.first()).isEqualTo(SessionConfigsSerializer.defaultValue)

    val sessionConfigs =
      SessionConfigs(
        sessionsEnabled = true,
        sessionSamplingRate = 0.5,
        sessionTimeoutSeconds = 60,
        cacheDurationSeconds = 3_600,
        cacheUpdatedTimeSeconds = 1_234L,
      )
    dataStore.updateData { sessionConfigs }

    assertThat(dataStore.data.first()).isEqualTo(sessionConfigs)
    assertThat(appContext.dataStoreFile("firebaseSessions/sessionConfigsDataStore.data").exists())
      .isTrue()
  }

  @Test
  fun sessionDataStore_readsDefaultThenPersistsUpdate() = runTest {
    val timeProvider = FakeTimeProvider()
    val sessionGenerator = SessionGenerator(timeProvider, FakeUuidGenerator())
    val dataStore =
      FirebaseSessionsComponent.MainModule.sessionDataStore(
        appContext = appContext,
        blockingDispatcher = StandardTestDispatcher(testScheduler, "blocking"),
        sessionDataSerializer = SessionDataSerializer(sessionGenerator),
      )

    val initialSessionData = dataStore.data.first()
    assertThat(initialSessionData.backgroundTime).isNull()

    val sessionData = initialSessionData.copy(backgroundTime = timeProvider.currentTime())
    dataStore.updateData { sessionData }

    assertThat(dataStore.data.first()).isEqualTo(sessionData)
    assertThat(appContext.dataStoreFile("firebaseSessions/sessionDataStore.data").exists()).isTrue()
  }
}
