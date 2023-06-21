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
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.sessions.settings.RemoteSettings
import com.google.firebase.sessions.settings.RemoteSettingsFetcher
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import com.google.firebase.sessions.testing.FakeRemoteConfigFetcher
import com.google.firebase.sessions.testing.TestSessionEventData.TEST_APPLICATION_INFO
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RemoteSettingsTest {

  @Test
  fun remoteSettings_successfulFetchCachesValues() = runTest {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()

    val remoteSettings =
      RemoteSettings(
        context,
        TestOnlyExecutors.blocking().asCoroutineDispatcher() + coroutineContext,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    assertThat(remoteSettings.sessionEnabled).isNull()
    assertThat(remoteSettings.samplingRate).isNull()
    assertThat(remoteSettings.sessionRestartTimeout).isNull()

    fakeFetcher.responseJSONObject = JSONObject(validResponse)
    remoteSettings.updateSettings()

    assertThat(remoteSettings.sessionEnabled).isFalse()
    assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    remoteSettings.clearCachedSettings()
  }

  @Test
  fun remoteSettings_successfulFetchWithLessConfigsCachesOnlyReceivedValues() = runTest {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()

    val remoteSettings =
      RemoteSettings(
        context,
        TestOnlyExecutors.blocking().asCoroutineDispatcher() + coroutineContext,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    assertThat(remoteSettings.sessionEnabled).isNull()
    assertThat(remoteSettings.samplingRate).isNull()
    assertThat(remoteSettings.sessionRestartTimeout).isNull()

    val fetchedResponse = JSONObject(validResponse)
    fetchedResponse.getJSONObject("app_quality").remove("sessions_enabled")
    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    assertThat(remoteSettings.sessionEnabled).isNull()
    assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    remoteSettings.clearCachedSettings()
  }

  @Test
  fun remoteSettings_successfulReFetchUpdatesCache() = runTest {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()

    val blockingDispatcher = TestOnlyExecutors.blocking().asCoroutineDispatcher() + coroutineContext
    val backgroundDispatcher =
      TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext
    val remoteSettings =
      RemoteSettings(
        context,
        blockingDispatcher,
        backgroundDispatcher,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    val fetchedResponse = JSONObject(validResponse)
    fetchedResponse.getJSONObject("app_quality").put("cache_duration", 1)
    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    assertThat(remoteSettings.sessionEnabled).isFalse()
    assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    fetchedResponse.getJSONObject("app_quality").put("sessions_enabled", true)
    fetchedResponse.getJSONObject("app_quality").put("sampling_rate", 0.25)
    fetchedResponse.getJSONObject("app_quality").put("session_timeout_seconds", 1200)

    // Sleep for a second before updating configs
    Thread.sleep(2000)

    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    assertThat(remoteSettings.sessionEnabled).isTrue()
    assertThat(remoteSettings.samplingRate).isEqualTo(0.25)
    assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(20.minutes)

    remoteSettings.clearCachedSettings()
  }

  @Test
  fun remoteSettings_successfulFetchWithEmptyConfigRetainsOldConfigs() = runTest {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val context = firebaseApp.applicationContext
    val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
    val fakeFetcher = FakeRemoteConfigFetcher()

    val remoteSettings =
      RemoteSettings(
        context,
        TestOnlyExecutors.blocking().asCoroutineDispatcher() + coroutineContext,
        TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
        firebaseInstallations,
        SessionEvents.getApplicationInfo(firebaseApp),
        fakeFetcher,
        SESSION_TEST_CONFIGS_NAME
      )
    val fetchedResponse = JSONObject(validResponse)
    fetchedResponse.getJSONObject("app_quality").put("cache_duration", 1)
    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    assertThat(remoteSettings.sessionEnabled).isFalse()
    assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    fetchedResponse.remove("app_quality")

    // Sleep for a second before updating configs
    Thread.sleep(2000)

    fakeFetcher.responseJSONObject = fetchedResponse
    remoteSettings.updateSettings()

    assertThat(remoteSettings.sessionEnabled).isFalse()
    assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
    assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

    remoteSettings.clearCachedSettings()
  }

  @Test
  fun remoteSettingsFetcher_badFetch_callsOnFailure() = runTest {
    var failure: String? = null

    RemoteSettingsFetcher(TEST_APPLICATION_INFO, baseUrl = "this.url.is.invalid")
      .doConfigFetch(
        headerOptions = emptyMap(),
        onSuccess = {},
        onFailure = { failure = it },
      )

    assertThat(failure).isNotNull()
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
