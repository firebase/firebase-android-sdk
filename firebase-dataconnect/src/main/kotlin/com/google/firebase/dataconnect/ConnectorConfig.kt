package com.google.firebase.dataconnect

public class ConnectorConfig(connector: String, location: String, service: String) {
  private val impl = Impl(connector = connector, location = location, service = service)

  public fun copy(
    connector: String = this.connector,
    location: String = this.location,
    service: String = this.service
  ): ConnectorConfig =
    ConnectorConfig(connector = connector, location = location, service = service)

  public val connector: String
    get() = impl.connector
  public val location: String
    get() = impl.location
  public val service: String
    get() = impl.service

  private data class Impl(val connector: String, val location: String, val service: String)

  override fun equals(other: Any?): Boolean = (other is ConnectorConfig) && other.impl == impl

  override fun hashCode(): Int = impl.hashCode()

  override fun toString(): String =
    "ConnectorConfig(connector=$connector, location=$location, service=$service)"
}
