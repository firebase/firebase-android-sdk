package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import org.junit.Test

class ConnectorConfigUnitTest {

  private val sampleConfig =
    ConnectorConfig(
      connector = SAMPLE_CONNECTOR,
      location = SAMPLE_LOCATION,
      service = SAMPLE_SERVICE
    )

  @Test
  fun `'connector' property should be the same object given to the constructor`() {
    val connector = "Test Connector"
    val config =
      ConnectorConfig(
        connector = connector,
        location = SAMPLE_LOCATION,
        service = SAMPLE_SERVICE,
      )

    assertThat(config.connector).isSameInstanceAs(connector)
  }

  @Test
  fun `'location' property should be the same object given to the constructor`() {
    val location = "Test Location"
    val config =
      ConnectorConfig(
        connector = SAMPLE_CONNECTOR,
        location = location,
        service = SAMPLE_SERVICE,
      )

    assertThat(config.location).isSameInstanceAs(location)
  }

  @Test
  fun `'service' property should be the same object given to the constructor`() {
    val service = "Test Service"
    val config =
      ConnectorConfig(
        connector = SAMPLE_CONNECTOR,
        location = SAMPLE_LOCATION,
        service = service,
      )
    assertThat(config.service).isSameInstanceAs(service)
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() {
    val config =
      ConnectorConfig(connector = "MyConnector", location = "MyLocation", service = "MyService")

    val toStringResult = config.toString()

    assertThat(toStringResult).startsWith("ConnectorConfig(")
    assertThat(toStringResult).endsWith(")")
    assertThat(toStringResult).containsWithNonAdjacentText("service=MyService")
    assertThat(toStringResult).containsWithNonAdjacentText("location=MyLocation")
    assertThat(toStringResult).containsWithNonAdjacentText("connector=MyConnector")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val config = sampleConfig

    assertThat(config.equals(config)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val config = sampleConfig
    val configCopy = config.copy()

    assertThat(config.equals(configCopy)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    assertThat(sampleConfig.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    assertThat(sampleConfig.equals("Not A ConnectorConfig Instance")).isFalse()
  }

  @Test
  fun `equals() should return false when only 'connector' differs`() {
    val config1 = sampleConfig.copy(connector = "Connector1")
    val config2 = sampleConfig.copy(connector = "Connector2")

    assertThat(config1.equals(config2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'location' differs`() {
    val config1 = sampleConfig.copy(location = "Location1")
    val config2 = sampleConfig.copy(location = "Location2")

    assertThat(config1.equals(config2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'service' differs`() {
    val config1 = sampleConfig.copy(service = "Service1")
    val config2 = sampleConfig.copy(service = "Service2")

    assertThat(config1.equals(config2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val hashCode = sampleConfig.hashCode()

    assertThat(sampleConfig.hashCode()).isEqualTo(hashCode)
    assertThat(sampleConfig.hashCode()).isEqualTo(hashCode)
    assertThat(sampleConfig.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val config = sampleConfig
    val configCopy = config.copy()

    assertThat(config.hashCode()).isEqualTo(configCopy.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'connector' differs`() {
    val config1 = sampleConfig.copy(connector = "Connector1")
    val config2 = sampleConfig.copy(connector = "Connector2")

    assertThat(config1.hashCode()).isNotEqualTo(config2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'location' differs`() {
    val config1 = sampleConfig.copy(location = "Location1")
    val config2 = sampleConfig.copy(location = "Location2")

    assertThat(config1.hashCode()).isNotEqualTo(config2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'service' differs`() {
    val config1 = sampleConfig.copy(service = "Service1")
    val config2 = sampleConfig.copy(service = "Service2")

    assertThat(config1.hashCode()).isNotEqualTo(config2.hashCode())
  }

  @Test
  fun `copy() should return a new, equal object when invoked with no explicit arguments`() {
    val config2 = sampleConfig.copy()

    assertThat(config2).isNotSameInstanceAs(sampleConfig)
    assertThat(config2.connector).isSameInstanceAs(sampleConfig.connector)
    assertThat(config2.location).isSameInstanceAs(sampleConfig.location)
    assertThat(config2.service).isSameInstanceAs(sampleConfig.service)
  }

  @Test
  fun `copy() should return an object with the given 'connector'`() {
    val newConnector = sampleConfig.connector + "ZZZZ"

    val config2 = sampleConfig.copy(connector = newConnector)

    assertThat(config2.connector).isSameInstanceAs(newConnector)
    assertThat(config2.location).isSameInstanceAs(sampleConfig.location)
    assertThat(config2.service).isSameInstanceAs(sampleConfig.service)
  }

  @Test
  fun `copy() should return an object with the given 'location'`() {
    val newLocation = sampleConfig.location + "ZZZZ"

    val config2 = sampleConfig.copy(location = newLocation)

    assertThat(config2.connector).isSameInstanceAs(sampleConfig.connector)
    assertThat(config2.location).isSameInstanceAs(newLocation)
    assertThat(config2.service).isSameInstanceAs(sampleConfig.service)
  }

  @Test
  fun `copy() should return an object with the given 'service'`() {
    val newService = sampleConfig.service + "ZZZZ"

    val config2 = sampleConfig.copy(service = newService)

    assertThat(config2.connector).isSameInstanceAs(sampleConfig.connector)
    assertThat(config2.location).isSameInstanceAs(sampleConfig.location)
    assertThat(config2.service).isSameInstanceAs(newService)
  }

  @Test
  fun `copy() should return an object with properties set to all given arguments`() {
    val newConnector = sampleConfig.connector + "ZZZZ"
    val newLocation = sampleConfig.location + "ZZZZ"
    val newService = sampleConfig.service + "ZZZZ"

    val config2 =
      sampleConfig.copy(connector = newConnector, location = newLocation, service = newService)

    assertThat(config2.connector).isSameInstanceAs(newConnector)
    assertThat(config2.location).isSameInstanceAs(newLocation)
    assertThat(config2.service).isSameInstanceAs(newService)
  }

  companion object {
    const val SAMPLE_CONNECTOR = "SampleConnector"
    const val SAMPLE_LOCATION = "SampleLocation"
    const val SAMPLE_SERVICE = "SampleService"
  }
}
