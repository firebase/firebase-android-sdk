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
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.ApplicationInfo
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONException
import org.json.JSONObject

internal class RemoteSettings(
  val context: Context,
  val blockingDispatcher: CoroutineContext,
  val backgroundDispatcher: CoroutineContext,
  val firebaseInstallationsApi: FirebaseInstallationsApi,
  val appInfo: ApplicationInfo,
  private val configsFetcher: CrashlyticsSettingsFetcher = RemoteSettingsFetcher(appInfo),
  dataStoreName: String = SESSION_CONFIGS_NAME
) : SettingsProvider {
  private val Context.dataStore by preferencesDataStore(name = dataStoreName)
  private val settingsCache = SettingsCache(context.dataStore)
  private var fetchInProgress = AtomicBoolean(false)

  override val sessionEnabled: Boolean?
    get() {
      return settingsCache.sessionsEnabled()
    }

  override val sessionRestartTimeout: Duration?
    get() {
      val durationInSeconds = settingsCache.sessionRestartTimeout()
      if (durationInSeconds != null) {
        return durationInSeconds.toLong().seconds
      }
      return null
    }

  override val samplingRate: Double?
    get() {
      return settingsCache.sessionSamplingRate()
    }

  override fun updateSettings() {
    // TODO: Move to blocking coroutine dispatcher.
    runBlocking(Dispatchers.Default) { launch { fetchConfigs() } }
  }

  override fun isSettingsStale(): Boolean {
    return settingsCache.hasCacheExpired()
  }

  internal fun clearCachedSettings() {
    val scope = CoroutineScope(backgroundDispatcher)
    scope.launch { settingsCache.removeConfigs() }
  }

  suspend private fun fetchConfigs() {
    // Check if a fetch is in progress. If yes, return
    if (fetchInProgress.get()) {
      return
    }

    // Check if cache is expired. If not, return
    if (!settingsCache.hasCacheExpired()) {
      return
    }

    fetchInProgress.set(true)

    // Get the installations ID before making a remote config fetch
    var installationId = firebaseInstallationsApi.id.await()
    if (installationId == null) {
      fetchInProgress.set(false)
      return
    }

    // All the required fields are available, start making a network request.
    val options =
      mapOf(
        "X-Crashlytics-Installation-ID" to installationId,
        "X-Crashlytics-Device-Model" to
          removeForwardSlashesIn(String.format("%s/%s", Build.MANUFACTURER, Build.MODEL)),
        "X-Crashlytics-OS-Build-Version" to removeForwardSlashesIn(Build.VERSION.INCREMENTAL),
        "X-Crashlytics-OS-Display-Version" to removeForwardSlashesIn(Build.VERSION.RELEASE),
        "X-Crashlytics-API-Client-Version" to appInfo.sessionSdkVersion
      )

    configsFetcher.doConfigFetch(
      headerOptions = options,
      onSuccess = {
        var sessionsEnabled: Boolean? = null
        var sessionSamplingRate: Double? = null
        var sessionTimeoutSeconds: Int? = null
        var cacheDuration: Int? = null
        if (it.has("app_quality")) {
          val aqsSettings = it.get("app_quality") as JSONObject
          try {
            if (aqsSettings.has("sessions_enabled")) {
              sessionsEnabled = aqsSettings.get("sessions_enabled") as Boolean?
            }

            if (aqsSettings.has("sampling_rate")) {
              sessionSamplingRate = aqsSettings.get("sampling_rate") as Double?
            }

            if (aqsSettings.has("session_timeout_seconds")) {
              sessionTimeoutSeconds = aqsSettings.get("session_timeout_seconds") as Int?
            }

            if (aqsSettings.has("cache_duration")) {
              cacheDuration = aqsSettings.get("cache_duration") as Int?
            }
          } catch (exception: JSONException) {
            Log.e(TAG, "Error parsing the configs remotely fetched: ", exception)
          }
        }

        val scope = CoroutineScope(backgroundDispatcher)
        sessionsEnabled?.let { settingsCache.updateSettingsEnabled(sessionsEnabled) }

        sessionTimeoutSeconds?.let {
          settingsCache.updateSessionRestartTimeout(sessionTimeoutSeconds)
        }

        sessionSamplingRate?.let { settingsCache.updateSamplingRate(sessionSamplingRate) }

        cacheDuration?.let { settingsCache.updateSessionCacheDuration(cacheDuration) }
          ?: let { settingsCache.updateSessionCacheDuration(86400) }

        settingsCache.updateSessionCacheUpdatedTime(System.currentTimeMillis())
        fetchInProgress.set(false)
      },
      onFailure = {
        // Network request failed here.
        Log.e(TAG, "Error failing to fetch the remote configs")
        fetchInProgress.set(false)
      }
    )
  }

  private fun removeForwardSlashesIn(s: String): String {
    return s.replace(FORWARD_SLASH_STRING.toRegex(), "")
  }

  companion object {
    private const val SESSION_CONFIGS_NAME = "firebase_session_settings"
    private const val TAG = "SessionConfigFetcher"
    private const val FORWARD_SLASH_STRING: String = "/"
  }
}
