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
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.enums.enumEntries
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EnumSubsetUnitTest {

  private enum class TestEnum {
    A,
    B,
    C,
    D,
    E
  }

  @Test
  fun `enumSubset(minSize=invalid) throws`() = runTest {
    checkAll(propTestConfig, invalidSizeArb<TestEnum>()) { invalidMinSize ->
      shouldThrow<IllegalArgumentException> { Arb.enumSubset<TestEnum>(minSize = invalidMinSize) }
    }
  }

  @Test
  fun `enumSubset(maxSize=invalid) throws`() = runTest {
    checkAll(propTestConfig, invalidSizeArb<TestEnum>()) { invalidMaxSize ->
      shouldThrow<IllegalArgumentException> { Arb.enumSubset<TestEnum>(maxSize = invalidMaxSize) }
    }
  }

  @Test
  fun `enumSubset(minSize greater than maxSize) throws`() = runTest {
    checkAll(
      propTestConfig,
      Arb.int(0..TestEnum.entries.size).twoDistinctValues(),
    ) { sizes ->
      val (maxSize, minSize) = sizes.sorted()
      shouldThrow<IllegalArgumentException> {
        Arb.enumSubset<TestEnum>(minSize = minSize, maxSize = maxSize)
      }
    }
  }

  @Test
  fun `enumSubset() respects minSize`() = runTest {
    checkAll(
      propTestConfig,
      Arb.int(0..TestEnum.entries.size),
    ) { minSize ->
      val arb = Arb.enumSubset<TestEnum>(minSize = minSize)
      val sample = arb.bind()
      sample.size shouldBeGreaterThanOrEqual minSize
    }
  }

  @Test
  fun `enumSubset() respects maxSize`() = runTest {
    checkAll(
      propTestConfig,
      Arb.int(0..TestEnum.entries.size),
    ) { maxSize ->
      val arb = Arb.enumSubset<TestEnum>(maxSize = maxSize)
      val sample = arb.bind()
      sample.size shouldBeLessThanOrEqual maxSize
    }
  }

  @Test
  fun `enumSubset() respects minSize and maxSize, when both are specified`() = runTest {
    checkAll(
      propTestConfig,
      Arb.twoValues(Arb.int(0..TestEnum.entries.size)),
    ) { sizes ->
      val (minSize, maxSize) = sizes.sorted()
      val arb = Arb.enumSubset<TestEnum>(minSize = minSize, maxSize = maxSize)
      val sample = arb.bind()
      sample.size shouldBeInRange minSize..maxSize
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

private inline fun <reified T : Enum<T>> invalidSizeArb(): Arb<Int> =
  Arb.choice(Arb.int(max = -1), Arb.int(min = enumEntries<T>().size + 1))
