/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
