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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.sint32Arb
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.sint32EdgeCases
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.sint32RangeByByteCount
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.sint64Arb
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.sint64EdgeCases
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.sint64RangeByByteCount
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.uint32Arb
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.uint32EdgeCases
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.uint32MaxValueByByteCount
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.uint64Arb
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.uint64EdgeCases
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.uint64MaxValueByByteCount
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.constant
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CodedIntegersTestingUnitTest {

  @Test
  fun `uint32Arb() should produce correct samples`() = runTest {
    val counts = IntArray(uint32MaxValueByByteCount.size)
    var negativeCount = 0
    val uint32Arb = uint32Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = uint32Arb.sample(randomSource()).value
      if (sample < 0) {
        negativeCount++
      } else if (sample == 0) {
        counts[0]++
      } else {
        val index = uint32MaxValueByByteCount.indexOfLast { sample > it }
        counts[index]++
      }
    }

    assertSoftly {
      withClue("negativeCount") { negativeCount shouldBeGreaterThan 0 }
      counts.forEachIndexed { index, count ->
        withClue("counts[$index]") { count shouldBeGreaterThan 0 }
      }
    }
  }

  @Test
  fun `uint32Arb() should produce correct edge cases`() = runTest {
    val counts = IntArray(uint32EdgeCases.size)
    val uint32Arb = uint32Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = uint32Arb.edgecase(randomSource())
      val index = uint32EdgeCases.indexOf(sample)
      counts[index]++
    }

    assertSoftly {
      counts.forEachIndexed { index, count ->
        withClue("counts[$index],uint32EdgeCases[$index]=${uint32EdgeCases[index]}") {
          count shouldBeGreaterThan 0
        }
      }
    }
  }

  @Test
  fun `uint64Arb() should produce correct samples`() = runTest {
    val counts = IntArray(uint64MaxValueByByteCount.size)
    var negativeCount = 0
    val uint64Arb = uint64Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = uint64Arb.sample(randomSource()).value
      if (sample < 0) {
        negativeCount++
      } else if (sample == 0L) {
        counts[0]++
      } else {
        val index = uint64MaxValueByByteCount.indexOfLast { sample > it }
        counts[index]++
      }
    }

    assertSoftly {
      withClue("negativeCount") { negativeCount shouldBeGreaterThan 0 }
      counts.forEachIndexed { index, count ->
        withClue("counts[$index]") { count shouldBeGreaterThan 0 }
      }
    }
  }

  @Test
  fun `uint64Arb() should produce correct edge cases`() = runTest {
    val counts = IntArray(uint64EdgeCases.size)
    val uint64Arb = uint64Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = uint64Arb.edgecase(randomSource())
      val index = uint64EdgeCases.indexOf(sample)
      counts[index]++
    }

    assertSoftly {
      counts.forEachIndexed { index, count ->
        withClue("counts[$index],uint64EdgeCases[$index]=${uint64EdgeCases[index]}") {
          count shouldBeGreaterThan 0
        }
      }
    }
  }

  @Test
  fun `sint32Arb() should produce correct samples`() = runTest {
    val counts = IntArray(sint32RangeByByteCount.size)
    val sint32Arb = sint32Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = sint32Arb.sample(randomSource()).value
      val index = sint32RangeByByteCount.indexOfFirst { sample in it }
      counts[index]++
    }

    assertSoftly {
      counts.forEachIndexed { index, count ->
        withClue("counts[$index]") { count shouldBeGreaterThan 0 }
      }
    }
  }

  @Test
  fun `sint32Arb() should produce correct edge cases`() = runTest {
    val counts = IntArray(sint32EdgeCases.size)
    val sint32Arb = sint32Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = sint32Arb.edgecase(randomSource())
      val index = sint32EdgeCases.indexOf(sample)
      counts[index]++
    }

    assertSoftly {
      counts.forEachIndexed { index, count ->
        withClue("counts[$index],sint32EdgeCases[$index]=${sint32EdgeCases[index]}") {
          count shouldBeGreaterThan 0
        }
      }
    }
  }

  @Test
  fun `sint64Arb() should produce correct samples`() = runTest {
    val counts = IntArray(sint64RangeByByteCount.size)
    val sint64Arb = sint64Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = sint64Arb.sample(randomSource()).value
      val index = sint64RangeByByteCount.indexOfFirst { sample in it }
      counts[index]++
    }

    assertSoftly {
      counts.forEachIndexed { index, count ->
        withClue("counts[$index]") { count shouldBeGreaterThan 0 }
      }
    }
  }

  @Test
  fun `sint64Arb() should produce correct edge cases`() = runTest {
    val counts = IntArray(sint64EdgeCases.size)
    val sint64Arb = sint64Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = sint64Arb.edgecase(randomSource())
      val index = sint64EdgeCases.indexOf(sample)
      counts[index]++
    }

    assertSoftly {
      counts.forEachIndexed { index, count ->
        withClue("counts[$index],sint64EdgeCases[$index]=${sint64EdgeCases[index]}") {
          count shouldBeGreaterThan 0
        }
      }
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25),
        shrinkingMode = ShrinkingMode.Off,
      )
  }
}
