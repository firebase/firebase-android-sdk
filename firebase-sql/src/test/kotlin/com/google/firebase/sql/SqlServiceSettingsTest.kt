/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.sql

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Tests the value semantics of [SqlServiceSettings]. */
class SqlServiceSettingsTest {

  @Test
  fun onlyTheServiceIdAndTheLocationAreRequired() {
    val settings = SqlServiceSettings(serviceId = SERVICE_ID, location = LOCATION)

    assertThat(settings.serviceId).isEqualTo(SERVICE_ID)
    assertThat(settings.location).isEqualTo(LOCATION)
    // The optional settings are unset; FirebaseSql.getInstance() applies their defaults.
    assertThat(settings.schemaName).isNull()
    assertThat(settings.headers).isEmpty()
    assertThat(settings.host).isNull()
    assertThat(settings.port).isNull()
    assertThat(settings.sslEnabled).isNull()
  }

  @Test
  fun settingsWithEqualPropertiesAreEqual() {
    val settings = fullSettings()
    val same = fullSettings()

    assertThat(same).isEqualTo(settings)
    assertThat(same.hashCode()).isEqualTo(settings.hashCode())
  }

  @Test
  fun settingsThatDifferInAnyPropertyAreNotEqual() {
    val settings = fullSettings()
    val others =
      listOf(
        settings.copy(serviceId = "other-service"),
        settings.copy(location = "europe-west1"),
        settings.copy(schemaName = "other_schema"),
        settings.copy(schemaName = null),
        settings.copy(headers = mapOf("X-Custom" to "other")),
        settings.copy(host = "other.example.com"),
        settings.copy(host = null),
        settings.copy(port = 8080),
        settings.copy(port = null),
        settings.copy(sslEnabled = true),
        settings.copy(sslEnabled = null)
      )

    for (other in others) {
      assertWithMessage("$other").that(other).isNotEqualTo(settings)
    }
  }

  @Test
  fun copyChangesOnlyTheGivenProperties() {
    val copy = fullSettings().copy(schemaName = "other_schema")

    assertThat(copy)
      .isEqualTo(
        SqlServiceSettings(
          serviceId = SERVICE_ID,
          location = LOCATION,
          schemaName = "other_schema",
          headers = HEADERS,
          host = HOST,
          port = PORT,
          sslEnabled = false
        )
      )
  }

  @Test
  fun theHeadersAreCopied() {
    // The settings of a FirebaseSql instance must not change after the instance is created.
    val headers = mutableMapOf("X-Custom" to "1")
    val settings =
      SqlServiceSettings(serviceId = SERVICE_ID, location = LOCATION, headers = headers)

    headers["X-Custom"] = "2"
    headers["X-Other"] = "3"

    assertThat(settings.headers).containsExactly("X-Custom", "1")
  }

  @Test
  fun toStringContainsEveryProperty() {
    val text = fullSettings().toString()

    for (value in
      listOf(
        SERVICE_ID,
        LOCATION,
        "inventory",
        "X-Custom=1",
        HOST,
        "port=$PORT",
        "sslEnabled=false"
      )) {
      assertWithMessage(value).that(text).contains(value)
    }
  }

  private fun fullSettings() =
    SqlServiceSettings(
      serviceId = SERVICE_ID,
      location = LOCATION,
      schemaName = "inventory",
      headers = HEADERS,
      host = HOST,
      port = PORT,
      sslEnabled = false
    )

  private companion object {
    const val SERVICE_ID = "my-service"
    const val LOCATION = "us-central1"
    const val HOST = "10.0.2.2"
    const val PORT = 9399
    val HEADERS = mapOf("X-Custom" to "1")
  }
}
