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

package com.google.firebase.sessions.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.sessions.SessionData
import com.google.firebase.sessions.SessionDataSerializer
import com.google.firebase.sessions.settings.SessionConfigs
import com.google.firebase.sessions.settings.SessionConfigsSerializer

/**
 * Container of instances of [DataStore] for testing.
 *
 * Note these do not pass the test scheduler to the instances, so won't work with `runCurrent`.
 */
internal object TestDataStores {
  private val appContext: Context = ApplicationProvider.getApplicationContext()

  val sessionConfigsDataStore: DataStore<SessionConfigs> by lazy {
    DataStoreFactory.create(
      serializer = SessionConfigsSerializer,
      produceFile = { appContext.dataStoreFile("sessionConfigsTestDataStore.data") },
    )
  }

  val sessionDataStore: DataStore<SessionData> by lazy {
    DataStoreFactory.create(
      serializer = SessionDataSerializer,
      produceFile = { appContext.dataStoreFile("sessionDataTestDataStore.data") },
    )
  }
}
