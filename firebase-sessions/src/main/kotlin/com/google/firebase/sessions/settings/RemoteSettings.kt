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
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStore
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class RemoteSettings(val context: Context) : SettingsProvider {
  private val SESSION_CONFIGS_NAME = "firebase_session_settings"
  private val Context.dataStore by preferencesDataStore(name = SESSION_CONFIGS_NAME)
  private val settingsCache = SettingsCache(context.dataStore)

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
    fetchConfigs()
  }

  override fun isSettingsStale(): Boolean {
    return settingsCache.hasCacheExpired()
  }

  companion object SettingsFetcher {
    private val FIREBASE_SESSIONS_BASE_URL_STRING = "https://firebase-settings.crashlytics.com"
    private val FIREBASE_PLATFORM = "android"
    private val fetchInProgress = false
    private val settingsUrl: URL = run {
      var uri = Uri.Builder()
      uri.scheme("https")
      uri.authority(FIREBASE_SESSIONS_BASE_URL_STRING)
      uri.appendPath("spi/v2/platforms")
      uri.appendPath(FIREBASE_PLATFORM)
      uri.appendPath("gmp")
      // TODO(visum) Replace below with the GMP APPId
      uri.appendPath("GMP_APP_ID")
      uri.appendPath("settings")

      uri.appendQueryParameter("build_version", "")
      uri.appendQueryParameter("display_version", "")

      URL(uri.build().toString())
    }

    fun fetchConfigs() {
      // Check if a fetch is in progress. If yes, return
      if (fetchInProgress) {
        return
      }

      // Check if cache is expired. If not, return
      // Initiate a fetch. On successful response cache the fetched values
    }
  }
}
