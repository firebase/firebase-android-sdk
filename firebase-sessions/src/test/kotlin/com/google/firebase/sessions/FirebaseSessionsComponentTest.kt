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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.settings.SessionConfigsSerializer
import com.google.firebase.sessions.testing.FakeFirebaseApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FirebaseSessionsComponentTest {

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun sessionConfigsDataStore_afterFirebaseAppDeleted_canBeRecreated() = runTest {
    val blockingDispatcher = StandardTestDispatcher(testScheduler)

    val firebaseApp = FakeFirebaseApp().firebaseApp
    val dataStore =
      FirebaseSessionsComponent.MainModule.sessionConfigsDataStore(
        firebaseApp.applicationContext,
        firebaseApp,
        blockingDispatcher,
      )
    dataStore.updateData { it.copy(sessionsEnabled = false) }

    firebaseApp.delete()
    advanceUntilIdle()

    val newFirebaseApp = FakeFirebaseApp().firebaseApp
    val newDataStore =
      FirebaseSessionsComponent.MainModule.sessionConfigsDataStore(
        newFirebaseApp.applicationContext,
        newFirebaseApp,
        blockingDispatcher,
      )

    assertThat(newDataStore.data.first())
      .isEqualTo(SessionConfigsSerializer.defaultValue.copy(sessionsEnabled = false))

    newFirebaseApp.delete()
  }
}
