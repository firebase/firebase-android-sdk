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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/**
 * Tests that [FirebaseSessionsComponent] provides data stores that aren't created until they're
 * used, so they aren't created on the main thread while Firebase initializes. See
 * https://github.com/firebase/firebase-android-sdk/issues/8615
 */
@RunWith(AndroidJUnit4::class)
class FirebaseSessionsComponentTest {
  private val appContext: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun sessionConfigsDataStore_isCreatedOnFirstRead() = runTest {
    val dataStore =
      FirebaseSessionsComponent.MainModule.sessionConfigsDataStore(
        appContext,
        blockingDispatcher = StandardTestDispatcher(testScheduler, "blocking"),
      )

    assertThat(dataStore).isInstanceOf(LazyDataStore::class.java)
    assertThat((dataStore as LazyDataStore<SessionConfigs>).isInitialized()).isFalse()

    assertThat(dataStore.data.first()).isEqualTo(SessionConfigsSerializer.defaultValue)
    assertThat(dataStore.isInitialized()).isTrue()
  }

  @Test
  fun sessionDataStore_isCreatedOnFirstRead() = runTest {
    val dataStore =
      FirebaseSessionsComponent.MainModule.sessionDataStore(
        appContext,
        blockingDispatcher = StandardTestDispatcher(testScheduler, "blocking"),
        sessionDataSerializer =
          SessionDataSerializer(SessionGenerator(FakeTimeProvider(), FakeUuidGenerator())),
      )

    assertThat(dataStore).isInstanceOf(LazyDataStore::class.java)
    assertThat((dataStore as LazyDataStore<SessionData>).isInitialized()).isFalse()

    assertThat(dataStore.data.first().sessionDetails.sessionId).isNotEmpty()
    assertThat(dataStore.isInitialized()).isTrue()
  }
}
