package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import org.junit.Test

class DataConnectSettingsTest {

  @Test
  fun `default constructor arguments are correct`() {
    val settings = DataConnectSettings()

    assertThat(settings.host).isEqualTo("dataconnect.googleapis.com")
    assertThat(settings.sslEnabled).isTrue()
  }

  @Test
  fun `'host' property should be the same object given to the constructor`() {
    val host = "Test Host"

    val settings = DataConnectSettings(host = host)

    assertThat(settings.host).isSameInstanceAs(host)
  }

  @Test
  fun `'sslEnabled' property should be the same object given to the constructor`() {
    val settingsWithSslEnabledTrue = DataConnectSettings(sslEnabled = true)
    val settingsWithSslEnabledFalse = DataConnectSettings(sslEnabled = false)

    assertThat(settingsWithSslEnabledTrue.sslEnabled).isTrue()
    assertThat(settingsWithSslEnabledFalse.sslEnabled).isFalse()
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() {
    val settings = DataConnectSettings(host = "MyHost", sslEnabled = false)

    val toStringResult = settings.toString()

    assertThat(toStringResult).startsWith("DataConnectSettings(")
    assertThat(toStringResult).endsWith(")")
    assertThat(toStringResult).containsWithNonAdjacentText("host=MyHost")
    assertThat(toStringResult).containsWithNonAdjacentText("sslEnabled=false")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val settings = DataConnectSettings()

    assertThat(settings.equals(settings)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val settings = DataConnectSettings()
    val settingsCopy = settings.copy()

    assertThat(settings.equals(settingsCopy)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val settings = DataConnectSettings()

    assertThat(settings.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val settings = DataConnectSettings()

    assertThat(settings.equals("Not A DataConnectSettings Instance")).isFalse()
  }

  @Test
  fun `equals() should return false when only 'host' differs`() {
    val settings1 = DataConnectSettings(host = "Host1")
    val settings2 = DataConnectSettings(host = "Host2")

    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'sslEnabled' differs`() {
    val settings1 = DataConnectSettings(sslEnabled = true)
    val settings2 = DataConnectSettings(sslEnabled = false)

    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val settings = DataConnectSettings()

    val hashCode = settings.hashCode()

    assertThat(settings.hashCode()).isEqualTo(hashCode)
    assertThat(settings.hashCode()).isEqualTo(hashCode)
    assertThat(settings.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val settings = DataConnectSettings()
    val settingsCopy = settings.copy()

    assertThat(settings.hashCode()).isEqualTo(settingsCopy.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'host' differs`() {
    val settings1 = DataConnectSettings(host = "Host1")
    val settings2 = DataConnectSettings(host = "Host2")

    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value when only 'sslEnabled' differs`() {
    val settings1 = DataConnectSettings(sslEnabled = true)
    val settings2 = DataConnectSettings(sslEnabled = false)

    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `copy() should return a new, equal object when invoked with no explicit arguments`() {
    val settings = DataConnectSettings()
    val settings2 = settings.copy()

    assertThat(settings2).isNotSameInstanceAs(settings)
    assertThat(settings2.host).isSameInstanceAs(settings.host)
    assertThat(settings2.sslEnabled).isEqualTo(settings.sslEnabled)
  }

  @Test
  fun `copy() should return an object with the given 'host'`() {
    val settings = DataConnectSettings()
    val newHost = settings.host + "ZZZZ"

    val settings2 = settings.copy(host = newHost)

    assertThat(settings2.host).isSameInstanceAs(newHost)
    assertThat(settings2.sslEnabled).isEqualTo(settings.sslEnabled)
  }

  @Test
  fun `copy() should return an object with the given 'sslEnabled'`() {
    val settings = DataConnectSettings()
    val newSslEnabled = !settings.sslEnabled

    val settings2 = settings.copy(sslEnabled = newSslEnabled)

    assertThat(settings2.host).isSameInstanceAs(settings.host)
    assertThat(settings2.sslEnabled).isEqualTo(newSslEnabled)
  }

  @Test
  fun `copy() should return an object with properties set to all given arguments`() {
    val settings = DataConnectSettings()
    val newHost = settings.host + "ZZZZ"
    val newSslEnabled = !settings.sslEnabled

    val settings2 = settings.copy(host = newHost, sslEnabled = newSslEnabled)

    assertThat(settings2.host).isSameInstanceAs(newHost)
    assertThat(settings2.sslEnabled).isEqualTo(newSslEnabled)
  }
}
