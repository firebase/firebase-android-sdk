// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

public class FirebaseDataConnectSettings private constructor(private val values: SettingsValues) {

  public val hostName: String
    get() = values.hostName

  public val port: Int
    get() = values.port

  public val sslEnabled: Boolean
    get() = values.sslEnabled

  public fun copy(
    hostName: String = this.hostName,
    port: Int = this.port,
    sslEnabled: Boolean = this.sslEnabled
  ): FirebaseDataConnectSettings =
    FirebaseDataConnectSettings(
      SettingsValues(hostName = hostName, port = port, sslEnabled = sslEnabled)
    )

  public companion object {
    public val defaults: FirebaseDataConnectSettings
      get() =
        FirebaseDataConnectSettings(
          SettingsValues(hostName = "firestore.googleapis.com", port = 443, sslEnabled = true)
        )

    public val emulator: FirebaseDataConnectSettings
      get() = defaults.copy(hostName = "10.0.2.2", port = 9510, sslEnabled = false)
  }

  override fun equals(other: Any?): Boolean =
    (other as? FirebaseDataConnectSettings)?.let { it.values == values } ?: false

  override fun hashCode(): Int = values.hashCode()

  override fun toString(): String =
    "FirebaseDataConnectSettings(" +
      "hostName=$hostName, " +
      "port=$port, " +
      "sslEnabled=$sslEnabled" +
      ")"
}

// Use a data class internally to store the settings to get the convenience of the equals(),
// hashCode(), and copy() auto-generated methods.
private data class SettingsValues(
  val hostName: String,
  val port: Int,
  val sslEnabled: Boolean,
)
