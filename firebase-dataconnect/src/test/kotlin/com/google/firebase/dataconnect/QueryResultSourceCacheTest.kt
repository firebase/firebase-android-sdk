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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.queryResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.source
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
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
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultSourceCacheTest {

  @Test
  fun `properties should be the same objects given to the constructor`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { isStale ->
      val cache = QueryResult.Source.Cache(isStale)
      cache.asClue { it.isStale shouldBe isStale }
    }
  }

  @Test
  fun `toString() returns a string that incorporates all property values`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { isStale ->
      val cache = QueryResult.Source.Cache(isStale)
      val toStringResult = cache.toString()
      assertSoftly {
        toStringResult shouldStartWith "Cache("
        toStringResult shouldEndWith ")"
        toStringResult shouldContainWithNonAbuttingText "isStale=${cache.isStale}"
      }
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache()) { cache ->
      cache.equals(cache) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache()) { cache1 ->
      val cache2 = cache1.copy()
      withClue("cache1=$cache1 cache2=$cache2") { cache1.equals(cache2) shouldBe true }
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache()) { cache ->
      cache.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherArb =
      Arb.choice(Arb.string(), Arb.int(), Arb.dataConnect.queryResult.source.server().toArb())
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache(), otherArb) { cache, other ->
      cache.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when 'isStale' differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache()) { cache1 ->
      val cache2 = cache1.copy(isStale = !cache1.isStale)
      withClue("cache1=$cache1 cache2=$cache2") { cache1.equals(cache2) shouldBe false }
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache()) { cache ->
        val hashCode1 = cache.hashCode()
        cache.hashCode() shouldBe hashCode1
        cache.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache()) { cache1 ->
      val cache2 = cache1.copy()
      withClue("cache1=$cache1 cache2=$cache2") { cache1.hashCode() shouldBe cache2.hashCode() }
    }
  }

  @Test
  fun `hashCode() should return a different value when 'isStale' differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache()) { cache1 ->
      val cache2 = cache1.copy(isStale = !cache1.isStale)
      withClue("cache1=$cache1 cache2=$cache2") { cache1.hashCode() shouldNotBe cache2.hashCode() }
    }
  }

  @Test
  fun `copy with no arguments should create a distinct, but equal object`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache()) { cache1 ->
      val cache2 = cache1.copy()
      withClue("cache1=$cache1 cache2=$cache2") {
        cache1 shouldNotBeSameInstanceAs cache2
        cache1 shouldBe cache2
      }
    }
  }

  @Test
  fun `copy should use the given isStale`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.cache()) { cache1 ->
      val cache2 = cache1.copy(isStale = !cache1.isStale)
      cache2.isStale shouldBe !cache1.isStale
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)
  }
}
