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
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.ApplicationInfo
import com.google.firebase.sessions.ProcessDetailsProvider.getProcessName
import com.google.firebase.sessions.SessionDataStoreConfigs
import com.google.firebase.sessions.SessionEvents
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** [SessionsSettings] manages all the configs that are relevant to the sessions library. */
internal class SessionsSettings(
  private val localOverrideSettings: SettingsProvider,
  private val remoteSettings: SettingsProvider,
) {
  private constructor(
    context: Context,
    blockingDispatcher: CoroutineContext,
    backgroundDispatcher: CoroutineContext,
    firebaseInstallationsApi: FirebaseInstallationsApi,
    appInfo: ApplicationInfo,
  ) : this(
    localOverrideSettings = LocalOverrideSettings(context),
    remoteSettings =
      RemoteSettings(
        backgroundDispatcher,
        firebaseInstallationsApi,
        appInfo,
        configsFetcher =
          RemoteSettingsFetcher(
            appInfo,
            blockingDispatcher,
          ),
        dataStore = context.dataStore,
      ),
  )

  constructor(
    firebaseApp: FirebaseApp,
    blockingDispatcher: CoroutineContext,
    backgroundDispatcher: CoroutineContext,
    firebaseInstallationsApi: FirebaseInstallationsApi,
  ) : this(
    firebaseApp.applicationContext,
    blockingDispatcher,
    backgroundDispatcher,
    firebaseInstallationsApi,
    SessionEvents.getApplicationInfo(firebaseApp),
  )

  // Order of preference for all the configs below:
  // 1. Honor local overrides
  // 2. If no local overrides, use remote config
  // 3. If no remote config, fall back to SDK defaults.

  /** Setting to qualify if sessions service is enabled. */
  val sessionsEnabled: Boolean
    get() {
      localOverrideSettings.sessionEnabled?.let {
        return it
      }
      remoteSettings.sessionEnabled?.let {
        return it
      }
      // SDK Default
      return true
    }

  /** Setting that provides the sessions sampling rate. */
  val samplingRate: Double
    get() {
      localOverrideSettings.samplingRate?.let {
        if (isValidSamplingRate(it)) {
          return it
        }
      }
      remoteSettings.samplingRate?.let {
        if (isValidSamplingRate(it)) {
          return it
        }
      }
      // SDK Default
      return 1.0
    }

  /** Background timeout config value before which a new session is generated. */
  val sessionRestartTimeout: Duration
    get() {
      localOverrideSettings.sessionRestartTimeout?.let {
        if (isValidSessionRestartTimeout(it)) {
          return it
        }
      }
      remoteSettings.sessionRestartTimeout?.let {
        if (isValidSessionRestartTimeout(it)) {
          return it
        }
      }
      // SDK Default
      return 30.minutes
    }

  private fun isValidSamplingRate(samplingRate: Double): Boolean = samplingRate in 0.0..1.0

  private fun isValidSessionRestartTimeout(sessionRestartTimeout: Duration): Boolean {
    return sessionRestartTimeout.isPositive() && sessionRestartTimeout.isFinite()
  }

  /** Update the settings for all the settings providers. */
  suspend fun updateSettings() {
    localOverrideSettings.updateSettings()
    remoteSettings.updateSettings()
  }

  internal companion object {
    private const val TAG = "SessionsSettings"

    val instance: SessionsSettings
      get() = Firebase.app[SessionsSettings::class.java]

    private val Context.dataStore: DataStore<Preferences> by
      preferencesDataStore(
        name = SessionDataStoreConfigs.SETTINGS_CONFIG_NAME,
        corruptionHandler =
          ReplaceFileCorruptionHandler { ex ->
            Log.w(TAG, "CorruptionException in settings DataStore in ${getProcessName()}.", ex)
            emptyPreferences()
          },
      )
  }
}
