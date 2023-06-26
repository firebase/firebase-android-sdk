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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsCacheTest {
  private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = SESSION_TEST_CONFIGS_NAME)

  @Test
  fun sessionCache_returnsEmptyCache() = runTest {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    assertThat(settingsCache.sessionSamplingRate()).isNull()
    assertThat(settingsCache.sessionsEnabled()).isNull()
    assertThat(settingsCache.sessionRestartTimeout()).isNull()
    assertThat(settingsCache.hasCacheExpired()).isTrue()
  }

  @Test
  fun settingConfigsReturnsCachedValue() = runTest {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    settingsCache.updateSettingsEnabled(false)
    settingsCache.updateSamplingRate(0.25)
    settingsCache.updateSessionRestartTimeout(600)
    settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
    settingsCache.updateSessionCacheDuration(1000)

    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(600)
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.removeConfigs()
  }

  @Test
  fun settingConfigsReturnsPreviouslyStoredValue() = runTest {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    settingsCache.updateSettingsEnabled(false)
    settingsCache.updateSamplingRate(0.25)
    settingsCache.updateSessionRestartTimeout(600)
    settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
    settingsCache.updateSessionCacheDuration(1000)

    // Create a new instance to imitate a second app launch.
    val newSettingsCache = SettingsCache(context.dataStore)

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
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    settingsCache.updateSettingsEnabled(false)
    settingsCache.updateSamplingRate(0.25)
    settingsCache.updateSessionRestartTimeout(600)
    settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
    settingsCache.updateSessionCacheDuration(0)

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(600)
    assertThat(settingsCache.hasCacheExpired()).isTrue()

    settingsCache.removeConfigs()
  }

  @Test
  fun settingConfigsReturnsCachedValueWithPartialConfigs() = runTest {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    settingsCache.updateSettingsEnabled(false)
    settingsCache.updateSamplingRate(0.25)
    settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
    settingsCache.updateSessionCacheDuration(1000)

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isNull()
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.removeConfigs()
  }

  @Test
  fun settingConfigsAllowsUpdateConfigsAndCachesValues() = runTest {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    settingsCache.updateSettingsEnabled(false)
    settingsCache.updateSamplingRate(0.25)
    settingsCache.updateSessionRestartTimeout(600)
    settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
    settingsCache.updateSessionCacheDuration(1000)

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(600)
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.updateSettingsEnabled(true)
    settingsCache.updateSamplingRate(0.33)
    settingsCache.updateSessionRestartTimeout(100)
    settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
    settingsCache.updateSessionCacheDuration(0)

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.33)
    assertThat(settingsCache.sessionsEnabled()).isTrue()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(100)
    assertThat(settingsCache.hasCacheExpired()).isTrue()

    settingsCache.removeConfigs()
  }

  @Test
  fun settingConfigsCleansCacheForNullValues() = runTest {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    settingsCache.updateSettingsEnabled(false)
    settingsCache.updateSamplingRate(0.25)
    settingsCache.updateSessionRestartTimeout(600)
    settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
    settingsCache.updateSessionCacheDuration(1000)

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
    assertThat(settingsCache.sessionsEnabled()).isFalse()
    assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(600)
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.updateSettingsEnabled(null)
    settingsCache.updateSamplingRate(0.33)
    settingsCache.updateSessionRestartTimeout(null)
    settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
    settingsCache.updateSessionCacheDuration(1000)

    assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.33)
    assertThat(settingsCache.sessionsEnabled()).isNull()
    assertThat(settingsCache.sessionRestartTimeout()).isNull()
    assertThat(settingsCache.hasCacheExpired()).isFalse()

    settingsCache.removeConfigs()
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  private companion object {
    const val SESSION_TEST_CONFIGS_NAME = "firebase_test_session_settings"
  }
}
