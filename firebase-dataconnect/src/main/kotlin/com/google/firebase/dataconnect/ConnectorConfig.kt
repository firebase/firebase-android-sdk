package com.google.firebase.dataconnect

import java.util.Objects

public class ConnectorConfig(
  public val connector: String,
  public val location: String,
  public val serviceId: String
) {

  public fun copy(
    connector: String = this.connector,
    location: String = this.location,
    serviceId: String = this.serviceId
  ): ConnectorConfig =
    ConnectorConfig(connector = connector, location = location, serviceId = serviceId)

  override fun equals(other: Any?): Boolean =
    (other is ConnectorConfig) &&
      other.connector == connector &&
      other.location == location &&
      other.serviceId == serviceId

  override fun hashCode(): Int =
    Objects.hash(ConnectorConfig::class, connector, location, serviceId)

  override fun toString(): String =
    "ConnectorConfig(connector=$connector, location=$location, serviceId=$serviceId)"
}
