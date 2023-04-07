package com.google.firebase.sessions.settings

import android.content.Context
import android.content.pm.PackageManager
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class LocalOverrideSettings(val context: Context) : SettingsProvider {

  private val sessions_metadata_flag_sessionsEnabled = "firebase_sessions_enabled"
  private val sessions_metadata_flag_sessionRestartTimeout =
    "firebase_sessions_sessions_restart_timeout"
  private val sessions_metadata_flag_samplingRate = "firebase_sessions_sampling_rate"
  private val metadata =
    context.packageManager
      .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
      .metaData

  override val sessionEnabled: Boolean?
    get() {
      if (metadata != null && metadata.containsKey(sessions_metadata_flag_sessionsEnabled)) {
        return metadata.getBoolean(sessions_metadata_flag_sessionsEnabled)
      }
      return null
    }

  override val sessionRestartTimeout: Duration?
    get() {
      if (metadata != null && metadata.containsKey(sessions_metadata_flag_sessionRestartTimeout)) {
        val timeoutInSeconds = metadata.getInt(sessions_metadata_flag_sessionRestartTimeout)
        val duration = timeoutInSeconds!!.toDuration(DurationUnit.SECONDS)
        return duration
      }
      return null
    }

  override val samplingRate: Double?
    get() {
      if (metadata != null && metadata.containsKey(sessions_metadata_flag_samplingRate)) {
        return metadata.getDouble(sessions_metadata_flag_samplingRate)
      }
      return null
    }

  override fun updateSettings() {
    // Nothing to be done here since there is nothing to be updated.
  }

  override fun isSettingsStale(): Boolean {
    // Settings are never stale since all of these are from Manifest file.
    return false
  }
}
