package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.FirebaseDataConnect.ServiceConfig
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import org.junit.Test

private val SAMPLE_SERVICE_ID = "SampleServiceId"
private val SAMPLE_LOCATION = "SampleLocation"
private val SAMPLE_CONNECTOR = "SampleConnector"

class FirebaseDataConnectServiceConfigTest {

  @Test
  fun `'serviceId' property should be the same object given to the constructor`() {
    val serviceId = "Test Service ID"
    val serviceConfig =
      ServiceConfig(
        serviceId = serviceId,
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig.serviceId).isSameInstanceAs(serviceId)
  }

  @Test
  fun `'location' property should be the same object given to the constructor`() {
    val location = "Test Location"
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = location,
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig.location).isSameInstanceAs(location)
  }

  @Test
  fun `'connector' property should be the same object given to the constructor`() {
    val connector = "Test Connector"
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = connector,
      )
    assertThat(serviceConfig.connector).isSameInstanceAs(connector)
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() {
    val serviceConfig =
      ServiceConfig(serviceId = "MyServiceId", location = "MyLocation", connector = "MyConnector")

    val toStringResult = serviceConfig.toString()

    assertThat(toStringResult).startsWith("ServiceConfig(")
    assertThat(toStringResult).endsWith(")")
    assertThat(toStringResult).containsWithNonAdjacentText("serviceId=MyServiceId")
    assertThat(toStringResult).containsWithNonAdjacentText("location=MyLocation")
    assertThat(toStringResult).containsWithNonAdjacentText("connector=MyConnector")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig.equals(serviceConfig)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig1.equals(serviceConfig2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only 'serviceId' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = "foo",
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = "bar",
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig1.equals(serviceConfig2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'location' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = "foo",
        connector = SAMPLE_CONNECTOR,
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = "bar",
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig1.equals(serviceConfig2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'connector' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = "foo",
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = "bar",
      )
    assertThat(serviceConfig1.equals(serviceConfig2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    val hashCode = serviceConfig.hashCode()
    assertThat(serviceConfig.hashCode()).isEqualTo(hashCode)
    assertThat(serviceConfig.hashCode()).isEqualTo(hashCode)
    assertThat(serviceConfig.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig1.hashCode()).isEqualTo(serviceConfig2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'serviceId' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = "foo",
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = "bar",
        location = SAMPLE_LOCATION,
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig1.hashCode()).isNotEqualTo(serviceConfig2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'location' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = "foo",
        connector = SAMPLE_CONNECTOR,
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = "bar",
        connector = SAMPLE_CONNECTOR,
      )
    assertThat(serviceConfig1.hashCode()).isNotEqualTo(serviceConfig2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'connector' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = "foo",
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        connector = "bar",
      )
    assertThat(serviceConfig1.hashCode()).isNotEqualTo(serviceConfig2.hashCode())
  }
}
