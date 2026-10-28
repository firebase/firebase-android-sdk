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

package com.google.firebase.sessions.settings

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.testing.FakeDataStore
import com.google.firebase.sessions.testing.FakeTimeProvider
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsCacheTest {
  private val appContext: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun sessionCache_returnsEmptyCache() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val settingsCache =
      SettingsCacheImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        timeProvider = fakeTimeProvider,
        sessionConfigsDataStore =
          DataStoreFactory.create(
            serializer = SessionConfigsSerializer,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler, "blocking")),
            produceFile = { appContext.dataStoreFile("sessionConfigsDataStore.data") },
          ),
      )

    runCurrent()

    assertThat(settingsCache.sessionSamplingRate()).isNull()
    assertThat(settingsCache.sessionsEnabled()).isNull()
    assertThat(settingsCache.sessionRestartTimeout()).isNull()
    assertThat(settingsCache.hasCacheExpired()).isTrue()
  }

  @Test
  fun settingConfigsReturnsCachedValue() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val settingsCache =
      SettingsCacheImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        timeProvider = fakeTimeProvider,
        sessionConfigsDataStore =
          DataStoreFactory.create(
            serializer = SessionConfigsSerializer,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler, "blocking")),
            produceFile = { appContext.dataStoreFile("sessionConfigsDataStore.data") },
          ),
      )

    settingsCache.updateConfigs(
      SessionConfigs(
        sessionsEnabled = false,
        sessionSamplingRate = 0.25,
        sessionTimeoutSeconds = 600,
        cacheUpdatedTimeSeconds = fakeTimeProvider.currentTime().seconds,
        cacheDurationSeconds = 1000,
      )
    )

    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(600)
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.removeConfigs()
  }

  @Test
  fun settingConfigsReturnsPreviouslyStoredValue() = runTest {
    val sessionConfigsDataStore =
      DataStoreFactory.create(
        serializer = SessionConfigsSerializer,
        scope = CoroutineScope(StandardTestDispatcher(testScheduler, "blocking")),
        produceFile = { appContext.dataStoreFile("sessionConfigsDataStore.data") },
      )

    val fakeTimeProvider = FakeTimeProvider()
    val settingsCache =
      SettingsCacheImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        timeProvider = fakeTimeProvider,
        sessionConfigsDataStore = sessionConfigsDataStore,
      )

    settingsCache.updateConfigs(
      SessionConfigs(
        sessionsEnabled = false,
        sessionSamplingRate = 0.25,
        sessionTimeoutSeconds = 600,
        cacheUpdatedTimeSeconds = fakeTimeProvider.currentTime().seconds,
        cacheDurationSeconds = 1000,
      )
    )

    // Create a new instance to imitate a second app launch.
    val newSettingsCache =
      SettingsCacheImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        timeProvider = fakeTimeProvider,
        sessionConfigsDataStore = sessionConfigsDataStore,
      )

    runCurrent()

    assertThat(newSettingsCache.sessionsEnabled()).isFalse()
    assertThat(newSettingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(newSettingsCache.sessionsEnabled()).isFalse()
    assertThat(newSettingsCache.sessionRestartTimeout()).isEqualTo(600)
    assertThat(newSettingsCache.hasCacheExpired()).isFalse()

    settingsCache.removeConfigs()
    newSettingsCache.removeConfigs()
  }

  @Test
  fun settingConfigsReturnsCacheExpiredWithShortCacheDuration() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val settingsCache =
      SettingsCacheImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        timeProvider = fakeTimeProvider,
        sessionConfigsDataStore =
          DataStoreFactory.create(
            serializer = SessionConfigsSerializer,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler, "blocking")),
            produceFile = { appContext.dataStoreFile("sessionConfigsDataStore.data") },
          ),
      )

    settingsCache.updateConfigs(
      SessionConfigs(
        sessionsEnabled = false,
        sessionSamplingRate = 0.25,
        sessionTimeoutSeconds = 600,
        cacheUpdatedTimeSeconds = fakeTimeProvider.currentTime().seconds,
        cacheDurationSeconds = 0,
      )
    )

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(600)
    assertThat(settingsCache.hasCacheExpired()).isTrue()

    settingsCache.removeConfigs()
  }

  @Test
  fun settingConfigsReturnsCachedValueWithPartialConfigs() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val settingsCache =
      SettingsCacheImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        timeProvider = fakeTimeProvider,
        sessionConfigsDataStore =
          DataStoreFactory.create(
            serializer = SessionConfigsSerializer,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler, "blocking")),
            produceFile = { appContext.dataStoreFile("sessionConfigsDataStore.data") },
          ),
      )

    settingsCache.updateConfigs(
      SessionConfigs(
        sessionsEnabled = false,
        sessionSamplingRate = 0.25,
        sessionTimeoutSeconds = null,
        cacheUpdatedTimeSeconds = fakeTimeProvider.currentTime().seconds,
        cacheDurationSeconds = 1000,
      )
    )

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isNull()
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.removeConfigs()
  }

  @Test
  fun settingConfigsAllowsUpdateConfigsAndCachesValues() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val settingsCache =
      SettingsCacheImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        timeProvider = fakeTimeProvider,
        sessionConfigsDataStore =
          DataStoreFactory.create(
            serializer = SessionConfigsSerializer,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler, "blocking")),
            produceFile = { appContext.dataStoreFile("sessionConfigsDataStore.data") },
          ),
      )

    settingsCache.updateConfigs(
      SessionConfigs(
        sessionsEnabled = false,
        sessionSamplingRate = 0.25,
        sessionTimeoutSeconds = 600,
        cacheUpdatedTimeSeconds = fakeTimeProvider.currentTime().seconds,
        cacheDurationSeconds = 1000,
      )
    )

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(600)
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.updateConfigs(
      SessionConfigs(
        sessionsEnabled = true,
        sessionSamplingRate = 0.33,
        sessionTimeoutSeconds = 100,
        cacheUpdatedTimeSeconds = fakeTimeProvider.currentTime().seconds,
        cacheDurationSeconds = 0,
      )
    )

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.33)
    assertThat(settingsCache.sessionsEnabled()).isTrue()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(100)
    assertThat(settingsCache.hasCacheExpired()).isTrue()

    settingsCache.removeConfigs()
  }

  @Test
  fun settingConfigsCleansCacheForNullValues() = runTest {
    val fakeTimeProvider = FakeTimeProvider()
    val settingsCache =
      SettingsCacheImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        timeProvider = fakeTimeProvider,
        sessionConfigsDataStore =
          DataStoreFactory.create(
            serializer = SessionConfigsSerializer,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler, "blocking")),
            produceFile = { appContext.dataStoreFile("sessionConfigsDataStore.data") },
          ),
      )

    settingsCache.updateConfigs(
      SessionConfigs(
        sessionsEnabled = false,
        sessionSamplingRate = 0.25,
        sessionTimeoutSeconds = 600,
        cacheUpdatedTimeSeconds = fakeTimeProvider.currentTime().seconds,
        cacheDurationSeconds = 1000,
      )
    )

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(600)
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.updateConfigs(
      SessionConfigs(
        sessionsEnabled = null,
        sessionSamplingRate = 0.33,
        sessionTimeoutSeconds = null,
        cacheUpdatedTimeSeconds = fakeTimeProvider.currentTime().seconds,
        cacheDurationSeconds = 1000,
      )
    )

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.33)
    assertThat(settingsCache.sessionsEnabled()).isNull()
    assertThat(settingsCache.sessionRestartTimeout()).isNull()
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.removeConfigs()
  }

  @Test
  fun settingsCache_dataStoreFailsToLoad_doesNotCrash() = runTest {
    // The data store fails like this when its directory can't be created, see
    // https://github.com/firebase/firebase-android-sdk/issues/8117
    var readCount = 0
    val backingDataStore =
      FakeDataStore(
        firstValue = SessionConfigsSerializer.defaultValue,
        firstThrowable = IOException("Failed to create directory"),
      )
    val countingDataStore =
      object : androidx.datastore.core.DataStore<SessionConfigs> by backingDataStore {
        override val data =
          kotlinx.coroutines.flow.flow {
            readCount++
            backingDataStore.data.collect { emit(it) }
          }
      }
    val settingsCache =
      SettingsCacheImpl(
        backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
        timeProvider = FakeTimeProvider(),
        sessionConfigsDataStore = countingDataStore,
      )

    // Run the background collection of the data store, which fails and emits defaultValue.
    runCurrent()

    assertThat(settingsCache.sessionSamplingRate()).isNull()
    assertThat(settingsCache.sessionsEnabled()).isNull()
    assertThat(settingsCache.sessionRestartTimeout()).isNull()
    assertThat(settingsCache.hasCacheExpired()).isTrue()
    assertThat(readCount).isEqualTo(1)
  }

  @Test
  fun settingsCache_dataStoreFailsToLoad_readBeforeBackgroundCollection_returnsDefaults() =
    runTest {
      val settingsCache =
        SettingsCacheImpl(
          backgroundDispatcher = StandardTestDispatcher(testScheduler, "background"),
          timeProvider = FakeTimeProvider(),
          sessionConfigsDataStore =
            FakeDataStore(
              firstValue = SessionConfigsSerializer.defaultValue,
              firstThrowable = IOException("Failed to create directory"),
            ),
        )

      // Read before the background collection runs, so the read loads the data store itself.
      assertThat(settingsCache.sessionSamplingRate()).isNull()
      assertThat(settingsCache.sessionsEnabled()).isNull()
      assertThat(settingsCache.sessionRestartTimeout()).isNull()
      assertThat(settingsCache.hasCacheExpired()).isTrue()

      runCurrent()

      assertThat(settingsCache.sessionsEnabled()).isNull()
    }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
