/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.cache

import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.inMemoryCache
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.invalidMaxCacheSizeBytes
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.maxCacheSizeBytes
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.persistentCache
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
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PersistentCacheUnitTest {

  @Test
  fun `default constructor arguments are correct`() {
    val cache = PersistentCache()
    cache.asClue { it.maxSizeBytes shouldBe PersistentCache.DEFAULT_MAX_SIZE_BYTES }
  }

  @Test
  fun `properties should be the same objects given to the constructor`() = runTest {
    checkAll(propTestConfig, Arb.maxCacheSizeBytes()) { maxSizeBytes ->
      val cache = PersistentCache(maxSizeBytes)
      cache.asClue { it.maxSizeBytes shouldBe maxSizeBytes }
    }
  }

  @Test
  fun `constructor should throw for invalid maxSizeBytes`() = runTest {
    checkAll(propTestConfig, Arb.invalidMaxCacheSizeBytes()) { invalidMaxSizeBytes ->
      val exception = shouldThrow<IllegalArgumentException> { PersistentCache(invalidMaxSizeBytes) }
      exception.message shouldContainWithNonAbuttingText "maxSizeBytes"
      exception.message shouldContainWithNonAbuttingText invalidMaxSizeBytes.toString()
    }
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() = runTest {
    checkAll(propTestConfig, Arb.maxCacheSizeBytes()) { maxSizeBytes ->
      val cache = PersistentCache(maxSizeBytes)
      val toStringResult = cache.toString()
      assertSoftly {
        toStringResult shouldStartWith "PersistentCache("
        toStringResult shouldEndWith ")"
        toStringResult shouldContainWithNonAbuttingText "maxSizeBytes=${cache.maxSizeBytes}"
      }
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, Arb.persistentCache()) { cache: PersistentCache ->
      cache.equals(cache) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.persistentCache()) { cache1: PersistentCache ->
      val cache2 = cache1.copy()
      withClue("cache1=$cache1 cache2=$cache2") { cache1.equals(cache2) shouldBe true }
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.persistentCache()) { cache: PersistentCache ->
      cache.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherArb =
      Arb.choice(
        Arb.string(),
        Arb.int(),
        Arb.dataConnect.errorPath(),
        Arb.inMemoryCache(),
      )
    checkAll(propTestConfig, Arb.persistentCache(), otherArb) { cache: PersistentCache, other: Any
      ->
      cache.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when 'maxSizeBytes' differs`() = runTest {
    checkAll(propTestConfig, Arb.maxCacheSizeBytes().distinctPair()) {
      (maxSizeBytes1, maxSizeBytes2) ->
      val cache1 = PersistentCache(maxSizeBytes1)
      val cache2 = PersistentCache(maxSizeBytes2)
      withClue("cache1=$cache1 cache2=$cache2") { cache1.equals(cache2) shouldBe false }
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, Arb.persistentCache()) { cache: PersistentCache ->
        val hashCode1 = cache.hashCode()
        cache.hashCode() shouldBe hashCode1
        cache.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, Arb.persistentCache()) { cache1: PersistentCache ->
      val cache2 = cache1.copy()
      withClue("cache1=$cache1 cache2=$cache2") { cache1.hashCode() shouldBe cache2.hashCode() }
    }
  }

  @Test
  fun `hashCode() should return a different value when 'maxSizeBytes' differs`() = runTest {
    checkAll(hashEqualityPropTestConfig, Arb.maxCacheSizeBytes().distinctPair()) {
      (maxSizeBytes1, maxSizeBytes2) ->
      val cache1 = PersistentCache(maxSizeBytes1)
      val cache2 = PersistentCache(maxSizeBytes2)
      withClue("cache1=$cache1 cache2=$cache2") { cache1.hashCode() shouldNotBe cache2.hashCode() }
    }
  }

  @Test
  fun `copy with no arguments should create a distinct, but equal object`() = runTest {
    checkAll(propTestConfig, Arb.persistentCache()) { cache1: PersistentCache ->
      val cache2 = cache1.copy()
      withClue("cache1=$cache1 cache2=$cache2") {
        cache1 shouldNotBeSameInstanceAs cache2
        cache1 shouldBe cache2
      }
    }
  }

  @Test
  fun `copy should use the given maxSizeBytes`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.maxCacheSizeBytes())) {
      (maxSizeBytes1, maxSizeBytes2) ->
      val cache1 = PersistentCache(maxSizeBytes1)
      val cache2 = cache1.copy(maxSizeBytes = maxSizeBytes2)
      cache2.maxSizeBytes shouldBe maxSizeBytes2
    }
  }

  @Test
  fun `copy should throw for invalid maxSizeBytes`() = runTest {
    checkAll(propTestConfig, Arb.persistentCache(), Arb.invalidMaxCacheSizeBytes()) {
      cache: PersistentCache,
      invalidMaxSizeBytes ->
      val exception =
        shouldThrow<IllegalArgumentException> { cache.copy(maxSizeBytes = invalidMaxSizeBytes) }
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
