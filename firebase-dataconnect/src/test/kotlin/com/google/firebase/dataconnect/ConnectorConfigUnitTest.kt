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

@file:Suppress("ReplaceCallWithBinaryOperator")
@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.next
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ConnectorConfigUnitTest {

  @get:Rule val randomSeedTestRule = RandomSeedTestRule()

  private val rs by randomSeedTestRule.rs

  @Test
  fun `properties should be the same objects given to the constructor`() = runTest {
    val arb = Arb.dataConnect.string()
    checkAll(propTestConfig, arb, arb, arb) { connector, location, serviceId ->
      val config =
        ConnectorConfig(
          connector = connector,
          location = location,
          serviceId = serviceId,
        )

      withClue(config) {
        assertSoftly {
          config.connector shouldBeSameInstanceAs connector
          config.location shouldBeSameInstanceAs location
          config.serviceId shouldBeSameInstanceAs serviceId
        }
      }
    }
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.connectorConfig()) { config ->
      val toStringResult = config.toString()

      withClue(toStringResult) {
        assertSoftly {
          toStringResult shouldStartWith "ConnectorConfig("
          toStringResult shouldEndWith ")"
          toStringResult shouldContainWithNonAbuttingText "connector=${config.connector}"
          toStringResult shouldContainWithNonAbuttingText "location=${config.location}"
          toStringResult shouldContainWithNonAbuttingText "serviceId=${config.serviceId}"
        }
      }
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val config = Arb.dataConnect.connectorConfig()

    config.equals(config) shouldBe true
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.connectorConfig()) { config ->
      val configCopy = config.copy()

      config.equals(configCopy) shouldBe true
      configCopy.equals(config) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() {
    val config = Arb.dataConnect.connectorConfig().next(rs)
    config.equals(null) shouldBe false
  }

  @Test
  fun `equals() should return false for a different type`() {
    val config = Arb.dataConnect.connectorConfig().next(rs)
    config.equals("Not A ConnectorConfig Instance") shouldBe false
  }

  @Test
  fun `equals() should return false when only 'connector' differs`() {
    val config1 = Arb.dataConnect.connectorConfig().next(rs)
    val config2 = config1.copy(connector = config1.connector + "NEW")

    config1.equals(config2) shouldBe false
  }

  @Test
  fun `equals() should return false when only 'location' differs`() {
    val config1 = Arb.dataConnect.connectorConfig().next(rs)
    val config2 = config1.copy(location = config1.location + "NEW")

    config1.equals(config2) shouldBe false
  }

  @Test
  fun `equals() should return false when only 'serviceId' differs`() {
    val config1 = Arb.dataConnect.connectorConfig().next(rs)
    val config2 = config1.copy(serviceId = config1.serviceId + "NEW")

    config1.equals(config2) shouldBe false
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.connectorConfig()) { config ->
        val hashCode1 = config.hashCode()
        repeat(20) { i -> withClue("iteration=$i") { config.hashCode() shouldBe hashCode1 } }
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.connectorConfig()) { config1 ->
      val config2 = config1.copy()
      config1.hashCode() shouldBe config2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value when only 'connector' differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.connectorConfig()) { config1 ->
      val config2 = config1.copy(connector = config1.connector + "NEW")
      assume(config1.connector.hashCode() != config2.connector.hashCode())
      config1.hashCode() shouldNotBe config2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value when only 'location' differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.connectorConfig()) { config1 ->
      val config2 = config1.copy(location = config1.location + "NEW")
      assume(config1.location.hashCode() != config2.location.hashCode())
      config1.hashCode() shouldNotBe config2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value when only 'serviceId' differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.connectorConfig()) { config1 ->
      val config2 = config1.copy(serviceId = config1.serviceId + "NEW")
      assume(config1.serviceId.hashCode() != config2.serviceId.hashCode())
      config1.hashCode() shouldNotBe config2.hashCode()
    }
  }

  @Test
  fun `copy() should return a new, equal object when invoked with no explicit arguments`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.connectorConfig()) { config ->
        val configCopy = config.copy()
        assertSoftly {
          configCopy shouldNotBeSameInstanceAs config
          configCopy.connector shouldBeSameInstanceAs config.connector
          configCopy.location shouldBeSameInstanceAs config.location
          configCopy.serviceId shouldBeSameInstanceAs config.serviceId
        }
      }
    }

  @Test
  fun `copy() should return an object with the given 'connector'`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.connectorConfig(), Arb.dataConnect.connectorName()) {
      config,
      newConnector ->
      val configCopy = config.copy(connector = newConnector)
      assertSoftly {
        configCopy.connector shouldBeSameInstanceAs newConnector
        configCopy.location shouldBeSameInstanceAs config.location
        configCopy.serviceId shouldBeSameInstanceAs config.serviceId
      }
    }
  }

  @Test
  fun `copy() should return an object with the given 'location'`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.connectorConfig(),
      Arb.dataConnect.connectorLocation()
    ) { config, newLocation ->
      val configCopy = config.copy(location = newLocation)
      assertSoftly {
        configCopy.connector shouldBeSameInstanceAs config.connector
        configCopy.location shouldBeSameInstanceAs newLocation
        configCopy.serviceId shouldBeSameInstanceAs config.serviceId
      }
    }
  }

  @Test
  fun `copy() should return an object with the given 'serviceId'`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.connectorConfig(),
      Arb.dataConnect.connectorServiceId()
    ) { config, newServiceId ->
      val configCopy = config.copy(serviceId = newServiceId)
      assertSoftly {
        configCopy.connector shouldBeSameInstanceAs config.connector
        configCopy.location shouldBeSameInstanceAs config.location
        configCopy.serviceId shouldBeSameInstanceAs newServiceId
      }
    }
  }

  @Test
  fun `copy() should return an object with properties set to all given arguments`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.connectorConfig(),
      Arb.dataConnect.connectorName(),
      Arb.dataConnect.connectorLocation(),
      Arb.dataConnect.connectorServiceId()
    ) { config, newConnector, newLocation, newServiceId ->
      val configCopy =
        config.copy(connector = newConnector, location = newLocation, serviceId = newServiceId)
      assertSoftly {
        configCopy.connector shouldBeSameInstanceAs newConnector
        configCopy.location shouldBeSameInstanceAs newLocation
        configCopy.serviceId shouldBeSameInstanceAs newServiceId
      }
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)
  }
}
