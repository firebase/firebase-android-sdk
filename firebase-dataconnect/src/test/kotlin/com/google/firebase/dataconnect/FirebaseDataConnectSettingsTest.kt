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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import com.google.firebase.dataconnect.testutil.generateRandomAlphanumericString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseDataConnectSettingsTest {

  @Test
  fun `'defaults' companion property should have the expected values`() {
    FirebaseDataConnectSettings.defaults.apply {
      assertThat(hostName).isEqualTo("firestore.googleapis.com")
      assertThat(port).isEqualTo(443)
      assertThat(sslEnabled).isEqualTo(true)
    }
  }

  @Test
  fun `'emulator' companion property should have the expected values`() {
    FirebaseDataConnectSettings.emulator.apply {
      assertThat(hostName).isEqualTo("10.0.2.2")
      assertThat(port).isEqualTo(9510)
      assertThat(sslEnabled).isEqualTo(false)
    }
  }

  @Test
  fun `copy() with no arguments should return a distinct, but equal, instance`() {
    val settings = FirebaseDataConnectSettings.defaults
    val settingsCopy = settings.copy()
    assertThat(settings).isEqualTo(settingsCopy)
    assertThat(settings).isNotSameInstanceAs(settingsCopy)
  }

  @Test
  fun `copy() can change the hostName`() {
    val originalSettings = FirebaseDataConnectSettings.defaults
    val newHostName = generateRandomAlphanumericString()

    val modifiedSettings = originalSettings.copy(hostName = newHostName)

    assertThat(modifiedSettings.hostName).isEqualTo(newHostName)
    assertThat(modifiedSettings.port).isEqualTo(originalSettings.port)
    assertThat(modifiedSettings.sslEnabled).isEqualTo(originalSettings.sslEnabled)
  }

  @Test
  fun `copy() can change the port`() {
    val originalSettings = FirebaseDataConnectSettings.defaults
    val newPort = originalSettings.port + 42

    val modifiedSettings = originalSettings.copy(port = newPort)

    assertThat(modifiedSettings.hostName).isEqualTo(originalSettings.hostName)
    assertThat(modifiedSettings.port).isEqualTo(newPort)
    assertThat(modifiedSettings.sslEnabled).isEqualTo(originalSettings.sslEnabled)
  }

  @Test
  fun `copy() can change the sslEnabled`() {
    val originalSettings = FirebaseDataConnectSettings.defaults
    val newSslEnabled = !originalSettings.sslEnabled

    val modifiedSettings = originalSettings.copy(sslEnabled = newSslEnabled)

    assertThat(modifiedSettings.hostName).isEqualTo(originalSettings.hostName)
    assertThat(modifiedSettings.port).isEqualTo(originalSettings.port)
    assertThat(modifiedSettings.sslEnabled).isEqualTo(newSslEnabled)
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return true for same instance`() {
    val settings = FirebaseDataConnectSettings.defaults
    assertThat(settings.equals(settings)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val settings = FirebaseDataConnectSettings.defaults
    assertThat(settings.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val settings = FirebaseDataConnectSettings.defaults
    assertThat(settings.equals("an instance of a different class")).isFalse()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return true for distinct instances with the same property values`() {
    val settings1 =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "abc123",
        port = 987654321,
        sslEnabled = true
      )
    val settings2 =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "abc123",
        port = 987654321,
        sslEnabled = true
      )
    // validate the assumption that `settings1` and `settings2` are distinct objects.
    assertThat(settings1).isNotSameInstanceAs(settings2)
    assertThat(settings1.equals(settings2)).isTrue()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return false when hostName differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "abc123",
        port = 987654321,
        sslEnabled = true
      )
    val settings2 =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "zzzzzz",
        port = 987654321,
        sslEnabled = true
      )
    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return false when port differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "abc123",
        port = 987654321,
        sslEnabled = true
      )
    val settings2 =
      FirebaseDataConnectSettings.defaults.copy(hostName = "abc123", port = -1, sslEnabled = true)
    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return false when sslEnabled differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "abc123",
        port = 987654321,
        sslEnabled = true
      )
    val settings2 =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "abc123",
        port = 987654321,
        sslEnabled = false
      )
    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value when invoked on the same object`() {
    val settings = FirebaseDataConnectSettings.defaults
    assertThat(settings.hashCode()).isEqualTo(settings.hashCode())
  }

  @Test
  fun `hashCode() should return the same value when invoked on a distinct, but equal object`() {
    val settings1 =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "abc123",
        port = 987654321,
        sslEnabled = true
      )
    val settings2 =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "abc123",
        port = 987654321,
        sslEnabled = true
      )
    // validate the assumption that `settings1` and `settings2` are distinct objects.
    assertThat(settings1).isNotSameInstanceAs(settings2)
    assertThat(settings1.hashCode()).isEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return the different values when hostName differs`() {
    val settings1 = FirebaseDataConnectSettings.defaults.copy(hostName = "abc123")
    val settings2 = FirebaseDataConnectSettings.defaults.copy(hostName = "xyz987")
    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return the different values when port differs`() {
    val settings1 = FirebaseDataConnectSettings.defaults.copy(port = 987)
    val settings2 = FirebaseDataConnectSettings.defaults.copy(port = 123)
    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return the different values when sslEnabled differs`() {
    val settings1 = FirebaseDataConnectSettings.defaults.copy(sslEnabled = true)
    val settings2 = FirebaseDataConnectSettings.defaults.copy(sslEnabled = false)
    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `toString() should return a string that contains the property values`() {
    val settings =
      FirebaseDataConnectSettings.defaults.copy(
        hostName = "abc123",
        port = 987654321,
        sslEnabled = true
      )
    val toStringResult = settings.toString()
    assertThat(toStringResult).containsWithNonAdjacentText("hostName=abc123")
    assertThat(toStringResult).containsWithNonAdjacentText("port=987654321")
    assertThat(toStringResult).containsWithNonAdjacentText("sslEnabled=true")
  }
}
