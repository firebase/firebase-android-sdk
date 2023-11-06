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

import android.os.Bundle
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.sessions.SessionDataStoreConfigs
import com.google.firebase.sessions.SessionEvents
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import com.google.firebase.sessions.testing.FakeRemoteConfigFetcher
import com.google.firebase.sessions.testing.FakeSettingsProvider
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionsSettingsTest {

  @Test
  fun sessionSettings_fetchDefaults() {
    val sessionsSettings =
      SessionsSettings(
        localOverrideSettings = FakeSettingsProvider(),
        remoteSettings = FakeSettingsProvider(),
      )

    assertThat(sessionsSettings.sessionsEnabled).isTrue()
    assertThat(sessionsSettings.samplingRate).isEqualTo(1.0)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(30.minutes)
  }

  @Test
  fun sessionSettings_fetchOverridingConfigs() {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", false)
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    metadata.putInt("firebase_sessions_sessions_restart_timeout", 180)
    val firebaseApp = FakeFirebaseApp(metadata).firebaseApp
    val context = firebaseApp.applicationContext

    val sessionsSettings =
      SessionsSettings(
        localOverrideSettings = LocalOverrideSettings(context),
        remoteSettings = FakeSettingsProvider(),
      )

    assertThat(sessionsSettings.sessionsEnabled).isFalse()
    assertThat(sessionsSettings.samplingRate).isEqualTo(0.5)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(3.minutes)
  }

  @Test
  fun sessionSettings_fetchOverridingConfigsOnlyWhenPresent() = runTest {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", false)
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    val firebaseApp = FakeFirebaseApp(metadata).firebaseApp
    val context = firebaseApp.applicationContext

    val sessionsSettings =
      SessionsSettings(
        localOverrideSettings = LocalOverrideSettings(context),
        remoteSettings = FakeSettingsProvider(),
      )

    runCurrent()

    assertThat(sessionsSettings.sessionsEnabled).isFalse()
    assertThat(sessionsSettings.samplingRate).isEqualTo(0.5)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(30.minutes)
  }

  @Test
  fun sessionSettings_remoteSettingsOverrideDefaultsWhenPresent() =
    runTest(UnconfinedTestDispatcher()) {
      val firebaseApp = FakeFirebaseApp().firebaseApp
      val context = firebaseApp.applicationContext
      val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
      val fakeFetcher = FakeRemoteConfigFetcher(JSONObject(VALID_RESPONSE))

      val remoteSettings =
        RemoteSettings(
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
          firebaseInstallations,
          SessionEvents.getApplicationInfo(firebaseApp),
          fakeFetcher,
          dataStore =
            PreferenceDataStoreFactory.create(
              scope = this,
              produceFile = { context.preferencesDataStoreFile(SESSION_TEST_CONFIGS_NAME) },
            ),
        )

      val sessionsSettings =
        SessionsSettings(
          localOverrideSettings = LocalOverrideSettings(context),
          remoteSettings = remoteSettings,
        )

      sessionsSettings.updateSettings()

      runCurrent()

      assertThat(sessionsSettings.sessionsEnabled).isFalse()
      assertThat(sessionsSettings.samplingRate).isEqualTo(0.75)
      assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(40.minutes)

      remoteSettings.clearCachedSettings()
    }

  @Test
  fun sessionSettings_manifestOverridesRemoteSettingsAndDefaultsWhenPresent() =
    runTest(UnconfinedTestDispatcher()) {
      val metadata = Bundle()
      metadata.putBoolean("firebase_sessions_enabled", true)
      metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
      metadata.putInt("firebase_sessions_sessions_restart_timeout", 180)
      val firebaseApp = FakeFirebaseApp(metadata).firebaseApp
      val context = firebaseApp.applicationContext
      val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
      val fakeFetcher = FakeRemoteConfigFetcher(JSONObject(VALID_RESPONSE))

      val remoteSettings =
        RemoteSettings(
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
          firebaseInstallations,
          SessionEvents.getApplicationInfo(firebaseApp),
          fakeFetcher,
          dataStore =
            PreferenceDataStoreFactory.create(
              scope = this,
              produceFile = { context.preferencesDataStoreFile(SESSION_TEST_CONFIGS_NAME) },
            ),
        )

      val sessionsSettings =
        SessionsSettings(
          localOverrideSettings = LocalOverrideSettings(context),
          remoteSettings = remoteSettings,
        )

      sessionsSettings.updateSettings()

      runCurrent()

      assertThat(sessionsSettings.sessionsEnabled).isTrue()
      assertThat(sessionsSettings.samplingRate).isEqualTo(0.5)
      assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(3.minutes)

      remoteSettings.clearCachedSettings()
    }

  @Test
  fun sessionSettings_invalidManifestConfigsDoNotOverride() =
    runTest(UnconfinedTestDispatcher()) {
      val metadata = Bundle()
      metadata.putBoolean("firebase_sessions_enabled", false)
      metadata.putDouble("firebase_sessions_sampling_rate", -0.2) // Invalid
      metadata.putInt("firebase_sessions_sessions_restart_timeout", -2) // Invalid
      val firebaseApp = FakeFirebaseApp(metadata).firebaseApp
      val context = firebaseApp.applicationContext
      val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
      val fakeFetcher = FakeRemoteConfigFetcher()
      val invalidResponse =
        VALID_RESPONSE.replace(
          "\"sampling_rate\":0.75,",
          "\"sampling_rate\":1.2,", // Invalid
        )
      fakeFetcher.responseJSONObject = JSONObject(invalidResponse)

      val remoteSettings =
        RemoteSettings(
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
          firebaseInstallations,
          SessionEvents.getApplicationInfo(firebaseApp),
          fakeFetcher,
          dataStore =
            PreferenceDataStoreFactory.create(
              scope = this,
              produceFile = { context.preferencesDataStoreFile(SESSION_TEST_CONFIGS_NAME) },
            ),
        )

      val sessionsSettings =
        SessionsSettings(
          localOverrideSettings = LocalOverrideSettings(context),
          remoteSettings = remoteSettings,
        )

      sessionsSettings.updateSettings()

      runCurrent()

      assertThat(sessionsSettings.sessionsEnabled).isFalse() // Manifest
      assertThat(sessionsSettings.samplingRate).isEqualTo(1.0) // SDK default
      assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(40.minutes) // Remote

      remoteSettings.clearCachedSettings()
    }

  @Test
  fun sessionSettings_dataStorePreferencesNameIsFilenameSafe() {
    assertThat(SessionDataStoreConfigs.SESSIONS_CONFIG_NAME).matches("^[a-zA-Z0-9_=]+\$")
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  private companion object {
    const val SESSION_TEST_CONFIGS_NAME = "firebase_session_settings_test"

    const val VALID_RESPONSE =
      """
      {
        "settings_version":3,
        "cache_duration":86400,
        "features":{
        },
        "app":{
        },
        "fabric":{
        },
        "app_quality":{
          "sessions_enabled":false,
          "sampling_rate":0.75,
          "session_timeout_seconds":2400
        }
      }
    """
  }
}
