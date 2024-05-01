package com.google.firebase.dataconnect

import java.util.Objects

public class DataConnectSettings(
  public val host: String = "dataconnect.googleapis.com",
  public val sslEnabled: Boolean = true
) {

  public fun copy(
    host: String = this.host,
    sslEnabled: Boolean = this.sslEnabled
  ): DataConnectSettings = DataConnectSettings(host = host, sslEnabled = sslEnabled)

  override fun equals(other: Any?): Boolean =
    (other is DataConnectSettings) && other.host == host && other.sslEnabled == sslEnabled

  override fun hashCode(): Int = Objects.hash(DataConnectSettings::class, host, sslEnabled)

  override fun toString(): String = "DataConnectSettings(host=$host, sslEnabled=$sslEnabled)"
}

internal fun DataConnectSettings.isDefaultHost() = host == DataConnectSettings().host

// NOTE: To have firebase-tools use a different Data Connect host (e.g. staging), set the
// environment variable `FIREBASE_DATACONNECT_URL` to the URL.
internal const val STAGING_DATACONNECT_HOST =
  "https://staging-firebasedataconnect.sandbox.googleapis.com"
internal const val AUTOPUSH_DATACONNECT_HOST =
  "https://autopush-firebasedataconnect.sandbox.googleapis.com"
