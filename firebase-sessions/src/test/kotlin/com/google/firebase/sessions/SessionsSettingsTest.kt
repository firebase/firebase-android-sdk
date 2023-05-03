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

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.settings.LocalOverrideSettings
import com.google.firebase.sessions.settings.RemoteSettings
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import com.google.firebase.sessions.testing.FakeRemoteConfigFetcher
import kotlin.time.Duration.Companion.minutes
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionsSettingsTest {

  @Test
  fun sessionSettings_fetchDefaults() {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")

    val sessionsSettings =
      SessionsSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp)
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
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val context = firebaseApp.applicationContext

    val sessionsSettings =
      SessionsSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp)
      )
    assertThat(sessionsSettings.sessionsEnabled).isFalse()
    assertThat(sessionsSettings.samplingRate).isEqualTo(0.5)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(3.minutes)
  }

  @Test
  fun sessionSettings_fetchOverridingConfigsOnlyWhenPresent() {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", false)
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    val firebaseApp = FakeFirebaseApp(metadata).firebaseApp
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val context = firebaseApp.applicationContext

    val sessionsSettings =
      SessionsSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp)
      )
    assertThat(sessionsSettings.sessionsEnabled).isFalse()
    assertThat(sessionsSettings.samplingRate).isEqualTo(0.5)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(30.minutes)
  }

  @Test
  fun sessionSettings_RemoteSettingsOverrideDefaultsWhenPresent() {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()
    fakeFetcher.responseJSONObject = JSONObject(validResponse)

    val remoteSettings =
      RemoteSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    remoteSettings.updateSettings()

    val sessionsSettings =
      SessionsSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        localOverrideSettings = LocalOverrideSettings(context),
        remoteSettings = remoteSettings
      )
    assertThat(sessionsSettings.sessionsEnabled).isFalse()
    assertThat(sessionsSettings.samplingRate).isEqualTo(0.75)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    remoteSettings.clearCachedSettings()
  }

  @Test
  fun sessionSettings_ManifestOverridesRemoteSettingsAndDefaultsWhenPresent() {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", true)
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    metadata.putInt("firebase_sessions_sessions_restart_timeout", 180)
    val firebaseApp = FakeFirebaseApp(metadata).firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()
    fakeFetcher.responseJSONObject = JSONObject(validResponse)

    val remoteSettings =
      RemoteSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    remoteSettings.updateSettings()

    val sessionsSettings =
      SessionsSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        localOverrideSettings = LocalOverrideSettings(context),
        remoteSettings = remoteSettings
      )
    assertThat(sessionsSettings.sessionsEnabled).isTrue()
    assertThat(sessionsSettings.samplingRate).isEqualTo(0.5)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(3.minutes)

    remoteSettings.clearCachedSettings()
  }

  companion object {
    private const val SESSION_TEST_CONFIGS_NAME = "firebase_session_settings_test"
    private const val validResponse =
      "{ \"settings_version\": 3, \"cache_duration\": 86400, \"features\": {}, \"app\": {}, \"fabric\": {}, \"app_quality\": { \"sessions_enabled\": false, \"sampling_rate\": 0.75, \"session_timeout_seconds\": 2400 } }"
  }
  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
