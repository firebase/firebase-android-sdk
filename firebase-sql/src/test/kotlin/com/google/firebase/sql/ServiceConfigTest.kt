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

/** Tests the value semantics of [ServiceConfig]. */
class ServiceConfigTest {

  @Test
  fun onlyTheServiceIdAndTheLocationAreRequired() {
    val config = ServiceConfig(serviceId = SERVICE_ID, location = LOCATION)

    assertThat(config.serviceId).isEqualTo(SERVICE_ID)
    assertThat(config.location).isEqualTo(LOCATION)
    // The optional properties are unset; FirebaseSql.getInstance() applies their defaults.
    assertThat(config.schemaName).isNull()
    assertThat(config.host).isNull()
    assertThat(config.port).isNull()
    assertThat(config.sslEnabled).isNull()
  }

  @Test
  fun configsWithEqualPropertiesAreEqual() {
    val config = fullConfig()
    val same = fullConfig()

    assertThat(same).isEqualTo(config)
    assertThat(same.hashCode()).isEqualTo(config.hashCode())
  }

  @Test
  fun configsThatDifferInAnyPropertyAreNotEqual() {
    val config = fullConfig()
    val others =
      listOf(
        config.copy(serviceId = "other-service"),
        config.copy(location = "europe-west1"),
        config.copy(schemaName = "other_schema"),
        config.copy(schemaName = null),
        config.copy(host = "other.example.com"),
        config.copy(host = null),
        config.copy(port = 8080),
        config.copy(port = null),
        config.copy(sslEnabled = true),
        config.copy(sslEnabled = null)
      )

    for (other in others) {
      assertWithMessage("$other").that(other).isNotEqualTo(config)
    }
  }

  @Test
  fun copyChangesOnlyTheGivenProperties() {
    val copy = fullConfig().copy(schemaName = "other_schema")

    assertThat(copy)
      .isEqualTo(
        ServiceConfig(
          serviceId = SERVICE_ID,
          location = LOCATION,
          schemaName = "other_schema",
          host = HOST,
          port = PORT,
          sslEnabled = false
        )
      )
  }

  @Test
  fun copyWithoutArgumentsGivesAnEqualConfig() {
    val config = fullConfig()

    val copy = config.copy()

    assertThat(copy).isEqualTo(config)
    assertThat(copy).isNotSameInstanceAs(config)
  }

  @Test
  fun toStringContainsEveryProperty() {
    val text = fullConfig().toString()

    for (value in
      listOf(
        "serviceId=$SERVICE_ID",
        "location=$LOCATION",
        "schemaName=inventory",
        "host=$HOST",
        "port=$PORT",
        "sslEnabled=false"
      )) {
      assertWithMessage(value).that(text).contains(value)
    }
  }

  private fun fullConfig() =
    ServiceConfig(
      serviceId = SERVICE_ID,
      location = LOCATION,
      schemaName = "inventory",
      host = HOST,
      port = PORT,
      sslEnabled = false
    )

  private companion object {
    const val SERVICE_ID = "my-service"
    const val LOCATION = "us-central1"
    const val HOST = "10.0.2.2"
    const val PORT = 9399
  }
}
