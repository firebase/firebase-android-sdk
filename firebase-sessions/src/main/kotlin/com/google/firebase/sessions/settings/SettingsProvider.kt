package com.google.firebase.sessions.settings

import kotlin.time.Duration

interface SettingsProvider {
  // Setting to control if session collection is enabled
  val sessionEnabled: Boolean?

  // Setting to represent when to restart a new session after app backgrounding.
  val sessionRestartTimeout: Duration?

  // Setting denoting the percentage of the sessions data that should be collected
  val samplingRate: Double?

  // Function to initiate refresh of the settings for the provider
  fun updateSettings()

  // Function representing if the settings are stale.
  fun isSettingsStale(): Boolean
}
