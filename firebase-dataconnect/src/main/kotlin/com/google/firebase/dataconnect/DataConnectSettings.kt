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
 * Settings that control the behavior of [FirebaseDataConnect] instances.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [DataConnectSettings] are thread-safe and may be safely called
 * and/or accessed concurrently from multiple threads and/or coroutines.
 *
 * @property host The host of the Firebase Data Connect service to which to connect (for example,
 * `"myproxy.foo.com"`, `"myproxy.foo.com:9987"`).
 * @property sslEnabled Whether to use SSL for the connection; if `true`, then the connection will
 * be encrypted using SSL and, if false, the connection will _not_ be encrypted and all network
 * transmission will happen in plaintext.
 */
public class DataConnectSettings(
  public val host: String = "firebasedataconnect.googleapis.com",
  public val sslEnabled: Boolean = true
) {

  /**
   * Compares this object with another object for equality.
   *
   * @param other The object to compare to this for equality.
   * @return true if, and only if, the other object is an instance of [DataConnectSettings] whose
   * public properties compare equal using the `==` operator to the corresponding properties of this
   * object.
   */
  override fun equals(other: Any?): Boolean =
    (other is DataConnectSettings) && other.host == host && other.sslEnabled == sslEnabled

  /**
   * Calculates and returns the hash code for this object.
   *
   * The hash code is _not_ guaranteed to be stable across application restarts.
   *
   * @return the hash code for this object, that incorporates the values of this object's public
   * properties.
   */
  override fun hashCode(): Int = Objects.hash(DataConnectSettings::class, host, sslEnabled)

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
  override fun toString(): String = "DataConnectSettings(host=$host, sslEnabled=$sslEnabled)"
}

/** Creates and returns a new [DataConnectSettings] instance with the given property values. */
public fun DataConnectSettings.copy(
  host: String = this.host,
  sslEnabled: Boolean = this.sslEnabled
): DataConnectSettings = DataConnectSettings(host = host, sslEnabled = sslEnabled)

internal fun DataConnectSettings.isDefaultHost() = host == DataConnectSettings().host
