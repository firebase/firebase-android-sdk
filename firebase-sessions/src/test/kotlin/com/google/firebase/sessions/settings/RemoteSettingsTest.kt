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

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.concurrent.TestOnlyExecutors
import com.google.firebase.sessions.SessionEvents
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseInstallations
import com.google.firebase.sessions.testing.FakeRemoteConfigFetcher
import com.google.firebase.sessions.testing.TestSessionEventData.TEST_APPLICATION_INFO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RemoteSettingsTest {

  @Test
  fun remoteSettings_successfulFetchCachesValues() =
    runTest(UnconfinedTestDispatcher()) {
      val firebaseApp = FakeFirebaseApp().firebaseApp
      val context = firebaseApp.applicationContext
      val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
      val fakeFetcher = FakeRemoteConfigFetcher()

      TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext

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

      runCurrent()

      assertThat(remoteSettings.sessionEnabled).isNull()
      assertThat(remoteSettings.samplingRate).isNull()
      assertThat(remoteSettings.sessionRestartTimeout).isNull()

      fakeFetcher.responseJSONObject = JSONObject(VALID_RESPONSE)
      remoteSettings.updateSettings()

      runCurrent()

      assertThat(remoteSettings.sessionEnabled).isFalse()
      assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
      assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

      remoteSettings.clearCachedSettings()
    }

  @Test
  fun remoteSettings_successfulFetchWithLessConfigsCachesOnlyReceivedValues() =
    runTest(UnconfinedTestDispatcher()) {
      val firebaseApp = FakeFirebaseApp().firebaseApp
      val context = firebaseApp.applicationContext
      val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
      val fakeFetcher = FakeRemoteConfigFetcher()

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

      runCurrent()

      assertThat(remoteSettings.sessionEnabled).isNull()
      assertThat(remoteSettings.samplingRate).isNull()
      assertThat(remoteSettings.sessionRestartTimeout).isNull()

      val fetchedResponse = JSONObject(VALID_RESPONSE)
      fetchedResponse.getJSONObject("app_quality").remove("sessions_enabled")
      fakeFetcher.responseJSONObject = fetchedResponse
      remoteSettings.updateSettings()

      runCurrent()

      assertThat(remoteSettings.sessionEnabled).isNull()
      assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
      assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

      remoteSettings.clearCachedSettings()
    }

  @Test
  fun remoteSettings_successfulReFetchUpdatesCache() =
    runTest(UnconfinedTestDispatcher()) {
      val firebaseApp = FakeFirebaseApp().firebaseApp
      val context = firebaseApp.applicationContext
      val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
      val fakeFetcher = FakeRemoteConfigFetcher()

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

      val fetchedResponse = JSONObject(VALID_RESPONSE)
      fetchedResponse.getJSONObject("app_quality").put("cache_duration", 1)
      fakeFetcher.responseJSONObject = fetchedResponse
      remoteSettings.updateSettings()

      runCurrent()

      assertThat(remoteSettings.sessionEnabled).isFalse()
      assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
      assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

      fetchedResponse.getJSONObject("app_quality").put("sessions_enabled", true)
      fetchedResponse.getJSONObject("app_quality").put("sampling_rate", 0.25)
      fetchedResponse.getJSONObject("app_quality").put("session_timeout_seconds", 1200)

      // TODO(mrober): Fix these so we don't need to sleep. Maybe use FakeTime?
      // Sleep for a second before updating configs
      Thread.sleep(2000)

      fakeFetcher.responseJSONObject = fetchedResponse
      remoteSettings.updateSettings()

      runCurrent()

      assertThat(remoteSettings.sessionEnabled).isTrue()
      assertThat(remoteSettings.samplingRate).isEqualTo(0.25)
      assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(20.minutes)

      remoteSettings.clearCachedSettings()
    }

  @Test
  fun remoteSettings_successfulFetchWithEmptyConfigRetainsOldConfigs() =
    runTest(UnconfinedTestDispatcher()) {
      val firebaseApp = FakeFirebaseApp().firebaseApp
      val context = firebaseApp.applicationContext
      val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
      val fakeFetcher = FakeRemoteConfigFetcher()

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

      val fetchedResponse = JSONObject(VALID_RESPONSE)
      fetchedResponse.getJSONObject("app_quality").put("cache_duration", 1)
      fakeFetcher.responseJSONObject = fetchedResponse
      remoteSettings.updateSettings()

      runCurrent()

      assertThat(remoteSettings.sessionEnabled).isFalse()
      assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
      assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

      fetchedResponse.remove("app_quality")

      // Sleep for a second before updating configs
      Thread.sleep(2000)

      fakeFetcher.responseJSONObject = fetchedResponse
      remoteSettings.updateSettings()

      runCurrent()

      assertThat(remoteSettings.sessionEnabled).isFalse()
      assertThat(remoteSettings.samplingRate).isEqualTo(0.75)
      assertThat(remoteSettings.sessionRestartTimeout).isEqualTo(40.minutes)

      remoteSettings.clearCachedSettings()
    }

  @Test
  fun remoteSettings_fetchWhileFetchInProgress() =
    runTest(UnconfinedTestDispatcher()) {
      // This test does:
      // 1. Do a fetch with a fake fetcher that will block for 3 seconds.
      // 2. While that is happening, do a second fetch.
      //    - First fetch is still fetching, so second fetch should fall through to the mutex.
      //    - Second fetch will be blocked until first completes.
      //    - First fetch returns, should unblock the second fetch.
      //    - Second fetch should go into mutex, sees cache is valid in "double check," exist early.
      // 3. After a fetch completes, do a third fetch.
      //    - First fetch should have have updated the cache.
      //    - Third fetch should exit even earlier, never having gone into the mutex.

      val firebaseApp = FakeFirebaseApp().firebaseApp
      val context = firebaseApp.applicationContext
      val firebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
      val fakeFetcherWithDelay =
        FakeRemoteConfigFetcher(
          JSONObject(VALID_RESPONSE),
          networkDelay = 3.seconds,
        )

      fakeFetcherWithDelay.responseJSONObject
        .getJSONObject("app_quality")
        .put("sampling_rate", 0.125)

      val remoteSettingsWithDelay =
        RemoteSettings(
          TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
          firebaseInstallations,
          SessionEvents.getApplicationInfo(firebaseApp),
          configsFetcher = fakeFetcherWithDelay,
          dataStore =
            PreferenceDataStoreFactory.create(
              scope = this,
              produceFile = { context.preferencesDataStoreFile(SESSION_TEST_CONFIGS_NAME) },
            ),
        )

      // Do the first fetch. This one should fetched the configsFetcher.
      val firstFetch = launch(Dispatchers.Default) { remoteSettingsWithDelay.updateSettings() }

      // Wait a second, and then do the second fetch while first is still running.
      // This one should block until the first fetch completes, but then exit early.
      launch(Dispatchers.Default) {
        delay(1.seconds)
        remoteSettingsWithDelay.updateSettings()
      }

      // Wait until the first fetch is done, then do a third fetch.
      // This one should not even block, and exit early.
      firstFetch.join()
      withTimeout(1.seconds) { remoteSettingsWithDelay.updateSettings() }

      // Assert that the configsFetcher was fetched exactly once.
      assertThat(fakeFetcherWithDelay.timesCalled).isEqualTo(1)
      assertThat(remoteSettingsWithDelay.samplingRate).isEqualTo(0.125)
    }

  @Test
  fun remoteSettingsFetcher_badFetch_callsOnFailure() = runTest {
    var failure: String? = null

    RemoteSettingsFetcher(
        TEST_APPLICATION_INFO,
        TestOnlyExecutors.blocking().asCoroutineDispatcher() + coroutineContext,
        baseUrl = "this.url.is.invalid",
      )
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
