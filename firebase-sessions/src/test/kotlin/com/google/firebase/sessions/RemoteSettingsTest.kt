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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.settings.RemoteSettings
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import com.google.firebase.sessions.testing.FakeRemoteConfigFetcher
import kotlin.time.Duration.Companion.minutes
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteSettingsTest {

  @Test
  fun RemoteSettings_successfulFetchCachesValues() {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()

    val remoteSettings =
      RemoteSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    Truth.assertThat(remoteSettings.sessionEnabled).isNull()
    Truth.assertThat(remoteSettings.samplingRate).isNull()
    Truth.assertThat(remoteSettings.sessionRestartTimeout).isNull()

    fakeFetcher.responseJSONObject = JSONObject(validResponse)
    remoteSettings.updateSettings()

    Truth.assertThat(remoteSettings.sessionEnabled).isFalse()
    Truth.assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    Truth.assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    remoteSettings.clearCachedSettings()
  }

  @Test
  fun RemoteSettings_successfulFetchWithLessConfigsCachesOnlyReceivedValues() {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()

    val remoteSettings =
      RemoteSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    Truth.assertThat(remoteSettings.sessionEnabled).isNull()
    Truth.assertThat(remoteSettings.samplingRate).isNull()
    Truth.assertThat(remoteSettings.sessionRestartTimeout).isNull()

    val fetchedResponse = JSONObject(validResponse)
    fetchedResponse.getJSONObject("app_quality").remove("sessions_enabled")
    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    Truth.assertThat(remoteSettings.sessionEnabled).isNull()
    Truth.assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    Truth.assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    remoteSettings.clearCachedSettings()
  }

  @Test
  fun RemoteSettings_successfulRefetchUpdatesCache() {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()

    val remoteSettings =
      RemoteSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    val fetchedResponse = JSONObject(validResponse)
    fetchedResponse.getJSONObject("app_quality").put("cache_duration", 1)
    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    Truth.assertThat(remoteSettings.sessionEnabled).isFalse()
    Truth.assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    Truth.assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    fetchedResponse.getJSONObject("app_quality").put("sessions_enabled", true)
    fetchedResponse.getJSONObject("app_quality").put("sampling_rate", 0.25)
    fetchedResponse.getJSONObject("app_quality").put("session_timeout_seconds", 1200)

    // Sleep for a second before updating configs
    Thread.sleep(2000)

    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    Truth.assertThat(remoteSettings.sessionEnabled).isTrue()
    Truth.assertThat(remoteSettings.samplingRate).isEqualTo(0.25)
    Truth.assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(20.minutes)

    remoteSettings.clearCachedSettings()
  }

  @Test
  fun RemoteSettings_successfulFetchWithEmptyConfigRetainsOldConfigs() {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()

    val remoteSettings =
      RemoteSettings(
        context,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    val fetchedResponse = JSONObject(validResponse)
    fetchedResponse.getJSONObject("app_quality").put("cache_duration", 1)
    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    Truth.assertThat(remoteSettings.sessionEnabled).isFalse()
    Truth.assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    Truth.assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    fetchedResponse.remove("app_quality")

    // Sleep for a second before updating configs
    Thread.sleep(2000)

    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    Truth.assertThat(remoteSettings.sessionEnabled).isFalse()
    Truth.assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    Truth.assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    remoteSettings.clearCachedSettings()
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  companion object {
    private const val SESSION_TEST_CONFIGS_NAME = "firebase_session_settings_test"
    private const val validResponse =
      "{ \"settings_version\": 3, \"cache_duration\": 86400, \"features\": {}, \"app\": {}, \"fabric\": {}, \"app_quality\": { \"sessions_enabled\": false, \"sampling_rate\": 0.75, \"session_timeout_seconds\": 2400 } }"
  }
}
