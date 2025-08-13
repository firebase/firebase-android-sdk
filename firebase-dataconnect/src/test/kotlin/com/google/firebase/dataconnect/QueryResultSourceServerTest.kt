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
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultSourceServerTest {

  @Test
  fun `toString() returns a string that incorporates all property values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.server()) { cache ->
      cache.toString() shouldBe "Server"
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.server()) { cache ->
      cache.equals(cache) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.server()) { cache ->
      cache.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherArb =
      Arb.choice(Arb.string(), Arb.int(), Arb.dataConnect.queryResult.source.cache().toArb())
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.server(), otherArb) { cache, other
      ->
      cache.equals(other) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResult.source.server()) { cache ->
      val hashCode1 = cache.hashCode()
      cache.hashCode() shouldBe hashCode1
      cache.hashCode() shouldBe hashCode1
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)
  }
}
