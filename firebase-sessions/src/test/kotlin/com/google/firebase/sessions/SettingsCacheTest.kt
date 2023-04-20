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

package com.google.firebase.sessions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.settings.SettingsCache
import com.google.firebase.sessions.testing.FakeFirebaseApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

val SESSION_TEST_CONFIGS_NAME = "firebase_test_session_settings"
val Context.dataStore: DataStore<Preferences> by
  preferencesDataStore(name = SESSION_TEST_CONFIGS_NAME)

@RunWith(RobolectricTestRunner::class)
class SettingsCacheTest {

  @Test
  fun sessionCache_returnsEmptyCache() {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)
    assertThat(settingsCache.sessionSamplingRate()).isNull()
    assertThat(settingsCache.sessionsEnabled()).isNull()
    assertThat(settingsCache.sessionRestartTimeout()).isNull()
    assertThat(settingsCache.hasCacheExpired()).isTrue()
  }

  @Test
  fun sessionCache_SettingConfigsReturnsCachedValue() {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    runBlocking {
      settingsCache.updateSettingsEnabled(false)
      settingsCache.updateSamplingRate(0.25)
      settingsCache.updateSessionRestartTimeout(600)
      settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
      settingsCache.updateSessionCacheDuration(1000)

      assertThat(settingsCache.sessionSamplingRate()).isEqualTo(0.25)
      assertThat(settingsCache.sessionsEnabled()).isFalse()
      assertThat(settingsCache.sessionRestartTimeout()).isEqualTo(600)
      assertThat(settingsCache.hasCacheExpired()).isFalse()

      settingsCache.removeConfigs()
    }
  }

  @Test
  fun sessionCache_SettingConfigsReturnsCacheExpiredWithShortCacheDuration() {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    runBlocking {
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
  }

  @Test
  fun sessionCache_SettingConfigsReturnsCachedValueWithPartialConfigs() {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    runBlocking {
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
  }

  @Test
  fun sessionCache_SettingConfigsAllowsUpdateConfigsAndCachesValues() {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    runBlocking {
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
  }

  @Test
  fun sessionCache_SettingConfigsCleansCacheForNullValues() {
    val context = FakeFirebaseApp().firebaseApp.applicationContext
    val settingsCache = SettingsCache(context.dataStore)

    runBlocking {
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
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
