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
