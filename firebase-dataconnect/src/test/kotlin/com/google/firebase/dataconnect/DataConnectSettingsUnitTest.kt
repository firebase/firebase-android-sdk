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

import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.sourceLocation
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectSettingsUnitTest {

  @Test
  fun `default constructor arguments are correct`() {
    val settings = DataConnectSettings()

    settings.host shouldBe "firebasedataconnect.googleapis.com"
    settings.sslEnabled shouldBe true
  }

  @Test
  fun `properties should be the same objects given to the constructor`() = runTest {
    checkAll(propTestConfig, Arb.string(), Arb.boolean()) { host, sslEnabled ->
      val settings = DataConnectSettings(host, sslEnabled)
      assertSoftly {
        settings.host shouldBeSameInstanceAs host
        settings.sslEnabled shouldBe sslEnabled
      }
    }
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() = runTest {
    checkAll(propTestConfig, Arb.string(), Arb.boolean()) { host, sslEnabled ->
      val settings = DataConnectSettings(host, sslEnabled)
      val toStringResult = settings.toString()
      assertSoftly {
        toStringResult shouldStartWith "DataConnectSettings("
        toStringResult shouldEndWith ")"
        toStringResult shouldContainWithNonAbuttingText "host=${settings.host}"
        toStringResult shouldContainWithNonAbuttingText "sslEnabled=${settings.sslEnabled}"
      }
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings()) { settings ->
      settings.equals(settings) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings()) { settings1 ->
      val settings2 = settings1.copy()
      settings1.equals(settings2) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings()) { settings ->
      settings.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherTypes = Arb.choice(Arb.string(), Arb.int(), Arb.dataConnect.sourceLocation())
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings(), otherTypes) { settings, other ->
      settings.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only 'host' differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings(), Arb.dataConnect.string()) {
      settings1,
      newHost ->
      val settings2 = settings1.copy(host = newHost)
      settings1.equals(settings2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only 'sslEnabled' differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings()) { settings1 ->
      val settings2 = settings1.copy(sslEnabled = !settings1.sslEnabled)
      settings1.equals(settings2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings()) { settings ->
        val hashCode1 = settings.hashCode()
        settings.hashCode() shouldBe hashCode1
        settings.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings()) { settings1 ->
      val settings2 = settings1.copy()
      settings1.equals(settings2) shouldBe true
    }
  }

  @Test
  fun `hashCode() should return a different value when only 'host' differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings(), Arb.dataConnect.string()) {
      settings1,
      newHost ->
      assume { settings1.host.hashCode() != newHost.hashCode() }
      val settings2 = settings1.copy(host = newHost)
      settings1.equals(settings2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return a different value when only 'sslEnabled' differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings()) { settings1 ->
      val settings2 = settings1.copy(sslEnabled = !settings1.sslEnabled)
      settings1.equals(settings2) shouldBe false
    }
  }

  @Test
  fun `copy() should return a new, equal object when invoked with no explicit arguments`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings()) { settings1 ->
        val settings2 = settings1.copy()
        assertSoftly {
          settings1 shouldNotBeSameInstanceAs settings2
          settings1.equals(settings2) shouldBe true
          settings1.host shouldBeSameInstanceAs settings2.host
          settings1.sslEnabled shouldBe settings2.sslEnabled
        }
      }
    }

  @Test
  fun `copy() should return an object with the given 'host'`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings(), Arb.dataConnect.string()) {
      settings1,
      newHost ->
      val settings2 = settings1.copy(host = newHost)
      assertSoftly {
        settings1 shouldNotBeSameInstanceAs settings2
        settings1.equals(settings2) shouldBe false
        settings2.host shouldBeSameInstanceAs newHost
      }
    }
  }

  @Test
  fun `copy() should return an object with the given 'sslEnabled'`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectSettings(), Arb.boolean()) {
      settings1,
      newSslEnabled ->
      val settings2 = settings1.copy(sslEnabled = newSslEnabled)
      assertSoftly {
        settings1 shouldNotBeSameInstanceAs settings2
        settings1.equals(settings2) shouldBe (settings1.sslEnabled == newSslEnabled)
        settings2.sslEnabled shouldBe newSslEnabled
      }
    }
  }

  @Test
  fun `copy() should return an object with properties set to all given arguments`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.dataConnectSettings(),
      Arb.dataConnect.string(),
      Arb.boolean()
    ) { settings1, newHost, newSslEnabled ->
      val settings2 = settings1.copy(host = newHost, sslEnabled = newSslEnabled)
      assertSoftly {
        settings1 shouldNotBeSameInstanceAs settings2
        settings1.equals(settings2) shouldBe false
        settings2.host shouldBeSameInstanceAs newHost
        settings2.sslEnabled shouldBe newSslEnabled
      }
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)
  }
}
