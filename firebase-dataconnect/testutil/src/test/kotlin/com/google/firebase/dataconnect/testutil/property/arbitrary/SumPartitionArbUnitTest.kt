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

@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SumPartitionArbUnitTest {

  @Test
  fun `produces valid partitions`() = runTest {
    checkAll(propTestConfig, Arb.int(0..100), Arb.int(1..20)) { sum, summandCount ->
      val arb = SumPartitionArb(sum, summandCount)
      val sample = arb.bind()
      assertSoftly {
        withClue("summandCount") { sample.summands.size shouldBe summandCount }
        withClue("sum") { sample.summands.sum() shouldBe sum }
        withClue("all elements non-negative") {
          sample.summands.forEach { it shouldBeGreaterThanOrEqual 0 }
        }
      }
    }
  }

  @Test
  fun `zero sum and summandCount produces empty list`() = runTest {
    checkAll(SumPartitionArb(sum = 0, summandCount = 0)) { sample ->
      assertSoftly { sample.summands shouldBe emptyList() }
    }
  }

  @Test
  fun `summandCount of one produces single element list containing sum`() = runTest {
    checkAll(propTestConfig, Arb.int(0..100)) { sum ->
      val arb = SumPartitionArb(sum, summandCount = 1)
      val sample = arb.bind()
      assertSoftly { sample.summands shouldBe listOf(sum) }
    }
  }

  @Test
  fun `invalid parameters throw IllegalArgumentException`() {
    shouldThrow<IllegalArgumentException> { SumPartitionArb(sum = -1, summandCount = 5) }
    shouldThrow<IllegalArgumentException> { SumPartitionArb(sum = 10, summandCount = -1) }
    shouldThrow<IllegalArgumentException> { SumPartitionArb(sum = 10, summandCount = 0) }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 200)
  }
}
