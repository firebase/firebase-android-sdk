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
  fun `defaults properties should have the expected values`() {
    FirebaseDataConnectSettings.defaults.apply {
      assertThat(hostName).isEqualTo("firestore.googleapis.com")
      assertThat(port).isEqualTo(443)
      assertThat(sslEnabled).isEqualTo(true)
    }
  }

  @Test
  fun `builder should be initialized with values from the object given to the constructor`() {
    val settings =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = false
      }
    settings.builder.apply {
      assertThat(hostName).isEqualTo("abc123")
      assertThat(port).isEqualTo(987654321)
      assertThat(sslEnabled).isEqualTo(false)
    }
  }

  @Test
  fun `builder can change the hostName`() {
    val defaultSettings = FirebaseDataConnectSettings.defaults
    val settings = defaultSettings.builder.build { hostName = "abc123" }
    settings.apply {
      assertThat(hostName).isEqualTo("abc123")
      assertThat(port).isEqualTo(defaultSettings.port)
      assertThat(sslEnabled).isEqualTo(defaultSettings.sslEnabled)
    }
  }

  @Test
  fun `builder can change the port`() {
    val defaultSettings = FirebaseDataConnectSettings.defaults
    val settings = defaultSettings.builder.build { port = 987654321 }
    settings.apply {
      assertThat(hostName).isEqualTo(defaultSettings.hostName)
      assertThat(port).isEqualTo(987654321)
      assertThat(sslEnabled).isEqualTo(defaultSettings.sslEnabled)
    }
  }

  @Test
  fun `builder can change the sslEnabled`() {
    val defaultSettings = FirebaseDataConnectSettings.defaults
    val settings = defaultSettings.builder.build { sslEnabled = !defaultSettings.sslEnabled }
    settings.apply {
      assertThat(hostName).isEqualTo(defaultSettings.hostName)
      assertThat(port).isEqualTo(defaultSettings.port)
      assertThat(sslEnabled).isEqualTo(!defaultSettings.sslEnabled)
    }
  }

  @Test
  fun `connectToEmulator() should set the correct values`() {
    FirebaseDataConnectSettings.defaults.builder.apply {
      connectToEmulator()
      assertThat(hostName).isEqualTo("10.0.2.2")
      assertThat(port).isEqualTo(9510)
      assertThat(sslEnabled).isEqualTo(false)
    }
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
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
      }
    val settings2 =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
      }
    // validate the assumption that `settings1` and `settings2` are distinct objects.
    assertThat(settings1).isNotSameInstanceAs(settings2)
    assertThat(settings1.equals(settings2)).isTrue()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return false when hostName differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
      }
    val settings2 =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "zzzzzz"
        port = 987654321
        sslEnabled = true
      }
    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return false when port differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
      }
    val settings2 =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = -1
        sslEnabled = true
      }
    assertThat(settings1.equals(settings2)).isFalse()
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `equals() should return false when sslEnabled differs`() {
    val settings1 =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
      }
    val settings2 =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = false
      }
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
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
      }
    val settings2 =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
      }
    // validate the assumption that `settings1` and `settings2` are distinct objects.
    assertThat(settings1).isNotSameInstanceAs(settings2)
    assertThat(settings1.hashCode()).isEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return the different values when hostName differs`() {
    val settings1 = FirebaseDataConnectSettings.defaults.builder.build { hostName = "abc123" }
    val settings2 = FirebaseDataConnectSettings.defaults.builder.build { hostName = "xyz987" }
    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return the different values when port differs`() {
    val settings1 = FirebaseDataConnectSettings.defaults.builder.build { port = 987 }
    val settings2 = FirebaseDataConnectSettings.defaults.builder.build { port = 123 }
    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `hashCode() should return the different values when sslEnabled differs`() {
    val settings1 = FirebaseDataConnectSettings.defaults.builder.build { sslEnabled = true }
    val settings2 = FirebaseDataConnectSettings.defaults.builder.build { sslEnabled = false }
    assertThat(settings1.hashCode()).isNotEqualTo(settings2.hashCode())
  }

  @Test
  fun `toString() should return a string that contains the property values`() {
    val settings =
      FirebaseDataConnectSettings.defaults.builder.build {
        hostName = "abc123"
        port = 987654321
        sslEnabled = true
      }
    val toStringResult = settings.toString()
    assertThat(toStringResult).containsMatch("hostName=abc123\\W")
    assertThat(toStringResult).containsMatch("port=987654321\\W")
    assertThat(toStringResult).containsMatch("sslEnabled=true\\W")
  }
}
