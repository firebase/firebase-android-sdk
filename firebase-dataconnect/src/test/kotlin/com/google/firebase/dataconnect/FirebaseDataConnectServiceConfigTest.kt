package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.FirebaseDataConnect.ServiceConfig
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import org.junit.Test

private val SAMPLE_SERVICE_ID = "SampleServiceId"
private val SAMPLE_LOCATION = "SampleLocation"
private val SAMPLE_OPERATION_SET = "SampleOperationSet"
private val SAMPLE_REVISION = "SampleRevision"

class FirebaseDataConnectServiceConfigTest {

  @Test
  fun `'serviceId' property should be the same object given to the constructor`() {
    val serviceId = "Test Service ID"
    val serviceConfig =
      ServiceConfig(
        serviceId = serviceId,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
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
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig.location).isSameInstanceAs(location)
  }

  @Test
  fun `'operationSet' property should be the same object given to the constructor`() {
    val operationSet = "Test Operation Set"
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = operationSet,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig.operationSet).isSameInstanceAs(operationSet)
  }

  @Test
  fun `'revision' property should be the same object given to the constructor`() {
    val revision = "Test Revision"
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = revision
      )
    assertThat(serviceConfig.revision).isSameInstanceAs(revision)
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() {
    val serviceConfig =
      ServiceConfig(
        serviceId = "MyServiceId",
        location = "MyLocation",
        operationSet = "MyOperationSet",
        revision = "MyRevision"
      )

    val toStringResult = serviceConfig.toString()

    assertThat(toStringResult).startsWith("ServiceConfig(")
    assertThat(toStringResult).endsWith(")")
    assertThat(toStringResult).containsWithNonAdjacentText("serviceId=MyServiceId")
    assertThat(toStringResult).containsWithNonAdjacentText("location=MyLocation")
    assertThat(toStringResult).containsWithNonAdjacentText("operationSet=MyOperationSet")
    assertThat(toStringResult).containsWithNonAdjacentText("revision=MyRevision")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig.equals(serviceConfig)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig1.equals(serviceConfig2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only 'serviceId' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = "foo",
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = "bar",
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig1.equals(serviceConfig2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'location' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = "foo",
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = "bar",
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig1.equals(serviceConfig2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'operationSet' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = "foo",
        revision = SAMPLE_REVISION
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = "bar",
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig1.equals(serviceConfig2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'revision' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = "foo"
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = "bar"
      )
    assertThat(serviceConfig1.equals(serviceConfig2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val serviceConfig =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
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
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig1.hashCode()).isEqualTo(serviceConfig2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'serviceId' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = "foo",
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = "bar",
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig1.hashCode()).isNotEqualTo(serviceConfig2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'location' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = "foo",
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = "bar",
        operationSet = SAMPLE_OPERATION_SET,
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig1.hashCode()).isNotEqualTo(serviceConfig2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'operationSet' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = "foo",
        revision = SAMPLE_REVISION
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = "bar",
        revision = SAMPLE_REVISION
      )
    assertThat(serviceConfig1.hashCode()).isNotEqualTo(serviceConfig2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'revision' differs`() {
    val serviceConfig1 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = "foo"
      )
    val serviceConfig2 =
      ServiceConfig(
        serviceId = SAMPLE_SERVICE_ID,
        location = SAMPLE_LOCATION,
        operationSet = SAMPLE_OPERATION_SET,
        revision = "bar"
      )
    assertThat(serviceConfig1.hashCode()).isNotEqualTo(serviceConfig2.hashCode())
  }
}
