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

/**
 * Information about a Firebase Data Connect "connector" that is used by [FirebaseDataConnect] to
 * connect to the correct Google Cloud resources.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [ConnectorConfig] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * @property connector The ID of the Firebase Data Connect "connector".
 * @property location The location where the connector is located (for example, `"us-central1"`).
 * @property serviceId The ID of the Firebase Data Connect service.
 */
public class ConnectorConfig(
  public val connector: String,
  public val location: String,
  public val serviceId: String
) {

  /**
   * Compares this object with another object for equality.
   *
   * @param other The object to compare to this for equality.
   * @return true if, and only if, the other object is an instance of [ConnectorConfig] whose public
   * properties compare equal using the `==` operator to the corresponding properties of this
   * object.
   */
  override fun equals(other: Any?): Boolean =
    (other is ConnectorConfig) &&
      other.connector == connector &&
      other.location == location &&
      other.serviceId == serviceId

  /**
   * Calculates and returns the hash code for this object.
   *
   * The hash code is _not_ guaranteed to be stable across application restarts.
   *
   * @return the hash code for this object, that incorporates the values of this object's public
   * properties.
   */
  override fun hashCode(): Int =
    Objects.hash(ConnectorConfig::class, connector, location, serviceId)

  /**
   * Returns a string representation of this object, useful for debugging.
   *
   * The string representation is _not_ guaranteed to be stable and may change without notice at any
   * time. Therefore, the only recommended usage of the returned string is debugging and/or logging.
   * Namely, parsing the returned string or storing the returned string in non-volatile storage
   * should generally be avoided in order to be robust in case that the string representation
   * changes.
   *
   * @return a string representation of this object, which includes the class name and the values of
   * all public properties.
   */
  override fun toString(): String =
    "ConnectorConfig(connector=$connector, location=$location, serviceId=$serviceId)"
}

/** Creates and returns a new [ConnectorConfig] instance with the given property values. */
public fun ConnectorConfig.copy(
  connector: String = this.connector,
  location: String = this.location,
  serviceId: String = this.serviceId
): ConnectorConfig =
  ConnectorConfig(connector = connector, location = location, serviceId = serviceId)
