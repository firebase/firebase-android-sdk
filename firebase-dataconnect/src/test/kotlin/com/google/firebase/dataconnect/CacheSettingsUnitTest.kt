/*
 * Copyright 2026 Google LLC
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
@file:OptIn(ExperimentalKotest::class, DelicateKotest::class)

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.CacheSettings.Storage
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.shuffle
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CacheSettingsUnitTest {

  @Test
  fun `default constructor arguments are correct`() {
    val cacheSettings = CacheSettings()
    cacheSettings.asClue {
      assertSoftly {
        it.storage shouldBe Storage.PERSISTENT
        it.maxSizeBytes shouldBe 100_000_000
      }
    }
  }

  @Test
  fun `properties should be the same objects given to the constructor`() = runTest {
    checkAll(propTestConfig, Arb.enum<Storage>(), Arb.dataConnect.maxCacheSizeBytes()) {
      storage,
      maxSizeBytes ->
      val cacheSettings = CacheSettings(storage, maxSizeBytes)
      cacheSettings.asClue {
        assertSoftly {
          it.storage shouldBe storage
          it.maxSizeBytes shouldBe maxSizeBytes
        }
      }
    }
  }

  @Test
  fun `constructor should throw for invalid maxSizeBytes`() = runTest {
    checkAll(propTestConfig, Arb.enum<Storage>(), Arb.dataConnect.invalidMaxCacheSizeBytes()) {
      storage,
      invalidMaxSizeBytes ->
      val exception =
        shouldThrow<IllegalArgumentException> { CacheSettings(storage, invalidMaxSizeBytes) }
      exception.message shouldContainWithNonAbuttingText "maxSizeBytes"
      exception.message shouldContainWithNonAbuttingText invalidMaxSizeBytes.toString()
    }
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.cacheSettings()) { cacheSettings: CacheSettings ->
      val toStringResult = cacheSettings.toString()
      assertSoftly {
        toStringResult shouldStartWith "CacheSettings("
        toStringResult shouldEndWith ")"
        toStringResult shouldContainWithNonAbuttingText "storage=${cacheSettings.storage}"
        toStringResult shouldContainWithNonAbuttingText "maxSizeBytes=${cacheSettings.maxSizeBytes}"
      }
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.cacheSettings()) { cacheSettings: CacheSettings ->
      cacheSettings.equals(cacheSettings) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.cacheSettings()) { cacheSettings1: CacheSettings ->
      val cacheSettings2 = cacheSettings1.copy()
      withClue("cacheSettings1=$cacheSettings1 cacheSettings2=$cacheSettings2") {
        cacheSettings1.equals(cacheSettings2) shouldBe true
      }
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.cacheSettings()) { cacheSettings: CacheSettings ->
      cacheSettings.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherArb =
      Arb.choice(
        Arb.string(),
        Arb.int(),
        Arb.dataConnect.dataConnectPath(),
        Arb.enum<Storage>(),
      )
    checkAll(propTestConfig, Arb.dataConnect.cacheSettings(), otherArb) {
      cacheSettings: CacheSettings,
      other: Any ->
      cacheSettings.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when 'storage' differs`() = runTest {
    checkAll(propTestConfig, Arb.shuffle(Storage.entries), Arb.dataConnect.maxCacheSizeBytes()) {
      storages,
      maxSizeBytes ->
      val cacheSettings1 = CacheSettings(storages[0], maxSizeBytes)
      val cacheSettings2 = CacheSettings(storages[1], maxSizeBytes)
      withClue("cacheSettings1=$cacheSettings1 cacheSettings2=$cacheSettings2") {
        cacheSettings1.equals(cacheSettings2) shouldBe false
      }
    }
  }

  @Test
  fun `equals() should return false when 'maxSizeBytes' differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.enum<Storage>(),
      Arb.dataConnect.maxCacheSizeBytes().distinctPair()
    ) { storage, (maxSizeBytes1, maxSizeBytes2) ->
      val cacheSettings1 = CacheSettings(storage, maxSizeBytes1)
      val cacheSettings2 = CacheSettings(storage, maxSizeBytes2)
      withClue("cacheSettings1=$cacheSettings1 cacheSettings2=$cacheSettings2") {
        cacheSettings1.equals(cacheSettings2) shouldBe false
      }
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.cacheSettings()) { cacheSettings: CacheSettings ->
        val hashCode1 = cacheSettings.hashCode()
        cacheSettings.hashCode() shouldBe hashCode1
        cacheSettings.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.cacheSettings()) { cacheSettings1: CacheSettings ->
      val cacheSettings2 = cacheSettings1.copy()
      withClue("cacheSettings1=$cacheSettings1 cacheSettings2=$cacheSettings2") {
        cacheSettings1.hashCode() shouldBe cacheSettings2.hashCode()
      }
    }
  }

  @Test
  fun `hashCode() should return a different value when 'storage' differs`() = runTest {
    checkAll(
      hashEqualityPropTestConfig,
      Arb.shuffle(Storage.entries),
      Arb.dataConnect.maxCacheSizeBytes()
    ) { storages, maxSizeBytes ->
      val cacheSettings1 = CacheSettings(storages[0], maxSizeBytes)
      val cacheSettings2 = CacheSettings(storages[1], maxSizeBytes)
      withClue("cacheSettings1=$cacheSettings1 cacheSettings2=$cacheSettings2") {
        cacheSettings1.hashCode() shouldNotBe cacheSettings2.hashCode()
      }
    }
  }

  @Test
  fun `hashCode() should return a different value when 'maxSizeBytes' differs`() = runTest {
    checkAll(
      hashEqualityPropTestConfig,
      Arb.enum<Storage>(),
      Arb.dataConnect.maxCacheSizeBytes().distinctPair()
    ) { storage, (maxSizeBytes1, maxSizeBytes2) ->
      val cacheSettings1 = CacheSettings(storage, maxSizeBytes1)
      val cacheSettings2 = CacheSettings(storage, maxSizeBytes2)
      withClue("cacheSettings1=$cacheSettings1 cacheSettings2=$cacheSettings2") {
        cacheSettings1.hashCode() shouldNotBe cacheSettings2.hashCode()
      }
    }
  }

  @Test
  fun `copy with no arguments should create a distinct, but equal object`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.cacheSettings()) { cacheSettings1: CacheSettings ->
      val cacheSettings2 = cacheSettings1.copy()
      withClue("cacheSettings1=$cacheSettings1 cacheSettings2=$cacheSettings2") {
        assertSoftly {
          cacheSettings1 shouldNotBeSameInstanceAs cacheSettings2
          cacheSettings1 shouldBe cacheSettings2
        }
      }
    }
  }

  @Test
  fun `copy should use the given storage`() = runTest {
    checkAll(
      propTestConfig,
      Arb.twoValues(Arb.enum<Storage>()),
      Arb.dataConnect.maxCacheSizeBytes()
    ) { (storage1, storage2), maxSizeBytes ->
      val cacheSettings1 = CacheSettings(storage1, maxSizeBytes)
      val cacheSettings2 = cacheSettings1.copy(storage = storage2)
      assertSoftly {
        cacheSettings2.maxSizeBytes shouldBe maxSizeBytes
        cacheSettings2.storage shouldBe storage2
      }
    }
  }

  @Test
  fun `copy should use the given maxSizeBytes`() = runTest {
    checkAll(
      propTestConfig,
      Arb.enum<Storage>(),
      Arb.twoValues(Arb.dataConnect.maxCacheSizeBytes())
    ) { storage, (maxSizeBytes1, maxSizeBytes2) ->
      val cacheSettings1 = CacheSettings(storage, maxSizeBytes1)
      val cacheSettings2 = cacheSettings1.copy(maxSizeBytes = maxSizeBytes2)
      assertSoftly {
        cacheSettings2.maxSizeBytes shouldBe maxSizeBytes2
        cacheSettings2.storage shouldBe storage
      }
    }
  }

  @Test
  fun `copy should throw for invalid maxSizeBytes`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.cacheSettings(),
      Arb.dataConnect.invalidMaxCacheSizeBytes(),
    ) { cacheSettings: CacheSettings, invalidMaxSizeBytes ->
      val exception =
        shouldThrow<IllegalArgumentException> {
          cacheSettings.copy(maxSizeBytes = invalidMaxSizeBytes)
        }
      exception.message shouldContainWithNonAbuttingText "maxSizeBytes"
      exception.message shouldContainWithNonAbuttingText invalidMaxSizeBytes.toString()
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
