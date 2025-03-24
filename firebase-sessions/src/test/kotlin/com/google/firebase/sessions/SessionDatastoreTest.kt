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

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SessionDatastoreTest {
  private val appContext: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun getCurrentSessionId_returnsLatest() = runTest {
    val sessionDatastore =
      SessionDatastoreImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        sessionDataStore =
          DataStoreFactory.create(
            serializer = SessionDataSerializer,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler, "blocking")),
            produceFile = { appContext.dataStoreFile("sessionDataStore.data") },
          ),
      )

    sessionDatastore.updateSessionId("sessionId1")
    sessionDatastore.updateSessionId("sessionId2")
    sessionDatastore.updateSessionId("sessionId3")

    runCurrent()

    assertThat(sessionDatastore.getCurrentSessionId()).isEqualTo("sessionId3")
  }
}
