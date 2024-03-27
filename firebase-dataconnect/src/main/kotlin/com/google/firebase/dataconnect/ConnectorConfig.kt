package com.google.firebase.dataconnect

public class ConnectorConfig(connector: String, location: String, serviceId: String) {
  private val impl = Impl(connector = connector, location = location, serviceId = serviceId)

  public fun copy(
    connector: String = this.connector,
    location: String = this.location,
    serviceId: String = this.serviceId
  ): ConnectorConfig =
    ConnectorConfig(connector = connector, location = location, serviceId = serviceId)

  public val connector: String
    get() = impl.connector
  public val location: String
    get() = impl.location
  public val serviceId: String
    get() = impl.serviceId

  private data class Impl(val connector: String, val location: String, val serviceId: String)

  override fun equals(other: Any?): Boolean = (other is ConnectorConfig) && other.impl == impl

  override fun hashCode(): Int = impl.hashCode()

  override fun toString(): String =
    "ConnectorConfig(connector=$connector, location=$location, serviceId=$serviceId)"
}
