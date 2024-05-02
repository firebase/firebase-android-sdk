package com.google.firebase.dataconnect

import java.util.Objects

public class DataConnectSettings(
  public val host: String = "firebasedataconnect.googleapis.com",
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
