package com.google.firebase.dataconnect

public class DataConnectSettings(
  host: String = "dataconnect.googleapis.com",
  sslEnabled: Boolean = true
) {
  private val impl = Impl(host = host, sslEnabled = sslEnabled)

  public fun copy(
    host: String = this.host,
    sslEnabled: Boolean = this.sslEnabled
  ): DataConnectSettings = DataConnectSettings(host = host, sslEnabled = sslEnabled)

  public val host: String
    get() = impl.host
  public val sslEnabled: Boolean
    get() = impl.sslEnabled

  private data class Impl(val host: String, val sslEnabled: Boolean)

  override fun equals(other: Any?): Boolean = (other is DataConnectSettings) && other.impl == impl

  override fun hashCode(): Int = impl.hashCode()

  override fun toString(): String = "DataConnectSettings(host=$host, sslEnabled=$sslEnabled)"
}

internal fun DataConnectSettings.isDefaultHost() = host == DataConnectSettings().host
