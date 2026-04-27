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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SubsetUnitTest {

  @Test
  fun `subset(minSize=invalid) throws`() = runTest {
    checkAll(propTestConfig, Arb.set(Arb.string(), 0..5)) { set ->
      val invalidMinSize = invalidSizeArb(set.size).bind()
      shouldThrow<IllegalArgumentException> { Arb.subset(values = set, minSize = invalidMinSize) }
    }
  }

  @Test
  fun `subset(maxSize=invalid) throws`() = runTest {
    checkAll(propTestConfig, Arb.set(Arb.string(), 0..5)) { set ->
      val invalidMaxSize = invalidSizeArb(set.size).bind()
      shouldThrow<IllegalArgumentException> { Arb.subset(values = set, maxSize = invalidMaxSize) }
    }
  }

  @Test
  fun `subset(minSize greater than maxSize) throws`() = runTest {
    checkAll(propTestConfig, Arb.set(Arb.string(), 2..10)) { set ->
      val (maxSize, minSize) = Arb.int(0..set.size).twoDistinctValues().bind().sorted()
      shouldThrow<IllegalArgumentException> {
        Arb.subset(values = set, minSize = minSize, maxSize = maxSize)
      }
    }
  }

  @Test
  fun `subset() respects minSize`() = runTest {
    checkAll(propTestConfig, Arb.set(Arb.string(), 0..5)) { set ->
      val minSize = Arb.int(0..set.size).bind()
      val arb = Arb.subset(values = set, minSize = minSize)
      val sample = arb.bind()
      sample.size shouldBeGreaterThanOrEqual minSize
    }
  }

  @Test
  fun `subset() respects maxSize`() = runTest {
    checkAll(propTestConfig, Arb.set(Arb.string(), 0..5)) { set ->
      val maxSize = Arb.int(0..set.size).bind()
      val arb = Arb.subset(values = set, maxSize = maxSize)
      val sample = arb.bind()
      sample.size shouldBeLessThanOrEqual maxSize
    }
  }

  @Test
  fun `subset() respects minSize and maxSize, when both are specified`() = runTest {
    checkAll(propTestConfig, Arb.set(Arb.string(), 0..5)) { set ->
      val (minSize, maxSize) = Arb.int(0..set.size).twoDistinctValues().bind().sorted()
      val arb = Arb.subset(values = set, minSize = minSize, maxSize = maxSize)
      val sample = arb.bind()
      sample.size shouldBeInRange minSize..maxSize
    }
  }

  @Test
  fun `subset() generates sets including only values from the given set`() = runTest {
    checkAll(propTestConfig, Arb.set(Arb.string(), 0..5)) { set ->
      val arb = Arb.subset(set)
      val sample = arb.bind()
      set shouldContainAll sample
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private fun invalidSizeArb(setSize: Int): Arb<Int> =
  Arb.choice(Arb.int(max = -1), Arb.int(min = setSize + 1))
