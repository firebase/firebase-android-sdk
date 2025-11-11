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
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectSettingsUnitTest {

  @Test
  fun `default constructor arguments are correct`() {
    val settings = DataConnectSettings()

    assertSoftly {
      withClue("host") { settings.host shouldBe "firebasedataconnect.googleapis.com" }
      withClue("sslEnabled") { settings.sslEnabled shouldBe true }
      withClue("cacheSettings") { settings.cacheSettings.shouldBeNull() }
    }
  }

  @Test
  fun `properties should be the same objects given to the constructor`() = runTest {
    checkAll(
      propTestConfig,
      Arb.string(),
      Arb.boolean(),
      Arb.dataConnect.cacheSettings().orNull(nullProbability = 0.33),
    ) { host, sslEnabled, cacheSettings ->
      val settings = DataConnectSettings(host, sslEnabled, cacheSettings)
      assertSoftly {
        withClue("host") { settings.host shouldBeSameInstanceAs host }
        withClue("sslEnabled") { settings.sslEnabled shouldBe sslEnabled }
        withClue("cacheSettings") { settings.cacheSettings shouldBeSameInstanceAs cacheSettings }
      }
    }
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() = runTest {
    checkAll(
      propTestConfig,
      Arb.string(),
      Arb.boolean(),
      Arb.dataConnect.cacheSettings().orNull(nullProbability = 0.33),
    ) { host, sslEnabled, cacheSettings ->
      val settings = DataConnectSettings(host, sslEnabled, cacheSettings)
      val toStringResult = settings.toString()
      assertSoftly {
        toStringResult shouldStartWith "DataConnectSettings("
        toStringResult shouldEndWith ")"
        toStringResult shouldContainWithNonAbuttingText "host=${settings.host}"
        toStringResult shouldContainWithNonAbuttingText "sslEnabled=${settings.sslEnabled}"
        toStringResult shouldContainWithNonAbuttingText "cacheSettings=${settings.cacheSettings}"
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
    val otherTypes =
      Arb.choice(
        Arb.string(),
        Arb.int(),
        Arb.dataConnect.errorPath(),
        Arb.dataConnect.cacheSettings()
      )
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
  fun `equals() should return false when only 'cacheSettings' differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.dataConnectSettings(),
      Arb.dataConnect.cacheSettings().orNull(nullProbability = 0.33),
    ) { settings1, newCacheSettings ->
      assume(settings1.cacheSettings != newCacheSettings)
      val settings2 = settings1.copy(cacheSettings = newCacheSettings)
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
      settings1.hashCode() shouldBe settings2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value when only 'host' differs`() = runTest {
    checkAll(
      hashEqualityPropTestConfig,
      Arb.dataConnect.dataConnectSettings(),
      Arb.dataConnect.string()
    ) { settings1, newHost ->
      assume { settings1.host.hashCode() != newHost.hashCode() }
      val settings2 = settings1.copy(host = newHost)
      settings1.hashCode() shouldNotBe settings2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value when only 'sslEnabled' differs`() = runTest {
    checkAll(hashEqualityPropTestConfig, Arb.dataConnect.dataConnectSettings()) { settings1 ->
      val settings2 = settings1.copy(sslEnabled = !settings1.sslEnabled)
      settings1.hashCode() shouldNotBe settings2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value when only 'cacheSettings' differs`() = runTest {
    checkAll(
      hashEqualityPropTestConfig,
      Arb.dataConnect.dataConnectSettings(),
      Arb.dataConnect.cacheSettings().orNull(nullProbability = 0.33),
    ) { settings1, newCacheSettings ->
      assume { settings1.cacheSettings.hashCode() != newCacheSettings.hashCode() }
      val settings2 = settings1.copy(cacheSettings = newCacheSettings)
      settings1.hashCode() shouldNotBe settings2.hashCode()
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
          settings1.cacheSettings shouldBeSameInstanceAs settings2.cacheSettings
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
        settings2.host shouldBeSameInstanceAs newHost
        settings2.sslEnabled shouldBe settings1.sslEnabled
        settings2.cacheSettings shouldBeSameInstanceAs settings1.cacheSettings
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
        settings2.host shouldBeSameInstanceAs settings1.host
        settings2.sslEnabled shouldBe newSslEnabled
        settings2.cacheSettings shouldBeSameInstanceAs settings1.cacheSettings
      }
    }
  }

  @Test
  fun `copy() should return an object with the given 'cacheSettings'`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.dataConnectSettings(),
      Arb.dataConnect.cacheSettings().orNull(nullProbability = 0.33),
    ) { settings1, newCacheSettings ->
      val settings2 = settings1.copy(cacheSettings = newCacheSettings)
      assertSoftly {
        settings2.host shouldBeSameInstanceAs settings1.host
        settings2.sslEnabled shouldBe settings1.sslEnabled
        settings2.cacheSettings shouldBeSameInstanceAs newCacheSettings
      }
    }
  }

  @Test
  fun `copy() should return an object with properties set to all given arguments`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.dataConnectSettings(),
      Arb.dataConnect.string(),
      Arb.boolean(),
      Arb.dataConnect.cacheSettings().orNull(nullProbability = 0.33),
    ) { settings1, newHost, newSslEnabled, newCacheSettings ->
      val settings2 =
        settings1.copy(host = newHost, sslEnabled = newSslEnabled, cacheSettings = newCacheSettings)
      assertSoftly {
        settings2.host shouldBeSameInstanceAs newHost
        settings2.sslEnabled shouldBe newSslEnabled
        settings2.cacheSettings shouldBeSameInstanceAs newCacheSettings
      }
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)

    // Allow a small number of failures to account for the rare, but possible situation where two
    // distinct instances produce the same hash code.
    val hashEqualityPropTestConfig =
      propTestConfig.copy(
        minSuccess = propTestConfig.iterations!! - 2,
        maxFailure = 2,
      )
  }
}
