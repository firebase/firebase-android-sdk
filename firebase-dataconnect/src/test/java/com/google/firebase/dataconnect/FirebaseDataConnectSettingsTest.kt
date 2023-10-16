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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseDataConnectSettingsTest {

  @Test
  fun `defaultInstance properties should have the expected values`() {
    FirebaseDataConnectSettings.defaultInstance.apply {
      assertThat(hostName).isEqualTo("firestore.googleapis.com")
      assertThat(port).isEqualTo(443)
      assertThat(sslEnabled).isEqualTo(true)
    }
  }

  @Test
  fun `builder should be initialized with values from the object given to the constructor`() {
    val settings =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = false
        build()
      }
    settings.builder.apply {
      assertThat(hostName).isEqualTo("abc123")
      assertThat(port).isEqualTo(987654321)
      assertThat(sslEnabled).isEqualTo(false)
    }
  }

  @Test
  fun `builder can change the hostName`() {
    val defaultSettings = FirebaseDataConnectSettings.defaultInstance
    val settings =
      defaultSettings.builder.run {
        hostName = "abc123"
        build()
      }
    settings.apply {
      assertThat(hostName).isEqualTo("abc123")
      assertThat(port).isEqualTo(defaultSettings.port)
      assertThat(sslEnabled).isEqualTo(defaultSettings.sslEnabled)
    }
  }

  @Test
  fun `builder can change the port`() {
    val defaultSettings = FirebaseDataConnectSettings.defaultInstance
    val settings =
      defaultSettings.builder.run {
        port = 987654321
        build()
      }
    settings.apply {
      assertThat(hostName).isEqualTo(defaultSettings.hostName)
      assertThat(port).isEqualTo(987654321)
      assertThat(sslEnabled).isEqualTo(defaultSettings.sslEnabled)
    }
  }

  @Test
  fun `builder can change the sslEnabled`() {
    val defaultSettings = FirebaseDataConnectSettings.defaultInstance
    val settings =
      defaultSettings.builder.run {
        sslEnabled = !defaultSettings.sslEnabled
        build()
      }
    settings.apply {
      assertThat(hostName).isEqualTo(defaultSettings.hostName)
      assertThat(port).isEqualTo(defaultSettings.port)
      assertThat(sslEnabled).isEqualTo(!defaultSettings.sslEnabled)
    }
  }

  @Test
  fun `connectToEmulator() should set the correct values`() {
    FirebaseDataConnectSettings.defaultInstance.builder.apply {
      connectToEmulator()
      assertThat(hostName).isEqualTo("10.0.2.2")
      assertThat(port).isEqualTo(9510)
      assertThat(sslEnabled).isEqualTo(false)
    }
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return true for same instance`() {
    val settings = FirebaseDataConnectSettings.defaultInstance
    assertThat(settings.equals(settings)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val settings = FirebaseDataConnectSettings.defaultInstance
    assertThat(settings.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val settings = FirebaseDataConnectSettings.defaultInstance
    assertThat(settings.equals("an instance of a different class")).isFalse()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return true for distinct instances with the same property values`() {
    val settings1 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
        build()
      }
    val settings2 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
        build()
      }
    // validate the assumption that `settings1` and `settings2` are distinct objects.
    assertThat(settings1).isNotSameInstanceAs(settings2)
    assertThat(settings1.equals(settings2)).isTrue()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return false when hostName differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
        build()
      }
    val settings2 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "zzzzzz"
        port = 987654321
        sslEnabled = true
        build()
      }
    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return false when port differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
        build()
      }
    val settings2 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = -1
        sslEnabled = true
        build()
      }
    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return false when sslEnabled differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
        build()
      }
    val settings2 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = false
        build()
      }
    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value when invoked on the same object`() {
    val settings = FirebaseDataConnectSettings.defaultInstance
    assertThat(settings.hashCode()).isEqualTo(settings.hashCode())
  }

  @Test
  fun `hashCode() should return the same value when invoked on a distinct, but equal object`() {
    val settings1 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
        build()
      }
    val settings2 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
        build()
      }
    // validate the assumption that `settings1` and `settings2` are distinct objects.
    assertThat(settings1).isNotSameInstanceAs(settings2)
    assertThat(settings1.hashCode()).isEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return the different values when hostName differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        build()
      }
    val settings2 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "xyz987"
        build()
      }
    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return the different values when port differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        port = 987
        build()
      }
    val settings2 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        port = 123
        build()
      }
    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return the different values when sslEnabled differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        sslEnabled = true
        build()
      }
    val settings2 =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        sslEnabled = false
        build()
      }
    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `toString() should return a string that contains the property values`() {
    val settings =
      FirebaseDataConnectSettings.defaultInstance.builder.run {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
        build()
      }
    val toStringResult = settings.toString()
    assertThat(toStringResult).containsMatch("hostName=abc123\\W")
    assertThat(toStringResult).containsMatch("port=987654321\\W")
    assertThat(toStringResult).containsMatch("sslEnabled=true\\W")
  }

  @Test
  fun `dataConnectSettings() should use the default values`() {
    dataConnectSettings {
      assertThat(hostName).isEqualTo(FirebaseDataConnectSettings.defaultInstance.hostName)
      assertThat(port).isEqualTo(FirebaseDataConnectSettings.defaultInstance.port)
      assertThat(sslEnabled).isEqualTo(FirebaseDataConnectSettings.defaultInstance.sslEnabled)
    }
  }

  @Test
  fun `dataConnectSettings() should create an object that uses the specified values`() {
    val settings = dataConnectSettings {
      hostName = "abc123"
      port = 987654321
      sslEnabled = false
    }
    settings.apply {
      assertThat(hostName).isEqualTo("abc123")
      assertThat(port).isEqualTo(987654321)
      assertThat(sslEnabled).isEqualTo(false)
    }
  }
}
