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

import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.ApplicationInfo
import com.google.firebase.sessions.FirebaseSessions.Companion.TAG
import com.google.firebase.sessions.InstallationId
import com.google.firebase.sessions.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject

@Singleton
internal class RemoteSettings
@Inject
constructor(
  private val timeProvider: TimeProvider,
  private val firebaseInstallationsApi: FirebaseInstallationsApi,
  private val appInfo: ApplicationInfo,
  private val configsFetcher: CrashlyticsSettingsFetcher,
  private val settingsCache: SettingsCache,
) : SettingsProvider {
  private val fetchInProgress = Mutex()

  override val sessionEnabled: Boolean?
    get() = settingsCache.sessionsEnabled()

  override val sessionRestartTimeout: Duration?
    get() = settingsCache.sessionRestartTimeout()?.seconds

  override val samplingRate: Double?
    get() = settingsCache.sessionSamplingRate()

  /**
   * Fetch remote settings. This should only be called if data collection is enabled.
   *
   * This will exit early if the cache is not expired. Otherwise it will block while fetching, even
   * if called multiple times. This may fetch the FID, so only call if data collection is enabled.
   */
  override suspend fun updateSettings() {
    // Check if cache is expired. If not, return early.
    if (!fetchInProgress.isLocked && !settingsCache.hasCacheExpired()) {
      return
    }

    fetchInProgress.withLock {
      // Double check if cache is expired. If not, return.
      if (!settingsCache.hasCacheExpired()) {
        Log.d(TAG, "Remote settings cache not expired. Using cached values.")
        return
      }

      // Get the installations ID before making a remote config fetch.
      val installationId = InstallationId.create(firebaseInstallationsApi).fid
      if (installationId == "") {
        Log.w(TAG, "Error getting Firebase Installation ID. Skipping this Session Event.")
        return
      }

      // All the required fields are available, start making a network request.
      val options =
        mapOf(
          "X-Crashlytics-Installation-ID" to installationId,
          "X-Crashlytics-Device-Model" to sanitize("${Build.MANUFACTURER}${Build.MODEL}"),
          "X-Crashlytics-OS-Build-Version" to sanitize(Build.VERSION.INCREMENTAL),
          "X-Crashlytics-OS-Display-Version" to sanitize(Build.VERSION.RELEASE),
          "X-Crashlytics-API-Client-Version" to appInfo.sessionSdkVersion,
        )

      Log.d(TAG, "Fetching settings from server.")
      configsFetcher.doConfigFetch(
        headerOptions = options,
        onSuccess = {
          Log.d(TAG, "Fetched settings: $it")
          var sessionsEnabled: Boolean? = null
          var sessionSamplingRate: Double? = null
          var sessionTimeoutSeconds: Int? = null
          var cacheDuration: Int? = null
          if (it.has("app_quality")) {
            val aqsSettings = it["app_quality"] as JSONObject
            try {
              if (aqsSettings.has("sessions_enabled")) {
                sessionsEnabled = aqsSettings["sessions_enabled"] as Boolean?
              }

              if (aqsSettings.has("sampling_rate")) {
                sessionSamplingRate = aqsSettings["sampling_rate"] as Double?
              }

              if (aqsSettings.has("session_timeout_seconds")) {
                sessionTimeoutSeconds = aqsSettings["session_timeout_seconds"] as Int?
              }

              if (aqsSettings.has("cache_duration")) {
                cacheDuration = aqsSettings["cache_duration"] as Int?
              }
            } catch (exception: JSONException) {
              Log.e(TAG, "Error parsing the configs remotely fetched: ", exception)
            }
          }

          settingsCache.updateConfigs(
            SessionConfigs(
              sessionsEnabled = sessionsEnabled,
              sessionTimeoutSeconds = sessionTimeoutSeconds,
              sessionSamplingRate = sessionSamplingRate,
              cacheDurationSeconds = cacheDuration ?: defaultCacheDuration,
              cacheUpdatedTimeSeconds = timeProvider.currentTime().seconds,
            )
          )
        },
        onFailure = { msg ->
          // Network request failed here.
          Log.e(TAG, "Error failed to fetch the remote configs: $msg")
        },
      )
    }
  }

  override fun isSettingsStale(): Boolean = settingsCache.hasCacheExpired()

  @VisibleForTesting
  internal suspend fun clearCachedSettings() {
    settingsCache.updateConfigs(SessionConfigsSerializer.defaultValue)
  }

  private fun sanitize(s: String) = s.replace(sanitizeRegex, "")

  private companion object {
    val defaultCacheDuration = 24.hours.inWholeSeconds.toInt()

    val sanitizeRegex = "/".toRegex()
  }
}
